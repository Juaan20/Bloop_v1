package com.bloop.support;

import com.bloop.shared.enums.RoleCode;
import com.bloop.tenants.entity.Tenant;
import com.bloop.tenants.repository.TenantRepository;
import com.bloop.users.entity.User;
import com.bloop.users.service.UserAccountService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

/**
 * Crea datos para los tests. Las cuentas base se crean directamente con los servicios;
 * todo lo demas (locales, eventos, compras) se hace por la API, como lo haria Angular.
 */
@Component
public class TestDataFactory {

    public static final String PASSWORD = "Password123!";

    private final UserAccountService userAccountService;
    private final TenantRepository tenantRepository;

    public TestDataFactory(UserAccountService userAccountService, TenantRepository tenantRepository) {
        this.userAccountService = userAccountService;
        this.tenantRepository = tenantRepository;
    }

    public record TenantFixture(UUID tenantId, String adminEmail) {
    }

    public record PublishedEvent(String eventId, String slug, String sessionId, String ticketTypeId,
                                 String venueId) {
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    public String createSuperAdmin() {
        String email = uniqueEmail("super");
        userAccountService.createAccount(null, email, PASSWORD, "Super", "Admin", null,
                Set.of(RoleCode.SUPER_ADMIN.name()), Set.of());
        return email;
    }

    public TenantFixture createTenantWithAdmin(String name) {
        Tenant tenant = new Tenant();
        tenant.setName(name);
        tenant.setSlug("t-" + UUID.randomUUID());
        tenant.setContactEmail(uniqueEmail("contact"));
        tenant = tenantRepository.save(tenant);
        String adminEmail = uniqueEmail("admin");
        userAccountService.createAccount(tenant.getId(), adminEmail, PASSWORD, "Admin", name, null,
                Set.of(RoleCode.TENANT_ADMIN.name()), Set.of());
        return new TenantFixture(tenant.getId(), adminEmail);
    }

    public String createStaff(UUID tenantId, RoleCode role, Set<UUID> venueIds) {
        String email = uniqueEmail(role.name().toLowerCase());
        userAccountService.createAccount(tenantId, email, PASSWORD, "Staff", role.name(), null,
                Set.of(role.name()), venueIds);
        return email;
    }

    public String createCustomer() {
        String email = uniqueEmail("customer");
        User user = userAccountService.createAccount(null, email, PASSWORD, "Cliente", "Test", null,
                Set.of(RoleCode.CUSTOMER.name()), Set.of());
        return user.getEmail();
    }

    // ---------------- Escenarios creados via API ----------------

    public String createVenue(ApiClient api, String adminToken, int capacity) {
        var response = api.post("/api/venues", adminToken, """
                {"name":"Sala %s","addressLine":"Calle Test 1","city":"Madrid","country":"ES","capacity":%d}
                """.formatted(UUID.randomUUID().toString().substring(0, 6), capacity));
        assertStatus(response, 201);
        return response.json("$.id");
    }

    /** Crea un evento con una sesion manana, un tipo de entrada y lo publica. */
    public PublishedEvent createPublishedEvent(ApiClient api, String adminToken, String price, int quantity,
                                               int maxPerOrder) {
        String venueId = createVenue(api, adminToken, 1000);
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        var event = api.post("/api/events", adminToken, """
                {"venueId":"%s","name":"Evento %s","description":"Test",
                 "firstSession":{"startsAt":"%s","endsAt":"%s","capacity":500}}
                """.formatted(venueId, UUID.randomUUID().toString().substring(0, 6), start,
                start.plus(6, ChronoUnit.HOURS)));
        assertStatus(event, 201);
        String eventId = event.json("$.id");
        String sessionId = event.json("$.sessions[0].id");
        var ticketType = api.post("/api/events/%s/sessions/%s/ticket-types".formatted(eventId, sessionId), adminToken,
                """
                {"name":"General","category":"%s","price":%s,"quantityTotal":%d,"maxPerOrder":%d,
                 "active":true,"sortOrder":0}
                """.formatted(Double.parseDouble(price) == 0 ? "FREE" : "GENERAL", price, quantity, maxPerOrder));
        assertStatus(ticketType, 201);
        assertStatus(api.post("/api/events/" + eventId + "/publish", adminToken, null), 200);
        return new PublishedEvent(eventId, event.json("$.slug"), sessionId, ticketType.json("$.id"), venueId);
    }

    /** Compra y paga (pasarela simulada) N entradas. Devuelve el id del pedido. */
    public String buyTickets(ApiClient api, String customerToken, PublishedEvent event, int quantity) {
        var order = api.postWithHeader("/api/checkout", customerToken, """
                {"sessionId":"%s","items":[{"ticketTypeId":"%s","quantity":%d}]}
                """.formatted(event.sessionId(), event.ticketTypeId(), quantity), "Idempotency-Key",
                "key-" + UUID.randomUUID());
        assertStatus(order, 201);
        String orderId = order.json("$.id");
        var payment = api.post("/api/checkout/orders/" + orderId + "/simulated-payment", customerToken,
                "{\"approve\":true}");
        assertStatus(payment, 200);
        return orderId;
    }

    /** Crea un pedido pendiente con el cuerpo de checkout indicado (sin pagarlo). */
    public ApiClient.Response checkout(ApiClient api, String customerToken, String body) {
        return api.postWithHeader("/api/checkout", customerToken, body, "Idempotency-Key",
                "key-" + UUID.randomUUID());
    }

    /** Paga un pedido pendiente con la pasarela simulada. */
    public void pay(ApiClient api, String customerToken, String orderId) {
        assertStatus(api.post("/api/checkout/orders/" + orderId + "/simulated-payment", customerToken,
                "{\"approve\":true}"), 200);
    }

    public static void assertStatus(ApiClient.Response response, int expected) {
        if (response.status() != expected) {
            throw new AssertionError("Se esperaba HTTP " + expected + " pero fue " + response.status() + ": "
                    + response.body());
        }
    }
}
