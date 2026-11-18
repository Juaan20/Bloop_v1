package com.bloop.orders.dto.response;

import java.math.BigDecimal;

/**
 * Resumen de los pedidos que cumplen los filtros (todas las paginas, no solo la visible).
 *
 * @param paidAmount     importe cobrado de los pedidos pagados
 * @param refundedAmount importe de los pedidos reembolsados
 */
public record OrderSummaryResponse(long orders, long paidOrders, long pendingOrders, long refundedOrders,
                                   long tickets, BigDecimal paidAmount, BigDecimal refundedAmount,
                                   BigDecimal discountAmount) {
}
