package com.bloop.events.controller;

import com.bloop.events.dto.request.CreateEventRequest;
import com.bloop.events.dto.request.UpdateEventRequest;
import com.bloop.events.dto.response.EventDetailResponse;
import com.bloop.events.dto.response.EventSummaryResponse;
import com.bloop.events.entity.EventStatus;
import com.bloop.events.service.EventService;
import com.bloop.shared.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Gestion de eventos en el backoffice. */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    @PreAuthorize("hasAuthority('EVENT_VIEW')")
    public PageResponse<EventSummaryResponse> list(@RequestParam(required = false) EventStatus status,
                                                   @RequestParam(required = false) UUID venueId,
                                                   @RequestParam(required = false) String search,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return eventService.list(status, venueId, search, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EVENT_VIEW')")
    public EventDetailResponse get(@PathVariable UUID id) {
        return eventService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EVENT_MANAGE')")
    public EventDetailResponse create(@Valid @RequestBody CreateEventRequest request) {
        return eventService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EVENT_MANAGE')")
    public EventDetailResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEventRequest request) {
        return eventService.update(id, request);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('EVENT_MANAGE')")
    public EventDetailResponse publish(@PathVariable UUID id) {
        return eventService.publish(id);
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAuthority('EVENT_MANAGE')")
    public EventDetailResponse unpublish(@PathVariable UUID id) {
        return eventService.unpublish(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('EVENT_MANAGE')")
    public EventDetailResponse cancel(@PathVariable UUID id) {
        return eventService.cancel(id);
    }
}
