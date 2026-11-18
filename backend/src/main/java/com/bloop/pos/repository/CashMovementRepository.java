package com.bloop.pos.repository;

import com.bloop.pos.entity.CashMovement;
import com.bloop.pos.entity.CashMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {

    List<CashMovement> findByCashSessionIdOrderByCreatedAt(UUID cashSessionId);

    @Query("""
            select coalesce(sum(m.amount), 0) from CashMovement m
            where m.cashSessionId = :sessionId and m.type = :type
            """)
    BigDecimal sumByType(@Param("sessionId") UUID sessionId, @Param("type") CashMovementType type);
}
