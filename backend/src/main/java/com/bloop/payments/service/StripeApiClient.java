package com.bloop.payments.service;

import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Llamadas HTTP a la API de Stripe (https://docs.stripe.com/api).
 *
 * Usamos RestClient de Spring en lugar del SDK oficial: solo necesitamos dos operaciones
 * (crear una sesion de Checkout y crear un reembolso) y asi se ve exactamente que se envia.
 * Stripe recibe formularios (application/x-www-form-urlencoded) y responde JSON.
 */
@Component
public class StripeApiClient {

    private static final Logger log = LoggerFactory.getLogger(StripeApiClient.class);

    private final BloopProperties.Stripe config;
    private final RestClient restClient;

    public StripeApiClient(BloopProperties properties) {
        this.config = properties.payments().stripe();
        this.restClient = RestClient.builder()
                .baseUrl(config.apiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.secretKey())
                .build();
    }

    /** POST /v1/checkout/sessions. Devuelve el JSON de la sesion (id, url...). */
    public Map<String, Object> createCheckoutSession(MultiValueMap<String, String> form) {
        return post("/v1/checkout/sessions", form);
    }

    /** POST /v1/refunds. Devuelve el JSON del reembolso (id, status...). */
    public Map<String, Object> createRefund(String paymentIntentId, long amountCents) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", paymentIntentId);
        form.add("amount", Long.toString(amountCents));
        return post("/v1/refunds", form);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, MultiValueMap<String, String> form) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            log.warn("Stripe respondio {} en {}: {}", e.getStatusCode(), path, e.getResponseBodyAsString());
            throw new BadRequestException("La pasarela de pago ha rechazado la operacion");
        }
    }
}
