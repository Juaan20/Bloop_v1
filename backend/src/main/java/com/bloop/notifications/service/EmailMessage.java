package com.bloop.notifications.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Contenido de un correo, independiente de su aspecto. {@link EmailTemplateRenderer} lo convierte
 * en HTML con la identidad de Bloop y en texto plano (para clientes de correo sin HTML).
 *
 * Uso:
 * <pre>
 * EmailMessage.of("Tus entradas", "Hola Ana,")
 *     .paragraph("Tu compra se ha completado.")
 *     .detail("Pedido", "VF-1234")
 *     .button("Ver mis entradas", url);
 * </pre>
 */
public final class EmailMessage {

    /** Fila "etiqueta: valor" de la tabla de datos del correo. */
    public record Detail(String label, String value) {
    }

    /** Imagen incrustada (p. ej. el QR de una entrada). Se referencia en el HTML como cid:{contentId}. */
    public record InlineImage(String contentId, byte[] png, String caption) {
    }

    private final String title;
    private final String greeting;
    private final List<String> paragraphs = new ArrayList<>();
    private final List<Detail> details = new ArrayList<>();
    private final List<InlineImage> images = new ArrayList<>();
    private String buttonLabel;
    private String buttonUrl;
    private String footnote;

    private EmailMessage(String title, String greeting) {
        this.title = title;
        this.greeting = greeting;
    }

    /** @param title titular grande del correo; @param greeting saludo ("Hola Ana,") o null */
    public static EmailMessage of(String title, String greeting) {
        return new EmailMessage(title, greeting);
    }

    public EmailMessage paragraph(String text) {
        if (text != null && !text.isBlank()) {
            paragraphs.add(text);
        }
        return this;
    }

    public EmailMessage detail(String label, String value) {
        if (value != null && !value.isBlank()) {
            details.add(new Detail(label, value));
        }
        return this;
    }

    public EmailMessage image(InlineImage image) {
        images.add(image);
        return this;
    }

    public EmailMessage button(String label, String url) {
        this.buttonLabel = label;
        this.buttonUrl = url;
        return this;
    }

    public EmailMessage footnote(String text) {
        this.footnote = text;
        return this;
    }

    public String title() {
        return title;
    }

    public String greeting() {
        return greeting;
    }

    public List<String> paragraphs() {
        return List.copyOf(paragraphs);
    }

    public List<Detail> details() {
        return List.copyOf(details);
    }

    public List<InlineImage> images() {
        return List.copyOf(images);
    }

    public String buttonLabel() {
        return buttonLabel;
    }

    public String buttonUrl() {
        return buttonUrl;
    }

    public String footnote() {
        return footnote;
    }
}
