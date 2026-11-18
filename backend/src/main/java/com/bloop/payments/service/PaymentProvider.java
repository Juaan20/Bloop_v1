package com.bloop.payments.service;

import com.bloop.payments.entity.Payment;
import com.bloop.payments.entity.PaymentProviderType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Contrato de una pasarela de pago. Implementaciones:
 * - SimulatedPaymentProvider: desarrollo local, sin servicios externos.
 * - StripePaymentProvider: Stripe Checkout en modo test.
 *
 * Flujo comun:
 * 1. createPayment(): el proveedor crea el cobro y devuelve su id (y, si hace falta, una URL de pago).
 * 2. El proveedor confirma el resultado (Stripe mediante webhook firmado; el simulador mediante un
 *    endpoint de desarrollo) y OrderPaymentService lo aplica al pedido.
 * 3. refund(): devuelve el dinero de un cobro ya confirmado.
 */
public interface PaymentProvider {

    PaymentProviderType type();

    /** Crea el cobro en el proveedor. */
    ProviderCheckout createPayment(PaymentRequest request);

    /**
     * Reembolsa (total o parcialmente) un cobro confirmado.
     *
     * @return identificador del reembolso en el proveedor
     */
    String refund(Payment payment, BigDecimal amount);

    /** Datos que necesita el proveedor para crear un cobro. */
    record PaymentRequest(UUID orderId, String orderReference, BigDecimal amount, String currency,
                          String description, String customerEmail, String successUrl, String cancelUrl) {
    }

    /**
     * Resultado de crear un cobro.
     *
     * @param providerPaymentId id unico del cobro en el proveedor
     * @param redirectUrl       pagina de pago a la que enviar al cliente (null si no hace falta)
     */
    record ProviderCheckout(String providerPaymentId, String redirectUrl) {
    }
}
