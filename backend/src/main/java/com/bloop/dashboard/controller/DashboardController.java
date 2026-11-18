package com.bloop.dashboard.controller;

import com.bloop.dashboard.dto.response.DashboardResponse;
import com.bloop.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    public DashboardResponse summary(@RequestParam(defaultValue = "30") int days,
                                     @RequestParam(required = false) UUID venueId) {
        return dashboardService.summary(days, venueId);
    }
}
