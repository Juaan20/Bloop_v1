package com.bloop.venues.entity;

import com.bloop.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Establecimiento fisico (discoteca, sala, pub...) que pertenece a una empresa. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "venues")
public class Venue extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    /** Identificador legible para la URL publica: /locales/{slug}. */
    @Column(nullable = false, unique = true)
    private String slug;

    private String description;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(nullable = false)
    private String city;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(nullable = false)
    private String country = "ES";

    private String phone;

    private String email;

    @Column(nullable = false)
    private int capacity;

    /** Horario en texto libre, p. ej. "Jue-Sab 00:00-06:00". */
    @Column(name = "opening_hours")
    private String openingHours;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VenueStatus status = VenueStatus.ACTIVE;
}
