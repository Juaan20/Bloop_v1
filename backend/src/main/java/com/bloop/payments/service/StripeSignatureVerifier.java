package com.bloop.payments.service;

import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.exception.BadRequestException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Verifica la cabecera Stripe-Signature de los webhooks (https://docs.stripe.com/webhooks#verify-manually).
 *
 * Formato: "t=1700000000,v1=firma_hex[,v1=otra_firma]".
 * firma = HMAC-SHA256(secreto_del_webhook, t + "." + cuerpo_exacto_recibido)
 *
 * - Sin firma valida, cualquiera podria marcar pedidos como pagados llamando al endpoint.
 * - La marca de tiempo t evita que se reenvie un webhook antiguo capturado (ataque de repeticion).
 */
@Component
public class StripeSignatureVerifier {

    private final BloopProperties.Stripe config;
    private final Clock clock;

    public StripeSignatureVerifier(BloopProperties properties, Clock clock) {
        this.config = properties.payments().stripe();
        this.clock = clock;
    }

    public void verify(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank() || config.webhookSecret() == null
                || config.webhookSecret().isBlank()) {
            throw new BadRequestException("Firma de webhook ausente");
        }
        Long timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if (kv[0].equals("t")) {
                timestamp = parseLong(kv[1]);
            } else if (kv[0].equals("v1")) {
                signatures.add(kv[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            throw new BadRequestException("Firma de webhook mal formada");
        }
        long age = Math.abs(Instant.now(clock).getEpochSecond() - timestamp);
        if (age > config.webhookToleranceSeconds()) {
            throw new BadRequestException("Webhook caducado");
        }
        byte[] expected = sign(timestamp + "." + payload, config.webhookSecret())
                .getBytes(StandardCharsets.UTF_8);
        boolean valid = signatures.stream()
                .anyMatch(s -> MessageDigest.isEqual(expected, s.getBytes(StandardCharsets.UTF_8)));
        if (!valid) {
            throw new BadRequestException("Firma de webhook no valida");
        }
    }

    /** HMAC-SHA256 en hexadecimal. Publico para que los tests puedan firmar webhooks de prueba. */
    public static String sign(String content, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular la firma", e);
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
