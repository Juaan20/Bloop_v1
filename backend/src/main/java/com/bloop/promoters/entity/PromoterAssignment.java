package com.bloop.promoters.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Evento asignado a un promotor, con su objetivo de entradas (opcional). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class PromoterAssignment {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "target_tickets")
    private Integer targetTickets;
}
