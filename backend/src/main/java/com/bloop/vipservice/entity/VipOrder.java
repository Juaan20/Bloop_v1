package com.bloop.vipservice.entity;

import com.bloop.reservations.entity.Reservation;
import com.bloop.shared.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pedido de botellas/productos de una mesa VIP, preparado y llevado por el personal de servicio. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vip_orders")
public class VipOrder extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "venue_id", nullable = false, updatable = false)
    private UUID venueId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private Reservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private VipOrderSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VipOrderStatus status = VipOrderStatus.NEW;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    private String notes;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;
    @Column(name = "preparing_at")
    private Instant preparingAt;
    @Column(name = "prepared_by_id")
    private UUID preparedById;
    @Column(name = "prepared_by_name")
    private String preparedByName;
    @Column(name = "ready_at")
    private Instant readyAt;
    @Column(name = "on_the_way_at")
    private Instant onTheWayAt;
    @Column(name = "delivered_by_id")
    private UUID deliveredById;
    @Column(name = "delivered_by_name")
    private String deliveredByName;
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VipOrderItem> items = new ArrayList<>();

    public void addItem(VipOrderItem item) {
        item.setOrder(this);
        item.setTenantId(tenantId);
        items.add(item);
    }

    /** Importe adicional (lo que no sale del paquete). */
    public BigDecimal extraTotal() {
        return items.stream().filter(i -> !i.isIncluded()).map(VipOrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
