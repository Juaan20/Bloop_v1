package com.bloop.accesscontrol.service;

import com.bloop.accesscontrol.dto.request.ValidateTicketRequest;
import com.bloop.accesscontrol.dto.response.AccessSessionResponse;
import com.bloop.accesscontrol.dto.response.AccessValidationResponse;
import com.bloop.accesscontrol.dto.response.ValidationResultResponse;
import com.bloop.accesscontrol.entity.AccessValidation;
import com.bloop.accesscontrol.entity.ValidationResult;
import com.bloop.accesscontrol.repository.AccessValidationRepository;
import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.events.entity.EventSession;
import com.bloop.events.repository.EventSessionRepository;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.response.PageResponse;
import com.bloop.shared.security.CurrentUser;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.ticketing.entity.Ticket;
import com.bloop.ticketing.entity.TicketStatus;
import com.bloop.ticketing.repository.TicketRepository;
import com.bloop.ticketing.service.QrCodeService;
import com.bloop.venues.service.StaffVenueAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Control de acceso en la puerta.
 *
 * Validacion de un QR:
 * 1. Se comprueba la firma HMAC (descarta QR falsos sin tocar la BD).
 * 2. Se busca la entrada y se comprueba que es de esta empresa y de esta sesion.
 * 3. Se marca como usada con un UPDATE condicional atomico: aunque dos porteros escaneen
 *    el mismo QR en el mismo instante, solo uno obtendra "ACCEPTED".
 * 4. Cada intento queda registrado en access_validations.
 */
@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final TicketRepository ticketRepository;
    private final EventSessionRepository sessionRepository;
    private final AccessValidationRepository validationRepository;
    private final StaffVenueAccess staffVenueAccess;
    private final QrCodeService qrCodeService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public ValidationResultResponse validate(ValidateTicketRequest request) {
        CurrentUser user = currentUserProvider.get();
        UUID tenantId = currentUserProvider.requireTenantId();
        EventSession session = sessionRepository.findByIdAndTenantId(request.sessionId(), tenantId)
                .orElseThrow(() -> NotFoundException.of("Sesion"));
        assertCanWorkAtVenue(user, session.getEvent().getVenue().getId());
        Instant now = Instant.now(clock);

        Optional<String> code = qrCodeService.extractVerifiedCode(request.qrPayload());
        Optional<Ticket> found = code.flatMap(ticketRepository::findByCode)
                // Una entrada de otra empresa se trata como inexistente: no revelamos nada.
                .filter(ticket -> ticket.getTenantId().equals(tenantId));
        if (found.isEmpty()) {
            return record(tenantId, session.getId(), null, user.userId(), ValidationResult.INVALID_CODE, now,
                    "Codigo QR no valido", null, null);
        }
        Ticket ticket = found.get();
        if (!ticket.getSession().getId().equals(session.getId())) {
            return record(tenantId, session.getId(), ticket.getId(), user.userId(), ValidationResult.WRONG_SESSION, now,
                    "La entrada es de otro evento o de otra fecha", ticket, ticket.getUsedAt());
        }
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            return record(tenantId, session.getId(), ticket.getId(), user.userId(), ValidationResult.CANCELLED, now,
                    "Entrada cancelada", ticket, null);
        }

        int updated = ticketRepository.markUsedIfValid(ticket.getId(), user.userId(), now);
        if (updated == 0) {
            // Otro escaneo se adelanto (o ya estaba usada): leemos la hora real de uso desde la BD.
            Instant usedAt = ticketRepository.findUsedAtById(ticket.getId()).orElse(null);
            return record(tenantId, session.getId(), ticket.getId(), user.userId(), ValidationResult.ALREADY_USED,
                    now, "Entrada YA UTILIZADA", ticket, usedAt);
        }
        auditService.record(AuditAction.TICKET_VALIDATED, "Ticket", ticket.getId(), "Acceso aceptado");
        return record(tenantId, session.getId(), ticket.getId(), user.userId(), ValidationResult.ACCEPTED, now,
                "Acceso permitido", ticket, now);
    }

    /** Sesiones de las ultimas 12 h y proximas 24 h en los locales donde trabaja el usuario. */
    @Transactional(readOnly = true)
    public List<AccessSessionResponse> activeSessions() {
        CurrentUser user = currentUserProvider.get();
        UUID tenantId = currentUserProvider.requireTenantId();
        Set<UUID> venueIds = allowedVenueIds(user, tenantId);
        if (venueIds.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now(clock);
        return sessionRepository.findActiveForAccess(tenantId, venueIds, now.minus(12, ChronoUnit.HOURS),
                        now.plus(24, ChronoUnit.HOURS)).stream()
                .map(s -> new AccessSessionResponse(s.getId(), s.getEvent().getName(), s.getEvent().getVenue().getName(),
                        s.getStartsAt(), s.getEndsAt(), s.getDoorsOpenAt(), s.getCapacity(),
                        s.getTicketsSold(), ticketRepository.countBySessionIdAndStatus(s.getId(), TicketStatus.USED)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AccessValidationResponse> history(UUID sessionId, int page, int size) {
        UUID tenantId = currentUserProvider.requireTenantId();
        sessionRepository.findByIdAndTenantId(sessionId, tenantId).orElseThrow(() -> NotFoundException.of("Sesion"));
        var result = validationRepository.findBySessionIdAndTenantIdOrderByCreatedAtDesc(sessionId, tenantId,
                PageRequest.of(page, Math.min(size, 100)));
        return PageResponse.of(result, v -> new AccessValidationResponse(v.getId(), v.getResult(), v.getTicketId(),
                v.getValidatedBy(), v.getCreatedAt()));
    }

    private ValidationResultResponse record(UUID tenantId, UUID sessionId, UUID ticketId, UUID userId,
                                            ValidationResult result, Instant now, String message, Ticket ticket,
                                            Instant usedAt) {
        AccessValidation validation = new AccessValidation();
        validation.setTenantId(tenantId);
        validation.setSessionId(sessionId);
        validation.setTicketId(ticketId);
        validation.setValidatedBy(userId);
        validation.setResult(result);
        validation.setCreatedAt(now);
        validationRepository.save(validation);
        return new ValidationResultResponse(result == ValidationResult.ACCEPTED, result, message,
                ticket == null ? null : ticket.getTicketType().getName(),
                ticket == null ? null : ticket.getHolderName(),
                usedAt,
                ticket == null ? null : com.bloop.orders.service.AttendeeRules.masked(ticket.getHolderDocumentType(),
                        ticket.getHolderDocumentLast4()),
                ticket == null ? null : ticket.getSession().getEvent().getMinAge());
    }

    /**
     * TENANT_ADMIN y MANAGER pueden validar en todos los locales de su empresa.
     * El resto (p. ej. DOOR_STAFF) solo en los locales que tienen asignados.
     */
    private void assertCanWorkAtVenue(CurrentUser user, UUID venueId) {
        staffVenueAccess.assertCanWorkAtVenue(user, venueId);
    }

    private Set<UUID> allowedVenueIds(CurrentUser user, UUID tenantId) {
        return staffVenueAccess.allowedVenueIds(user, tenantId);
    }
}
