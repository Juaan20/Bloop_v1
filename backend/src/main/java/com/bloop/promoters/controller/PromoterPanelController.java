package com.bloop.promoters.controller;

import com.bloop.promoters.dto.response.PromoterDetailResponse;
import com.bloop.promoters.service.PromoterService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Panel individual del promotor autenticado: sus eventos, enlaces, ventas, comisiones y objetivos. */
@RestController
@RequestMapping("/api/promoter")
@RequiredArgsConstructor
public class PromoterPanelController {

    private final PromoterService promoterService;

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('PROMOTER_PANEL')")
    public PromoterDetailResponse myPanel() {
        return promoterService.myPanel();
    }
}
