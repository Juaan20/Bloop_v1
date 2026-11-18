package com.bloop.venues.controller;

import com.bloop.venues.dto.request.CreateVenueRequest;
import com.bloop.venues.dto.request.UpdateVenueRequest;
import com.bloop.venues.dto.response.VenueResponse;
import com.bloop.venues.service.VenueService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Gestion de establecimientos de la empresa (backoffice). */
@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    @GetMapping
    @PreAuthorize("hasAuthority('VENUE_VIEW')")
    public List<VenueResponse> list() {
        return venueService.listMyVenues();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VENUE_VIEW')")
    public VenueResponse get(@PathVariable UUID id) {
        return venueService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VENUE_MANAGE')")
    public VenueResponse create(@Valid @RequestBody CreateVenueRequest request) {
        return venueService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('VENUE_MANAGE')")
    public VenueResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateVenueRequest request) {
        return venueService.update(id, request);
    }
}
