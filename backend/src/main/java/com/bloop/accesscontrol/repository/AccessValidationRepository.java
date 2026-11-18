package com.bloop.accesscontrol.repository;

import com.bloop.accesscontrol.entity.AccessValidation;
import com.bloop.accesscontrol.entity.ValidationResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccessValidationRepository extends JpaRepository<AccessValidation, UUID> {

    Page<AccessValidation> findBySessionIdAndTenantIdOrderByCreatedAtDesc(UUID sessionId, UUID tenantId,
                                                                          Pageable pageable);

    long countBySessionIdAndResult(UUID sessionId, ValidationResult result);

    long countBySessionIdAndResultNot(UUID sessionId, ValidationResult result);
}
