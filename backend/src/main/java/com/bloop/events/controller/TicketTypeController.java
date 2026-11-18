package com.bloop.events.controller;

import com.bloop.events.dto.request.TicketTypeRequest;
import com.bloop.events.dto.response.TicketTypeResponse;
import com.bloop.events.service.TicketTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Tipos de entrada de una sesion. */
@RestController
@RequestMapping("/api/events/{eventId}/sessions/{sessionId}/ticket-types")
@PreAuthorize("hasAuthority('EVENT_MANAGE')")
@RequiredArgsConstructor
public class TicketTypeController {

    private final TicketTypeService ticketTypeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketTypeResponse create(@PathVariable UUID eventId, @PathVariable UUID sessionId,
                                     @Valid @RequestBody TicketTypeRequest request) {
        return ticketTypeService.create(eventId, sessionId, request);
    }

    @PutMapping("/{ticketTypeId}")
    public TicketTypeResponse update(@PathVariable UUID eventId, @PathVariable UUID sessionId,
                                     @PathVariable UUID ticketTypeId, @Valid @RequestBody TicketTypeRequest request) {
        return ticketTypeService.update(eventId, sessionId, ticketTypeId, request);
    }

    @DeleteMapping("/{ticketTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID eventId, @PathVariable UUID sessionId, @PathVariable UUID ticketTypeId) {
        ticketTypeService.delete(eventId, sessionId, ticketTypeId);
    }
}
