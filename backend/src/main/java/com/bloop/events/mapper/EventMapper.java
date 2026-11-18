package com.bloop.events.mapper;

import com.bloop.events.dto.response.EventDetailResponse;
import com.bloop.events.dto.response.EventSummaryResponse;
import com.bloop.events.dto.response.PublicEventCardResponse;
import com.bloop.events.dto.response.PublicEventDetailResponse;
import com.bloop.events.dto.response.SessionResponse;
import com.bloop.events.dto.response.TicketTypeResponse;
import com.bloop.events.entity.Event;
import com.bloop.events.entity.EventSession;
import com.bloop.events.entity.SaleStatus;
import com.bloop.events.entity.TicketType;
import com.bloop.venues.mapper.VenueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Convierte eventos, sesiones y tipos de entrada en DTOs.
 * Recibe "now" para calcular el estado de venta en el momento de la peticion.
 */
@Component
@RequiredArgsConstructor
public class EventMapper {

    private final VenueMapper venueMapper;

    public EventSummaryResponse toSummary(Event event, Instant now) {
        Instant next = event.getSessions().stream()
                .filter(s -> s.isUpcoming(now))
                .map(EventSession::getStartsAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
        int sold = event.getSessions().stream().mapToInt(EventSession::getTicketsSold).sum();
        int configured = event.getSessions().stream().mapToInt(EventSession::getTicketsConfigured).sum();
        return new EventSummaryResponse(event.getId(), event.getName(), event.getSlug(), event.getStatus(),
                event.getVenue().getId(), event.getVenue().getName(), next, event.getSessions().size(), sold, configured);
    }

    public EventDetailResponse toDetail(Event event, Instant now) {
        List<SessionResponse> sessions = event.getSessions().stream().map(s -> toResponse(s, now)).toList();
        return new EventDetailResponse(event.getId(), event.getName(), event.getSlug(), event.getDescription(),
                event.getImageUrl(), event.getMinAge(), event.getStatus(), event.getPublishedAt(),
                event.getVenue().getId(), event.getVenue().getName(), event.getVenue().getCapacity(), sessions,
                event.getCreatedAt());
    }

    public SessionResponse toResponse(EventSession s, Instant now) {
        return new SessionResponse(s.getId(), s.getStartsAt(), s.getEndsAt(), s.getDoorsOpenAt(), s.getCapacity(),
                s.getStatus(), s.getTicketsSold(), s.getTicketsConfigured(),
                s.getTicketTypes().stream().map(t -> toResponse(t, now)).toList(), s.getTitle(), s.getDescription(),
                s.getImageUrl(), s.isSalesPaused());
    }

    public TicketTypeResponse toResponse(TicketType t, Instant now) {
        return new TicketTypeResponse(t.getId(), t.getName(), t.getDescription(), t.getCategory(), t.getPrice(),
                t.getCurrency(), t.getQuantityTotal(), t.getQuantitySold(), t.getAvailable(), t.getMaxPerOrder(),
                t.getSalesStartAt(), t.getSalesEndAt(), t.isActive(), t.getSortOrder(), t.saleStatus(now));
    }

    // ---------- Web publica ----------

    public PublicEventCardResponse toPublicCard(EventSession session, Instant now) {
        Event event = session.getEvent();
        List<TicketType> visible = visibleTicketTypes(session);
        BigDecimal minPrice = visible.stream().map(TicketType::getPrice).min(Comparator.naturalOrder()).orElse(null);
        boolean soldOut = visible.stream().noneMatch(t -> t.saleStatus(now) == SaleStatus.ON_SALE
                || t.saleStatus(now) == SaleStatus.NOT_STARTED);
        String currency = visible.isEmpty() ? "EUR" : visible.getFirst().getCurrency();
        return new PublicEventCardResponse(event.getSlug(), event.getName(), event.getImageUrl(),
                event.getVenue().getName(), event.getVenue().getSlug(), event.getVenue().getCity(), session.getId(),
                session.getStartsAt(), minPrice, currency, soldOut, session.getTitle());
    }

    public PublicEventDetailResponse toPublicDetail(Event event, Instant now) {
        List<PublicEventDetailResponse.Session> sessions = event.getSessions().stream()
                .filter(s -> s.isUpcoming(now))
                .map(s -> {
                    List<PublicEventDetailResponse.Ticket> tickets = visibleTicketTypes(s).stream()
                            .map(t -> new PublicEventDetailResponse.Ticket(t.getId(), t.getName(), t.getDescription(),
                                    t.getCategory(), t.getPrice(), t.getCurrency(), t.getAvailable(),
                                    t.getMaxPerOrder(), t.saleStatus(now)))
                            .toList();
                    boolean soldOut = tickets.stream().noneMatch(t -> t.saleStatus() == SaleStatus.ON_SALE
                            || t.saleStatus() == SaleStatus.NOT_STARTED);
                    return new PublicEventDetailResponse.Session(s.getId(), s.getStartsAt(), s.getEndsAt(),
                            s.getDoorsOpenAt(), soldOut, tickets, s.getTitle(), s.getDescription(), s.getImageUrl(),
                            s.isSalesPaused());
                })
                .toList();
        return new PublicEventDetailResponse(event.getSlug(), event.getName(), event.getDescription(),
                event.getImageUrl(), event.getMinAge(), venueMapper.toPublicResponse(event.getVenue()), sessions);
    }

    /** En la web solo se muestran los tipos activos. */
    private static List<TicketType> visibleTicketTypes(EventSession session) {
        return session.getTicketTypes().stream().filter(TicketType::isActive).toList();
    }
}
