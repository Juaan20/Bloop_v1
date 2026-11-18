package com.bloop.orders.controller;

import com.bloop.orders.dto.request.CheckoutRequest;
import com.bloop.orders.dto.request.SimulatedPaymentRequest;
import com.bloop.orders.dto.response.OrderResponse;
import com.bloop.orders.dto.response.PaymentResultResponse;
import com.bloop.orders.service.CheckoutService;
import com.bloop.orders.service.OrderLifecycleService;
import com.bloop.orders.service.StripeCheckoutService;
import com.bloop.orders.dto.response.StripeSessionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Compra de entradas. Requiere usuario autenticado (cualquier rol). */
@RestController
@RequestMapping("/api/checkout")
// Zona de clientes: solo cuentas de cliente (un trabajador recibe 403).
@org.springframework.security.access.prepost.PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final OrderLifecycleService orderLifecycleService;
    private final StripeCheckoutService stripeCheckoutService;

    /** Crea el pedido y reserva el stock. La cabecera Idempotency-Key evita pedidos duplicados. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                  @Valid @RequestBody CheckoutRequest request) {
        return checkoutService.checkout(request, idempotencyKey);
    }

    /** Pago con tarjeta: crea la pagina de pago de Stripe (modo test) y devuelve su URL. */
    @PostMapping("/orders/{orderId}/stripe-session")
    public StripeSessionResponse stripeSession(@PathVariable UUID orderId) {
        return stripeCheckoutService.startCheckout(orderId);
    }

    /** Pasarela simulada (solo con bloop.payments.simulated-enabled=true). */
    @PostMapping("/orders/{orderId}/simulated-payment")
    public PaymentResultResponse paySimulated(@PathVariable UUID orderId,
                                              @RequestBody SimulatedPaymentRequest request) {
        return checkoutService.paySimulated(orderId, request.approve());
    }

    @PostMapping("/orders/{orderId}/cancel")
    public OrderResponse cancel(@PathVariable UUID orderId) {
        return orderLifecycleService.cancelMyPendingOrder(orderId);
    }
}
