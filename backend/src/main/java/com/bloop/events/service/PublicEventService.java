package com.bloop.events.service;

import com.bloop.events.dto.response.PublicEventCardResponse;
import com.bloop.events.dto.response.PublicEventDetailResponse;
import com.bloop.events.entity.EventStatus;
import com.bloop.events.mapper.EventMapper;
import com.bloop.events.repository.EventRepository;
import com.bloop.events.repository.EventSessionRepository;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.response.PageResponse;
import com.bloop.venues.entity.VenueStatus;
import com.bloop.venues.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Consultas de la web publica. No requieren login y abarcan a TODAS las empresas,
 * pero solo devuelven eventos publicados de locales activos.
 */
@Service
@RequiredArgsConstructor
public class PublicEventService {

    private final EventRepository eventRepository;
    private final EventSessionRepository sessionRepository;
    private final VenueRepository venueRepository;
    private final EventMapper eventMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<PublicEventCardResponse> listUpcoming(String search, String city, String venueSlug,
                                                              int page, int size) {
        Instant now = Instant.now(clock);
        var result = sessionRepository.findPublicUpcoming(now, normalize(search), normalize(city),
                normalize(venueSlug), PageRequest.of(page, Math.min(size, 48)));
        return PageResponse.of(result, session -> eventMapper.toPublicCard(session, now));
    }

    @Transactional(readOnly = true)
    public PublicEventDetailResponse getBySlug(String slug) {
        return eventRepository.findBySlugAndStatus(slug, EventStatus.PUBLISHED)
                .filter(event -> event.getVenue().getStatus() == VenueStatus.ACTIVE)
                .map(event -> eventMapper.toPublicDetail(event, Instant.now(clock)))
                .orElseThrow(() -> NotFoundException.of("Evento"));
    }

    @Transactional(readOnly = true)
    public List<String> cities() {
        return venueRepository.findActiveCities();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
