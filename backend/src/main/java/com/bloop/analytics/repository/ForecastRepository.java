package com.bloop.analytics.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultas de la prevision de asistencia. Siempre filtradas por empresa.
 * "Vendidas" = entradas VALID o USED (las canceladas/reembolsadas no cuentan); "asistencia" = USED.
 */
@Repository
@RequiredArgsConstructor
public class ForecastRepository {

    private static final String SOLD = "(SELECT COUNT(*) FROM tickets t WHERE t.session_id = s.id AND t.status IN ('VALID', 'USED'))";
    private static final String USED = "(SELECT COUNT(*) FROM tickets t WHERE t.session_id = s.id AND t.status = 'USED')";

    private final JdbcClient jdbc;

    public record SessionRow(UUID sessionId, UUID eventId, String eventName, UUID venueId, String venueName,
                             Instant startsAt, int capacity, long sold, long checkedIn) {
    }

    public Optional<SessionRow> session(UUID tenantId, UUID sessionId) {
        return jdbc.sql("""
                SELECT s.id, e.id AS event_id, e.name AS event_name, v.id AS venue_id, v.name AS venue_name,
                       s.starts_at, s.capacity, %s AS sold, %s AS used
                FROM event_sessions s JOIN events e ON e.id = s.event_id JOIN venues v ON v.id = e.venue_id
                WHERE s.id = :id AND s.tenant_id = :tenantId
                """.formatted(SOLD, USED))
                .param("id", sessionId).param("tenantId", tenantId)
                .query((rs, i) -> new SessionRow(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
                        rs.getString("event_name"), rs.getObject("venue_id", UUID.class), rs.getString("venue_name"),
                        rs.getTimestamp("starts_at").toInstant(), rs.getInt("capacity"), rs.getLong("sold"),
                        rs.getLong("used")))
                .optional();
    }

    /** Proximas sesiones (selector de la pantalla de prevision). */
    public List<SessionRow> upcoming(UUID tenantId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT s.id, e.id AS event_id, e.name AS event_name, v.id AS venue_id, v.name AS venue_name,
                       s.starts_at, s.capacity, %s AS sold, %s AS used
                FROM event_sessions s JOIN events e ON e.id = s.event_id JOIN venues v ON v.id = e.venue_id
                WHERE s.tenant_id = :tenantId AND s.status = 'SCHEDULED' AND e.status = 'PUBLISHED'
                  AND s.starts_at >= :from AND s.starts_at < :to
                ORDER BY s.starts_at
                LIMIT 30
                """.formatted(SOLD, USED))
                .param("tenantId", tenantId).param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
                .query((rs, i) -> new SessionRow(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
                        rs.getString("event_name"), rs.getObject("venue_id", UUID.class), rs.getString("venue_name"),
                        rs.getTimestamp("starts_at").toInstant(), rs.getInt("capacity"), rs.getLong("sold"),
                        rs.getLong("used")))
                .list();
    }

    public record ComparableRow(UUID sessionId, UUID eventId, String eventName, Instant startsAt, int capacity,
                                long sold, long checkedIn, long soldAtLead, long vipReservations, BigDecimal revenue) {
    }

    /**
     * Noches comparables: mismo local, mismo dia de la semana (hora de Espana), ya celebradas, en el ultimo
     * ano y con ventas. soldAtLead = vendidas "leadSeconds" antes de empezar (misma antelacion que hoy).
     */
    public List<ComparableRow> comparables(UUID tenantId, UUID venueId, UUID excludeSessionId, int isoDayOfWeek,
                                           Instant since, Instant before, long leadSeconds) {
        return jdbc.sql("""
                SELECT s.id, e.id AS event_id, e.name AS event_name, s.starts_at, s.capacity,
                       %s AS sold, %s AS used,
                       (SELECT COUNT(*) FROM tickets t JOIN ticket_orders o ON o.id = t.order_id
                         WHERE t.session_id = s.id AND t.status IN ('VALID', 'USED')
                           AND o.paid_at <= s.starts_at - make_interval(secs => :lead)) AS sold_at_lead,
                       (SELECT COUNT(*) FROM reservations r WHERE r.session_id = s.id
                         AND r.status IN ('CONFIRMED', 'SEATED', 'COMPLETED')) AS vip,
                       (SELECT COALESCE(SUM(o.total_amount), 0) FROM ticket_orders o
                         WHERE o.session_id = s.id AND o.status = 'PAID') AS revenue
                FROM event_sessions s JOIN events e ON e.id = s.event_id
                WHERE s.tenant_id = :tenantId AND e.venue_id = :venueId AND s.id <> :exclude
                  AND s.status = 'SCHEDULED' AND s.starts_at >= :since AND s.starts_at < :before
                  AND EXTRACT(ISODOW FROM s.starts_at AT TIME ZONE 'Europe/Madrid') = :dow
                  AND %s > 0
                ORDER BY s.starts_at DESC
                LIMIT 16
                """.formatted(SOLD, USED, SOLD))
                .param("tenantId", tenantId).param("venueId", venueId).param("exclude", excludeSessionId)
                .param("since", Timestamp.from(since)).param("before", Timestamp.from(before))
                .param("dow", isoDayOfWeek).param("lead", (double) leadSeconds)
                .query((rs, i) -> new ComparableRow(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
                        rs.getString("event_name"), rs.getTimestamp("starts_at").toInstant(), rs.getInt("capacity"),
                        rs.getLong("sold"), rs.getLong("used"), rs.getLong("sold_at_lead"), rs.getLong("vip"),
                        rs.getBigDecimal("revenue")))
                .list();
    }

    public record CurvePoint(UUID sessionId, int daysBefore, long tickets) {
    }

    /** Entradas vendidas por sesion y dias antes del evento (para la curva de ventas acumuladas). */
    public List<CurvePoint> salesByDaysBefore(Collection<UUID> sessionIds) {
        if (sessionIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT s.id, GREATEST(0, FLOOR(EXTRACT(EPOCH FROM (s.starts_at - o.paid_at)) / 86400))::int AS days_before,
                       COUNT(t.id) AS n
                FROM tickets t JOIN ticket_orders o ON o.id = t.order_id JOIN event_sessions s ON s.id = t.session_id
                WHERE t.session_id IN (:ids) AND t.status IN ('VALID', 'USED') AND o.paid_at IS NOT NULL
                GROUP BY s.id, days_before
                """)
                .param("ids", sessionIds)
                .query((rs, i) -> new CurvePoint(rs.getObject("id", UUID.class), rs.getInt("days_before"), rs.getLong("n")))
                .list();
    }
}
