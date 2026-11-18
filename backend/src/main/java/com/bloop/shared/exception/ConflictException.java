package com.bloop.shared.exception;

/** La operacion choca con el estado actual (sin stock, email repetido...). Respuesta 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
