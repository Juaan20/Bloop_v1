package com.bloop.venues.dto.response;

import com.bloop.venues.entity.VenueStatus;

import java.time.Instant;
import java.util.UUID;

public record VenueResponse(UUID id, String name, String slug, String description, String addressLine, String city,
                            String postalCode, String country, String phone, String email, int capacity,
                            String openingHours, String imageUrl, VenueStatus status, Instant createdAt) {
}
