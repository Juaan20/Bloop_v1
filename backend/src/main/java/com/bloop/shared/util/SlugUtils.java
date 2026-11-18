package com.bloop.shared.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** Genera "slugs" para URLs publicas: "Sala Aurora Madrid" -> "sala-aurora-madrid". */
public final class SlugUtils {

    private SlugUtils() {
    }

    public static String slugify(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isEmpty() ? "item" : normalized;
    }

    /**
     * Devuelve un slug que no exista todavia, anadiendo -2, -3... si hace falta.
     *
     * @param exists funcion que indica si un slug ya esta ocupado
     */
    public static String uniqueSlug(String text, Predicate<String> exists) {
        String base = slugify(text);
        String candidate = base;
        int suffix = 2;
        while (exists.test(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}
