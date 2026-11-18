package com.bloop;

import com.bloop.shared.enums.RoleCode;
import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de concurrencia reales: varias peticiones HTTP simultaneas contra PostgreSQL.
 * Verifican que no hay sobreventa, doble validacion de QR ni doble procesamiento de pagos.
 */
class ConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Test
    void concurrentCheckoutsNeverOversell() throws Exception {
        var api = api();
        String admin = api.login(data.createTenantWithAdmin("Stock").adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "10.00", 5, 5);

        int buyers = 12;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            tokens.add(api.login(data.createCustomer(), TestDataFactory.PASSWORD));
        }
        List<Integer> statuses = runConcurrently(buyers, i -> api.postWithHeader("/api/checkout", tokens.get(i), """
                {"sessionId":"%s","items":[{"ticketTypeId":"%s","quantity":1}]}
                """.formatted(event.sessionId(), event.ticketTypeId()), "Idempotency-Key",
                "c-" + UUID.randomUUID()).status());

        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(5);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(buyers - 5);
        var detail = api.get("/api/events/" + event.eventId(), admin);
        assertThat((Integer) detail.json("$.sessions[0].ticketTypes[0].quantitySold")).isEqualTo(5);
    }

    @Test
    void sameIdempotencyKeyCreatesASingleOrder() throws Exception {
        var api = api();
        String admin = api.login(data.createTenantWithAdmin("Idem").adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "10.00", 50, 5);
        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        String key = "same-" + UUID.randomUUID();

        runConcurrently(6, i -> api.postWithHeader("/api/checkout", customer, """
                {"sessionId":"%s","items":[{"ticketTypeId":"%s","quantity":2}]}
                """.formatted(event.sessionId(), event.ticketTypeId()), "Idempotency-Key", key).status());

        assertThat((List<?>) api.get("/api/me/orders", customer).json("$")).hasSize(1);
        var detail = api.get("/api/events/" + event.eventId(), admin);
        assertThat((Integer) detail.json("$.sessions[0].ticketTypes[0].quantitySold")).isEqualTo(2);
    }

    @Test
    void concurrentPaymentsIssueTicketsOnlyOnce() throws Exception {
        var api = api();
        String admin = api.login(data.createTenantWithAdmin("Pago").adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "25.00", 50, 5);
        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        var order = api.postWithHeader("/api/checkout", customer, """
                {"sessionId":"%s","items":[{"ticketTypeId":"%s","quantity":3}]}
                """.formatted(event.sessionId(), event.ticketTypeId()), "Idempotency-Key", "p-" + UUID.randomUUID());
        String orderId = order.json("$.id");

        List<Integer> statuses = runConcurrently(5, i -> api.post(
                "/api/checkout/orders/" + orderId + "/simulated-payment", customer, "{\"approve\":true}").status());

        assertThat(statuses).allMatch(s -> s == 200);
        assertThat((List<?>) api.get("/api/me/tickets", customer).json("$")).hasSize(3);
    }

    @Test
    void sameQrScannedSimultaneouslyIsAcceptedOnlyOnce() throws Exception {
        var api = api();
        var tenant = data.createTenantWithAdmin("Doble QR");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "10.00", 50, 5);
        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        data.buyTickets(api, customer, event, 1);
        String qr = (String) ((List<?>) api.get("/api/me/tickets", customer).json("$[*].qrPayload")).getFirst();

        int scanners = 8;
        List<String> doorTokens = new ArrayList<>();
        for (int i = 0; i < scanners; i++) {
            doorTokens.add(api.login(data.createStaff(tenant.tenantId(), RoleCode.DOOR_STAFF,
                    Set.of(UUID.fromString(event.venueId()))), TestDataFactory.PASSWORD));
        }
        List<String> results = runConcurrently(scanners, i -> api.post("/api/access/validate", doorTokens.get(i),
                "{\"qrPayload\":\"%s\",\"sessionId\":\"%s\"}".formatted(qr, event.sessionId()))
                .<String>json("$.result"));

        assertThat(results.stream().filter("ACCEPTED"::equals).count()).isEqualTo(1);
        assertThat(results.stream().filter("ALREADY_USED"::equals).count()).isEqualTo(scanners - 1);
    }

    /** Lanza N tareas a la vez (todas esperan en un "pistoletazo de salida" comun). */
    private static <T> List<T> runConcurrently(int count, IndexedTask<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                Callable<T> callable = () -> {
                    start.await();
                    return task.run(index);
                };
                futures.add(pool.submit(callable));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface IndexedTask<T> {
        T run(int index);
    }
}
