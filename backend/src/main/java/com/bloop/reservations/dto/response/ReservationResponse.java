package com.bloop.reservations.dto.response;

import com.bloop.reservations.entity.ReservationSource;
import com.bloop.reservations.entity.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Reserva VIP (backoffice y "Mis reservas"). */
public record ReservationResponse(UUID id, String reference, UUID venueId, String venueName, String zoneName,
                                  UUID tableId, String tableName, UUID sessionId, String eventName,
                                  String contactName, String contactEmail, String contactPhone, int guests,
                                  Instant startsAt, Instant endsAt, ReservationStatus status, BigDecimal minSpend,
                                  BigDecimal depositAmount, Instant depositPaidAt, boolean depositPending,
                                  String notes, ReservationSource source, UUID customerId, Instant createdAt) {
}
