package com.bloop.authentication.service;

import com.bloop.authentication.dto.response.AuthResponse;

/**
 * Resultado interno de un login: la respuesta JSON y el refresh token en claro,
 * que el controlador coloca en una cookie HttpOnly.
 */
public record AuthSession(AuthResponse response, String refreshToken) {
}
