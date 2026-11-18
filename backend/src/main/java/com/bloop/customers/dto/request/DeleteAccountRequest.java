package com.bloop.customers.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Confirmacion con la contrasena antes de suprimir la cuenta (accion irreversible). */
public record DeleteAccountRequest(@NotBlank(message = "Introduce tu contrasena") String password) {
}
