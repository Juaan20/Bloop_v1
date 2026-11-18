package com.bloop.media;

import com.bloop.shared.enums.RoleCode;
import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.ApiClient;
import com.bloop.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static com.bloop.support.TestDataFactory.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;

/** Biblioteca de imagenes: validacion por contenido, aislamiento entre empresas y uso antes de borrar. */
class MediaIntegrationTest extends AbstractIntegrationTest {

    @Test
    void uploadValidatesContentAndIsolatesTenants() throws IOException {
        ApiClient api = api();
        var tenant = data.createTenantWithAdmin("Imagenes");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);

        // PNG real: se acepta, se guarda fuera del proyecto y se sirve con nosniff
        var uploaded = api.upload("/api/media", admin, "cartel viernes.png", png(320, 200), "image/png");
        assertStatus(uploaded, 201);
        assertThat((Integer) uploaded.json("$.width")).isEqualTo(320);
        String url = uploaded.json("$.url");
        String path = url.substring(url.indexOf("/api/public/media/"));
        var served = api.get(path, null);
        assertThat(served.status()).isEqualTo(200);
        assertThat(served.header("X-Content-Type-Options")).isEqualTo("nosniff");

        // Un script con extension .png, un SVG y un fichero vacio se rechazan por su contenido
        assertThat(api.upload("/api/media", admin, "shell.png", "<?php system($_GET['c']); ?>"
                .getBytes(StandardCharsets.UTF_8), "image/png").status()).isEqualTo(400);
        assertThat(api.upload("/api/media", admin, "logo.svg", "<svg onload=\"alert(1)\"></svg>"
                .getBytes(StandardCharsets.UTF_8), "image/svg+xml").status()).isEqualTo(400);
        assertThat(api.upload("/api/media", admin, "vacio.jpg", new byte[0], "image/jpeg").status()).isEqualTo(400);

        // Sin MEDIA_MANAGE (un cajero) no se puede subir
        String cashier = api.login(data.createStaff(tenant.tenantId(), RoleCode.CASHIER, Set.of()), TestDataFactory.PASSWORD);
        assertThat(api.upload("/api/media", cashier, "a.png", png(10, 10), "image/png").status()).isEqualTo(403);

        // Otra empresa ni la ve en su biblioteca ni la puede borrar ni usar
        var other = data.createTenantWithAdmin("Otra imagenes");
        String otherAdmin = api.login(other.adminEmail(), TestDataFactory.PASSWORD);
        List<?> otherLibrary = api.get("/api/media", otherAdmin).json("$.content");
        assertThat(otherLibrary).isEmpty();
        assertThat(api.delete("/api/media/" + uploaded.json("$.id"), otherAdmin).status()).isEqualTo(404);
        String otherVenue = data.createVenue(api, otherAdmin, 100);
        assertThat(api.post("/api/events", otherAdmin, eventBody(otherVenue, url)).status()).isEqualTo(400);

        // En uso por un evento no se puede borrar
        String venue = data.createVenue(api, admin, 100);
        assertStatus(api.post("/api/events", admin, eventBody(venue, url)), 201);
        assertThat(api.delete("/api/media/" + uploaded.json("$.id"), admin).status()).isEqualTo(409);
        // Una URL externa cualquiera tampoco se acepta
        assertThat(api.post("/api/events", admin, eventBody(venue, "https://ejemplo.com/foto.jpg")).status())
                .isEqualTo(400);
    }

    private static String eventBody(String venueId, String imageUrl) {
        return """
                {"venueId":"%s","name":"Con cartel","imageUrl":"%s",
                 "firstSession":{"startsAt":"2031-06-01T22:00:00Z","endsAt":"2031-06-02T05:00:00Z","capacity":50}}
                """.formatted(venueId, imageUrl);
    }

    private static byte[] png(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
