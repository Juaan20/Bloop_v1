package com.bloop.inventory.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.inventory.dto.request.InventoryRequests;
import com.bloop.inventory.dto.response.InventoryResponses;
import com.bloop.inventory.entity.StockLevel;
import com.bloop.inventory.entity.StockMovement;
import com.bloop.inventory.entity.StockMovementType;
import com.bloop.inventory.entity.Warehouse;
import com.bloop.inventory.repository.StockLevelRepository;
import com.bloop.inventory.repository.StockMovementRepository;
import com.bloop.inventory.repository.WarehouseRepository;
import com.bloop.pos.entity.ProductVariant;
import com.bloop.pos.repository.ProductVariantRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.response.PageResponse;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.users.service.UserNames;
import com.bloop.venues.entity.Venue;
import com.bloop.venues.repository.VenueRepository;
import com.bloop.venues.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Inventario en el backoffice: almacenes, existencias, movimientos manuales y minimos de aviso. */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final WarehouseRepository warehouseRepository;
    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository movementRepository;
    private final ProductVariantRepository variantRepository;
    private final VenueRepository venueRepository;
    private final VenueService venueService;
    private final StockService stockService;
    private final com.bloop.inventory.repository.SupplierRepository supplierRepository;
    private final UserNames userNames;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<InventoryResponses.Warehouse> listWarehouses() {
        UUID tenantId = currentUserProvider.requireTenantId();
        Map<UUID, String> venues = venueRepository.findByTenantIdOrderByName(tenantId).stream()
                .collect(Collectors.toMap(Venue::getId, Venue::getName));
        return warehouseRepository.findByTenantIdOrderByName(tenantId).stream()
                .map(w -> toResponse(w, venues.get(w.getVenueId()))).toList();
    }

    @Transactional
    public InventoryResponses.Warehouse createWarehouse(InventoryRequests.Warehouse request) {
        Venue venue = venueService.findInMyTenant(request.venueId());
        Warehouse warehouse = new Warehouse();
        warehouse.setTenantId(venue.getTenantId());
        warehouse.setVenueId(venue.getId());
        warehouse.setName(request.name().trim());
        warehouse.setActive(request.active());
        warehouse = warehouseRepository.save(warehouse);
        auditService.record(AuditAction.WAREHOUSE_CHANGED, "Warehouse", warehouse.getId(), "Nuevo almacen "
                + warehouse.getName());
        return toResponse(warehouse, venue.getName());
    }

    @Transactional
    public InventoryResponses.Warehouse updateWarehouse(UUID id, InventoryRequests.Warehouse request) {
        Warehouse warehouse = findWarehouse(id);
        if (!warehouse.getVenueId().equals(request.venueId())) {
            throw new BadRequestException("Un almacen no se puede cambiar de local");
        }
        warehouse.setName(request.name().trim());
        warehouse.setActive(request.active());
        auditService.record(AuditAction.WAREHOUSE_CHANGED, "Warehouse", id, warehouse.getName() + " activo="
                + warehouse.isActive());
        String venueName = venueRepository.findById(warehouse.getVenueId()).map(Venue::getName).orElse("");
        return toResponse(warehouse, venueName);
    }

    /** Existencias del almacen: todas las variantes con control de stock (las que nunca se movieron, a 0). */
    @Transactional(readOnly = true)
    public List<InventoryResponses.StockItem> stock(UUID warehouseId) {
        Warehouse warehouse = findWarehouse(warehouseId);
        Map<UUID, StockLevel> levels = stockLevelRepository.findByWarehouseId(warehouse.getId()).stream()
                .collect(Collectors.toMap(StockLevel::getVariantId, Function.identity()));
        return variantRepository.findStockTracked(warehouse.getTenantId()).stream()
                .filter(v -> v.isActive() || levels.containsKey(v.getId()))
                .map(v -> {
                    StockLevel level = levels.get(v.getId());
                    int quantity = level == null ? 0 : level.getQuantity();
                    int min = level == null ? 0 : level.getMinQuantity();
                    return new InventoryResponses.StockItem(v.getId(), v.getProduct().getCategory().getName(),
                            v.getProduct().getName(), v.getName(), v.getSku(), v.isSellable(), quantity, min,
                            min > 0 && quantity <= min, v.getStockUnit(), v.getUnitSizeMl(), v.getCost());
                })
                .toList();
    }

    @Transactional
    public List<InventoryResponses.StockItem> registerMovement(UUID warehouseId, InventoryRequests.Movement request) {
        Warehouse warehouse = findWarehouse(warehouseId);
        if (request.type() != StockMovementType.IN && request.type() != StockMovementType.OUT
                && request.type() != StockMovementType.ADJUSTMENT) {
            throw new BadRequestException("Las ventas las registra el TPV y los traspasos tienen su propia pantalla");
        }
        if (request.type() != StockMovementType.ADJUSTMENT && request.quantity() == 0) {
            throw new BadRequestException("La cantidad debe ser mayor que 0");
        }
        ProductVariant variant = findTrackedVariant(warehouse.getTenantId(), request.variantId());
        // Entrada de proveedor: queda el albaran y el coste, y el coste del producto pasa a ser el ultimo
        StockService.Purchase purchase = null;
        if (request.type() == StockMovementType.IN && request.supplierId() != null) {
            supplierRepository.findByIdAndTenantId(request.supplierId(), warehouse.getTenantId())
                    .orElseThrow(() -> NotFoundException.of("Proveedor"));
            purchase = new StockService.Purchase(request.supplierId(), request.documentRef(), request.unitCost());
            if (request.unitCost() != null) {
                variant.setCost(request.unitCost());
            }
        }
        StockMovement movement = stockService.manual(warehouse.getTenantId(), warehouse.getId(),
                new StockService.Line(variant.getId(), request.quantity(), label(variant), variant.getUnitSizeMl()),
                request.type(),
                request.reason(), currentUserProvider.get().userId(), purchase);
        if (movement != null) {
            auditService.record(AuditAction.STOCK_MOVEMENT, "StockMovement", movement.getId(),
                    request.type() + " " + movement.getQuantity() + " x " + label(variant) + " en " + warehouse.getName());
        }
        return stock(warehouseId);
    }

    @Transactional
    public List<InventoryResponses.StockItem> setMinimum(UUID warehouseId, UUID variantId,
                                                         InventoryRequests.MinQuantity request) {
        Warehouse warehouse = findWarehouse(warehouseId);
        ProductVariant variant = findTrackedVariant(warehouse.getTenantId(), variantId);
        stockLevelRepository.insertIfMissing(warehouse.getTenantId(), warehouse.getId(), variant.getId(),
                Instant.now(clock));
        StockLevel level = stockLevelRepository.findByWarehouseIdAndVariantId(warehouse.getId(), variant.getId())
                .orElseThrow(() -> NotFoundException.of("Existencias"));
        // @DynamicUpdate en StockLevel: solo se escribe min_quantity, nunca quantity.
        level.setMinQuantity(request.minQuantity());
        level.setUpdatedAt(Instant.now(clock));
        stockLevelRepository.flush();
        stockService.checkLowStock(warehouse.getTenantId(), warehouse.getId(), variant.getId(), label(variant),
                variant.getUnitSizeMl());
        return stock(warehouseId);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryResponses.Movement> movements(UUID warehouseId, int page, int size) {
        Warehouse warehouse = findWarehouse(warehouseId);
        var result = movementRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouse.getId(),
                PageRequest.of(page, Math.min(size, 100)));
        Map<UUID, ProductVariant> variants = variantRepository.findAllWithProduct(
                        result.getContent().stream().map(StockMovement::getVariantId).collect(Collectors.toSet()),
                        warehouse.getTenantId()).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        Map<UUID, String> users = userNames.of(result.getContent().stream().map(StockMovement::getUserId).toList());
        return PageResponse.of(result, m -> {
            ProductVariant v = variants.get(m.getVariantId());
            return new InventoryResponses.Movement(m.getId(), m.getVariantId(),
                    v == null ? "" : v.getProduct().getName(), v == null ? "" : v.getName(), m.getType(),
                    m.getQuantity(), m.getReason(), m.getSaleId(), users.get(m.getUserId()), m.getCreatedAt());
        });
    }

    /** Almacen del tenant actual (404 si es de otra empresa). Lo usa tambien el modulo TPV. */
    @Transactional(readOnly = true)
    public Warehouse findWarehouse(UUID id) {
        return warehouseRepository.findByIdAndTenantId(id, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Almacen"));
    }

    private ProductVariant findTrackedVariant(UUID tenantId, UUID variantId) {
        ProductVariant variant = variantRepository.findAllWithProduct(List.of(variantId), tenantId).stream()
                .findFirst().orElseThrow(() -> NotFoundException.of("Producto"));
        if (!variant.getProduct().isTrackStock()) {
            throw new BadRequestException("Este producto no controla existencias");
        }
        return variant;
    }

    static String label(ProductVariant v) {
        return v.getProduct().getName() + " (" + v.getName() + ")";
    }

    private static InventoryResponses.Warehouse toResponse(Warehouse w, String venueName) {
        return new InventoryResponses.Warehouse(w.getId(), w.getVenueId(), venueName, w.getName(), w.isActive());
    }
}
