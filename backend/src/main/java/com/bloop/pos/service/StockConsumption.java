package com.bloop.pos.service;

import com.bloop.pos.entity.ProductVariant;
import com.bloop.pos.entity.StockUnit;
import com.bloop.pos.entity.VariantRecipeItem;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Que se descuenta del inventario al vender algo (logica pura, sin base de datos).
 *
 * - Variante CON receta (una copa, un combinado): sus componentes, en ml o unidades.
 * - Variante SIN receta de un producto con existencias: ella misma. Si se cuenta en ml (una botella
 *   entera vendida en una mesa), se descuenta su contenido completo.
 * - Producto sin control de existencias (una entrada de taquilla): nada.
 */
public final class StockConsumption {

    private StockConsumption() {
    }

    /** @return variante a descontar -> cantidad en su unidad de inventario */
    public static Map<ProductVariant, Integer> of(ProductVariant variant, int quantity) {
        Map<ProductVariant, Integer> result = new LinkedHashMap<>();
        add(result, variant, quantity);
        return result;
    }

    public static void add(Map<ProductVariant, Integer> into, ProductVariant variant, int quantity) {
        if (!variant.getRecipe().isEmpty()) {
            for (VariantRecipeItem item : variant.getRecipe()) {
                into.merge(item.getComponent(), item.getQuantity() * quantity, Integer::sum);
            }
            return;
        }
        if (!variant.getProduct().isTrackStock()) {
            return;
        }
        int perUnit = variant.getStockUnit() == StockUnit.ML ? variant.getUnitSizeMl() : 1;
        into.merge(variant, perUnit * quantity, Integer::sum);
    }

    /**
     * Cuantas raciones se pueden servir con las existencias actuales (lo que muestra el TPV).
     *
     * @param stock existencias del almacen por variante, en su unidad de inventario
     * @return null si la variante no controla existencias
     */
    public static Integer servingsAvailable(ProductVariant variant, Map<UUID, Integer> stock) {
        Map<ProductVariant, Integer> needs = of(variant, 1);
        if (needs.isEmpty()) {
            return null;
        }
        int servings = Integer.MAX_VALUE;
        for (Map.Entry<ProductVariant, Integer> need : needs.entrySet()) {
            int available = stock.getOrDefault(need.getKey().getId(), 0);
            servings = Math.min(servings, available / need.getValue());
        }
        return servings;
    }
}
