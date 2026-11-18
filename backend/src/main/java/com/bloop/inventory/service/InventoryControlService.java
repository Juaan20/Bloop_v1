package com.bloop.inventory.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.inventory.dto.request.InventoryRequests;
import com.bloop.inventory.dto.response.InventoryResponses;
import com.bloop.inventory.entity.ContainerReturn;
import com.bloop.inventory.entity.StockLevel;
import com.bloop.inventory.entity.StockMovementType;
import com.bloop.inventory.entity.Supplier;
import com.bloop.inventory.entity.Warehouse;
import com.bloop.inventory.repository.ContainerReturnRepository;
import com.bloop.inventory.repository.StockLevelRepository;
import com.bloop.inventory.repository.StockMovementRepository;
import com.bloop.inventory.repository.SupplierRepository;
import com.bloop.pos.entity.ProductVariant;
import com.bloop.pos.entity.StockUnit;
import com.bloop.pos.repository.ProductVariantRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Inventario como se lleva en una discoteca: proveedores, traspasos almacen -> barra, recuento de
 * cierre, control de merma (vendido frente a gastado), pedido sugerido y envases retornables.
 */
@Service
@RequiredArgsConstructor
public class InventoryControlService {

    private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");

    private final SupplierRepository supplierRepository;
    private final ContainerReturnRepository containerReturnRepository;
    private final StockMovementRepository movementRepository;
    private final StockLevelRepository stockLevelRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryService inventoryService;
    private final StockService stockService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final Clock clock;

    // ----------------------------------------------------------------- Proveedores

    @Transactional(readOnly = true)
    public List<InventoryResponses.Supplier> suppliers() {
        return supplierRepository.findByTenantIdOrderByName(currentUserProvider.requireTenantId()).stream()
                .map(InventoryControlService::toResponse).toList();
    }

    @Transactional
    public InventoryResponses.Supplier createSupplier(InventoryRequests.SupplierRequest r) {
        UUID tenantId = currentUserProvider.requireTenantId();
        if (supplierRepository.existsByTenantIdAndNameIgnoreCase(tenantId, r.name().trim())) {
            throw new ConflictException("Ya existe un proveedor con ese nombre");
        }
        Supplier s = new Supplier();
        s.setTenantId(tenantId);
        apply(s, r);
        s = supplierRepository.save(s);
        auditService.record(AuditAction.STOCK_MOVEMENT, "Supplier", s.getId(), "Proveedor " + s.getName());
        return toResponse(s);
    }

    @Transactional
    public InventoryResponses.Supplier updateSupplier(UUID id, InventoryRequests.SupplierRequest r) {
        Supplier s = findSupplier(id);
        apply(s, r);
        return toResponse(s);
    }

    private Supplier findSupplier(UUID id) {
        return supplierRepository.findByIdAndTenantId(id, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Proveedor"));
    }

    // ----------------------------------------------------------------- Traspasos y recuento

    @Transactional
    public void transfer(InventoryRequests.Transfer r) {
        if (r.fromWarehouseId().equals(r.toWarehouseId())) {
            throw new BadRequestException("El almacen de origen y el de destino son el mismo");
        }
        Warehouse from = inventoryService.findWarehouse(r.fromWarehouseId());
        Warehouse to = inventoryService.findWarehouse(r.toWarehouseId());
        ProductVariant v = trackedVariant(from.getTenantId(), r.variantId());
        String reason = r.reason() == null || r.reason().isBlank()
                ? "Traspaso " + from.getName() + " -> " + to.getName() : r.reason().trim();
        stockService.transfer(from.getTenantId(), from.getId(), to.getId(),
                new StockService.Line(v.getId(), r.quantity(), InventoryService.label(v), v.getUnitSizeMl()),
                reason, currentUserProvider.get().userId());
        auditService.record(AuditAction.STOCK_MOVEMENT, "Warehouse", to.getId(), "Traspaso " + r.quantity() + " x "
                + InventoryService.label(v) + " de " + from.getName() + " a " + to.getName());
    }

    /** Recuento de cierre: deja cada producto en la cantidad contada (la diferencia es la merma). */
    @Transactional
    public List<InventoryResponses.StockItem> count(UUID warehouseId, InventoryRequests.Count r) {
        Warehouse warehouse = inventoryService.findWarehouse(warehouseId);
        UUID userId = currentUserProvider.get().userId();
        String reason = r.reason() == null || r.reason().isBlank() ? "Recuento de cierre" : r.reason().trim();
        for (InventoryRequests.CountItem item : r.items()) {
            ProductVariant v = trackedVariant(warehouse.getTenantId(), item.variantId());
            stockService.manual(warehouse.getTenantId(), warehouse.getId(),
                    new StockService.Line(v.getId(), item.quantity(), InventoryService.label(v), v.getUnitSizeMl()),
                    StockMovementType.ADJUSTMENT, reason, userId);
        }
        auditService.record(AuditAction.STOCK_MOVEMENT, "Warehouse", warehouseId, "Recuento de " + r.items().size()
                + " productos en " + warehouse.getName());
        return inventoryService.stock(warehouseId);
    }

    // ----------------------------------------------------------------- Control (merma)

    /**
     * Control de un almacen en un periodo. Sin fechas: desde el penultimo recuento hasta ahora (la
     * ultima noche si se cuenta al cerrar), o las ultimas 24 h si no hay recuentos.
     */
    @Transactional(readOnly = true)
    public InventoryResponses.Control control(UUID warehouseId, Instant from, Instant to) {
        Warehouse warehouse = inventoryService.findWarehouse(warehouseId);
        Instant end = to != null ? to : Instant.now(clock).plusSeconds(1);
        Instant start = from;
        if (start == null) {
            Instant last = movementRepository.lastCountBefore(warehouse.getId(), end);
            Instant previous = last == null ? null : movementRepository.lastCountBefore(warehouse.getId(), last);
            start = previous != null ? previous.plusMillis(1) : end.minus(Duration.ofHours(24));
        }
        Map<UUID, Map<StockMovementType, Integer>> period = new HashMap<>();
        for (Object[] row : movementRepository.sumByVariantAndType(warehouse.getId(), start, end)) {
            period.computeIfAbsent((UUID) row[0], k -> new EnumMap<>(StockMovementType.class))
                    .put((StockMovementType) row[1], ((Number) row[2]).intValue());
        }
        Map<UUID, Integer> after = new HashMap<>();
        for (Object[] row : movementRepository.sumByVariantAndType(warehouse.getId(), end, FAR_FUTURE)) {
            after.merge((UUID) row[0], ((Number) row[2]).intValue(), Integer::sum);
        }
        Map<UUID, Integer> current = stockLevelRepository.findByWarehouseId(warehouse.getId()).stream()
                .collect(Collectors.toMap(StockLevel::getVariantId, StockLevel::getQuantity));
        List<InventoryResponses.ControlRow> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ProductVariant v : variantRepository.findStockTracked(warehouse.getTenantId())) {
            if (!period.containsKey(v.getId()) && !current.containsKey(v.getId())) {
                continue;
            }
            var res = InventoryControlCalculator.compute(period.getOrDefault(v.getId(), Map.of()),
                    after.getOrDefault(v.getId(), 0), current.getOrDefault(v.getId(), 0));
            BigDecimal value = InventoryControlCalculator.mermaValue(res.merma(), v.getCost(),
                    v.getStockUnit() == StockUnit.ML ? v.getUnitSizeMl() : null);
            total = total.add(value);
            rows.add(new InventoryResponses.ControlRow(v.getId(), v.getProduct().getCategory().getName(),
                    v.getProduct().getName(), v.getName(), v.getStockUnit(), v.getUnitSizeMl(), res.opening(),
                    res.received(), res.sent(), res.sold(), res.merma(), res.closing(), value));
        }
        return new InventoryResponses.Control(warehouse.getId(), start, end, rows, total);
    }

    // ----------------------------------------------------------------- Pedido sugerido

    @Transactional(readOnly = true)
    public List<InventoryResponses.SuggestedLine> suggestedOrder(UUID warehouseId) {
        Warehouse warehouse = inventoryService.findWarehouse(warehouseId);
        Map<UUID, UUID> lastSupplier = new HashMap<>();
        for (Object[] row : movementRepository.lastSupplierByVariant(warehouse.getTenantId())) {
            lastSupplier.put((UUID) row[0], (UUID) row[1]);
        }
        Map<UUID, String> names = supplierNames(warehouse.getTenantId());
        List<StockLevel> low = stockLevelRepository.findByWarehouseId(warehouse.getId()).stream()
                .filter(l -> l.getMinQuantity() > 0 && l.getQuantity() < l.getMinQuantity()).toList();
        Map<UUID, ProductVariant> variants = variantRepository.findAllWithProduct(
                        low.stream().map(StockLevel::getVariantId).toList(), warehouse.getTenantId()).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        return low.stream().filter(l -> variants.containsKey(l.getVariantId())).map(l -> {
            ProductVariant v = variants.get(l.getVariantId());
            UUID supplierId = lastSupplier.get(v.getId());
            return new InventoryResponses.SuggestedLine(v.getId(), v.getProduct().getName(), v.getName(),
                    v.getStockUnit(), v.getUnitSizeMl(), l.getQuantity(), l.getMinQuantity(),
                    l.getMinQuantity() - l.getQuantity(), supplierId, names.get(supplierId));
        }).toList();
    }

    // ----------------------------------------------------------------- Envases retornables

    @Transactional
    public List<InventoryResponses.ContainerBalance> returnContainers(InventoryRequests.ContainerReturnRequest r) {
        Supplier supplier = findSupplier(r.supplierId());
        ProductVariant v = trackedVariant(supplier.getTenantId(), r.variantId());
        if (!v.isReturnable()) {
            throw new BadRequestException(InventoryService.label(v) + " no tiene envase retornable");
        }
        ContainerReturn cr = new ContainerReturn();
        cr.setTenantId(supplier.getTenantId());
        cr.setSupplierId(supplier.getId());
        cr.setVariantId(v.getId());
        cr.setQuantity(r.quantity());
        cr.setDocumentRef(r.documentRef() == null || r.documentRef().isBlank() ? null : r.documentRef().trim());
        cr.setUserId(currentUserProvider.get().userId());
        cr.setCreatedAt(Instant.now(clock));
        containerReturnRepository.save(cr);
        auditService.record(AuditAction.STOCK_MOVEMENT, "Supplier", supplier.getId(), "Envases devueltos: "
                + r.quantity() + " x " + InventoryService.label(v));
        return containers();
    }

    /** Por proveedor y producto retornable: envases recibidos llenos, devueltos vacios y pendientes. */
    @Transactional(readOnly = true)
    public List<InventoryResponses.ContainerBalance> containers() {
        UUID tenantId = currentUserProvider.requireTenantId();
        List<Object[]> received = movementRepository.sumReceivedBySupplier(tenantId);
        List<Object[]> returned = containerReturnRepository.sumReturned(tenantId);
        List<UUID> ids = new ArrayList<>();
        received.forEach(r -> ids.add((UUID) r[1]));
        returned.forEach(r -> ids.add((UUID) r[1]));
        Map<UUID, ProductVariant> variants = new HashMap<>();
        variantRepository.findAllWithProduct(ids, tenantId).forEach(v -> variants.put(v.getId(), v));
        record Key(UUID supplierId, UUID variantId) {
        }
        Map<Key, int[]> balance = new HashMap<>();
        for (Object[] r : received) {
            ProductVariant v = variants.get((UUID) r[1]);
            if (v == null || !v.isReturnable()) {
                continue;
            }
            int units = ((Number) r[2]).intValue();
            if (v.getStockUnit() == StockUnit.ML && v.getUnitSizeMl() != null) {
                units = units / v.getUnitSizeMl(); // botellas llenas recibidas
            }
            balance.computeIfAbsent(new Key((UUID) r[0], v.getId()), k -> new int[2])[0] += units;
        }
        for (Object[] r : returned) {
            balance.computeIfAbsent(new Key((UUID) r[0], (UUID) r[1]), k -> new int[2])[1] += ((Number) r[2]).intValue();
        }
        Map<UUID, String> names = supplierNames(tenantId);
        return balance.entrySet().stream().filter(e -> variants.containsKey(e.getKey().variantId())).map(e -> {
            ProductVariant v = variants.get(e.getKey().variantId());
            int[] b = e.getValue();
            return new InventoryResponses.ContainerBalance(e.getKey().supplierId(), names.get(e.getKey().supplierId()),
                    v.getId(), v.getProduct().getName(), v.getName(), b[0], b[1], Math.max(0, b[0] - b[1]));
        }).sorted(Comparator.comparing(InventoryResponses.ContainerBalance::supplierName,
                Comparator.nullsLast(String::compareTo))).toList();
    }

    // ----------------------------------------------------------------- Auxiliares

    private Map<UUID, String> supplierNames(UUID tenantId) {
        return supplierRepository.findByTenantIdOrderByName(tenantId).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getName));
    }

    private ProductVariant trackedVariant(UUID tenantId, UUID variantId) {
        ProductVariant v = variantRepository.findAllWithProduct(List.of(variantId), tenantId).stream().findFirst()
                .orElseThrow(() -> NotFoundException.of("Producto"));
        if (!v.getProduct().isTrackStock() || !v.getRecipe().isEmpty()) {
            throw new BadRequestException(InventoryService.label(v) + " no se lleva en el inventario");
        }
        return v;
    }

    private static void apply(Supplier s, InventoryRequests.SupplierRequest r) {
        s.setName(r.name().trim());
        s.setContactName(blank(r.contactName()));
        s.setPhone(blank(r.phone()));
        s.setEmail(blank(r.email()));
        s.setActive(r.active());
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static InventoryResponses.Supplier toResponse(Supplier s) {
        return new InventoryResponses.Supplier(s.getId(), s.getName(), s.getContactName(), s.getPhone(), s.getEmail(),
                s.isActive());
    }
}
