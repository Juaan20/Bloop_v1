package com.bloop.accesscontrol.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param qrPayload texto leido del QR (VF1.codigo.firma)
 * @param sessionId sesion en la que esta trabajando el portero
 */
public record ValidateTicketRequest(@NotBlank @Size(max = 200) String qrPayload, @NotNull UUID sessionId) {
}
