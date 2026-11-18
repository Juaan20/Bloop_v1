package com.bloop.ticketing.entity;

/** Tipo de cambio de titular. */
public enum TransferType {
    /** La entrada pasa a otra cuenta: cambia el propietario y se genera un QR nuevo. */
    TRANSFER,
    /** Solo cambia el nombre que figura en la entrada (mismo propietario y mismo QR). */
    RENAME
}
