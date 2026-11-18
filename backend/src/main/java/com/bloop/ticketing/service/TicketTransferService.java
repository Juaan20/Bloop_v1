package com.bloop.ticketing.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.events.entity.EventStatus;
import com.bloop.events.entity.SessionStatus;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.shared.util.SecureTokens;
import com.bloop.ticketing.dto.request.ChangeHolderRequest;
import com.bloop.ticketing.dto.request.TransferTicketRequest;
import com.bloop.ticketing.dto.response.MyTicketResponse;
import com.bloop.ticketing.entity.Ticket;
import com.bloop.ticketing.entity.TicketStatus;
import com.bloop.ticketing.entity.TicketTransfer;
import com.bloop.ticketing.entity.TransferType;
import com.bloop.ticketing.repository.TicketRepository;
import com.bloop.ticketing.repository.TicketTransferRepository;
import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import com.bloop.users.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Cambios de titular de una entrada.
 *
 * - RENAME: cambia solo el nombre que figura en la entrada (lo puede hacer el propietario
 *   o un empleado con ORDER_MANAGE). El QR no cambia.
 * - TRANSFER: el propietario pasa la entrada a otra cuenta. Se genera un codigo NUEVO,
 *   asi el QR anterior (una captura de pantalla, por ejemplo) deja de ser valido en la puerta.
 *
 * Solo se puede cambiar una entrada VALIDA de una sesion que no haya terminado ni se haya cancelado.
 */
@Service
@RequiredArgsConstructor
public class TicketTransferService {

    private final TicketRepository ticketRepository;
    private final TicketTransferRepository transferRepository;
    private final UserRepository userRepository;
    private final MyTicketService myTicketService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public MyTicketResponse renameMine(UUID ticketId, ChangeHolderRequest request) {
        UUID userId = currentUserProvider.get().userId();
        Ticket ticket = ticketRepository.findOfOwner(ticketId, userId)
                .orElseThrow(() -> NotFoundException.of("Entrada"));
        rename(ticket, request.holderName(), userId);
        return myTicketService.toResponse(ticket);
    }

    /** Cambio de nombre desde el backoffice (la entrada debe ser de un pedido de la empresa). */
    @Transactional
    public void renameByStaff(UUID orderId, UUID ticketId, ChangeHolderRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .filter(t -> t.getTenantId().equals(currentUserProvider.requireTenantId())
                        && t.getOrderId().equals(orderId))
                .orElseThrow(() -> NotFoundException.of("Entrada"));
        rename(ticket, request.holderName(), currentUserProvider.get().userId());
    }

    @Transactional
    public void transferMine(UUID ticketId, TransferTicketRequest request) {
        UUID userId = currentUserProvider.get().userId();
        Ticket ticket = ticketRepository.findOfOwner(ticketId, userId)
                .orElseThrow(() -> NotFoundException.of("Entrada"));
        assertChangeable(ticket);
        User sender = userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("Usuario"));
        User recipient = userRepository.findByEmail(UserAccountService.normalizeEmail(request.recipientEmail()))
                .filter(User::isActive)
                .orElseThrow(() -> new BadRequestException(
                        "No hay ninguna cuenta con ese email. Pide a esa persona que se registre en Bloop"));
        if (recipient.getId().equals(userId)) {
            throw new BadRequestException("No puedes transferirte una entrada a ti mismo");
        }
        String holder = request.holderName() == null || request.holderName().isBlank()
                ? recipient.getFullName() : request.holderName().trim();

        saveHistory(ticket, TransferType.TRANSFER, recipient.getId(), holder, userId);
        ticket.setOwnerUserId(recipient.getId());
        ticket.setHolderName(holder);
        ticket.setCode(SecureTokens.randomToken(20));
        auditService.recordInTenant(ticket.getTenantId(), AuditAction.TICKET_TRANSFERRED, "Ticket", ticket.getId(),
                "Entrada transferida a otra cuenta (QR regenerado)");
        eventPublisher.publishEvent(new TicketTransferredEvent(ticket.getId(), sender.getFullName()));
    }

    private void rename(Ticket ticket, String newName, UUID performedBy) {
        assertChangeable(ticket);
        String holder = newName.trim();
        if (holder.equals(ticket.getHolderName())) {
            return;
        }
        saveHistory(ticket, TransferType.RENAME, ticket.getOwnerUserId(), holder, performedBy);
        ticket.setHolderName(holder);
        auditService.recordInTenant(ticket.getTenantId(), AuditAction.TICKET_HOLDER_CHANGED, "Ticket", ticket.getId(),
                "Nuevo titular: " + holder);
    }

    private void assertChangeable(Ticket ticket) {
        if (ticket.getStatus() != TicketStatus.VALID) {
            throw new ConflictException("Solo se pueden modificar entradas validas (no usadas ni canceladas)");
        }
        var session = ticket.getSession();
        if (session.getStatus() == SessionStatus.CANCELLED
                || session.getEvent().getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("El evento esta cancelado");
        }
        if (!session.getEndsAt().isAfter(Instant.now(clock))) {
            throw new ConflictException("El evento ya ha terminado");
        }
    }

    private void saveHistory(Ticket ticket, TransferType type, UUID toUserId, String toHolder, UUID performedBy) {
        TicketTransfer transfer = new TicketTransfer();
        transfer.setTenantId(ticket.getTenantId());
        transfer.setTicketId(ticket.getId());
        transfer.setFromUserId(ticket.getOwnerUserId());
        transfer.setToUserId(toUserId);
        transfer.setFromHolderName(ticket.getHolderName());
        transfer.setToHolderName(toHolder);
        transfer.setType(type);
        transfer.setPerformedBy(performedBy);
        transfer.setCreatedAt(Instant.now(clock));
        transferRepository.save(transfer);
    }
}
