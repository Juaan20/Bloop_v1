package com.bloop.events.repository;

import com.bloop.events.entity.Event;
import com.bloop.events.entity.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    @EntityGraph(attributePaths = "venue")
    Optional<Event> findByIdAndTenantId(UUID id, UUID tenantId);

    @EntityGraph(attributePaths = "venue")
    Optional<Event> findBySlugAndStatus(String slug, EventStatus status);

    boolean existsBySlug(String slug);

    /** Cuantos de esos eventos pertenecen a la empresa (para validar asignaciones). */
    long countByIdInAndTenantId(java.util.Collection<UUID> ids, UUID tenantId);

    /** Listado del backoffice: siempre filtrado por tenant. Los filtros vacios se ignoran. */
    @Query(value = """
            select e from Event e join fetch e.venue v
            where e.tenantId = :tenantId
              and (:status is null or e.status = :status)
              and (:venueId is null or v.id = :venueId)
              and (:search = '' or lower(e.name) like lower(concat('%', :search, '%')))
            """,
            countQuery = """
            select count(e) from Event e
            where e.tenantId = :tenantId
              and (:status is null or e.status = :status)
              and (:venueId is null or e.venue.id = :venueId)
              and (:search = '' or lower(e.name) like lower(concat('%', :search, '%')))
            """)
    Page<Event> searchByTenant(@Param("tenantId") UUID tenantId, @Param("status") EventStatus status,
                               @Param("venueId") UUID venueId, @Param("search") String search, Pageable pageable);
}
