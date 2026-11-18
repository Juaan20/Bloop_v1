package com.bloop.accesscontrol.controller;

import com.bloop.accesscontrol.dto.request.ValidateTicketRequest;
import com.bloop.accesscontrol.dto.response.AccessSessionResponse;
import com.bloop.accesscontrol.dto.response.AccessValidationResponse;
import com.bloop.accesscontrol.dto.response.ValidationResultResponse;
import com.bloop.accesscontrol.service.AccessControlService;
import com.bloop.shared.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Control de acceso: validacion de QR en la puerta. */
@RestController
@RequestMapping("/api/access")
@PreAuthorize("hasAuthority('TICKET_VALIDATE')")
@RequiredArgsConstructor
public class AccessControlController {

    private final AccessControlService accessControlService;

    @GetMapping("/sessions")
    public List<AccessSessionResponse> sessions() {
        return accessControlService.activeSessions();
    }

    @PostMapping("/validate")
    public ValidationResultResponse validate(@Valid @RequestBody ValidateTicketRequest request) {
        return accessControlService.validate(request);
    }

    @GetMapping("/sessions/{sessionId}/validations")
    public PageResponse<AccessValidationResponse> history(@PathVariable UUID sessionId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return accessControlService.history(sessionId, page, size);
    }
}
