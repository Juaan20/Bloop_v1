package com.bloop.orders.service;

import com.bloop.events.repository.TicketTypeRepository;
import com.bloop.orders.dto.response.OrderResponse;
import com.bloop.orders.entity.OrderStatus;
import com.bloop.orders.entity.TicketOrder;
import com.bloop.orders.entity.TicketOrderItem;
import com.bloop.orders.mapper.OrderMapper;
import com.bloop.orders.repository.TicketOrderRepository;
import com.bloop.promoters.service.PromoCodeService;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Cierre de pedidos pendientes (caducidad o cancelacion) devolviendo su stock. */
@Service
@RequiredArgsConstructor
public class OrderLifecycleService {

    private final TicketOrderRepository orderRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final PromoCodeService promoCodeService;
    private final OrderMapper orderMapper;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    /** Caduca un pedido pendiente cuyo plazo de pago ha vencido. Devuelve true si se caduco. */
    @Transactional
    public boolean expire(UUID orderId) {
        TicketOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        Instant now = Instant.now(clock);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT
                || order.getExpiresAt() == null || order.getExpiresAt().isAfter(now)) {
            return false;
        }
        releaseStock(order);
        order.setStatus(OrderStatus.EXPIRED);
        return true;
    }

    /** El cliente abandona un pedido pendiente: se libera el stock inmediatamente. */
    @Transactional
    public OrderResponse cancelMyPendingOrder(UUID orderId) {
        UUID customerId = currentUserProvider.get().userId();
        TicketOrder order = orderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.getCustomerUserId().equals(customerId))
                .orElseThrow(() -> NotFoundException.of("Pedido"));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("Solo se pueden cancelar pedidos pendientes de pago");
        }
        releaseStock(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now(clock));
        order.setExpiresAt(null);
        return orderMapper.toResponse(order);
    }

    /** Devuelve el stock de las entradas y, si se uso un codigo promocional, su uso. */
    private void releaseStock(TicketOrder order) {
        for (TicketOrderItem item : order.getItems()) {
            ticketTypeRepository.releaseStock(item.getTicketType().getId(), item.getQuantity());
        }
        if (order.getPromoCodeId() != null) {
            promoCodeService.releaseUse(order.getPromoCodeId());
        }
    }
}
