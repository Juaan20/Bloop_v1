package com.bloop.ticketing.service;

import java.util.UUID;

/** Se publica al transferir una entrada; TicketTransferListener avisa al destinatario tras el commit. */
public record TicketTransferredEvent(UUID ticketId, String senderName) {
}
