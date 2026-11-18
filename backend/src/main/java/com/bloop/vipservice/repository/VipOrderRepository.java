package com.bloop.vipservice.repository;

import com.bloop.vipservice.entity.VipOrder;
import com.bloop.vipservice.entity.VipOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VipOrderRepository extends JpaRepository<VipOrder, UUID> {

    @Query("""
            select distinct o from VipOrder o left join fetch o.items
            where o.reservation.id = :reservationId order by o.createdAt desc
            """)
    List<VipOrder> findByReservation(@Param("reservationId") UUID reservationId);

    @Query("""
            select distinct o from VipOrder o left join fetch o.items
            where o.reservation.id in :ids
            """)
    List<VipOrder> findByReservations(@Param("ids") Collection<UUID> reservationIds);

    Optional<VipOrder> findByReservationIdAndIdempotencyKey(UUID reservationId, String idempotencyKey);

    /** Panel de servicio: pedidos abiertos y los terminados desde "since" de un local. */
    @Query("""
            select distinct o from VipOrder o left join fetch o.items join fetch o.reservation r join fetch r.table t
            join fetch t.zone z
            where o.tenantId = :tenantId and o.venueId = :venueId
              and (o.status in :open or o.updatedAt >= :since)
            order by o.createdAt asc
            """)
    List<VipOrder> findBoard(@Param("tenantId") UUID tenantId, @Param("venueId") UUID venueId,
                             @Param("open") Collection<VipOrderStatus> open, @Param("since") Instant since);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from VipOrder o where o.id = :id and o.tenantId = :tenantId")
    Optional<VipOrder> findByIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    long countByReservationIdAndStatusIn(UUID reservationId, Collection<VipOrderStatus> statuses);
}
