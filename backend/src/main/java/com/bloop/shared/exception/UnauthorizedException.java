package com.bloop.shared.exception;

/** Credenciales o token no validos (respuesta 401). */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
