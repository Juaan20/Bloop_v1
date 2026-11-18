package com.bloop.orders.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.customers.service.CustomerService;
import com.bloop.events.repository.TicketTypeRepository;
import com.bloop.orders.dto.response.OrderResponse;
import com.bloop.orders.entity.OrderStatus;
import com.bloop.orders.entity.TicketOrder;
import com.bloop.orders.entity.TicketOrderItem;
import com.bloop.orders.mapper.OrderMapper;
import com.bloop.orders.repository.TicketOrderRepository;
import com.bloop.payments.service.PaymentService;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.ticketing.entity.TicketStatus;
import com.bloop.ticketing.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Reembolso de pedidos de entradas.
 *
 * Un reembolso, en una unica transaccion:
 * 1. Bloquea el pedido (SELECT ... FOR UPDATE): dos reembolsos simultaneos no devuelven dos veces.
 * 2. Devuelve el dinero en el proveedor (simulado o Stripe). Si el proveedor falla, no se cambia nada.
 * 3. Anula las entradas no usadas (sus QR dejan de valer en la puerta) y devuelve el stock.
 * 4. Pone a cero la comision del promotor y descuenta la compra de la ficha CRM.
 * 5. Marca el pedido como REFUNDED, lo audita y avisa al cliente por email (tras el commit).
 *
 * Es idempotente: reembolsar un pedido ya reembolsado no hace nada.
 */
@Service
@RequiredArgsConstructor
public class OrderRefundService {

    private final TicketOrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final PaymentService paymentService;
    private final CustomerService customerService;
    private final OrderMapper orderMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** Reembolso manual desde el backoffice (permiso ORDER_REFUND). */
    @Transactional
    public OrderResponse refundAsStaff(UUID orderId, String reason) {
        UUID tenantId = currentUserProvider.requireTenantId();
        TicketOrder order = lock(orderId);
        // Un pedido de otra empresa responde 404, como si no existiera.
        if (!order.getTenantId().equals(tenantId)) {
            throw NotFoundException.of("Pedido");
        }
        if (order.getStatus() == OrderStatus.PAID
                && ticketRepository.countByOrderIdAndStatus(orderId, TicketStatus.USED) > 0) {
            throw new ConflictException("El pedido tiene entradas ya utilizadas en la puerta: no se puede reembolsar");
        }
        refund(order, reason);
        return orderMapper.toResponse(order);
    }

    /**
     * Reembolso automatico por cancelacion de un evento o sesion. Se ejecuta en su propia transaccion
     * (REQUIRES_NEW) para que el fallo de un pedido no deshaga los reembolsos de los demas.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refundForCancellation(UUID orderId, String reason) {
        refund(lock(orderId), reason);
    }

    private void refund(TicketOrder order, String reason) {
        if (order.getStatus() == OrderStatus.REFUNDED) {
            return; // Ya reembolsado: no repetimos nada.
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new ConflictException("Solo se pueden reembolsar pedidos pagados (estado " + order.getStatus() + ")");
        }
        paymentService.refundSucceededPayment(order.getId(), order.getTotalAmount());

        ticketRepository.cancelValidByOrder(order.getId());
        for (TicketOrderItem item : order.getItems()) {
            ticketTypeRepository.releaseStock(item.getTicketType().getId(), item.getQuantity());
        }
        customerService.registerRefund(order.getTenantId(), order.getCustomerUserId(), order.getTotalAmount(),
                order.getTicketCount());

        BigDecimal commission = order.getCommissionAmount();
        order.setCommissionAmount(BigDecimal.ZERO);
        order.setStatus(OrderStatus.REFUNDED);
        order.setRefundedAt(Instant.now(clock));
        order.setRefundReason(reason == null || reason.isBlank() ? null : truncate(reason.trim()));

        auditService.recordInTenant(order.getTenantId(), AuditAction.ORDER_REFUNDED, "TicketOrder", order.getId(),
                order.getReference() + " importe=" + order.getTotalAmount() + " " + order.getCurrency()
                        + (commission.signum() > 0 ? " comision anulada=" + commission : "")
                        + (order.getRefundReason() != null ? " motivo=" + order.getRefundReason() : ""));
        eventPublisher.publishEvent(new OrderRefundedEvent(order.getId()));
    }

    private TicketOrder lock(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> NotFoundException.of("Pedido"));
    }

    private static String truncate(String value) {
        return value.length() <= 255 ? value : value.substring(0, 255);
    }
}
