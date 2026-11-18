package com.bloop.events.dto.response;

import com.bloop.events.entity.SaleStatus;
import com.bloop.events.entity.TicketCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TicketTypeResponse(UUID id, String name, String description, TicketCategory category,
                                 BigDecimal price, String currency, int quantityTotal, int quantitySold,
                                 int available, int maxPerOrder, Instant salesStartAt, Instant salesEndAt,
                                 boolean active, int sortOrder, SaleStatus saleStatus) {
}
