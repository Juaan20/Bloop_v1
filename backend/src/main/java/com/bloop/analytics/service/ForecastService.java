package com.bloop.analytics.service;

import com.bloop.analytics.dto.response.ForecastResponse;
import com.bloop.analytics.repository.ForecastRepository;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Prevision de asistencia y comparativa con noches similares (ver AttendanceForecaster). */
@Service
@RequiredArgsConstructor
public class ForecastService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final String[] WEEKDAYS = {"lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo"};
    /** Dias antes del evento que se pintan en la curva de ventas. */
    private static final int CURVE_DAYS = 21;

    private final ForecastRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ForecastResponse.UpcomingSession> upcoming() {
        Instant now = Instant.now(clock);
        return repository.upcoming(currentUserProvider.requireTenantId(), now.minus(12, ChronoUnit.HOURS),
                        now.plus(60, ChronoUnit.DAYS)).stream()
                .map(s -> new ForecastResponse.UpcomingSession(s.sessionId(), s.eventName(), s.venueName(),
                        s.startsAt(), s.capacity(), s.sold()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ForecastResponse forecast(UUID sessionId) {
        UUID tenantId = currentUserProvider.requireTenantId();
        var s = repository.session(tenantId, sessionId).orElseThrow(() -> NotFoundException.of("Sesion"));
        Instant now = Instant.now(clock);
        boolean future = s.startsAt().isAfter(now);
        long leadSeconds = future ? Duration.between(now, s.startsAt()).getSeconds() : 0;
        int dow = s.startsAt().atZone(ZONE).getDayOfWeek().getValue();

        var rows = repository.comparables(tenantId, s.venueId(), s.sessionId(), dow,
                s.startsAt().minus(365, ChronoUnit.DAYS), now.isBefore(s.startsAt()) ? now : s.startsAt(), leadSeconds);
        var result = AttendanceForecaster.forecast(s.sold(), s.checkedIn(), s.capacity(), future,
                rows.stream().map(r -> new AttendanceForecaster.Comparable(r.sold(), r.checkedIn(), r.soldAtLead()))
                        .toList());

        long daysToGo = future ? Duration.between(now, s.startsAt()).toDays() : 0;
        String weekday = WEEKDAYS[dow - 1];
        String weekdays = plural(weekday);
        var forecast = new ForecastResponse.Forecast(result.method(), result.confidence(), result.dataPoints(),
                result.soldLow(), result.soldMid(), result.soldHigh(), result.attendanceLow(), result.attendanceMid(),
                result.attendanceHigh(), result.attendanceRate(), explanation(result, s.sold(), daysToGo, weekday,
                weekdays, rows.size()));
        var comparables = rows.stream()
                .map(r -> new ForecastResponse.ComparableNight(r.sessionId(), r.eventName(), r.startsAt(),
                        r.eventId().equals(s.eventId()), r.capacity(), r.sold(), r.checkedIn(),
                        r.sold() > 0 ? (double) r.checkedIn() / r.sold() : 0, r.soldAtLead(), r.vipReservations(),
                        r.revenue()))
                .toList();
        var session = new ForecastResponse.Session(s.sessionId(), s.eventName(), s.venueName(), s.startsAt(), weekday,
                s.capacity(), s.sold(), s.checkedIn(), future, daysToGo);
        return new ForecastResponse(session, forecast, comparables, curve(s.sessionId(), future, daysToGo,
                rows.stream().map(ForecastRepository.ComparableRow::sessionId).toList()));
    }

    /** Ventas acumuladas desde D-21 hasta el dia del evento: esta sesion frente a la media de las comparables. */
    private List<ForecastResponse.CurvePoint> curve(UUID sessionId, boolean future, long daysToGo, List<UUID> comparableIds) {
        List<UUID> ids = Stream.concat(Stream.of(sessionId), comparableIds.stream()).toList();
        Map<UUID, Map<Integer, Long>> bySession = new HashMap<>();
        for (var p : repository.salesByDaysBefore(ids)) {
            bySession.computeIfAbsent(p.sessionId(), k -> new HashMap<>()).merge(p.daysBefore(), p.tickets(), Long::sum);
        }
        List<ForecastResponse.CurvePoint> points = new ArrayList<>();
        for (int d = CURVE_DAYS; d >= 0; d--) {
            final int day = d;
            Long current = !future || d >= daysToGo ? cumulative(bySession.get(sessionId), day) : null;
            Long average = comparableIds.isEmpty() ? null : Math.round(comparableIds.stream()
                    .mapToLong(id -> cumulative(bySession.get(id), day)).average().orElse(0));
            points.add(new ForecastResponse.CurvePoint(d, current, average));
        }
        return points;
    }

    /** Entradas vendidas hasta "day" dias antes del evento (las vendidas con mas antelacion incluidas). */
    private static long cumulative(Map<Integer, Long> byDay, int day) {
        if (byDay == null) {
            return 0;
        }
        return byDay.entrySet().stream().filter(e -> e.getKey() >= day).mapToLong(Map.Entry::getValue).sum();
    }

    /** "viernes" -> "viernes", "sabado" -> "sabados". */
    static String plural(String weekday) {
        return weekday.endsWith("s") ? weekday : weekday + "s";
    }

    private static String explanation(AttendanceForecaster.Result r, long sold, long daysToGo, String weekday,
                                      String weekdays, int comparables) {
        return switch (r.method()) {
            case ACTUAL -> "Sesion ya celebrada: asistencia real registrada en la puerta.";
            case NONE -> "No hay noches comparables (mismo local, en " + weekday
                    + ", en el ultimo ano) con ventas: no se puede estimar la asistencia todavia.";
            case PACE -> "Segun el ritmo de venta actual (" + sold + " entradas a " + daysToGo + " dias) y "
                    + r.dataPoints() + " " + weekdays + " comparables, se estima una asistencia de entre "
                    + r.attendanceLow() + " y " + r.attendanceHigh() + " personas.";
            case HISTORICAL -> "Aun no hay ventas que comparar: estimacion basada en las ventas finales y la asistencia de "
                    + comparables + " " + weekdays + " anteriores (entre " + r.attendanceLow() + " y "
                    + r.attendanceHigh() + " personas).";
        };
    }
}
