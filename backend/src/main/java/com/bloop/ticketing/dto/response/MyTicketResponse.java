package com.bloop.ticketing.dto.response;

import com.bloop.ticketing.entity.TicketStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Entrada vista por su propietario. qrPayload es el texto que Angular convierte en imagen QR.
 */
public record MyTicketResponse(UUID id, UUID orderId, String eventName, String eventSlug, String venueName,
                               String venueAddress, String city, Instant startsAt, Instant doorsOpenAt,
                               String ticketTypeName, String holderName, TicketStatus status, Instant usedAt,
                               String qrPayload, String sessionTitle, String imageUrl, Instant endsAt,
                               Integer minAge) {
}
