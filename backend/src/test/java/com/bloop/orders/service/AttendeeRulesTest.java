package com.bloop.orders.service;

import com.bloop.orders.entity.DocumentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AttendeeRulesTest {

    @Test
    void validatesSpanishDocumentsWithControlLetter() {
        assertThat(AttendeeRules.isValidDocument(DocumentType.DNI, "12345678Z")).isTrue();
        assertThat(AttendeeRules.isValidDocument(DocumentType.DNI, "12.345.678-z")).isTrue();
        assertThat(AttendeeRules.isValidDocument(DocumentType.DNI, "12345678A")).isFalse();
        assertThat(AttendeeRules.isValidDocument(DocumentType.DNI, "1234567Z")).isFalse();
        assertThat(AttendeeRules.isValidDocument(DocumentType.NIE, "X1234567L")).isTrue();
        assertThat(AttendeeRules.isValidDocument(DocumentType.NIE, "X1234567A")).isFalse();
        assertThat(AttendeeRules.isValidDocument(DocumentType.PASSPORT, "XDA123456")).isTrue();
        assertThat(AttendeeRules.isValidDocument(DocumentType.PASSPORT, "AB1")).isFalse();
    }

    @Test
    void keepsOnlyTheLastFourCharacters() {
        assertThat(AttendeeRules.last4("12345678Z")).isEqualTo("678Z");
        assertThat(AttendeeRules.masked(DocumentType.DNI, "678Z")).isEqualTo("DNI ····678Z");
    }

    @Test
    void computesTheAgeOnTheDayOfTheSession() {
        Instant session = Instant.parse("2026-11-20T22:00:00Z");
        assertThat(AttendeeRules.ageOn(LocalDate.of(2008, 11, 20), session)).isEqualTo(18);
        assertThat(AttendeeRules.ageOn(LocalDate.of(2008, 11, 21), session)).isEqualTo(17);
    }
}
