package com.bloop.reservations;

import com.bloop.shared.enums.RoleCode;
import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.ApiClient;
import com.bloop.support.Concurrently;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.bloop.support.TestDataFactory.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;

/** Reservas VIP: zonas y mesas, solicitud web, confirmacion, anticipos y prevencion de solapamientos. */
class ReservationIntegrationTest extends AbstractIntegrationTest {

    private record Setup(String admin, TestDataFactory.TenantFixture tenant, TestDataFactory.PublishedEvent event,
                         String zoneId, String tableId) {
    }

    private Setup setup(ApiClient api, String name) {
        var tenant = data.createTenantWithAdmin(name);
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "10.00", 100, 10);
        var zone = api.post("/api/venues/" + event.venueId() + "/vip-zones", admin,
                "{\"name\":\"Escenario\",\"active\":true,\"sortOrder\":0}");
        assertStatus(zone, 201);
        String zoneId = zone.json("$.id");
        var withTable = api.post("/api/vip-zones/" + zoneId + "/tables", admin,
                "{\"name\":\"Mesa 1\",\"capacity\":6,\"minSpend\":300,\"depositAmount\":100,\"active\":true}");
        assertStatus(withTable, 201);
        String tableId = withTable.json("$.tables[0].id");
        // Solo se reservan las mesas colocadas en el plano de su sala
        assertStatus(api.put("/api/venues/" + event.venueId() + "/vip-layout", admin,
                "{\"tables\":[{\"id\":\"%s\",\"posX\":20,\"posY\":30,\"width\":9,\"height\":9,\"shape\":\"ROUND\"}]}".formatted(tableId)), 200);
        return new Setup(admin, tenant, event, zoneId, tableId);
    }

    private String requestBody(Setup s, int guests) {
        return "{\"sessionId\":\"%s\",\"tableId\":\"%s\",\"guests\":%d,\"phone\":\"600000000\"}"
                .formatted(s.event().sessionId(), s.tableId(), guests);
    }

    @Test
    void customerRequestsAndStaffConfirmsAReservation() {
        var api = api();
        Setup s = setup(api, "VIP flujo");
        String tablesUrl = "/api/public/sessions/" + s.event().sessionId() + "/vip-tables";
        assertThat((Boolean) api.get(tablesUrl, null).json("$[0].available")).isTrue();

        String email = data.createCustomer();
        String customer = api.login(email, TestDataFactory.PASSWORD);
        assertThat(api.post("/api/me/reservations", customer, requestBody(s, 10)).status()).isEqualTo(400); // capacidad
        var reservation = api.post("/api/me/reservations", customer, requestBody(s, 4));
        assertStatus(reservation, 201);
        assertThat((String) reservation.json("$.status")).isEqualTo("PENDING");
        assertThat((Boolean) reservation.json("$.depositPending")).isTrue();
        String id = reservation.json("$.id");
        assertThat((Boolean) api.get(tablesUrl, null).json("$[0].available")).isFalse();

        // La misma mesa para la misma sesion: solapamiento
        String other = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        assertThat(api.post("/api/me/reservations", other, requestBody(s, 2)).status()).isEqualTo(409);

        // El equipo recibe una alerta y ve la reserva
        var alerts = api.get("/api/alerts", s.admin());
        assertThat(((Number) alerts.json("$.unreadCount")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat((String) alerts.json("$.alerts[0].type")).isEqualTo("RESERVATION_REQUESTED");
        assertThat(api.get("/api/reservations", s.admin()).body()).contains(id);

        // Confirmar, transicion no valida, anticipo
        assertStatus(api.post("/api/reservations/" + id + "/status", s.admin(), "{\"status\":\"CONFIRMED\"}"), 200);
        assertThat(api.post("/api/reservations/" + id + "/status", s.admin(), "{\"status\":\"COMPLETED\"}").status())
                .isEqualTo(400);
        var deposit = api.post("/api/reservations/" + id + "/deposit", s.admin(), null);
        assertThat((Boolean) deposit.json("$.depositPending")).isFalse();
        assertThat(api.post("/api/reservations/" + id + "/deposit", s.admin(), null).status()).isEqualTo(409);
        assertThat((String) api.get("/api/me/reservations", customer).json("$[0].status")).isEqualTo("CONFIRMED");

        // Correos enviados (modo log): recibida y confirmada
        String log = api.get("/api/notifications?size=50", s.admin()).body();
        assertThat(log).contains("Solicitud de reserva recibida").contains("Reserva confirmada");

        // El cliente aparece en el CRM con su reserva
        String customerId = api.get("/api/customers?search=" + email, s.admin()).json("$.content[0].id");
        assertThat((List<?>) api.get("/api/customers/" + customerId, s.admin()).json("$.reservations")).hasSize(1);

        // El cliente cancela: la mesa vuelve a estar libre
        assertThat((String) api.post("/api/me/reservations/" + id + "/cancel", customer, null).json("$.status"))
                .isEqualTo("CANCELLED");
        assertThat((Boolean) api.get(tablesUrl, null).json("$[0].available")).isTrue();
        assertThat(api.post("/api/me/reservations/" + id + "/cancel", customer, null).status()).isEqualTo(409);
    }

    @Test
    void concurrentRequestsForTheSameTableOnlyOneWins() throws Exception {
        var api = api();
        Setup s = setup(api, "VIP carrera");
        int customers = 6;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < customers; i++) {
            tokens.add(api.login(data.createCustomer(), TestDataFactory.PASSWORD));
        }
        List<Integer> statuses = Concurrently.run(customers,
                i -> api.post("/api/me/reservations", tokens.get(i), requestBody(s, 2)).status());

        // La restriccion EXCLUDE de PostgreSQL garantiza una unica reserva aunque lleguen a la vez.
        assertThat(statuses.stream().filter(st -> st == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(st -> st == 409).count()).isEqualTo(customers - 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void backofficeReservationsArePerPartyAndOnlyForTablesOnThePlan() {
        var api = api();
        Setup s = setup(api, "VIP manual");
        String body = """
                {"tableId":"%s","sessionId":"%s","guests":2,"contactName":"Ana Lopez",
                 "contactEmail":"ana@example.test","contactPhone":"611"}""";
        // Sin fiesta (por horas) ya no se puede reservar
        assertThat(api.post("/api/reservations", s.admin(), """
                {"tableId":"%s","startsAt":"2031-01-01T22:00:00Z","endsAt":"2031-01-02T02:00:00Z","guests":2,
                 "contactName":"Por horas"}""".formatted(s.tableId())).status()).isEqualTo(400);

        var created = api.post("/api/reservations", s.admin(), body.formatted(s.tableId(), s.event().sessionId()));
        assertStatus(created, 201);
        assertThat((String) created.json("$.status")).isEqualTo("CONFIRMED");
        assertThat((String) created.json("$.source")).isEqualTo("BACKOFFICE");
        // La misma mesa en la misma fiesta: ocupada
        assertThat(api.post("/api/reservations", s.admin(), body.formatted(s.tableId(), s.event().sessionId())).status())
                .isEqualTo(409);
        var availability = api.get("/api/reservations/availability?venueId=" + s.event().venueId() + "&sessionId="
                + s.event().sessionId(), s.admin());
        assertThat((Boolean) availability.json("$[0].available")).isFalse();

        // Una mesa que no esta en el plano no se puede reservar ni aparece como disponible
        var unplaced = api.post("/api/vip-zones/" + s.zoneId() + "/tables", s.admin(),
                "{\"name\":\"Mesa fantasma\",\"capacity\":4,\"minSpend\":0,\"depositAmount\":0,\"active\":true}");
        String unplacedId = ((List<java.util.Map<String, Object>>) unplaced.json("$.tables")).stream()
                .filter(t -> "Mesa fantasma".equals(t.get("name"))).map(t -> (String) t.get("id")).findFirst().orElseThrow();
        assertThat(api.post("/api/reservations", s.admin(), body.formatted(unplacedId, s.event().sessionId())).status())
                .isEqualTo(400);
        assertThat(api.get("/api/reservations/availability?venueId=" + s.event().venueId() + "&sessionId="
                + s.event().sessionId(), s.admin()).body()).doesNotContain(unplacedId);

        // Otra empresa no ve ni toca nada
        String id = created.json("$.id");
        String other = api.login(data.createTenantWithAdmin("VIP otra").adminEmail(), TestDataFactory.PASSWORD);
        assertThat(api.get("/api/reservations/" + id, other).status()).isEqualTo(404);
        assertThat(api.get("/api/venues/" + s.event().venueId() + "/vip-zones", other).status()).isEqualTo(404);
        assertThat(api.post("/api/vip-zones/" + s.zoneId() + "/tables", other,
                "{\"name\":\"X\",\"capacity\":2,\"minSpend\":0,\"depositAmount\":0,\"active\":true}").status())
                .isEqualTo(404);
        assertThat(api.post("/api/reservations", other, body.formatted(s.tableId(), s.event().sessionId())).status())
                .isEqualTo(404);

        // Personal de puerta: puede consultar pero no crear
        String door = api.login(data.createStaff(s.tenant().tenantId(), RoleCode.DOOR_STAFF, Set.of()),
                TestDataFactory.PASSWORD);
        assertThat(api.get("/api/reservations", door).status()).isEqualTo(200);
        assertThat(api.post("/api/reservations", door, body.formatted(s.tableId(), s.event().sessionId())).status())
                .isEqualTo(403);
    }

    @Test
    @SuppressWarnings("unchecked")
    void staffCannotUseTheCustomerArea() {
        var api = api();
        Setup s = setup(api, "VIP separacion");
        // Un trabajador (aunque sea administrador) no usa los endpoints de cliente
        assertThat(api.get("/api/me/tickets", s.admin()).status()).isEqualTo(403);
        assertThat(api.get("/api/me/orders", s.admin()).status()).isEqualTo(403);
        assertThat(api.get("/api/me/reservations", s.admin()).status()).isEqualTo(403);
        assertThat(api.post("/api/me/reservations", s.admin(), requestBody(s, 2)).status()).isEqualTo(403);
        assertThat(data.checkout(api, s.admin(), """
                {"sessionId":"%s","items":[{"ticketTypeId":"%s","quantity":1}]}"""
                .formatted(s.event().sessionId(), s.event().ticketTypeId())).status()).isEqualTo(403);
        // Y un cliente no usa los del panel
        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        assertThat(api.get("/api/me/tickets", customer).status()).isEqualTo(200);
        assertThat(api.get("/api/reservations", customer).status()).isEqualTo(403);
        assertThat(api.get("/api/orders", customer).status()).isEqualTo(403);
        assertThat(api.get("/api/venues/" + s.event().venueId() + "/vip-map", customer).status()).isEqualTo(403);
    }

    @Test
    void inactiveTablesCannotBeBooked() {
        var api = api();
        Setup s = setup(api, "VIP inactiva");
        assertStatus(api.put("/api/vip-tables/" + s.tableId(), s.admin(),
                "{\"name\":\"Mesa 1\",\"capacity\":6,\"minSpend\":300,\"depositAmount\":100,\"active\":false}"), 200);
        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        assertThat(api.post("/api/me/reservations", customer, requestBody(s, 2)).status()).isEqualTo(400);
        assertThat((List<?>) api.get("/api/public/sessions/" + s.event().sessionId() + "/vip-tables", null).json("$"))
                .isEmpty();
    }
}
