package com.bloop.customers.controller;

import com.bloop.customers.dto.request.DeleteAccountRequest;
import com.bloop.customers.dto.request.UpdateConsentRequest;
import com.bloop.customers.dto.response.MyConsentResponse;
import com.bloop.customers.dto.response.MyDataExportResponse;
import com.bloop.customers.service.GdprService;
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

/** "Mi cuenta > Privacidad": derechos RGPD del propio usuario. */
@RestController
@RequestMapping("/api/me")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class MyPrivacyController {

    private final GdprService gdprService;

    /** Empresas que tienen una ficha del usuario y su consentimiento comercial. */
    @GetMapping("/privacy/consents")
    public List<MyConsentResponse> consents() {
        return gdprService.myConsents();
    }

    @PutMapping("/privacy/consents/{customerId}")
    public MyConsentResponse updateConsent(@PathVariable UUID customerId,
                                           @Valid @RequestBody UpdateConsentRequest request) {
        return gdprService.updateMyConsent(customerId, request);
    }

    /** Derecho de acceso y portabilidad: descarga de todos los datos en JSON. */
    @GetMapping("/data-export")
    public MyDataExportResponse export() {
        return gdprService.exportMine();
    }

    /** Derecho de supresion: anonimiza la cuenta. Requiere la contrasena. */
    @PostMapping("/delete-account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
        gdprService.deleteMyAccount(request.password());
    }
}
