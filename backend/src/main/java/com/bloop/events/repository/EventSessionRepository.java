package com.bloop.events.repository;

import com.bloop.events.entity.EventSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventSessionRepository extends JpaRepository<EventSession, UUID> {

    Optional<EventSession> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Fiestas de un local que terminan despues de "from" (para el selector del plano VIP). */
    @org.springframework.data.jpa.repository.Query("""
            select s from EventSession s join fetch s.event e
            where s.tenantId = :tenantId and e.venue.id = :venueId and s.endsAt > :from
              and s.status = com.bloop.events.entity.SessionStatus.SCHEDULED
              and e.status <> com.bloop.events.entity.EventStatus.CANCELLED
            order by s.startsAt
            """)
    java.util.List<EventSession> findPartiesOfVenue(
            @org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
            @org.springframework.data.repository.query.Param("venueId") UUID venueId,
            @org.springframework.data.repository.query.Param("from") java.time.Instant from);

    /** Sesion con su evento y local (reservas VIP de la web). */
    @Query("select s from EventSession s join fetch s.event e join fetch e.venue v where s.id = :id")
    Optional<EventSession> findWithEventById(@Param("id") UUID id);

    /**
     * Cartelera publica: sesiones futuras de eventos publicados en locales activos,
     * ordenadas por fecha. Una tarjeta por sesion (un evento con 3 fechas aparece 3 veces).
     */
    @Query(value = """
            select s from EventSession s join fetch s.event e join fetch e.venue v
            where e.status = com.bloop.events.entity.EventStatus.PUBLISHED
              and v.status = com.bloop.venues.entity.VenueStatus.ACTIVE
              and s.status = com.bloop.events.entity.SessionStatus.SCHEDULED
              and s.endsAt > :now
              and (:search = '' or lower(e.name) like lower(concat('%', :search, '%'))
                   or lower(v.name) like lower(concat('%', :search, '%')))
              and (:city = '' or lower(v.city) = lower(:city))
              and (:venueSlug = '' or v.slug = :venueSlug)
            order by s.startsAt asc
            """,
            countQuery = """
            select count(s) from EventSession s join s.event e join e.venue v
            where e.status = com.bloop.events.entity.EventStatus.PUBLISHED
              and v.status = com.bloop.venues.entity.VenueStatus.ACTIVE
              and s.status = com.bloop.events.entity.SessionStatus.SCHEDULED
              and s.endsAt > :now
              and (:search = '' or lower(e.name) like lower(concat('%', :search, '%'))
                   or lower(v.name) like lower(concat('%', :search, '%')))
              and (:city = '' or lower(v.city) = lower(:city))
              and (:venueSlug = '' or v.slug = :venueSlug)
            """)
    Page<EventSession> findPublicUpcoming(@Param("now") Instant now, @Param("search") String search,
                                          @Param("city") String city, @Param("venueSlug") String venueSlug,
                                          Pageable pageable);

    /** Sesiones en curso o proximas (para el control de acceso) en los locales indicados. */
    @Query("""
            select s from EventSession s join fetch s.event e join fetch e.venue v
            where s.tenantId = :tenantId
              and v.id in :venueIds
              and s.status = com.bloop.events.entity.SessionStatus.SCHEDULED
              and e.status <> com.bloop.events.entity.EventStatus.CANCELLED
              and s.endsAt > :from and s.startsAt < :to
            order by s.startsAt asc
            """)
    List<EventSession> findActiveForAccess(@Param("tenantId") UUID tenantId,
                                           @Param("venueIds") Collection<UUID> venueIds,
                                           @Param("from") Instant from, @Param("to") Instant to);
}
