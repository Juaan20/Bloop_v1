package com.bloop.promoters.repository;

import com.bloop.promoters.entity.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {

    List<PromoCode> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<PromoCode> findByPromoterIdOrderByCreatedAtDesc(UUID promoterId);

    Optional<PromoCode> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<PromoCode> findByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    /**
     * Consume un uso del codigo de forma ATOMICA (mismo patron que el stock de entradas):
     * si dos clientes usan a la vez el ultimo uso disponible, solo uno obtiene 1.
     *
     * @return 1 si se consumio el uso, 0 si el codigo ya estaba agotado
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update PromoCode p set p.timesUsed = p.timesUsed + 1
            where p.id = :id and (p.maxUses is null or p.timesUsed < p.maxUses)
            """)
    int reserveUse(@Param("id") UUID id);

    /** Devuelve un uso (el pedido caduco o se cancelo sin pagar). */
    @Modifying(flushAutomatically = true)
    @Query("update PromoCode p set p.timesUsed = p.timesUsed - 1 where p.id = :id and p.timesUsed > 0")
    int releaseUse(@Param("id") UUID id);
}
