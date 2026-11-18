package com.bloop.pos.entity;

public enum PosSaleStatus {
    COMPLETED,
    /** Anulada por un supervisor: devuelve el stock y no cuenta en la caja. */
    VOIDED
}
