package com.bloop.payments.service;

import com.bloop.payments.entity.Payment;
import com.bloop.payments.entity.PaymentProviderType;
import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.ForbiddenException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;

/**
 * Stripe Checkout en modo test.
 *
 * 1. createPayment() crea una "Checkout Session": Stripe devuelve una URL donde el cliente
 *    introduce la tarjeta (Bloop nunca ve los datos de la tarjeta).
 * 2. Al terminar, Stripe avisa a POST /api/payments/stripe/webhook con el evento
 *    checkout.session.completed, firmado con el secreto del webhook.
 * 3. refund() devuelve el dinero del PaymentIntent asociado.
 */
@Component
public class StripePaymentProvider implements PaymentProvider {

    private final StripeApiClient api;
    private final BloopProperties.Stripe config;
    private final Clock clock;

    public StripePaymentProvider(StripeApiClient api, BloopProperties properties, Clock clock) {
        this.api = api;
        this.config = properties.payments().stripe();
        this.clock = clock;
    }

    /** Stripe solo funciona si esta activado y tiene clave secreta de TEST y secreto de webhook. */
    public boolean isEnabled() {
        return config.enabled()
                && config.secretKey() != null && config.secretKey().startsWith("sk_test_")
                && config.webhookSecret() != null && !config.webhookSecret().isBlank();
    }

    /** Instante en que caducara la pagina de pago de Stripe que se cree ahora. */
    public Instant sessionExpiry() {
        return Instant.now(clock).plus(config.sessionMinutes(), ChronoUnit.MINUTES);
    }

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.STRIPE;
    }

    @Override
    public ProviderCheckout createPayment(PaymentRequest request) {
        assertEnabled();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("client_reference_id", request.orderId().toString());
        form.add("metadata[order_id]", request.orderId().toString());
        form.add("metadata[order_reference]", request.orderReference());
        form.add("success_url", request.successUrl());
        form.add("cancel_url", request.cancelUrl());
        form.add("expires_at", Long.toString(sessionExpiry().getEpochSecond()));
        if (request.customerEmail() != null) {
            form.add("customer_email", request.customerEmail());
        }
        // Una unica linea con el total del pedido (los descuentos ya estan aplicados).
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", request.currency().toLowerCase(Locale.ROOT));
        form.add("line_items[0][price_data][unit_amount]", Long.toString(toCents(request.amount())));
        form.add("line_items[0][price_data][product_data][name]", request.description());

        Map<String, Object> session = api.createCheckoutSession(form);
        Object id = session.get("id");
        Object url = session.get("url");
        if (id == null || url == null) {
            throw new BadRequestException("Stripe no ha devuelto una sesion de pago valida");
        }
        return new ProviderCheckout(id.toString(), url.toString());
    }

    @Override
    public String refund(Payment payment, BigDecimal amount) {
        assertEnabled();
        if (payment.getProviderReference() == null) {
            throw new ConflictException("El pago de Stripe no tiene PaymentIntent asociado; reembolsalo desde Stripe");
        }
        Map<String, Object> refund = api.createRefund(payment.getProviderReference(), toCents(amount));
        return String.valueOf(refund.get("id"));
    }

    /** Stripe trabaja con enteros en la unidad minima de la moneda (centimos). */
    public static long toCents(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private void assertEnabled() {
        if (!isEnabled()) {
            throw new ForbiddenException("El pago con tarjeta (Stripe) no esta configurado en este entorno");
        }
    }
}
