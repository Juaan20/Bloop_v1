package com.bloop.events.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.events.dto.request.CreateEventRequest;
import com.bloop.events.dto.request.UpdateEventRequest;
import com.bloop.events.dto.response.EventDetailResponse;
import com.bloop.events.dto.response.EventSummaryResponse;
import com.bloop.events.entity.Event;
import com.bloop.events.entity.EventSession;
import com.bloop.events.entity.EventStatus;
import com.bloop.events.entity.TicketType;
import com.bloop.events.mapper.EventMapper;
import com.bloop.events.repository.EventRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.response.PageResponse;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.shared.util.SlugUtils;
import com.bloop.venues.entity.Venue;
import com.bloop.venues.entity.VenueStatus;
import com.bloop.venues.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Casos de uso de eventos en el backoffice: alta, edicion y ciclo de publicacion. */
@Service
@RequiredArgsConstructor
public class EventService {

    private final com.bloop.media.service.MediaUrlValidator mediaUrls;
    private final EventRepository eventRepository;
    private final EventSessionService eventSessionService;
    private final VenueService venueService;
    private final EventMapper eventMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> list(EventStatus status, UUID venueId, String search, int page, int size) {
        UUID tenantId = currentUserProvider.requireTenantId();
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Instant now = Instant.now(clock);
        return PageResponse.of(
                eventRepository.searchByTenant(tenantId, status, venueId, search == null ? "" : search.trim(), pageable),
                event -> eventMapper.toSummary(event, now));
    }

    @Transactional(readOnly = true)
    public EventDetailResponse get(UUID id) {
        return eventMapper.toDetail(findInMyTenant(id), Instant.now(clock));
    }

    @Transactional
    public EventDetailResponse create(CreateEventRequest request) {
        Venue venue = venueService.findInMyTenant(request.venueId());
        Event event = new Event();
        event.setTenantId(venue.getTenantId());
        event.setVenue(venue);
        applyData(event, request.name(), request.description(), mediaUrls.requireOwnUrl(request.imageUrl(), null),
                request.minAge());
        event.setSlug(SlugUtils.uniqueSlug(request.name(), eventRepository::existsBySlug));

        EventSession session = new EventSession();
        eventSessionService.applySessionData(session, request.firstSession(), venue);
        event.addSession(session);

        event = eventRepository.save(event);
        auditService.record(AuditAction.EVENT_CREATED, "Event", event.getId(), event.getName());
        return eventMapper.toDetail(event, Instant.now(clock));
    }

    @Transactional
    public EventDetailResponse update(UUID id, UpdateEventRequest request) {
        Event event = findInMyTenant(id);
        assertNotCancelled(event);
        applyData(event, request.name(), request.description(),
                mediaUrls.requireOwnUrl(request.imageUrl(), event.getImageUrl()), request.minAge());
        auditService.record(AuditAction.EVENT_UPDATED, "Event", id, event.getName());
        return eventMapper.toDetail(event, Instant.now(clock));
    }

    /**
     * Publica el evento. Requisitos: local activo y al menos una sesion futura
     * con algun tipo de entrada activo.
     */
    @Transactional
    public EventDetailResponse publish(UUID id) {
        Event event = findInMyTenant(id);
        assertNotCancelled(event);
        Instant now = Instant.now(clock);
        if (event.getVenue().getStatus() != VenueStatus.ACTIVE) {
            throw new BadRequestException("El local esta inactivo");
        }
        boolean hasSellableSession = event.getSessions().stream()
                .filter(s -> s.isUpcoming(now))
                .anyMatch(s -> s.getTicketTypes().stream().anyMatch(TicketType::isActive));
        if (!hasSellableSession) {
            throw new BadRequestException("Para publicar, el evento necesita una sesion futura con entradas activas");
        }
        event.setStatus(EventStatus.PUBLISHED);
        if (event.getPublishedAt() == null) {
            event.setPublishedAt(now);
        }
        auditService.record(AuditAction.EVENT_PUBLISHED, "Event", id, event.getName());
        return eventMapper.toDetail(event, now);
    }

    /** Vuelve a borrador: deja de verse en la web, pero las entradas vendidas siguen siendo validas. */
    @Transactional
    public EventDetailResponse unpublish(UUID id) {
        Event event = findInMyTenant(id);
        assertNotCancelled(event);
        event.setStatus(EventStatus.DRAFT);
        auditService.record(AuditAction.EVENT_UNPUBLISHED, "Event", id, event.getName());
        return eventMapper.toDetail(event, Instant.now(clock));
    }

    /**
     * Cancela el evento definitivamente y avisa por correo a los titulares de entradas.
     * En esta fase no se hacen reembolsos automaticos: se gestionaran en la fase 4 junto con Stripe.
     */
    @Transactional
    public EventDetailResponse cancel(UUID id) {
        Event event = findInMyTenant(id);
        assertNotCancelled(event);
        event.setStatus(EventStatus.CANCELLED);
        auditService.record(AuditAction.EVENT_CANCELLED, "Event", id, event.getName());
        // Aviso por correo a todos los titulares de entradas (tras el commit).
        eventPublisher.publishEvent(new EventChangeNotice(id,
                event.getSessions().stream().map(EventSession::getId).toList(),
                EventChangeNotice.Kind.EVENT_CANCELLED, null));
        return eventMapper.toDetail(event, Instant.now(clock));
    }

    /** Busca un evento del tenant actual (404 si no existe o es de otra empresa). */
    public Event findInMyTenant(UUID id) {
        return eventSessionService.findEvent(id);
    }

    static void assertNotCancelled(Event event) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("El evento esta cancelado y no admite cambios");
        }
    }

    private static void applyData(Event event, String name, String description, String imageUrl, Integer minAge) {
        event.setName(name.trim());
        event.setDescription(description);
        event.setImageUrl(imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim());
        event.setMinAge(minAge);
    }
}
