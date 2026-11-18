package com.bloop.events.dto.response;

import com.bloop.events.entity.EventStatus;

import java.time.Instant;
import java.util.UUID;

/** Fila del listado de eventos del backoffice. */
public record EventSummaryResponse(UUID id, String name, String slug, EventStatus status, UUID venueId,
                                   String venueName, Instant nextSessionAt, int sessionsCount, int ticketsSold,
                                   int ticketsConfigured) {
}
