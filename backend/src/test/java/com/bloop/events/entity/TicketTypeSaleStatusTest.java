package com.bloop.events.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas de "esta a la venta" de un tipo de entrada. */
class TicketTypeSaleStatusTest {

    private final Instant now = Instant.parse("2026-11-20T20:00:00Z");

    @Test
    void onSaleWhenActiveWithinWindowAndWithStock() {
        assertThat(ticketType(10, 0).saleStatus(now)).isEqualTo(SaleStatus.ON_SALE);
    }

    @Test
    void soldOutWhenNoStockLeft() {
        assertThat(ticketType(10, 10).saleStatus(now)).isEqualTo(SaleStatus.SOLD_OUT);
    }

    @Test
    void respectsSalesWindowAndSessionEnd() {
        TicketType notYet = ticketType(10, 0);
        notYet.setSalesStartAt(now.plus(1, ChronoUnit.HOURS));
        assertThat(notYet.saleStatus(now)).isEqualTo(SaleStatus.NOT_STARTED);

        TicketType ended = ticketType(10, 0);
        ended.setSalesEndAt(now.minus(1, ChronoUnit.MINUTES));
        assertThat(ended.saleStatus(now)).isEqualTo(SaleStatus.ENDED);

        // Sin fecha de fin explicita, la venta termina cuando acaba la sesion.
        assertThat(ticketType(10, 0).saleStatus(now.plus(2, ChronoUnit.DAYS))).isEqualTo(SaleStatus.ENDED);
    }

    @Test
    void inactiveTypeIsNeverOnSale() {
        TicketType inactive = ticketType(10, 0);
        inactive.setActive(false);
        assertThat(inactive.saleStatus(now)).isEqualTo(SaleStatus.INACTIVE);
    }

    private TicketType ticketType(int total, int sold) {
        EventSession session = new EventSession();
        session.setStartsAt(now.plus(3, ChronoUnit.HOURS));
        session.setEndsAt(now.plus(9, ChronoUnit.HOURS));
        TicketType type = new TicketType();
        type.setSession(session);
        type.setPrice(new BigDecimal("10.00"));
        type.setQuantityTotal(total);
        type.setQuantitySold(sold);
        return type;
    }
}
