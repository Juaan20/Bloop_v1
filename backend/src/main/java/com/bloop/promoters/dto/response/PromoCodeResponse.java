package com.bloop.promoters.dto.response;

import com.bloop.promoters.entity.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PromoCodeResponse(UUID id, String code, String description, DiscountType discountType,
                                BigDecimal discountValue, UUID eventId, String eventName, UUID promoterId,
                                String promoterName, Integer maxUses, int timesUsed, Instant validFrom,
                                Instant validUntil, boolean active, Instant createdAt) {
}
