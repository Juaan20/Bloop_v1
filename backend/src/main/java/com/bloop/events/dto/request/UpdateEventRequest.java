package com.bloop.events.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos editables de un evento. El local no se cambia para no afectar a entradas vendidas. */
public record UpdateEventRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 8000) String description,
        @Size(max = 500) @Pattern(regexp = "^$|https?://.+", message = "Debe ser una URL http(s)") String imageUrl,
        @Min(0) @Max(99) Integer minAge) {
}
