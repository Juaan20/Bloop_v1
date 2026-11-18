package com.bloop.reservations.repository;

import com.bloop.reservations.entity.Reservation;
import com.bloop.reservations.entity.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @Query("""
            select r from Reservation r join fetch r.table t join fetch t.zone z left join fetch r.session s
            left join fetch s.event e
            where r.id = :id and r.tenantId = :tenantId
            """)
    Optional<Reservation> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("""
            select r from Reservation r join fetch r.table t join fetch t.zone z left join fetch r.session s
            left join fetch s.event e
            where r.id = :id and r.customerUserId = :userId
            """)
    Optional<Reservation> findOfCustomer(@Param("id") UUID id, @Param("userId") UUID userId);

    /** Reserva con todos sus datos para componer los correos (sin filtro de empresa: uso interno). */
    @Query("""
            select r from Reservation r join fetch r.table t join fetch t.zone z left join fetch r.session s
            left join fetch s.event e
            where r.id = :id
            """)
    Optional<Reservation> findDetailedById(@Param("id") UUID id);

    boolean existsByReference(String reference);

    /**
     * Hay alguna reserva activa de la mesa que se cruce con [startsAt, endsAt)?
     * Es la comprobacion "amable" para dar un mensaje claro; la garantia real frente a
     * peticiones simultaneas es la restriccion EXCLUDE de PostgreSQL.
     */
    @Query("""
            select count(r) > 0 from Reservation r
            where r.table.id = :tableId and r.status in :statuses
              and r.startsAt < :endsAt and r.endsAt > :startsAt
            """)
    boolean existsOverlap(@Param("tableId") UUID tableId, @Param("startsAt") Instant startsAt,
                          @Param("endsAt") Instant endsAt, @Param("statuses") Collection<ReservationStatus> statuses);

    /** Mesas de un local ocupadas en un intervalo (para mostrar disponibilidad). */
    @Query("""
            select distinct r.table.id from Reservation r
            where r.venueId = :venueId and r.status in :statuses
              and r.startsAt < :endsAt and r.endsAt > :startsAt
            """)
    List<UUID> findBusyTableIds(@Param("venueId") UUID venueId, @Param("startsAt") Instant startsAt,
                                @Param("endsAt") Instant endsAt,
                                @Param("statuses") Collection<ReservationStatus> statuses);

    /** Listado del backoffice filtrado por empresa, fechas, local y estado. */
    @Query(value = """
            select r from Reservation r join fetch r.table t join fetch t.zone z left join fetch r.session s
            left join fetch s.event e
            where r.tenantId = :tenantId
              and r.startsAt >= :from and r.startsAt < :to
              and (:venueId is null or r.venueId = :venueId)
              and (:status is null or r.status = :status)
              and (:search = '' or lower(r.contactName) like lower(concat('%', :search, '%'))
                   or lower(r.contactEmail) like lower(concat('%', :search, '%'))
                   or lower(r.reference) like lower(concat('%', :search, '%')))
            order by r.startsAt asc, t.name asc
            """,
            countQuery = """
            select count(r) from Reservation r
            where r.tenantId = :tenantId
              and r.startsAt >= :from and r.startsAt < :to
              and (:venueId is null or r.venueId = :venueId)
              and (:status is null or r.status = :status)
              and (:search = '' or lower(r.contactName) like lower(concat('%', :search, '%'))
                   or lower(r.contactEmail) like lower(concat('%', :search, '%'))
                   or lower(r.reference) like lower(concat('%', :search, '%')))
            """)
    Page<Reservation> search(@Param("tenantId") UUID tenantId, @Param("from") Instant from, @Param("to") Instant to,
                             @Param("venueId") UUID venueId, @Param("status") ReservationStatus status,
                             @Param("search") String search, Pageable pageable);

    @Query("""
            select r from Reservation r join fetch r.table t join fetch t.zone z left join fetch r.session s
            left join fetch s.event e
            where r.customerUserId = :userId
            order by r.startsAt desc
            """)
    List<Reservation> findAllOfCustomerUser(@Param("userId") UUID userId);

    @Query("""
            select r from Reservation r join fetch r.table t join fetch t.zone z
            where r.customerId = :customerId
            order by r.startsAt desc
            """)
    List<Reservation> findAllOfCustomer(@Param("customerId") UUID customerId);

    long countByCustomerUserIdAndStatusInAndEndsAtAfter(UUID userId, Collection<ReservationStatus> statuses,
                                                        Instant now);

    long countByCustomerIdAndStatusInAndEndsAtAfter(UUID customerId, Collection<ReservationStatus> statuses,
                                                    Instant now);

    /** RGPD: borra los datos de contacto de las reservas de una cuenta que se suprime. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Reservation r set r.contactName = :name, r.contactEmail = :email, r.contactPhone = null,
                   r.notes = null
            where r.customerUserId = :userId
            """)
    int anonymizeContactOfUser(@Param("userId") UUID userId, @Param("name") String name,
                               @Param("email") String email);

    /** RGPD: borra los datos de contacto de las reservas ligadas a una ficha del CRM. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Reservation r set r.contactName = :name, r.contactEmail = :email, r.contactPhone = null,
                   r.notes = null
            where r.customerId = :customerId
            """)
    int anonymizeContactOfCustomer(@Param("customerId") UUID customerId, @Param("name") String name,
                                   @Param("email") String email);
    /** Reserva por el token de su QR de mesa (pagina publica de la mesa). */
    @Query("""
            select r from Reservation r join fetch r.table t join fetch t.zone z left join fetch r.session s
            left join fetch s.event e
            where r.tableToken = :token
            """)
    Optional<Reservation> findByTableToken(@Param("token") String token);

    /** Bloquea la fila de la reserva (pedidos y cobros de la cuenta de la mesa, sin carreras). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id and r.tenantId = :tenantId")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    /** Reservas activas o recien terminadas de un local que se cruzan con [from, to) (plano en vivo). */
    @Query("""
            select r from Reservation r join fetch r.table t join fetch t.zone z left join fetch r.session s
            left join fetch s.event e
            where r.venueId = :venueId and r.tenantId = :tenantId and r.status in :statuses
              and r.startsAt < :to and r.endsAt > :from
            """)
    List<Reservation> findInVenueBetween(@Param("venueId") UUID venueId, @Param("tenantId") UUID tenantId,
                                         @Param("from") Instant from, @Param("to") Instant to,
                                         @Param("statuses") Collection<ReservationStatus> statuses);

    boolean existsByTableId(UUID tableId);

    /** Todas las reservas de una fiesta en un local (activas e historial), con su mesa. */
    @Query("""
            select r from Reservation r join fetch r.table t join fetch t.zone z
            where r.venueId = :venueId and r.tenantId = :tenantId and r.session.id = :sessionId
            order by r.createdAt
            """)
    List<Reservation> findBySessionInVenue(@Param("venueId") UUID venueId, @Param("tenantId") UUID tenantId,
                                           @Param("sessionId") UUID sessionId);
}
