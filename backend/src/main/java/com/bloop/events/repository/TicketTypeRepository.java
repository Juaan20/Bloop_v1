package com.bloop.events.repository;

import com.bloop.events.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    @Query("select t from TicketType t join fetch t.session s join fetch s.event e where t.id in :ids")
    List<TicketType> findAllWithSessionByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * Reserva stock de forma ATOMICA. La condicion del WHERE se evalua en PostgreSQL con
     * la fila bloqueada, asi que dos compras simultaneas nunca pueden superar quantity_total.
     *
     * @return 1 si se reservo, 0 si no habia stock suficiente
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update TicketType t set t.quantitySold = t.quantitySold + :quantity
            where t.id = :id and t.quantitySold + :quantity <= t.quantityTotal
            """)
    int reserveStock(@Param("id") UUID id, @Param("quantity") int quantity);

    /** Cantidades actuales leidas de la BD (no de la entidad en memoria, que puede estar desfasada). */
    @Query("select new com.bloop.events.repository.TicketStock(t.quantitySold, t.quantityTotal) "
            + "from TicketType t where t.id = :id")
    java.util.Optional<TicketStock> findStock(@Param("id") UUID id);

    /** Devuelve stock (pedido caducado o cancelado). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update TicketType t set t.quantitySold = t.quantitySold - :quantity
            where t.id = :id and t.quantitySold >= :quantity
            """)
    int releaseStock(@Param("id") UUID id, @Param("quantity") int quantity);
}
