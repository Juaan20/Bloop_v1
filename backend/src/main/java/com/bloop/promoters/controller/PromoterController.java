package com.bloop.promoters.controller;

import com.bloop.promoters.dto.request.CreatePromoterRequest;
import com.bloop.promoters.dto.request.PromoterAssignmentsRequest;
import com.bloop.promoters.dto.request.UpdatePromoterRequest;
import com.bloop.promoters.dto.response.PromoterCandidateResponse;
import com.bloop.promoters.dto.response.PromoterDetailResponse;
import com.bloop.promoters.dto.response.PromoterResponse;
import com.bloop.promoters.service.PromoterService;
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

/** Gestion de promotores / RRPP de la empresa (backoffice). */
@RestController
@RequestMapping("/api/promoters")
@PreAuthorize("hasAuthority('PROMOTER_MANAGE')")
@RequiredArgsConstructor
public class PromoterController {

    private final PromoterService promoterService;

    @GetMapping
    public List<PromoterResponse> list() {
        return promoterService.list();
    }

    /** Empleados con rol PROMOTER que se pueden dar de alta como promotores. */
    @GetMapping("/candidates")
    public List<PromoterCandidateResponse> candidates() {
        return promoterService.candidates();
    }

    @GetMapping("/{id}")
    public PromoterDetailResponse get(@PathVariable UUID id) {
        return promoterService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromoterDetailResponse create(@Valid @RequestBody CreatePromoterRequest request) {
        return promoterService.create(request);
    }

    @PutMapping("/{id}")
    public PromoterDetailResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePromoterRequest request) {
        return promoterService.update(id, request);
    }

    /** Sustituye los eventos asignados y sus objetivos. */
    @PutMapping("/{id}/events")
    public PromoterDetailResponse updateAssignments(@PathVariable UUID id,
                                                    @Valid @RequestBody PromoterAssignmentsRequest request) {
        return promoterService.updateAssignments(id, request);
    }
}
