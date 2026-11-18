package com.bloop.ticketing.service;

import com.bloop.notifications.entity.NotificationType;
import com.bloop.notifications.service.EmailMessage;
import com.bloop.notifications.service.NotificationService;
import com.bloop.shared.config.BloopProperties;
import com.bloop.ticketing.entity.Ticket;
import com.bloop.ticketing.repository.TicketRepository;
import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Avisa al destinatario de una entrada transferida (despues del commit). */
@Component
@RequiredArgsConstructor
public class TicketTransferListener {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm",
            Locale.forLanguageTag("es-ES")).withZone(ZoneId.of("Europe/Madrid"));

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final BloopProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onTicketTransferred(TicketTransferredEvent event) {
        Ticket ticket = ticketRepository.findById(event.ticketId()).orElse(null);
        User recipient = ticket == null ? null : userRepository.findById(ticket.getOwnerUserId()).orElse(null);
        if (recipient == null) {
            return;
        }
        var session = ticket.getSession();
        var eventEntity = session.getEvent();
        EmailMessage message = EmailMessage.of("Te han enviado una entrada", "Hola " + recipient.getFirstName() + ",")
                .paragraph(event.senderName() + " te ha transferido una entrada. Ya esta en tu cuenta con su propio QR.")
                .detail("Evento", eventEntity.getName())
                .detail("Lugar", eventEntity.getVenue().getName())
                .detail("Fecha", DATE.format(session.getStartsAt()))
                .detail("Entrada", ticket.getTicketType().getName() + " (a nombre de " + ticket.getHolderName() + ")")
                .button("Ver mis entradas", properties.publicUrl() + "/cuenta/entradas");
        notificationService.sendEmail(ticket.getTenantId(), recipient.getEmail(), NotificationType.TICKET_TRANSFERRED,
                "Te han enviado una entrada para " + eventEntity.getName(), message);
    }
}
