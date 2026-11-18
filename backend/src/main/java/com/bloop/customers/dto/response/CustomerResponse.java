package com.bloop.customers.dto.response;

import com.bloop.customers.entity.CustomerSegment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Cliente en el listado del CRM. */
public record CustomerResponse(UUID id, String email, String firstName, String lastName, String phone, String tags,
                               boolean marketingConsent, int ordersCount, int ticketsCount, BigDecimal totalSpent,
                               Instant firstPurchaseAt, Instant lastPurchaseAt, List<CustomerSegment> segments,
                               boolean hasAccount, Instant createdAt) {
}
