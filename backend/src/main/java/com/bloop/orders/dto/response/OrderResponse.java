package com.bloop.orders.dto.response;

import com.bloop.orders.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pedido. totalAmount = subtotalAmount - discountAmount (descuento del codigo promocional promoCode).
 */
public record OrderResponse(UUID id, String reference, OrderStatus status, BigDecimal subtotalAmount,
                            BigDecimal discountAmount, BigDecimal totalAmount, String currency, String promoCode,
                            String eventName, String eventSlug, String venueName, UUID sessionId,
                            Instant sessionStartsAt, String buyerName, String buyerEmail, int ticketCount,
                            List<OrderItemResponse> items, Instant expiresAt, Instant paidAt, Instant createdAt,
                            Instant refundedAt, String refundReason, String sessionTitle) {
}
