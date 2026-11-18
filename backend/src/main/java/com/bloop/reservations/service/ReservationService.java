package com.bloop.reservations.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.customers.service.CustomerService;
import com.bloop.events.entity.EventSession;
import com.bloop.events.repository.EventSessionRepository;
import com.bloop.reservations.dto.request.ChangeReservationStatusRequest;
import com.bloop.reservations.dto.request.CreateReservationRequest;
import com.bloop.reservations.dto.response.ReservationResponse;
import com.bloop.reservations.dto.response.TableAvailabilityResponse;
import com.bloop.reservations.entity.Reservation;
import com.bloop.reservations.entity.ReservationSource;
import com.bloop.reservations.entity.ReservationStatus;
import com.bloop.reservations.entity.VipTable;
import com.bloop.reservations.mapper.ReservationMapper;
import com.bloop.reservations.repository.ReservationRepository;
import com.bloop.reservations.repository.VipTableRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.util.SecureTokens;
import com.bloop.reservations.dto.request.WalkInRequest;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.response.PageResponse;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.venues.entity.Venue;
import com.bloop.venues.repository.VenueRepository;
import com.bloop.venues.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Reservas VIP en el backoffice: listado, alta manual, cambios de estado y anticipos. */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final VipTableRepository tableRepository;
    private final EventSessionRepository sessionRepository;
    private final VenueRepository venueRepository;
    private final VenueService venueService;
    private final CustomerService customerService;
    private final ReservationBooking booking;
    private final ReservationMapper reservationMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** Sin fechas: desde hace 12 horas (la noche en curso) hasta dentro de 60 dias. */
    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> list(Instant from, Instant to, UUID venueId, ReservationStatus status,
                                                  String search, int page, int size) {
        UUID tenantId = currentUserProvider.requireTenantId();
        Instant now = Instant.now(clock);
        Instant start = from != null ? from : now.minus(Duration.ofHours(12));
        Instant end = to != null ? to : start.plus(Duration.ofDays(60));
        Map<UUID, String> venues = venueNames(tenantId);
        var result = reservationRepository.search(tenantId, start, end, venueId, status,
                search == null ? "" : search.trim(), PageRequest.of(page, Math.min(size, 100)));
        return PageResponse.of(result, r -> reservationMapper.toResponse(r, venues.get(r.getVenueId())));
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(UUID id) {
        return toResponse(findInMyTenant(id));
    }

    /** Disponibilidad de las mesas del plano de un local para una fiesta. */
    @Transactional(readOnly = true)
    public List<TableAvailabilityResponse> availability(UUID venueId, UUID sessionId) {
        Venue venue = venueService.findInMyTenant(venueId);
        Interval interval = resolveInterval(venue.getId(), sessionId);
        return booking.availability(venue.getId(), interval.startsAt(), interval.endsAt());
    }

    /** Alta manual (telefono, en persona...): nace CONFIRMADA porque la gestiona el propio equipo. */
    @Transactional
    public ReservationResponse create(CreateReservationRequest request) {
        UUID tenantId = currentUserProvider.requireTenantId();
        VipTable table = tableRepository.findByIdAndTenantId(request.tableId(), tenantId)
                .orElseThrow(() -> NotFoundException.of("Mesa"));
        UUID venueId = table.getZone().getVenueId();
        Interval interval = resolveInterval(venueId, request.sessionId());

        Reservation reservation = new Reservation();
        reservation.setTenantId(tenantId);
        reservation.setVenueId(venueId);
        reservation.setTable(table);
        reservation.setSession(interval.session());
        reservation.setStartsAt(interval.startsAt());
        reservation.setEndsAt(interval.endsAt());
        reservation.setGuests(request.guests());
        reservation.setContactName(request.contactName().trim());
        reservation.setContactEmail(normalizeEmail(request.contactEmail()));
        reservation.setContactPhone(blankToNull(request.contactPhone()));
        reservation.setNotes(blankToNull(request.notes()));
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setSource(ReservationSource.BACKOFFICE);
        linkCustomer(tenantId, reservation);

        Reservation saved = booking.book(reservation);
        auditService.record(AuditAction.RESERVATION_CREATED, "Reservation", saved.getId(),
                saved.getReference() + " mesa=" + table.getName());
        if (saved.getContactEmail() != null) {
            eventPublisher.publishEvent(new ReservationEmailEvent(saved.getId(), ReservationEmailEvent.Kind.CONFIRMED));
        }
        return toResponse(saved);
    }

    /**
     * Mesa vendida en taquilla: reserva desde ahora, cobro de la reserva + paquete y acceso directo a la
     * mesa (con su QR), todo en una transaccion. Los solapamientos se controlan igual que en una reserva.
     */
    @Transactional
    public ReservationResponse walkIn(WalkInRequest request) {
        UUID tenantId = currentUserProvider.requireTenantId();
        VipTable table = tableRepository.findByIdAndTenantId(request.tableId(), tenantId)
                .orElseThrow(() -> NotFoundException.of("Mesa"));
        Instant now = Instant.now(clock);
        Interval interval = resolveInterval(table.getZone().getVenueId(), request.sessionId());
        if (!interval.endsAt().isAfter(now)) {
            throw new BadRequestException("Esa fiesta ya ha terminado");
        }
        if (table.getDepositAmount().signum() > 0 && request.paymentMethod() == null) {
            throw new BadRequestException("Indica como se cobra la mesa (" + table.getDepositAmount() + " EUR)");
        }
        Reservation reservation = new Reservation();
        reservation.setTenantId(tenantId);
        reservation.setVenueId(table.getZone().getVenueId());
        reservation.setTable(table);
        reservation.setSession(interval.session());
        reservation.setStartsAt(interval.startsAt());
        reservation.setEndsAt(interval.endsAt());
        reservation.setGuests(request.guests());
        reservation.setContactName(request.contactName().trim());
        reservation.setContactEmail(normalizeEmail(request.contactEmail()));
        reservation.setContactPhone(blankToNull(request.contactPhone()));
        reservation.setNotes(blankToNull(request.notes()));
        reservation.setSource(ReservationSource.WALK_IN);
        reservation.setStatus(ReservationStatus.SEATED);
        linkCustomer(tenantId, reservation);
        Reservation saved = booking.book(reservation);
        if (saved.getDepositAmount().signum() > 0) {
            saved.setDepositPaidAt(now);
            saved.setPrepaidMethod(request.paymentMethod());
        }
        saved.setTableToken(SecureTokens.randomToken(32));
        saved.setTableTokenActive(true);
        auditService.record(AuditAction.RESERVATION_CREATED, "Reservation", saved.getId(), saved.getReference()
                + " taquilla mesa=" + table.getName() + " cobrado=" + saved.getDepositAmount() + " "
                + request.paymentMethod());
        if (saved.getDepositAmount().signum() > 0) {
            eventPublisher.publishEvent(new ReservationEmailEvent(saved.getId(), ReservationEmailEvent.Kind.PAID));
        }
        return toResponse(saved);
    }

    /** Si el email es de un cliente del CRM, enlazamos la reserva a su ficha (y a su cuenta web). */
    private void linkCustomer(UUID tenantId, Reservation reservation) {
        if (reservation.getContactEmail() == null) {
            return;
        }
        customerService.findByEmail(tenantId, reservation.getContactEmail()).ifPresent(customer -> {
            reservation.setCustomerId(customer.getId());
            reservation.setCustomerUserId(customer.getUserId());
        });
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }

    @Transactional
    public ReservationResponse changeStatus(UUID id, ChangeReservationStatusRequest request) {
        Reservation reservation = findInMyTenant(id);
        ReservationStatus next = request.status();
        if (!reservation.getStatus().canChangeTo(next)) {
            throw new BadRequestException("No se puede pasar una reserva de " + reservation.getStatus() + " a " + next);
        }
        if (next == ReservationStatus.SEATED && !reservation.isPrepaid()) {
            // Regla de negocio: la reserva y el paquete inicial se pagan ANTES de entrar.
            throw new BadRequestException("Falta registrar el pago de la reserva y el paquete ("
                    + reservation.getDepositAmount() + " EUR) antes de dar acceso a la mesa");
        }
        if (next == ReservationStatus.COMPLETED) {
            throw new BadRequestException("Para terminar una reserva, cierra la cuenta de la mesa en su ficha VIP");
        }
        reservation.setStatus(next);
        if (next == ReservationStatus.CANCELLED) {
            reservation.setCancelledAt(Instant.now(clock));
        }
        if (next == ReservationStatus.SEATED && reservation.getTableToken() == null) {
            // Al sentarse, la mesa recibe su QR para pedir desde el movil.
            reservation.setTableToken(SecureTokens.randomToken(32));
            reservation.setTableTokenActive(true);
        }
        if (next == ReservationStatus.CANCELLED || next == ReservationStatus.NO_SHOW) {
            reservation.setTableTokenActive(false);
        }
        auditService.record(AuditAction.RESERVATION_STATUS_CHANGED, "Reservation", id,
                reservation.getReference() + " -> " + next);
        if (next == ReservationStatus.CONFIRMED || next == ReservationStatus.CANCELLED) {
            eventPublisher.publishEvent(new ReservationEmailEvent(id, next == ReservationStatus.CONFIRMED
                    ? ReservationEmailEvent.Kind.CONFIRMED : ReservationEmailEvent.Kind.CANCELLED));
        }
        return toResponse(reservation);
    }

    /** Registra que el cliente ha abonado el anticipo (en el local, por transferencia...). */
    @Transactional
    public ReservationResponse registerDeposit(UUID id, com.bloop.pos.entity.PaymentMethod method) {
        Reservation reservation = findInMyTenant(id);
        if (reservation.getDepositAmount().signum() == 0) {
            throw new BadRequestException("Esta reserva no requiere anticipo");
        }
        if (reservation.getDepositPaidAt() != null) {
            throw new ConflictException("El anticipo ya estaba registrado");
        }
        if (!ReservationStatus.ACTIVE.contains(reservation.getStatus())) {
            throw new BadRequestException("La reserva ya no esta activa");
        }
        reservation.setDepositPaidAt(Instant.now(clock));
        reservation.setPrepaidMethod(method);
        auditService.record(AuditAction.RESERVATION_DEPOSIT_PAID, "Reservation", id,
                reservation.getReference() + " anticipo=" + reservation.getDepositAmount());
        eventPublisher.publishEvent(new ReservationEmailEvent(id, ReservationEmailEvent.Kind.PAID));
        return toResponse(reservation);
    }

    // ----------------------------------------------------------------- Auxiliares

    /**
     * Las mesas se reservan para una fiesta completa (una sesion de un evento del local), nunca por
     * horas: el horario de la reserva es siempre el de la fiesta.
     */
    private Interval resolveInterval(UUID venueId, UUID sessionId) {
        if (sessionId == null) {
            throw new BadRequestException("Elige la fiesta: las mesas se reservan para la fiesta completa");
        }
        EventSession session = sessionRepository.findByIdAndTenantId(sessionId, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Fiesta"));
        if (!session.getEvent().getVenue().getId().equals(venueId)) {
            throw new BadRequestException("La fiesta es de otro local");
        }
        return new Interval(session, session.getStartsAt(), session.getEndsAt());
    }

    private record Interval(EventSession session, Instant startsAt, Instant endsAt) {
    }

    private Reservation findInMyTenant(UUID id) {
        return reservationRepository.findByIdAndTenantId(id, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Reserva"));
    }

    private ReservationResponse toResponse(Reservation r) {
        String venueName = venueRepository.findById(r.getVenueId()).map(Venue::getName).orElse("");
        return reservationMapper.toResponse(r, venueName);
    }

    private Map<UUID, String> venueNames(UUID tenantId) {
        return venueRepository.findByTenantIdOrderByName(tenantId).stream()
                .collect(Collectors.toMap(Venue::getId, Venue::getName));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
