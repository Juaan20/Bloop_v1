package com.bloop.venues.repository;

import com.bloop.venues.entity.Venue;
import com.bloop.venues.entity.VenueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

    List<Venue> findByTenantIdOrderByName(UUID tenantId);

    Optional<Venue> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Venue> findBySlugAndStatus(String slug, VenueStatus status);

    boolean existsBySlug(String slug);

    long countByIdInAndTenantId(Collection<UUID> ids, UUID tenantId);

    @Query("select distinct v.city from Venue v where v.status = com.bloop.venues.entity.VenueStatus.ACTIVE order by v.city")
    List<String> findActiveCities();
}
