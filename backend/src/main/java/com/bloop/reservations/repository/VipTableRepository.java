package com.bloop.reservations.repository;

import com.bloop.reservations.entity.VipTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VipTableRepository extends JpaRepository<VipTable, UUID> {

    @Query("select t from VipTable t join fetch t.zone z where t.id = :id and t.tenantId = :tenantId")
    Optional<VipTable> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("select t from VipTable t join fetch t.zone z where t.id = :id")
    Optional<VipTable> findWithZoneById(@Param("id") UUID id);
}
