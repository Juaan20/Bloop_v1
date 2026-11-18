package com.bloop.analytics.service;

import com.bloop.analytics.service.AttendanceForecaster.Comparable;
import com.bloop.analytics.service.AttendanceForecaster.Confidence;
import com.bloop.analytics.service.AttendanceForecaster.Method;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AttendanceForecasterTest {

    @Test
    void projectsCurrentSalesWithThePaceOfComparableNights() {
        // En noches comparables, a esta antelacion se habia vendido la mitad del total, y fue el 80 %.
        var comps = List.of(new Comparable(400, 320, 200), new Comparable(500, 400, 250), new Comparable(300, 240, 150));
        var r = AttendanceForecaster.forecast(210, 0, 800, true, comps);
        assertThat(r.method()).isEqualTo(Method.PACE);
        assertThat(r.confidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(r.soldMid()).isEqualTo(420);
        assertThat(r.attendanceMid()).isEqualTo(336);
        assertThat(r.attendanceRate()).isCloseTo(0.8, within(0.001));
    }

    @Test
    void neverExceedsCapacityNorGoesBelowCurrentSales() {
        var comps = List.of(new Comparable(500, 450, 50));
        var r = AttendanceForecaster.forecast(300, 0, 600, true, comps);
        assertThat(r.soldHigh()).isEqualTo(600);
        assertThat(r.soldLow()).isGreaterThanOrEqualTo(300);
        assertThat(r.confidence()).isEqualTo(Confidence.LOW);
    }

    @Test
    void usesHistoricalFinalsWhenThereAreNoSalesYet() {
        var comps = List.of(new Comparable(400, 300, 0), new Comparable(600, 480, 10));
        var r = AttendanceForecaster.forecast(0, 0, 800, true, comps);
        assertThat(r.method()).isEqualTo(Method.HISTORICAL);
        assertThat(r.soldMid()).isEqualTo(500);
    }

    @Test
    void doesNotInventAnEstimateWithoutHistory() {
        var r = AttendanceForecaster.forecast(120, 0, 800, true, List.of());
        assertThat(r.method()).isEqualTo(Method.NONE);
        assertThat(r.confidence()).isEqualTo(Confidence.NONE);
    }

    @Test
    void pastSessionsReturnTheRealAttendance() {
        var r = AttendanceForecaster.forecast(500, 410, 800, false, List.of());
        assertThat(r.method()).isEqualTo(Method.ACTUAL);
        assertThat(r.attendanceMid()).isEqualTo(410);
    }

    @Test
    void percentileInterpolates() {
        assertThat(AttendanceForecaster.percentile(List.of(1.0, 2.0, 3.0, 4.0), 50)).isEqualTo(2.5);
        assertThat(AttendanceForecaster.percentile(List.of(10.0), 25)).isEqualTo(10.0);
    }
}
