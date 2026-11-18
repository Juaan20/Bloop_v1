package com.bloop.reservations.service;

import com.bloop.pos.entity.ProductVariant;
import com.bloop.pos.repository.ProductVariantRepository;
import com.bloop.reservations.dto.request.VipLayoutRequest;
import com.bloop.reservations.dto.request.VipPackageRequest;
import com.bloop.reservations.entity.VipTablePackageItem;
import com.bloop.reservations.repository.ReservationRepository;
import com.bloop.shared.exception.BadRequestException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.reservations.dto.request.VipTableRequest;
import com.bloop.reservations.dto.request.VipZoneRequest;
import com.bloop.reservations.dto.response.VipZoneResponse;
import com.bloop.reservations.entity.VipTable;
import com.bloop.reservations.entity.VipZone;
import com.bloop.reservations.mapper.ReservationMapper;
import com.bloop.reservations.repository.VipTableRepository;
import com.bloop.reservations.repository.VipZoneRepository;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.venues.entity.Venue;
import com.bloop.venues.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Configuracion VIP de un local: zonas y mesas. No se borran (pueden tener reservas
 * historicas); se desactivan para que no admitan reservas nuevas.
 */
@Service
@RequiredArgsConstructor
public class VipLayoutService {

    private final VipZoneRepository zoneRepository;
    private final VipTableRepository tableRepository;
    private final VenueService venueService;
    private final ReservationMapper reservationMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final ProductVariantRepository variantRepository;
    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public List<VipZoneResponse> listZones(UUID venueId) {
        Venue venue = venueService.findInMyTenant(venueId);
        return zoneRepository.findByVenueIdAndTenantIdOrderBySortOrderAscNameAsc(venue.getId(), venue.getTenantId())
                .stream().map(reservationMapper::toZoneResponse).toList();
    }

    @Transactional
    public VipZoneResponse createZone(UUID venueId, VipZoneRequest request) {
        Venue venue = venueService.findInMyTenant(venueId);
        VipZone zone = new VipZone();
        zone.setTenantId(venue.getTenantId());
        zone.setVenueId(venue.getId());
        applyZone(zone, request);
        zone = zoneRepository.save(zone);
        auditService.record(AuditAction.VIP_TABLE_CHANGED, "VipZone", zone.getId(), "Nueva zona " + zone.getName());
        return reservationMapper.toZoneResponse(zone);
    }

    @Transactional
    public VipZoneResponse updateZone(UUID zoneId, VipZoneRequest request) {
        VipZone zone = findZone(zoneId);
        applyZone(zone, request);
        auditService.record(AuditAction.VIP_TABLE_CHANGED, "VipZone", zoneId, "Zona " + zone.getName()
                + " activa=" + zone.isActive());
        return reservationMapper.toZoneResponse(zone);
    }

    @Transactional
    public VipZoneResponse createTable(UUID zoneId, VipTableRequest request) {
        VipZone zone = findZone(zoneId);
        assertUniqueName(zone, request.name(), null);
        VipTable table = new VipTable();
        applyTable(table, request);
        zone.addTable(table);
        // flush (no save): la zona ya esta gestionada y la mesa nueva se persiste en cascada (ver CLAUDE.md).
        zoneRepository.flush();
        auditService.record(AuditAction.VIP_TABLE_CHANGED, "VipTable", table.getId(), "Nueva mesa " + table.getName());
        return reservationMapper.toZoneResponse(zone);
    }

    @Transactional
    public VipZoneResponse updateTable(UUID tableId, VipTableRequest request) {
        VipTable table = tableRepository.findByIdAndTenantId(tableId, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Mesa"));
        VipZone target = table.getZone();
        if (request.zoneId() != null && !request.zoneId().equals(target.getId())) {
            // Mover a otra sala: solo dentro del mismo local. Al cambiar de plano se quita su posicion.
            target = findZone(request.zoneId());
            if (!target.getVenueId().equals(table.getZone().getVenueId())) {
                throw new BadRequestException("La sala es de otro local");
            }
        }
        assertUniqueName(target, request.name(), table.getId());
        applyTable(table, request);
        if (target != table.getZone()) {
            table.getZone().getTables().remove(table);
            table.setZone(target);
            target.getTables().add(table);
            table.setPosX(null);
            table.setPosY(null);
        }
        auditService.record(AuditAction.VIP_TABLE_CHANGED, "VipTable", tableId, "Mesa " + table.getName()
                + " sala=" + target.getName() + " activa=" + table.isActive());
        return reservationMapper.toZoneResponse(target);
    }

    /** Guarda el plano: posicion, tamano y forma de las mesas del local (todas deben ser suyas). */
    @Transactional
    public List<VipZoneResponse> saveLayout(UUID venueId, VipLayoutRequest request) {
        Venue venue = venueService.findInMyTenant(venueId);
        List<VipZone> zones = zoneRepository.findByVenueIdAndTenantIdOrderBySortOrderAscNameAsc(venue.getId(),
                venue.getTenantId());
        Map<UUID, VipTable> tables = zones.stream().flatMap(z -> z.getTables().stream())
                .collect(Collectors.toMap(VipTable::getId, t -> t));
        for (VipLayoutRequest.TablePosition p : request.tables()) {
            VipTable table = tables.get(p.id());
            if (table == null) {
                throw NotFoundException.of("Mesa");
            }
            boolean placed = p.posX() != null && p.posY() != null;
            table.setPosX(placed ? p.posX() : null);
            table.setPosY(placed ? p.posY() : null);
            table.setWidth(p.width());
            table.setHeight(p.height());
            table.setShape(p.shape());
        }
        auditService.record(AuditAction.VIP_TABLE_CHANGED, "Venue", venueId, "Plano VIP guardado ("
                + request.tables().size() + " mesas)");
        return zones.stream().map(reservationMapper::toZoneResponse).toList();
    }

    /** Sustituye el paquete incluido de una mesa. Las reservas ya hechas conservan su copia. */
    @Transactional
    public VipZoneResponse setPackage(UUID tableId, VipPackageRequest request) {
        VipTable table = findTable(tableId);
        Set<UUID> ids = request.items().stream().map(VipPackageRequest.Item::variantId).collect(Collectors.toSet());
        if (ids.size() != request.items().size()) {
            throw new BadRequestException("Hay productos repetidos en el paquete");
        }
        Map<UUID, ProductVariant> variants = variantRepository.findAllWithProduct(ids, table.getTenantId()).stream()
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));
        table.getPackageItems().clear();
        tableRepository.flush(); // borra antes de insertar (restriccion unica mesa + variante)
        for (VipPackageRequest.Item item : request.items()) {
            ProductVariant variant = variants.get(item.variantId());
            if (variant == null || !variant.isSellable()) {
                throw new BadRequestException("Producto no disponible para el paquete");
            }
            VipTablePackageItem p = new VipTablePackageItem();
            p.setTenantId(table.getTenantId());
            p.setTable(table);
            p.setVariant(variant);
            p.setQuantity(item.quantity());
            table.getPackageItems().add(p);
        }
        tableRepository.flush();
        auditService.record(AuditAction.VIP_TABLE_CHANGED, "VipTable", tableId, "Paquete de " + table.getName()
                + ": " + request.items().size() + " productos");
        return reservationMapper.toZoneResponse(table.getZone());
    }

    /**
     * Borra una sala y sus mesas si ninguna mesa tiene reservas. Si ya hay historial, hay que
     * desactivarla (las reservas pasadas siguen apuntando a sus mesas).
     */
    @Transactional
    public void deleteZone(UUID zoneId) {
        VipZone zone = findZone(zoneId);
        boolean hasReservations = zone.getTables().stream().anyMatch(t -> reservationRepository.existsByTableId(t.getId()));
        if (hasReservations) {
            throw new ConflictException("La sala tiene mesas con reservas: desactivala en lugar de borrarla");
        }
        tableRepository.deleteAll(zone.getTables());
        zone.getTables().clear();
        zoneRepository.delete(zone);
        auditService.record(AuditAction.VIP_TABLE_CHANGED, "VipZone", zoneId, "Sala borrada " + zone.getName());
    }

    /** Borra una mesa sin reservas. Si ya tiene historial, hay que desactivarla para no perderlo. */
    @Transactional
    public void deleteTable(UUID tableId) {
        VipTable table = findTable(tableId);
        if (reservationRepository.existsByTableId(tableId)) {
            throw new ConflictException("La mesa tiene reservas: desactivala en lugar de borrarla");
        }
        table.getZone().getTables().remove(table);
        tableRepository.delete(table);
        auditService.record(AuditAction.VIP_TABLE_CHANGED, "VipTable", tableId, "Mesa borrada " + table.getName());
    }

    private VipTable findTable(UUID tableId) {
        return tableRepository.findByIdAndTenantId(tableId, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Mesa"));
    }

    private VipZone findZone(UUID zoneId) {
        return zoneRepository.findByIdAndTenantId(zoneId, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Zona"));
    }

    private static void assertUniqueName(VipZone zone, String name, UUID exceptTableId) {
        boolean taken = zone.getTables().stream()
                .anyMatch(t -> t.getName().equalsIgnoreCase(name.trim()) && !t.getId().equals(exceptTableId));
        if (taken) {
            throw new ConflictException("Ya hay una mesa llamada '" + name.trim() + "' en esta zona");
        }
    }

    private static void applyZone(VipZone zone, VipZoneRequest r) {
        zone.setName(r.name().trim());
        zone.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        zone.setActive(r.active());
        zone.setSortOrder(r.sortOrder());
        zone.setCapacity(r.capacity());
        zone.setColor(r.color() == null ? "violet" : r.color());
    }

    private static void applyTable(VipTable table, VipTableRequest r) {
        table.setName(r.name().trim());
        table.setCapacity(r.capacity());
        table.setMinSpend(r.minSpend());
        table.setDepositAmount(r.depositAmount());
        table.setActive(r.active());
        if (r.shape() != null) {
            table.setShape(r.shape());
        }
        if (r.width() != null) {
            table.setWidth(r.width());
        }
        if (r.height() != null) {
            table.setHeight(r.height());
        }
    }
}
