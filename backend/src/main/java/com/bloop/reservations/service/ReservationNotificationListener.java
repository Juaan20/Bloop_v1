package com.bloop.reservations.service;

import com.bloop.notifications.entity.NotificationType;
import com.bloop.notifications.service.EmailMessage;
import com.bloop.notifications.service.NotificationService;
import com.bloop.reservations.entity.Reservation;
import com.bloop.reservations.repository.ReservationRepository;
import com.bloop.shared.config.BloopProperties;
import com.bloop.venues.entity.Venue;
import com.bloop.venues.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Correos al cliente sobre su reserva VIP (recibida, confirmada, cancelada). */
@Component
@RequiredArgsConstructor
public class ReservationNotificationListener {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm",
            Locale.forLanguageTag("es-ES")).withZone(ZoneId.of("Europe/Madrid"));

    private final ReservationRepository reservationRepository;
    private final VenueRepository venueRepository;
    private final NotificationService notificationService;
    private final BloopProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onReservationEmail(ReservationEmailEvent event) {
        Reservation r = reservationRepository.findDetailedById(event.reservationId()).orElse(null);
        if (r == null) {
            return;
        }
        String venue = venueRepository.findById(r.getVenueId()).map(Venue::getName).orElse("");
        String what = r.getSession() == null ? venue : r.getSession().getEvent().getName() + " (" + venue + ")";
        if (r.getContactEmail() == null) {
            return; // venta en taquilla sin email
        }
        String intro = switch (event.kind()) {
            case RECEIVED -> "Hemos recibido tu solicitud de reserva. El local la revisara y te confirmara por correo.";
            case CONFIRMED -> "Tu reserva esta confirmada. Te esperamos.";
            case CANCELLED -> "Tu reserva ha sido cancelada.";
            case PAID -> "Hemos recibido el pago de tu reserva. Muestra este correo o tu nombre al llegar.";
        };
        String title = switch (event.kind()) {
            case RECEIVED -> "Solicitud de reserva recibida";
            case CONFIRMED -> "Reserva confirmada";
            case CANCELLED -> "Reserva cancelada";
            case PAID -> "Pago de la reserva recibido";
        };
        String deposit = r.getDepositAmount().signum() == 0 ? null
                : r.getDepositAmount() + " EUR" + (r.getDepositPaidAt() == null
                ? " (pendiente: el local te indicara como abonarlo)" : " (pagado)");
        EmailMessage message = EmailMessage.of(title, "Hola " + r.getContactName() + ",")
                .paragraph(intro)
                .detail("Reserva", r.getReference())
                .detail("Lugar", what)
                .detail("Sala y mesa", r.getTable().getZone().getName() + " · " + r.getTable().getName())
                .detail("Fecha", DATE.format(r.getStartsAt()))
                .detail("Personas", String.valueOf(r.getGuests()))
                .detail("Consumo minimo", r.getMinSpend().signum() == 0 ? null : r.getMinSpend() + " EUR")
                .detail("Reserva + paquete", deposit)
                .button("Ver mis reservas", properties.publicUrl() + "/cuenta/reservas");
        NotificationType type = switch (event.kind()) {
            case RECEIVED -> NotificationType.RESERVATION_RECEIVED;
            case CONFIRMED -> NotificationType.RESERVATION_CONFIRMED;
            case CANCELLED -> NotificationType.RESERVATION_CANCELLED;
            case PAID -> NotificationType.RESERVATION_PAID;
        };
        String subject = title + " - " + r.getReference();
        notificationService.sendEmail(r.getTenantId(), r.getContactEmail(), type, subject, message);
    }
}
