package com.bloop.pos.controller;

import com.bloop.pos.dto.request.CashRequests;
import com.bloop.pos.dto.request.VoidSaleRequest;
import com.bloop.pos.dto.response.CashSessionResponse;
import com.bloop.pos.dto.response.PosSaleResponse;
import com.bloop.pos.service.CashSessionService;
import com.bloop.pos.service.PosSaleService;
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

/** Cajas del TPV (movimientos, cierre, informe) y anulacion de ventas. */
@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
public class CashSessionController {

    private final CashSessionService cashSessionService;
    private final PosSaleService saleService;

    /** Historial de cajas de la empresa (cierres e informes). */
    @GetMapping("/cash-sessions")
    @PreAuthorize("hasAuthority('POS_MANAGE')")
    public PageResponse<CashSessionResponse> history(@RequestParam(required = false) UUID terminalId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return cashSessionService.history(terminalId, page, size);
    }

    @GetMapping("/cash-sessions/{id}")
    @PreAuthorize("hasAnyAuthority('POS_SELL', 'POS_MANAGE')")
    public CashSessionResponse get(@PathVariable UUID id) {
        return cashSessionService.get(id);
    }

    @GetMapping("/cash-sessions/{id}/sales")
    @PreAuthorize("hasAnyAuthority('POS_SELL', 'POS_MANAGE')")
    public List<PosSaleResponse> sales(@PathVariable UUID id) {
        return cashSessionService.sales(id);
    }

    @PostMapping("/cash-sessions/{id}/movements")
    @PreAuthorize("hasAuthority('POS_SELL')")
    public CashSessionResponse addMovement(@PathVariable UUID id, @Valid @RequestBody CashRequests.Movement request) {
        return cashSessionService.addMovement(id, request);
    }

    /** Cierre con arqueo: devuelve el informe final de la caja. */
    @PostMapping("/cash-sessions/{id}/close")
    @PreAuthorize("hasAuthority('POS_SELL')")
    public CashSessionResponse close(@PathVariable UUID id, @Valid @RequestBody CashRequests.Close request) {
        return cashSessionService.close(id, request);
    }

    @GetMapping("/sales/{id}")
    @PreAuthorize("hasAnyAuthority('POS_SELL', 'POS_MANAGE')")
    public PosSaleResponse sale(@PathVariable UUID id) {
        return saleService.get(id);
    }

    /** Anulacion de una venta de una caja abierta (devuelve el stock). */
    @PostMapping("/sales/{id}/void")
    @PreAuthorize("hasAuthority('POS_SUPERVISE')")
    public PosSaleResponse voidSale(@PathVariable UUID id, @Valid @RequestBody VoidSaleRequest request) {
        return saleService.voidSale(id, request);
    }
}
