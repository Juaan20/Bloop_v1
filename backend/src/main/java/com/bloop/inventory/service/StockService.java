package com.bloop.inventory.service;

import com.bloop.inventory.entity.StockLevel;
import com.bloop.inventory.entity.StockMovement;
import com.bloop.inventory.entity.StockMovementType;
import com.bloop.inventory.repository.StockLevelRepository;
import com.bloop.inventory.repository.StockMovementRepository;
import com.bloop.notifications.entity.AlertType;
import com.bloop.notifications.service.AdminAlertService;
import com.bloop.shared.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Cambios de existencias. TODOS pasan por aqui para que siempre:
 * 1. se usen UPDATE atomicos (nunca "leer, restar y guardar"),
 * 2. quede un movimiento en el historial,
 * 3. se avise al equipo si un producto baja de su minimo (alerta LOW_INVENTORY).
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository movementRepository;
    private final AdminAlertService alertService;
    private final Clock clock;

    /**
     * Una linea a descontar o devolver: variante, cantidad en su unidad de inventario y nombre.
     *
     * @param unitSizeMl ml por botella si la variante se cuenta en ml (para los mensajes); null si son unidades
     */
    public record Line(UUID variantId, int quantity, String label, Integer unitSizeMl) {

        public Line(UUID variantId, int quantity, String label) {
            this(variantId, quantity, label, null);
        }
    }

    /** Datos de una entrada de proveedor (todos opcionales). */
    public record Purchase(UUID supplierId, String documentRef, java.math.BigDecimal unitCost) {
    }

    /**
     * Descuenta las existencias de una venta del TPV. Como en cualquier TPV de hosteleria, la venta
     * NUNCA se bloquea por stock: el stock teorico puede quedar en negativo hasta el recuento.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void consumeForSale(UUID tenantId, UUID warehouseId, List<Line> lines, UUID saleId, UUID userId) {
        Instant now = Instant.now(clock);
        // Orden fijo por id: dos ventas simultaneas bloquean las filas en el mismo orden (sin interbloqueos).
        for (Line line : lines.stream().sorted(Comparator.comparing(Line::variantId)).toList()) {
            stockLevelRepository.insertIfMissing(tenantId, warehouseId, line.variantId(), now);
            stockLevelRepository.decreaseAllowingNegative(warehouseId, line.variantId(), line.quantity(), now);
            record(tenantId, warehouseId, line.variantId(), StockMovementType.SALE, -line.quantity(), null, saleId,
                    userId, now);
            checkLowStock(tenantId, warehouseId, line.variantId(), line.label(), line.unitSizeMl());
        }
    }

    /**
     * Devuelve al almacen lo que desconto una venta anulada. Se leen sus movimientos SALE, asi se
     * devuelve exactamente lo mismo aunque la receta haya cambiado despues de vender.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreForVoid(UUID tenantId, UUID warehouseId, UUID saleId, UUID userId) {
        Instant now = Instant.now(clock);
        List<StockMovement> sold = movementRepository.findBySaleIdAndType(saleId, StockMovementType.SALE).stream()
                .sorted(Comparator.comparing(StockMovement::getVariantId)).toList();
        for (StockMovement m : sold) {
            int quantity = -m.getQuantity();
            stockLevelRepository.insertIfMissing(tenantId, warehouseId, m.getVariantId(), now);
            stockLevelRepository.increase(warehouseId, m.getVariantId(), quantity, now);
            record(tenantId, warehouseId, m.getVariantId(), StockMovementType.SALE_VOID, quantity,
                    "Anulacion de venta", saleId, userId, now);
            checkLowStock(tenantId, warehouseId, m.getVariantId(), "producto de la venta anulada", null);
        }
    }

    /**
     * Movimiento manual del backoffice.
     * IN suma, OUT resta (409 si no hay suficientes) y ADJUSTMENT deja la cantidad contada.
     *
     * @return el movimiento registrado (null si un ajuste no cambia nada)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement manual(UUID tenantId, UUID warehouseId, Line line, StockMovementType type, String reason,
                                UUID userId) {
        return manual(tenantId, warehouseId, line, type, reason, userId, null);
    }

    /** Igual, con los datos del albaran si es una entrada de proveedor. */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement manual(UUID tenantId, UUID warehouseId, Line line, StockMovementType type, String reason,
                                UUID userId, Purchase purchase) {
        Instant now = Instant.now(clock);
        stockLevelRepository.insertIfMissing(tenantId, warehouseId, line.variantId(), now);
        int delta = switch (type) {
            case IN -> {
                stockLevelRepository.increase(warehouseId, line.variantId(), line.quantity(), now);
                yield line.quantity();
            }
            case OUT -> {
                if (stockLevelRepository.decrease(warehouseId, line.variantId(), line.quantity(), now) == 0) {
                    throw new ConflictException("No hay existencias suficientes de " + line.label());
                }
                yield -line.quantity();
            }
            case ADJUSTMENT -> {
                int current = stockLevelRepository.findQuantity(warehouseId, line.variantId()).orElse(0);
                stockLevelRepository.setQuantity(warehouseId, line.variantId(), line.quantity(), now);
                yield line.quantity() - current;
            }
            case SALE, SALE_VOID, TRANSFER_IN, TRANSFER_OUT ->
                    throw new IllegalArgumentException("Movimiento reservado al TPV o a los traspasos");
        };
        checkLowStock(tenantId, warehouseId, line.variantId(), line.label(), line.unitSizeMl());
        if (delta == 0) {
            return null;
        }
        StockMovement movement = newMovement(tenantId, warehouseId, line.variantId(), type, delta, reason, null,
                userId, now);
        if (purchase != null && type == StockMovementType.IN) {
            movement.setSupplierId(purchase.supplierId());
            movement.setDocumentRef(purchase.documentRef() == null || purchase.documentRef().isBlank() ? null
                    : purchase.documentRef().trim());
            movement.setUnitCost(purchase.unitCost());
        }
        return movementRepository.save(movement);
    }

    /**
     * Traspaso entre almacenes (p. ej. del almacen a la barra): sale de uno y entra en otro en la misma
     * transaccion. El origen debe tener suficiente (409 si no).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void transfer(UUID tenantId, UUID fromWarehouseId, UUID toWarehouseId, Line line, String reason,
                         UUID userId) {
        Instant now = Instant.now(clock);
        stockLevelRepository.insertIfMissing(tenantId, fromWarehouseId, line.variantId(), now);
        stockLevelRepository.insertIfMissing(tenantId, toWarehouseId, line.variantId(), now);
        if (stockLevelRepository.decrease(fromWarehouseId, line.variantId(), line.quantity(), now) == 0) {
            throw new ConflictException("No hay suficientes " + line.label() + " en el almacen de origen");
        }
        stockLevelRepository.increase(toWarehouseId, line.variantId(), line.quantity(), now);
        record(tenantId, fromWarehouseId, line.variantId(), StockMovementType.TRANSFER_OUT, -line.quantity(), reason,
                null, userId, now);
        record(tenantId, toWarehouseId, line.variantId(), StockMovementType.TRANSFER_IN, line.quantity(), reason,
                null, userId, now);
        checkLowStock(tenantId, fromWarehouseId, line.variantId(), line.label(), line.unitSizeMl());
        checkLowStock(tenantId, toWarehouseId, line.variantId(), line.label(), line.unitSizeMl());
    }

    /**
     * Alerta si la cantidad esta en el minimo o por debajo; si se ha repuesto, retira la alerta
     * para que pueda volver a avisar la proxima vez.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkLowStock(UUID tenantId, UUID warehouseId, UUID variantId, String label, Integer unitSizeMl) {
        StockLevel level = stockLevelRepository.findByWarehouseIdAndVariantId(warehouseId, variantId).orElse(null);
        if (level == null) {
            return;
        }
        // La cantidad se lee aparte: la entidad puede tener un valor anterior al UPDATE atomico.
        int quantity = stockLevelRepository.findQuantity(warehouseId, variantId).orElse(0);
        if (level.getMinQuantity() > 0 && quantity <= level.getMinQuantity()) {
            alertService.raise(tenantId, AlertType.LOW_INVENTORY, "Stock bajo: " + label,
                    "Quedan " + describe(quantity, unitSizeMl) + " (minimo " + describe(level.getMinQuantity(),
                            unitSizeMl) + ").",
                    "/admin/inventario", level.getId());
        } else {
            alertService.clear(tenantId, AlertType.LOW_INVENTORY, level.getId());
        }
    }

    /** "12 unidades" o "2.100 ml (3 botellas)". */
    static String describe(int quantity, Integer unitSizeMl) {
        if (unitSizeMl == null) {
            return quantity + " unidades";
        }
        return String.format(java.util.Locale.forLanguageTag("es-ES"), "%,d ml (%.1f botellas)", quantity,
                quantity / (double) unitSizeMl);
    }

    private StockMovement record(UUID tenantId, UUID warehouseId, UUID variantId, StockMovementType type,
                                 int quantity, String reason, UUID saleId, UUID userId, Instant now) {
        return movementRepository.save(newMovement(tenantId, warehouseId, variantId, type, quantity, reason, saleId,
                userId, now));
    }

    private StockMovement newMovement(UUID tenantId, UUID warehouseId, UUID variantId, StockMovementType type,
                                      int quantity, String reason, UUID saleId, UUID userId, Instant now) {
        StockMovement movement = new StockMovement();
        movement.setTenantId(tenantId);
        movement.setWarehouseId(warehouseId);
        movement.setVariantId(variantId);
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        movement.setSaleId(saleId);
        movement.setUserId(userId);
        movement.setCreatedAt(now);
        return movement;
    }
}
