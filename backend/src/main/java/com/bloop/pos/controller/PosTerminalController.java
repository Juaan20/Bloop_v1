package com.bloop.pos.controller;

import com.bloop.pos.dto.request.CashRequests;
import com.bloop.pos.dto.request.PosSaleRequest;
import com.bloop.pos.dto.request.TerminalRequest;
import com.bloop.pos.dto.response.CashSessionResponse;
import com.bloop.pos.dto.response.CatalogResponses;
import com.bloop.pos.dto.response.PosSaleResponse;
import com.bloop.pos.service.CashSessionService;
import com.bloop.pos.service.PosSaleService;
import com.bloop.pos.service.PosTerminalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Terminales del TPV. Gestion con POS_MANAGE; el uso (pantalla, abrir caja, vender) con POS_SELL
 * y solo en los locales donde trabaja el empleado.
 */
@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
public class PosTerminalController {

    private final PosTerminalService terminalService;
    private final CashSessionService cashSessionService;
    private final PosSaleService saleService;

    @GetMapping("/terminals")
    @PreAuthorize("hasAuthority('POS_MANAGE')")
    public List<CatalogResponses.Terminal> list() {
        return terminalService.list(false);
    }

    /** Terminales activos de los locales del usuario (pantalla de eleccion del TPV). */
    @GetMapping("/my-terminals")
    @PreAuthorize("hasAuthority('POS_SELL')")
    public List<CatalogResponses.Terminal> myTerminals() {
        return terminalService.list(true);
    }

    @PostMapping("/terminals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('POS_MANAGE')")
    public CatalogResponses.Terminal create(@Valid @RequestBody TerminalRequest request) {
        return terminalService.create(request);
    }

    @PutMapping("/terminals/{id}")
    @PreAuthorize("hasAuthority('POS_MANAGE')")
    public CatalogResponses.Terminal update(@PathVariable UUID id, @Valid @RequestBody TerminalRequest request) {
        return terminalService.update(id, request);
    }

    /** Todo lo que necesita la pantalla tactil: caja abierta y productos con existencias. */
    @GetMapping("/terminals/{id}/screen")
    @PreAuthorize("hasAuthority('POS_SELL')")
    public CatalogResponses.PosScreen screen(@PathVariable UUID id) {
        return terminalService.screen(id);
    }

    @PostMapping("/terminals/{id}/cash-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('POS_SELL')")
    public CashSessionResponse openCashSession(@PathVariable UUID id, @Valid @RequestBody CashRequests.Open request) {
        return cashSessionService.open(id, request);
    }

    /** Cobra un ticket. La cabecera Idempotency-Key evita cobrar dos veces el mismo ticket. */
    @PostMapping("/terminals/{id}/sales")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('POS_SELL')")
    public PosSaleResponse sell(@PathVariable UUID id,
                                @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                @Valid @RequestBody PosSaleRequest request) {
        return saleService.sell(id, request, idempotencyKey);
    }
}
