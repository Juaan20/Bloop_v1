package com.bloop.orders.controller;

import com.bloop.orders.service.StripeWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint que llama Stripe (no Angular). Es publico, pero solo acepta peticiones con una firma
 * Stripe-Signature valida. El cuerpo se recibe como texto para verificar la firma sobre los bytes exactos.
 */
@RestController
@RequestMapping("/api/payments/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeWebhookService stripeWebhookService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
                                        @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        stripeWebhookService.handle(payload, signature);
        return ResponseEntity.ok().build();
    }
}
