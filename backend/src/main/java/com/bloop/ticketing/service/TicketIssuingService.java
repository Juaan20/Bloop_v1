package com.bloop.ticketing.service;

import com.bloop.orders.entity.OrderAttendee;
import java.util.HashMap;
import java.util.Map;

import com.bloop.events.entity.TicketType;
import com.bloop.shared.util.SecureTokens;
import com.bloop.ticketing.entity.Ticket;
import com.bloop.ticketing.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Emite las entradas de un pedido pagado. Se ejecuta dentro de la transaccion de confirmacion del pago. */
@Service
@RequiredArgsConstructor
public class TicketIssuingService {

    private final TicketRepository ticketRepository;
    private final Clock clock;

    /** Datos minimos de una linea de pedido necesarios para emitir sus entradas. */
    public record IssueLine(UUID orderItemId, TicketType ticketType, int quantity) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Ticket> issue(UUID tenantId, UUID orderId, UUID ownerUserId, String holderName, List<IssueLine> lines,
                              List<OrderAttendee> attendees) {
        Instant now = Instant.now(clock);
        List<Ticket> tickets = new ArrayList<>();
        // Asistentes por tipo de entrada, en el orden en que se indicaron: uno por entrada.
        Map<UUID, java.util.Deque<OrderAttendee>> byType = new HashMap<>();
        for (OrderAttendee a : attendees) {
            byType.computeIfAbsent(a.getTicketTypeId(), k -> new java.util.ArrayDeque<>()).add(a);
        }
        for (IssueLine line : lines) {
            for (int i = 0; i < line.quantity(); i++) {
                OrderAttendee attendee = byType.getOrDefault(line.ticketType().getId(), new java.util.ArrayDeque<>()).poll();
                Ticket ticket = new Ticket();
                ticket.setTenantId(tenantId);
                ticket.setOrderId(orderId);
                ticket.setOrderItemId(line.orderItemId());
                ticket.setTicketType(line.ticketType());
                ticket.setSession(line.ticketType().getSession());
                ticket.setOwnerUserId(ownerUserId);
                ticket.setHolderName(attendee != null ? attendee.getFullName() : holderName);
                if (attendee != null) {
                    ticket.setHolderBirthDate(attendee.getBirthDate());
                    ticket.setHolderDocumentType(attendee.getDocumentType());
                    ticket.setHolderDocumentLast4(attendee.getDocumentLast4());
                }
                ticket.setCode(SecureTokens.randomToken(20));
                ticket.setCreatedAt(now);
                tickets.add(ticket);
            }
        }
        return ticketRepository.saveAll(tickets);
    }
}
