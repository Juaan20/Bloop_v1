package com.bloop.vipservice.repository;

import com.bloop.vipservice.entity.VipAccountPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VipAccountPaymentRepository extends JpaRepository<VipAccountPayment, UUID> {

    @Query("select coalesce(sum(p.amount), 0) from VipAccountPayment p where p.reservationId = :reservationId")
    BigDecimal sumByReservation(@Param("reservationId") UUID reservationId);

    List<VipAccountPayment> findByReservationIdOrderByCreatedAt(UUID reservationId);

    Optional<VipAccountPayment> findByReservationIdAndIdempotencyKey(UUID reservationId, String idempotencyKey);
}
