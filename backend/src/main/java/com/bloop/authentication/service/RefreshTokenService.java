package com.bloop.authentication.service;

import com.bloop.authentication.entity.RefreshToken;
import com.bloop.authentication.repository.RefreshTokenRepository;
import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.exception.UnauthorizedException;
import com.bloop.shared.util.SecureTokens;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Gestion de refresh tokens con rotacion:
 * cada vez que se usa uno, se revoca y se emite otro nuevo. Si alguien presenta un token
 * ya revocado (posible robo), revocamos todas las sesiones de ese usuario.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final BloopProperties properties;
    private final Clock clock;

    /** Crea un refresh token para el usuario y devuelve el valor en claro (solo existe en la cookie). */
    @Transactional
    public String issue(UUID userId) {
        String raw = SecureTokens.randomToken(32);
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(SecureTokens.sha256(raw));
        Instant now = Instant.now(clock);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(properties.security().refreshTokenDays(), ChronoUnit.DAYS));
        refreshTokenRepository.save(token);
        return raw;
    }

    /** Valida el token, lo revoca y devuelve el id de su usuario. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public UUID consume(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new UnauthorizedException("Sesion no valida");
        }
        Instant now = Instant.now(clock);
        RefreshToken token = refreshTokenRepository.findByTokenHash(SecureTokens.sha256(raw))
                .orElseThrow(() -> new UnauthorizedException("Sesion no valida"));
        if (token.getRevokedAt() != null) {
            log.warn("Reutilizacion de refresh token revocado del usuario {}. Se revocan todas sus sesiones.",
                    token.getUserId());
            refreshTokenRepository.revokeAllForUser(token.getUserId(), now);
            throw new UnauthorizedException("Sesion no valida");
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw new UnauthorizedException("La sesion ha caducado");
        }
        token.setRevokedAt(now);
        return token.getUserId();
    }

    @Transactional
    public void revoke(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(SecureTokens.sha256(raw))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(Instant.now(clock)));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now(clock));
    }
}
