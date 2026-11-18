package com.bloop.notifications.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Aviso interno para el equipo de una empresa (campana del backoffice).
 * Se inserta con AdminAlertRepository.insertIfAbsent, por eso el id no se genera aqui.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "admin_alerts")
public class AdminAlert {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AlertType type;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(nullable = false, updatable = false)
    private String message;

    @Column(updatable = false)
    private String link;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
