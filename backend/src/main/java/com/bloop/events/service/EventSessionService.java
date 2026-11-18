package com.bloop.events.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.events.dto.request.SessionRequest;
import com.bloop.events.dto.response.SessionResponse;
import com.bloop.events.entity.Event;
import com.bloop.events.entity.EventSession;
import com.bloop.events.entity.SessionStatus;
import com.bloop.events.mapper.EventMapper;
import com.bloop.events.repository.EventRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.venues.entity.Venue;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Gestion de las sesiones (fechas) de un evento. */
@Service
@RequiredArgsConstructor
public class EventSessionService {

    private static final Duration MAX_SESSION_LENGTH = Duration.ofHours(24);

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final com.bloop.media.service.MediaUrlValidator mediaUrls;

    @Transactional
    public SessionResponse add(UUID eventId, SessionRequest request) {
        Event event = findEvent(eventId);
        EventService.assertNotCancelled(event);
        EventSession session = new EventSession();
        applySessionData(session, request, event.getVenue());
        event.addSession(session);
        // flush (no save): el evento ya esta gestionado; Hibernate persiste en cascada el hijo nuevo y le asigna id.
        eventRepository.flush();
        auditService.record(AuditAction.SESSION_CHANGED, "EventSession", session.getId(), "Nueva sesion " + session.getStartsAt());
        return eventMapper.toResponse(session, Instant.now(clock));
    }

    @Transactional
    public SessionResponse update(UUID eventId, UUID sessionId, SessionRequest request) {
        Event event = findEvent(eventId);
        EventService.assertNotCancelled(event);
        EventSession session = findSession(event, sessionId);
        if (request.capacity() < session.getTicketsConfigured()) {
            throw new BadRequestException("El aforo no puede ser menor que las entradas configuradas ("
                    + session.getTicketsConfigured() + ")");
        }
        Instant previousStart = session.getStartsAt();
        Instant previousEnd = session.getEndsAt();
        applySessionData(session, request, event.getVenue());
        auditService.record(AuditAction.SESSION_CHANGED, "EventSession", sessionId, "Sesion modificada");
        boolean rescheduled = !previousStart.equals(session.getStartsAt()) || !previousEnd.equals(session.getEndsAt());
        if (rescheduled && session.getTicketsSold() > 0) {
            // Quien ya tiene entradas debe enterarse del nuevo horario (correo tras el commit).
            eventPublisher.publishEvent(new EventChangeNotice(eventId, List.of(sessionId),
                    EventChangeNotice.Kind.SESSION_RESCHEDULED, previousStart));
        }
        return eventMapper.toResponse(session, Instant.now(clock));
    }

    /** Elimina una sesion sin ventas; si ya tiene ventas solo se puede cancelar. */
    @Transactional
    public void delete(UUID eventId, UUID sessionId) {
        Event event = findEvent(eventId);
        EventSession session = findSession(event, sessionId);
        if (session.getTicketsSold() > 0) {
            throw new ConflictException("La sesion tiene entradas vendidas: cancelala en lugar de eliminarla");
        }
        if (event.getSessions().size() == 1) {
            throw new ConflictException("Un evento debe tener al menos una sesion");
        }
        event.getSessions().remove(session);
        eventRepository.flush();
        auditService.record(AuditAction.SESSION_CHANGED, "EventSession", sessionId, "Sesion eliminada");
    }

    @Transactional
    public SessionResponse cancel(UUID eventId, UUID sessionId) {
        Event event = findEvent(eventId);
        EventSession session = findSession(event, sessionId);
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new ConflictException("La sesion ya estaba cancelada");
        }
        session.setStatus(SessionStatus.CANCELLED);
        auditService.record(AuditAction.SESSION_CHANGED, "EventSession", sessionId, "Sesion cancelada");
        eventPublisher.publishEvent(new EventChangeNotice(eventId, List.of(sessionId),
                EventChangeNotice.Kind.SESSION_CANCELLED, null));
        return eventMapper.toResponse(session, Instant.now(clock));
    }

    /** Valida y copia los datos de una sesion. Lo reutiliza EventService al crear el evento. */
    public void applySessionData(EventSession session, SessionRequest request, Venue venue) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BadRequestException("La hora de fin debe ser posterior a la de inicio");
        }
        if (Duration.between(request.startsAt(), request.endsAt()).compareTo(MAX_SESSION_LENGTH) > 0) {
            throw new BadRequestException("Una sesion no puede durar mas de 24 horas");
        }
        if (request.doorsOpenAt() != null && request.doorsOpenAt().isAfter(request.startsAt())) {
            throw new BadRequestException("La apertura de puertas no puede ser posterior al inicio");
        }
        if (request.capacity() > venue.getCapacity()) {
            throw new BadRequestException("El aforo de la sesion supera el del local (" + venue.getCapacity() + ")");
        }
        session.setStartsAt(request.startsAt());
        session.setEndsAt(request.endsAt());
        session.setDoorsOpenAt(request.doorsOpenAt());
        session.setCapacity(request.capacity());
        session.setTitle(blankToNull(request.title()));
        session.setDescription(blankToNull(request.description()));
        session.setImageUrl(mediaUrls.requireOwnUrl(request.imageUrl(), session.getImageUrl()));
        session.setSalesPaused(Boolean.TRUE.equals(request.salesPaused()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Busca un evento del tenant actual (404 si no existe o es de otra empresa). */
    Event findEvent(UUID eventId) {
        return eventRepository.findByIdAndTenantId(eventId, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Evento"));
    }

    static EventSession findSession(Event event, UUID sessionId) {
        return event.getSessions().stream()
                .filter(s -> s.getId().equals(sessionId))
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("Sesion"));
    }
}
