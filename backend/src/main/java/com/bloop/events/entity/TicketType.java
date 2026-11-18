package com.bloop.events.entity;

import com.bloop.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tipo de entrada de una sesion (General, VIP...), con precio, stock y ventana de venta.
 *
 * IMPORTANTE: quantitySold NUNCA se modifica con setters al vender. Se actualiza con un
 * UPDATE condicional atomico (ver TicketTypeRepository#reserveStock) para evitar sobreventa
 * cuando varios clientes compran a la vez.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ticket_types")
public class TicketType extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private EventSession session;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketCategory category;

    /** Importe con 2 decimales. Siempre BigDecimal para dinero (nunca double). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(name = "quantity_total", nullable = false)
    private int quantityTotal;

    @Column(name = "quantity_sold", nullable = false)
    private int quantitySold;

    @Column(name = "max_per_order", nullable = false)
    private int maxPerOrder = 10;

    @Column(name = "sales_start_at")
    private Instant salesStartAt;

    @Column(name = "sales_end_at")
    private Instant salesEndAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public int getAvailable() {
        return Math.max(0, quantityTotal - quantitySold);
    }

    /** Calcula si este tipo se puede comprar ahora mismo. */
    public SaleStatus saleStatus(Instant now) {
        if (!active) {
            return SaleStatus.INACTIVE;
        }
        if (session.isSalesPaused()) {
            return SaleStatus.PAUSED;
        }
        if (salesStartAt != null && now.isBefore(salesStartAt)) {
            return SaleStatus.NOT_STARTED;
        }
        Instant end = salesEndAt != null ? salesEndAt : session.getEndsAt();
        if (!now.isBefore(end)) {
            return SaleStatus.ENDED;
        }
        if (getAvailable() == 0) {
            return SaleStatus.SOLD_OUT;
        }
        return SaleStatus.ON_SALE;
    }
}
