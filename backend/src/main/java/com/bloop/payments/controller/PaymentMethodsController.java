package com.bloop.payments.controller;

import com.bloop.payments.service.PaymentService;
import com.bloop.shared.config.BloopProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Metodos de pago activos en este entorno, para que la web muestre solo los disponibles. */
@RestController
@RequestMapping("/api/public/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodsController {

    private final PaymentService paymentService;
    private final BloopProperties properties;

    @GetMapping
    public PaymentService.PaymentMethods methods() {
        return paymentService.availableMethods(properties.payments().simulatedEnabled());
    }
}
