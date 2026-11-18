package com.bloop.inventory.dto.request;

import com.bloop.inventory.entity.StockMovementType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Peticiones de inventario: almacenes, movimientos manuales y minimo de aviso. */
public final class InventoryRequests {

    private InventoryRequests() {
    }

    /** El local no se puede cambiar al editar. */
    public record Warehouse(@NotNull UUID venueId, @NotBlank @Size(max = 80) String name, boolean active) {
    }

    /**
     * Movimiento manual: IN (entrada), OUT (salida) o ADJUSTMENT (recuento: quantity es la cantidad
     * contada). SALE y SALE_VOID solo los genera el TPV.
     */
    public record Movement(
            @NotNull UUID variantId,
            @NotNull StockMovementType type,
            @Min(0) @Max(10000000) int quantity,
            @Size(max = 255) String reason,
            /** Solo en entradas (IN): proveedor, numero de albaran y coste de cada unidad de compra. */
            UUID supplierId,
            @Size(max = 60) String documentRef,
            @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer = 8, fraction = 2)
            java.math.BigDecimal unitCost) {

        public Movement(UUID variantId, StockMovementType type, int quantity, String reason) {
            this(variantId, type, quantity, reason, null, null, null);
        }
    }

    /** Traspaso entre dos almacenes de la empresa (cantidad en la unidad de inventario). */
    public record Transfer(@NotNull UUID fromWarehouseId, @NotNull UUID toWarehouseId, @NotNull UUID variantId,
                           @Min(1) @Max(10000000) int quantity, @Size(max = 255) String reason) {
    }

    /** Recuento de cierre: lo contado de cada producto (en su unidad de inventario). */
    public record Count(@jakarta.validation.constraints.NotEmpty @Size(max = 500) java.util.List<@jakarta.validation.Valid CountItem> items,
                        @Size(max = 255) String reason) {
    }

    public record CountItem(@NotNull UUID variantId, @Min(0) @Max(10000000) int quantity) {
    }

    public record SupplierRequest(@jakarta.validation.constraints.NotBlank @Size(max = 150) String name,
                                  @Size(max = 150) String contactName, @Size(max = 40) String phone,
                                  @jakarta.validation.constraints.Email @Size(max = 255) String email, boolean active) {
    }

    /** Envases vacios que se lleva el proveedor (en unidades: botellas, botellines...). */
    public record ContainerReturnRequest(@NotNull UUID supplierId, @NotNull UUID variantId,
                                         @Min(1) @Max(100000) int quantity, @Size(max = 60) String documentRef) {
    }

    public record MinQuantity(@Min(0) @Max(100000) int minQuantity) {
    }
}
