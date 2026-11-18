package com.bloop.orders.dto.request;

import com.bloop.orders.entity.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Asistente de una entrada. birthDate y el documento son obligatorios si el evento tiene edad minima.
 * documentNumber se valida y se descarta: solo se guardan sus 4 ultimos caracteres.
 */
public record AttendeeRequest(
        @NotNull UUID ticketTypeId,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Past LocalDate birthDate,
        DocumentType documentType,
        @Size(max = 30) String documentNumber) {

    /** Evita que el documento acabe en logs si alguien imprime la peticion. */
    @Override
    public String toString() {
        return "AttendeeRequest[ticketTypeId=" + ticketTypeId + ", firstName=" + firstName + "]";
    }
}
