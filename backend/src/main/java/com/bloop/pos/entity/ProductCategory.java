package com.bloop.pos.entity;

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

/** Categoria del TPV (Cervezas, Copas, Refrescos, Taquilla...). Es una pestana de la pantalla tactil. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_categories")
public class ProductCategory extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    /** Clave de color del boton (violet, amber, emerald, sky, rose, zinc). */
    @Column(nullable = false)
    private String color = "violet";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    /** Tipo de categoria: decide en que terminales y en que carta aparece. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryKind kind = CategoryKind.BAR;
}
