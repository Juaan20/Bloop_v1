package com.bloop.shared.exception;

/** El usuario esta autenticado pero no puede realizar esta accion (respuesta 403). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
