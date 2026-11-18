package com.bloop.orders;

import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.ApiClient;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static com.bloop.support.TestDataFactory.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;

/** Pedidos de un festival de dos dias: filtrar por dia, combinar filtros, ordenar y resumir. */
class OrderFilterIntegrationTest extends AbstractIntegrationTest {

    @Test
    void filtersBySessionAndSummarizes() {
        ApiClient api = api();
        var tenant = data.createTenantWithAdmin("Festival filtros");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var festival = data.createPublishedEvent(api, admin, "20.00", 100, 10);

        // Segundo dia (sabado) con su titulo y su propio tipo de entrada
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        var saturday = api.post("/api/events/" + festival.eventId() + "/sessions", admin, """
                {"startsAt":"%s","endsAt":"%s","capacity":300,"title":"Sabado - Techno"}
                """.formatted(start, start.plus(6, ChronoUnit.HOURS)));
        assertStatus(saturday, 201);
        assertThat((String) saturday.json("$.title")).isEqualTo("Sabado - Techno");
        String saturdayId = saturday.json("$.id");
        var vipType = api.post("/api/events/%s/sessions/%s/ticket-types".formatted(festival.eventId(), saturdayId),
                admin, """
                {"name":"VIP sabado","category":"VIP","price":50,"quantityTotal":50,"maxPerOrder":5,"active":true,"sortOrder":0}
                """);
        assertStatus(vipType, 201);

        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        data.buyTickets(api, customer, festival, 2); // viernes: 40
        var satOrder = data.checkout(api, customer, """
                {"sessionId":"%s","items":[{"ticketTypeId":"%s","quantity":1}]}""".formatted(saturdayId, vipType.json("$.id")));
        assertStatus(satOrder, 201);
        data.pay(api, customer, satOrder.json("$.id")); // sabado: 50

        // Solo el sabado
        List<Map<String, Object>> rows = api.get("/api/orders?sessionId=" + saturdayId, admin).json("$.content");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("sessionTitle")).isEqualTo("Sabado - Techno");

        // Filtros combinados: evento + tipo VIP + pagado; metodo y estado del pago
        List<?> vipPaid = api.get("/api/orders?eventId=" + festival.eventId() + "&ticketCategory=VIP&status=PAID", admin)
                .json("$.content");
        assertThat(vipPaid).hasSize(1);
        List<?> simulated = api.get("/api/orders?eventId=" + festival.eventId()
                + "&paymentProvider=SIMULATED&paymentStatus=SUCCEEDED", admin).json("$.content");
        assertThat(simulated).hasSize(2);
        List<?> future = api.get("/api/orders?eventId=" + festival.eventId() + "&from="
                + Instant.now().plus(1, ChronoUnit.HOURS), admin).json("$.content");
        assertThat(future).isEmpty();

        // Orden por importe y resumen de todo el evento
        List<Map<String, Object>> byAmount = api.get("/api/orders?eventId=" + festival.eventId() + "&sort=amount_desc", admin)
                .json("$.content");
        assertThat(((Number) byAmount.getFirst().get("totalAmount")).doubleValue()).isEqualTo(50.0);
        var summary = api.get("/api/orders/summary?eventId=" + festival.eventId(), admin);
        assertStatus(summary, 200);
        assertThat(((Number) summary.json("$.orders")).intValue()).isEqualTo(2);
        assertThat(((Number) summary.json("$.tickets")).intValue()).isEqualTo(3);
        assertThat(((Number) summary.json("$.paidAmount")).doubleValue()).isEqualTo(90.0);
        var satSummary = api.get("/api/orders/summary?sessionId=" + saturdayId, admin);
        assertThat(((Number) satSummary.json("$.paidAmount")).doubleValue()).isEqualTo(50.0);

        // Otra empresa no ve nada de este festival
        var other = data.createTenantWithAdmin("Otra filtros");
        String otherAdmin = api.login(other.adminEmail(), TestDataFactory.PASSWORD);
        List<?> none = api.get("/api/orders?sessionId=" + saturdayId, otherAdmin).json("$.content");
        assertThat(none).isEmpty();
    }
}
