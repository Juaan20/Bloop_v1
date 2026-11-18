package com.bloop.authentication.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.bloop.shared.config.BloopProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Configura la firma (encoder) y verificacion (decoder) de los JWT con HMAC-SHA256.
 * El mismo backend emite y valida los tokens, por eso basta una clave simetrica.
 *
 * Rotacion de claves sin cerrar sesiones:
 * 1. Mueve la clave actual a JWT_PREVIOUS_SECRETS y pon una nueva en JWT_SECRET.
 * 2. Los tokens nuevos se firman con la nueva; los antiguos (15 min de vida) se siguen aceptando.
 * 3. Pasado ese tiempo, borra la clave anterior de JWT_PREVIOUS_SECRETS.
 */
@Configuration
public class JwtConfig {

    private static final int MIN_SECRET_LENGTH = 32;

    @Bean
    public SecretKey jwtSecretKey(BloopProperties properties) {
        return toKey(properties.security().jwtSecret(), "JWT_SECRET");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, BloopProperties properties) {
        List<JwtDecoder> decoders = new ArrayList<>();
        decoders.add(decoderFor(jwtSecretKey));
        List<String> previous = properties.security().previousJwtSecrets();
        if (previous != null) {
            previous.stream().filter(s -> s != null && !s.isBlank())
                    .forEach(s -> decoders.add(decoderFor(toKey(s.trim(), "JWT_PREVIOUS_SECRETS"))));
        }
        if (decoders.size() == 1) {
            return decoders.getFirst();
        }
        // Prueba la clave actual y, si la firma no coincide, las anteriores.
        return token -> {
            JwtException last = null;
            for (JwtDecoder decoder : decoders) {
                try {
                    return decoder.decode(token);
                } catch (JwtException e) {
                    last = e;
                }
            }
            throw last;
        };
    }

    private static JwtDecoder decoderFor(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static SecretKey toKey(String secret, String variable) {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "Falta " + variable + " o tiene menos de " + MIN_SECRET_LENGTH + " caracteres. Revisa el fichero .env");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
