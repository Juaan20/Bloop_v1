package com.bloop.customers.entity;

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

/** Un cambio de consentimiento (dado o retirado). Es un historial: nunca se modifica. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customer_consents")
public class CustomerConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentType type;

    @Column(nullable = false)
    private boolean granted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
