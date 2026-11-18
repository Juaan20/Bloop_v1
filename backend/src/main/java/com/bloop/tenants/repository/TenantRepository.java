package com.bloop.tenants.repository;

import com.bloop.tenants.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    boolean existsBySlug(String slug);

    @Query("""
            select t from Tenant t
            where :search = '' or lower(t.name) like lower(concat('%', :search, '%'))
               or lower(t.contactEmail) like lower(concat('%', :search, '%'))
            """)
    Page<Tenant> search(@Param("search") String search, Pageable pageable);
}
