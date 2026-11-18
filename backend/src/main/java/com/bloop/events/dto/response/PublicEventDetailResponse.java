package com.bloop.events.dto.response;

import com.bloop.events.entity.SaleStatus;
import com.bloop.events.entity.TicketCategory;
import com.bloop.venues.dto.response.PublicVenueResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ficha publica de un evento con sus proximas sesiones y entradas disponibles. */
public record PublicEventDetailResponse(String slug, String name, String description, String imageUrl,
                                        Integer minAge, PublicVenueResponse venue, List<Session> sessions) {

    /** Un dia del evento. title, description e imageUrl son los propios del dia (pueden ser null). */
    public record Session(UUID id, Instant startsAt, Instant endsAt, Instant doorsOpenAt, boolean soldOut,
                          List<Ticket> ticketTypes, String title, String description, String imageUrl,
                          boolean salesPaused) {
    }

    public record Ticket(UUID id, String name, String description, TicketCategory category, BigDecimal price,
                         String currency, int available, int maxPerOrder, SaleStatus saleStatus) {
    }
}
