package com.bloop.pos.entity;

/** Unidad en la que se cuentan las existencias de una variante. */
public enum StockUnit {
    /** Unidades (latas, botellines, botellas que se venden enteras y se cuentan enteras). */
    UNIT,
    /** Mililitros: botellas de las que se sirven copas. unitSizeMl dice cuanto trae cada botella. */
    ML
}
