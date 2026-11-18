package com.bloop.orders.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** Fila de la consulta de ventas de un promotor por evento (constructor expression de JPQL). */
public record PromoterSalesRow(UUID eventId, Long orders, BigDecimal revenue, BigDecimal commission) {
}
