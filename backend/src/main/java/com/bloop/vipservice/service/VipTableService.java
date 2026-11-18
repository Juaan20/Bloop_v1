package com.bloop.vipservice.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.events.entity.EventSession;
import com.bloop.reservations.entity.Reservation;
import com.bloop.reservations.entity.ReservationStatus;
import com.bloop.reservations.entity.VipTable;
import com.bloop.reservations.entity.VipZone;
import com.bloop.reservations.mapper.ReservationMapper;
import com.bloop.reservations.repository.ReservationRepository;
import com.bloop.reservations.repository.VipZoneRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.shared.util.SecureTokens;
import com.bloop.venues.entity.Venue;
import com.bloop.venues.repository.VenueRepository;
import com.bloop.venues.service.VenueService;
import com.bloop.vipservice.dto.request.VipAccountPaymentRequest;
import com.bloop.vipservice.dto.response.VipAccountResponse;
import com.bloop.vipservice.dto.response.VipMapResponse;
import com.bloop.vipservice.dto.response.VipReservationDetailResponse;
import com.bloop.vipservice.dto.response.VipTableStatus;
import com.bloop.vipservice.entity.VipAccountPayment;
import com.bloop.vipservice.entity.VipOrder;
import com.bloop.vipservice.mapper.VipServiceMapper;
import com.bloop.vipservice.repository.VipAccountPaymentRepository;
import com.bloop.vipservice.repository.VipOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mesas VIP en servicio: plano en vivo, ficha de la reserva, QR de la mesa y cuenta
 * (cobro de consumiciones adicionales y cierre).
 */
@Service
@RequiredArgsConstructor
public class VipTableService {

    /** Bytes aleatorios del token del QR (256 bits): imposible de adivinar. */
    private static final int TOKEN_BYTES = 32;

    private final com.bloop.events.repository.EventSessionRepository sessionRepository;
    private final com.bloop.reservations.service.FloorPlanService floorPlans;
    private final ReservationRepository reservationRepository;
    private final VipZoneRepository zoneRepository;
    private final VipOrderRepository orderRepository;
    private final VipAccountPaymentRepository paymentRepository;
    private final VenueService venueService;
    private final VenueRepository venueRepository;
    private final ReservationMapper reservationMapper;
    private final VipServiceMapper vipMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final Clock clock;

    // ----------------------------------------------------------------- Plano en vivo

    /**
     * Plano de un local para una fiesta: cada mesa del plano con su reserva activa de esa fiesta
     * (si la tiene) y las reservas anteriores ya liberadas. Sin sessionId se usa la fiesta en curso
     * (o ninguna: todas las mesas libres).
     */
    @Transactional(readOnly = true)
    public VipMapResponse map(UUID venueId, UUID sessionId) {
        Venue venue = venueService.findInMyTenant(venueId);
        Instant now = Instant.now(clock);
        EventSession party = sessionId != null ? findParty(venue, sessionId)
                : sessionRepository.findPartiesOfVenue(venue.getTenantId(), venue.getId(), now).stream()
                        .filter(s -> !s.getStartsAt().isAfter(now)).findFirst().orElse(null);
        List<VipZone> zones = zoneRepository.findByVenueIdAndTenantIdOrderBySortOrderAscNameAsc(venue.getId(),
                venue.getTenantId());
        List<Reservation> all = party == null ? List.of()
                : reservationRepository.findBySessionInVenue(venue.getId(), venue.getTenantId(), party.getId());
        List<Reservation> active = all.stream().filter(r -> ReservationStatus.ACTIVE.contains(r.getStatus())).toList();
        // Si una mesa tuviera varias activas (no deberia: EXCLUDE), manda la que ya esta sentada.
        Map<UUID, Reservation> byTable = active.stream().collect(Collectors.toMap(r -> r.getTable().getId(),
                Function.identity(), (a, b) -> a.getStatus() == ReservationStatus.SEATED ? a : b));
        Map<UUID, List<VipMapResponse.PastBooking>> history = all.stream()
                .filter(r -> !ReservationStatus.ACTIVE.contains(r.getStatus()))
                .collect(Collectors.groupingBy(r -> r.getTable().getId(), Collectors.mapping(r ->
                        new VipMapResponse.PastBooking(r.getId(), r.getReference(), r.getContactName(), r.getGuests(),
                                r.getStatus(), r.getAccountClosedAt()), Collectors.toList())));
        Map<UUID, List<VipOrder>> orders = active.isEmpty() ? Map.of()
                : orderRepository.findByReservations(byTable.values().stream().map(Reservation::getId).toList())
                        .stream().collect(Collectors.groupingBy(o -> o.getReservation().getId()));

        var zoneDtos = zones.stream().map(z -> new VipMapResponse.Zone(z.getId(), z.getName(), z.isActive(),
                z.getDescription(), z.getCapacity(), z.getColor(), floorPlans.parse(z.getLayout()))).toList();
        var tables = zones.stream().flatMap(z -> z.getTables().stream())
                .sorted(Comparator.comparing(VipTable::getName))
                .map(t -> toMapTable(t, byTable.get(t.getId()), orders, history.getOrDefault(t.getId(), List.of())))
                .toList();
        return new VipMapResponse(venue.getId(), party == null ? now : party.getStartsAt(), zoneDtos, tables,
                party == null ? null : party.getId());
    }

    /** Fiestas del local: la que esta en curso, las proximas y (con past=true) las de los ultimos 30 dias. */
    @Transactional(readOnly = true)
    public List<VipMapResponse.Party> parties(UUID venueId, boolean past) {
        Venue venue = venueService.findInMyTenant(venueId);
        Instant now = Instant.now(clock);
        Instant from = past ? now.minus(java.time.Duration.ofDays(30)) : now;
        return sessionRepository.findPartiesOfVenue(venue.getTenantId(), venue.getId(), from).stream()
                .map(s -> new VipMapResponse.Party(s.getId(), s.getEvent().getId(), s.getEvent().getName(),
                        s.getTitle(), s.getStartsAt(), s.getEndsAt(),
                        !s.getStartsAt().isAfter(now) && s.getEndsAt().isAfter(now)))
                .toList();
    }

    private EventSession findParty(Venue venue, UUID sessionId) {
        EventSession s = sessionRepository.findByIdAndTenantId(sessionId, venue.getTenantId())
                .orElseThrow(() -> NotFoundException.of("Fiesta"));
        if (!s.getEvent().getVenue().getId().equals(venue.getId())) {
            throw new BadRequestException("La fiesta es de otro local");
        }
        return s;
    }

    private VipMapResponse.Table toMapTable(VipTable t, Reservation r, Map<UUID, List<VipOrder>> ordersByRes,
                                           List<VipMapResponse.PastBooking> history) {
        VipMapResponse.Booking booking = null;
        VipTableStatus status;
        if (r == null) {
            status = t.isBookable() && t.isOnPlan() ? VipTableStatus.AVAILABLE : VipTableStatus.INACTIVE;
        } else {
            List<VipOrder> orders = ordersByRes.getOrDefault(r.getId(), List.of());
            long open = VipAccountCalculator.openOrders(orders);
            BigDecimal extras = VipAccountCalculator.extrasTotal(orders);
            BigDecimal pending = extras.subtract(paymentRepository.sumByReservation(r.getId())).max(BigDecimal.ZERO);
            status = statusOf(r, open);
            booking = new VipMapResponse.Booking(r.getId(), r.getReference(), r.getContactName(), r.getGuests(),
                    r.getStartsAt(), r.getEndsAt(), r.getStatus(),
                    r.getSession() == null ? null : r.getSession().getEvent().getName(), r.getDepositAmount(),
                    r.isPrepaid(), extras, pending, open);
        }
        return new VipMapResponse.Table(t.getId(), t.getZone().getId(), t.getZone().getName(), t.getName(),
                t.getCapacity(), t.getDepositAmount(), t.getMinSpend(), t.isBookable(), t.getPosX(), t.getPosY(),
                t.getWidth(), t.getHeight(), t.getShape(), status, booking, t.isOnPlan(), history);
    }

    /** Regla de colores del plano. Publica para poder probarla. */
    public static VipTableStatus statusOf(Reservation r, long openOrders) {
        if (r.getStatus() == ReservationStatus.SEATED) {
            return openOrders > 0 ? VipTableStatus.SERVICE_REQUESTED : VipTableStatus.OCCUPIED;
        }
        if (r.isDepositPending()) {
            return VipTableStatus.PENDING_PAYMENT;
        }
        return r.getDepositAmount().signum() > 0 ? VipTableStatus.PAID : VipTableStatus.RESERVED;
    }

    // ----------------------------------------------------------------- Ficha VIP de la reserva

    @Transactional(readOnly = true)
    public VipReservationDetailResponse detail(UUID reservationId) {
        Reservation r = findInMyTenant(reservationId);
        return toDetail(r);
    }

    /** Genera (o regenera) el QR de la mesa. Regenerarlo invalida el anterior. */
    @Transactional
    public VipReservationDetailResponse generateQr(UUID reservationId) {
        Reservation r = findInMyTenant(reservationId);
        if (r.getStatus() != ReservationStatus.CONFIRMED && r.getStatus() != ReservationStatus.SEATED) {
            throw new BadRequestException("Solo las reservas confirmadas o en la mesa tienen QR");
        }
        r.setTableToken(SecureTokens.randomToken(TOKEN_BYTES));
        r.setTableTokenActive(true);
        auditService.record(AuditAction.VIP_TABLE_QR_CHANGED, "Reservation", reservationId,
                r.getReference() + ": QR generado");
        return toDetail(r);
    }

    @Transactional
    public VipReservationDetailResponse deactivateQr(UUID reservationId) {
        Reservation r = findInMyTenant(reservationId);
        r.setTableTokenActive(false);
        auditService.record(AuditAction.VIP_TABLE_QR_CHANGED, "Reservation", reservationId,
                r.getReference() + ": QR desactivado");
        return toDetail(r);
    }

    // ----------------------------------------------------------------- Cuenta de la mesa

    /** Registra un cobro de consumiciones adicionales. Nunca mas de lo pendiente. Idempotente por clave. */
    @Transactional
    public VipReservationDetailResponse pay(UUID reservationId, VipAccountPaymentRequest request) {
        UUID tenantId = currentUserProvider.requireTenantId();
        Reservation r = reservationRepository.findByIdForUpdate(reservationId, tenantId)
                .orElseThrow(() -> NotFoundException.of("Reserva"));
        if (paymentRepository.findByReservationIdAndIdempotencyKey(r.getId(), request.idempotencyKey()).isPresent()) {
            return detail(reservationId); // reintento: el cobro ya estaba registrado
        }
        if (r.getAccountClosedAt() != null) {
            throw new BadRequestException("La cuenta de esta mesa ya esta cerrada");
        }
        List<VipOrder> orders = orderRepository.findByReservation(r.getId());
        BigDecimal pending = VipAccountCalculator.extrasTotal(orders)
                .subtract(paymentRepository.sumByReservation(r.getId()));
        if (request.amount().compareTo(pending) > 0) {
            throw new BadRequestException("El importe supera lo pendiente de la mesa (" + pending.max(BigDecimal.ZERO)
                    + " EUR)");
        }
        VipAccountPayment p = new VipAccountPayment();
        p.setTenantId(tenantId);
        p.setReservationId(r.getId());
        p.setAmount(request.amount());
        p.setMethod(request.method());
        p.setIdempotencyKey(request.idempotencyKey());
        p.setCreatedById(currentUserProvider.get().userId());
        paymentRepository.save(p);
        auditService.record(AuditAction.VIP_ACCOUNT_PAYMENT, "Reservation", r.getId(),
                r.getReference() + " cobro " + request.amount() + " " + request.method());
        return detail(reservationId);
    }

    /**
     * Cierra la cuenta: solo si el grupo esta en la mesa, pago su reserva, no hay pedidos sin entregar
     * y no queda nada pendiente. La reserva pasa a COMPLETED y el QR deja de funcionar.
     */
    @Transactional
    public VipReservationDetailResponse close(UUID reservationId) {
        UUID tenantId = currentUserProvider.requireTenantId();
        Reservation locked = reservationRepository.findByIdForUpdate(reservationId, tenantId)
                .orElseThrow(() -> NotFoundException.of("Reserva"));
        Reservation r = findInMyTenant(locked.getId());
        List<VipOrder> orders = orderRepository.findByReservation(r.getId());
        VipAccountResponse account = VipAccountCalculator.account(r, orders,
                paymentRepository.findByReservationIdOrderByCreatedAt(r.getId()));
        if (account.closed()) {
            throw new BadRequestException("La cuenta ya estaba cerrada");
        }
        if (r.getStatus() != ReservationStatus.SEATED) {
            throw new BadRequestException("Solo se cierra la cuenta de un grupo que esta en la mesa");
        }
        if (account.openOrders() > 0) {
            throw new BadRequestException("Hay pedidos sin entregar o cancelar en esta mesa");
        }
        if (account.pendingTotal().signum() > 0) {
            throw new BadRequestException("Quedan " + account.pendingTotal() + " EUR pendientes de cobro");
        }
        r.setStatus(ReservationStatus.COMPLETED);
        r.setAccountClosedAt(Instant.now(clock));
        r.setTableTokenActive(false);
        auditService.record(AuditAction.VIP_ACCOUNT_CLOSED, "Reservation", r.getId(), r.getReference()
                + " cuenta cerrada, consumiciones " + account.extrasTotal());
        return toDetail(r);
    }

    // ----------------------------------------------------------------- Auxiliares

    private VipReservationDetailResponse toDetail(Reservation r) {
        List<VipOrder> orders = orderRepository.findByReservation(r.getId());
        VipAccountResponse account = VipAccountCalculator.account(r, orders,
                paymentRepository.findByReservationIdOrderByCreatedAt(r.getId()));
        String venueName = venueRepository.findById(r.getVenueId()).map(Venue::getName).orElse("");
        return new VipReservationDetailResponse(reservationMapper.toResponse(r, venueName), account,
                orders.stream().map(vipMapper::toResponse).toList(),
                new VipReservationDetailResponse.TableQr(r.getTableToken(), r.isTableTokenActive()));
    }

    private Reservation findInMyTenant(UUID id) {
        return reservationRepository.findByIdAndTenantId(id, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Reserva"));
    }
}
