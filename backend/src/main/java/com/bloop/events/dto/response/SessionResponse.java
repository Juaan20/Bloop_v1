package com.bloop.events.dto.response;

import com.bloop.events.entity.SessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionResponse(UUID id, Instant startsAt, Instant endsAt, Instant doorsOpenAt, int capacity,
                              SessionStatus status, int ticketsSold, int ticketsConfigured,
                              List<TicketTypeResponse> ticketTypes, String title, String description,
                              String imageUrl, boolean salesPaused) {
}
