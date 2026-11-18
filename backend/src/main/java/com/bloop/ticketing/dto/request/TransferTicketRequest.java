package com.bloop.ticketing.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Transferencia de una entrada a otra persona con cuenta en Bloop.
 * holderName es opcional: si no se indica, se usa el nombre de la cuenta destinataria.
 */
public record TransferTicketRequest(
        @NotBlank @Email @Size(max = 255) String recipientEmail,
        @Size(max = 200) String holderName) {
}
