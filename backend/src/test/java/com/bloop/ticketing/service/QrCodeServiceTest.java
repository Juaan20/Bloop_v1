package com.bloop.ticketing.service;

import com.bloop.shared.config.BloopProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Test unitario puro (sin Spring): firma y verificacion del contenido de los QR. */
class QrCodeServiceTest {

    private final QrCodeService service = new QrCodeService(propertiesWithSecret("unit-test-qr-secret-0123456789abcdef"));

    @Test
    void generatedPayloadIsVerified() {
        String payload = service.payloadFor("abc123");
        assertThat(payload).startsWith("VF1.abc123.");
        assertThat(service.extractVerifiedCode(payload)).contains("abc123");
    }

    @Test
    void tamperedPayloadIsRejected() {
        String payload = service.payloadFor("abc123");
        assertThat(service.extractVerifiedCode(payload.replace("abc123", "abc124"))).isEmpty();
        assertThat(service.extractVerifiedCode(payload + "x")).isEmpty();
        assertThat(service.extractVerifiedCode("texto cualquiera")).isEmpty();
        assertThat(service.extractVerifiedCode(null)).isEmpty();
    }

    @Test
    void payloadSignedWithAnotherSecretIsRejected() {
        QrCodeService other = new QrCodeService(propertiesWithSecret("another-secret-that-is-long-enough-123"));
        assertThat(service.extractVerifiedCode(other.payloadFor("abc123"))).isEmpty();
    }

    @Test
    void qrSignedWithPreviousSecretStillValidDuringRotation() {
        String oldSecret = "old-qr-secret-0123456789abcdef0123";
        String oldPayload = new QrCodeService(propertiesWithSecret(oldSecret)).payloadFor("abc123");
        var rotated = new QrCodeService(new BloopProperties(new BloopProperties.Security("x".repeat(40), 15, 14,
                "new-qr-secret-0123456789abcdef0123", false, java.util.List.of(), java.util.List.of(oldSecret), 5, 15),
                null, null, null, null, null, null, null));
        assertThat(rotated.extractVerifiedCode(oldPayload)).contains("abc123");
        // Los QR nuevos se firman con la clave nueva, que la antigua no reconoce.
        assertThat(new QrCodeService(propertiesWithSecret(oldSecret)).extractVerifiedCode(rotated.payloadFor("xyz")))
                .isEmpty();
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new QrCodeService(propertiesWithSecret("corto")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static BloopProperties propertiesWithSecret(String secret) {
        return new BloopProperties(new BloopProperties.Security("x".repeat(40), 15, 14, secret, false,
                java.util.List.of(), java.util.List.of(), 5, 15),
                null, null, null, null, null, null, null);
    }
}
