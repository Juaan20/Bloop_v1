package com.bloop.payments.service;

import com.bloop.payments.entity.Payment;
import com.bloop.payments.entity.PaymentProviderType;
import com.bloop.payments.entity.PaymentStatus;
import com.bloop.payments.repository.PaymentRepository;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Registro de intentos de pago y reembolsos. No conoce los pedidos: solo guarda su id
 * (asi el modulo de pagos no depende del de pedidos).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final SimulatedPaymentProvider simulatedProvider;
    private final StripePaymentProvider stripeProvider;
    private final Clock clock;

    @Transactional
    public Payment startSimulatedPayment(UUID tenantId, UUID orderId, BigDecimal amount, String currency,
                                         String orderReference) {
        var checkout = simulatedProvider.createPayment(new PaymentProvider.PaymentRequest(orderId, orderReference,
                amount, currency, null, null, null, null));
        return paymentRepository.save(newPayment(tenantId, orderId, PaymentProviderType.SIMULATED,
                checkout.providerPaymentId(), amount, currency));
    }

    /**
     * Crea una sesion de Stripe Checkout y registra el intento de pago (PENDING).
     *
     * @return el pago creado y la URL de Stripe a la que hay que enviar al cliente
     */
    @Transactional
    public StripeStart startStripePayment(UUID tenantId, PaymentProvider.PaymentRequest request) {
        var checkout = stripeProvider.createPayment(request);
        Payment payment = paymentRepository.save(newPayment(tenantId, request.orderId(), PaymentProviderType.STRIPE,
                checkout.providerPaymentId(), request.amount(), request.currency()));
        return new StripeStart(payment, checkout.redirectUrl());
    }

    public record StripeStart(Payment payment, String redirectUrl) {
    }

    /** Pedidos de importe 0: se registra un "pago" FREE ya completado para dejar constancia. */
    @Transactional
    public Payment recordFreePayment(UUID tenantId, UUID orderId, String currency) {
        Payment payment = newPayment(tenantId, orderId, PaymentProviderType.FREE, "free_" + orderId,
                BigDecimal.ZERO, currency);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        return paymentRepository.save(payment);
    }

    /**
     * Reembolsa el cobro confirmado de un pedido a traves de su proveedor.
     * Si el proveedor falla, se lanza la excepcion y la transaccion del reembolso se deshace entera.
     *
     * @return el pago reembolsado
     */
    @Transactional
    public Payment refundSucceededPayment(UUID orderId, BigDecimal amount) {
        Payment payment = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                .findFirst()
                .orElseThrow(() -> new ConflictException("El pedido no tiene ningun cobro confirmado que reembolsar"));
        String refundId = switch (payment.getProvider()) {
            case SIMULATED -> simulatedProvider.refund(payment, amount);
            case STRIPE -> stripeProvider.refund(payment, amount);
            case FREE -> null; // Nada que devolver.
        };
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAmount(amount);
        payment.setProviderRefundId(refundId);
        payment.setRefundedAt(Instant.now(clock));
        return payment;
    }

    /**
     * Cobro de Stripe que llega cuando el pedido ya no lo admite (caducado, cancelado o pagado con
     * otro intento): se devuelve el dinero automaticamente para no cobrar sin entregar entradas.
     */
    @Transactional
    public void refundOrphanStripePayment(Payment payment, String reason) {
        String refundId = stripeProvider.refund(payment, payment.getAmount());
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAmount(payment.getAmount());
        payment.setProviderRefundId(refundId);
        payment.setRefundedAt(Instant.now(clock));
        payment.setFailureReason(reason);
    }

    /** Metodos de pago disponibles en este entorno (la web solo muestra los activos). */
    public PaymentMethods availableMethods(boolean simulatedEnabled) {
        return new PaymentMethods(simulatedEnabled, stripeProvider.isEnabled());
    }

    public record PaymentMethods(boolean simulated, boolean stripe) {
    }

    @Transactional(readOnly = true)
    public Payment findByProviderId(String providerPaymentId) {
        return paymentRepository.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> NotFoundException.of("Pago"));
    }

    @Transactional(readOnly = true)
    public List<Payment> findByOrder(UUID orderId) {
        return paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    private static Payment newPayment(UUID tenantId, UUID orderId, PaymentProviderType type, String providerId,
                                      BigDecimal amount, String currency) {
        Payment payment = new Payment();
        payment.setTenantId(tenantId);
        payment.setOrderId(orderId);
        payment.setProvider(type);
        payment.setProviderPaymentId(providerId);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        return payment;
    }
}
