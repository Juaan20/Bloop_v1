package com.bloop.authentication;

import com.bloop.authentication.config.JwtConfig;
import com.bloop.shared.config.BloopProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rotacion de la clave JWT: los tokens firmados con la clave anterior siguen siendo validos. */
class JwtKeyRotationTest {

    private static final String OLD = "old-secret-0123456789abcdef0123456789";
    private static final String NEW = "new-secret-0123456789abcdef0123456789";

    private final JwtConfig config = new JwtConfig();

    @Test
    void tokenSignedWithPreviousKeyIsAccepted() {
        String oldToken = tokenSignedWith(OLD);
        JwtDecoder decoder = decoder(NEW, List.of(OLD));
        assertThat(decoder.decode(oldToken).getSubject()).isEqualTo("user-1");
        assertThat(decoder.decode(tokenSignedWith(NEW)).getSubject()).isEqualTo("user-1");
    }

    @Test
    void tokenSignedWithUnknownKeyIsRejected() {
        JwtDecoder decoder = decoder(NEW, List.of());
        assertThatThrownBy(() -> decoder.decode(tokenSignedWith(OLD))).isInstanceOf(JwtException.class);
    }

    private JwtDecoder decoder(String current, List<String> previous) {
        var properties = new BloopProperties(new BloopProperties.Security(current, 15, 14,
                "q".repeat(40), false, previous, List.of(), 5, 15), null, null, null, null, null, null, null);
        return config.jwtDecoder(config.jwtSecretKey(properties), properties);
    }

    private String tokenSignedWith(String secret) {
        var properties = new BloopProperties(new BloopProperties.Security(secret, 15, 14,
                "q".repeat(40), false, List.of(), List.of(), 5, 15), null, null, null, null, null, null, null);
        var encoder = config.jwtEncoder(config.jwtSecretKey(properties));
        var claims = JwtClaimsSet.builder().subject("user-1").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
