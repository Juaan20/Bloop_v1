package com.bloop.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Propiedades propias de la aplicacion (prefijo "bloop" en application.yml).
 * Agruparlas en un record tipado evita repartir @Value por todo el codigo.
 */
@ConfigurationProperties(prefix = "bloop")
public record BloopProperties(
        Security security,
        Cors cors,
        Orders orders,
        Payments payments,
        Mail mail,
        Media media,
        DemoData demoData,
        String publicUrl) {

    /**
     * @param previousJwtSecrets claves JWT anteriores: se aceptan para validar tokens ya emitidos
     *                           durante una rotacion de claves, pero nunca se usan para firmar
     * @param previousQrSecrets  igual para la firma de los QR (las entradas antiguas siguen valiendo)
     * @param maxLoginFailures   intentos fallidos de login permitidos antes del bloqueo temporal
     * @param loginLockMinutes   minutos de bloqueo tras superar los intentos fallidos
     */
    public record Security(String jwtSecret, int accessTokenMinutes, int refreshTokenDays,
                           String qrSecret, boolean refreshCookieSecure, List<String> previousJwtSecrets,
                           List<String> previousQrSecrets, int maxLoginFailures, int loginLockMinutes) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Orders(int pendingMinutes) {
    }

    public record Payments(boolean simulatedEnabled, Stripe stripe) {
    }

    /**
     * Stripe en modo test. Sin claves, Stripe queda desactivado y solo existe el pago simulado.
     *
     * @param apiBaseUrl              https://api.stripe.com (los tests apuntan a un servidor falso local)
     * @param webhookToleranceSeconds antiguedad maxima aceptada de un webhook firmado (anti-reenvio)
     * @param sessionMinutes          validez de la pagina de pago de Stripe (minimo 30 segun Stripe)
     */
    public record Stripe(boolean enabled, String secretKey, String webhookSecret, String apiBaseUrl,
                         int webhookToleranceSeconds, int sessionMinutes) {
    }

    /**
     * @param enabled  false = modo pruebas: los correos se guardan pero no se envian
     * @param from     direccion del remitente (debe estar autorizada en tu proveedor SMTP)
     * @param fromName nombre visible del remitente
     * @param replyTo  direccion de respuesta opcional
     */
    public record Mail(boolean enabled, String from, String fromName, String replyTo) {
    }

    /**
     * Imagenes subidas desde el backoffice.
     *
     * @param provider      "local" (carpeta en disco, desarrollo) o "cloudinary" (recomendado)
     * @param maxBytes      tamano maximo de cada imagen
     * @param folder        carpeta raiz dentro de Cloudinary
     * @param localDir      carpeta de disco para provider=local (fuera del proyecto)
     * @param publicBaseUrl URL del backend con la que se construyen las URLs de provider=local
     */
    public record Media(String provider, long maxBytes, String folder, String localDir, String publicBaseUrl,
                        Cloudinary cloudinary) {

        /** Credenciales de Cloudinary (https://console.cloudinary.com/settings/api-keys). */
        public record Cloudinary(String cloudName, String apiKey, String apiSecret, String apiBaseUrl) {
        }
    }

    /**
     * @param historyDays dias de historico de demostracion a generar (0 = no generar). Ver HistoricalDemoDataGenerator.
     * @param dataset     juego de datos demo: "kembo" (discoteca de Zaragoza, por defecto) o "noctambula" (el antiguo).
     */
    public record DemoData(boolean enabled, String password, int historyDays, String dataset) {

        public boolean isDataset(String name) {
            return enabled && name.equalsIgnoreCase(dataset == null || dataset.isBlank() ? "kembo" : dataset);
        }
    }
}
