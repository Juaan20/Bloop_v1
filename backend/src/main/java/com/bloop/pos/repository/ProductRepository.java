package com.bloop.pos.repository;

import com.bloop.pos.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("""
            select p from Product p join fetch p.category c
            where p.tenantId = :tenantId
            order by c.sortOrder, c.name, p.sortOrder, p.name
            """)
    List<Product> findAllOfTenant(@Param("tenantId") UUID tenantId);

    @Query("select p from Product p join fetch p.category c where p.id = :id and p.tenantId = :tenantId")
    Optional<Product> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
