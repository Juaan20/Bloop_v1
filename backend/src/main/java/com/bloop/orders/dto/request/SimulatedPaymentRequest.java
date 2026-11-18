package com.bloop.orders.dto.request;

/**
 * Peticion al simulador de pagos (solo desarrollo).
 *
 * @param approve true simula un pago aceptado; false un pago rechazado por el banco
 */
public record SimulatedPaymentRequest(boolean approve) {
}
