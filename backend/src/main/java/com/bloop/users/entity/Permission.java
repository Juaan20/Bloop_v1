package com.bloop.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Permiso atomico (p. ej. EVENT_MANAGE). Datos de referencia gestionados por Flyway. */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    private String code;

    @Column(nullable = false)
    private String description;
}
