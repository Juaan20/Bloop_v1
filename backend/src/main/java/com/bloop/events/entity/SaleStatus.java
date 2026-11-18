package com.bloop.events.entity;

/** Estado de venta calculado de un tipo de entrada en un momento dado (no se guarda en BD). */
public enum SaleStatus {
    ON_SALE,
    NOT_STARTED,
    ENDED,
    SOLD_OUT,
    INACTIVE,
    /** El organizador ha pausado la venta de ese dia. */
    PAUSED
}
