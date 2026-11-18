package com.bloop.orders.service;

import com.bloop.orders.entity.DocumentType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reglas de los datos de asistentes (sin base de datos: probadas en AttendeeRulesTest).
 *
 * IMPORTANTE: validar el FORMATO de un DNI/NIE no verifica la identidad de nadie. Bloop no tiene
 * integracion con un proveedor de verificacion oficial; la comprobacion real la hace el personal
 * de puerta comparando con el documento fisico.
 */
public final class AttendeeRules {

    /** Zona horaria de los locales (Espana) para calcular la edad el dia del evento. */
    public static final ZoneId VENUE_ZONE = ZoneId.of("Europe/Madrid");
    private static final String DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";
    private static final Pattern DNI = Pattern.compile("^\\d{8}[A-Z]$");
    private static final Pattern NIE = Pattern.compile("^[XYZ]\\d{7}[A-Z]$");
    private static final Pattern PASSPORT = Pattern.compile("^[A-Z0-9]{5,20}$");

    private AttendeeRules() {
    }

    /** Mayusculas y sin espacios, puntos ni guiones ("12.345.678-z" -> "12345678Z"). */
    public static String normalize(String document) {
        return document == null ? "" : document.toUpperCase(Locale.ROOT).replaceAll("[\\s.\\-]", "");
    }

    /** Formato (y letra de control en DNI/NIE) correcto. */
    public static boolean isValidDocument(DocumentType type, String rawNumber) {
        String n = normalize(rawNumber);
        return switch (type) {
            case DNI -> DNI.matcher(n).matches() && controlLetterOk(n.substring(0, 8), n.charAt(8));
            case NIE -> NIE.matcher(n).matches()
                    && controlLetterOk("XYZ".indexOf(n.charAt(0)) + n.substring(1, 8), n.charAt(8));
            case PASSPORT -> PASSPORT.matcher(n).matches();
        };
    }

    private static boolean controlLetterOk(String digits, char letter) {
        return DNI_LETTERS.charAt(Integer.parseInt(digits) % 23) == letter;
    }

    /** Ultimos 4 caracteres: lo unico que se guarda del documento. */
    public static String last4(String rawNumber) {
        String n = normalize(rawNumber);
        return n.length() <= 4 ? n : n.substring(n.length() - 4);
    }

    /** Edad cumplida el dia en que empieza la sesion. */
    public static int ageOn(LocalDate birthDate, Instant sessionStart) {
        return Period.between(birthDate, LocalDate.ofInstant(sessionStart, VENUE_ZONE)).getYears();
    }

    /** "DNI ····678Z" para mostrar sin exponer el documento. */
    public static String masked(DocumentType type, String last4) {
        return type == null || last4 == null ? null : type.name() + " ····" + last4;
    }
}
