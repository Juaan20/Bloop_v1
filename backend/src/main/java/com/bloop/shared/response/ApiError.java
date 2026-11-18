package com.bloop.shared.response;

import java.time.Instant;
import java.util.Map;

/**
 * Formato unico de error que devuelve la API. Angular lo lee para mostrar mensajes.
 *
 * @param fieldErrors errores de validacion por campo (solo en respuestas 400 de validacion)
 */
public record ApiError(Instant timestamp, int status, String error, String message, String path,
                       Map<String, String> fieldErrors) {
}
