package com.bloop.orders.dto.response;

/** URL de la pagina de pago de Stripe a la que Angular redirige al cliente. */
public record StripeSessionResponse(String checkoutUrl) {
}
