package com.bloop.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

/**
 * Existencias de una variante de producto en un almacen.
 * quantity solo se modifica con los UPDATE atomicos de StockLevelRepository (nunca con setQuantity).
 * @DynamicUpdate: al cambiar minQuantity desde la entidad, Hibernate escribe SOLO esa columna y no
 * pisa quantity con un valor antiguo que tenga en memoria.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@DynamicUpdate
@Table(name = "stock_levels")
public class StockLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private UUID warehouseId;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private UUID variantId;

    @Column(nullable = false)
    private int quantity;

    /** Umbral de aviso de stock bajo (0 = sin aviso). */
    @Column(name = "min_quantity", nullable = false)
    private int minQuantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
