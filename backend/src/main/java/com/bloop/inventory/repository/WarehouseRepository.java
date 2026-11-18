package com.bloop.inventory.repository;

import com.bloop.inventory.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    List<Warehouse> findByTenantIdOrderByName(UUID tenantId);

    Optional<Warehouse> findByIdAndTenantId(UUID id, UUID tenantId);
}
