package com.bloop.ticketing.service;

import com.bloop.events.entity.Event;
import com.bloop.events.entity.EventSession;
import com.bloop.events.repository.EventRepository;
import com.bloop.events.service.EventChangeNotice;
import com.bloop.notifications.entity.NotificationType;
import com.bloop.notifications.service.EmailMessage;
import com.bloop.notifications.service.NotificationService;
import com.bloop.shared.config.BloopProperties;
import com.bloop.ticketing.repository.TicketHolderContact;
import com.bloop.ticketing.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Avisa por correo a los titulares de entradas validas cuando una sesion cambia de horario,
 * se cancela una sesion o se cancela el evento. Se ejecuta solo si el cambio se confirmo (AFTER_COMMIT).
 */
@Component
@RequiredArgsConstructor
public class EventChangeNotificationListener {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm",
            Locale.forLanguageTag("es-ES")).withZone(ZoneId.of("Europe/Madrid"));

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;
    private final BloopProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onEventChange(EventChangeNotice notice) {
        Event event = eventRepository.findById(notice.eventId()).orElse(null);
        if (event == null || notice.sessionIds().isEmpty()) {
            return;
        }
        List<TicketHolderContact> holders = ticketRepository.findHolderContacts(notice.sessionIds());
        if (holders.isEmpty()) {
            return;
        }
        String subject;
        String message;
        NotificationType type;
        switch (notice.kind()) {
            case SESSION_RESCHEDULED -> {
                EventSession session = event.getSessions().stream()
                        .filter(s -> s.getId().equals(notice.sessionIds().getFirst()))
                        .findFirst().orElse(null);
                if (session == null) {
                    return;
                }
                type = NotificationType.EVENT_CHANGED;
                subject = "Cambio de horario: " + event.getName();
                message = "La sesion de " + event.getName() + " prevista para el " + DATE.format(notice.previousStartsAt())
                        + " cambia de horario.\n\nNueva fecha: " + DATE.format(session.getStartsAt())
                        + "\n\nTus entradas siguen siendo validas para la nueva fecha.";
            }
            case SESSION_CANCELLED -> {
                type = NotificationType.EVENT_CANCELLED;
                subject = "Sesion cancelada: " + event.getName();
                message = "Lamentamos informarte de que una de las sesiones de " + event.getName()
                        + " para la que tienes entradas ha sido cancelada.\n\nEl organizador se pondra en contacto "
                        + "contigo para gestionar la devolucion.";
            }
            default -> {
                type = NotificationType.EVENT_CANCELLED;
                subject = "Evento cancelado: " + event.getName();
                message = "Lamentamos informarte de que el evento " + event.getName()
                        + " ha sido cancelado.\n\nEl organizador se pondra en contacto contigo para gestionar "
                        + "la devolucion.";
            }
        }
        for (TicketHolderContact holder : holders) {
            EmailMessage email = EmailMessage.of(subject, "Hola " + holder.firstName() + ",")
                    .paragraph(message)
                    .button("Ver mis entradas", properties.publicUrl() + "/cuenta/entradas");
            notificationService.sendEmail(event.getTenantId(), holder.email(), type, subject, email);
        }
    }
}
