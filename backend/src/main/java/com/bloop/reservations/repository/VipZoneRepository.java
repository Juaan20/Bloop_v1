package com.bloop.reservations.repository;

import com.bloop.reservations.entity.VipZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VipZoneRepository extends JpaRepository<VipZone, UUID> {

    List<VipZone> findByVenueIdAndTenantIdOrderBySortOrderAscNameAsc(UUID venueId, UUID tenantId);

    /** Zonas de un local para la web publica (el local ya se ha validado como publico). */
    List<VipZone> findByVenueIdAndActiveTrueOrderBySortOrderAscNameAsc(UUID venueId);

    Optional<VipZone> findByIdAndTenantId(UUID id, UUID tenantId);
}
