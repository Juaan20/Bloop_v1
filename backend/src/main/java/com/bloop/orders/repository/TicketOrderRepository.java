package com.bloop.orders.repository;

import com.bloop.orders.entity.OrderStatus;
import com.bloop.orders.entity.TicketOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketOrderRepository extends JpaRepository<TicketOrder, UUID> {

    Optional<TicketOrder> findByCustomerUserIdAndIdempotencyKey(UUID customerUserId, String idempotencyKey);

    Optional<TicketOrder> findByIdAndCustomerUserId(UUID id, UUID customerUserId);

    Optional<TicketOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Pedidos pagados de unas sesiones (para reembolsarlos si se cancela el evento o la sesion). */
    @Query("select o.id from TicketOrder o where o.session.id in :sessionIds and o.status = :status")
    List<UUID> findIdsBySessionIdsAndStatus(@Param("sessionIds") java.util.Collection<UUID> sessionIds,
                                           @Param("status") OrderStatus status);

    boolean existsByReference(String reference);

    /**
     * Carga el pedido con bloqueo de fila (SELECT ... FOR UPDATE). Mientras dure la transaccion,
     * cualquier otra que intente bloquear el mismo pedido espera. Asi un pago nunca se procesa dos veces.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from TicketOrder o where o.id = :id")
    Optional<TicketOrder> findByIdForUpdate(@Param("id") UUID id);

    @Query("select o.id from TicketOrder o where o.status = :status and o.expiresAt < :now")
    List<UUID> findIdsByStatusAndExpiresAtBefore(@Param("status") OrderStatus status, @Param("now") Instant now);

    @Query("""
            select o from TicketOrder o join fetch o.session s join fetch s.event e join fetch e.venue v
            where o.customerUserId = :customerId
            order by o.createdAt desc
            """)
    List<TicketOrder> findAllOfCustomer(@Param("customerId") UUID customerId);

    /** Historial de compras de un cliente en UNA empresa (ficha del CRM). */
    @Query("""
            select o from TicketOrder o join fetch o.session s join fetch s.event e
            where o.tenantId = :tenantId and o.customerUserId = :customerId
            order by o.createdAt desc
            """)
    List<TicketOrder> findAllOfCustomerInTenant(@Param("tenantId") UUID tenantId,
                                                @Param("customerId") UUID customerId);

    /**
     * RGPD: al suprimir una cuenta se borran nombre y email del comprador. Los importes se
     * conservan porque la empresa esta obligada a guardar sus registros contables.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TicketOrder o set o.buyerName = :name, o.buyerEmail = :email where o.customerUserId = :userId")
    int anonymizeBuyer(@Param("userId") UUID userId, @Param("name") String name, @Param("email") String email);

    /** Ventas PAGADAS de un promotor agrupadas por evento: pedidos, ingresos y comision. */
    @Query("""
            select new com.bloop.orders.repository.PromoterSalesRow(e.id, count(o), sum(o.totalAmount),
                   sum(o.commissionAmount))
            from TicketOrder o join o.session s join s.event e
            where o.promoterId = :promoterId and o.status = com.bloop.orders.entity.OrderStatus.PAID
            group by e.id
            """)
    List<PromoterSalesRow> sumPromoterSalesByEvent(@Param("promoterId") UUID promoterId);

    /** Entradas vendidas por un promotor agrupadas por evento (pedidos PAGADOS). */
    @Query("""
            select new com.bloop.orders.repository.EventTicketsRow(e.id, sum(i.quantity))
            from TicketOrderItem i join i.order o join o.session s join s.event e
            where o.promoterId = :promoterId and o.status = com.bloop.orders.entity.OrderStatus.PAID
            group by e.id
            """)
    List<EventTicketsRow> sumPromoterTicketsByEvent(@Param("promoterId") UUID promoterId);

    /**
     * Condiciones del listado de ventas del backoffice (siempre filtrado por tenant). Se reutilizan
     * en el listado, en su conteo y en el resumen para que los tres cuenten exactamente lo mismo.
     * Un filtro null (o search vacio) no filtra.
     */
    String ORDER_FILTER = """
            where o.tenantId = :tenantId
              and (:status is null or o.status = :status)
              and (:eventId is null or e.id = :eventId)
              and (:sessionId is null or s.id = :sessionId)
              and (:venueId is null or e.venue.id = :venueId)
              and (cast(:from as Instant) is null or o.createdAt >= :from)
              and (cast(:to as Instant) is null or o.createdAt < :to)
              and (:paymentStatus is null or exists (select p.id from Payment p
                   where p.orderId = o.id and p.status = :paymentStatus))
              and (:paymentProvider is null or exists (select p.id from Payment p
                   where p.orderId = o.id and p.provider = :paymentProvider))
              and (:ticketCategory is null or exists (select i.id from TicketOrderItem i
                   where i.order = o and i.ticketType.category = :ticketCategory))
              and (:search = '' or lower(o.reference) like lower(concat('%', :search, '%'))
                   or lower(o.buyerEmail) like lower(concat('%', :search, '%'))
                   or lower(o.buyerName) like lower(concat('%', :search, '%')))
            """;

    @Query(value = "select o from TicketOrder o join fetch o.session s join fetch s.event e join fetch e.venue v "
            + ORDER_FILTER,
            countQuery = "select count(o) from TicketOrder o join o.session s join s.event e " + ORDER_FILTER)
    Page<TicketOrder> search(@Param("tenantId") UUID tenantId, @Param("status") OrderStatus status,
                             @Param("eventId") UUID eventId, @Param("sessionId") UUID sessionId,
                             @Param("venueId") UUID venueId, @Param("from") Instant from, @Param("to") Instant to,
                             @Param("paymentStatus") com.bloop.payments.entity.PaymentStatus paymentStatus,
                             @Param("paymentProvider") com.bloop.payments.entity.PaymentProviderType paymentProvider,
                             @Param("ticketCategory") com.bloop.events.entity.TicketCategory ticketCategory,
                             @Param("search") String search, Pageable pageable);

    /** Totales de los pedidos filtrados: pedidos, pagados, pendientes, reembolsados, cobrado, reembolsado, descuentos. */
    @Query("""
            select count(o),
                   coalesce(sum(case when o.status = com.bloop.orders.entity.OrderStatus.PAID then 1 else 0 end), 0),
                   coalesce(sum(case when o.status = com.bloop.orders.entity.OrderStatus.PENDING_PAYMENT then 1 else 0 end), 0),
                   coalesce(sum(case when o.status = com.bloop.orders.entity.OrderStatus.REFUNDED then 1 else 0 end), 0),
                   coalesce(sum(case when o.status = com.bloop.orders.entity.OrderStatus.PAID then o.totalAmount else 0 end), 0),
                   coalesce(sum(case when o.status = com.bloop.orders.entity.OrderStatus.REFUNDED then o.totalAmount else 0 end), 0),
                   coalesce(sum(case when o.status = com.bloop.orders.entity.OrderStatus.PAID then o.discountAmount else 0 end), 0)
            from TicketOrder o join o.session s join s.event e
            """ + ORDER_FILTER)
    List<Object[]> summarize(@Param("tenantId") UUID tenantId, @Param("status") OrderStatus status,
                             @Param("eventId") UUID eventId, @Param("sessionId") UUID sessionId,
                             @Param("venueId") UUID venueId, @Param("from") Instant from, @Param("to") Instant to,
                             @Param("paymentStatus") com.bloop.payments.entity.PaymentStatus paymentStatus,
                             @Param("paymentProvider") com.bloop.payments.entity.PaymentProviderType paymentProvider,
                             @Param("ticketCategory") com.bloop.events.entity.TicketCategory ticketCategory,
                             @Param("search") String search);

    /** Entradas vendidas (pedidos pagados) que cumplen los mismos filtros. */
    @Query("""
            select coalesce(sum(i.quantity), 0) from TicketOrderItem i join i.order o join o.session s join s.event e
            """ + ORDER_FILTER + " and o.status = com.bloop.orders.entity.OrderStatus.PAID")
    long countPaidTickets(@Param("tenantId") UUID tenantId, @Param("status") OrderStatus status,
                          @Param("eventId") UUID eventId, @Param("sessionId") UUID sessionId,
                          @Param("venueId") UUID venueId, @Param("from") Instant from, @Param("to") Instant to,
                          @Param("paymentStatus") com.bloop.payments.entity.PaymentStatus paymentStatus,
                          @Param("paymentProvider") com.bloop.payments.entity.PaymentProviderType paymentProvider,
                          @Param("ticketCategory") com.bloop.events.entity.TicketCategory ticketCategory,
                          @Param("search") String search);
}
