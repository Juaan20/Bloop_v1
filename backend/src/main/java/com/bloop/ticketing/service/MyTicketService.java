package com.bloop.ticketing.service;

import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.ticketing.dto.response.MyTicketResponse;
import com.bloop.ticketing.entity.Ticket;
import com.bloop.ticketing.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Entradas del cliente autenticado (zona "Mi cuenta" de la web publica). */
@Service
@RequiredArgsConstructor
public class MyTicketService {

    private final TicketRepository ticketRepository;
    private final QrCodeService qrCodeService;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public List<MyTicketResponse> listMine() {
        return ticketRepository.findAllOfOwner(currentUserProvider.get().userId()).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MyTicketResponse getMine(UUID id) {
        return ticketRepository.findOfOwner(id, currentUserProvider.get().userId())
                .map(this::toResponse)
                .orElseThrow(() -> NotFoundException.of("Entrada"));
    }

    MyTicketResponse toResponse(Ticket t) {
        var session = t.getSession();
        var event = session.getEvent();
        var venue = event.getVenue();
        return new MyTicketResponse(t.getId(), t.getOrderId(), event.getName(), event.getSlug(), venue.getName(),
                venue.getAddressLine(), venue.getCity(), session.getStartsAt(), session.getDoorsOpenAt(),
                t.getTicketType().getName(), t.getHolderName(), t.getStatus(), t.getUsedAt(),
                qrCodeService.payloadFor(t.getCode()), session.getTitle(),
                // Cartel del dia si lo tiene; si no, la imagen general del evento.
                session.getImageUrl() != null ? session.getImageUrl() : event.getImageUrl(), session.getEndsAt(),
                event.getMinAge());
    }
}
