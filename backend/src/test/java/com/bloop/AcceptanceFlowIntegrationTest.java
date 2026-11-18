package com.bloop;

import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.bloop.support.TestDataFactory.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flujo de aceptacion obligatorio de la FASE 1, de extremo a extremo y por HTTP:
 * empresa -> local -> evento -> entradas -> publicacion -> compra -> QR -> validacion -> ventas.
 */
class AcceptanceFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void fullPhaseOneFlow() {
        var api = api();

        // 1. Un super administrador crea una empresa con su administrador.
        String superToken = api.login(data.createSuperAdmin(), TestDataFactory.PASSWORD);
        String adminEmail = TestDataFactory.uniqueEmail("owner");
        var tenant = api.post("/api/admin/tenants", superToken, """
                {"name":"Club Aceptacion","contactEmail":"club@test.local","adminFirstName":"Ada",
                 "adminLastName":"Admin","adminEmail":"%s","adminPassword":"Password123!"}
                """.formatted(adminEmail));
        assertStatus(tenant, 201);
        String tenantId = tenant.json("$.id");

        // 2. El administrador inicia sesion y crea un establecimiento.
        String adminToken = api.login(adminEmail, "Password123!");
        String venueId = data.createVenue(api, adminToken, 300);

        // 3. Crea un evento (en borrador) con su primera sesion.
        // Empieza en 2 horas: el control de acceso muestra las sesiones de las proximas 24 h.
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
        var event = api.post("/api/events", adminToken, """
                {"venueId":"%s","name":"Fiesta de Aceptacion","minAge":18,
                 "firstSession":{"startsAt":"%s","endsAt":"%s","capacity":300}}
                """.formatted(venueId, start, start.plus(6, ChronoUnit.HOURS)));
        assertStatus(event, 201);
        assertThat((String) event.json("$.status")).isEqualTo("DRAFT");
        String eventId = event.json("$.id");
        String sessionId = event.json("$.sessions[0].id");
        String slug = event.json("$.slug");

        // Un evento sin entradas no se puede publicar.
        assertThat(api.post("/api/events/" + eventId + "/publish", adminToken, null).status()).isEqualTo(400);

        // 4. Configura los tipos de entrada.
        var general = api.post("/api/events/%s/sessions/%s/ticket-types".formatted(eventId, sessionId), adminToken, """
                {"name":"General","category":"GENERAL","price":15.50,"quantityTotal":200,"maxPerOrder":6,
                 "active":true,"sortOrder":0}
                """);
        assertStatus(general, 201);
        String generalId = general.json("$.id");
        // La suma de entradas no puede superar el aforo de la sesion (300).
        var tooMany = api.post("/api/events/%s/sessions/%s/ticket-types".formatted(eventId, sessionId), adminToken, """
                {"name":"VIP","category":"VIP","price":40,"quantityTotal":150,"maxPerOrder":4,"active":true,"sortOrder":1}
                """);
        assertThat(tooMany.status()).isEqualTo(400);

        // 5. Publica el evento.
        var published = api.post("/api/events/" + eventId + "/publish", adminToken, null);
        assertStatus(published, 200);
        assertThat((String) published.json("$.status")).isEqualTo("PUBLISHED");

        // 6. Un cliente accede a la web publica y ve el evento.
        var publicList = api.get("/api/public/events?search=Aceptacion", null);
        assertStatus(publicList, 200);
        List<String> slugs = publicList.json("$.content[*].eventSlug");
        assertThat(slugs).contains(slug);
        var detail = api.get("/api/public/events/" + slug, null);
        assertThat((Integer) detail.json("$.sessions[0].ticketTypes[0].available")).isEqualTo(200);

        // 7. El cliente se registra y compra 2 entradas.
        String customerEmail = TestDataFactory.uniqueEmail("comprador");
        var register = api.post("/api/auth/register", null, """
                {"email":"%s","password":"Password123!","firstName":"Carla","lastName":"Compradora","acceptPrivacy":true}
                """.formatted(customerEmail));
        String customerToken = register.json("$.accessToken");
        String key = "accept-" + UUID.randomUUID();
        // El evento es +18: cada asistente indica fecha de nacimiento y documento (V12).
        var order = api.postWithHeader("/api/checkout", customerToken, """
                {"sessionId":"%s","items":[{"ticketTypeId":"%s","quantity":2}],
                 "attendees":[
                   {"ticketTypeId":"%s","firstName":"Carla","lastName":"Compradora","birthDate":"1995-04-10",
                    "documentType":"DNI","documentNumber":"12345678Z"},
                   {"ticketTypeId":"%s","firstName":"Iker","lastName":"Amigo","birthDate":"1996-09-01",
                    "documentType":"NIE","documentNumber":"X1234567L"}]}
                """.formatted(sessionId, generalId, generalId, generalId), "Idempotency-Key", key);
        assertStatus(order, 201);
        assertThat((String) order.json("$.status")).isEqualTo("PENDING_PAYMENT");
        assertThat(order.json("$.totalAmount").toString()).isEqualTo("31.0");
        String orderId = order.json("$.id");

        // Reintento con la misma Idempotency-Key: mismo pedido, sin reservar mas stock.
        var retry = api.postWithHeader("/api/checkout", customerToken, """
                {"sessionId":"%s","items":[{"ticketTypeId":"%s","quantity":2}]}
                """.formatted(sessionId, generalId), "Idempotency-Key", key);
        assertThat((String) retry.json("$.id")).isEqualTo(orderId);

        // 8. Pago (pasarela simulada) -> el sistema registra el pedido como pagado.
        var payment = api.post("/api/checkout/orders/" + orderId + "/simulated-payment", customerToken,
                "{\"approve\":true}");
        assertStatus(payment, 200);
        assertThat((Boolean) payment.json("$.approved")).isTrue();
        assertThat((String) payment.json("$.order.status")).isEqualTo("PAID");
        // Pagar dos veces no duplica nada.
        var payAgain = api.post("/api/checkout/orders/" + orderId + "/simulated-payment", customerToken,
                "{\"approve\":true}");
        assertThat((String) payAgain.json("$.message")).contains("ya estaba pagado");

        // 9-10. Se generan los QR y el cliente consulta sus entradas.
        var tickets = api.get("/api/me/tickets", customerToken);
        assertStatus(tickets, 200);
        List<String> payloads = tickets.json("$[*].qrPayload");
        assertThat(payloads).hasSize(2).allMatch(p -> p.startsWith("VF1."));
        assertThat(api.get("/api/me/orders", customerToken).body()).contains(orderId);

        // 11-13. Un empleado de puerta (asignado al local) escanea el QR.
        String doorEmail = data.createStaff(UUID.fromString(tenantId), com.bloop.shared.enums.RoleCode.DOOR_STAFF,
                java.util.Set.of(UUID.fromString(venueId)));
        String doorToken = api.login(doorEmail, TestDataFactory.PASSWORD);
        var sessions = api.get("/api/access/sessions", doorToken);
        assertThat(sessions.body()).contains(sessionId);

        String validateBody = "{\"qrPayload\":\"%s\",\"sessionId\":\"%s\"}";
        var first = api.post("/api/access/validate", doorToken, validateBody.formatted(payloads.get(0), sessionId));
        assertStatus(first, 200);
        assertThat((String) first.json("$.result")).isEqualTo("ACCEPTED");
        var second = api.post("/api/access/validate", doorToken, validateBody.formatted(payloads.get(0), sessionId));
        assertThat((String) second.json("$.result")).isEqualTo("ALREADY_USED");
        var forged = api.post("/api/access/validate", doorToken,
                validateBody.formatted(payloads.get(1).substring(0, payloads.get(1).length() - 2) + "xx", sessionId));
        assertThat((String) forged.json("$.result")).isEqualTo("INVALID_CODE");

        var history = api.get("/api/access/sessions/" + sessionId + "/validations", doorToken);
        assertThat((Integer) history.json("$.totalElements")).isEqualTo(3);

        // 14. El administrador consulta ventas y asistencia.
        var orders = api.get("/api/orders?eventId=" + eventId, adminToken);
        assertThat((Integer) orders.json("$.totalElements")).isEqualTo(1);
        var dashboard = api.get("/api/dashboard", adminToken);
        assertStatus(dashboard, 200);
        assertThat(dashboard.json("$.kpis.revenue").toString()).isEqualTo("31.0");
        assertThat((Integer) dashboard.json("$.kpis.ticketsSold")).isEqualTo(2);
        assertThat((Integer) dashboard.json("$.kpis.checkedIn")).isEqualTo(1);
        var detailAfter = api.get("/api/events/" + eventId, adminToken);
        assertThat((Integer) detailAfter.json("$.sessions[0].ticketsSold")).isEqualTo(2);

        // La auditoria registra las operaciones economicas y de acceso.
        var audit = api.get("/api/audit", adminToken);
        assertThat(audit.body()).contains("EVENT_PUBLISHED").contains("ORDER_PAID").contains("TICKET_VALIDATED");
    }
}
