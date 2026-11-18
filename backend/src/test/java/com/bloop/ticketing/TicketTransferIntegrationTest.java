package com.bloop.ticketing;

import com.bloop.shared.enums.RoleCode;
import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.bloop.support.TestDataFactory.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;

/** Cambio de titular, transferencia con QR nuevo, reenvio de entradas y avisos de cambios del evento. */
class TicketTransferIntegrationTest extends AbstractIntegrationTest {

    @Test
    void ticketCanBeRenamedAndTransferredAndTheOldQrStopsWorking() {
        var api = api();
        var tenant = data.createTenantWithAdmin("Transferencias");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "10.00", 50, 5);
        String door = api.login(data.createStaff(tenant.tenantId(), RoleCode.DOOR_STAFF,
                Set.of(UUID.fromString(event.venueId()))), TestDataFactory.PASSWORD);

        String sender = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        data.buyTickets(api, sender, event, 1);
        String ticketId = api.get("/api/me/tickets", sender).json("$[0].id");
        String oldQr = api.get("/api/me/tickets", sender).json("$[0].qrPayload");

        var renamed = api.put("/api/me/tickets/" + ticketId + "/holder", sender, "{\"holderName\":\"Pepe Amigo\"}");
        assertThat((String) renamed.json("$.holderName")).isEqualTo("Pepe Amigo");
        assertThat((String) renamed.json("$.qrPayload")).isEqualTo(oldQr);

        String transfer = "/api/me/tickets/" + ticketId + "/transfer";
        assertThat(api.post(transfer, sender, "{\"recipientEmail\":\"nadie@nowhere.test\"}").status()).isEqualTo(400);
        String recipientEmail = data.createCustomer();
        assertThat(api.post(transfer, sender, "{\"recipientEmail\":\"%s\"}".formatted(recipientEmail)).status())
                .isEqualTo(204);

        assertThat((List<?>) api.get("/api/me/tickets", sender).json("$")).isEmpty();
        String recipient = api.login(recipientEmail, TestDataFactory.PASSWORD);
        var received = api.get("/api/me/tickets", recipient);
        String newQr = received.json("$[0].qrPayload");
        assertThat(newQr).isNotEqualTo(oldQr);
        assertThat((String) received.json("$[0].holderName")).isEqualTo("Cliente Test");

        // En la puerta: el QR antiguo ya no vale, el nuevo si.
        String validate = "{\"qrPayload\":\"%s\",\"sessionId\":\"" + event.sessionId() + "\"}";
        assertThat((String) api.post("/api/access/validate", door, validate.formatted(oldQr)).json("$.result"))
                .isEqualTo("INVALID_CODE");
        assertThat((String) api.post("/api/access/validate", door, validate.formatted(newQr)).json("$.result"))
                .isEqualTo("ACCEPTED");
        // Una entrada ya usada no se puede cambiar
        assertThat(api.put("/api/me/tickets/" + ticketId + "/holder", recipient, "{\"holderName\":\"Otro\"}").status())
                .isEqualTo(409);
        // El destinatario recibio un correo
        assertThat(api.get("/api/notifications?size=50", admin).body()).contains("Te han enviado una entrada");
    }

    @Test
    void ordersCanBeResentWithRateLimitAndStaffCanRenameHolders() {
        var api = api();
        var tenant = data.createTenantWithAdmin("Reenvios");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "10.00", 50, 5);
        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        String orderId = data.buyTickets(api, customer, event, 2);

        assertThat(api.post("/api/me/orders/" + orderId + "/resend", customer, null).status()).isEqualTo(204);
        assertThat(api.post("/api/me/orders/" + orderId + "/resend", customer, null).status()).isEqualTo(409);
        assertThat(api.post("/api/orders/" + orderId + "/resend", admin, null).status()).isEqualTo(409);

        // Un pedido pendiente de pago no se reenvia
        var pending = data.checkout(api, customer, """
                {"sessionId":"%s","items":[{"ticketTypeId":"%s","quantity":1}]}
                """.formatted(event.sessionId(), event.ticketTypeId()));
        assertThat(api.post("/api/me/orders/" + pending.json("$.id") + "/resend", customer, null).status())
                .isEqualTo(409);

        // Cambio de titular desde el backoffice (ORDER_MANAGE)
        String ticketId = api.get("/api/orders/" + orderId, admin).json("$.tickets[0].id");
        String holderUrl = "/api/orders/" + orderId + "/tickets/" + ticketId + "/holder";
        assertThat(api.put(holderUrl, admin, "{\"holderName\":\"Invitado VIP\"}").status()).isEqualTo(204);
        assertThat((String) api.get("/api/orders/" + orderId, admin).json("$.tickets[0].holderName"))
                .isEqualTo("Invitado VIP");
        String door = api.login(data.createStaff(tenant.tenantId(), RoleCode.DOOR_STAFF, Set.of()),
                TestDataFactory.PASSWORD);
        assertThat(api.put(holderUrl, door, "{\"holderName\":\"X\"}").status()).isEqualTo(403);
        // Otra empresa no puede tocar la entrada
        String other = api.login(data.createTenantWithAdmin("Reenvios otra").adminEmail(), TestDataFactory.PASSWORD);
        assertThat(api.put(holderUrl, other, "{\"holderName\":\"X\"}").status()).isEqualTo(404);
    }

    @Test
    void holdersAreNotifiedWhenTheEventChangesOrIsCancelled() {
        var api = api();
        var tenant = data.createTenantWithAdmin("Avisos");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "10.00", 50, 5);
        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        data.buyTickets(api, customer, event, 1);

        Instant newStart = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        assertStatus(api.put("/api/events/" + event.eventId() + "/sessions/" + event.sessionId(), admin, """
                {"startsAt":"%s","endsAt":"%s","capacity":500}
                """.formatted(newStart, newStart.plus(6, ChronoUnit.HOURS))), 200);
        assertThat(api.get("/api/notifications?size=50", admin).body()).contains("Cambio de horario");

        assertStatus(api.post("/api/events/" + event.eventId() + "/cancel", admin, null), 200);
        assertThat(api.get("/api/notifications?size=50", admin).body()).contains("Evento cancelado");
    }
}
