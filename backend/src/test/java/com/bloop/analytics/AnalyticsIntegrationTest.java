package com.bloop.analytics;

import com.bloop.shared.enums.RoleCode;
import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.ApiClient;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.bloop.support.TestDataFactory.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;

/** Fase 4: analitica avanzada (entradas + TPV + reembolsos) e informes CSV con datos reales. */
class AnalyticsIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Madrid"));

    @Test
    void overviewCombinesTicketsRefundsAndPosSales() {
        var api = api();
        var tenant = data.createTenantWithAdmin("Analitica");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "20.00", 100, 5);
        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        data.buyTickets(api, customer, event, 2);                       // 40 €
        String refunded = data.buyTickets(api, customer, event, 1);     // 20 €, se reembolsa
        assertStatus(api.post("/api/orders/" + refunded + "/refund", admin, "{\"reason\":\"Prueba\"}"), 200);

        // TPV: 3 cervezas (3 €) + 1 mojito (9 €) = 18 € en efectivo, y 1 mojito con tarjeta (9 €).
        String[] pos = posSetup(api, admin, event.venueId());
        String terminalId = pos[0];
        String[] variants = {pos[1], pos[2]};
        sell(api, admin, terminalId, """
                {"items":[{"variantId":"%s","quantity":3},{"variantId":"%s","quantity":1}],
                 "paymentMethod":"CASH","cashReceived":20}""".formatted(variants[0], variants[1]));
        sell(api, admin, terminalId, """
                {"items":[{"variantId":"%s","quantity":1}],"paymentMethod":"CARD"}""".formatted(variants[1]));

        var overview = api.get("/api/analytics?from=%s&to=%s".formatted(TODAY.minusDays(1), TODAY), admin);
        assertStatus(overview, 200);
        assertThat(overview.json("$.summary.ticketRevenue").toString()).isEqualTo("40.0");
        assertThat((Integer) overview.json("$.summary.ticketsSold")).isEqualTo(2);
        assertThat(overview.json("$.summary.refundedAmount").toString()).isEqualTo("20.0");
        assertThat((Integer) overview.json("$.summary.refundedOrders")).isEqualTo(1);
        assertThat(overview.json("$.summary.posRevenue").toString()).isEqualTo("27.0");
        assertThat((Integer) overview.json("$.summary.posSales")).isEqualTo(2);
        assertThat(overview.json("$.summary.totalRevenue").toString()).isEqualTo("67.0");
        assertThat((String) overview.json("$.topProducts[0].productName")).isEqualTo("Cerveza");
        assertThat((Integer) overview.json("$.topProducts[0].quantity")).isEqualTo(3);
        List<String> methods = overview.json("$.posByPaymentMethod[*].paymentMethod");
        assertThat(methods).containsExactlyInAnyOrder("CASH", "CARD");
        assertThat((String) overview.json("$.topEvents[0].eventId")).isEqualTo(event.eventId());

        // Informe CSV de pedidos: formato Excel en espanol y protegido.
        var csv = api.get("/api/analytics/reports/orders.csv?from=%s&to=%s".formatted(TODAY, TODAY), admin);
        assertStatus(csv, 200);
        assertThat(csv.header("Content-Type")).startsWith("text/csv");
        assertThat(csv.header("Content-Disposition")).contains("attachment").contains("bloop-pedidos-");
        assertThat(csv.body()).startsWith("﻿Referencia;Estado;").contains(";REFUNDED;").contains(";PAID;")
                .contains("20,00");
        var products = api.get("/api/analytics/reports/products.csv?from=%s&to=%s".formatted(TODAY, TODAY), admin);
        assertThat(products.body()).contains("Cerveza;Cana;3;9,00");
        assertThat(api.get("/api/audit?action=REPORT_EXPORTED", admin).json("$.totalElements").toString()).isEqualTo("2");
    }

    @Test
    void analyticsIsProtectedAndScopedToTenant() {
        var api = api();
        var tenant = data.createTenantWithAdmin("Analitica permisos");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        String range = "from=%s&to=%s".formatted(TODAY, TODAY);
        String door = api.login(data.createStaff(tenant.tenantId(), RoleCode.DOOR_STAFF, Set.of()),
                TestDataFactory.PASSWORD);
        assertThat(api.get("/api/analytics?" + range, door).status()).isEqualTo(403);
        assertThat(api.get("/api/analytics/reports/orders.csv?" + range, door).status()).isEqualTo(403);

        // Un local de otra empresa no se puede usar como filtro.
        String otherAdmin = api.login(data.createTenantWithAdmin("Otra analitica").adminEmail(), TestDataFactory.PASSWORD);
        String otherVenue = data.createVenue(api, otherAdmin, 100);
        assertThat(api.get("/api/analytics?" + range + "&venueId=" + otherVenue, admin).status()).isEqualTo(404);

        // Periodo invalido, informe inexistente y datos vacios.
        assertThat(api.get("/api/analytics?from=%s&to=%s".formatted(TODAY, TODAY.minusDays(1)), admin).status())
                .isEqualTo(400);
        assertThat(api.get("/api/analytics/reports/secreto.csv?" + range, admin).status()).isEqualTo(404);
        var empty = api.get("/api/analytics?" + range, admin);
        assertThat(empty.json("$.summary.totalRevenue").toString()).isEqualTo("0");
    }

    // ---------------------------------------------------------------- TPV minimo para el test

    /** Devuelve {terminal, variante cana, variante copa de mojito}. */
    private String[] posSetup(ApiClient api, String admin, String venueId) {
        String warehouseId = api.post("/api/inventory/warehouses", admin,
                "{\"venueId\":\"%s\",\"name\":\"Barra\",\"active\":true}".formatted(venueId)).json("$.id");
        String categoryId = api.post("/api/pos/categories", admin,
                "{\"name\":\"Bebidas\",\"color\":\"amber\",\"sortOrder\":0,\"active\":true}").json("$.id");
        var beer = api.post("/api/pos/products", admin, """
                {"categoryId":"%s","name":"Cerveza","trackStock":false,"sortOrder":0,"active":true,
                 "variants":[{"name":"Cana","price":3.00,"sortOrder":0,"active":true}]}""".formatted(categoryId));
        assertStatus(beer, 201);
        var mojito = api.post("/api/pos/products", admin, """
                {"categoryId":"%s","name":"Mojito","trackStock":false,"sortOrder":1,"active":true,
                 "variants":[{"name":"Copa","price":9.00,"sortOrder":0,"active":true}]}""".formatted(categoryId));
        assertStatus(mojito, 201);
        String terminalId = api.post("/api/pos/terminals", admin, """
                {"name":"Barra 1","venueId":"%s","warehouseId":"%s","type":"BAR","active":true}
                """.formatted(venueId, warehouseId)).json("$.id");
        assertStatus(api.post("/api/pos/terminals/" + terminalId + "/cash-sessions", admin, "{\"openingFloat\":50}"), 201);
        return new String[]{terminalId, beer.json("$.variants[0].id"), mojito.json("$.variants[0].id")};
    }

    private void sell(ApiClient api, String token, String terminalId, String body) {
        assertStatus(api.postWithHeader("/api/pos/terminals/" + terminalId + "/sales", token, body, "Idempotency-Key",
                "pos-" + UUID.randomUUID()), 201);
    }
}
