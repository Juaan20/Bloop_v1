package com.bloop.promoters.dto.response;

import com.bloop.promoters.entity.CommissionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Promotor con el resumen de sus ventas pagadas. */
public record PromoterResponse(UUID id, UUID userId, String name, String email, String referralCode,
                               CommissionType commissionType, BigDecimal commissionValue, boolean active,
                               int eventsAssigned, long ticketsSold, long orders, BigDecimal revenue,
                               BigDecimal commission, Instant createdAt) {
}
