package com.bloop.ticketing.repository;

import com.bloop.ticketing.entity.TicketTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketTransferRepository extends JpaRepository<TicketTransfer, UUID> {

    List<TicketTransfer> findByTicketIdOrderByCreatedAtDesc(UUID ticketId);
}
