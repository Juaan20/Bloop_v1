package com.bloop.orders.dto.response;

/** Resultado de un intento de pago: si se aprobo, un mensaje para el usuario y el pedido actualizado. */
public record PaymentResultResponse(boolean approved, String message, OrderResponse order) {
}
