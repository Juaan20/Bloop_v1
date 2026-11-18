package com.bloop.promoters.repository;

import com.bloop.promoters.entity.Promoter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromoterRepository extends JpaRepository<Promoter, UUID> {

    List<Promoter> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<Promoter> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Promoter> findByUserId(UUID userId);

    Optional<Promoter> findByReferralCode(String referralCode);

    boolean existsByReferralCode(String referralCode);

    boolean existsByUserId(UUID userId);
}
