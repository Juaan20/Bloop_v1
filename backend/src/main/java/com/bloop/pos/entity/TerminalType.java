package com.bloop.pos.entity;

/** Tipo de terminal: solo cambia la etiqueta en pantalla; ambos venden productos del catalogo. */
public enum TerminalType {
    /** Barra: bebidas y consumiciones. */
    BAR,
    /** Taquilla: entradas en puerta, guardarropa... (productos sin QR). */
    BOX_OFFICE,
    /** Servicio VIP: botellas para mesas; puede cargar la venta a la cuenta de una mesa ocupada. */
    VIP_SERVICE
}
