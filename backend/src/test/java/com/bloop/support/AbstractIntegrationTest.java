package com.bloop.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Base de los tests de integracion:
 * - Arranca la aplicacion completa en un puerto aleatorio (peticiones HTTP reales).
 * - Usa un PostgreSQL 17 REAL embebido (no H2): mismas migraciones Flyway, mismas
 *   restricciones y mismo comportamiento de bloqueos que en produccion.
 * Si en tu maquina tienes Docker, podrias sustituirlo por Testcontainers sin tocar los tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestDataFactory data;

    @Autowired
    private com.bloop.authentication.security.LoginAttemptService loginAttemptService;

    /** Cada test empieza sin intentos de login fallidos acumulados (todos llegan desde 127.0.0.1). */
    @org.junit.jupiter.api.BeforeEach
    void resetLoginAttempts() {
        loginAttemptService.clearAll();
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // fin de los tests
                }
            }));
            return postgres;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo arrancar PostgreSQL embebido", e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
    }

    protected ApiClient api() {
        return new ApiClient("http://localhost:" + port);
    }
}
