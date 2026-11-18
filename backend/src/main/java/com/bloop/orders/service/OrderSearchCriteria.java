package com.bloop.orders.service;

import com.bloop.events.entity.TicketCategory;
import com.bloop.orders.entity.OrderStatus;
import com.bloop.payments.entity.PaymentProviderType;
import com.bloop.payments.entity.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Filtros del listado de pedidos del backoffice. Todos son opcionales y se combinan (Y logico).
 *
 * @param sessionId       dia concreto de un evento (p. ej. el sabado de un festival)
 * @param from            pedidos creados desde (incluido)
 * @param to              pedidos creados hasta (excluido)
 * @param paymentProvider metodo de pago: STRIPE (tarjeta online), SIMULATED (pruebas) o FREE
 * @param ticketCategory  tipo de entrada vendida (GENERAL, VIP...)
 * @param search          referencia, nombre o email del cliente
 */
public record OrderSearchCriteria(OrderStatus status, UUID eventId, UUID sessionId, UUID venueId, Instant from,
                                  Instant to, PaymentStatus paymentStatus, PaymentProviderType paymentProvider,
                                  TicketCategory ticketCategory, String search) {

    public OrderSearchCriteria {
        search = search == null ? "" : search.trim();
    }
}
