package com.bloop.pos.repository;

import com.bloop.pos.entity.PaymentMethod;
import com.bloop.pos.entity.PosSale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosSaleRepository extends JpaRepository<PosSale, UUID> {

    Optional<PosSale> findByTerminalIdAndIdempotencyKey(UUID terminalId, String idempotencyKey);

    Optional<PosSale> findByIdAndTenantId(UUID id, UUID tenantId);

    List<PosSale> findByCashSessionIdOrderByCreatedAtDesc(UUID cashSessionId);

    boolean existsByReference(String reference);

    /** Total cobrado en una caja con un metodo de pago (solo ventas no anuladas). */
    @Query("""
            select coalesce(sum(s.totalAmount), 0) from PosSale s
            where s.cashSessionId = :sessionId and s.paymentMethod = :method
              and s.status = com.bloop.pos.entity.PosSaleStatus.COMPLETED
            """)
    BigDecimal sumCompleted(@Param("sessionId") UUID sessionId, @Param("method") PaymentMethod method);

    @Query("""
            select coalesce(sum(s.discountAmount), 0) from PosSale s
            where s.cashSessionId = :sessionId and s.status = com.bloop.pos.entity.PosSaleStatus.COMPLETED
            """)
    BigDecimal sumDiscounts(@Param("sessionId") UUID sessionId);

    long countByCashSessionIdAndStatus(UUID sessionId, com.bloop.pos.entity.PosSaleStatus status);

    /** Productos mas vendidos de una caja (unidades e importe). */
    @Query("""
            select new com.bloop.pos.repository.TopProductRow(i.productName, i.variantName, sum(i.quantity),
                   sum(i.lineTotal))
            from PosSaleItem i join i.sale s
            where s.cashSessionId = :sessionId and s.status = com.bloop.pos.entity.PosSaleStatus.COMPLETED
            group by i.productName, i.variantName
            order by sum(i.quantity) desc
            """)
    List<TopProductRow> topProducts(@Param("sessionId") UUID sessionId);
}
