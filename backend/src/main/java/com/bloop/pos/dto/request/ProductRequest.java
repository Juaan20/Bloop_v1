package com.bloop.pos.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Alta y edicion de un producto con TODAS sus variantes.
 * Al editar: una variante con id se actualiza, sin id se crea, y las que falten se desactivan
 * (no se borran porque pueden tener ventas y existencias).
 */
public record ProductRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @Size(max = 500) @jakarta.validation.constraints.Pattern(regexp = "^(https?://.*)?$", message = "La imagen debe ser una URL http(s)") String imageUrl,
        boolean trackStock,
        @Min(0) @Max(1000) int sortOrder,
        boolean active,
        @NotEmpty(message = "El producto necesita al menos una variante") @Size(max = 20) List<@Valid Variant> variants,
        @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal mixerSurcharge) {

    /** mixerSurcharge: null = no es refresco de combinado; 0 = refresco sin suplemento. */
    public ProductRequest(UUID categoryId, String name, String description, String imageUrl, boolean trackStock,
                          int sortOrder, boolean active, List<Variant> variants) {
        this(categoryId, name, description, imageUrl, trackStock, sortOrder, active, variants, null);
    }

    public record Variant(
            UUID id,
            @NotBlank @Size(max = 60) String name,
            @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 8, fraction = 2) BigDecimal price,
            @Size(max = 40) String sku,
            @Min(0) @Max(1000) int sortOrder,
            boolean active,
            com.bloop.pos.entity.StockUnit stockUnit,
            @Min(1) @Max(20000) Integer unitSizeMl,
            @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 8, fraction = 2) BigDecimal cost,
            Boolean requiresMixer,
            @Size(max = 10) List<@Valid RecipeItem> recipe,
            Boolean returnable) {

        public Variant(UUID id, String name, BigDecimal price, String sku, int sortOrder, boolean active) {
            this(id, name, price, sku, sortOrder, active, null, null, null, null, null, null);
        }
    }

    /** Linea de receta: componente y cantidad en su unidad (ml para botellas, unidades para latas). */
    public record RecipeItem(@NotNull UUID componentVariantId, @Min(1) @Max(5000) int quantity) {
    }
}
