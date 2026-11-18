package com.bloop.promoters.dto.response;

import com.bloop.events.entity.EventStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Panel de un promotor: resumen, rendimiento por evento asignado (con su enlace de venta y
 * el progreso del objetivo) y sus codigos promocionales. Lo usan el admin y el propio promotor.
 */
public record PromoterDetailResponse(PromoterResponse promoter, List<EventPerformance> events,
                                     List<PromoCodeResponse> promoCodes) {

    /**
     * @param targetProgress porcentaje del objetivo alcanzado (null si no hay objetivo)
     * @param assigned       false si el evento ya no esta asignado pero tuvo ventas atribuidas
     */
    public record EventPerformance(UUID eventId, String eventName, String eventSlug, EventStatus eventStatus,
                                   boolean assigned, String salesLink, Integer targetTickets, long ticketsSold,
                                   long orders, BigDecimal revenue, BigDecimal commission, Integer targetProgress) {
    }
}
