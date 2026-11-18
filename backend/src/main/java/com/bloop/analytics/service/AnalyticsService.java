package com.bloop.analytics.service;

import com.bloop.analytics.dto.response.AnalyticsResponse;
import com.bloop.analytics.repository.AnalyticsRepository;
import com.bloop.analytics.repository.AnalyticsRepository.Period;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.venues.repository.VenueRepository;
import com.bloop.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Analitica avanzada: une ventas de entradas, TPV y promotores de un periodo.
 * Las fechas se interpretan en hora de Espana: del dia "from" a las 00:00 al dia "to" a las 24:00.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final int MAX_DAYS = 366;

    private final AnalyticsRepository analyticsRepository;
    private final VenueRepository venueRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public AnalyticsResponse overview(LocalDate from, LocalDate to, UUID venueId) {
        Period period = period(from, to, venueId);

        var tickets = analyticsRepository.ticketTotals(period);
        var refunds = analyticsRepository.refundTotals(period);
        var pos = analyticsRepository.posTotals(period);
        BigDecimal posAverage = pos.sales() == 0 ? BigDecimal.ZERO
                : pos.revenue().divide(BigDecimal.valueOf(pos.sales()), 2, RoundingMode.HALF_UP);
        var summary = new AnalyticsResponse.Summary(tickets.revenue(), tickets.orders(), tickets.tickets(),
                refunds.amount(), refunds.orders(), pos.revenue(), pos.sales(), pos.voided(), posAverage,
                tickets.revenue().add(pos.revenue()), tickets.commissions());

        return new AnalyticsResponse(from, to, summary,
                analyticsRepository.revenueByDay(period),
                analyticsRepository.topEvents(period, 10),
                analyticsRepository.topProducts(period, 10),
                analyticsRepository.posByTerminal(period),
                analyticsRepository.posByPaymentMethod(period),
                analyticsRepository.posByHour(period),
                analyticsRepository.promoters(period),
                analyticsRepository.vipTotals(period),
                analyticsRepository.occupancy(period));
    }

    /**
     * Valida el periodo y el local, y lo convierte a instantes UTC con el tenant del token.
     * Tambien lo usa el servicio de informes CSV.
     */
    Period period(LocalDate from, LocalDate to, UUID venueId) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new BadRequestException("Indica un periodo valido (desde <= hasta)");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new BadRequestException("El periodo maximo es de " + MAX_DAYS + " dias");
        }
        UUID tenantId = currentUserProvider.requireTenantId();
        if (venueId != null && venueRepository.findByIdAndTenantId(venueId, tenantId).isEmpty()) {
            throw NotFoundException.of("Local");
        }
        return new Period(tenantId, venueId, from.atStartOfDay(ZONE).toInstant(),
                to.plusDays(1).atStartOfDay(ZONE).toInstant());
    }
}
