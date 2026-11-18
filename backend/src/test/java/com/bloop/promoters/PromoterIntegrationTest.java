package com.bloop.promoters;

import com.bloop.shared.enums.RoleCode;
import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.ApiClient;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.bloop.support.TestDataFactory.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;

/** Promotores / RRPP: atribucion de ventas, comisiones, objetivos, panel y codigos promocionales. */
class PromoterIntegrationTest extends AbstractIntegrationTest {

    @Test
    void referralLinkAttributesSalesOnlyForAssignedEvents() {
        var api = api();
        var tenant = data.createTenantWithAdmin("RRPP");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var assigned = data.createPublishedEvent(api, admin, "20.00", 100, 10);
        var notAssigned = data.createPublishedEvent(api, admin, "20.00", 100, 10);
        String promoterEmail = data.createStaff(tenant.tenantId(), RoleCode.PROMOTER, Set.of());
        String code = "RRPP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String promoterId = createPromoter(api, admin, promoterEmail, code, "PERCENTAGE", "10");

        var assignments = api.put("/api/promoters/" + promoterId + "/events", admin, """
                {"assignments":[{"eventId":"%s","targetTickets":4}]}""".formatted(assigned.eventId()));
        assertStatus(assignments, 200);
        assertThat((String) assignments.json("$.events[0].salesLink")).endsWith("?rrpp=" + code);

        // Venta con el enlace (en minusculas: el codigo se normaliza) en el evento asignado.
        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        String orderId = buyWith(api, customer, assigned, 2, "\"referralCode\":\"" + code.toLowerCase() + "\"");
        // Mismo enlace en un evento NO asignado: la venta no se atribuye.
        String other = buyWith(api, customer, notAssigned, 1, "\"referralCode\":\"" + code + "\"");
        // Venta sin enlace
        data.buyTickets(api, api.login(data.createCustomer(), TestDataFactory.PASSWORD), assigned, 1);

        assertThat((String) api.get("/api/orders/" + orderId, admin).json("$.promoterName")).isNotNull();
        assertThat(((Number) api.get("/api/orders/" + orderId, admin).json("$.commissionAmount")).doubleValue())
                .isEqualTo(4.0);
        assertThat((Object) api.get("/api/orders/" + other, admin).json("$.promoterName")).isNull();

        // Panel individual del promotor
        String promoter = api.login(promoterEmail, TestDataFactory.PASSWORD);
        var panel = api.get("/api/promoter/me", promoter);
        assertStatus(panel, 200);
        assertThat(((Number) panel.json("$.promoter.ticketsSold")).intValue()).isEqualTo(2);
        assertThat(((Number) panel.json("$.promoter.revenue")).doubleValue()).isEqualTo(40.0);
        assertThat(((Number) panel.json("$.promoter.commission")).doubleValue()).isEqualTo(4.0);
        assertThat((Integer) panel.json("$.events[0].targetProgress")).isEqualTo(50);

        // El promotor no gestiona promotores, y otra empresa no ve este promotor
        assertThat(api.get("/api/promoters", promoter).status()).isEqualTo(403);
        String otherAdmin = api.login(data.createTenantWithAdmin("RRPP otra").adminEmail(), TestDataFactory.PASSWORD);
        assertThat(api.get("/api/promoters/" + promoterId, otherAdmin).status()).isEqualTo(404);
    }

    @Test
    void promoCodeAppliesDiscountAndRespectsUsageLimit() {
        var api = api();
        var tenant = data.createTenantWithAdmin("Codigos");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "20.00", 100, 10);
        String code = "FIESTA-" + UUID.randomUUID().toString().substring(0, 4);
        var created = api.post("/api/promo-codes", admin, """
                {"code":"%s","discountType":"PERCENTAGE","discountValue":25,"maxUses":1,"active":true}
                """.formatted(code));
        assertStatus(created, 201);
        assertThat((String) created.json("$.code")).isEqualTo(code.toUpperCase());

        String customerA = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        var orderA = checkoutWith(api, customerA, event, 2, "\"promoCode\":\"" + code.toLowerCase() + "\"");
        assertStatus(orderA, 201);
        assertThat(((Number) orderA.json("$.subtotalAmount")).doubleValue()).isEqualTo(40.0);
        assertThat(((Number) orderA.json("$.discountAmount")).doubleValue()).isEqualTo(10.0);
        assertThat(((Number) orderA.json("$.totalAmount")).doubleValue()).isEqualTo(30.0);

        // Solo tenia un uso
        String customerB = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        assertThat(checkoutWith(api, customerB, event, 1, "\"promoCode\":\"" + code + "\"").status()).isEqualTo(409);
        // Si A abandona su pedido, el uso se devuelve
        assertStatus(api.post("/api/checkout/orders/" + orderA.json("$.id") + "/cancel", customerA, null), 200);
        assertThat(checkoutWith(api, customerB, event, 1, "\"promoCode\":\"" + code + "\"").status()).isEqualTo(201);

        // Codigo inexistente, de otra empresa o de otro evento: 400
        assertThat(checkoutWith(api, customerA, event, 1, "\"promoCode\":\"NOEXISTE\"").status()).isEqualTo(400);
        String otherAdmin = api.login(data.createTenantWithAdmin("Codigos otra").adminEmail(),
                TestDataFactory.PASSWORD);
        assertStatus(api.post("/api/promo-codes", otherAdmin, """
                {"code":"AJENO1","discountType":"PERCENTAGE","discountValue":50,"active":true}"""), 201);
        assertThat(checkoutWith(api, customerA, event, 1, "\"promoCode\":\"AJENO1\"").status()).isEqualTo(400);
        var otherEvent = data.createPublishedEvent(api, admin, "20.00", 100, 10);
        assertStatus(api.post("/api/promo-codes", admin, """
                {"code":"SOLOOTRO","discountType":"PERCENTAGE","discountValue":50,"eventId":"%s","active":true}
                """.formatted(otherEvent.eventId())), 201);
        assertThat(checkoutWith(api, customerA, event, 1, "\"promoCode\":\"SOLOOTRO\"").status()).isEqualTo(400);

        // Un descuento mayor que el importe deja el pedido a 0: se confirma sin pago.
        assertStatus(api.post("/api/promo-codes", admin, """
                {"code":"INVITADO","discountType":"FIXED_AMOUNT","discountValue":1000,"active":true}"""), 201);
        var free = checkoutWith(api, customerA, event, 1, "\"promoCode\":\"INVITADO\"");
        assertThat(((Number) free.json("$.totalAmount")).doubleValue()).isZero();
        assertThat((String) free.json("$.status")).isEqualTo("PAID");
    }

    @Test
    void promoterPromoCodeAttributesTheSale() {
        var api = api();
        var tenant = data.createTenantWithAdmin("Codigo RRPP");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "30.00", 100, 10);
        String promoterEmail = data.createStaff(tenant.tenantId(), RoleCode.PROMOTER, Set.of());
        String promoterId = createPromoter(api, admin, promoterEmail, null, "FIXED_PER_TICKET", "1.50");
        assertStatus(api.post("/api/promo-codes", admin, """
                {"code":"LAURA5","discountType":"FIXED_AMOUNT","discountValue":5,"promoterId":"%s","active":true}
                """.formatted(promoterId)), 201);

        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        String orderId = buyWith(api, customer, event, 2, "\"promoCode\":\"LAURA5\"");

        var detail = api.get("/api/orders/" + orderId, admin);
        assertThat(((Number) detail.json("$.order.totalAmount")).doubleValue()).isEqualTo(55.0);
        assertThat(((Number) detail.json("$.commissionAmount")).doubleValue()).isEqualTo(3.0);
        var promoter = api.get("/api/promoters/" + promoterId, admin);
        assertThat(((Number) promoter.json("$.promoter.ticketsSold")).intValue()).isEqualTo(2);
        assertThat((List<?>) promoter.json("$.promoCodes")).hasSize(1);
    }

    @Test
    void onlyPromoterUsersCanBeRegisteredAndPanelRequiresConfiguration() {
        var api = api();
        var tenant = data.createTenantWithAdmin("RRPP alta");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        String promoterEmail = data.createStaff(tenant.tenantId(), RoleCode.PROMOTER, Set.of());
        String promoter = api.login(promoterEmail, TestDataFactory.PASSWORD);

        // Sin ficha de promotor todavia
        assertThat(api.get("/api/promoter/me", promoter).status()).isEqualTo(404);
        // Un portero no tiene panel de promotor
        String door = api.login(data.createStaff(tenant.tenantId(), RoleCode.DOOR_STAFF, Set.of()),
                TestDataFactory.PASSWORD);
        assertThat(api.get("/api/promoter/me", door).status()).isEqualTo(403);
        // Comision porcentual > 100: 400
        String userId = candidateId(api, admin, promoterEmail);
        assertThat(api.post("/api/promoters", admin, """
                {"userId":"%s","commissionType":"PERCENTAGE","commissionValue":150}""".formatted(userId)).status())
                .isEqualTo(400);

        createPromoter(api, admin, promoterEmail, null, "PERCENTAGE", "5");
        assertThat(api.get("/api/promoter/me", promoter).status()).isEqualTo(200);
        // Ya no aparece como candidato y no se puede dar de alta dos veces
        assertThat(api.get("/api/promoters/candidates", admin).body()).doesNotContain(promoterEmail);
        assertThat(api.post("/api/promoters", admin, """
                {"userId":"%s","commissionType":"PERCENTAGE","commissionValue":5}""".formatted(userId)).status())
                .isEqualTo(409);
    }

    // ----------------------------------------------------------------- Auxiliares

    private String candidateId(ApiClient api, String admin, String email) {
        List<Map<String, Object>> candidates = api.get("/api/promoters/candidates", admin).json("$");
        return candidates.stream().filter(c -> email.equals(c.get("email")))
                .map(c -> (String) c.get("userId")).findFirst().orElseThrow();
    }

    private String createPromoter(ApiClient api, String admin, String email, String code, String type,
                                  String value) {
        String codeJson = code == null ? "" : "\"referralCode\":\"" + code + "\",";
        var response = api.post("/api/promoters", admin, """
                {"userId":"%s",%s"commissionType":"%s","commissionValue":%s}
                """.formatted(candidateId(api, admin, email), codeJson, type, value));
        assertStatus(response, 201);
        return response.json("$.promoter.id");
    }

    private ApiClient.Response checkoutWith(ApiClient api, String customer, TestDataFactory.PublishedEvent event,
                                            int quantity, String extraJson) {
        return data.checkout(api, customer, """
                {"sessionId":"%s","items":[{"ticketTypeId":"%s","quantity":%d}],%s}
                """.formatted(event.sessionId(), event.ticketTypeId(), quantity, extraJson));
    }

    private String buyWith(ApiClient api, String customer, TestDataFactory.PublishedEvent event, int quantity,
                           String extraJson) {
        var order = checkoutWith(api, customer, event, quantity, extraJson);
        assertStatus(order, 201);
        String orderId = order.json("$.id");
        data.pay(api, customer, orderId);
        return orderId;
    }
}
