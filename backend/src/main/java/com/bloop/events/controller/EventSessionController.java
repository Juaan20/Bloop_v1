package com.bloop.events.controller;

import com.bloop.events.dto.request.SessionRequest;
import com.bloop.events.dto.response.SessionResponse;
import com.bloop.events.service.EventSessionService;
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

/** Sesiones (fechas) de un evento. */
@RestController
@RequestMapping("/api/events/{eventId}/sessions")
@PreAuthorize("hasAuthority('EVENT_MANAGE')")
@RequiredArgsConstructor
public class EventSessionController {

    private final EventSessionService sessionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse add(@PathVariable UUID eventId, @Valid @RequestBody SessionRequest request) {
        return sessionService.add(eventId, request);
    }

    @PutMapping("/{sessionId}")
    public SessionResponse update(@PathVariable UUID eventId, @PathVariable UUID sessionId,
                                  @Valid @RequestBody SessionRequest request) {
        return sessionService.update(eventId, sessionId, request);
    }

    @PostMapping("/{sessionId}/cancel")
    public SessionResponse cancel(@PathVariable UUID eventId, @PathVariable UUID sessionId) {
        return sessionService.cancel(eventId, sessionId);
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID eventId, @PathVariable UUID sessionId) {
        sessionService.delete(eventId, sessionId);
    }
}
