package com.bloop.vipservice.controller;

import com.bloop.vipservice.dto.request.VipOrderRequest;
import com.bloop.vipservice.dto.response.GuestTableResponse;
import com.bloop.vipservice.dto.response.VipOrderResponse;
import com.bloop.vipservice.service.VipOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pagina de la mesa que se abre con el QR (publica, sin login). El token de 256 bits es la credencial:
 * solo funciona mientras la reserva esta en curso y el personal no lo haya desactivado.
 */
@RestController
@RequiredArgsConstructor
public class PublicVipTableController {

    private final VipOrderService orderService;

    @GetMapping("/api/public/vip-tables/{token}")
    public GuestTableResponse view(@PathVariable String token) {
        return orderService.guestView(token);
    }

    @PostMapping("/api/public/vip-tables/{token}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public VipOrderResponse order(@PathVariable String token, @Valid @RequestBody VipOrderRequest request) {
        return orderService.guestOrder(token, request);
    }
}
