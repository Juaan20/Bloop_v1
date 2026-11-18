package com.bloop.shared.exception;

/** El recurso no existe o no pertenece al tenant del usuario (respuesta 404). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String resource) {
        return new NotFoundException(resource + " no encontrado");
    }
}
