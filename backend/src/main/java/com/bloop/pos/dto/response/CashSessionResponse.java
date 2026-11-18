package com.bloop.pos.dto.response;

import com.bloop.pos.entity.CashMovementType;
import com.bloop.pos.entity.CashSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Caja (turno) con su resumen: ventas por metodo de pago, movimientos de efectivo,
 * efectivo esperado y productos mas vendidos. Es el "informe Z" al cerrar.
 */
public record CashSessionResponse(UUID id, UUID terminalId, String terminalName, CashSessionStatus status,
                                  String openedByName, Instant openedAt, BigDecimal openingFloat,
                                  String closedByName, Instant closedAt, BigDecimal countedCash,
                                  BigDecimal difference, String closingNotes, Summary summary) {

    /**
     * @param expectedCash fondo + ventas en efectivo + entradas - salidas (lo que deberia haber en el cajon)
     */
    public record Summary(BigDecimal cashSales, BigDecimal cardSales, BigDecimal totalSales, BigDecimal discounts,
                          BigDecimal cashIn, BigDecimal cashOut, BigDecimal expectedCash, long salesCount,
                          long voidedCount, List<TopProduct> topProducts, List<Movement> movements) {
    }

    public record TopProduct(String productName, String variantName, long quantity, BigDecimal total) {
    }

    public record Movement(UUID id, CashMovementType type, BigDecimal amount, String reason, String userName,
                           Instant createdAt) {
    }
}
