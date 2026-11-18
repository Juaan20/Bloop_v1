package com.bloop.orders.repository;

import java.util.UUID;

/** Fila con el numero de entradas vendidas de un evento (constructor expression de JPQL). */
public record EventTicketsRow(UUID eventId, Long tickets) {
}
