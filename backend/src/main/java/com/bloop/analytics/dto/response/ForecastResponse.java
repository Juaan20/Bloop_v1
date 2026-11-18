package com.bloop.analytics.dto.response;

import com.bloop.analytics.service.AttendanceForecaster;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Prevision de asistencia de una sesion, con los datos reales en los que se basa.
 * Si no hay historico suficiente, forecast.method = NONE y no se da ninguna cifra inventada.
 */
public record ForecastResponse(Session session, Forecast forecast, List<ComparableNight> comparables,
                               List<CurvePoint> salesCurve) {

    public record Session(UUID sessionId, String eventName, String venueName, Instant startsAt, String weekday,
                          int capacity, long ticketsSold, long checkedIn, boolean future, long daysToGo) {
    }

    public record Forecast(AttendanceForecaster.Method method, AttendanceForecaster.Confidence confidence,
                           int dataPoints, long soldLow, long soldMid, long soldHigh, long attendanceLow,
                           long attendanceMid, long attendanceHigh, double attendanceRate, String explanation) {
    }

    /** Noche comparable (mismo local y dia de la semana). sameEvent = edicion anterior del mismo evento. */
    public record ComparableNight(UUID sessionId, String eventName, Instant startsAt, boolean sameEvent, int capacity,
                                  long ticketsSold, long checkedIn, double attendanceRate, long soldAtSameLead,
                                  long vipReservations, BigDecimal revenue) {
    }

    /** Ventas acumuladas N dias antes: de esta sesion (si ya existen) y media de las comparables. */
    public record CurvePoint(int daysBefore, Long current, Long comparableAverage) {
    }

    /** Sesion del selector. */
    public record UpcomingSession(UUID sessionId, String eventName, String venueName, Instant startsAt,
                                  int capacity, long ticketsSold) {
    }
}
