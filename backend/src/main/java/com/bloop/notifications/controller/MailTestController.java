package com.bloop.notifications.controller;

import com.bloop.notifications.dto.response.TestEmailResponse;
import com.bloop.notifications.service.MailTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Diagnostico del correo: envia un correo de prueba al usuario que lo pide. */
@RestController
@RequiredArgsConstructor
public class MailTestController {

    private final MailTestService mailTestService;

    @PostMapping("/api/notifications/test-email")
    @PreAuthorize("hasAuthority('TENANT_SETTINGS')")
    public TestEmailResponse sendTest() {
        return mailTestService.sendToMe();
    }
}
