package com.bloop.dashboard.repository;

import com.bloop.dashboard.dto.response.DashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consultas de agregacion para el dashboard. Usamos SQL directo (JdbcClient) porque son
 * sumas y agrupaciones que se leen mejor en SQL que en JPQL, y no necesitan entidades.
 * TODAS las consultas filtran por tenant_id.
 * El filtro de local es opcional: (:venueId IS NULL OR v.id = :venueId).
 */
@Repository
@RequiredArgsConstructor
public class DashboardRepository {

    private final JdbcClient jdbc;

    public record RevenueTotals(BigDecimal revenue, long paidOrders) {
    }

    public record TicketTotals(long sold, long checkedIn) {
    }

    public RevenueTotals revenueAndOrders(UUID tenantId, UUID venueId, Instant from) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(o.total_amount), 0) AS revenue, COUNT(*) AS orders
                        FROM ticket_orders o
                        JOIN event_sessions s ON s.id = o.session_id
                        JOIN events e ON e.id = s.event_id
                        WHERE o.tenant_id = :tenantId AND o.status = 'PAID' AND o.paid_at >= :from
                          AND (CAST(:venueId AS uuid) IS NULL OR e.venue_id = :venueId)
                        """)
                .param("tenantId", tenantId).param("venueId", venueId).param("from", Timestamp.from(from))
                .query((rs, i) -> new RevenueTotals(rs.getBigDecimal("revenue"), rs.getLong("orders")))
                .single();
    }

    public TicketTotals ticketsAndCheckIns(UUID tenantId, UUID venueId, Instant from) {
        return jdbc.sql("""
                        SELECT COUNT(*) FILTER (WHERE t.status IN ('VALID', 'USED')) AS sold,
                               COUNT(*) FILTER (WHERE t.status = 'USED') AS used
                        FROM tickets t
                        JOIN event_sessions s ON s.id = t.session_id
                        JOIN events e ON e.id = s.event_id
                        WHERE t.tenant_id = :tenantId AND t.created_at >= :from
                          AND (CAST(:venueId AS uuid) IS NULL OR e.venue_id = :venueId)
                        """)
                .param("tenantId", tenantId).param("venueId", venueId).param("from", Timestamp.from(from))
                .query((rs, i) -> new TicketTotals(rs.getLong("sold"), rs.getLong("used")))
                .single();
    }

    public long upcomingSessions(UUID tenantId, UUID venueId, Instant now) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM event_sessions s JOIN events e ON e.id = s.event_id
                        WHERE s.tenant_id = :tenantId AND s.status = 'SCHEDULED' AND s.ends_at > :now
                          AND e.status = 'PUBLISHED'
                          AND (CAST(:venueId AS uuid) IS NULL OR e.venue_id = :venueId)
                        """)
                .param("tenantId", tenantId).param("venueId", venueId).param("now", Timestamp.from(now))
                .query(Long.class).single();
    }

    public long publishedEvents(UUID tenantId, UUID venueId) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM events e
                        WHERE e.tenant_id = :tenantId AND e.status = 'PUBLISHED'
                          AND (CAST(:venueId AS uuid) IS NULL OR e.venue_id = :venueId)
                        """)
                .param("tenantId", tenantId).param("venueId", venueId)
                .query(Long.class).single();
    }

    /** Ventas por dia (zona horaria de Espana) de los pedidos pagados. */
    public List<DashboardResponse.DailySales> salesByDay(UUID tenantId, UUID venueId, Instant from) {
        return jdbc.sql("""
                        SELECT CAST(o.paid_at AT TIME ZONE 'Europe/Madrid' AS date) AS day,
                               SUM(o.total_amount) AS revenue,
                               SUM((SELECT COALESCE(SUM(i.quantity), 0) FROM ticket_order_items i WHERE i.order_id = o.id)) AS tickets
                        FROM ticket_orders o
                        JOIN event_sessions s ON s.id = o.session_id
                        JOIN events e ON e.id = s.event_id
                        WHERE o.tenant_id = :tenantId AND o.status = 'PAID' AND o.paid_at >= :from
                          AND (CAST(:venueId AS uuid) IS NULL OR e.venue_id = :venueId)
                        GROUP BY 1 ORDER BY 1
                        """)
                .param("tenantId", tenantId).param("venueId", venueId).param("from", Timestamp.from(from))
                .query((rs, i) -> new DashboardResponse.DailySales(rs.getDate("day").toLocalDate(),
                        rs.getBigDecimal("revenue"), rs.getLong("tickets")))
                .list();
    }

    /** Rendimiento de las sesiones recientes y proximas: ocupacion, asistencia e ingresos. */
    public List<DashboardResponse.SessionPerformance> sessionPerformance(UUID tenantId, UUID venueId, Instant from,
                                                                         int limit) {
        return jdbc.sql("""
                        SELECT s.id AS session_id, e.id AS event_id, e.name AS event_name, v.name AS venue_name,
                               s.starts_at, s.capacity,
                               (SELECT COUNT(*) FROM tickets t WHERE t.session_id = s.id AND t.status IN ('VALID','USED')) AS sold,
                               (SELECT COUNT(*) FROM tickets t WHERE t.session_id = s.id AND t.status = 'USED') AS checked_in,
                               (SELECT COALESCE(SUM(o.total_amount), 0) FROM ticket_orders o
                                 WHERE o.session_id = s.id AND o.status = 'PAID') AS revenue
                        FROM event_sessions s
                        JOIN events e ON e.id = s.event_id
                        JOIN venues v ON v.id = e.venue_id
                        WHERE s.tenant_id = :tenantId AND s.ends_at >= :from AND e.status <> 'CANCELLED'
                          AND (CAST(:venueId AS uuid) IS NULL OR v.id = :venueId)
                        ORDER BY s.starts_at
                        LIMIT :limit
                        """)
                .param("tenantId", tenantId).param("venueId", venueId).param("from", Timestamp.from(from))
                .param("limit", limit)
                .query((rs, i) -> new DashboardResponse.SessionPerformance(
                        rs.getObject("session_id", UUID.class), rs.getObject("event_id", UUID.class),
                        rs.getString("event_name"), rs.getString("venue_name"),
                        rs.getTimestamp("starts_at").toInstant(), rs.getInt("capacity"), rs.getLong("sold"),
                        rs.getLong("checked_in"), rs.getBigDecimal("revenue")))
                .list();
    }

    public List<DashboardResponse.VenueSales> salesByVenue(UUID tenantId, Instant from) {
        return jdbc.sql("""
                        SELECT v.id AS venue_id, v.name AS venue_name,
                               COALESCE(SUM(o.total_amount), 0) AS revenue,
                               COALESCE(SUM((SELECT SUM(i.quantity) FROM ticket_order_items i WHERE i.order_id = o.id)), 0) AS tickets
                        FROM venues v
                        LEFT JOIN events e ON e.venue_id = v.id
                        LEFT JOIN event_sessions s ON s.event_id = e.id
                        LEFT JOIN ticket_orders o ON o.session_id = s.id AND o.status = 'PAID' AND o.paid_at >= :from
                        WHERE v.tenant_id = :tenantId
                        GROUP BY v.id, v.name
                        ORDER BY revenue DESC
                        """)
                .param("tenantId", tenantId).param("from", Timestamp.from(from))
                .query((rs, i) -> new DashboardResponse.VenueSales(rs.getObject("venue_id", UUID.class),
                        rs.getString("venue_name"), rs.getBigDecimal("revenue"), rs.getLong("tickets")))
                .list();
    }
}
