package com.bloop.customers.dto.response;

import com.bloop.orders.entity.OrderStatus;
import com.bloop.reservations.entity.ReservationStatus;
import com.bloop.ticketing.entity.TicketStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Exportacion RGPD que descarga el propio cliente: todos sus datos en la plataforma
 * (cuenta, pedidos, entradas, reservas y empresas que tienen su ficha).
 */
public record MyDataExportResponse(Instant exportedAt, Account account, List<Order> orders, List<Ticket> tickets,
                                   List<Reservation> reservations, List<CompanyRecord> companies) {

    public record Account(UUID id, String email, String firstName, String lastName, String phone,
                          boolean marketingConsent, Instant privacyAcceptedAt, Instant createdAt) {
    }

    public record Order(String reference, String eventName, Instant sessionStartsAt, OrderStatus status,
                        BigDecimal totalAmount, String currency, Instant createdAt) {
    }

    public record Ticket(String eventName, Instant startsAt, String ticketTypeName, String holderName,
                         TicketStatus status) {
    }

    public record Reservation(String reference, String tableName, Instant startsAt, int guests,
                              ReservationStatus status) {
    }

    public record CompanyRecord(String companyName, boolean marketingConsent, int ordersCount,
                                BigDecimal totalSpent, Instant lastPurchaseAt) {
    }
}
