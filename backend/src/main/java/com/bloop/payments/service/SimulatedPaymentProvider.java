package com.bloop.payments.service;

import com.bloop.payments.entity.Payment;
import com.bloop.payments.entity.PaymentProviderType;
import com.bloop.shared.util.SecureTokens;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pasarela simulada para desarrollo local: no contacta con ningun servicio externo ni
 * pide datos de tarjeta. El resultado (exito o fallo) lo decide el endpoint de pago simulado,
 * y los reembolsos se aceptan siempre.
 */
@Component
public class SimulatedPaymentProvider implements PaymentProvider {

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.SIMULATED;
    }

    @Override
    public ProviderCheckout createPayment(PaymentRequest request) {
        return new ProviderCheckout("sim_" + SecureTokens.randomToken(16), null);
    }

    @Override
    public String refund(Payment payment, BigDecimal amount) {
        return "sim_re_" + SecureTokens.randomToken(12);
    }
}
