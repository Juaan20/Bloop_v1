package com.bloop.notifications.service;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Convierte un {@link EmailMessage} en HTML y en texto plano.
 *
 * Los correos se maquetan con tablas y estilos en linea porque muchos clientes de correo
 * (Outlook, Gmail) ignoran las hojas de estilo y el CSS moderno. Todo el texto se escapa
 * con HtmlUtils: un nombre de cliente nunca puede inyectar HTML en el correo.
 */
@Component
public class EmailTemplateRenderer {

    private static final String BRAND = "#7c5cff";
    private static final String INK = "#0b0d14";

    public String html(EmailMessage m) {
        StringBuilder body = new StringBuilder();
        if (m.greeting() != null) {
            body.append("<p style=\"margin:0 0 16px;font-size:16px;color:#1f2330\">")
                    .append(esc(m.greeting())).append("</p>");
        }
        for (String p : m.paragraphs()) {
            body.append("<p style=\"margin:0 0 14px;font-size:15px;line-height:1.6;color:#3b4150\">")
                    .append(esc(p).replace("\n", "<br>")).append("</p>");
        }
        if (!m.details().isEmpty()) {
            body.append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" ")
                    .append("style=\"margin:18px 0;border-collapse:collapse;background:#f6f6fb;border-radius:12px\">");
            for (EmailMessage.Detail d : m.details()) {
                body.append("<tr><td style=\"padding:10px 16px;font-size:13px;color:#6b7080;width:40%;")
                        .append("border-bottom:1px solid #ebebf3\">").append(esc(d.label())).append("</td>")
                        .append("<td style=\"padding:10px 16px;font-size:14px;color:#11131a;font-weight:600;")
                        .append("border-bottom:1px solid #ebebf3\">").append(esc(d.value()).replace("\n", "<br>"))
                        .append("</td></tr>");
            }
            body.append("</table>");
        }
        for (EmailMessage.InlineImage img : m.images()) {
            body.append("<div style=\"margin:18px 0;text-align:center\">")
                    .append("<img src=\"cid:").append(esc(img.contentId())).append("\" width=\"200\" height=\"200\" ")
                    .append("alt=\"Codigo QR\" style=\"display:block;margin:0 auto;border:8px solid #fff;")
                    .append("border-radius:8px;background:#fff\">");
            if (img.caption() != null) {
                body.append("<p style=\"margin:8px 0 0;font-size:13px;color:#3b4150\">")
                        .append(esc(img.caption())).append("</p>");
            }
            body.append("</div>");
        }
        if (m.buttonUrl() != null) {
            body.append("<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin:24px 0 8px\">")
                    .append("<tr><td style=\"border-radius:10px;background:").append(BRAND).append("\">")
                    .append("<a href=\"").append(esc(m.buttonUrl())).append("\" style=\"display:inline-block;")
                    .append("padding:13px 22px;font-size:15px;font-weight:600;color:#fff;text-decoration:none\">")
                    .append(esc(m.buttonLabel())).append("</a></td></tr></table>")
                    .append("<p style=\"margin:0;font-size:12px;color:#8a8fa0\">Si el boton no funciona, copia este enlace: ")
                    .append(esc(m.buttonUrl())).append("</p>");
        }
        if (m.footnote() != null) {
            body.append("<p style=\"margin:22px 0 0;font-size:13px;line-height:1.5;color:#6b7080\">")
                    .append(esc(m.footnote())).append("</p>");
        }

        return """
                <!DOCTYPE html>
                <html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
                <title>%s</title></head>
                <body style="margin:0;padding:0;background:%s;font-family:Inter,Segoe UI,Helvetica,Arial,sans-serif">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:%s;padding:32px 12px">
                <tr><td align="center">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px">
                    <tr><td style="padding:0 4px 18px;font-size:18px;font-weight:700;letter-spacing:-0.02em;color:#fff">
                      <span style="display:inline-block;width:10px;height:10px;border-radius:3px;background:%s;margin-right:8px"></span>Bloop
                    </td></tr>
                    <tr><td style="background:#ffffff;border-radius:18px;padding:32px 28px">
                      <h1 style="margin:0 0 18px;font-size:24px;line-height:1.25;color:#0b0d14">%s</h1>
                      %s
                    </td></tr>
                    <tr><td style="padding:18px 6px;font-size:12px;line-height:1.5;color:#8a8fa0">
                      Recibes este correo por una compra, reserva o gestion en Bloop.<br>
                      Bloop nunca te pedira tu contrasena ni los datos de tu tarjeta por correo.
                    </td></tr>
                  </table>
                </td></tr></table>
                </body></html>
                """.formatted(esc(m.title()), INK, INK, BRAND, esc(m.title()), body);
    }

    /** Version de texto plano (la leen los clientes sin HTML y los filtros antispam). */
    public String text(EmailMessage m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.title()).append("\n\n");
        if (m.greeting() != null) {
            sb.append(m.greeting()).append("\n\n");
        }
        m.paragraphs().forEach(p -> sb.append(p).append("\n\n"));
        m.details().forEach(d -> sb.append(d.label()).append(": ").append(d.value()).append('\n'));
        if (!m.details().isEmpty()) {
            sb.append('\n');
        }
        m.images().forEach(i -> sb.append("[Codigo QR] ").append(i.caption() == null ? "" : i.caption()).append('\n'));
        if (m.buttonUrl() != null) {
            sb.append(m.buttonLabel()).append(": ").append(m.buttonUrl()).append("\n\n");
        }
        if (m.footnote() != null) {
            sb.append(m.footnote()).append('\n');
        }
        return sb.toString().trim() + "\n";
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
