package com.bloop.pos.entity;

import com.bloop.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Turno de caja de un terminal: se abre con un fondo inicial y se cierra con el arqueo
 * (efectivo contado frente al esperado). Solo puede haber UNA caja abierta por terminal
 * (indice unico parcial ux_cash_sessions_one_open).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cash_sessions")
public class CashSession extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "terminal_id", nullable = false, updatable = false)
    private UUID terminalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CashSessionStatus status = CashSessionStatus.OPEN;

    @Column(name = "opened_by", nullable = false, updatable = false)
    private UUID openedBy;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    /** Efectivo con el que empieza la caja (cambio). */
    @Column(name = "opening_float", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal openingFloat;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Fondo + ventas en efectivo + entradas - salidas de efectivo (calculado al cerrar). */
    @Column(name = "expected_cash", precision = 10, scale = 2)
    private BigDecimal expectedCash;

    @Column(name = "counted_cash", precision = 10, scale = 2)
    private BigDecimal countedCash;

    /** countedCash - expectedCash: negativo = falta dinero, positivo = sobra. */
    @Column(precision = 10, scale = 2)
    private BigDecimal difference;

    @Column(name = "closing_notes")
    private String closingNotes;

    public boolean isOpen() {
        return status == CashSessionStatus.OPEN;
    }
}
