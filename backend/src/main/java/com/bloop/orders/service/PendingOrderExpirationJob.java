package com.bloop.orders.service;

import com.bloop.orders.entity.OrderStatus;
import com.bloop.orders.repository.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tarea programada: cada minuto caduca los pedidos pendientes cuyo plazo de pago termino
 * y libera su stock. Cada pedido se procesa en su propia transaccion.
 */
@Component
@RequiredArgsConstructor
public class PendingOrderExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderExpirationJob.class);

    private final TicketOrderRepository orderRepository;
    private final OrderLifecycleService orderLifecycleService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    public void expirePendingOrders() {
        List<UUID> ids = orderRepository.findIdsByStatusAndExpiresAtBefore(OrderStatus.PENDING_PAYMENT,
                Instant.now(clock));
        int expired = 0;
        for (UUID id : ids) {
            try {
                if (orderLifecycleService.expire(id)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo caducar el pedido {}: {}", id, e.getMessage());
            }
        }
        if (expired > 0) {
            log.info("Pedidos pendientes caducados: {}", expired);
        }
    }
}
