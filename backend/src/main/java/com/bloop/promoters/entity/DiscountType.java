package com.bloop.promoters.entity;

/** Tipo de descuento de un codigo promocional. */
public enum DiscountType {
    /** Porcentaje (1-100) sobre el subtotal del pedido. */
    PERCENTAGE,
    /** Importe fijo que se resta del subtotal (nunca por debajo de 0). */
    FIXED_AMOUNT
}
