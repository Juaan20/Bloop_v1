package com.bloop.analytics.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Analitica avanzada de un periodo: entradas + TPV + promotores. Todo sale de consultas SQL
 * sobre datos reales (AnalyticsRepository).
 */
public record AnalyticsResponse(LocalDate from, LocalDate to, Summary summary, List<DailyRevenue> revenueByDay,
                                List<TopEvent> topEvents, List<TopProduct> topProducts,
                                List<TerminalSales> posByTerminal, List<PaymentMethodSales> posByPaymentMethod,
                                List<HourlySales> posByHour, List<PromoterPerformance> promoters, Vip vip,
                                Occupancy occupancy) {

    /**
     * Mesas VIP del periodo (por fecha de la reserva).
     * @param prepaidRevenue reservas + paquetes cobrados antes de entrar
     * @param extrasRevenue  botellas adicionales pedidas en mesa (pedidos no cancelados)
     */
    public record Vip(long reservations, long seatedOrCompleted, long noShows, long cancelled, long bottlesSold,
                      BigDecimal prepaidRevenue, BigDecimal extrasRevenue) {
    }

    /** Sesiones que empiezan en el periodo: aforo, vendidas y asistencia real (entradas escaneadas). */
    public record Occupancy(long sessions, long capacity, long ticketsSold, long checkedIn) {
    }

    /**
     * @param ticketRevenue    pedidos de entradas pagados (los reembolsados no cuentan)
     * @param refundedAmount   importe reembolsado en el periodo
     * @param posRevenue       ventas del TPV completadas (las anuladas no cuentan)
     * @param totalRevenue     ticketRevenue + posRevenue
     * @param promoterCommissions comisiones generadas por promotores en pedidos pagados
     */
    public record Summary(BigDecimal ticketRevenue, long paidOrders, long ticketsSold, BigDecimal refundedAmount,
                          long refundedOrders, BigDecimal posRevenue, long posSales, long voidedPosSales,
                          BigDecimal posAverageTicket, BigDecimal totalRevenue, BigDecimal promoterCommissions) {
    }

    public record DailyRevenue(LocalDate day, BigDecimal ticketRevenue, BigDecimal posRevenue) {
    }

    public record TopEvent(UUID eventId, String eventName, String venueName, long ticketsSold, long checkedIn,
                           BigDecimal revenue) {
    }

    public record TopProduct(String productName, String variantName, long quantity, BigDecimal revenue) {
    }

    public record TerminalSales(UUID terminalId, String terminalName, String type, String venueName, long sales,
                                BigDecimal revenue) {
    }

    public record PaymentMethodSales(String paymentMethod, long sales, BigDecimal revenue) {
    }

    /** Ventas del TPV por hora del dia (hora de Espana): para planificar personal de barra. */
    public record HourlySales(int hour, long sales, BigDecimal revenue) {
    }

    public record PromoterPerformance(UUID promoterId, String promoterName, String referralCode, long orders,
                                      long tickets, BigDecimal revenue, BigDecimal commission) {
    }
}
