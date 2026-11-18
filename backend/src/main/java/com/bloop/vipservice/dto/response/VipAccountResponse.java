package com.bloop.vipservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cuenta de una mesa VIP. Separa claramente:
 * - prepaidAmount: reserva + paquete inicial (se paga ANTES de entrar).
 * - extrasTotal: botellas adicionales pedidas en la mesa (se pagan despues).
 * - pendingTotal = extrasTotal - paidTotal.
 */
public record VipAccountResponse(BigDecimal prepaidAmount, boolean prepaid, Instant prepaidAt,
                                 BigDecimal extrasTotal, BigDecimal paidTotal, BigDecimal pendingTotal,
                                 long openOrders, boolean closed, Instant closedAt, boolean canClose,
                                 List<PackageLine> packageItems, List<Payment> payments) {

    /** Producto del paquete: unidades incluidas, ya pedidas y las que quedan. */
    public record PackageLine(UUID variantId, String productName, int included, int consumed, int remaining) {
    }

    public record Payment(UUID id, BigDecimal amount, String method, Instant createdAt) {
    }
}
