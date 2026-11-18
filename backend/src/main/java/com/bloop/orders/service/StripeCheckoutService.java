package com.bloop.orders.service;

import com.bloop.orders.dto.response.StripeSessionResponse;
import com.bloop.orders.entity.OrderStatus;
import com.bloop.orders.entity.TicketOrder;
import com.bloop.orders.repository.TicketOrderRepository;
import com.bloop.payments.service.PaymentProvider;
import com.bloop.payments.service.PaymentService;
import com.bloop.payments.service.StripePaymentProvider;
import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Inicio del pago con tarjeta mediante Stripe Checkout (modo test).
 *
 * Devuelve la URL de la pagina de pago de Stripe. El resultado NO llega por aqui: Stripe lo
 * comunica despues por webhook (StripeWebhookService). Mientras la pagina de Stripe este abierta,
 * se amplia la reserva del pedido para que no caduque a mitad del pago.
 */
@Service
@RequiredArgsConstructor
public class StripeCheckoutService {

    private final TicketOrderRepository orderRepository;
    private final PaymentService paymentService;
    private final StripePaymentProvider stripeProvider;
    private final CurrentUserProvider currentUserProvider;
    private final BloopProperties properties;
    private final Clock clock;

    @Transactional
    public StripeSessionResponse startCheckout(UUID orderId) {
        UUID customerId = currentUserProvider.get().userId();
        TicketOrder order = orderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.getCustomerUserId().equals(customerId))
                .orElseThrow(() -> NotFoundException.of("Pedido"));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("El pedido ya no admite pagos (estado " + order.getStatus() + ")");
        }
        Instant now = Instant.now(clock);
        if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(now)) {
            throw new ConflictException("El tiempo para pagar ha terminado. Vuelve a seleccionar tus entradas");
        }
        if (order.getTotalAmount().signum() == 0) {
            throw new BadRequestException("Este pedido es gratuito y no necesita pago");
        }

        // La reserva dura al menos lo mismo que la pagina de pago de Stripe (+1 minuto de margen).
        Instant keepUntil = stripeProvider.sessionExpiry().plus(1, ChronoUnit.MINUTES);
        if (order.getExpiresAt() == null || order.getExpiresAt().isBefore(keepUntil)) {
            order.setExpiresAt(keepUntil);
        }

        String base = properties.publicUrl() + "/checkout/" + order.getId();
        var event = order.getSession().getEvent();
        var request = new PaymentProvider.PaymentRequest(order.getId(), order.getReference(), order.getTotalAmount(),
                order.getCurrency(), event.getName() + " - " + order.getTicketCount() + " entradas (" + order.getReference() + ")",
                order.getBuyerEmail(), base + "?pago=ok", base + "?pago=cancelado");
        var start = paymentService.startStripePayment(order.getTenantId(), request);
        return new StripeSessionResponse(start.redirectUrl());
    }
}
