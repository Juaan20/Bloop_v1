package com.bloop.authentication;

import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Fase 4: limitacion de intentos de login y cabeceras de seguridad. */
class SecurityHardeningIntegrationTest extends AbstractIntegrationTest {

    @Test
    void loginIsBlockedAfterTooManyFailures() {
        var api = api();
        String email = data.createCustomer();
        String wrong = "{\"email\":\"%s\",\"password\":\"incorrecta\"}".formatted(email);
        for (int i = 0; i < 5; i++) {
            assertThat(api.post("/api/auth/login", null, wrong).status()).isEqualTo(401);
        }
        // A partir del limite, ni siquiera la contrasena correcta entra: 429 con Retry-After.
        var blocked = api.post("/api/auth/login", null,
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, TestDataFactory.PASSWORD));
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(Long.parseLong(blocked.header("Retry-After"))).isPositive();
        assertThat((String) blocked.json("$.message")).contains("Demasiados intentos");

        // Otra cuenta desde la misma IP sigue pudiendo entrar (el bloqueo es por email + IP).
        assertThat(api.post("/api/auth/login", null, "{\"email\":\"%s\",\"password\":\"%s\"}"
                .formatted(data.createCustomer(), TestDataFactory.PASSWORD)).status()).isEqualTo(200);
    }

    @Test
    void successfulLoginResetsTheFailureCounter() {
        var api = api();
        String email = data.createCustomer();
        String wrong = "{\"email\":\"%s\",\"password\":\"incorrecta\"}".formatted(email);
        String ok = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, TestDataFactory.PASSWORD);
        for (int i = 0; i < 4; i++) {
            api.post("/api/auth/login", null, wrong);
        }
        assertThat(api.post("/api/auth/login", null, ok).status()).isEqualTo(200);
        for (int i = 0; i < 4; i++) {
            api.post("/api/auth/login", null, wrong);
        }
        assertThat(api.post("/api/auth/login", null, ok).status()).isEqualTo(200);
    }

    @Test
    void passwordResetRequestsAreRateLimited() {
        var api = api();
        String body = "{\"email\":\"nadie@test.local\"}";
        for (int i = 0; i < 5; i++) {
            assertThat(api.post("/api/auth/forgot-password", null, body).status()).isEqualTo(204);
        }
        assertThat(api.post("/api/auth/forgot-password", null, body).status()).isEqualTo(429);
    }

    @Test
    void apiResponsesCarrySecurityHeaders() {
        var response = api().get("/api/public/events", null);
        assertThat(response.header("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.header("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.header("Content-Security-Policy")).contains("default-src 'none'");
        assertThat(response.header("Permissions-Policy")).contains("camera=()");
        // Swagger UI necesita cargar scripts: la CSP estricta solo se aplica a la API.
        assertThat(api().get("/swagger-ui/index.html", null).header("Content-Security-Policy")).isNull();
    }
}
