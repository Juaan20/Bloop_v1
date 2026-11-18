package com.bloop.pos.entity;

import com.bloop.shared.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import org.hibernate.annotations.BatchSize;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Variante vendible de un producto, con su precio. Las existencias se llevan por variante. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_variants")
public class ProductVariant extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    private String sku;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    /** Como se cuentan sus existencias: unidades o ml (botellas de las que se sirven copas). */
    @Enumerated(EnumType.STRING)
    @Column(name = "stock_unit", nullable = false)
    private StockUnit stockUnit = StockUnit.UNIT;

    /** ml de cada botella (solo si stockUnit = ML). */
    @Column(name = "unit_size_ml")
    private Integer unitSizeMl;

    /** Coste de compra de una unidad (para margenes). Opcional. */
    @Column(precision = 10, scale = 2)
    private BigDecimal cost;

    /** Envase retornable: el proveedor se lleva el vacio (botella, botellin, caja...). */
    @Column(nullable = false)
    private boolean returnable;

    /** Copa que se sirve con refresco: al venderla el TPV pide elegir cual (Coca-Cola, Tonica...). */
    @Column(name = "requires_mixer", nullable = false)
    private boolean requiresMixer;

    /** Receta: lo que se descuenta al venderla. Vacia = se descuenta la propia variante. */
    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<VariantRecipeItem> recipe = new ArrayList<>();

    /** Se puede vender si ella, su producto y su categoria estan activos. */
    public boolean isSellable() {
        return active && product.isActive() && product.getCategory().isActive();
    }
}
