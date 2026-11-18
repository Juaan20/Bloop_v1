package com.bloop.pos.repository;

import java.math.BigDecimal;

/** Unidades e importe vendidos de una variante en una caja (constructor expression de JPQL). */
public record TopProductRow(String productName, String variantName, Long quantity, BigDecimal total) {
}
