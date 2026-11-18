package com.bloop.pos.dto.response;

import com.bloop.pos.entity.PaymentMethod;
import com.bloop.pos.entity.PosSaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ticket de una venta del TPV. */
public record PosSaleResponse(UUID id, String reference, PosSaleStatus status, UUID terminalId, UUID cashSessionId,
                              BigDecimal subtotalAmount, BigDecimal discountAmount, BigDecimal totalAmount,
                              PaymentMethod paymentMethod, BigDecimal cashReceived, BigDecimal changeGiven,
                              String discountReason, String discountAuthorizedByName, String soldByName,
                              String voidedByName, Instant voidedAt, String voidReason, Instant createdAt,
                              List<Item> items) {

    public record Item(UUID variantId, String productName, String variantName, int quantity, BigDecimal unitPrice,
                       BigDecimal lineTotal, String mixerName) {
    }
}
