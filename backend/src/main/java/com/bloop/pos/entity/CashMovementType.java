package com.bloop.pos.entity;

public enum CashMovementType {
    /** Entra efectivo en la caja (p. ej. mas cambio). */
    CASH_IN,
    /** Sale efectivo de la caja (p. ej. retirada a caja fuerte, pago a un proveedor). */
    CASH_OUT
}
