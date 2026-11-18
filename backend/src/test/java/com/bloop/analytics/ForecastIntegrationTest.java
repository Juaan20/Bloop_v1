package com.bloop.analytics;

import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.bloop.support.TestDataFactory.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;

/** Prevision de asistencia: sin historico no inventa cifras, y respeta el aislamiento entre empresas. */
class ForecastIntegrationTest extends AbstractIntegrationTest {

    @Test
    void forecastWithoutHistoryIsNotAvailableAndIsTenantScoped() {
        var api = api();
        var tenant = data.createTenantWithAdmin("Prevision");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);
        var event = data.createPublishedEvent(api, admin, "10.00", 100, 10);
        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        data.buyTickets(api, customer, event, 3);

        var upcoming = api.get("/api/analytics/forecast", admin);
        assertStatus(upcoming, 200);
        assertThat(upcoming.body()).contains(event.sessionId());

        var forecast = api.get("/api/analytics/forecast/" + event.sessionId(), admin);
        assertStatus(forecast, 200);
        assertThat((Integer) forecast.json("$.session.ticketsSold")).isEqualTo(3);
        assertThat((String) forecast.json("$.forecast.method")).isEqualTo("NONE");
        assertThat((String) forecast.json("$.forecast.confidence")).isEqualTo("NONE");
        assertThat((String) forecast.json("$.forecast.explanation")).contains("No hay noches comparables");
        assertThat((Integer) forecast.json("$.salesCurve.length()")).isEqualTo(22);

        // Otra empresa no puede consultar la sesion; un cliente no tiene acceso a la analitica
        String intruder = api.login(data.createTenantWithAdmin("Prevision 2").adminEmail(), TestDataFactory.PASSWORD);
        assertThat(api.get("/api/analytics/forecast/" + event.sessionId(), intruder).status()).isEqualTo(404);
        assertThat(api.get("/api/analytics/forecast", customer).status()).isEqualTo(403);

        // El resumen incluye VIP y ocupacion de las sesiones del periodo
        String from = LocalDate.now().toString();
        String to = LocalDate.now().plusDays(3).toString();
        var overview = api.get("/api/analytics?from=" + from + "&to=" + to, admin);
        assertStatus(overview, 200);
        assertThat((Integer) overview.json("$.occupancy.ticketsSold")).isEqualTo(3);
        assertThat((Integer) overview.json("$.vip.reservations")).isZero();
    }
}
