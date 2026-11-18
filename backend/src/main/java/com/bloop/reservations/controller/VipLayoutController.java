package com.bloop.reservations.controller;

import com.bloop.reservations.dto.request.VipLayoutRequest;
import com.bloop.reservations.dto.request.VipPackageRequest;
import com.bloop.reservations.dto.request.VipTableRequest;
import com.bloop.reservations.dto.request.VipZoneRequest;
import com.bloop.reservations.dto.response.VipZoneResponse;
import com.bloop.reservations.service.VipLayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Zonas y mesas VIP de un local (se configuran con VENUE_MANAGE). */
@RestController
@RequiredArgsConstructor
public class VipLayoutController {

    private final VipLayoutService vipLayoutService;
    private final com.bloop.reservations.service.FloorPlanService floorPlanService;

    @GetMapping("/api/venues/{venueId}/vip-zones")
    @PreAuthorize("hasAnyAuthority('VENUE_VIEW', 'RESERVATION_VIEW')")
    public List<VipZoneResponse> list(@PathVariable UUID venueId) {
        return vipLayoutService.listZones(venueId);
    }

    @PostMapping("/api/venues/{venueId}/vip-zones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VENUE_MANAGE')")
    public VipZoneResponse createZone(@PathVariable UUID venueId, @Valid @RequestBody VipZoneRequest request) {
        return vipLayoutService.createZone(venueId, request);
    }

    @PutMapping("/api/vip-zones/{zoneId}")
    @PreAuthorize("hasAuthority('VENUE_MANAGE')")
    public VipZoneResponse updateZone(@PathVariable UUID zoneId, @Valid @RequestBody VipZoneRequest request) {
        return vipLayoutService.updateZone(zoneId, request);
    }

    @PostMapping("/api/vip-zones/{zoneId}/tables")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VENUE_MANAGE')")
    public VipZoneResponse createTable(@PathVariable UUID zoneId, @Valid @RequestBody VipTableRequest request) {
        return vipLayoutService.createTable(zoneId, request);
    }

    @PutMapping("/api/vip-tables/{tableId}")
    @PreAuthorize("hasAuthority('VENUE_MANAGE')")
    public VipZoneResponse updateTable(@PathVariable UUID tableId, @Valid @RequestBody VipTableRequest request) {
        return vipLayoutService.updateTable(tableId, request);
    }

    /** Borra una sala sin reservas (con historial: desactivarla). */
    @DeleteMapping("/api/vip-zones/{zoneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('VENUE_MANAGE')")
    public void deleteZone(@PathVariable UUID zoneId) {
        vipLayoutService.deleteZone(zoneId);
    }

    /** Forma de una sala (rejilla pintada + elementos). null si aun no se ha disenado. */
    @GetMapping("/api/vip-zones/{zoneId}/floor-plan")
    @PreAuthorize("hasAnyAuthority('VENUE_VIEW', 'RESERVATION_VIEW', 'VIP_SERVICE')")
    public com.bloop.reservations.dto.request.FloorPlanRequest floorPlan(@PathVariable UUID zoneId) {
        return floorPlanService.get(zoneId);
    }

    @PutMapping("/api/vip-zones/{zoneId}/floor-plan")
    @PreAuthorize("hasAuthority('VENUE_MANAGE')")
    public com.bloop.reservations.dto.request.FloorPlanRequest saveFloorPlan(@PathVariable UUID zoneId,
            @Valid @RequestBody com.bloop.reservations.dto.request.FloorPlanRequest request) {
        return floorPlanService.save(zoneId, request);
    }

    /** Guarda el plano del local (posiciones, tamanos y formas de las mesas). */
    @PutMapping("/api/venues/{venueId}/vip-layout")
    @PreAuthorize("hasAuthority('VENUE_MANAGE')")
    public List<VipZoneResponse> saveLayout(@PathVariable UUID venueId, @Valid @RequestBody VipLayoutRequest request) {
        return vipLayoutService.saveLayout(venueId, request);
    }

    @PutMapping("/api/vip-tables/{tableId}/package")
    @PreAuthorize("hasAuthority('VENUE_MANAGE')")
    public VipZoneResponse setPackage(@PathVariable UUID tableId, @Valid @RequestBody VipPackageRequest request) {
        return vipLayoutService.setPackage(tableId, request);
    }

    @DeleteMapping("/api/vip-tables/{tableId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('VENUE_MANAGE')")
    public void deleteTable(@PathVariable UUID tableId) {
        vipLayoutService.deleteTable(tableId);
    }
}
