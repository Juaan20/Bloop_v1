package com.bloop.pos;

import com.bloop.shared.enums.RoleCode;
import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.ApiClient;
import com.bloop.support.Concurrently;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.bloop.support.TestDataFactory.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;

/** TPV: caja, ventas, descuentos autorizados, anulaciones, existencias y bloqueo por terminal. */
class PosIntegrationTest extends AbstractIntegrationTest {

    /** Local con almacen, terminal y catalogo: Cerveza (Cana 3 €, Pinta 5 €, con stock) y Mojito (9 €, sin stock). */
    private record Setup(TestDataFactory.TenantFixture tenant, String admin, String venueId, String warehouseId,
                         String terminalId, String cana, String pinta, String mojito) {
    }

    private Setup setup(ApiClient api, String name, int canaStock) {
        var tenant = data.createTenantWithAdmin(name);
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        String venueId = data.createVenue(api, admin, 500);
        var warehouse = api.post("/api/inventory/warehouses", admin,
                "{\"venueId\":\"%s\",\"name\":\"Barra\",\"active\":true}".formatted(venueId));
        assertStatus(warehouse, 201);
        String warehouseId = warehouse.json("$.id");
        var category = api.post("/api/pos/categories", admin, "{\"name\":\"Bebidas\",\"color\":\"amber\",\"sortOrder\":0,\"active\":true}");
        assertStatus(category, 201);
        String categoryId = category.json("$.id");
        var beer = api.post("/api/pos/products", admin, """
                {"categoryId":"%s","name":"Cerveza","trackStock":true,"sortOrder":0,"active":true,
                 "variants":[{"name":"Cana","price":3.00,"sortOrder":0,"active":true},
                             {"name":"Pinta","price":5.00,"sortOrder":1,"active":true}]}""".formatted(categoryId));
        assertStatus(beer, 201);
        var mojito = api.post("/api/pos/products", admin, """
                {"categoryId":"%s","name":"Mojito","trackStock":false,"sortOrder":1,"active":true,
                 "variants":[{"name":"Copa","price":9.00,"sortOrder":0,"active":true}]}""".formatted(categoryId));
        assertStatus(mojito, 201);
        String cana = beer.json("$.variants[0].id");
        if (canaStock > 0) {
            assertStatus(api.post("/api/inventory/warehouses/" + warehouseId + "/movements", admin,
                    "{\"variantId\":\"%s\",\"type\":\"IN\",\"quantity\":%d,\"reason\":\"Compra\"}".formatted(cana, canaStock)), 200);
        }
        var terminal = api.post("/api/pos/terminals", admin, """
                {"name":"Barra 1","venueId":"%s","warehouseId":"%s","type":"BAR","active":true}
                """.formatted(venueId, warehouseId));
        assertStatus(terminal, 201);
        return new Setup(tenant, admin, venueId, warehouseId, terminal.json("$.id"), cana,
                beer.json("$.variants[1].id"), mojito.json("$.variants[0].id"));
    }

    private ApiClient.Response sell(ApiClient api, String token, Setup s, String body) {
        return api.postWithHeader("/api/pos/terminals/" + s.terminalId() + "/sales", token, body, "Idempotency-Key",
                "pos-" + UUID.randomUUID());
    }

    private int stockOf(ApiClient api, Setup s, String variantId) {
        List<Map<String, Object>> stock = api.get("/api/inventory/warehouses/" + s.warehouseId() + "/stock", s.admin())
                .json("$");
        return stock.stream().filter(i -> variantId.equals(i.get("variantId")))
                .map(i -> ((Number) i.get("quantity")).intValue()).findFirst().orElseThrow();
    }

    @Test
    void fullShiftWithSalesDiscountVoidAndClose() {
        var api = api();
        Setup s = setup(api, "TPV turno", 10);
        String cashier = api.login(data.createStaff(s.tenant().tenantId(), RoleCode.CASHIER,
                Set.of(UUID.fromString(s.venueId()))), TestDataFactory.PASSWORD);

        assertThat((Object) api.get("/api/pos/terminals/" + s.terminalId() + "/screen", cashier).json("$.session")).isNull();
        // Sin caja abierta no se vende
        assertThat(sell(api, cashier, s, "{\"items\":[{\"variantId\":\"%s\",\"quantity\":1}],\"paymentMethod\":\"CARD\"}"
                .formatted(s.cana())).status()).isEqualTo(409);
        var session = api.post("/api/pos/terminals/" + s.terminalId() + "/cash-sessions", cashier, "{\"openingFloat\":100}");
        assertStatus(session, 201);
        String sessionId = session.json("$.id");
        assertThat(api.post("/api/pos/terminals/" + s.terminalId() + "/cash-sessions", s.admin(), "{\"openingFloat\":50}")
                .status()).isEqualTo(409);

        // Venta en efectivo: 2 canas + 1 mojito = 15 €, entrega 20 -> cambio 5
        String body = """
                {"items":[{"variantId":"%s","quantity":2},{"variantId":"%s","quantity":1}],
                 "paymentMethod":"CASH","cashReceived":20}""".formatted(s.cana(), s.mojito());
        String key = "pos-" + UUID.randomUUID();
        var sale = api.postWithHeader("/api/pos/terminals/" + s.terminalId() + "/sales", cashier, body, "Idempotency-Key", key);
        assertStatus(sale, 201);
        assertThat(((Number) sale.json("$.totalAmount")).doubleValue()).isEqualTo(15.0);
        assertThat(((Number) sale.json("$.changeGiven")).doubleValue()).isEqualTo(5.0);
        // Doble toque con la misma clave: misma venta, el stock no baja dos veces
        var again = api.postWithHeader("/api/pos/terminals/" + s.terminalId() + "/sales", cashier, body, "Idempotency-Key", key);
        assertThat((String) again.json("$.reference")).isEqualTo(sale.json("$.reference"));
        assertThat(stockOf(api, s, s.cana())).isEqualTo(8);

        // Tarjeta (1 cana). El stock baja pero la venta nunca se bloquea por existencias (ver test de carrera)
        var card = sell(api, cashier, s, "{\"items\":[{\"variantId\":\"%s\",\"quantity\":1}],\"paymentMethod\":\"CARD\"}"
                .formatted(s.cana()));
        assertStatus(card, 201);
        assertThat(stockOf(api, s, s.cana())).isEqualTo(7);

        // Descuento: el cajero no puede solo; con email y contrasena del administrador si
        String discount = """
                {"items":[{"variantId":"%s","quantity":1}],"paymentMethod":"CASH","cashReceived":4.50,
                 "discount":{"type":"PERCENTAGE","value":50,"reason":"Invitacion socio"%s}}""";
        assertThat(sell(api, cashier, s, discount.formatted(s.mojito(), "")).status()).isEqualTo(403);
        assertThat(sell(api, cashier, s, discount.formatted(s.mojito(),
                ",\"supervisorEmail\":\"%s\",\"supervisorPassword\":\"mala\"".formatted(s.tenant().adminEmail()))).status())
                .isEqualTo(403);
        var discounted = sell(api, cashier, s, discount.formatted(s.mojito(),
                ",\"supervisorEmail\":\"%s\",\"supervisorPassword\":\"%s\"".formatted(s.tenant().adminEmail(), TestDataFactory.PASSWORD)));
        assertStatus(discounted, 201);
        assertThat(((Number) discounted.json("$.totalAmount")).doubleValue()).isEqualTo(4.5);
        assertThat((String) discounted.json("$.discountAuthorizedByName")).isNotNull();

        // Retirada de efectivo y anulacion (solo supervisor) de la venta con tarjeta
        assertStatus(api.post("/api/pos/cash-sessions/" + sessionId + "/movements", cashier,
                "{\"type\":\"CASH_OUT\",\"amount\":10,\"reason\":\"Retirada a caja fuerte\"}"), 200);
        String voidUrl = "/api/pos/sales/" + card.json("$.id") + "/void";
        assertThat(api.post(voidUrl, cashier, "{\"reason\":\"Error\"}").status()).isEqualTo(403);
        assertThat((String) api.post(voidUrl, s.admin(), "{\"reason\":\"Cobro duplicado\"}").json("$.status"))
                .isEqualTo("VOIDED");
        assertThat(api.post(voidUrl, s.admin(), "{\"reason\":\"Otra vez\"}").status()).isEqualTo(409);
        assertThat(stockOf(api, s, s.cana())).isEqualTo(8);

        // Cierre: esperado = 100 + 15 + 4,50 - 10 = 109,50; contado 110 -> sobran 0,50
        var closed = api.post("/api/pos/cash-sessions/" + sessionId + "/close", cashier, "{\"countedCash\":110}");
        assertStatus(closed, 200);
        assertThat(((Number) closed.json("$.summary.expectedCash")).doubleValue()).isEqualTo(109.5);
        assertThat(((Number) closed.json("$.difference")).doubleValue()).isEqualTo(0.5);
        assertThat(((Number) closed.json("$.summary.cardSales")).doubleValue()).isZero();
        assertThat(((Number) closed.json("$.summary.voidedCount")).intValue()).isEqualTo(1);
        assertThat((String) closed.json("$.summary.topProducts[0].productName")).isEqualTo("Cerveza");
        // Con la caja cerrada ya no se vende ni se cierra otra vez
        assertThat(sell(api, cashier, s, "{\"items\":[{\"variantId\":\"%s\",\"quantity\":1}],\"paymentMethod\":\"CARD\"}"
                .formatted(s.cana())).status()).isEqualTo(409);
        assertThat(api.post("/api/pos/cash-sessions/" + sessionId + "/close", cashier, "{\"countedCash\":1}").status())
                .isEqualTo(409);
        assertThat(api.get("/api/pos/cash-sessions", s.admin()).body()).contains(sessionId);
    }

    @Test
    void concurrentSalesAreNeverBlockedAndStockStaysExact() throws Exception {
        var api = api();
        Setup s = setup(api, "TPV carrera", 5);
        assertStatus(api.post("/api/pos/terminals/" + s.terminalId() + "/cash-sessions", s.admin(), "{\"openingFloat\":0}"), 201);

        List<Integer> statuses = Concurrently.run(10, i -> sell(api, s.admin(), s,
                "{\"items\":[{\"variantId\":\"%s\",\"quantity\":1}],\"paymentMethod\":\"CARD\"}".formatted(s.cana())).status());

        // Como en cualquier TPV de hosteleria: se vende siempre y el stock teorico queda en negativo
        // (exacto, sin perder ninguna venta aunque lleguen a la vez) hasta el recuento.
        assertThat(statuses.stream().filter(st -> st == 201).count()).isEqualTo(10);
        assertThat(stockOf(api, s, s.cana())).isEqualTo(-5);
    }

    @Test
    void concurrentOpeningsCreateASingleCashSession() throws Exception {
        var api = api();
        Setup s = setup(api, "TPV apertura", 0);
        List<Integer> statuses = Concurrently.run(5, i -> api.post("/api/pos/terminals/" + s.terminalId()
                + "/cash-sessions", s.admin(), "{\"openingFloat\":50}").status());
        assertThat(statuses.stream().filter(st -> st == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(st -> st == 409).count()).isEqualTo(4);
    }

    @Test
    void venueAssignmentPermissionsAndTenantIsolation() {
        var api = api();
        Setup s = setup(api, "TPV permisos", 3);
        String screen = "/api/pos/terminals/" + s.terminalId() + "/screen";
        String unassigned = api.login(data.createStaff(s.tenant().tenantId(), RoleCode.CASHIER, Set.of()),
                TestDataFactory.PASSWORD);
        assertThat(api.get(screen, unassigned).status()).isEqualTo(403);
        assertThat((List<?>) api.get("/api/pos/my-terminals", unassigned).json("$")).isEmpty();
        String door = api.login(data.createStaff(s.tenant().tenantId(), RoleCode.DOOR_STAFF,
                Set.of(UUID.fromString(s.venueId()))), TestDataFactory.PASSWORD);
        assertThat(api.get(screen, door).status()).isEqualTo(403);

        String cashier = api.login(data.createStaff(s.tenant().tenantId(), RoleCode.CASHIER,
                Set.of(UUID.fromString(s.venueId()))), TestDataFactory.PASSWORD);
        var pos = api.get(screen, cashier);
        assertStatus(pos, 200);
        assertThat(pos.body()).contains("Cerveza").contains("Mojito");
        assertThat(api.post("/api/pos/categories", cashier, "{\"name\":\"X\",\"sortOrder\":0,\"active\":true}").status())
                .isEqualTo(403);
        assertThat(api.post("/api/inventory/warehouses/" + s.warehouseId() + "/movements", cashier,
                "{\"variantId\":\"%s\",\"type\":\"IN\",\"quantity\":1}".formatted(s.cana())).status()).isEqualTo(403);

        String other = api.login(data.createTenantWithAdmin("TPV otra").adminEmail(), TestDataFactory.PASSWORD);
        assertThat(api.get(screen, other).status()).isEqualTo(404);
        assertThat(api.get("/api/inventory/warehouses/" + s.warehouseId() + "/stock", other).status()).isEqualTo(404);
        assertThat(api.post("/api/pos/terminals/" + s.terminalId() + "/cash-sessions", other, "{\"openingFloat\":0}")
                .status()).isEqualTo(404);
        // Un producto de otra empresa no se puede vender en este terminal
        assertStatus(api.post("/api/pos/terminals/" + s.terminalId() + "/cash-sessions", s.admin(), "{\"openingFloat\":0}"), 201);
        Setup foreign = setup(api, "TPV ajeno", 3);
        assertThat(sell(api, s.admin(), s, "{\"items\":[{\"variantId\":\"%s\",\"quantity\":1}],\"paymentMethod\":\"CARD\"}"
                .formatted(foreign.cana())).status()).isEqualTo(404);
    }

    @Test
    void inventoryMovementsAndLowStockAlerts() {
        var api = api();
        Setup s = setup(api, "Inventario", 10);
        String base = "/api/inventory/warehouses/" + s.warehouseId();
        assertStatus(api.put(base + "/stock/" + s.cana() + "/minimum", s.admin(), "{\"minQuantity\":3}"), 200);

        // Salida que deja 2 (por debajo del minimo 3) -> alerta
        assertStatus(api.post(base + "/movements", s.admin(),
                "{\"variantId\":\"%s\",\"type\":\"OUT\",\"quantity\":8,\"reason\":\"Rotura\"}".formatted(s.cana())), 200);
        assertThat(api.get("/api/alerts", s.admin()).body()).contains("LOW_INVENTORY");
        // Mas de lo que hay: 409
        assertThat(api.post(base + "/movements", s.admin(),
                "{\"variantId\":\"%s\",\"type\":\"OUT\",\"quantity\":5}".formatted(s.cana())).status()).isEqualTo(409);
        // Reposicion por encima del minimo -> la alerta se retira
        assertStatus(api.post(base + "/movements", s.admin(),
                "{\"variantId\":\"%s\",\"type\":\"IN\",\"quantity\":5}".formatted(s.cana())), 200);
        assertThat(api.get("/api/alerts", s.admin()).body()).doesNotContain("LOW_INVENTORY");
        // Recuento: habia 7, se cuentan 4 -> movimiento de -3
        var adjusted = api.post(base + "/movements", s.admin(),
                "{\"variantId\":\"%s\",\"type\":\"ADJUSTMENT\",\"quantity\":4,\"reason\":\"Recuento\"}".formatted(s.cana()));
        assertStatus(adjusted, 200);
        assertThat(stockOf(api, s, s.cana())).isEqualTo(4);
        var movements = api.get(base + "/movements", s.admin());
        assertThat(((Number) movements.json("$.content[0].quantity")).intValue()).isEqualTo(-3);
        assertThat((String) movements.json("$.content[0].type")).isEqualTo("ADJUSTMENT");
        // Un producto sin control de existencias no admite movimientos; tampoco los tipos del TPV
        assertThat(api.post(base + "/movements", s.admin(),
                "{\"variantId\":\"%s\",\"type\":\"IN\",\"quantity\":1}".formatted(s.mojito())).status()).isEqualTo(400);
        assertThat(api.post(base + "/movements", s.admin(),
                "{\"variantId\":\"%s\",\"type\":\"SALE\",\"quantity\":1}".formatted(s.cana())).status()).isEqualTo(400);
    }
}
