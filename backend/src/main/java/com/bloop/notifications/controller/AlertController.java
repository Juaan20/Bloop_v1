package com.bloop.notifications.controller;

import com.bloop.notifications.dto.response.AlertsResponse;
import com.bloop.notifications.dto.response.NotificationLogResponse;
import com.bloop.notifications.service.AdminAlertService;
import com.bloop.shared.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Alertas administrativas (cualquier empleado de la empresa) e historial de correos (AUDIT_VIEW).
 * El servicio exige que el usuario pertenezca a una empresa (requireTenantId => 403 si no).
 */
@RestController
@RequiredArgsConstructor
public class AlertController {

    private final AdminAlertService alertService;

    @GetMapping("/api/alerts")
    @PreAuthorize("isAuthenticated()")
    public AlertsResponse latest() {
        return alertService.latest();
    }

    @PostMapping("/api/alerts/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void markRead(@PathVariable UUID id) {
        alertService.markRead(id);
    }

    @PostMapping("/api/alerts/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void markAllRead() {
        alertService.markAllRead();
    }

    @GetMapping("/api/notifications")
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public PageResponse<NotificationLogResponse> emailLog(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "25") int size) {
        return alertService.emailLog(page, size);
    }
}
