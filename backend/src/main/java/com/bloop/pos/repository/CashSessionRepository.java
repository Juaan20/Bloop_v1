package com.bloop.pos.repository;

import com.bloop.pos.entity.CashSession;
import com.bloop.pos.entity.CashSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CashSessionRepository extends JpaRepository<CashSession, UUID> {

    Optional<CashSession> findByTerminalIdAndStatus(UUID terminalId, CashSessionStatus status);

    Optional<CashSession> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * BLOQUEO POR TERMINAL: la caja abierta del terminal con SELECT ... FOR UPDATE.
     * Las ventas, anulaciones, movimientos y el cierre de una misma caja se ejecutan de una en una,
     * asi el cierre nunca se cuela en mitad de una venta ni se vende con la caja ya cerrada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from CashSession c
            where c.terminalId = :terminalId and c.status = com.bloop.pos.entity.CashSessionStatus.OPEN
            """)
    Optional<CashSession> findOpenForUpdate(@Param("terminalId") UUID terminalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CashSession c where c.id = :id")
    Optional<CashSession> findByIdForUpdate(@Param("id") UUID id);

    /** Historial de cajas de la empresa (opcionalmente de un terminal). */
    @Query("""
            select c from CashSession c
            where c.tenantId = :tenantId and (:terminalId is null or c.terminalId = :terminalId)
            order by c.openedAt desc
            """)
    Page<CashSession> search(@Param("tenantId") UUID tenantId, @Param("terminalId") UUID terminalId,
                             Pageable pageable);
}
