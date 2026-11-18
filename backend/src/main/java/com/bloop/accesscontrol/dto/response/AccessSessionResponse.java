package com.bloop.accesscontrol.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Sesion disponible para el control de acceso, con su contador de entradas. */
public record AccessSessionResponse(UUID sessionId, String eventName, String venueName, Instant startsAt,
                                    Instant endsAt, Instant doorsOpenAt, int capacity, long ticketsSold,
                                    long checkedIn) {
}
