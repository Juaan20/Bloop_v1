package com.bloop.shared.exception;

/** Datos validos sintacticamente pero que incumplen una regla de negocio (respuesta 400). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
