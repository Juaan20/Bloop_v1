package com.bloop.orders.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.notifications.entity.NotificationType;
import com.bloop.notifications.service.EmailMessage;
import com.bloop.notifications.service.NotificationService;
import com.bloop.orders.entity.OrderStatus;
import com.bloop.orders.entity.TicketOrder;
import com.bloop.orders.repository.TicketOrderRepository;
import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Reenvio por correo de las entradas de un pedido pagado. Lo puede pedir el propio cliente
 * o un empleado (ORDER_MANAGE). Para evitar abusos, como mucho un reenvio por minuto y destinatario.
 */
@Service
@RequiredArgsConstructor
public class OrderResendService {

    static final Duration MIN_INTERVAL = Duration.ofMinutes(1);

    private final TicketOrderRepository orderRepository;
    private final TicketEmailBuilder ticketEmailBuilder;
    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final BloopProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public void resendMine(UUID orderId) {
        TicketOrder order = orderRepository.findByIdAndCustomerUserId(orderId, currentUserProvider.get().userId())
                .orElseThrow(() -> NotFoundException.of("Pedido"));
        resend(order);
    }

    @Transactional(readOnly = true)
    public void resendForTenant(UUID orderId) {
        TicketOrder order = orderRepository.findByIdAndTenantId(orderId, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Pedido"));
        resend(order);
    }

    private void resend(TicketOrder order) {
        if (order.getStatus() != OrderStatus.PAID) {
            throw new ConflictException("Solo se pueden reenviar las entradas de pedidos pagados");
        }
        Instant now = Instant.now(clock);
        if (notificationService.wasSentSince(order.getBuyerEmail(), NotificationType.TICKETS_RESENT,
                now.minus(MIN_INTERVAL))) {
            throw new ConflictException("Las entradas se acaban de reenviar. Espera un minuto antes de repetirlo");
        }
        var event = order.getSession().getEvent();
        EmailMessage message = ticketEmailBuilder.build(order, "Tus entradas para " + event.getName(),
                "Te reenviamos las entradas de tu pedido. Si has transferido alguna, su QR ahora lo tiene "
                        + "la otra persona y no aparece aqui.", properties.publicUrl());
        notificationService.sendEmail(order.getTenantId(), order.getBuyerEmail(), NotificationType.TICKETS_RESENT,
                "Tus entradas para " + event.getName() + " - " + order.getReference(), message);
        auditService.recordInTenant(order.getTenantId(), AuditAction.TICKETS_RESENT, "TicketOrder", order.getId(),
                order.getReference());
    }
}
