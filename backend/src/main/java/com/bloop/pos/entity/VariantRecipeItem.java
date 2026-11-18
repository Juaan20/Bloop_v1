package com.bloop.pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Linea de receta: al vender "variant" se descuentan "quantity" unidades de "component".
 * La cantidad va en la unidad de inventario del componente: ml si es una botella (stockUnit=ML),
 * unidades si es una lata o un botellin (stockUnit=UNIT).
 *
 * Ejemplo: "Ron Barcelo - Copa" lleva 50 (ml) de "Ron Barcelo - Botella 70 cl".
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "variant_recipe_items")
public class VariantRecipeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "component_variant_id", nullable = false)
    private ProductVariant component;

    @Column(nullable = false)
    private int quantity;
}
