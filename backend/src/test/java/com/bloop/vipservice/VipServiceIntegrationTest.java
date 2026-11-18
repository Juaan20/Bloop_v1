package com.bloop.vipservice;

import com.bloop.shared.enums.RoleCode;
import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.ApiClient;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.bloop.support.TestDataFactory.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flujo VIP completo: plano y paquete de la mesa, pago previo obligatorio para entrar, QR de la mesa,
 * pedidos desde el movil (paquete + adicionales), panel de servicio, cobro y cierre de cuenta.
 */
class VipServiceIntegrationTest extends AbstractIntegrationTest {

    @Test
    void fullVipNightFromReservationToClosedAccount() {
        var api = api();
        var tenant = data.createTenantWithAdmin("VIP servicio");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "10.00", 100, 10);

        // Carta VIP: una botella
        var category = api.post("/api/pos/categories", admin,
                "{\"name\":\"Botellas\",\"color\":\"violet\",\"sortOrder\":0,\"active\":true,\"kind\":\"VIP_BOTTLE\"}");
        assertStatus(category, 201);
        var product = api.post("/api/pos/products", admin, """
                {"categoryId":"%s","name":"Ginebra","trackStock":false,"sortOrder":0,"active":true,
                 "variants":[{"name":"Botella","price":150,"sortOrder":0,"active":true}]}
                """.formatted((String) category.json("$.id")));
        assertStatus(product, 201);
        String gin = product.json("$.variants[0].id");

        // Mesa con precio de reserva 200 EUR que incluye 1 botella, colocada en el plano
        var zone = api.post("/api/venues/" + event.venueId() + "/vip-zones", admin,
                "{\"name\":\"Escenario\",\"active\":true,\"sortOrder\":0}");
        String zoneId = zone.json("$.id");
        var table = api.post("/api/vip-zones/" + zoneId + "/tables", admin,
                "{\"name\":\"Mesa 1\",\"capacity\":6,\"minSpend\":0,\"depositAmount\":200,\"active\":true}");
        String tableId = table.json("$.tables[0].id");
        assertStatus(api.put("/api/vip-tables/" + tableId + "/package", admin,
                "{\"items\":[{\"variantId\":\"%s\",\"quantity\":1}]}".formatted(gin)), 200);
        var layout = api.put("/api/venues/" + event.venueId() + "/vip-layout", admin,
                "{\"tables\":[{\"id\":\"%s\",\"posX\":10,\"posY\":20,\"width\":9,\"height\":7,\"shape\":\"RECT\"}]}"
                        .formatted(tableId));
        assertStatus(layout, 200);
        assertThat((String) layout.json("$[0].tables[0].shape")).isEqualTo("RECT");
        assertThat((String) layout.json("$[0].tables[0].packageItems[0].productName")).isEqualTo("Ginebra · Botella");

        // Reserva hecha por el equipo para la sesion
        var reservation = api.post("/api/reservations", admin, """
                {"tableId":"%s","sessionId":"%s","guests":4,"contactName":"Ana Lopez","contactEmail":"ana@test.local"}
                """.formatted(tableId, event.sessionId()));
        assertStatus(reservation, 201);
        String id = reservation.json("$.id");
        if ("PENDING".equals(reservation.json("$.status"))) {
            assertStatus(api.post("/api/reservations/" + id + "/status", admin, "{\"status\":\"CONFIRMED\"}"), 200);
        }
        // El plano se consulta por fiesta
        String mapUrl = "/api/venues/" + event.venueId() + "/vip-map?sessionId=" + event.sessionId();
        assertThat(api.get("/api/venues/" + event.venueId() + "/vip-parties", admin).body()).contains(event.sessionId());
        assertThat((String) api.get(mapUrl, admin).json("$.tables[0].status")).isEqualTo("PENDING_PAYMENT");

        // Sin pagar la reserva y el paquete no se puede entrar
        assertThat(api.post("/api/reservations/" + id + "/status", admin, "{\"status\":\"SEATED\"}").status())
                .isEqualTo(400);
        assertStatus(api.post("/api/reservations/" + id + "/deposit", admin, null), 200);
        assertThat((String) api.get(mapUrl, admin).json("$.tables[0].status")).isEqualTo("PAID");
        assertStatus(api.post("/api/reservations/" + id + "/status", admin, "{\"status\":\"SEATED\"}"), 200);
        assertThat((String) api.get(mapUrl, admin).json("$.tables[0].status")).isEqualTo("OCCUPIED");

        // Al sentarse, la mesa tiene QR
        var detail = api.get("/api/reservations/" + id + "/vip", admin);
        String token = detail.json("$.qr.token");
        assertThat(token).hasSizeGreaterThan(40);
        assertThat((Boolean) detail.json("$.qr.active")).isTrue();

        // El grupo abre la carta con el QR: sin datos personales, con su paquete
        var guest = api.get("/api/public/vip-tables/" + token, null);
        assertStatus(guest, 200);
        assertThat(guest.body()).doesNotContain("ana@test.local");
        assertThat((Boolean) guest.json("$.canOrder")).isTrue();
        assertThat((Integer) guest.json("$.packageItems[0].remaining")).isEqualTo(1);

        // Pide 2 botellas: 1 sale del paquete (0 EUR) y 1 es adicional (150 EUR). Un reintento no duplica.
        String orderBody = "{\"idempotencyKey\":\"movil-1\",\"lines\":[{\"variantId\":\"%s\",\"quantity\":2}]}"
                .formatted(gin);
        var order = api.post("/api/public/vip-tables/" + token + "/orders", null, orderBody);
        assertStatus(order, 201);
        String orderId = order.json("$.id");
        assertThat(((Number) order.json("$.extraTotal")).doubleValue()).isEqualTo(150.0);
        assertThat((List<?>) order.json("$.items")).hasSize(2);
        assertThat((String) api.post("/api/public/vip-tables/" + token + "/orders", null, orderBody).json("$.id"))
                .isEqualTo(orderId);
        assertThat((String) api.get(mapUrl, admin).json("$.tables[0].status")).isEqualTo("SERVICE_REQUESTED");

        // El camarero VIP lo ve en su panel y lo avanza paso a paso; el portero no puede
        String waiter = api.login(data.createStaff(tenant.tenantId(), RoleCode.VIP_WAITER,
                Set.of(UUID.fromString(event.venueId()))), TestDataFactory.PASSWORD);
        String door = api.login(data.createStaff(tenant.tenantId(), RoleCode.DOOR_STAFF,
                Set.of(UUID.fromString(event.venueId()))), TestDataFactory.PASSWORD);
        assertThat(api.get("/api/venues/" + event.venueId() + "/vip-orders", waiter).body()).contains(orderId);
        String statusUrl = "/api/vip-orders/" + orderId + "/status";
        assertThat(api.post(statusUrl, door, "{\"status\":\"PENDING\"}").status()).isEqualTo(403);
        assertThat(api.post(statusUrl, waiter, "{\"status\":\"DELIVERED\"}").status()).isEqualTo(400); // sin saltos
        for (String step : List.of("PENDING", "PREPARING", "READY", "ON_THE_WAY", "DELIVERED")) {
            assertStatus(api.post(statusUrl, waiter, "{\"status\":\"%s\"}".formatted(step)), 200);
        }
        var delivered = api.get("/api/reservations/" + id + "/vip", admin);
        assertThat((String) delivered.json("$.orders[0].status")).isEqualTo("DELIVERED");
        assertThat((String) delivered.json("$.orders[0].deliveredByName")).isNotBlank();
        assertThat(((Number) delivered.json("$.account.pendingTotal")).doubleValue()).isEqualTo(150.0);

        // No se puede cerrar con importes pendientes ni cobrar de mas; el cobro es idempotente
        assertThat(api.post("/api/reservations/" + id + "/account/close", admin, null).status()).isEqualTo(400);
        assertThat(api.post("/api/reservations/" + id + "/account/payments", admin,
                "{\"amount\":200,\"method\":\"CASH\",\"idempotencyKey\":\"p0\"}").status()).isEqualTo(400);
        String pay = "{\"amount\":150,\"method\":\"CARD\",\"idempotencyKey\":\"p1\"}";
        assertStatus(api.post("/api/reservations/" + id + "/account/payments", admin, pay), 200);
        var paid = api.post("/api/reservations/" + id + "/account/payments", admin, pay);
        assertThat((List<?>) paid.json("$.account.payments")).hasSize(1);
        assertThat((Boolean) paid.json("$.account.canClose")).isTrue();

        // Cierre: reserva terminada y el QR deja de funcionar
        var closed = api.post("/api/reservations/" + id + "/account/close", admin, null);
        assertStatus(closed, 200);
        assertThat((String) closed.json("$.reservation.status")).isEqualTo("COMPLETED");
        assertThat(api.get("/api/public/vip-tables/" + token, null).status()).isEqualTo(404);
        assertThat(api.post("/api/public/vip-tables/" + token + "/orders", null,
                orderBody.replace("movil-1", "movil-2")).status()).isEqualTo(404);

        // Mesa liberada: vuelve a estar libre en la MISMA fiesta y la reserva anterior queda en el historial
        var freed = api.get(mapUrl, admin);
        assertThat((String) freed.json("$.tables[0].status")).isEqualTo("AVAILABLE");
        assertThat((String) freed.json("$.tables[0].history[0].status")).isEqualTo("COMPLETED");
        var again = api.post("/api/reservations", admin, """
                {"tableId":"%s","sessionId":"%s","guests":3,"contactName":"Segundo grupo"}
                """.formatted(tableId, event.sessionId()));
        assertStatus(again, 201);
        assertThat((String) api.get(mapUrl, admin).json("$.tables[0].reservation.contactName")).isEqualTo("Segundo grupo");
        // Los pedidos y pagos del primer grupo se conservan
        assertThat((List<?>) api.get("/api/reservations/" + id + "/vip", admin).json("$.account.payments")).hasSize(1);
    }

    @Test
    void boxOfficeSellsATableAndEachVenueHasItsOwnShape() {
        var api = api();
        var tenant = data.createTenantWithAdmin("VIP taquilla");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        String venueId = data.createVenue(api, admin, 500);

        // Dos salas en el mismo local: cada una con su propio plano
        var zone = api.post("/api/venues/" + venueId + "/vip-zones", admin, "{\"name\":\"Pista\",\"active\":true,\"sortOrder\":0}");
        var terrace = api.post("/api/venues/" + venueId + "/vip-zones", admin,
                "{\"name\":\"Terraza\",\"active\":true,\"sortOrder\":1,\"capacity\":120,\"color\":\"sky\"}");
        assertThat((Integer) terrace.json("$.capacity")).isEqualTo(120);
        String zoneId = zone.json("$.id");

        // Forma en L: rejilla 10x8 con la esquina superior derecha fuera de la sala
        assertThat(api.get("/api/vip-zones/" + zoneId + "/floor-plan", admin).body()).isEmpty();
        StringBuilder cells = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 10; c++) {
                cells.append(r < 4 && c >= 5 ? '0' : '1');
            }
        }
        String plan = """
                {"cols":10,"rows":8,"cells":"%s","elements":[{"type":"BAR","label":"Barra","x":5,"y":80,"w":30,"h":8}]}
                """.formatted(cells);
        assertStatus(api.put("/api/vip-zones/" + zoneId + "/floor-plan", admin, plan), 200);
        var saved = api.get("/api/vip-zones/" + zoneId + "/floor-plan", admin);
        assertThat((String) saved.json("$.cells")).isEqualTo(cells.toString());
        assertThat((String) saved.json("$.elements[0].type")).isEqualTo("BAR");
        assertThat(api.get("/api/vip-zones/" + terrace.json("$.id") + "/floor-plan", admin).body()).isEmpty();
        assertThat(api.put("/api/vip-zones/" + zoneId + "/floor-plan", admin,
                plan.replace("\"cols\":10", "\"cols\":11")).status()).isEqualTo(400); // rejilla incoherente
        // Otra empresa no ve ni toca el plano de esta sala
        var other = data.createTenantWithAdmin("Otra empresa planos");
        String otherAdmin = api.login(other.adminEmail(), TestDataFactory.PASSWORD);
        assertThat(api.get("/api/vip-zones/" + zoneId + "/floor-plan", otherAdmin).status()).isEqualTo(404);
        assertThat(api.put("/api/vip-zones/" + zoneId + "/floor-plan", otherAdmin, plan).status()).isEqualTo(404);
        // Una sala sin reservas se puede borrar
        assertStatus(api.delete("/api/vip-zones/" + terrace.json("$.id"), admin), 204);

        // Venta en taquilla: sin email, cobro y acceso directo con QR
        var table = api.post("/api/vip-zones/" + zoneId + "/tables", admin,
                "{\"name\":\"T1\",\"capacity\":6,\"minSpend\":0,\"depositAmount\":300,\"active\":true}");
        String tableId = table.json("$.tables[0].id");
        // Fiesta en curso en este local
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
        var party = api.post("/api/events", admin, """
                {"venueId":"%s","name":"Fiesta de hoy","firstSession":{"startsAt":"%s","endsAt":"%s","capacity":100}}
                """.formatted(venueId, start, start.plus(7, ChronoUnit.HOURS)));
        assertStatus(party, 201);
        String partyId = party.json("$.sessions[0].id");
        String body = "{\"tableId\":\"%s\",\"guests\":4,\"contactName\":\"Grupo taquilla\",\"sessionId\":\"%s\"%s}";
        // Sin colocar en el plano no se vende
        assertThat(api.post("/api/reservations/walk-in", admin, body.formatted(tableId, partyId, ",\"paymentMethod\":\"CASH\""))
                .status()).isEqualTo(400);
        assertStatus(api.put("/api/venues/" + venueId + "/vip-layout", admin,
                "{\"tables\":[{\"id\":\"%s\",\"posX\":20,\"posY\":30,\"width\":9,\"height\":9,\"shape\":\"ROUND\"}]}".formatted(tableId)), 200);
        assertThat(api.post("/api/reservations/walk-in", admin, body.formatted(tableId, partyId, "")).status())
                .isEqualTo(400); // falta el metodo de pago
        var sold = api.post("/api/reservations/walk-in", admin,
                body.formatted(tableId, partyId, ",\"paymentMethod\":\"CASH\""));
        assertStatus(sold, 201);
        assertThat((String) sold.json("$.status")).isEqualTo("SEATED");
        assertThat((Boolean) sold.json("$.depositPending")).isFalse();
        String id = sold.json("$.id");
        assertThat((Boolean) api.get("/api/reservations/" + id + "/vip", admin).json("$.qr.active")).isTrue();
        assertThat((String) api.get("/api/venues/" + venueId + "/vip-map?sessionId=" + partyId, admin)
                .json("$.tables[0].status")).isEqualTo("OCCUPIED");
        // La mesa ya esta ocupada: otra venta en taquilla se rechaza
        assertThat(api.post("/api/reservations/walk-in", admin,
                body.formatted(tableId, partyId, ",\"paymentMethod\":\"CARD\"")).status()).isEqualTo(409);

        // TPV de servicio VIP: ve la mesa ocupada y le carga una botella (queda pendiente en su cuenta)
        String warehouseId = api.post("/api/inventory/warehouses", admin,
                "{\"venueId\":\"%s\",\"name\":\"Barra\",\"active\":true}".formatted(venueId)).json("$.id");
        String vipCategory = api.post("/api/pos/categories", admin,
                "{\"name\":\"Botellas\",\"color\":\"violet\",\"sortOrder\":0,\"active\":true,\"kind\":\"VIP_BOTTLE\"}").json("$.id");
        String vodka = api.post("/api/pos/products", admin, """
                {"categoryId":"%s","name":"Vodka","trackStock":false,"sortOrder":0,"active":true,
                 "variants":[{"name":"Botella","price":160,"sortOrder":0,"active":true}]}""".formatted(vipCategory))
                .json("$.variants[0].id");
        String vipTerminal = api.post("/api/pos/terminals", admin, """
                {"name":"Servicio VIP","venueId":"%s","warehouseId":"%s","type":"VIP_SERVICE","active":true}
                """.formatted(venueId, warehouseId)).json("$.id");
        String barTerminal = api.post("/api/pos/terminals", admin, """
                {"name":"Barra","venueId":"%s","warehouseId":"%s","type":"BAR","active":true}
                """.formatted(venueId, warehouseId)).json("$.id");
        var screen = api.get("/api/pos/terminals/" + vipTerminal + "/screen", admin);
        assertThat((String) screen.json("$.categories[0].kind")).isEqualTo("VIP_BOTTLE");
        assertThat(api.get("/api/pos/terminals/" + barTerminal + "/screen", admin).body()).doesNotContain("Vodka");
        assertThat(api.get("/api/pos/terminals/" + vipTerminal + "/vip-tables", admin).body()).contains(id);
        String charge = """
                {"reservationId":"%s","order":{"idempotencyKey":"tpv-1","lines":[{"variantId":"%s","quantity":1}]}}
                """.formatted(id, vodka);
        assertThat(api.post("/api/pos/terminals/" + barTerminal + "/vip-charge", admin, charge).status()).isEqualTo(400);
        var charged = api.post("/api/pos/terminals/" + vipTerminal + "/vip-charge", admin, charge);
        assertStatus(charged, 201);
        assertThat((String) charged.json("$.source")).isEqualTo("STAFF");
        assertThat(((Number) api.get("/api/reservations/" + id + "/vip", admin).json("$.account.pendingTotal"))
                .doubleValue()).isEqualTo(160.0);
    }

    @Test
    void anotherCompanyCannotSeeTheVipTableAndFakeTokensFail() {
        var api = api();
        var tenant = data.createTenantWithAdmin("VIP aislado");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "10.00", 100, 10);
        var zone = api.post("/api/venues/" + event.venueId() + "/vip-zones", admin,
                "{\"name\":\"Escenario\",\"active\":true,\"sortOrder\":0}");
        var table = api.post("/api/vip-zones/" + zone.json("$.id") + "/tables", admin,
                "{\"name\":\"Mesa 1\",\"capacity\":6,\"minSpend\":0,\"depositAmount\":0,\"active\":true}");
        String isolatedTable = table.json("$.tables[0].id");
        assertStatus(api.put("/api/venues/" + event.venueId() + "/vip-layout", admin,
                "{\"tables\":[{\"id\":\"%s\",\"posX\":20,\"posY\":30,\"width\":9,\"height\":9,\"shape\":\"ROUND\"}]}".formatted(isolatedTable)), 200);
        var reservation = api.post("/api/reservations", admin, """
                {"tableId":"%s","sessionId":"%s","guests":2,"contactName":"Leo","contactEmail":"leo@test.local"}
                """.formatted(isolatedTable, event.sessionId()));
        assertStatus(reservation, 201);
        String id = reservation.json("$.id");

        var other = data.createTenantWithAdmin("VIP intruso");
        String intruder = api.login(other.adminEmail(), TestDataFactory.PASSWORD);
        assertThat(api.get("/api/reservations/" + id + "/vip", intruder).status()).isEqualTo(404);
        assertThat(api.get("/api/venues/" + event.venueId() + "/vip-map", intruder).status()).isEqualTo(404);
        assertThat(api.post("/api/reservations/" + id + "/account/close", intruder, null).status()).isEqualTo(404);
        assertThat(api.get("/api/public/vip-tables/" + "x".repeat(43), null).status()).isEqualTo(404);
        assertThat(api.get("/api/public/vip-tables/abc", null).status()).isEqualTo(404);
    }
}
