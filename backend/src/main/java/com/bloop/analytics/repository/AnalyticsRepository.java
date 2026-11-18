package com.bloop.analytics.repository;

import com.bloop.analytics.dto.response.AnalyticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Consultas SQL de la analitica avanzada y de los informes CSV.
 *
 * Reglas comunes de todas las consultas:
 * - Siempre filtradas por tenant_id (aislamiento entre empresas).
 * - Periodo [from, to) en UTC; el servicio convierte las fechas de Espana a instantes.
 * - Local opcional: (CAST(:venueId AS uuid) IS NULL OR ... = :venueId).
 * Los dias y horas se agrupan en hora de Espana (AT TIME ZONE 'Europe/Madrid').
 */
@Repository
@RequiredArgsConstructor
public class AnalyticsRepository {

    private final JdbcClient jdbc;

    /** Filtro comun de un periodo. */
    public record Period(UUID tenantId, UUID venueId, Instant from, Instant to) {
    }

    // ------------------------------------------------------------------ Resumen

    public record TicketTotals(BigDecimal revenue, long orders, long tickets, BigDecimal commissions) {
    }

    public TicketTotals ticketTotals(Period p) {
        return query("""
                SELECT COALESCE(SUM(o.total_amount), 0) AS revenue, COUNT(*) AS orders,
                       COALESCE(SUM((SELECT SUM(i.quantity) FROM ticket_order_items i WHERE i.order_id = o.id)), 0) AS tickets,
                       COALESCE(SUM(o.commission_amount), 0) AS commissions
                FROM ticket_orders o
                JOIN event_sessions s ON s.id = o.session_id
                JOIN events e ON e.id = s.event_id
                WHERE o.tenant_id = :tenantId AND o.status = 'PAID' AND o.paid_at >= :from AND o.paid_at < :to
                  AND (CAST(:venueId AS uuid) IS NULL OR e.venue_id = :venueId)
                """, p)
                .query((rs, i) -> new TicketTotals(rs.getBigDecimal("revenue"), rs.getLong("orders"),
                        rs.getLong("tickets"), rs.getBigDecimal("commissions")))
                .single();
    }

    public record RefundTotals(BigDecimal amount, long orders) {
    }

    public RefundTotals refundTotals(Period p) {
        return query("""
                SELECT COALESCE(SUM(o.total_amount), 0) AS amount, COUNT(*) AS orders
                FROM ticket_orders o
                JOIN event_sessions s ON s.id = o.session_id
                JOIN events e ON e.id = s.event_id
                WHERE o.tenant_id = :tenantId AND o.status = 'REFUNDED' AND o.refunded_at >= :from AND o.refunded_at < :to
                  AND (CAST(:venueId AS uuid) IS NULL OR e.venue_id = :venueId)
                """, p)
                .query((rs, i) -> new RefundTotals(rs.getBigDecimal("amount"), rs.getLong("orders")))
                .single();
    }

    public record PosTotals(BigDecimal revenue, long sales, long voided) {
    }

    public PosTotals posTotals(Period p) {
        return query("""
                SELECT COALESCE(SUM(ps.total_amount) FILTER (WHERE ps.status = 'COMPLETED'), 0) AS revenue,
                       COUNT(*) FILTER (WHERE ps.status = 'COMPLETED') AS sales,
                       COUNT(*) FILTER (WHERE ps.status = 'VOIDED') AS voided
                FROM pos_sales ps
                JOIN pos_terminals t ON t.id = ps.terminal_id
                WHERE ps.tenant_id = :tenantId AND ps.created_at >= :from AND ps.created_at < :to
                  AND (CAST(:venueId AS uuid) IS NULL OR t.venue_id = :venueId)
                """, p)
                .query((rs, i) -> new PosTotals(rs.getBigDecimal("revenue"), rs.getLong("sales"), rs.getLong("voided")))
                .single();
    }

    // ------------------------------------------------------------------ Series y rankings

    public List<AnalyticsResponse.DailyRevenue> revenueByDay(Period p) {
        return query("""
                WITH tickets AS (
                    SELECT CAST(o.paid_at AT TIME ZONE 'Europe/Madrid' AS date) AS day, SUM(o.total_amount) AS amount
                    FROM ticket_orders o
                    JOIN event_sessions s ON s.id = o.session_id
                    JOIN events e ON e.id = s.event_id
                    WHERE o.tenant_id = :tenantId AND o.status = 'PAID' AND o.paid_at >= :from AND o.paid_at < :to
                      AND (CAST(:venueId AS uuid) IS NULL OR e.venue_id = :venueId)
                    GROUP BY 1
                ), pos AS (
                    SELECT CAST(ps.created_at AT TIME ZONE 'Europe/Madrid' AS date) AS day, SUM(ps.total_amount) AS amount
                    FROM pos_sales ps
                    JOIN pos_terminals t ON t.id = ps.terminal_id
                    WHERE ps.tenant_id = :tenantId AND ps.status = 'COMPLETED' AND ps.created_at >= :from AND ps.created_at < :to
                      AND (CAST(:venueId AS uuid) IS NULL OR t.venue_id = :venueId)
                    GROUP BY 1
                )
                SELECT COALESCE(tickets.day, pos.day) AS day,
                       COALESCE(tickets.amount, 0) AS ticket_revenue, COALESCE(pos.amount, 0) AS pos_revenue
                FROM tickets FULL OUTER JOIN pos ON pos.day = tickets.day
                ORDER BY 1
                """, p)
                .query((rs, i) -> new AnalyticsResponse.DailyRevenue(rs.getDate("day").toLocalDate(),
                        rs.getBigDecimal("ticket_revenue"), rs.getBigDecimal("pos_revenue")))
                .list();
    }

    public List<AnalyticsResponse.TopEvent> topEvents(Period p, int limit) {
        return query("""
                SELECT e.id AS event_id, e.name AS event_name, v.name AS venue_name,
                       COUNT(t.id) FILTER (WHERE t.status IN ('VALID', 'USED')) AS sold,
                       COUNT(t.id) FILTER (WHERE t.status = 'USED') AS checked_in,
                       (SELECT COALESCE(SUM(o2.total_amount), 0) FROM ticket_orders o2
                          JOIN event_sessions s2 ON s2.id = o2.session_id
                         WHERE s2.event_id = e.id AND o2.status = 'PAID' AND o2.paid_at >= :from AND o2.paid_at < :to) AS revenue
                FROM events e
                JOIN venues v ON v.id = e.venue_id
                JOIN event_sessions s ON s.event_id = e.id
                JOIN tickets t ON t.session_id = s.id
                JOIN ticket_orders o ON o.id = t.order_id AND o.paid_at >= :from AND o.paid_at < :to
                WHERE e.tenant_id = :tenantId
                  AND (CAST(:venueId AS uuid) IS NULL OR v.id = :venueId)
                GROUP BY e.id, e.name, v.name
                HAVING COUNT(t.id) FILTER (WHERE t.status IN ('VALID', 'USED')) > 0
                ORDER BY revenue DESC, sold DESC
                LIMIT :limit
                """, p).param("limit", limit)
                .query((rs, i) -> new AnalyticsResponse.TopEvent(rs.getObject("event_id", UUID.class),
                        rs.getString("event_name"), rs.getString("venue_name"), rs.getLong("sold"),
                        rs.getLong("checked_in"), rs.getBigDecimal("revenue")))
                .list();
    }

    public List<AnalyticsResponse.TopProduct> topProducts(Period p, int limit) {
        return query("""
                SELECT i.product_name, i.variant_name, SUM(i.quantity) AS quantity, SUM(i.line_total) AS revenue
                FROM pos_sale_items i
                JOIN pos_sales ps ON ps.id = i.sale_id
                JOIN pos_terminals t ON t.id = ps.terminal_id
                WHERE ps.tenant_id = :tenantId AND ps.status = 'COMPLETED' AND ps.created_at >= :from AND ps.created_at < :to
                  AND (CAST(:venueId AS uuid) IS NULL OR t.venue_id = :venueId)
                GROUP BY i.product_name, i.variant_name
                ORDER BY quantity DESC, revenue DESC
                LIMIT :limit
                """, p).param("limit", limit)
                .query((rs, i) -> new AnalyticsResponse.TopProduct(rs.getString("product_name"),
                        rs.getString("variant_name"), rs.getLong("quantity"), rs.getBigDecimal("revenue")))
                .list();
    }

    public List<AnalyticsResponse.TerminalSales> posByTerminal(Period p) {
        return query("""
                SELECT t.id, t.name, t.type, v.name AS venue_name, COUNT(ps.id) AS sales,
                       COALESCE(SUM(ps.total_amount), 0) AS revenue
                FROM pos_terminals t
                JOIN venues v ON v.id = t.venue_id
                JOIN pos_sales ps ON ps.terminal_id = t.id AND ps.status = 'COMPLETED'
                     AND ps.created_at >= :from AND ps.created_at < :to
                WHERE t.tenant_id = :tenantId
                  AND (CAST(:venueId AS uuid) IS NULL OR t.venue_id = :venueId)
                GROUP BY t.id, t.name, t.type, v.name
                ORDER BY revenue DESC
                """, p)
                .query((rs, i) -> new AnalyticsResponse.TerminalSales(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("type"), rs.getString("venue_name"), rs.getLong("sales"), rs.getBigDecimal("revenue")))
                .list();
    }

    public List<AnalyticsResponse.PaymentMethodSales> posByPaymentMethod(Period p) {
        return query("""
                SELECT ps.payment_method, COUNT(*) AS sales, SUM(ps.total_amount) AS revenue
                FROM pos_sales ps
                JOIN pos_terminals t ON t.id = ps.terminal_id
                WHERE ps.tenant_id = :tenantId AND ps.status = 'COMPLETED' AND ps.created_at >= :from AND ps.created_at < :to
                  AND (CAST(:venueId AS uuid) IS NULL OR t.venue_id = :venueId)
                GROUP BY ps.payment_method
                ORDER BY revenue DESC
                """, p)
                .query((rs, i) -> new AnalyticsResponse.PaymentMethodSales(rs.getString("payment_method"),
                        rs.getLong("sales"), rs.getBigDecimal("revenue")))
                .list();
    }

    public List<AnalyticsResponse.HourlySales> posByHour(Period p) {
        return query("""
                SELECT CAST(EXTRACT(HOUR FROM ps.created_at AT TIME ZONE 'Europe/Madrid') AS int) AS hour,
                       COUNT(*) AS sales, SUM(ps.total_amount) AS revenue
                FROM pos_sales ps
                JOIN pos_terminals t ON t.id = ps.terminal_id
                WHERE ps.tenant_id = :tenantId AND ps.status = 'COMPLETED' AND ps.created_at >= :from AND ps.created_at < :to
                  AND (CAST(:venueId AS uuid) IS NULL OR t.venue_id = :venueId)
                GROUP BY 1
                ORDER BY 1
                """, p)
                .query((rs, i) -> new AnalyticsResponse.HourlySales(rs.getInt("hour"), rs.getLong("sales"),
                        rs.getBigDecimal("revenue")))
                .list();
    }

    public List<AnalyticsResponse.PromoterPerformance> promoters(Period p) {
        return query("""
                SELECT pr.id, u.first_name || ' ' || u.last_name AS name, pr.referral_code,
                       COUNT(o.id) AS orders,
                       COALESCE(SUM((SELECT SUM(i.quantity) FROM ticket_order_items i WHERE i.order_id = o.id)), 0) AS tickets,
                       COALESCE(SUM(o.total_amount), 0) AS revenue, COALESCE(SUM(o.commission_amount), 0) AS commission
                FROM promoters pr
                JOIN users u ON u.id = pr.user_id
                JOIN ticket_orders o ON o.promoter_id = pr.id AND o.status = 'PAID'
                     AND o.paid_at >= :from AND o.paid_at < :to
                JOIN event_sessions s ON s.id = o.session_id
                JOIN events e ON e.id = s.event_id
                WHERE pr.tenant_id = :tenantId
                  AND (CAST(:venueId AS uuid) IS NULL OR e.venue_id = :venueId)
                GROUP BY pr.id, u.first_name, u.last_name, pr.referral_code
                ORDER BY revenue DESC
                """, p)
                .query((rs, i) -> new AnalyticsResponse.PromoterPerformance(rs.getObject("id", UUID.class),
                        rs.getString("name"), rs.getString("referral_code"), rs.getLong("orders"), rs.getLong("tickets"),
                        rs.getBigDecimal("revenue"), rs.getBigDecimal("commission")))
                .list();
    }

    // ------------------------------------------------------------------ Filas para informes CSV

    /** Recorre los pedidos del periodo fila a fila (sin cargarlos todos en memoria). */
    public void forEachOrderRow(Period p, Consumer<Object[]> row) {
        query("""
                SELECT o.reference, o.status, o.created_at, o.paid_at, o.refunded_at, e.name AS event_name,
                       v.name AS venue_name, s.starts_at, o.buyer_name, o.buyer_email,
                       (SELECT SUM(i.quantity) FROM ticket_order_items i WHERE i.order_id = o.id) AS tickets,
                       o.subtotal_amount, o.discount_amount, o.total_amount, o.promo_code, o.commission_amount
                FROM ticket_orders o
                JOIN event_sessions s ON s.id = o.session_id
                JOIN events e ON e.id = s.event_id
                JOIN venues v ON v.id = e.venue_id
                WHERE o.tenant_id = :tenantId AND o.created_at >= :from AND o.created_at < :to
                  AND (CAST(:venueId AS uuid) IS NULL OR v.id = :venueId)
                ORDER BY o.created_at
                """, p)
                .query(rs -> {
                    row.accept(new Object[]{rs.getString("reference"), rs.getString("status"),
                            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("paid_at")),
                            instant(rs.getTimestamp("refunded_at")), rs.getString("event_name"), rs.getString("venue_name"),
                            instant(rs.getTimestamp("starts_at")), rs.getString("buyer_name"), rs.getString("buyer_email"),
                            rs.getLong("tickets"), rs.getBigDecimal("subtotal_amount"), rs.getBigDecimal("discount_amount"),
                            rs.getBigDecimal("total_amount"), rs.getString("promo_code"), rs.getBigDecimal("commission_amount")});
                });
    }

    public void forEachPosSaleRow(Period p, Consumer<Object[]> row) {
        query("""
                SELECT ps.reference, ps.status, ps.created_at, v.name AS venue_name, t.name AS terminal_name,
                       ps.payment_method, ps.subtotal_amount, ps.discount_amount, ps.total_amount,
                       u.email AS sold_by, ps.void_reason
                FROM pos_sales ps
                JOIN pos_terminals t ON t.id = ps.terminal_id
                JOIN venues v ON v.id = t.venue_id
                JOIN users u ON u.id = ps.sold_by
                WHERE ps.tenant_id = :tenantId AND ps.created_at >= :from AND ps.created_at < :to
                  AND (CAST(:venueId AS uuid) IS NULL OR t.venue_id = :venueId)
                ORDER BY ps.created_at
                """, p)
                .query(rs -> {
                    row.accept(new Object[]{rs.getString("reference"), rs.getString("status"),
                            instant(rs.getTimestamp("created_at")), rs.getString("venue_name"), rs.getString("terminal_name"),
                            rs.getString("payment_method"), rs.getBigDecimal("subtotal_amount"),
                            rs.getBigDecimal("discount_amount"), rs.getBigDecimal("total_amount"), rs.getString("sold_by"),
                            rs.getString("void_reason")});
                });
    }

    // ------------------------------------------------------------------ VIP y ocupacion

    public AnalyticsResponse.Vip vipTotals(Period p) {
        return query("""
                SELECT COUNT(*) FILTER (WHERE r.status <> 'CANCELLED') AS reservations,
                       COUNT(*) FILTER (WHERE r.status IN ('SEATED', 'COMPLETED')) AS seated,
                       COUNT(*) FILTER (WHERE r.status = 'NO_SHOW') AS no_shows,
                       COUNT(*) FILTER (WHERE r.status = 'CANCELLED') AS cancelled,
                       COALESCE(SUM(r.deposit_amount) FILTER (WHERE r.deposit_paid_at IS NOT NULL), 0) AS prepaid,
                       COALESCE((SELECT SUM(i.quantity) FROM vip_orders o JOIN vip_order_items i ON i.order_id = o.id
                                 JOIN reservations r2 ON r2.id = o.reservation_id
                                 WHERE o.tenant_id = :tenantId AND o.status <> 'CANCELLED'
                                   AND r2.starts_at >= :from AND r2.starts_at < :to
                                   AND (CAST(:venueId AS uuid) IS NULL OR r2.venue_id = :venueId)), 0) AS bottles,
                       COALESCE((SELECT SUM(i.line_total) FROM vip_orders o JOIN vip_order_items i ON i.order_id = o.id
                                 JOIN reservations r2 ON r2.id = o.reservation_id
                                 WHERE o.tenant_id = :tenantId AND o.status <> 'CANCELLED' AND NOT i.included
                                   AND r2.starts_at >= :from AND r2.starts_at < :to
                                   AND (CAST(:venueId AS uuid) IS NULL OR r2.venue_id = :venueId)), 0) AS extras
                FROM reservations r
                WHERE r.tenant_id = :tenantId AND r.starts_at >= :from AND r.starts_at < :to
                  AND (CAST(:venueId AS uuid) IS NULL OR r.venue_id = :venueId)
                """, p)
                .query((rs, i) -> new AnalyticsResponse.Vip(rs.getLong("reservations"), rs.getLong("seated"),
                        rs.getLong("no_shows"), rs.getLong("cancelled"), rs.getLong("bottles"),
                        rs.getBigDecimal("prepaid"), rs.getBigDecimal("extras")))
                .single();
    }

    public AnalyticsResponse.Occupancy occupancy(Period p) {
        return query("""
                SELECT COUNT(*) AS sessions, COALESCE(SUM(s.capacity), 0) AS capacity,
                       COALESCE(SUM((SELECT COUNT(*) FROM tickets t WHERE t.session_id = s.id
                                     AND t.status IN ('VALID', 'USED'))), 0) AS sold,
                       COALESCE(SUM((SELECT COUNT(*) FROM tickets t WHERE t.session_id = s.id
                                     AND t.status = 'USED')), 0) AS checked_in
                FROM event_sessions s JOIN events e ON e.id = s.event_id
                WHERE s.tenant_id = :tenantId AND s.status <> 'CANCELLED' AND s.starts_at >= :from AND s.starts_at < :to
                  AND (CAST(:venueId AS uuid) IS NULL OR e.venue_id = :venueId)
                """, p)
                .query((rs, i) -> new AnalyticsResponse.Occupancy(rs.getLong("sessions"), rs.getLong("capacity"),
                        rs.getLong("sold"), rs.getLong("checked_in")))
                .single();
    }

    private JdbcClient.StatementSpec query(String sql, Period p) {
        return jdbc.sql(sql)
                .param("tenantId", p.tenantId())
                .param("venueId", p.venueId())
                .param("from", Timestamp.from(p.from()))
                .param("to", Timestamp.from(p.to()));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
