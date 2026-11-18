package com.bloop.authentication.service;

import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.security.JwtClaims;
import com.bloop.users.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Emite los JWT de acceso (vida corta, 15 min por defecto).
 * El token lleva: sub=id de usuario, email, tenantId, roles y permisos.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final BloopProperties properties;
    private final Clock clock;

    public record AccessToken(String value, Instant expiresAt) {
    }

    public AccessToken createAccessToken(User user) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(properties.security().accessTokenMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("bloop")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim(JwtClaims.EMAIL, user.getEmail())
                .claim(JwtClaims.ROLES, List.copyOf(user.getRoleCodes()))
                .claim(JwtClaims.PERMISSIONS, List.copyOf(user.getPermissionCodes()));
        if (user.getTenantId() != null) {
            claims.claim(JwtClaims.TENANT_ID, user.getTenantId().toString());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new AccessToken(token, expiresAt);
    }
}
