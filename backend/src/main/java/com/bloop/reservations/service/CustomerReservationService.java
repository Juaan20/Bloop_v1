package com.bloop.reservations.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.customers.entity.Customer;
import com.bloop.customers.service.CustomerService;
import com.bloop.events.entity.EventSession;
import com.bloop.events.entity.EventStatus;
import com.bloop.events.repository.EventSessionRepository;
import com.bloop.notifications.entity.AlertType;
import com.bloop.notifications.service.AdminAlertService;
import com.bloop.reservations.dto.request.RequestReservationRequest;
import com.bloop.reservations.dto.response.ReservationResponse;
import com.bloop.reservations.dto.response.TableAvailabilityResponse;
import com.bloop.reservations.entity.Reservation;
import com.bloop.reservations.entity.ReservationSource;
import com.bloop.reservations.entity.ReservationStatus;
import com.bloop.reservations.entity.VipTable;
import com.bloop.reservations.mapper.ReservationMapper;
import com.bloop.reservations.repository.ReservationRepository;
import com.bloop.reservations.repository.VipTableRepository;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import com.bloop.venues.entity.VenueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Reservas VIP desde la web publica: el cliente ve las mesas libres de una sesion,
 * solicita una (queda PENDIENTE hasta que el local la confirma), consulta y cancela las suyas.
 */
@Service
@RequiredArgsConstructor
public class CustomerReservationService {

    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.of("Europe/Madrid"));

    private final ReservationRepository reservationRepository;
    private final VipTableRepository tableRepository;
    private final EventSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final CustomerService customerService;
    private final AdminAlertService alertService;
    private final ReservationBooking booking;
    private final ReservationMapper reservationMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** Mesas VIP del local de la sesion y si estan libres (sin autenticacion). */
    @Transactional(readOnly = true)
    public List<TableAvailabilityResponse> tablesForSession(UUID sessionId) {
        EventSession session = findBookableSession(sessionId);
        return booking.availability(session.getEvent().getVenue().getId(), session.getStartsAt(),
                session.getEndsAt());
    }

    @Transactional
    public ReservationResponse request(RequestReservationRequest request) {
        EventSession session = findBookableSession(request.sessionId());
        UUID venueId = session.getEvent().getVenue().getId();
        VipTable table = tableRepository.findWithZoneById(request.tableId())
                .filter(t -> t.getZone().getVenueId().equals(venueId))
                .orElseThrow(() -> NotFoundException.of("Mesa"));
        User user = userRepository.findById(currentUserProvider.get().userId())
                .orElseThrow(() -> NotFoundException.of("Usuario"));
        Customer customer = customerService.ensureCustomer(session.getTenantId(), user.getId());

        Reservation reservation = new Reservation();
        reservation.setTenantId(session.getTenantId());
        reservation.setVenueId(venueId);
        reservation.setTable(table);
        reservation.setSession(session);
        reservation.setStartsAt(session.getStartsAt());
        reservation.setEndsAt(session.getEndsAt());
        reservation.setGuests(request.guests());
        reservation.setCustomerId(customer.getId());
        reservation.setCustomerUserId(user.getId());
        reservation.setContactName(user.getFullName());
        reservation.setContactEmail(user.getEmail());
        reservation.setContactPhone(request.phone() == null || request.phone().isBlank() ? user.getPhone()
                : request.phone().trim());
        reservation.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setSource(ReservationSource.WEB);

        Reservation saved = booking.book(reservation);
        alertService.raise(saved.getTenantId(), AlertType.RESERVATION_REQUESTED, "Nueva solicitud de reserva VIP",
                saved.getContactName() + " pide la mesa " + table.getName() + " para " + saved.getGuests()
                        + " personas (" + session.getEvent().getName() + ", "
                        + SHORT_DATE.format(saved.getStartsAt()) + ").",
                "/admin/vip", saved.getId());
        auditService.recordInTenant(saved.getTenantId(), AuditAction.RESERVATION_CREATED, "Reservation",
                saved.getId(), saved.getReference() + " (web)");
        eventPublisher.publishEvent(new ReservationEmailEvent(saved.getId(), ReservationEmailEvent.Kind.RECEIVED));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> mine() {
        return reservationRepository.findAllOfCustomerUser(currentUserProvider.get().userId()).stream()
                .map(this::toResponse).toList();
    }

    /** El cliente cancela su reserva (solo si aun no ha empezado y esta pendiente o confirmada). */
    @Transactional
    public ReservationResponse cancelMine(UUID id) {
        Reservation reservation = reservationRepository.findOfCustomer(id, currentUserProvider.get().userId())
                .orElseThrow(() -> NotFoundException.of("Reserva"));
        Instant now = Instant.now(clock);
        boolean cancellable = (reservation.getStatus() == ReservationStatus.PENDING
                || reservation.getStatus() == ReservationStatus.CONFIRMED) && reservation.getStartsAt().isAfter(now);
        if (!cancellable) {
            throw new ConflictException("Esta reserva ya no se puede cancelar");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(now);
        alertService.raise(reservation.getTenantId(), AlertType.RESERVATION_CANCELLED, "Reserva cancelada por el cliente",
                reservation.getContactName() + " ha cancelado la reserva " + reservation.getReference() + " (mesa "
                        + reservation.getTable().getName() + ").",
                "/admin/vip", reservation.getId());
        auditService.recordInTenant(reservation.getTenantId(), AuditAction.RESERVATION_STATUS_CHANGED, "Reservation",
                id, reservation.getReference() + " -> CANCELLED (cliente)");
        eventPublisher.publishEvent(new ReservationEmailEvent(id, ReservationEmailEvent.Kind.CANCELLED));
        return toResponse(reservation);
    }

    /** Sesion publicada, de un local activo y que aun no ha terminado (si no, 404). */
    private EventSession findBookableSession(UUID sessionId) {
        Instant now = Instant.now(clock);
        return sessionRepository.findWithEventById(sessionId)
                .filter(s -> s.getEvent().getStatus() == EventStatus.PUBLISHED
                        && s.getEvent().getVenue().getStatus() == VenueStatus.ACTIVE
                        && s.isUpcoming(now))
                .orElseThrow(() -> NotFoundException.of("Sesion"));
    }

    private ReservationResponse toResponse(Reservation r) {
        String venueName = r.getSession() != null ? r.getSession().getEvent().getVenue().getName() : "";
        return reservationMapper.toResponse(r, venueName);
    }
}
