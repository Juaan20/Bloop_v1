package com.bloop.ticketing.repository;

/** Email y nombre de un titular de entradas (para enviarle avisos). */
public record TicketHolderContact(String email, String firstName) {
}
