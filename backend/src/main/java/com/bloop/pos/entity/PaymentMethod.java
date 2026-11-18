package com.bloop.pos.entity;

/**
 * Metodo de pago en el TPV. La tarjeta se cobra con el datafono del local: Bloop solo
 * registra que se pago con tarjeta, nunca datos de la tarjeta.
 */
public enum PaymentMethod {
    CASH,
    CARD
}
