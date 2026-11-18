package com.bloop.notifications;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.bloop.support.AbstractIntegrationTest;
import com.bloop.support.ApiClient;
import com.bloop.support.TestDataFactory;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Envio REAL por SMTP contra un servidor de correo en memoria (GreenMail) que exige usuario y
 * contrasena, como un proveedor de verdad. Comprueba que el correo llega con HTML y texto, y que
 * con una contrasena erronea queda FAILED con un motivo comprensible (no se finge el envio).
 */
class MailDeliveryIntegrationTest extends AbstractIntegrationTest {

    private static final int SMTP_PORT = 3025 + (int) (ProcessHandle.current().pid() % 1000);

    @RegisterExtension
    static GreenMailExtension smtp = new GreenMailExtension(new ServerSetup(SMTP_PORT, "127.0.0.1", "smtp"))
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("bloop", "secreto-de-prueba"))
            .withPerMethodLifecycle(false);

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        registry.add("bloop.mail.enabled", () -> "true");
        registry.add("bloop.mail.from", () -> "no-reply@bloop.test");
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> SMTP_PORT);
        registry.add("spring.mail.username", () -> "bloop");
        registry.add("spring.mail.password", () -> "secreto-de-prueba");
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "true");
    }

    @Test
    void testEmailIsDeliveredWithHtmlAndText() throws Exception {
        ApiClient api = api();
        var tenant = data.createTenantWithAdmin("Correo real");
        String admin = api.login(tenant.adminEmail(), TestDataFactory.PASSWORD);

        var result = api.post("/api/notifications/test-email", admin, null);
        assertThat(result.status()).isEqualTo(200);
        assertThat((String) result.json("$.status")).isEqualTo("SENT");

        assertThat(smtp.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage received = smtp.getReceivedMessages()[0];
        assertThat(received.getSubject()).isEqualTo("Prueba de correo de Bloop");
        assertThat(received.getAllRecipients()[0].toString()).isEqualTo(tenant.adminEmail());
        assertThat(received.getFrom()[0].toString()).contains("no-reply@bloop.test");
        String raw = GreenMailUtil.getWholeMessage(received);
        assertThat(raw).contains("text/html").contains("text/plain");

        // Sin TENANT_SETTINGS no se puede usar para mandar correos
        String customer = api.login(data.createCustomer(), TestDataFactory.PASSWORD);
        assertThat(api.post("/api/notifications/test-email", customer, null).status()).isEqualTo(403);

        // Contrasena incorrecta en el servidor: FAILED con explicacion, sin romper la peticion
        smtp.setUser("bloop", "otra-contrasena");
        var failed = api.post("/api/notifications/test-email", admin, null);
        assertThat(failed.status()).isEqualTo(200);
        assertThat((String) failed.json("$.status")).isEqualTo("FAILED");
        assertThat((String) failed.json("$.errorMessage")).containsIgnoringCase("autenticacion");
    }
}
