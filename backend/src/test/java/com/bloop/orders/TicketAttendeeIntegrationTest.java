package com.bloop.orders;

import com.bloop.shared.enums.RoleCode;
import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.bloop.support.TestDataFactory.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compra con datos de asistentes: edad minima validada en el backend, documento protegido
 * (solo 4 ultimos caracteres) y una entrada por asistente que la puerta valida una sola vez.
 */
class TicketAttendeeIntegrationTest extends AbstractIntegrationTest {

    private static final String ADULT_DNI = "12345678Z"; // letra de control correcta

    @Test
    void ageRestrictedEventRequiresValidAdultAttendees() {
        var api = api();
        var tenant = data.createTenantWithAdmin("Edad minima");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "15.00", 100, 10);
        assertStatus(api.put("/api/events/" + event.eventId(), admin,
                "{\"name\":\"Fiesta +18\",\"description\":\"Solo adultos\",\"imageUrl\":\"\",\"minAge\":18}"), 200);

        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        String items = "\"items\":[{\"ticketTypeId\":\"%s\",\"quantity\":2}]".formatted(event.ticketTypeId());
        String base = "{\"sessionId\":\"%s\",%s,\"buyerPhone\":\"+34 600 111 222\"".formatted(event.sessionId(), items);
        String adultBirth = LocalDate.now().minusYears(25).toString();
        String minorBirth = LocalDate.now().minusYears(16).toString();

        // Sin asistentes: rechazado
        assertThat(data.checkout(api, customer, base + "}").status()).isEqualTo(400);
        // Un asistente menor: rechazado
        var minor = data.checkout(api, customer, base + ",\"attendees\":[%s,%s]}".formatted(
                attendee(event.ticketTypeId(), "Ana", adultBirth, "DNI", ADULT_DNI),
                attendee(event.ticketTypeId(), "Leo", minorBirth, "DNI", "87654321X")));
        assertThat(minor.status()).isEqualTo(400);
        assertThat(minor.body()).contains("edad minima").doesNotContain("87654321");
        // DNI con letra incorrecta: rechazado sin repetir el numero en el error
        var badDni = data.checkout(api, customer, base + ",\"attendees\":[%s,%s]}".formatted(
                attendee(event.ticketTypeId(), "Ana", adultBirth, "DNI", "12345678A"),
                attendee(event.ticketTypeId(), "Eva", adultBirth, "PASSPORT", "XDA123456")));
        assertThat(badDni.status()).isEqualTo(400);
        assertThat(badDni.body()).doesNotContain("12345678A");
        // Numero de asistentes distinto del de entradas: rechazado
        assertThat(data.checkout(api, customer, base + ",\"attendees\":[%s]}".formatted(
                attendee(event.ticketTypeId(), "Ana", adultBirth, "DNI", ADULT_DNI))).status()).isEqualTo(400);

        // Correcto: dos adultos
        var order = data.checkout(api, customer, base + ",\"attendees\":[%s,%s]}".formatted(
                attendee(event.ticketTypeId(), "Ana", adultBirth, "DNI", ADULT_DNI),
                attendee(event.ticketTypeId(), "Eva", adultBirth, "PASSPORT", "XDA123456")));
        assertStatus(order, 201);
        assertThat(order.body()).doesNotContain(ADULT_DNI).doesNotContain("XDA123456");
        data.pay(api, customer, order.json("$.id"));

        // Una entrada por asistente, con su nombre
        var tickets = api.get("/api/me/tickets", customer);
        List<String> holders = tickets.json("$[*].holderName");
        assertThat(holders).containsExactlyInAnyOrder("Ana Garcia", "Eva Garcia");
        assertThat(tickets.body()).doesNotContain(ADULT_DNI);

        // La puerta ve el documento enmascarado y valida cada entrada una sola vez
        String door = api.login(data.createStaff(tenant.tenantId(), RoleCode.DOOR_STAFF,
                Set.of(UUID.fromString(event.venueId()))), TestDataFactory.PASSWORD);
        List<String> payloads = tickets.json("$[*].qrPayload");
        String body = "{\"qrPayload\":\"%s\",\"sessionId\":\"%s\"}";
        var first = api.post("/api/access/validate", door, body.formatted(payloads.get(0), event.sessionId()));
        assertThat((Boolean) first.json("$.accepted")).isTrue();
        assertThat((Integer) first.json("$.minAge")).isEqualTo(18);
        assertThat((String) first.json("$.holderDocument")).matches("(DNI ····678Z|PASSPORT ····3456)");
        assertThat((Boolean) api.post("/api/access/validate", door, body.formatted(payloads.get(0), event.sessionId()))
                .json("$.accepted")).isFalse();
    }

    private static String attendee(String ticketTypeId, String name, String birthDate, String docType, String doc) {
        return """
                {"ticketTypeId":"%s","firstName":"%s","lastName":"Garcia","birthDate":"%s",
                 "documentType":"%s","documentNumber":"%s"}""".formatted(ticketTypeId, name, birthDate, docType, doc);
    }
}
