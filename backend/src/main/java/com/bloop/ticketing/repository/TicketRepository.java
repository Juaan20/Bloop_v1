package com.bloop.ticketing.repository;

import com.bloop.ticketing.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Optional<Ticket> findByCode(String code);

    List<Ticket> findByOrderIdOrderByCreatedAtAscIdAsc(UUID orderId);

    @Query("""
            select t from Ticket t join fetch t.ticketType tt join fetch t.session s
            join fetch s.event e join fetch e.venue v
            where t.ownerUserId = :ownerId
            order by s.startsAt desc, t.createdAt asc
            """)
    List<Ticket> findAllOfOwner(@Param("ownerId") UUID ownerId);

    @Query("""
            select t from Ticket t join fetch t.ticketType tt join fetch t.session s
            join fetch s.event e join fetch e.venue v
            where t.id = :id and t.ownerUserId = :ownerId
            """)
    Optional<Ticket> findOfOwner(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    /**
     * Marca la entrada como usada SOLO si sigue VALID. Es una operacion atomica en PostgreSQL:
     * si dos porteros escanean el mismo QR a la vez, solo uno obtiene 1; el otro obtiene 0.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Ticket t set t.status = com.bloop.ticketing.entity.TicketStatus.USED,
                   t.usedAt = :now, t.usedByUserId = :userId
            where t.id = :id and t.status = com.bloop.ticketing.entity.TicketStatus.VALID
            """)
    int markUsedIfValid(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);

    /** Anula las entradas aun no usadas de un pedido (reembolso). Devuelve cuantas se anularon. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Ticket t set t.status = com.bloop.ticketing.entity.TicketStatus.CANCELLED
            where t.orderId = :orderId and t.status = com.bloop.ticketing.entity.TicketStatus.VALID
            """)
    int cancelValidByOrder(@Param("orderId") UUID orderId);

    long countByOrderIdAndStatus(UUID orderId, com.bloop.ticketing.entity.TicketStatus status);

    @Query("select t.usedAt from Ticket t where t.id = :id")
    Optional<Instant> findUsedAtById(@Param("id") UUID id);

    long countBySessionIdAndStatus(UUID sessionId, com.bloop.ticketing.entity.TicketStatus status);

    /** Entradas validas de un usuario para sesiones que aun no han terminado. */
    @Query("""
            select count(t) from Ticket t
            where t.ownerUserId = :ownerId and t.status = com.bloop.ticketing.entity.TicketStatus.VALID
              and t.session.endsAt > :now
            """)
    long countUpcomingValidOfOwner(@Param("ownerId") UUID ownerId, @Param("now") Instant now);

    /** RGPD: borra el nombre del titular de las entradas de un usuario que suprime su cuenta. */
    @Modifying(flushAutomatically = true)
    @Query("update Ticket t set t.holderName = :name where t.ownerUserId = :ownerId")
    int anonymizeHolder(@Param("ownerId") UUID ownerId, @Param("name") String name);

    /** Entradas de un pedido con su tipo y sesion (reenvio y detalle del backoffice). */
    @Query("""
            select t from Ticket t join fetch t.ticketType tt join fetch t.session s
            where t.orderId = :orderId order by t.createdAt
            """)
    List<Ticket> findWithTypeByOrderId(@Param("orderId") UUID orderId);

    /** Titulares (cuenta propietaria) de las entradas validas de unas sesiones, para avisarles de cambios. */
    @Query("""
            select distinct new com.bloop.ticketing.repository.TicketHolderContact(u.email, u.firstName)
            from Ticket t join com.bloop.users.entity.User u on u.id = t.ownerUserId
            where t.session.id in :sessionIds and t.status = com.bloop.ticketing.entity.TicketStatus.VALID
            """)
    List<TicketHolderContact> findHolderContacts(@Param("sessionIds") java.util.Collection<UUID> sessionIds);
}
