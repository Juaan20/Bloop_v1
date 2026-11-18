package com.bloop.events.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Tarjeta de la cartelera publica: una sesion de un evento. */
public record PublicEventCardResponse(String eventSlug, String eventName, String imageUrl, String venueName,
                                      String venueSlug, String city, UUID sessionId, Instant startsAt,
                                      BigDecimal minPrice, String currency, boolean soldOut,
                                      String sessionTitle) {
}
