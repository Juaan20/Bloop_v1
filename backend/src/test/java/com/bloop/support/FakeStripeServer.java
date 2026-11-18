package com.bloop.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Servidor HTTP local que imita las dos operaciones de la API de Stripe que usa Bloop:
 * POST /v1/checkout/sessions y POST /v1/refunds. Guarda las peticiones recibidas para que
 * los tests comprueben exactamente que se envio (importe, PaymentIntent...).
 */
public final class FakeStripeServer {

    public record Request(String path, String authorization, Map<String, String> form) {
    }

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger counter = new AtomicInteger();

    public FakeStripeServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/v1/checkout/sessions", exchange -> {
            Request request = record(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String id = "cs_test_" + counter.incrementAndGet();
            respond(exchange, """
                    {"id":"%s","object":"checkout.session","url":"https://checkout.stripe.test/%s","amount_total":%s}
                    """.formatted(id, id, request.form().get("line_items[0][price_data][unit_amount]")));
        });
        server.createContext("/v1/refunds", exchange -> {
            record(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, "{\"id\":\"re_test_%d\",\"status\":\"succeeded\"}".formatted(counter.incrementAndGet()));
        });
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<Request> requests(String path) {
        return requests.stream().filter(r -> r.path().equals(path)).toList();
    }

    private Request record(String path, String authorization, String body) {
        Map<String, String> form = body.isBlank() ? Map.of() : java.util.Arrays.stream(body.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(kv -> URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        kv -> kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "", (a, b) -> b));
        Request request = new Request(path, authorization, form);
        requests.add(request);
        return request;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
