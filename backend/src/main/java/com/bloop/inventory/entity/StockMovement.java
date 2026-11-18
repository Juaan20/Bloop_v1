package com.bloop.inventory.entity;

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

/** Movimiento de inventario (historial, solo se inserta). quantity con signo: + entra, - sale. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private UUID warehouseId;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private UUID variantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private StockMovementType type;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(updatable = false)
    private String reason;

    /** Venta del TPV que origino el movimiento (SALE / SALE_VOID). */
    @Column(name = "sale_id", updatable = false)
    private UUID saleId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    /** Entrada de proveedor: quien la trajo, numero de albaran y coste de cada unidad de compra. */
    @Column(name = "supplier_id", updatable = false)
    private UUID supplierId;

    @Column(name = "document_ref", updatable = false)
    private String documentRef;

    @Column(name = "unit_cost", updatable = false)
    private java.math.BigDecimal unitCost;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
