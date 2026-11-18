package com.bloop.vipservice.controller;

import com.bloop.vipservice.dto.request.VipOrderRequest;
import com.bloop.vipservice.dto.response.VipOrderResponse;
import com.bloop.vipservice.service.PosVipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** TPV de servicio VIP: mesas ocupadas y carga de botellas a la cuenta de una mesa. */
@RestController
@RequiredArgsConstructor
public class PosVipController {

    private final PosVipService posVipService;

    public record ChargeRequest(@NotNull UUID reservationId, @NotNull @Valid VipOrderRequest order) {
    }

    @GetMapping("/api/pos/terminals/{id}/vip-tables")
    @PreAuthorize("hasAuthority('POS_SELL')")
    public List<PosVipService.OccupiedTable> occupiedTables(@PathVariable UUID id) {
        return posVipService.occupiedTables(id);
    }

    @PostMapping("/api/pos/terminals/{id}/vip-charge")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('POS_SELL')")
    public VipOrderResponse charge(@PathVariable UUID id, @Valid @RequestBody ChargeRequest request) {
        return posVipService.charge(id, request.reservationId(), request.order());
    }
}
