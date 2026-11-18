package com.bloop.payments;

import com.bloop.payments.service.StripeSignatureVerifier;
import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.ApiClient;
import com.bloop.support.FakeStripeServer;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.bloop.support.TestDataFactory.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stripe en modo test de extremo a extremo, contra un servidor falso que imita la API de Stripe:
 * sesion de pago -> webhook firmado -> pedido pagado -> reembolso por la API de Stripe.
 */
class StripeIntegrationTest extends AbstractIntegrationTest {

    private static final String WEBHOOK_SECRET = "whsec_test_bloop_0123456789";
    private static final FakeStripeServer STRIPE = new FakeStripeServer();

    @DynamicPropertySource
    static void stripe(DynamicPropertyRegistry registry) {
        registry.add("bloop.payments.stripe.enabled", () -> "true");
        registry.add("bloop.payments.stripe.secret-key", () -> "sk_test_fake_key");
        registry.add("bloop.payments.stripe.webhook-secret", () -> WEBHOOK_SECRET);
        registry.add("bloop.payments.stripe.api-base-url", STRIPE::baseUrl);
    }

    @Autowired
    private JdbcClient jdbc;

    @Test
    void cardPaymentThroughStripeCheckoutAndSignedWebhook() {
        var api = api();
        String admin = api.login(data.createTenantWithAdmin("Stripe").adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "12.50", 50, 5);
        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);

        assertThat((Boolean) api.get("/api/public/payment-methods", null).json("$.stripe")).isTrue();

        var order = data.checkout(api, customer, """
                {"sessionId":"%s","items":[{"ticketTypeId":"%s","quantity":2}]}
                """.formatted(event.sessionId(), event.ticketTypeId()));
        String orderId = order.json("$.id");

        // 1. Sesion de Stripe Checkout: se envia el total en centimos con la clave secreta.
        var session = api.post("/api/checkout/orders/" + orderId + "/stripe-session", customer, null);
        assertStatus(session, 200);
        assertThat((String) session.json("$.checkoutUrl")).startsWith("https://checkout.stripe.test/cs_test_");
        var sent = STRIPE.requests("/v1/checkout/sessions").getLast();
        assertThat(sent.authorization()).isEqualTo("Bearer sk_test_fake_key");
        assertThat(sent.form()).containsEntry("line_items[0][price_data][unit_amount]", "2500")
                .containsEntry("client_reference_id", orderId);
        String sessionId = ((String) session.json("$.checkoutUrl")).substring("https://checkout.stripe.test/".length());

        // 2. Un webhook sin firma valida se rechaza y el pedido sigue pendiente.
        String completed = completedEvent("evt_" + UUID.randomUUID(), sessionId, "pi_test_123");
        assertThat(postWebhook(api, completed, "t=" + now() + ",v1=firmafalsa").status()).isEqualTo(400);
        assertThat(postWebhook(api, completed, signature(completed, now() - 3600)).status()).isEqualTo(400);
        assertThat((String) api.get("/api/me/orders/" + orderId, customer).json("$.status")).isEqualTo("PENDING_PAYMENT");

        // 3. Webhook firmado: pedido pagado y entradas emitidas. Repetirlo no duplica nada.
        assertStatus(postWebhook(api, completed, signature(completed, now())), 200);
        assertStatus(postWebhook(api, completed, signature(completed, now())), 200);
        assertThat((String) api.get("/api/me/orders/" + orderId, customer).json("$.status")).isEqualTo("PAID");
        assertThat((List<?>) api.get("/api/me/tickets", customer).json("$")).hasSize(2);

        // 4. Reembolso desde el backoffice: se llama a la API de refunds de Stripe con el PaymentIntent.
        assertStatus(api.post("/api/orders/" + orderId + "/refund", admin, "{\"reason\":\"Prueba Stripe\"}"), 200);
        var refund = STRIPE.requests("/v1/refunds").getLast();
        assertThat(refund.form()).containsEntry("payment_intent", "pi_test_123").containsEntry("amount", "2500");
        var detail = api.get("/api/orders/" + orderId, admin);
        assertThat((String) detail.json("$.payments[0].status")).isEqualTo("REFUNDED");
    }

    @Test
    void paymentArrivingAfterOrderClosedIsRefundedAutomatically() {
        var api = api();
        String admin = api.login(data.createTenantWithAdmin("Stripe tarde").adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "10.00", 50, 5);
        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        String orderId = data.checkout(api, customer, """
                {"sessionId":"%s","items":[{"ticketTypeId":"%s","quantity":1}]}
                """.formatted(event.sessionId(), event.ticketTypeId())).json("$.id");
        String url = api.post("/api/checkout/orders/" + orderId + "/stripe-session", customer, null).json("$.checkoutUrl");
        String sessionId = url.substring("https://checkout.stripe.test/".length());

        // El cliente cancela el pedido, pero Stripe llega a cobrar.
        assertStatus(api.post("/api/checkout/orders/" + orderId + "/cancel", customer, null), 200);
        int refundsBefore = STRIPE.requests("/v1/refunds").size();
        String completed = completedEvent("evt_" + UUID.randomUUID(), sessionId, "pi_test_late");
        assertStatus(postWebhook(api, completed, signature(completed, now())), 200);

        assertThat(STRIPE.requests("/v1/refunds")).hasSize(refundsBefore + 1);
        assertThat(STRIPE.requests("/v1/refunds").getLast().form()).containsEntry("payment_intent", "pi_test_late");
        String paymentStatus = jdbc.sql("SELECT status FROM payments WHERE provider_payment_id = :id")
                .param("id", sessionId).query(String.class).single();
        assertThat(paymentStatus).isEqualTo("REFUNDED");
        assertThat((List<?>) api.get("/api/me/tickets", customer).json("$")).isEmpty();
    }

    private static ApiClient.Response postWebhook(ApiClient api, String payload, String signature) {
        return api.postWithHeader("/api/payments/stripe/webhook", null, payload, "Stripe-Signature", signature);
    }

    private static String completedEvent(String eventId, String sessionId, String paymentIntent) {
        return """
                {"id":"%s","type":"checkout.session.completed","data":{"object":{"id":"%s","object":"checkout.session",
                "payment_status":"paid","payment_intent":"%s"}}}""".formatted(eventId, sessionId, paymentIntent);
    }

    /** Firma como lo hace Stripe: HMAC-SHA256(secreto, t + "." + cuerpo). */
    private static String signature(String payload, long timestamp) {
        return "t=" + timestamp + ",v1=" + StripeSignatureVerifier.sign(timestamp + "." + payload, WEBHOOK_SECRET);
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }
}
