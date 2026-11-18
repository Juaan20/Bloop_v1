package com.bloop.customers.controller;

import com.bloop.customers.dto.request.UpdateCustomerRequest;
import com.bloop.customers.dto.response.CustomerDetailResponse;
import com.bloop.customers.dto.response.CustomerExportResponse;
import com.bloop.customers.dto.response.CustomerResponse;
import com.bloop.customers.entity.CustomerSegment;
import com.bloop.customers.service.CustomerService;
import com.bloop.customers.service.GdprService;
import com.bloop.shared.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** CRM de clientes de la empresa (backoffice). */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final GdprService gdprService;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public PageResponse<CustomerResponse> list(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) CustomerSegment segment,
                                               @RequestParam(defaultValue = "false") boolean marketingOnly,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return customerService.list(search, segment, marketingOnly, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public CustomerDetailResponse get(@PathVariable UUID id) {
        return customerService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public CustomerDetailResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
        return customerService.update(id, request);
    }

    /** RGPD - derecho de acceso: todos los datos que la empresa guarda del cliente. */
    @GetMapping("/{id}/export")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public CustomerExportResponse export(@PathVariable UUID id) {
        return gdprService.exportForTenant(id);
    }

    /** RGPD - derecho de supresion: anonimiza la ficha (irreversible). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public void anonymize(@PathVariable UUID id) {
        gdprService.anonymizeForTenant(id);
    }
}
