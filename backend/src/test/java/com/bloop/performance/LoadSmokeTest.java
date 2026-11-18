package com.bloop.performance;

import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.ApiClient;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de carga de humo (Fase 4). NO se ejecuta en el build normal; lanzarla con:
 *
 *   mvnw test -Dgroups=performance -Dsurefire.excludedGroups=ninguno
 *
 * Simula la apertura de la venta de un evento: muchas personas consultando la cartelera y
 * comprando a la vez. Mide latencias (p50, p95, max) por HTTP real contra PostgreSQL y comprueba
 * que no hay errores ni sobreventa. Los resultados se imprimen en la consola.
 * Los umbrales son generosos: sirven para detectar regresiones graves, no para "certificar" rendimiento.
 */
@Tag("performance")
class LoadSmokeTest extends AbstractIntegrationTest {

    private static final int THREADS = 20;

    @Test
    void ticketSaleOpeningUnderLoad() throws Exception {
        var api = api();
        String admin = api.login(data.createTenantWithAdmin("Carga").adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "10.00", 150, 10);

        // Compradores preparados de antemano (el login no forma parte de la medicion).
        List<String> buyers = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            buyers.add(api.login(data.createCustomer(), TestDataFactory.PASSWORD));
        }
        // Calentamiento: la primera peticion de cada endpoint carga clases y prepara consultas.
        for (int i = 0; i < 20; i++) {
            api.get("/api/public/events", null);
            api.get("/api/public/events/" + event.slug(), null);
        }

        Result catalog = measure(400, i -> () -> api.get(i % 2 == 0 ? "/api/public/events"
                : "/api/public/events/" + event.slug(), null));
        // 200 intentos de compra de 1 entrada para solo 150 entradas: 50 deben recibir 409 (agotado).
        Result checkout = measure(200, i -> () -> api.postWithHeader("/api/checkout", buyers.get(i % buyers.size()), """
                {"sessionId":"%s","items":[{"ticketTypeId":"%s","quantity":1}]}
                """.formatted(event.sessionId(), event.ticketTypeId()), "Idempotency-Key", "load-" + UUID.randomUUID()));

        System.out.printf("%n==== PRUEBA DE CARGA (%d hilos concurrentes) ====%n", THREADS);
        System.out.println(catalog.describe("Cartelera y ficha publica"));
        System.out.println(checkout.describe("Checkout (reserva de stock)"));

        assertThat(catalog.serverErrors).isZero();
        assertThat(checkout.serverErrors).isZero();
        assertThat(checkout.count(201)).isEqualTo(150);
        assertThat(checkout.count(409)).isEqualTo(50);
        assertThat(catalog.percentile(95)).isLessThan(1_000);
        assertThat(checkout.percentile(95)).isLessThan(2_000);
        var detail = api.get("/api/events/" + event.eventId(), admin);
        assertThat((Integer) detail.json("$.sessions[0].ticketTypes[0].quantitySold")).isEqualTo(150);
    }

    private Result measure(int requests, java.util.function.IntFunction<Supplier<ApiClient.Response>> request)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<long[]>> futures = new ArrayList<>();
            long start = System.nanoTime();
            for (int i = 0; i < requests; i++) {
                Supplier<ApiClient.Response> call = request.apply(i);
                futures.add(pool.submit(() -> {
                    long t0 = System.nanoTime();
                    int status = call.get().status();
                    return new long[]{(System.nanoTime() - t0) / 1_000_000, status};
                }));
            }
            Result result = new Result();
            for (Future<long[]> f : futures) {
                long[] r = f.get();
                result.latencies.add(r[0]);
                result.statuses.add((int) r[1]);
                if (r[1] >= 500) {
                    result.serverErrors++;
                }
            }
            result.totalMillis = (System.nanoTime() - start) / 1_000_000;
            Collections.sort(result.latencies);
            return result;
        } finally {
            pool.shutdownNow();
        }
    }

    private static final class Result {
        final List<Long> latencies = new ArrayList<>();
        final List<Integer> statuses = new ArrayList<>();
        int serverErrors;
        long totalMillis;

        long percentile(int p) {
            int index = (int) Math.ceil(p / 100.0 * latencies.size()) - 1;
            return latencies.get(Math.max(0, index));
        }

        long count(int status) {
            return statuses.stream().filter(s -> s == status).count();
        }

        String describe(String name) {
            double throughput = latencies.size() * 1000.0 / Math.max(1, totalMillis);
            return "%-30s %4d peticiones | %6.1f pet/s | p50 %4d ms | p95 %4d ms | max %4d ms | errores 5xx %d"
                    .formatted(name, latencies.size(), throughput, percentile(50), percentile(95),
                            latencies.getLast(), serverErrors);
        }
    }
}
