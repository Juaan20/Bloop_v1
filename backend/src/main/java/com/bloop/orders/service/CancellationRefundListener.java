package com.bloop.orders.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.events.service.EventChangeNotice;
import com.bloop.notifications.entity.AlertType;
import com.bloop.notifications.service.AdminAlertService;
import com.bloop.orders.entity.OrderStatus;
import com.bloop.orders.repository.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

/**
 * Cancelacion con devolucion: cuando se cancela un evento o una sesion (y la cancelacion ya se ha
 * guardado), reembolsa automaticamente todos sus pedidos pagados.
 *
 * - Cada pedido se reembolsa en su propia transaccion: si uno falla (p. ej. Stripe no responde),
 *   los demas se reembolsan igualmente.
 * - Los fallos generan una alerta en el panel para que el equipo los revise y los reembolse a mano
 *   desde la ficha del pedido.
 *
 * Escucha el mismo aviso (EventChangeNotice) que usa el modulo de entradas para enviar los emails
 * de cancelacion, asi el modulo de eventos no necesita conocer los pedidos.
 */
@Component
@RequiredArgsConstructor
public class CancellationRefundListener {

    private static final Logger log = LoggerFactory.getLogger(CancellationRefundListener.class);

    private final TicketOrderRepository orderRepository;
    private final OrderRefundService orderRefundService;
    private final AdminAlertService alertService;
    private final AuditService auditService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // Transaccion propia (tras el commit de la cancelacion) para guardar alertas y auditoria de los fallos.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCancellation(EventChangeNotice notice) {
        if (notice.kind() == EventChangeNotice.Kind.SESSION_RESCHEDULED || notice.sessionIds().isEmpty()) {
            return;
        }
        String reason = notice.kind() == EventChangeNotice.Kind.EVENT_CANCELLED
                ? "Evento cancelado por el organizador"
                : "Sesion cancelada por el organizador";
        List<UUID> orderIds = orderRepository.findIdsBySessionIdsAndStatus(notice.sessionIds(), OrderStatus.PAID);
        int refunded = 0;
        for (UUID orderId : orderIds) {
            try {
                orderRefundService.refundForCancellation(orderId, reason);
                refunded++;
            } catch (RuntimeException e) {
                log.warn("No se pudo reembolsar el pedido {} tras la cancelacion: {}", orderId, e.getMessage());
                orderRepository.findById(orderId).ifPresent(order -> {
                    auditService.recordInTenant(order.getTenantId(), AuditAction.REFUND_FAILED, "TicketOrder",
                            orderId, order.getReference() + ": " + e.getMessage());
                    alertService.raise(order.getTenantId(), AlertType.REFUND_FAILED,
                            "Reembolso pendiente: " + order.getReference(),
                            "No se pudo reembolsar automaticamente tras la cancelacion. Reembolsalo desde la ficha del pedido.",
                            "/admin/ventas/" + orderId, orderId);
                });
            }
        }
        if (!orderIds.isEmpty()) {
            log.info("Cancelacion {}: {} de {} pedidos reembolsados", notice.eventId(), refunded, orderIds.size());
        }
    }
}
