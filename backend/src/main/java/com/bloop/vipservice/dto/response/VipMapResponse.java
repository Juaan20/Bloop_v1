package com.bloop.vipservice.dto.response;

import com.bloop.reservations.entity.ReservationStatus;
import com.bloop.reservations.entity.VipTableShape;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Plano en vivo de un local en un momento dado. */
/** Plano de un local para una fiesta (sessionId) con el estado de cada mesa. */
public record VipMapResponse(UUID venueId, Instant at, List<Zone> zones, List<Table> tables, UUID sessionId) {

    /** Fiesta del selector: nombre del evento, titulo del dia y fechas. */
    public record Party(UUID sessionId, UUID eventId, String eventName, String title, Instant startsAt,
                        Instant endsAt, boolean now) {
    }

    /** Reserva anterior de la mesa en la misma fiesta (ya liberada, cancelada o no presentada). */
    public record PastBooking(UUID id, String reference, String contactName, int guests, ReservationStatus status,
                              Instant closedAt) {
    }

    /** Sala del local con su forma (layout null = sin disenar). */
    public record Zone(UUID id, String name, boolean active, String description, Integer capacity, String color,
                       com.bloop.reservations.dto.request.FloorPlanRequest layout) {
    }

    public record Table(UUID id, UUID zoneId, String zoneName, String name, int capacity, BigDecimal price,
                        BigDecimal minSpend, boolean active, BigDecimal posX, BigDecimal posY, BigDecimal width,
                        BigDecimal height, VipTableShape shape, VipTableStatus status, Booking reservation,
                        boolean onPlan, List<PastBooking> history) {
    }

    /** Reserva que ocupa la mesa en ese momento (solo el nombre de contacto, sin email ni telefono). */
    public record Booking(UUID id, String reference, String contactName, int guests, Instant startsAt, Instant endsAt,
                          ReservationStatus status, String eventName, BigDecimal prepaidAmount, boolean prepaid,
                          BigDecimal extrasTotal, BigDecimal pendingTotal, long openOrders) {
    }
}
