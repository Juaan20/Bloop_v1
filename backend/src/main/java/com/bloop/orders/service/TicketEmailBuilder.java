package com.bloop.orders.service;

import com.bloop.notifications.service.EmailMessage;
import com.bloop.notifications.service.QrImageGenerator;
import com.bloop.orders.entity.TicketOrder;
import com.bloop.ticketing.entity.Ticket;
import com.bloop.ticketing.entity.TicketStatus;
import com.bloop.ticketing.repository.TicketRepository;
import com.bloop.ticketing.service.QrCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Prepara el correo con las entradas de un pedido y sus QR incrustados.
 * Solo incluye el QR de las entradas validas que siguen siendo del comprador: si una se ha
 * transferido, su QR ya es de otra persona y no se reenvia.
 */
@Component
@RequiredArgsConstructor
public class TicketEmailBuilder {

    /** Mas QR harian el correo demasiado pesado; el resto esta en "Mis entradas". */
    static final int MAX_QR = 10;
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm",
            Locale.forLanguageTag("es-ES")).withZone(ZoneId.of("Europe/Madrid"));

    private final TicketRepository ticketRepository;
    private final QrCodeService qrCodeService;
    private final QrImageGenerator qrImageGenerator;

    public EmailMessage build(TicketOrder order, String title, String intro, String publicUrl) {
        var session = order.getSession();
        var event = session.getEvent();
        String sessionName = session.getTitle() == null || session.getTitle().isBlank() ? null : session.getTitle();
        EmailMessage message = EmailMessage.of(title, "Hola " + order.getBuyerName() + ",")
                .paragraph(intro)
                .detail("Pedido", order.getReference())
                .detail("Evento", event.getName() + (sessionName == null ? "" : " · " + sessionName))
                .detail("Lugar", event.getVenue().getName())
                .detail("Fecha", DATE.format(session.getStartsAt()))
                .detail("Total", order.getTotalAmount() + " " + order.getCurrency());

        List<Ticket> mine = ticketRepository.findWithTypeByOrderId(order.getId()).stream()
                .filter(t -> t.getStatus() == TicketStatus.VALID)
                .filter(t -> Objects.equals(t.getOwnerUserId(), order.getCustomerUserId()))
                .toList();
        int index = 1;
        for (Ticket t : mine.stream().limit(MAX_QR).toList()) {
            message.image(new EmailMessage.InlineImage("qr-" + index++,
                    qrImageGenerator.png(qrCodeService.payloadFor(t.getCode())),
                    t.getTicketType().getName() + " · " + t.getHolderName()));
        }
        if (mine.size() > MAX_QR) {
            message.paragraph("Este pedido tiene " + mine.size() + " entradas: el resto de codigos QR esta en tu cuenta.");
        }
        return message
                .button("Ver mis entradas", publicUrl + "/cuenta/entradas")
                .footnote("Muestra el QR en la puerta desde el movil o impreso. Cada QR solo vale para una entrada "
                        + "y se invalida al usarse: no lo compartas.");
    }
}
