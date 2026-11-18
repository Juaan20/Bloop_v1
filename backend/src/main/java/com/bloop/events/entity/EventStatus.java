package com.bloop.events.entity;

/**
 * Ciclo de vida de un evento:
 * DRAFT (borrador, invisible) -> PUBLISHED (a la venta) -> CANCELLED (final).
 * Un evento publicado puede volver a DRAFT (despublicar) para ocultarlo temporalmente.
 */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED
}
