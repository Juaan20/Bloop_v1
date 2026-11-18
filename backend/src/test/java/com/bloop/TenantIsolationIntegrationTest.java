package com.bloop;

import com.bloop.shared.enums.RoleCode;
import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aislamiento multi-tenant: un usuario de la empresa A nunca puede ver ni modificar
 * datos de la empresa B, aunque conozca sus identificadores.
 */
class TenantIsolationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void adminCannotReadOrModifyAnotherTenantsData() {
        var api = api();
        var tenantA = data.createTenantWithAdmin("Empresa A");
        var tenantB = data.createTenantWithAdmin("Empresa B");
        String tokenA = api.login(tenantA.adminEmail(), TestDataFactory.PASSWORD);
        String tokenB = api.login(tenantB.adminEmail(), TestDataFactory.PASSWORD);

        var eventB = data.createPublishedEvent(api, tokenB, "10.00", 50, 5);

        // Listados: A no ve nada de B
        List<String> venuesA = api.get("/api/venues", tokenA).json("$[*].id");
        assertThat(venuesA).doesNotContain(eventB.venueId());
        assertThat(api.get("/api/events", tokenA).body()).doesNotContain(eventB.eventId());

        // Acceso directo por id: 404 (no revelamos que existe)
        assertThat(api.get("/api/venues/" + eventB.venueId(), tokenA).status()).isEqualTo(404);
        assertThat(api.get("/api/events/" + eventB.eventId(), tokenA).status()).isEqualTo(404);
        assertThat(api.put("/api/events/" + eventB.eventId(), tokenA, "{\"name\":\"Hackeado\"}").status())
                .isEqualTo(404);
        assertThat(api.post("/api/events/" + eventB.eventId() + "/cancel", tokenA, null).status()).isEqualTo(404);

        // Crear un evento en un local de otra empresa no es posible
        var crossCreate = api.post("/api/events", tokenA, """
                {"venueId":"%s","name":"Intruso","firstSession":{"startsAt":"2030-01-01T22:00:00Z",
                 "endsAt":"2030-01-02T04:00:00Z","capacity":10}}
                """.formatted(eventB.venueId()));
        assertThat(crossCreate.status()).isEqualTo(404);

        // Asignar a un empleado un local de otra empresa tampoco
        var crossUser = api.post("/api/users", tokenA, """
                {"email":"%s","firstName":"X","lastName":"Y","password":"Password123!",
                 "roles":["DOOR_STAFF"],"venueIds":["%s"]}
                """.formatted(TestDataFactory.uniqueEmail("x"), eventB.venueId()));
        assertThat(crossUser.status()).isEqualTo(400);
    }

    @Test
    void ordersAndUsersAreScopedToTenant() {
        var api = api();
        var tenantA = data.createTenantWithAdmin("Ventas A");
        var tenantB = data.createTenantWithAdmin("Ventas B");
        String tokenA = api.login(tenantA.adminEmail(), TestDataFactory.PASSWORD);
        String tokenB = api.login(tenantB.adminEmail(), TestDataFactory.PASSWORD);
        var eventB = data.createPublishedEvent(api, tokenB, "20.00", 50, 5);
        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        String orderB = data.buyTickets(api, customer, eventB, 1);

        assertThat(api.get("/api/orders", tokenA).body()).doesNotContain(orderB);
        assertThat(api.get("/api/orders/" + orderB, tokenA).status()).isEqualTo(404);
        assertThat(api.get("/api/orders/" + orderB, tokenB).status()).isEqualTo(200);
        assertThat(api.get("/api/users", tokenA).body()).doesNotContain(tenantB.adminEmail());
        // El dashboard de A no incluye las ventas de B
        assertThat(api.get("/api/dashboard", tokenA).json("$.kpis.paidOrders").toString()).isEqualTo("0");
    }

    @Test
    void doorStaffCannotValidateTicketsOfAnotherTenantOrUnassignedVenue() {
        var api = api();
        var tenantA = data.createTenantWithAdmin("Puerta A");
        var tenantB = data.createTenantWithAdmin("Puerta B");
        String tokenA = api.login(tenantA.adminEmail(), TestDataFactory.PASSWORD);
        String tokenB = api.login(tenantB.adminEmail(), TestDataFactory.PASSWORD);
        var eventA = data.createPublishedEvent(api, tokenA, "10.00", 20, 5);
        var eventB = data.createPublishedEvent(api, tokenB, "10.00", 20, 5);

        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        data.buyTickets(api, customer, eventB, 1);
        String qrB = (String) ((List<?>) api.get("/api/me/tickets", customer).json("$[*].qrPayload")).getFirst();

        // Portero de A asignado a su local: no puede usar una sesion de B (404)...
        String doorA = api.login(data.createStaff(tenantA.tenantId(), RoleCode.DOOR_STAFF,
                Set.of(UUID.fromString(eventA.venueId()))), TestDataFactory.PASSWORD);
        var otherSession = api.post("/api/access/validate", doorA,
                "{\"qrPayload\":\"%s\",\"sessionId\":\"%s\"}".formatted(qrB, eventB.sessionId()));
        assertThat(otherSession.status()).isEqualTo(404);
        // ...y una entrada de B escaneada en su sesion es "codigo no valido".
        var foreignTicket = api.post("/api/access/validate", doorA,
                "{\"qrPayload\":\"%s\",\"sessionId\":\"%s\"}".formatted(qrB, eventA.sessionId()));
        assertThat((String) foreignTicket.json("$.result")).isEqualTo("INVALID_CODE");

        // Portero sin asignacion al local: 403
        String doorUnassigned = api.login(data.createStaff(tenantA.tenantId(), RoleCode.DOOR_STAFF, Set.of()),
                TestDataFactory.PASSWORD);
        var forbidden = api.post("/api/access/validate", doorUnassigned,
                "{\"qrPayload\":\"VF1.x.y\",\"sessionId\":\"%s\"}".formatted(eventA.sessionId()));
        assertThat(forbidden.status()).isEqualTo(403);
    }

    @Test
    void rolesRestrictEndpoints() {
        var api = api();
        var tenant = data.createTenantWithAdmin("Roles");
        String door = api.login(data.createStaff(tenant.tenantId(), RoleCode.DOOR_STAFF, Set.of()),
                TestDataFactory.PASSWORD);
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);

        assertThat(api.get("/api/events", door).status()).isEqualTo(200);   // EVENT_VIEW
        String validVenue = """
                {"name":"X","addressLine":"Y","city":"Z","country":"ES","capacity":10}""";
        assertThat(api.post("/api/venues", door, validVenue).status()).isEqualTo(403); // sin VENUE_MANAGE
        assertThat(api.get("/api/orders", door).status()).isEqualTo(403);
        assertThat(api.get("/api/users", door).status()).isEqualTo(403);
        assertThat(api.get("/api/admin/tenants", admin).status()).isEqualTo(403); // solo SUPER_ADMIN

        // Un admin no puede asignar el rol SUPER_ADMIN
        var escalation = api.post("/api/users", admin, """
                {"email":"%s","firstName":"X","lastName":"Y","password":"Password123!","roles":["SUPER_ADMIN"]}
                """.formatted(TestDataFactory.uniqueEmail("esc")));
        assertThat(escalation.status()).isEqualTo(400);
    }
}
