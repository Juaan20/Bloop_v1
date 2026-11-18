package com.bloop.orders.service;

import com.bloop.orders.entity.OrderStatus;
import com.bloop.orders.entity.TicketOrder;
import com.bloop.orders.repository.TicketOrderRepository;
import com.bloop.payments.entity.Payment;
import com.bloop.payments.entity.PaymentProviderType;
import com.bloop.payments.entity.PaymentStatus;
import com.bloop.payments.repository.PaymentRepository;
import com.bloop.payments.repository.StripeWebhookEventRepository;
import com.bloop.payments.service.PaymentService;
import com.bloop.payments.service.StripeSignatureVerifier;
import com.bloop.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Procesa los webhooks de Stripe.
 *
 * Seguridad e idempotencia:
 * 1. Se verifica la firma (Stripe-Signature) sobre el cuerpo EXACTO recibido.
 * 2. Cada evento se registra por su id (stripe_webhook_events): si Stripe lo reenvia, se ignora.
 * 3. Todo ocurre en una transaccion: si algo falla, no queda nada a medias y Stripe reintentara.
 *
 * Eventos tratados:
 * - checkout.session.completed (pagado) y checkout.session.async_payment_succeeded -> pedido pagado.
 * - checkout.session.expired y checkout.session.async_payment_failed -> intento fallido.
 */
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private final StripeSignatureVerifier signatureVerifier;
    private final StripeWebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final TicketOrderRepository orderRepository;
    private final OrderPaymentService orderPaymentService;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Transactional
    public void handle(String payload, String signatureHeader) {
        signatureVerifier.verify(payload, signatureHeader);
        Map<String, Object> event = parse(payload);
        String eventId = String.valueOf(event.get("id"));
        String type = String.valueOf(event.get("type"));
        if (!webhookEventRepository.registerIfNew(eventId, type, Instant.now(clock))) {
            log.info("Webhook de Stripe {} ya procesado: se ignora", eventId);
            return;
        }
        Map<String, Object> session = sessionObject(event);
        String sessionId = String.valueOf(session.get("id"));
        switch (type) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {
                if ("paid".equals(session.get("payment_status"))) {
                    onPaid(sessionId, (String) session.get("payment_intent"));
                }
            }
            case "checkout.session.expired" -> fail(sessionId, "La pagina de pago de Stripe ha caducado");
            case "checkout.session.async_payment_failed" -> fail(sessionId, "Stripe ha rechazado el pago");
            default -> log.debug("Webhook de Stripe {} no utilizado", type);
        }
    }

    private void onPaid(String sessionId, String paymentIntentId) {
        Payment payment = paymentRepository.findByProviderPaymentId(sessionId)
                .filter(p -> p.getProvider() == PaymentProviderType.STRIPE)
                .orElse(null);
        if (payment == null) {
            log.warn("Webhook de Stripe para una sesion desconocida {}", sessionId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        payment.setProviderReference(paymentIntentId);
        TicketOrder order = orderRepository.findByIdForUpdate(payment.getOrderId()).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            // Stripe ha cobrado pero el pedido ya no admite el pago: devolvemos el dinero.
            paymentService.refundOrphanStripePayment(payment,
                    "Cobro recibido con el pedido " + (order == null ? "inexistente" : order.getStatus())
                            + ": reembolsado automaticamente");
            return;
        }
        orderPaymentService.confirmPayment(sessionId);
    }

    private void fail(String sessionId, String reason) {
        paymentRepository.findByProviderPaymentId(sessionId)
                .ifPresent(payment -> orderPaymentService.failPayment(sessionId, reason));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String payload) {
        try {
            return jsonMapper.readValue(payload, Map.class);
        } catch (JacksonException e) {
            throw new BadRequestException("Webhook con JSON no valido");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sessionObject(Map<String, Object> event) {
        if (event.get("data") instanceof Map<?, ?> data && data.get("object") instanceof Map<?, ?> object) {
            return (Map<String, Object>) object;
        }
        throw new BadRequestException("Webhook sin datos");
    }
}
