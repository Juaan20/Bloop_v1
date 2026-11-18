package com.bloop.pos.entity;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tipo de categoria del catalogo. Separa lo que se vende en cada sitio:
 * la barra normal solo ve BAR; las botellas VIP salen en la carta de las mesas; etc.
 */
public enum CategoryKind {
    /** Consumo rapido de barra: refrescos, agua, cerveza, copas, combinados. */
    BAR,
    /** Botellas para mesas VIP (carta del QR y paquetes). */
    VIP_BOTTLE,
    /** Productos de servicio (hielo, cristaleria, bengalas...). */
    SERVICE,
    /** Resto: taquilla, guardarropa, merchandising... */
    OTHER,
    /** Solo inventario: botellas de las que se sirven combinados y chupitos. No sale en ningun TPV. */
    SUPPLY;

    /** Convierte "BAR,OTHER" en un conjunto. Ignora valores desconocidos. */
    public static Set<CategoryKind> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return EnumSet.noneOf(CategoryKind.class);
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> Arrays.stream(values()).anyMatch(k -> k.name().equals(s)))
                .map(CategoryKind::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CategoryKind.class)));
    }

    public static String format(Set<CategoryKind> kinds) {
        return kinds.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }
}
