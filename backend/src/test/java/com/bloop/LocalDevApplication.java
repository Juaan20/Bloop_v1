package com.bloop;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Arranque de desarrollo SIN Docker: levanta un PostgreSQL 17 real (binarios embebidos)
 * con los datos guardados en backend/.local-db y despues arranca Bloop conectado a el.
 *
 * Uso:  mvnw spring-boot:test-run      (lo hace start-dev.bat si no detecta Docker)
 *
 * Esta clase vive en src/test para que la dependencia embedded-postgres nunca llegue al jar
 * de produccion. Con Docker disponible, se recomienda docker compose + mvnw spring-boot:run.
 */
public final class LocalDevApplication {

    private LocalDevApplication() {
    }

    public static void main(String[] args) throws IOException, SQLException {
        Path dataDir = Path.of(System.getProperty("bloop.localdb.dir", ".local-db/pgdata")).toAbsolutePath();
        int port = Integer.getInteger("bloop.localdb.port", 5433);
        Files.createDirectories(dataDir.getParent());

        EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setDataDirectory(dataDir)
                .setCleanDataDirectory(false)
                .setPort(port)
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                postgres.close();
            } catch (IOException ignored) {
                // El proceso termina igualmente.
            }
        }));
        createDatabaseIfMissing(postgres, "bloop");

        // Propiedades de sistema: tienen prioridad sobre application.yml.
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl("postgres", "bloop"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "");
        System.out.printf("%n>>> PostgreSQL embebido en localhost:%d (datos en %s)%n%n", port, dataDir);

        BloopApplication.main(args);
    }

    private static void createDatabaseIfMissing(EmbeddedPostgres postgres, String name) throws SQLException {
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
             Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + name + "'")) {
                if (rs.next()) {
                    return;
                }
            }
            statement.execute("CREATE DATABASE " + name);
        }
    }
}
