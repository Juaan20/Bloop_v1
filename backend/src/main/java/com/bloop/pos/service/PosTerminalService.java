package com.bloop.pos.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.inventory.entity.StockLevel;
import com.bloop.inventory.entity.Warehouse;
import com.bloop.inventory.repository.StockLevelRepository;
import com.bloop.inventory.repository.WarehouseRepository;
import com.bloop.inventory.service.InventoryService;
import com.bloop.pos.dto.request.TerminalRequest;
import com.bloop.pos.dto.response.CatalogResponses;
import com.bloop.pos.entity.CashSession;
import com.bloop.pos.entity.CashSessionStatus;
import com.bloop.pos.entity.PosTerminal;
import com.bloop.pos.entity.Product;
import com.bloop.pos.entity.ProductVariant;
import com.bloop.pos.mapper.PosMapper;
import com.bloop.pos.repository.CashSessionRepository;
import com.bloop.pos.repository.PosTerminalRepository;
import com.bloop.pos.repository.ProductRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.security.CurrentUser;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.users.service.UserNames;
import com.bloop.venues.entity.Venue;
import com.bloop.venues.repository.VenueRepository;
import com.bloop.venues.service.StaffVenueAccess;
import com.bloop.venues.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Terminales del TPV (alta y edicion con POS_MANAGE) y datos de la pantalla tactil (POS_SELL).
 * Un cajero solo ve y usa los terminales de los locales que tiene asignados.
 */
@Service
@RequiredArgsConstructor
public class PosTerminalService {

    private final PosTerminalRepository terminalRepository;
    private final CashSessionRepository cashSessionRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockLevelRepository stockLevelRepository;
    private final ProductRepository productRepository;
    private final VenueRepository venueRepository;
    private final VenueService venueService;
    private final InventoryService inventoryService;
    private final CashSessionService cashSessionService;
    private final StaffVenueAccess staffVenueAccess;
    private final PosTerminalAccess terminalAccess;
    private final UserNames userNames;
    private final PosMapper posMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    /**
     * @param onlyMine true: terminales activos de los locales del usuario (pantalla de eleccion del TPV);
     *                 false: todos los de la empresa (gestion)
     */
    @Transactional(readOnly = true)
    public List<CatalogResponses.Terminal> list(boolean onlyMine) {
        CurrentUser user = currentUserProvider.get();
        UUID tenantId = currentUserProvider.requireTenantId();
        Set<UUID> allowed = onlyMine ? staffVenueAccess.allowedVenueIds(user, tenantId) : null;
        List<PosTerminal> terminals = terminalRepository.findByTenantIdOrderByName(tenantId).stream()
                .filter(t -> !onlyMine || (t.isActive() && allowed.contains(t.getVenueId())))
                .toList();
        return toResponses(tenantId, terminals);
    }

    @Transactional
    public CatalogResponses.Terminal create(TerminalRequest request) {
        Venue venue = venueService.findInMyTenant(request.venueId());
        PosTerminal terminal = new PosTerminal();
        terminal.setTenantId(venue.getTenantId());
        terminal.setVenueId(venue.getId());
        apply(terminal, request);
        terminal = terminalRepository.save(terminal);
        auditService.record(AuditAction.POS_TERMINAL_CHANGED, "PosTerminal", terminal.getId(), "Nuevo terminal "
                + terminal.getName());
        return toResponses(terminal.getTenantId(), List.of(terminal)).getFirst();
    }

    @Transactional
    public CatalogResponses.Terminal update(UUID id, TerminalRequest request) {
        PosTerminal terminal = terminalAccess.findInMyTenant(id);
        if (!terminal.getVenueId().equals(request.venueId())) {
            throw new BadRequestException("Un terminal no se puede cambiar de local");
        }
        apply(terminal, request);
        auditService.record(AuditAction.POS_TERMINAL_CHANGED, "PosTerminal", id, terminal.getName() + " activo="
                + terminal.isActive());
        return toResponses(terminal.getTenantId(), List.of(terminal)).getFirst();
    }

    /** Pantalla tactil: terminal, caja abierta y productos vendibles con las existencias de su almacen. */
    @Transactional(readOnly = true)
    public CatalogResponses.PosScreen screen(UUID terminalId) {
        PosTerminal terminal = terminalAccess.findUsable(terminalId);
        CashSession open = cashSessionRepository.findByTerminalIdAndStatus(terminal.getId(), CashSessionStatus.OPEN)
                .orElse(null);
        Map<UUID, Integer> stock = stockLevelRepository.findByWarehouseId(terminal.getWarehouseId()).stream()
                .collect(Collectors.toMap(StockLevel::getVariantId, StockLevel::getQuantity));

        // Productos activos agrupados por categoria (la consulta ya viene ordenada).
        Map<UUID, List<CatalogResponses.PosProduct>> byCategory = new LinkedHashMap<>();
        Map<UUID, Product> categoryOf = new LinkedHashMap<>();
        var allowedKinds = terminal.allowedKindSet();
        for (Product p : productRepository.findAllOfTenant(terminal.getTenantId())) {
            // Cada terminal solo ve las categorias de los tipos que tiene permitidos (barra, VIP...).
            if (!p.isActive() || !p.getCategory().isActive() || !allowedKinds.contains(p.getCategory().getKind())) {
                continue;
            }
            List<CatalogResponses.PosVariant> variants = new ArrayList<>();
            for (ProductVariant v : p.getVariants()) {
                if (v.isActive()) {
                    variants.add(new CatalogResponses.PosVariant(v.getId(), v.getName(), v.getPrice(),
                            StockConsumption.servingsAvailable(v, stock), v.isRequiresMixer()));
                }
            }
            if (!variants.isEmpty()) {
                byCategory.computeIfAbsent(p.getCategory().getId(), k -> new ArrayList<>())
                        .add(new CatalogResponses.PosProduct(p.getId(), p.getName(), p.isTrackStock(), variants));
                categoryOf.putIfAbsent(p.getCategory().getId(), p);
            }
        }
        List<CatalogResponses.PosCategory> categories = byCategory.entrySet().stream()
                .map(e -> {
                    var category = categoryOf.get(e.getKey()).getCategory();
                    return new CatalogResponses.PosCategory(category.getId(), category.getName(), category.getColor(),
                            category.getKind(), e.getValue());
                })
                .toList();
        // Refrescos para los combinados (cualquier producto marcado como refresco y activo).
        List<CatalogResponses.PosMixer> mixers = new ArrayList<>();
        for (Product p : productRepository.findAllOfTenant(terminal.getTenantId())) {
            if (!p.isMixer() || !p.isActive() || !p.getCategory().isActive()) {
                continue;
            }
            p.getVariants().stream().filter(ProductVariant::isActive).findFirst().ifPresent(v -> mixers.add(
                    new CatalogResponses.PosMixer(v.getId(), p.getName(), p.getMixerSurcharge(),
                            StockConsumption.servingsAvailable(v, stock))));
        }
        return new CatalogResponses.PosScreen(toResponses(terminal.getTenantId(), List.of(terminal)).getFirst(),
                open == null ? null : cashSessionService.toResponse(open), categories, mixers);
    }

    /** Barra: productos de barra. Taquilla: otros. Servicio VIP: botellas y servicio de mesa. */
    private static java.util.Set<com.bloop.pos.entity.CategoryKind> defaultKinds(com.bloop.pos.entity.TerminalType type) {
        return switch (type) {
            case BAR -> java.util.EnumSet.of(com.bloop.pos.entity.CategoryKind.BAR);
            case BOX_OFFICE -> java.util.EnumSet.of(com.bloop.pos.entity.CategoryKind.OTHER);
            case VIP_SERVICE -> java.util.EnumSet.of(com.bloop.pos.entity.CategoryKind.VIP_BOTTLE,
                    com.bloop.pos.entity.CategoryKind.SERVICE);
        };
    }

    private void apply(PosTerminal terminal, TerminalRequest r) {
        Warehouse warehouse = inventoryService.findWarehouse(r.warehouseId());
        if (!warehouse.getVenueId().equals(terminal.getVenueId())) {
            throw new BadRequestException("El almacen debe ser del mismo local que el terminal");
        }
        terminal.setWarehouseId(warehouse.getId());
        terminal.setName(r.name().trim());
        terminal.setType(r.type());
        terminal.setActive(r.active());
        // Por defecto la barra solo vende productos de barra y la taquilla "otros".
        var kinds = r.allowedKinds() == null || r.allowedKinds().isEmpty()
                ? defaultKinds(r.type())
                : r.allowedKinds();
        terminal.setAllowedKinds(com.bloop.pos.entity.CategoryKind.format(kinds));
    }

    private List<CatalogResponses.Terminal> toResponses(UUID tenantId, List<PosTerminal> terminals) {
        Map<UUID, String> venues = venueRepository.findByTenantIdOrderByName(tenantId).stream()
                .collect(Collectors.toMap(Venue::getId, Venue::getName));
        Map<UUID, String> warehouses = warehouseRepository.findByTenantIdOrderByName(tenantId).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        Map<UUID, CashSession> open = new LinkedHashMap<>();
        terminals.forEach(t -> cashSessionRepository.findByTerminalIdAndStatus(t.getId(), CashSessionStatus.OPEN)
                .ifPresent(s -> open.put(t.getId(), s)));
        Map<UUID, String> users = userNames.of(open.values().stream().map(CashSession::getOpenedBy).toList());
        return terminals.stream()
                .map(t -> {
                    CashSession s = open.get(t.getId());
                    return posMapper.toResponse(t, venues.get(t.getVenueId()), warehouses.get(t.getWarehouseId()), s,
                            s == null ? null : users.get(s.getOpenedBy()));
                })
                .toList();
    }
}
