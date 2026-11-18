package com.bloop.customers.dto.response;

import com.bloop.customers.entity.ConsentSource;
import com.bloop.customers.entity.ConsentType;
import com.bloop.orders.entity.OrderStatus;
import com.bloop.reservations.entity.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ficha completa del cliente: datos, historial de compras y reservas, y consentimientos. */
public record CustomerDetailResponse(CustomerResponse customer, String notes, List<OrderSummary> orders,
                                     List<ReservationSummary> reservations, List<ConsentInfo> consents) {

    public record OrderSummary(UUID id, String reference, String eventName, Instant sessionStartsAt,
                               OrderStatus status, int ticketCount, BigDecimal totalAmount, String currency,
                               Instant createdAt) {
    }

    public record ReservationSummary(UUID id, String reference, String zoneName, String tableName,
                                     Instant startsAt, int guests, ReservationStatus status) {
    }

    public record ConsentInfo(ConsentType type, boolean granted, ConsentSource source, Instant createdAt) {
    }
}
