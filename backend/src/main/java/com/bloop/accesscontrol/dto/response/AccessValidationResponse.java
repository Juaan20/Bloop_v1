package com.bloop.accesscontrol.dto.response;

import com.bloop.accesscontrol.entity.ValidationResult;

import java.time.Instant;
import java.util.UUID;

/** Fila del historial de escaneos. */
public record AccessValidationResponse(UUID id, ValidationResult result, UUID ticketId, UUID validatedBy,
                                       Instant createdAt) {
}
