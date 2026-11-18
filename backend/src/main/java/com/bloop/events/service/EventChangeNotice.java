package com.bloop.events.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evento de aplicacion que se publica cuando cambia algo que afecta a quien ya tiene entradas:
 * una sesion cambia de horario, se cancela una sesion o se cancela el evento entero.
 * EventChangeNotificationListener (modulo ticketing) avisa por correo a los titulares DESPUES del commit.
 *
 * @param previousStartsAt hora de inicio anterior (solo en RESCHEDULED)
 */
public record EventChangeNotice(UUID eventId, List<UUID> sessionIds, Kind kind, Instant previousStartsAt) {

    public enum Kind {
        SESSION_RESCHEDULED,
        SESSION_CANCELLED,
        EVENT_CANCELLED
    }
}
