package com.bloop.vipservice.controller;

import com.bloop.vipservice.dto.request.VipAccountPaymentRequest;
import com.bloop.vipservice.dto.request.VipOrderRequest;
import com.bloop.vipservice.dto.request.VipOrderStatusRequest;
import com.bloop.vipservice.dto.response.VipMapResponse;
import com.bloop.vipservice.dto.response.VipOrderResponse;
import com.bloop.vipservice.dto.response.VipReservationDetailResponse;
import com.bloop.vipservice.service.VipOrderService;
import com.bloop.vipservice.service.VipTableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mesas VIP en servicio: plano en vivo, ficha, QR, cuenta y panel de pedidos del personal. */
@RestController
@RequiredArgsConstructor
public class VipServiceController {

    private final VipTableService tableService;
    private final VipOrderService orderService;

    @GetMapping("/api/venues/{venueId}/vip-map")
    @PreAuthorize("hasAnyAuthority('RESERVATION_VIEW', 'VIP_SERVICE')")
    public VipMapResponse map(@PathVariable UUID venueId, @RequestParam(required = false) UUID sessionId) {
        return tableService.map(venueId, sessionId);
    }

    /** Fiestas del local para el selector del plano VIP (en curso y proximas; past=true incluye 30 dias atras). */
    @GetMapping("/api/venues/{venueId}/vip-parties")
    @PreAuthorize("hasAnyAuthority('RESERVATION_VIEW', 'VIP_SERVICE')")
    public List<VipMapResponse.Party> parties(@PathVariable UUID venueId,
                                              @RequestParam(defaultValue = "false") boolean past) {
        return tableService.parties(venueId, past);
    }

    @GetMapping("/api/reservations/{id}/vip")
    @PreAuthorize("hasAnyAuthority('RESERVATION_VIEW', 'VIP_SERVICE')")
    public VipReservationDetailResponse detail(@PathVariable UUID id) {
        return tableService.detail(id);
    }

    @PostMapping("/api/reservations/{id}/table-qr")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public VipReservationDetailResponse generateQr(@PathVariable UUID id) {
        return tableService.generateQr(id);
    }

    @DeleteMapping("/api/reservations/{id}/table-qr")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public VipReservationDetailResponse deactivateQr(@PathVariable UUID id) {
        return tableService.deactivateQr(id);
    }

    @PostMapping("/api/reservations/{id}/account/payments")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public VipReservationDetailResponse pay(@PathVariable UUID id, @Valid @RequestBody VipAccountPaymentRequest request) {
        return tableService.pay(id, request);
    }

    @PostMapping("/api/reservations/{id}/account/close")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public VipReservationDetailResponse close(@PathVariable UUID id) {
        return tableService.close(id);
    }

    @PostMapping("/api/reservations/{id}/vip-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('VIP_SERVICE', 'RESERVATION_MANAGE')")
    public VipOrderResponse staffOrder(@PathVariable UUID id, @Valid @RequestBody VipOrderRequest request) {
        return orderService.staffOrder(id, request);
    }

    @GetMapping("/api/venues/{venueId}/vip-orders")
    @PreAuthorize("hasAuthority('VIP_SERVICE')")
    public List<VipOrderResponse> board(@PathVariable UUID venueId) {
        return orderService.board(venueId);
    }

    @PostMapping("/api/vip-orders/{id}/status")
    @PreAuthorize("hasAuthority('VIP_SERVICE')")
    public VipOrderResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody VipOrderStatusRequest request) {
        return orderService.changeStatus(id, request.status());
    }
}
