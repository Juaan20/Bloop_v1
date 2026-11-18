package com.bloop.authentication.dto.response;

import java.time.Instant;

/**
 * Respuesta de login/refresh. El refresh token NO va aqui: viaja en una cookie HttpOnly
 * que JavaScript no puede leer (protege frente a robo por XSS).
 */
public record AuthResponse(String accessToken, Instant expiresAt, AuthUserResponse user) {
}
