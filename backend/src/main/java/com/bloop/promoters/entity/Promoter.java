package com.bloop.promoters.entity;

import com.bloop.shared.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Promotor / RRPP: un empleado con rol PROMOTER, su codigo de enlace de venta,
 * su comision y los eventos que tiene asignados (con su objetivo de ventas).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promoters")
public class Promoter extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Codigo del enlace de venta (en mayusculas): /eventos/{slug}?rrpp=CODIGO */
    @Column(name = "referral_code", nullable = false, unique = true)
    private String referralCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_type", nullable = false)
    private CommissionType commissionType;

    @Column(name = "commission_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal commissionValue;

    @Column(nullable = false)
    private boolean active = true;

    /** Eventos asignados (tabla promoter_events). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "promoter_events", joinColumns = @JoinColumn(name = "promoter_id"))
    private List<PromoterAssignment> assignments = new ArrayList<>();

    public boolean isAssignedTo(UUID eventId) {
        return assignments.stream().anyMatch(a -> a.getEventId().equals(eventId));
    }

    /**
     * Comision de una venta.
     * PERCENTAGE: porcentaje del importe pagado. FIXED_PER_TICKET: importe fijo por entrada.
     */
    public BigDecimal commissionFor(BigDecimal orderTotal, int tickets) {
        BigDecimal commission = switch (commissionType) {
            case PERCENTAGE -> orderTotal.multiply(commissionValue).divide(BigDecimal.valueOf(100), 2,
                    RoundingMode.HALF_UP);
            case FIXED_PER_TICKET -> commissionValue.multiply(BigDecimal.valueOf(tickets));
        };
        return commission.setScale(2, RoundingMode.HALF_UP);
    }
}
