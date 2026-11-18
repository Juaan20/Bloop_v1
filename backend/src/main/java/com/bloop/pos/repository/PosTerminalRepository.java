package com.bloop.pos.repository;

import com.bloop.pos.entity.PosTerminal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosTerminalRepository extends JpaRepository<PosTerminal, UUID> {

    List<PosTerminal> findByTenantIdOrderByName(UUID tenantId);

    Optional<PosTerminal> findByIdAndTenantId(UUID id, UUID tenantId);
}
