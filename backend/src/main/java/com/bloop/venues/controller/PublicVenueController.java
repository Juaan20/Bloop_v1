package com.bloop.venues.controller;

import com.bloop.venues.dto.response.PublicVenueResponse;
import com.bloop.venues.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints publicos (sin login) de locales para la web de clientes. */
@RestController
@RequestMapping("/api/public/venues")
@RequiredArgsConstructor
public class PublicVenueController {

    private final VenueService venueService;

    @GetMapping("/{slug}")
    public PublicVenueResponse get(@PathVariable String slug) {
        return venueService.getPublic(slug);
    }
}
