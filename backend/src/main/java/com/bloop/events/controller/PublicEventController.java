package com.bloop.events.controller;

import com.bloop.events.dto.response.PublicEventCardResponse;
import com.bloop.events.dto.response.PublicEventDetailResponse;
import com.bloop.events.service.PublicEventService;
import com.bloop.shared.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Cartelera y ficha de eventos de la web publica (sin login). */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicEventController {

    private final PublicEventService publicEventService;

    @GetMapping("/events")
    public PageResponse<PublicEventCardResponse> list(@RequestParam(required = false) String search,
                                                      @RequestParam(required = false) String city,
                                                      @RequestParam(required = false) String venue,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "12") int size) {
        return publicEventService.listUpcoming(search, city, venue, page, size);
    }

    @GetMapping("/events/{slug}")
    public PublicEventDetailResponse get(@PathVariable String slug) {
        return publicEventService.getBySlug(slug);
    }

    @GetMapping("/cities")
    public List<String> cities() {
        return publicEventService.cities();
    }
}
