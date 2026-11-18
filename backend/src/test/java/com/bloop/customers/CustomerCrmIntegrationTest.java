package com.bloop.customers;

import com.bloop.shared.enums.RoleCode;
import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.bloop.support.TestDataFactory.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;

/** CRM de clientes y derechos RGPD (empresa y propio cliente). */
class CustomerCrmIntegrationTest extends AbstractIntegrationTest {

    @Test
    void paidOrdersBuildTheCustomerRecordWithSegmentsAndConsent() {
        var api = api();
        var tenant = data.createTenantWithAdmin("CRM");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "20.00", 100, 10);
        String email = data.createCustomer();
        String customer = api.login(email, TestDataFactory.PASSWORD);

        // Primera compra: marca la casilla de comunicaciones del organizador.
        var order = data.checkout(api, customer, """
                {"sessionId":"%s","items":[{"ticketTypeId":"%s","quantity":2}],"marketingConsent":true}
                """.formatted(event.sessionId(), event.ticketTypeId()));
        assertStatus(order, 201);
        data.pay(api, customer, order.json("$.id"));
        // Segunda compra sin marcarla: NO retira el consentimiento anterior.
        data.buyTickets(api, customer, event, 1);

        var page = api.get("/api/customers?search=" + email, admin);
        assertStatus(page, 200);
        assertThat((Integer) page.json("$.totalElements")).isEqualTo(1);
        assertThat((Integer) page.json("$.content[0].ordersCount")).isEqualTo(2);
        assertThat((Integer) page.json("$.content[0].ticketsCount")).isEqualTo(3);
        assertThat(((Number) page.json("$.content[0].totalSpent")).doubleValue()).isEqualTo(60.0);
        assertThat((Boolean) page.json("$.content[0].marketingConsent")).isTrue();
        assertThat((List<String>) page.json("$.content[0].segments")).contains("RECURRING");
        String customerId = page.json("$.content[0].id");

        var detail = api.get("/api/customers/" + customerId, admin);
        assertThat((List<?>) detail.json("$.orders")).hasSize(2);
        assertThat((String) detail.json("$.consents[0].source")).isEqualTo("CHECKOUT");

        // Filtros de segmento
        assertThat(api.get("/api/customers?segment=NEW", admin).body()).doesNotContain(email);
        assertThat(api.get("/api/customers?segment=RECURRING", admin).body()).contains(email);
        assertThat(api.get("/api/customers?marketingOnly=true", admin).body()).contains(email);

        // Otra empresa no ve la ficha
        String other = api.login(data.createTenantWithAdmin("CRM otra").adminEmail(), TestDataFactory.PASSWORD);
        assertThat(api.get("/api/customers/" + customerId, other).status()).isEqualTo(404);
        assertThat(api.get("/api/customers", other).body()).doesNotContain(email);
    }

    @Test
    void tenantCanEditExportAndAnonymizeACustomer() {
        var api = api();
        var tenant = data.createTenantWithAdmin("RGPD empresa");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "15.00", 100, 10);
        String email = data.createCustomer();
        String customer = api.login(email, TestDataFactory.PASSWORD);
        data.buyTickets(api, customer, event, 1);
        String customerId = api.get("/api/customers?search=" + email, admin).json("$.content[0].id");

        var updated = api.put("/api/customers/" + customerId, admin, """
                {"phone":"600111222","notes":"Celebra su cumpleanos","tags":" vip ,cumple, vip ","marketingConsent":false}
                """);
        assertStatus(updated, 200);
        assertThat((String) updated.json("$.customer.tags")).isEqualTo("vip, cumple");

        var export = api.get("/api/customers/" + customerId + "/export", admin);
        assertStatus(export, 200);
        assertThat((String) export.json("$.customer.email")).isEqualTo(email);
        assertThat((String) export.json("$.notes")).isEqualTo("Celebra su cumpleanos");
        assertThat((List<?>) export.json("$.orders")).hasSize(1);

        assertThat(api.delete("/api/customers/" + customerId, admin).status()).isEqualTo(204);
        assertThat(api.get("/api/customers/" + customerId, admin).status()).isEqualTo(404);
        assertThat((Integer) api.get("/api/customers?search=" + email, admin).json("$.totalElements")).isZero();
        // La cuenta del cliente sigue funcionando y conserva sus pedidos (obligacion contable de la empresa).
        assertThat((List<?>) api.get("/api/me/orders", customer).json("$")).hasSize(1);
    }

    @Test
    void permissionsSeparateViewingFromManaging() {
        var api = api();
        var tenant = data.createTenantWithAdmin("CRM permisos");
        String manager = api.login(data.createStaff(tenant.tenantId(), RoleCode.MANAGER, Set.of()),
                TestDataFactory.PASSWORD);
        String door = api.login(data.createStaff(tenant.tenantId(), RoleCode.DOOR_STAFF, Set.of()),
                TestDataFactory.PASSWORD);

        assertThat(api.get("/api/customers", manager).status()).isEqualTo(200);
        assertThat(api.delete("/api/customers/00000000-0000-0000-0000-000000000000", manager).status())
                .isEqualTo(403);
        assertThat(api.get("/api/customers", door).status()).isEqualTo(403);
    }

    @Test
    void customerManagesConsentsExportsDataAndDeletesAccount() {
        var api = api();
        var tenant = data.createTenantWithAdmin("Privacidad");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "10.00", 100, 10);
        String email = data.createCustomer();
        String customer = api.login(email, TestDataFactory.PASSWORD);
        data.buyTickets(api, customer, event, 1);

        var consents = api.get("/api/me/privacy/consents", customer);
        assertThat((List<?>) consents.json("$")).hasSize(1);
        assertThat((String) consents.json("$[0].companyName")).isEqualTo("Privacidad");
        assertThat((Boolean) consents.json("$[0].marketingConsent")).isFalse();
        String customerId = consents.json("$[0].customerId");

        var granted = api.put("/api/me/privacy/consents/" + customerId, customer, "{\"marketingConsent\":true}");
        assertThat((Boolean) granted.json("$.marketingConsent")).isTrue();
        assertThat((String) api.get("/api/customers/" + customerId, admin).json("$.consents[0].source"))
                .isEqualTo("CUSTOMER");
        // Otro cliente no puede tocar esa ficha
        String intruder = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        assertThat(api.put("/api/me/privacy/consents/" + customerId, intruder, "{\"marketingConsent\":false}")
                .status()).isEqualTo(404);

        var export = api.get("/api/me/data-export", customer);
        assertStatus(export, 200);
        assertThat((String) export.json("$.account.email")).isEqualTo(email);
        assertThat((List<?>) export.json("$.orders")).hasSize(1);
        assertThat((List<?>) export.json("$.tickets")).hasSize(1);
        assertThat((List<?>) export.json("$.companies")).hasSize(1);

        // Con entradas para un evento futuro no se puede suprimir la cuenta (las perderia).
        String deleteBody = "{\"password\":\"%s\"}".formatted(TestDataFactory.PASSWORD);
        assertThat(api.post("/api/me/delete-account", customer, deleteBody).status()).isEqualTo(409);
        // Los empleados no usan este flujo
        assertThat(api.post("/api/me/delete-account", admin, deleteBody).status()).isEqualTo(403);

        String emailWithoutTickets = data.createCustomer();
        String other = api.login(emailWithoutTickets, TestDataFactory.PASSWORD);
        assertThat(api.post("/api/me/delete-account", other, "{\"password\":\"incorrecta\"}").status())
                .isEqualTo(400);
        assertThat(api.post("/api/me/delete-account", other, deleteBody).status()).isEqualTo(204);
        var login = api.post("/api/auth/login", null,
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(emailWithoutTickets, TestDataFactory.PASSWORD));
        assertThat(login.status()).isEqualTo(401);
    }
}
