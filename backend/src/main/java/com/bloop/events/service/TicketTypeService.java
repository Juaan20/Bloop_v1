package com.bloop.events.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.events.dto.request.TicketTypeRequest;
import com.bloop.events.dto.response.TicketTypeResponse;
import com.bloop.events.entity.Event;
import com.bloop.events.entity.EventSession;
import com.bloop.events.entity.TicketCategory;
import com.bloop.events.entity.TicketType;
import com.bloop.events.mapper.EventMapper;
import com.bloop.events.repository.EventRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Configuracion de tipos de entrada (precio, stock, limites y fechas de venta) de una sesion. */
@Service
@RequiredArgsConstructor
public class TicketTypeService {

    private final EventSessionService eventSessionService;
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public TicketTypeResponse create(UUID eventId, UUID sessionId, TicketTypeRequest request) {
        Event event = eventSessionService.findEvent(eventId);
        EventService.assertNotCancelled(event);
        EventSession session = EventSessionService.findSession(event, sessionId);
        assertFitsCapacity(session, null, request.quantityTotal());
        TicketType ticketType = new TicketType();
        apply(ticketType, request);
        session.addTicketType(ticketType);
        // flush (no save): el evento ya esta gestionado; Hibernate persiste en cascada el hijo nuevo y le asigna id.
        eventRepository.flush();
        auditService.record(AuditAction.TICKET_TYPE_CHANGED, "TicketType", ticketType.getId(),
                "Nuevo tipo " + ticketType.getName() + " precio=" + ticketType.getPrice());
        return eventMapper.toResponse(ticketType, Instant.now(clock));
    }

    @Transactional
    public TicketTypeResponse update(UUID eventId, UUID sessionId, UUID ticketTypeId, TicketTypeRequest request) {
        Event event = eventSessionService.findEvent(eventId);
        EventService.assertNotCancelled(event);
        EventSession session = EventSessionService.findSession(event, sessionId);
        TicketType ticketType = findTicketType(session, ticketTypeId);
        if (request.quantityTotal() < ticketType.getQuantitySold()) {
            throw new BadRequestException("No puedes dejar menos entradas que las ya vendidas ("
                    + ticketType.getQuantitySold() + ")");
        }
        if (ticketType.getQuantitySold() > 0 && request.price().compareTo(ticketType.getPrice()) != 0) {
            // Se permite, pero queda auditado: los pedidos antiguos conservan su precio en la linea de pedido.
            auditService.record(AuditAction.TICKET_TYPE_CHANGED, "TicketType", ticketTypeId,
                    "Cambio de precio con ventas: " + ticketType.getPrice() + " -> " + request.price());
        }
        assertFitsCapacity(session, ticketType, request.quantityTotal());
        apply(ticketType, request);
        auditService.record(AuditAction.TICKET_TYPE_CHANGED, "TicketType", ticketTypeId, "Tipo modificado");
        return eventMapper.toResponse(ticketType, Instant.now(clock));
    }

    @Transactional
    public void delete(UUID eventId, UUID sessionId, UUID ticketTypeId) {
        Event event = eventSessionService.findEvent(eventId);
        EventSession session = EventSessionService.findSession(event, sessionId);
        TicketType ticketType = findTicketType(session, ticketTypeId);
        if (ticketType.getQuantitySold() > 0) {
            throw new ConflictException("Este tipo tiene entradas vendidas: desactivalo en lugar de eliminarlo");
        }
        session.getTicketTypes().remove(ticketType);
        // Si existe algun pedido historico (p. ej. caducado) la FK lo impedira y se devolvera 409.
        eventRepository.flush();
        auditService.record(AuditAction.TICKET_TYPE_CHANGED, "TicketType", ticketTypeId, "Tipo eliminado");
    }

    /** La suma de entradas de todos los tipos no puede superar el aforo de la sesion. */
    private static void assertFitsCapacity(EventSession session, TicketType editing, int newQuantity) {
        int others = session.getTicketTypes().stream()
                .filter(t -> t != editing)
                .mapToInt(TicketType::getQuantityTotal)
                .sum();
        if (others + newQuantity > session.getCapacity()) {
            throw new BadRequestException("La suma de entradas (" + (others + newQuantity)
                    + ") supera el aforo de la sesion (" + session.getCapacity() + ")");
        }
    }

    private static void apply(TicketType ticketType, TicketTypeRequest r) {
        BigDecimal price = r.price().setScale(2, RoundingMode.HALF_UP);
        if (r.category() == TicketCategory.FREE && price.signum() != 0) {
            throw new BadRequestException("Una entrada gratuita debe tener precio 0");
        }
        if (r.salesStartAt() != null && r.salesEndAt() != null && !r.salesEndAt().isAfter(r.salesStartAt())) {
            throw new BadRequestException("El fin de venta debe ser posterior al inicio de venta");
        }
        ticketType.setName(r.name().trim());
        ticketType.setDescription(r.description());
        ticketType.setCategory(r.category());
        ticketType.setPrice(price);
        ticketType.setQuantityTotal(r.quantityTotal());
        ticketType.setMaxPerOrder(r.maxPerOrder());
        ticketType.setSalesStartAt(r.salesStartAt());
        ticketType.setSalesEndAt(r.salesEndAt());
        ticketType.setActive(r.active());
        ticketType.setSortOrder(r.sortOrder());
    }

    private static TicketType findTicketType(EventSession session, UUID ticketTypeId) {
        return session.getTicketTypes().stream()
                .filter(t -> t.getId().equals(ticketTypeId))
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("Tipo de entrada"));
    }
}
