package com.bloop.orders.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.customers.service.CustomerService;
import com.bloop.orders.entity.OrderStatus;
import com.bloop.orders.entity.TicketOrder;
import com.bloop.orders.repository.TicketOrderRepository;
import com.bloop.payments.entity.Payment;
import com.bloop.payments.entity.PaymentStatus;
import com.bloop.payments.service.PaymentService;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.ticketing.service.TicketIssuingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Aplica el resultado de un pago a su pedido. Es IDEMPOTENTE: si el proveedor notifica
 * dos veces el mismo pago (algo habitual con webhooks), la segunda llamada no hace nada.
 * El bloqueo de fila del pedido (findByIdForUpdate) impide procesar dos pagos a la vez.
 */
@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentService.class);

    private final TicketOrderRepository orderRepository;
    private final PaymentService paymentService;
    private final TicketIssuingService ticketIssuingService;
    private final AuditService auditService;
    private final CustomerService customerService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public TicketOrder confirmPayment(String providerPaymentId) {
        Payment payment = paymentService.findByProviderId(providerPaymentId);
        TicketOrder order = lockOrder(payment);

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return order; // Notificacion repetida: ya procesada.
        }
        if (order.getStatus() == OrderStatus.PAID) {
            // Segundo cobro correcto para un pedido ya pagado: no emitimos entradas otra vez.
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Pedido ya pagado con otro cobro: requiere reembolso");
            log.warn("Pago duplicado {} para el pedido {}", providerPaymentId, order.getReference());
            return order;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("El pedido no admite pagos (estado " + order.getStatus() + ")");
        }
        payment.setStatus(PaymentStatus.SUCCEEDED);
        markPaid(order);
        return order;
    }

    @Transactional
    public void failPayment(String providerPaymentId, String reason) {
        Payment payment = paymentService.findByProviderId(providerPaymentId);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        auditService.recordInTenant(payment.getTenantId(), AuditAction.PAYMENT_FAILED, "Payment", payment.getId(), reason);
    }

    /** Pedido de importe 0: se registra un pago FREE y se emiten las entradas. */
    @Transactional
    public void completeFreeOrder(TicketOrder order) {
        paymentService.recordFreePayment(order.getTenantId(), order.getId(), order.getCurrency());
        markPaid(order);
    }

    private void markPaid(TicketOrder order) {
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(Instant.now(clock));
        order.setExpiresAt(null);
        ticketIssuingService.issue(order.getTenantId(), order.getId(), order.getCustomerUserId(), order.getBuyerName(),
                order.getItems().stream()
                        .map(item -> new TicketIssuingService.IssueLine(item.getId(), item.getTicketType(),
                                item.getQuantity()))
                        .toList(),
                order.getAttendees());
        // CRM: la compra se suma a la ficha del cliente en esta empresa.
        customerService.registerPaidOrder(order.getTenantId(), order.getCustomerUserId(), order.getTotalAmount(),
                order.getTicketCount(), order.getPaidAt(), order.isMarketingConsent());
        auditService.recordInTenant(order.getTenantId(), AuditAction.ORDER_PAID, "TicketOrder", order.getId(),
                order.getReference() + " total=" + order.getTotalAmount() + " " + order.getCurrency());
        eventPublisher.publishEvent(new OrderPaidEvent(order.getId()));
    }

    private TicketOrder lockOrder(Payment payment) {
        return orderRepository.findByIdForUpdate(payment.getOrderId())
                .orElseThrow(() -> NotFoundException.of("Pedido"));
    }
}
