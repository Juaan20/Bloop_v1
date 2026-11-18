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

import java.util.UUID;

/** Punto de venta fisico (una tablet en la barra, la taquilla...). Descuenta stock de su almacen. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pos_terminals")
public class PosTerminal extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "venue_id", nullable = false, updatable = false)
    private UUID venueId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TerminalType type = TerminalType.BAR;

    @Column(nullable = false)
    private boolean active = true;

    /** Tipos de categoria que puede vender este terminal (p. ej. "BAR"). */
    @Column(name = "allowed_kinds", nullable = false)
    private String allowedKinds = "BAR";

    public java.util.Set<CategoryKind> allowedKindSet() {
        return CategoryKind.parse(allowedKinds);
    }
}
