package com.bloop.orders.dto.response;

import com.bloop.payments.entity.PaymentProviderType;
import com.bloop.payments.entity.PaymentStatus;
import com.bloop.ticketing.entity.TicketStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detalle de un pedido para el backoffice: pedido, intentos de pago, entradas emitidas
 * y promotor al que se atribuye la venta (promoterName null si no hay).
 */
public record OrderDetailResponse(OrderResponse order, List<PaymentInfo> payments, List<TicketInfo> tickets,
                                  String promoterName, BigDecimal commissionAmount) {

    public record PaymentInfo(UUID id, PaymentProviderType provider, PaymentStatus status, BigDecimal amount,
                              String failureReason, Instant createdAt) {
    }

    public record TicketInfo(UUID id, String ticketTypeName, String holderName, TicketStatus status, Instant usedAt) {
    }
}
