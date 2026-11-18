package com.bloop.inventory.dto.response;

import com.bloop.inventory.entity.StockMovementType;

import java.time.Instant;
import java.util.UUID;

/** Respuestas de inventario. */
public final class InventoryResponses {

    private InventoryResponses() {
    }

    public record Warehouse(UUID id, UUID venueId, String venueName, String name, boolean active) {
    }

    /** Existencias de una variante en un almacen. low = esta en el minimo o por debajo. */
    /**
     * Existencias de una variante. quantity y minQuantity van en su unidad: unidades o ml
     * (stockUnit = ML, con unitSizeMl ml por botella).
     */
    public record StockItem(UUID variantId, String categoryName, String productName, String variantName, String sku,
                            boolean variantActive, int quantity, int minQuantity, boolean low,
                            com.bloop.pos.entity.StockUnit stockUnit, Integer unitSizeMl,
                            java.math.BigDecimal cost) {
    }

    public record Supplier(UUID id, String name, String contactName, String phone, String email, boolean active) {
    }

    /**
     * Control de un producto en un periodo (todo en su unidad de inventario: ml o unidades).
     * merma = lo que cambio el recuento (negativo = falta). mermaValue = coste de lo que falta.
     */
    public record ControlRow(UUID variantId, String categoryName, String productName, String variantName,
                             com.bloop.pos.entity.StockUnit stockUnit, Integer unitSizeMl, int opening,
                             int received, int sent, int sold, int merma, int closing,
                             java.math.BigDecimal mermaValue) {
    }

    public record Control(UUID warehouseId, java.time.Instant from, java.time.Instant to, java.util.List<ControlRow> rows,
                          java.math.BigDecimal totalMermaValue) {
    }

    /** Lo que falta para llegar al minimo, con el ultimo proveedor que lo trajo. */
    public record SuggestedLine(UUID variantId, String productName, String variantName,
                                com.bloop.pos.entity.StockUnit stockUnit, Integer unitSizeMl, int quantity,
                                int minQuantity, int missing, UUID supplierId, String supplierName) {
    }

    /** Envases retornables por proveedor: recibidos llenos, devueltos vacios y pendientes. */
    public record ContainerBalance(UUID supplierId, String supplierName, UUID variantId, String productName,
                                   String variantName, int received, int returned, int pending) {
    }

    public record Movement(UUID id, UUID variantId, String productName, String variantName, StockMovementType type,
                           int quantity, String reason, UUID saleId, String userName, Instant createdAt) {
    }
}
