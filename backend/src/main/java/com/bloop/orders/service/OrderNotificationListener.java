package com.bloop.orders.service;

import com.bloop.notifications.entity.NotificationType;
import com.bloop.notifications.service.EmailMessage;
import com.bloop.notifications.service.NotificationService;
import com.bloop.orders.entity.TicketOrder;
import com.bloop.orders.repository.TicketOrderRepository;
import com.bloop.shared.config.BloopProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


/**
 * Envia el email de confirmacion de compra. Se ejecuta solo si la transaccion del pago
 * se ha confirmado (AFTER_COMMIT): nunca avisamos de una compra que luego se deshizo.
 */
@Component
@RequiredArgsConstructor
public class OrderNotificationListener {

    private final TicketOrderRepository orderRepository;
    private final NotificationService notificationService;
    private final BloopProperties properties;
    private final TicketEmailBuilder ticketEmailBuilder;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onOrderPaid(OrderPaidEvent event) {
        TicketOrder order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            return;
        }
        var eventEntity = order.getSession().getEvent();
        EmailMessage message = ticketEmailBuilder.build(order, "Tus entradas para " + eventEntity.getName(),
                "Tu compra se ha completado correctamente. Aqui tienes tus entradas con su codigo QR.",
                properties.publicUrl());
        notificationService.sendEmail(order.getTenantId(), order.getBuyerEmail(), NotificationType.ORDER_CONFIRMED,
                "Tus entradas para " + eventEntity.getName() + " - " + order.getReference(), message);
    }

    /** Aviso de reembolso al cliente (tambien tras el commit). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onOrderRefunded(OrderRefundedEvent event) {
        TicketOrder order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            return;
        }
        var eventEntity = order.getSession().getEvent();
        EmailMessage message = EmailMessage.of("Pedido reembolsado", "Hola " + order.getBuyerName() + ",")
                .paragraph("Hemos reembolsado tu pedido de \"" + eventEntity.getName() + "\".")
                .detail("Pedido", order.getReference())
                .detail("Importe devuelto", order.getTotalAmount() + " " + order.getCurrency())
                .detail("Motivo", order.getRefundReason())
                .paragraph("Las entradas de este pedido ya no son validas. El dinero llegara a tu medio de pago "
                        + "en los plazos habituales de tu banco.")
                .button("Ver mis pedidos", properties.publicUrl() + "/cuenta/pedidos");
        notificationService.sendEmail(order.getTenantId(), order.getBuyerEmail(), NotificationType.ORDER_REFUNDED,
                "Reembolso de tu pedido " + order.getReference(), message);
    }
}
