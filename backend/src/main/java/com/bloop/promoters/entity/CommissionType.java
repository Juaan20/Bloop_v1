package com.bloop.promoters.entity;

/** Forma de calcular la comision del promotor. */
public enum CommissionType {
    /** Porcentaje (0-100) del importe pagado del pedido. */
    PERCENTAGE,
    /** Importe fijo por cada entrada vendida. */
    FIXED_PER_TICKET
}
