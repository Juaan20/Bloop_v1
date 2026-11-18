package com.bloop.events.dto.response;

import com.bloop.events.entity.EventStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventDetailResponse(UUID id, String name, String slug, String description, String imageUrl,
                                  Integer minAge, EventStatus status, Instant publishedAt, UUID venueId,
                                  String venueName, int venueCapacity, List<SessionResponse> sessions,
                                  Instant createdAt) {
}
