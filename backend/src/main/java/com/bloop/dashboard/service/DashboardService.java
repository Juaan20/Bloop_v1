package com.bloop.dashboard.service;

import com.bloop.dashboard.dto.response.DashboardResponse;
import com.bloop.dashboard.repository.DashboardRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Calcula los indicadores del panel para la empresa del usuario (opcionalmente para un local). */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardRepository dashboardRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    /**
     * @param days    periodo de analisis hacia atras (ventas, entradas)
     * @param venueId local opcional
     */
    @Transactional(readOnly = true)
    public DashboardResponse summary(int days, UUID venueId) {
        if (days < 1 || days > 365) {
            throw new BadRequestException("El periodo debe estar entre 1 y 365 dias");
        }
        UUID tenantId = currentUserProvider.requireTenantId();
        Instant now = Instant.now(clock);
        Instant from = now.minus(days, ChronoUnit.DAYS);

        var money = dashboardRepository.revenueAndOrders(tenantId, venueId, from);
        var tickets = dashboardRepository.ticketsAndCheckIns(tenantId, venueId, from);
        BigDecimal average = money.paidOrders() == 0 ? BigDecimal.ZERO
                : money.revenue().divide(BigDecimal.valueOf(money.paidOrders()), 2, RoundingMode.HALF_UP);

        var kpis = new DashboardResponse.Kpis(money.revenue(), money.paidOrders(), tickets.sold(), tickets.checkedIn(),
                dashboardRepository.upcomingSessions(tenantId, venueId, now),
                dashboardRepository.publishedEvents(tenantId, venueId), average);

        return new DashboardResponse(kpis,
                dashboardRepository.salesByDay(tenantId, venueId, from),
                dashboardRepository.sessionPerformance(tenantId, venueId, now.minus(7, ChronoUnit.DAYS), 12),
                dashboardRepository.salesByVenue(tenantId, from));
    }
}
