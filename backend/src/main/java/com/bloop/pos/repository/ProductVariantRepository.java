package com.bloop.pos.repository;

import com.bloop.pos.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    /** Variantes con su producto y categoria, solo de la empresa indicada. */
    @Query("""
            select v from ProductVariant v join fetch v.product p join fetch p.category c
            where v.id in :ids and v.tenantId = :tenantId
            """)
    List<ProductVariant> findAllWithProduct(@Param("ids") Collection<UUID> ids, @Param("tenantId") UUID tenantId);

    /** Variantes de productos con control de existencias (pantalla de inventario). */
    @Query("""
            select v from ProductVariant v join fetch v.product p join fetch p.category c
            where v.tenantId = :tenantId and p.trackStock = true
              and not exists (select r.id from VariantRecipeItem r where r.variant = v)
            order by c.sortOrder, c.name, p.sortOrder, p.name, v.sortOrder
            """)
    List<ProductVariant> findStockTracked(@Param("tenantId") UUID tenantId);
}
