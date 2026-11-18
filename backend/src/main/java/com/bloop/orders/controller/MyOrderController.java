package com.bloop.orders.controller;

import com.bloop.orders.dto.response.OrderResponse;
import com.bloop.orders.service.OrderQueryService;
import com.bloop.orders.service.OrderResendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Compras del usuario autenticado (zona "Mi cuenta"). */
@RestController
@RequestMapping("/api/me/orders")
// Zona de clientes: solo cuentas de cliente (un trabajador recibe 403).
@org.springframework.security.access.prepost.PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class MyOrderController {

    private final OrderQueryService orderQueryService;
    private final OrderResendService orderResendService;

    @GetMapping
    public List<OrderResponse> list() {
        return orderQueryService.listMine();
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return orderQueryService.getMine(id);
    }

    /** Reenvia por correo las entradas del pedido (maximo una vez por minuto). */
    @PostMapping("/{id}/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resend(@PathVariable UUID id) {
        orderResendService.resendMine(id);
    }
}
