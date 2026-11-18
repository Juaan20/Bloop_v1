package com.bloop.inventory.repository;

import com.bloop.inventory.entity.ContainerReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ContainerReturnRepository extends JpaRepository<ContainerReturn, UUID> {

    /** Envases devueltos por proveedor y producto: [supplierId, variantId, cantidad]. */
    @Query("""
            select r.supplierId, r.variantId, sum(r.quantity) from ContainerReturn r
            where r.tenantId = :tenantId group by r.supplierId, r.variantId
            """)
    List<Object[]> sumReturned(@Param("tenantId") UUID tenantId);
}
