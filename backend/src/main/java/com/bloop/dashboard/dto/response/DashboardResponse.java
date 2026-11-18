package com.bloop.dashboard.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Indicadores del panel. Todos se calculan con consultas SQL sobre datos reales.
 *
 * @param revenue       ingresos de pedidos pagados
 * @param ticketsSold   entradas emitidas (validas o usadas)
 * @param checkedIn     entradas ya validadas en puerta (asistencia)
 */
public record DashboardResponse(Kpis kpis, List<DailySales> salesByDay, List<SessionPerformance> sessions,
                                List<VenueSales> salesByVenue) {

    public record Kpis(BigDecimal revenue, long paidOrders, long ticketsSold, long checkedIn,
                       long upcomingSessions, long publishedEvents, BigDecimal averageOrderValue) {
    }

    public record DailySales(LocalDate day, BigDecimal revenue, long tickets) {
    }

    public record SessionPerformance(UUID sessionId, UUID eventId, String eventName, String venueName,
                                     Instant startsAt, int capacity, long ticketsSold, long checkedIn,
                                     BigDecimal revenue) {
    }

    public record VenueSales(UUID venueId, String venueName, BigDecimal revenue, long ticketsSold) {
    }
}
