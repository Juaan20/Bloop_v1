package com.bloop.pos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo de la anulacion de una venta (queda en la auditoria). */
public record VoidSaleRequest(@NotBlank @Size(max = 255) String reason) {
}
