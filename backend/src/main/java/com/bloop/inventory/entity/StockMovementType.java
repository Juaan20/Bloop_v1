package com.bloop.inventory.entity;

/** Tipos de movimiento de inventario. */
public enum StockMovementType {
    /** Entrada de mercancia (compra a proveedor, traspaso...). */
    IN,
    /** Salida manual (rotura, invitacion, merma...). */
    OUT,
    /** Ajuste tras un recuento: deja las existencias en la cantidad contada. */
    ADJUSTMENT,
    /** Descuento automatico por una venta del TPV. */
    SALE,
    /** Devolucion al anular una venta del TPV. */
    SALE_VOID,
    /** Llega de otro almacen (p. ej. del almacen a la barra). */
    TRANSFER_IN,
    /** Sale hacia otro almacen. */
    TRANSFER_OUT
}
