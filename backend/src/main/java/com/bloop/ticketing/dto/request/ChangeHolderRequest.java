package com.bloop.ticketing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nuevo nombre del titular que figura en la entrada. */
public record ChangeHolderRequest(@NotBlank @Size(max = 200) String holderName) {
}
