package com.bloop.accesscontrol.dto.response;

import com.bloop.accesscontrol.entity.ValidationResult;

import java.time.Instant;

/** Respuesta que pinta la pantalla del escaner: verde si accepted, rojo en caso contrario. */
public record ValidationResultResponse(boolean accepted, ValidationResult result, String message,
                                       String ticketTypeName, String holderName, Instant usedAt,
                                       /** "DNI ····678Z" para compararlo con el documento fisico (null si no se pidio). */
                                       String holderDocument,
                                       /** Edad minima del evento (null si no tiene). */
                                       Integer minAge) {
}
