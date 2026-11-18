package com.bloop.pos.dto.request;

import com.bloop.pos.entity.TerminalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Alta y edicion de un terminal. El almacen debe ser del mismo local. El local no se puede cambiar. */
public record TerminalRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull UUID venueId,
        @NotNull UUID warehouseId,
        @NotNull TerminalType type,
        boolean active,
        /** Tipos de categoria que vende el terminal. Vacio o null = el valor por defecto de su tipo. */
        java.util.Set<com.bloop.pos.entity.CategoryKind> allowedKinds) {
}
