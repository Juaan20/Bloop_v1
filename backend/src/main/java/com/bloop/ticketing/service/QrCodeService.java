package com.bloop.ticketing.service;

import com.bloop.shared.config.BloopProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Genera y verifica el contenido de los QR de las entradas.
 *
 * Formato: VF1.{codigo}.{firma}
 *  - codigo: 20 bytes aleatorios (imposible de adivinar).
 *  - firma: HMAC-SHA256(codigo) con una clave secreta del servidor (truncada a 16 bytes).
 * La firma permite rechazar QR falsificados o alterados sin consultar la base de datos.
 *
 * Rotacion de claves: los QR nuevos se firman siempre con QR_SECRET; al verificar se aceptan
 * tambien las claves de QR_PREVIOUS_SECRETS, para que las entradas ya vendidas sigan valiendo.
 */
@Service
public class QrCodeService {

    private static final String PREFIX = "VF1";
    private static final int SIGNATURE_BYTES = 16;
    private static final int MIN_SECRET_LENGTH = 32;

    private final SecretKeySpec signingKey;
    /** Clave actual + claves anteriores (solo para verificar). */
    private final List<SecretKeySpec> verificationKeys = new ArrayList<>();

    public QrCodeService(BloopProperties properties) {
        this.signingKey = toKey(properties.security().qrSecret(), "QR_SECRET");
        verificationKeys.add(signingKey);
        List<String> previous = properties.security().previousQrSecrets();
        if (previous != null) {
            previous.stream().filter(s -> s != null && !s.isBlank())
                    .forEach(s -> verificationKeys.add(toKey(s.trim(), "QR_PREVIOUS_SECRETS")));
        }
    }

    public String payloadFor(String ticketCode) {
        return PREFIX + "." + ticketCode + "." + sign(ticketCode, signingKey);
    }

    /** Devuelve el codigo de la entrada si el QR tiene formato correcto y firma valida. */
    public Optional<String> extractVerifiedCode(String payload) {
        if (payload == null) {
            return Optional.empty();
        }
        String[] parts = payload.trim().split("\\.");
        if (parts.length != 3 || !PREFIX.equals(parts[0]) || parts[1].isBlank()) {
            return Optional.empty();
        }
        byte[] received = parts[2].getBytes(StandardCharsets.UTF_8);
        for (SecretKeySpec key : verificationKeys) {
            // Comparacion en tiempo constante para no filtrar informacion por tiempos.
            if (MessageDigest.isEqual(sign(parts[1], key).getBytes(StandardCharsets.UTF_8), received)) {
                return Optional.of(parts[1]);
            }
        }
        return Optional.empty();
    }

    private static SecretKeySpec toKey(String secret, String variable) {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("Falta " + variable + " o tiene menos de " + MIN_SECRET_LENGTH
                    + " caracteres. Revisa el fichero .env");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static String sign(String code, SecretKeySpec key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] full = mac.doFinal(code.getBytes(StandardCharsets.UTF_8));
            byte[] truncated = new byte[SIGNATURE_BYTES];
            System.arraycopy(full, 0, truncated, 0, SIGNATURE_BYTES);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(truncated);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar el QR", e);
        }
    }
}
