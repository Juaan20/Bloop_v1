package com.bloop.support;

import com.jayway.jsonpath.JsonPath;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * Cliente HTTP minimo para los tests: envia JSON y permite leer campos con JsonPath,
 * por ejemplo {@code response.json("$.user.email")}.
 */
public class ApiClient {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final String baseUrl;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Response get(String path, String token) {
        return send(builder(path, token).GET());
    }

    public Response post(String path, String token, String json) {
        return send(builder(path, token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json == null ? "{}" : json)));
    }

    public Response postWithHeader(String path, String token, String json, String header, String value) {
        return send(builder(path, token).header("Content-Type", "application/json").header(header, value)
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    public Response put(String path, String token, String json) {
        return send(builder(path, token).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)));
    }

    /** Sube un fichero como multipart/form-data (campo "file"). */
    public Response upload(String path, String token, String filename, byte[] content, String contentType) {
        String boundary = "----vf" + java.util.UUID.randomUUID();
        var out = new java.io.ByteArrayOutputStream();
        String crlf = "\r\n";
        String head = "--" + boundary + crlf + "Content-Disposition: form-data; name=\"file\"; filename=\""
                + filename + "\"" + crlf + "Content-Type: " + contentType + crlf + crlf;
        out.writeBytes(head.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.writeBytes(content);
        out.writeBytes((crlf + "--" + boundary + "--" + crlf).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return send(builder(path, token).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())));
    }

    public Response delete(String path, String token) {
        return send(builder(path, token).DELETE());
    }

    /** Inicia sesion y devuelve el access token. */
    public String login(String email, String password) {
        Response response = post("/api/auth/login", null,
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
        if (response.status() != 200) {
            throw new IllegalStateException("Login fallido para " + email + ": " + response.body());
        }
        return response.json("$.accessToken");
    }

    private HttpRequest.Builder builder(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private Response send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body(),
                    response.headers().firstValue("Set-Cookie"), response.headers());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public record Response(int status, String body, Optional<String> setCookie, java.net.http.HttpHeaders headers) {

        /** Valor de una cabecera de la respuesta (o null si no viene). */
        public String header(String name) {
            return headers.firstValue(name).orElse(null);
        }

        public <T> T json(String path) {
            return JsonPath.read(body, path);
        }
    }
}
