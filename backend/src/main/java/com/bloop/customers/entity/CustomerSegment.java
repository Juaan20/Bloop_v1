package com.bloop.customers.entity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Segmentacion basica del CRM. Un cliente puede estar en varios segmentos a la vez
 * (p. ej. RECURRING y VIP). Las mismas reglas se usan en CustomerRepository.search.
 */
public enum CustomerSegment {
    /** Como mucho una compra. */
    NEW,
    /** Dos compras o mas. */
    RECURRING,
    /** Ha gastado VIP_MIN_SPENT o mas. */
    VIP,
    /** Su ultima compra fue hace mas de INACTIVE_AFTER. */
    INACTIVE;

    public static final BigDecimal VIP_MIN_SPENT = new BigDecimal("200.00");
    public static final Duration INACTIVE_AFTER = Duration.ofDays(90);

    public static List<CustomerSegment> of(Customer customer, Instant now) {
        List<CustomerSegment> segments = new ArrayList<>();
        segments.add(customer.getOrdersCount() <= 1 ? NEW : RECURRING);
        if (customer.getTotalSpent().compareTo(VIP_MIN_SPENT) >= 0) {
            segments.add(VIP);
        }
        if (customer.getLastPurchaseAt() != null && customer.getLastPurchaseAt().isBefore(now.minus(INACTIVE_AFTER))) {
            segments.add(INACTIVE);
        }
        return segments;
    }
}
