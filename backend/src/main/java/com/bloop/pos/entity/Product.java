package com.bloop.pos.entity;

import com.bloop.shared.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Producto del TPV. Se vende a traves de sus variantes (Copa, Botella...), que tienen el precio. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ProductCategory category;

    @Column(nullable = false)
    private String name;

    private String description;

    /** Foto del producto (URL), visible en la carta VIP. */
    @Column(name = "image_url")
    private String imageUrl;

    /** Si es false, vender el producto no descuenta inventario (cocteles, entradas de taquilla...). */
    @Column(name = "track_stock", nullable = false)
    private boolean trackStock = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Suplemento si se elige como refresco de un combinado (0 = sin suplemento). null = no es refresco
     * de combinado. Al elegirlo se descuenta del inventario su primera variante activa (la lata, por ejemplo).
     */
    @Column(name = "mixer_surcharge", precision = 10, scale = 2)
    private java.math.BigDecimal mixerSurcharge;

    public boolean isMixer() {
        return mixerSurcharge != null;
    }

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, price ASC")
    @BatchSize(size = 50)
    private List<ProductVariant> variants = new ArrayList<>();

    public void addVariant(ProductVariant variant) {
        variant.setProduct(this);
        variant.setTenantId(tenantId);
        variants.add(variant);
    }
}
