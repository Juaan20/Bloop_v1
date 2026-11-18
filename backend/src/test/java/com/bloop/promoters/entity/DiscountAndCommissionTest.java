package com.bloop.promoters.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Calculos de descuentos de codigos promocionales y de comisiones de promotores. */
class DiscountAndCommissionTest {

    @Test
    void percentageDiscountIsRoundedToCents() {
        PromoCode promo = promo(DiscountType.PERCENTAGE, "15");
        assertThat(promo.discountFor(new BigDecimal("33.33"))).isEqualByComparingTo("5.00");
    }

    @Test
    void fixedDiscountNeverExceedsTheSubtotal() {
        PromoCode promo = promo(DiscountType.FIXED_AMOUNT, "50");
        assertThat(promo.discountFor(new BigDecimal("30.00"))).isEqualByComparingTo("30.00");
        assertThat(promo.discountFor(new BigDecimal("80.00"))).isEqualByComparingTo("50.00");
    }

    @Test
    void validityWindowAndEventRestriction() {
        Instant now = Instant.parse("2026-11-20T20:00:00Z");
        PromoCode promo = promo(DiscountType.PERCENTAGE, "10");
        promo.setValidFrom(now.minusSeconds(60));
        promo.setValidUntil(now.plusSeconds(60));
        assertThat(promo.isValidAt(now)).isTrue();
        assertThat(promo.isValidAt(now.plusSeconds(61))).isFalse();
        promo.setActive(false);
        assertThat(promo.isValidAt(now)).isFalse();

        UUID eventId = UUID.randomUUID();
        promo.setEventId(eventId);
        assertThat(promo.appliesToEvent(eventId)).isTrue();
        assertThat(promo.appliesToEvent(UUID.randomUUID())).isFalse();
    }

    @Test
    void commissionByPercentageOrPerTicket() {
        Promoter promoter = new Promoter();
        promoter.setCommissionType(CommissionType.PERCENTAGE);
        promoter.setCommissionValue(new BigDecimal("12.5"));
        assertThat(promoter.commissionFor(new BigDecimal("40.00"), 2)).isEqualByComparingTo("5.00");

        promoter.setCommissionType(CommissionType.FIXED_PER_TICKET);
        promoter.setCommissionValue(new BigDecimal("1.50"));
        assertThat(promoter.commissionFor(BigDecimal.ZERO, 3)).isEqualByComparingTo("4.50");
    }

    private static PromoCode promo(DiscountType type, String value) {
        PromoCode promo = new PromoCode();
        promo.setDiscountType(type);
        promo.setDiscountValue(new BigDecimal(value));
        return promo;
    }
}
