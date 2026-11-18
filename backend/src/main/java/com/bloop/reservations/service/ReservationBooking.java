package com.bloop.reservations.service;

import com.bloop.reservations.entity.ReservationPackageItem;
import com.bloop.reservations.mapper.ReservationMapper;

import com.bloop.reservations.dto.response.TableAvailabilityResponse;
import com.bloop.reservations.entity.Reservation;
import com.bloop.reservations.entity.ReservationStatus;
import com.bloop.reservations.entity.VipTable;
import com.bloop.reservations.mapper.ReservationMapper;
import com.bloop.reservations.repository.ReservationRepository;
import com.bloop.reservations.repository.VipZoneRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.util.SecureTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Logica comun de las reservas que usan el backoffice (ReservationService) y la web
 * (CustomerReservationService): validar la mesa, evitar solapamientos y calcular la disponibilidad.
 */
@Component
@RequiredArgsConstructor
public class ReservationBooking {

    static final Duration MAX_LENGTH = Duration.ofHours(24);
    static final String OVERLAP_MESSAGE = "La mesa ya esta reservada en ese horario";

    private final ReservationRepository reservationRepository;
    private final VipZoneRepository zoneRepository;
    private final ReservationMapper reservationMapper;

    /**
     * Valida y guarda una reserva nueva (ya rellenada con mesa, horario, personas y contacto).
     * Copia el consumo minimo y el anticipo de la mesa ("congelados" en la reserva).
     *
     * Solapamientos, en dos niveles:
     * 1. existsOverlap da un mensaje claro en el caso normal.
     * 2. Si dos peticiones llegan a la vez, ambas pasan el paso 1, pero la restriccion EXCLUDE de
     *    PostgreSQL rechaza la segunda al hacer flush: la convertimos en el mismo 409.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Reservation book(Reservation reservation) {
        VipTable table = reservation.getTable();
        if (!table.isBookable()) {
            throw new BadRequestException("La mesa no esta disponible para reservas");
        }
        // El plano es la unica fuente de verdad: solo se vende lo que el administrador ha colocado en el.
        if (!table.isOnPlan()) {
            throw new BadRequestException("La mesa " + table.getName() + " no esta colocada en el plano de su sala");
        }
        if (reservation.getGuests() > table.getCapacity()) {
            throw new BadRequestException("La mesa " + table.getName() + " admite como maximo " + table.getCapacity()
                    + " personas");
        }
        if (!reservation.getEndsAt().isAfter(reservation.getStartsAt())) {
            throw new BadRequestException("La hora de fin debe ser posterior a la de inicio");
        }
        if (Duration.between(reservation.getStartsAt(), reservation.getEndsAt()).compareTo(MAX_LENGTH) > 0) {
            throw new BadRequestException("Una reserva no puede durar mas de 24 horas");
        }
        if (reservationRepository.existsOverlap(table.getId(), reservation.getStartsAt(), reservation.getEndsAt(),
                ReservationStatus.ACTIVE)) {
            throw new ConflictException(OVERLAP_MESSAGE);
        }
        reservation.setReference(newReference());
        reservation.setMinSpend(table.getMinSpend());
        reservation.setDepositAmount(table.getDepositAmount());
        copyPackage(reservation, table);
        try {
            return reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException e) {
            // exclusion_violation de ex_reservations_no_overlap: otra reserva se adelanto.
            throw new ConflictException(OVERLAP_MESSAGE);
        }
    }

    /** Mesas activas de un local y si estan libres en [startsAt, endsAt). */
    @Transactional(readOnly = true)
    public List<TableAvailabilityResponse> availability(UUID venueId, Instant startsAt, Instant endsAt) {
        Set<UUID> busy = new HashSet<>(reservationRepository.findBusyTableIds(venueId, startsAt, endsAt,
                ReservationStatus.ACTIVE));
        return zoneRepository.findByVenueIdAndActiveTrueOrderBySortOrderAscNameAsc(venueId).stream()
                .flatMap(zone -> zone.getTables().stream())
                .filter(VipTable::isActive)
                .filter(VipTable::isOnPlan)
                .map(table -> reservationMapper.toAvailability(table, !busy.contains(table.getId())))
                .toList();
    }

    private String newReference() {
        String reference;
        do {
            reference = "VR-" + SecureTokens.randomReference(6);
        } while (reservationRepository.existsByReference(reference));
        return reference;
    }

    /** Congela en la reserva el paquete incluido de la mesa (lo que el cliente paga por adelantado). */
    private static void copyPackage(Reservation reservation, VipTable table) {
        for (var item : table.getPackageItems()) {
            ReservationPackageItem copy = new ReservationPackageItem();
            copy.setTenantId(table.getTenantId());
            copy.setReservation(reservation);
            copy.setVariantId(item.getVariant().getId());
            copy.setProductName(ReservationMapper.variantLabel(item.getVariant()));
            copy.setQuantity(item.getQuantity());
            reservation.getPackageItems().add(copy);
        }
    }
}
