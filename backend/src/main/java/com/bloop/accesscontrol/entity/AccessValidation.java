package com.bloop.accesscontrol.entity;

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

/** Registro de cada escaneo (aceptado o rechazado): historial de accesos. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "access_validations")
public class AccessValidation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /** Null si el QR no corresponde a ninguna entrada. */
    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "validated_by", nullable = false)
    private UUID validatedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ValidationResult result;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
