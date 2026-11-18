package com.bloop.events.repository;

/** Cantidades actuales de un tipo de entrada leidas directamente de la base de datos. */
public record TicketStock(int quantitySold, int quantityTotal) {
}
