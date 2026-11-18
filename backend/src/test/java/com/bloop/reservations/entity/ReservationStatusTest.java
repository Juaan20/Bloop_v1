package com.bloop.reservations.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Transiciones de estado permitidas de una reserva VIP. */
class ReservationStatusTest {

    @Test
    void allowedTransitions() {
        assertThat(ReservationStatus.PENDING.canChangeTo(ReservationStatus.CONFIRMED)).isTrue();
        assertThat(ReservationStatus.PENDING.canChangeTo(ReservationStatus.CANCELLED)).isTrue();
        assertThat(ReservationStatus.CONFIRMED.canChangeTo(ReservationStatus.SEATED)).isTrue();
        assertThat(ReservationStatus.CONFIRMED.canChangeTo(ReservationStatus.NO_SHOW)).isTrue();
        assertThat(ReservationStatus.SEATED.canChangeTo(ReservationStatus.COMPLETED)).isTrue();
    }

    @Test
    void forbiddenTransitions() {
        assertThat(ReservationStatus.PENDING.canChangeTo(ReservationStatus.SEATED)).isFalse();
        assertThat(ReservationStatus.SEATED.canChangeTo(ReservationStatus.CANCELLED)).isFalse();
        assertThat(ReservationStatus.CANCELLED.canChangeTo(ReservationStatus.CONFIRMED)).isFalse();
        assertThat(ReservationStatus.COMPLETED.canChangeTo(ReservationStatus.CANCELLED)).isFalse();
    }
}
