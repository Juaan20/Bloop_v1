package com.bloop.reservations.entity;

import com.bloop.shared.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Mesa reservable de una zona VIP. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vip_tables")
public class VipTable extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false, updatable = false)
    private VipZone zone;

    @Column(nullable = false)
    private String name;

    /** Numero maximo de personas. */
    @Column(nullable = false)
    private int capacity;

    /** Consumo minimo que se compromete a gastar el grupo. */
    @Column(name = "min_spend", nullable = false, precision = 10, scale = 2)
    private BigDecimal minSpend = BigDecimal.ZERO;

    /** Anticipo que se pide para confirmar la reserva (se descuenta del consumo). */
    @Column(name = "deposit_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    // ---- Posicion en el plano (en % del lienzo). null = sin colocar.
    @Column(name = "pos_x", precision = 5, scale = 2)
    private BigDecimal posX;

    @Column(name = "pos_y", precision = 5, scale = 2)
    private BigDecimal posY;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal width = BigDecimal.valueOf(8);

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal height = BigDecimal.valueOf(8);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VipTableShape shape = VipTableShape.ROUND;

    /** Productos incluidos en el precio de la reserva. */
    @OneToMany(mappedBy = "table", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VipTablePackageItem> packageItems = new ArrayList<>();

    /** Una mesa solo se puede reservar si ella y su zona estan activas. */
    public boolean isBookable() {
        return active && zone.isActive();
    }

    /** Colocada en el plano de su sala (las que no lo estan no se pueden vender ni reservar). */
    public boolean isOnPlan() {
        return posX != null && posY != null;
    }
}
