package com.bloop.customers.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Edicion de la ficha desde el backoffice. El nombre y el email vienen de la cuenta del
 * cliente y no se editan aqui. marketingConsent solo debe cambiarse a peticion del cliente.
 */
public record UpdateCustomerRequest(
        @Size(max = 40) String phone,
        @Size(max = 2000) String notes,
        @Size(max = 500) String tags,
        boolean marketingConsent) {
}
