package com.bloop.pos;

import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.ApiClient;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.bloop.support.TestDataFactory.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Combinados con receta: una copa de ron con refresco descuenta 50 ml de la botella de ron y una
 * lata de refresco (no una botella entera), el precio suma el suplemento del refresco y anular la
 * venta devuelve exactamente lo descontado.
 */
class PosRecipeIntegrationTest extends AbstractIntegrationTest {

    @Test
    void combinedDrinkConsumesRecipeAndMixer() {
        ApiClient api = api();
        var tenant = data.createTenantWithAdmin("Recetas");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        String venueId = data.createVenue(api, admin, 500);
        String warehouseId = api.post("/api/inventory/warehouses", admin,
                "{\"venueId\":\"%s\",\"name\":\"Barra\",\"active\":true}".formatted(venueId)).json("$.id");
        String rumCat = api.post("/api/pos/categories", admin,
                "{\"name\":\"Ron\",\"color\":\"amber\",\"sortOrder\":0,\"active\":true}").json("$.id");
        String softCat = api.post("/api/pos/categories", admin,
                "{\"name\":\"Refrescos\",\"color\":\"sky\",\"sortOrder\":1,\"active\":true}").json("$.id");

        // Botella de 700 ml contada en ml
        var rum = api.post("/api/pos/products", admin, """
                {"categoryId":"%s","name":"Ron Barcelo","trackStock":true,"sortOrder":0,"active":true,
                 "variants":[{"name":"Botella 70 cl","price":90.00,"sortOrder":0,"active":true,
                              "stockUnit":"ML","unitSizeMl":700,"cost":14.50}]}""".formatted(rumCat));
        assertStatus(rum, 201);
        String bottle = rum.json("$.variants[0].id");
        // Refresco (lata) elegible como mezclador; Red Bull con suplemento
        var cola = api.post("/api/pos/products", admin, """
                {"categoryId":"%s","name":"Coca-Cola","trackStock":true,"sortOrder":0,"active":true,"mixerSurcharge":0,
                 "variants":[{"name":"Lata","price":3.00,"sortOrder":0,"active":true}]}""".formatted(softCat));
        var redbull = api.post("/api/pos/products", admin, """
                {"categoryId":"%s","name":"Red Bull","trackStock":true,"sortOrder":1,"active":true,"mixerSurcharge":2.00,
                 "variants":[{"name":"Lata","price":4.00,"sortOrder":0,"active":true}]}""".formatted(softCat));
        String colaCan = cola.json("$.variants[0].id");
        String redbullCan = redbull.json("$.variants[0].id");

        // La copa: receta de 50 ml de la botella y pide refresco
        var updated = api.put("/api/pos/products/" + rum.json("$.id"), admin, """
                {"categoryId":"%s","name":"Ron Barcelo","trackStock":true,"sortOrder":0,"active":true,
                 "variants":[{"id":"%s","name":"Botella 70 cl","price":90.00,"sortOrder":0,"active":true,
                              "stockUnit":"ML","unitSizeMl":700},
                             {"name":"Copa","price":8.00,"sortOrder":1,"active":true,"requiresMixer":true,
                              "recipe":[{"componentVariantId":"%s","quantity":50}]}]}"""
                .formatted(rumCat, bottle, bottle));
        assertStatus(updated, 200);
        String glass = updated.json("$.variants[1].id");
        assertThat((Integer) updated.json("$.variants[1].recipe[0].quantity")).isEqualTo(50);

        // Existencias: 1 botella (700 ml) y 10 latas de cada refresco
        movement(api, admin, warehouseId, bottle, 700);
        movement(api, admin, warehouseId, colaCan, 10);
        movement(api, admin, warehouseId, redbullCan, 10);
        // La copa no es una existencia: no aparece en el inventario
        List<Map<String, Object>> stock = api.get("/api/inventory/warehouses/" + warehouseId + "/stock", admin).json("$");
        assertThat(stock).noneMatch(i -> glass.equals(i.get("variantId")));

        String terminalId = api.post("/api/pos/terminals", admin, """
                {"name":"Barra 1","venueId":"%s","warehouseId":"%s","type":"BAR","active":true}
                """.formatted(venueId, warehouseId)).json("$.id");
        assertStatus(api.post("/api/pos/terminals/" + terminalId + "/cash-sessions", admin, "{\"openingFloat\":0}"), 201);

        assertThat(api.get("/api/pos/products", admin).body()).as("catalogo").contains("Copa");
        // La pantalla muestra los refrescos y que la copa pide refresco
        var screen = api.get("/api/pos/terminals/" + terminalId + "/screen", admin);
        List<Map<String, Object>> mixers = screen.json("$.mixers");
        assertThat(mixers).extracting(m -> m.get("name")).contains("Coca-Cola", "Red Bull");
        assertThat(screen.body()).contains("\"requiresMixer\":true");

        // Sin refresco: 400
        assertThat(sell(api, admin, terminalId, "{\"items\":[{\"variantId\":\"%s\",\"quantity\":1}],\"paymentMethod\":\"CARD\"}"
                .formatted(glass)).status()).isEqualTo(400);

        // 2 copas con Coca-Cola + 1 con Red Bull (suplemento 2 EUR) = 8+8+10
        var sale = sell(api, admin, terminalId, """
                {"items":[{"variantId":"%s","quantity":2,"mixerVariantId":"%s"},
                          {"variantId":"%s","quantity":1,"mixerVariantId":"%s"}],"paymentMethod":"CARD"}"""
                .formatted(glass, colaCan, glass, redbullCan));
        assertStatus(sale, 201);
        assertThat(((Number) sale.json("$.totalAmount")).doubleValue()).isEqualTo(26.0);
        assertThat(sale.body()).contains("Red Bull");

        assertThat(quantity(api, admin, warehouseId, bottle)).isEqualTo(700 - 150); // 3 copas x 50 ml
        assertThat(quantity(api, admin, warehouseId, colaCan)).isEqualTo(8);
        assertThat(quantity(api, admin, warehouseId, redbullCan)).isEqualTo(9);

        // Anular devuelve exactamente lo descontado
        assertStatus(api.post("/api/pos/sales/" + sale.json("$.id") + "/void", admin, "{\"reason\":\"Error\"}"), 200);
        assertThat(quantity(api, admin, warehouseId, bottle)).isEqualTo(700);
        assertThat(quantity(api, admin, warehouseId, colaCan)).isEqualTo(10);
        assertThat(quantity(api, admin, warehouseId, redbullCan)).isEqualTo(10);

        // 15 copas de una botella de 700 ml: se venden igual (no se bloquea) y el stock teorico queda en -50 ml
        assertStatus(sell(api, admin, terminalId, "{\"items\":[{\"variantId\":\"%s\",\"quantity\":15,\"mixerVariantId\":\"%s\"}],\"paymentMethod\":\"CARD\"}"
                .formatted(glass, colaCan)), 201);
        assertThat(quantity(api, admin, warehouseId, bottle)).isEqualTo(-50);
    }

    private static void movement(ApiClient api, String admin, String warehouseId, String variantId, int qty) {
        assertStatus(api.post("/api/inventory/warehouses/" + warehouseId + "/movements", admin,
                "{\"variantId\":\"%s\",\"type\":\"IN\",\"quantity\":%d,\"reason\":\"Compra\"}".formatted(variantId, qty)), 200);
    }

    private static int quantity(ApiClient api, String admin, String warehouseId, String variantId) {
        List<Map<String, Object>> stock = api.get("/api/inventory/warehouses/" + warehouseId + "/stock", admin).json("$");
        return stock.stream().filter(i -> variantId.equals(i.get("variantId")))
                .map(i -> ((Number) i.get("quantity")).intValue()).findFirst().orElseThrow();
    }

    private static ApiClient.Response sell(ApiClient api, String token, String terminalId, String body) {
        return api.postWithHeader("/api/pos/terminals/" + terminalId + "/sales", token, body, "Idempotency-Key",
                "pos-" + UUID.randomUUID());
    }
}
