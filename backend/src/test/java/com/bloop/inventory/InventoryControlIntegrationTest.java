package com.bloop.inventory;

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
 * Inventario como en una discoteca: el proveedor trae 2 cajas de ron (albaran y coste), se pasan 3
 * botellas a la barra, se venden 20 cubatas (sin bloquear), se cuenta al cerrar y el control da la
 * merma. El proveedor se lleva los cascos vacios.
 */
class InventoryControlIntegrationTest extends AbstractIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void supplierEntryTransferSalesCountAndMerma() {
        ApiClient api = api();
        var tenant = data.createTenantWithAdmin("Inventario discoteca");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        String venueId = data.createVenue(api, admin, 500);
        String store = api.post("/api/inventory/warehouses", admin,
                "{\"venueId\":\"%s\",\"name\":\"Almacen\",\"active\":true}".formatted(venueId)).json("$.id");
        String bar = api.post("/api/inventory/warehouses", admin,
                "{\"venueId\":\"%s\",\"name\":\"Barra\",\"active\":true}".formatted(venueId)).json("$.id");

        // Botella de ron (solo inventario, retornable) y combinado de ron con receta de 50 ml
        String supply = api.post("/api/pos/categories", admin,
                "{\"name\":\"Botellas\",\"color\":\"zinc\",\"sortOrder\":9,\"active\":true,\"kind\":\"SUPPLY\"}").json("$.id");
        String combinados = api.post("/api/pos/categories", admin,
                "{\"name\":\"Combinados\",\"color\":\"violet\",\"sortOrder\":0,\"active\":true}").json("$.id");
        var rum = api.post("/api/pos/products", admin, """
                {"categoryId":"%s","name":"Ron","trackStock":true,"sortOrder":0,"active":true,
                 "variants":[{"name":"Botella 70 cl","price":0,"sortOrder":0,"active":true,"stockUnit":"ML",
                              "unitSizeMl":700,"returnable":true}]}""".formatted(supply));
        assertStatus(rum, 201);
        String bottle = rum.json("$.variants[0].id");
        var cubata = api.post("/api/pos/products", admin, """
                {"categoryId":"%s","name":"Ron","trackStock":true,"sortOrder":0,"active":true,
                 "variants":[{"name":"Cubata","price":9,"sortOrder":0,"active":true,
                              "recipe":[{"componentVariantId":"%s","quantity":50}]}]}""".formatted(combinados, bottle));
        assertStatus(cubata, 201);
        String cubataId = cubata.json("$.variants[0].id");

        // Proveedor y entrada: 2 cajas de 6 botellas (12 x 700 ml) con albaran y coste por botella
        var supplier = api.post("/api/inventory/suppliers", admin,
                "{\"name\":\"Distribuciones Norte\",\"phone\":\"600111222\",\"active\":true}");
        assertStatus(supplier, 201);
        String supplierId = supplier.json("$.id");
        assertThat(api.post("/api/inventory/suppliers", admin, "{\"name\":\"distribuciones norte\",\"active\":true}")
                .status()).isEqualTo(409);
        assertStatus(api.post("/api/inventory/warehouses/" + store + "/movements", admin, """
                {"variantId":"%s","type":"IN","quantity":%d,"supplierId":"%s","documentRef":"ALB-001","unitCost":12.50}
                """.formatted(bottle, 12 * 700, supplierId)), 200);

        // Traspaso de 3 botellas a la barra; mas de las que hay -> 409
        assertStatus(api.post("/api/inventory/transfers", admin, """
                {"fromWarehouseId":"%s","toWarehouseId":"%s","variantId":"%s","quantity":%d}
                """.formatted(store, bar, bottle, 3 * 700)), 204);
        assertThat(api.post("/api/inventory/transfers", admin, """
                {"fromWarehouseId":"%s","toWarehouseId":"%s","variantId":"%s","quantity":%d}
                """.formatted(store, bar, bottle, 100 * 700)).status()).isEqualTo(409);

        // 20 cubatas en la barra (1000 ml teoricos)
        String terminal = api.post("/api/pos/terminals", admin, """
                {"name":"Barra 1","venueId":"%s","warehouseId":"%s","type":"BAR","active":true}
                """.formatted(venueId, bar)).json("$.id");
        assertStatus(api.post("/api/pos/terminals/" + terminal + "/cash-sessions", admin, "{\"openingFloat\":0}"), 201);
        assertStatus(api.postWithHeader("/api/pos/terminals/" + terminal + "/sales", admin,
                "{\"items\":[{\"variantId\":\"%s\",\"quantity\":20}],\"paymentMethod\":\"CARD\"}".formatted(cubataId),
                "Idempotency-Key", "pos-" + UUID.randomUUID()), 201);

        // Recuento de cierre: quedan 1,5 botellas (1050 ml); deberian quedar 1100: faltan 50 ml
        assertStatus(api.post("/api/inventory/warehouses/" + bar + "/count", admin,
                "{\"items\":[{\"variantId\":\"%s\",\"quantity\":1050}]}".formatted(bottle)), 200);
        var control = api.get("/api/inventory/warehouses/" + bar + "/control", admin);
        assertStatus(control, 200);
        Map<String, Object> row = ((List<Map<String, Object>>) control.json("$.rows")).stream()
                .filter(r -> bottle.equals(r.get("variantId"))).findFirst().orElseThrow();
        assertThat(((Number) row.get("received")).intValue()).isEqualTo(2100);
        assertThat(((Number) row.get("sold")).intValue()).isEqualTo(1000);
        assertThat(((Number) row.get("merma")).intValue()).isEqualTo(-50);
        assertThat(((Number) row.get("closing")).intValue()).isEqualTo(1050);
        assertThat(((Number) row.get("mermaValue")).doubleValue()).isEqualTo(0.89); // 50 ml a 12,50 EUR/700 ml

        // Pedido sugerido: minimo de 5 botellas en la barra
        assertStatus(api.put("/api/inventory/warehouses/" + bar + "/stock/" + bottle + "/minimum", admin,
                "{\"minQuantity\":%d}".formatted(5 * 700)), 200);
        var order = api.get("/api/inventory/warehouses/" + bar + "/suggested-order", admin);
        assertThat((String) order.json("$[0].supplierName")).isEqualTo("Distribuciones Norte");
        assertThat(((Number) order.json("$[0].missing")).intValue()).isEqualTo(5 * 700 - 1050);

        // Envases: 12 botellas recibidas; el proveedor se lleva 3 cascos -> quedan 9 pendientes
        var returned = api.post("/api/inventory/container-returns", admin,
                "{\"supplierId\":\"%s\",\"variantId\":\"%s\",\"quantity\":3,\"documentRef\":\"ALB-002\"}"
                        .formatted(supplierId, bottle));
        assertStatus(returned, 200);
        assertThat(((Number) returned.json("$[0].received")).intValue()).isEqualTo(12);
        assertThat(((Number) returned.json("$[0].pending")).intValue()).isEqualTo(9);

        // Otra empresa no ve nada
        String other = api.login(data.createTenantWithAdmin("Otra inventario").adminEmail(), TestDataFactory.PASSWORD);
        assertThat(api.get("/api/inventory/warehouses/" + bar + "/control", other).status()).isEqualTo(404);
        assertThat((List<?>) api.get("/api/inventory/suppliers", other).json("$")).isEmpty();
        assertThat(api.post("/api/inventory/container-returns", other,
                "{\"supplierId\":\"%s\",\"variantId\":\"%s\",\"quantity\":1}".formatted(supplierId, bottle)).status())
                .isEqualTo(404);
    }
}
