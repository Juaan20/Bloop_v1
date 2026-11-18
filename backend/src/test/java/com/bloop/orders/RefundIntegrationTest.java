package com.bloop.orders;

import com.bloop.shared.enums.RoleCode;
import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.bloop.support.TestDataFactory.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 4: reembolsos manuales y cancelacion de eventos con devolucion automatica.
 */
class RefundIntegrationTest extends AbstractIntegrationTest {

    @Test
    void staffRefundCancelsTicketsReleasesStockAndIsIdempotent() {
        var api = api();
        var tenant = data.createTenantWithAdmin("Reembolsos");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "20.00", 50, 5);
        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        String orderId = data.buyTickets(api, customer, event, 2);
        String qr = (String) ((List<?>) api.get("/api/me/tickets", customer).json("$[*].qrPayload")).getFirst();

        // Sin motivo no se puede reembolsar.
        assertThat(api.post("/api/orders/" + orderId + "/refund", admin, "{\"reason\":\"\"}").status()).isEqualTo(400);

        var refund = api.post("/api/orders/" + orderId + "/refund", admin, "{\"reason\":\"El cliente no puede asistir\"}");
        assertStatus(refund, 200);
        assertThat((String) refund.json("$.status")).isEqualTo("REFUNDED");
        assertThat((String) refund.json("$.refundReason")).isEqualTo("El cliente no puede asistir");

        // Las entradas quedan anuladas y el QR ya no permite entrar.
        List<String> statuses = api.get("/api/me/tickets", customer).json("$[*].status");
        assertThat(statuses).containsOnly("CANCELLED");
        String door = api.login(data.createStaff(tenant.tenantId(), RoleCode.DOOR_STAFF,
                Set.of(UUID.fromString(event.venueId()))), TestDataFactory.PASSWORD);
        var scan = api.post("/api/access/validate", door,
                "{\"qrPayload\":\"%s\",\"sessionId\":\"%s\"}".formatted(qr, event.sessionId()));
        assertThat((String) scan.json("$.result")).isEqualTo("CANCELLED");

        // El stock vuelve a estar disponible y el pago consta como reembolsado.
        var detail = api.get("/api/events/" + event.eventId(), admin);
        assertThat((Integer) detail.json("$.sessions[0].ticketTypes[0].quantitySold")).isZero();
        var order = api.get("/api/orders/" + orderId, admin);
        assertThat((String) order.json("$.payments[0].status")).isEqualTo("REFUNDED");

        // Repetir el reembolso no devuelve el dinero dos veces.
        var again = api.post("/api/orders/" + orderId + "/refund", admin, "{\"reason\":\"Otra vez\"}");
        assertThat((String) again.json("$.refundReason")).isEqualTo("El cliente no puede asistir");
        assertThat(api.get("/api/audit?action=ORDER_REFUNDED", admin).json("$.totalElements").toString()).isEqualTo("1");

        // El dashboard ya no cuenta ese ingreso.
        assertThat(api.get("/api/dashboard", admin).json("$.kpis.revenue").toString()).isEqualTo("0");
    }

    @Test
    void refundRequiresPermissionAndOwnTenantAndUnusedTickets() {
        var api = api();
        var tenant = data.createTenantWithAdmin("Permisos reembolso");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "15.00", 50, 5);
        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        String orderId = data.buyTickets(api, customer, event, 1);
        String body = "{\"reason\":\"Prueba\"}";

        String door = api.login(data.createStaff(tenant.tenantId(), RoleCode.DOOR_STAFF,
                Set.of(UUID.fromString(event.venueId()))), TestDataFactory.PASSWORD);
        assertThat(api.post("/api/orders/" + orderId + "/refund", door, body).status()).isEqualTo(403);
        assertThat(api.post("/api/orders/" + orderId + "/refund", customer, body).status()).isEqualTo(403);
        String otherAdmin = api.login(data.createTenantWithAdmin("Otra").adminEmail(), TestDataFactory.PASSWORD);
        assertThat(api.post("/api/orders/" + orderId + "/refund", otherAdmin, body).status()).isEqualTo(404);

        // Con la entrada ya usada en la puerta, no se puede reembolsar.
        String qr = (String) ((List<?>) api.get("/api/me/tickets", customer).json("$[*].qrPayload")).getFirst();
        assertStatus(api.post("/api/access/validate", door,
                "{\"qrPayload\":\"%s\",\"sessionId\":\"%s\"}".formatted(qr, event.sessionId())), 200);
        assertThat(api.post("/api/orders/" + orderId + "/refund", admin, body).status()).isEqualTo(409);
    }

    @Test
    void cancellingAnEventRefundsAllPaidOrders() {
        var api = api();
        var tenant = data.createTenantWithAdmin("Cancelacion");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "30.00", 50, 5);
        String c1 = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        String c2 = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        String order1 = data.buyTickets(api, c1, event, 2);
        String order2 = data.buyTickets(api, c2, event, 1);

        assertStatus(api.post("/api/events/" + event.eventId() + "/cancel", admin, null), 200);

        for (String orderId : List.of(order1, order2)) {
            var order = api.get("/api/orders/" + orderId, admin);
            assertThat((String) order.json("$.order.status")).isEqualTo("REFUNDED");
            assertThat((String) order.json("$.order.refundReason")).contains("cancelado");
        }
        assertThat((List<?>) api.get("/api/me/tickets", c1).json("$[?(@.status == 'VALID')]")).isEmpty();
        assertThat(api.get("/api/audit?action=ORDER_REFUNDED", admin).json("$.totalElements").toString()).isEqualTo("2");
    }
}
