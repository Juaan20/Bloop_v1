package com.bloop.shared.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilsTest {

    @Test
    void removesAccentsAndSymbols() {
        assertThat(SlugUtils.slugify("  Sala Aurora — Año Nuevo!! ")).isEqualTo("sala-aurora-ano-nuevo");
        assertThat(SlugUtils.slugify("***")).isEqualTo("item");
    }

    @Test
    void addsSuffixWhenSlugIsTaken() {
        Set<String> taken = Set.of("techno", "techno-2");
        assertThat(SlugUtils.uniqueSlug("Techno", taken::contains)).isEqualTo("techno-3");
    }
}
