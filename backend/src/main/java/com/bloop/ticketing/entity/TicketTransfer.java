package com.bloop.ticketing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Historial de cambios de titular de una entrada (no se modifica nunca). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ticket_transfers")
public class TicketTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

    @Column(name = "from_user_id", updatable = false)
    private UUID fromUserId;

    @Column(name = "to_user_id", updatable = false)
    private UUID toUserId;

    @Column(name = "from_holder_name", nullable = false, updatable = false)
    private String fromHolderName;

    @Column(name = "to_holder_name", nullable = false, updatable = false)
    private String toHolderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransferType type;

    /** Usuario que hizo el cambio (el propietario o un empleado). */
    @Column(name = "performed_by", updatable = false)
    private UUID performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
