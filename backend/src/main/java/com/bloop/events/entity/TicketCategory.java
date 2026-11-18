package com.bloop.events.entity;

/** Categorias de entrada. FREE obliga a precio 0 (tambien lo comprueba la BD). */
public enum TicketCategory {
    GENERAL,
    EARLY_BIRD,
    VIP,
    FREE
}
