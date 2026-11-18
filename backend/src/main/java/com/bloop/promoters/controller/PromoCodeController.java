package com.bloop.promoters.controller;

import com.bloop.promoters.dto.request.PromoCodeRequest;
import com.bloop.promoters.dto.response.PromoCodeResponse;
import com.bloop.promoters.service.PromoCodeService;
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

/** Codigos promocionales de la empresa (backoffice). */
@RestController
@RequestMapping("/api/promo-codes")
@PreAuthorize("hasAuthority('PROMOTER_MANAGE')")
@RequiredArgsConstructor
public class PromoCodeController {

    private final PromoCodeService promoCodeService;

    @GetMapping
    public List<PromoCodeResponse> list() {
        return promoCodeService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromoCodeResponse create(@Valid @RequestBody PromoCodeRequest request) {
        return promoCodeService.create(request);
    }

    @PutMapping("/{id}")
    public PromoCodeResponse update(@PathVariable UUID id, @Valid @RequestBody PromoCodeRequest request) {
        return promoCodeService.update(id, request);
    }
}
