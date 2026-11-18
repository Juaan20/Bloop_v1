package com.bloop.pos.dto.response;

import com.bloop.pos.entity.TerminalType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Respuestas del catalogo del TPV y de los terminales. */
public final class CatalogResponses {

    private CatalogResponses() {
    }

    public record Category(UUID id, String name, String color, int sortOrder, boolean active,
                           com.bloop.pos.entity.CategoryKind kind) {
    }

    public record Variant(UUID id, String name, BigDecimal price, String sku, int sortOrder, boolean active,
                          com.bloop.pos.entity.StockUnit stockUnit, Integer unitSizeMl, BigDecimal cost,
                          boolean requiresMixer, List<RecipeItem> recipe, boolean returnable) {
    }

    /** Componente de una receta con su nombre ("Ron Barcelo · Botella 70 cl") y su unidad. */
    public record RecipeItem(UUID componentVariantId, String componentName,
                             com.bloop.pos.entity.StockUnit unit, int quantity) {
    }

    public record Product(UUID id, UUID categoryId, String categoryName, String name, String description,
                          String imageUrl, boolean trackStock, int sortOrder, boolean active, List<Variant> variants,
                          BigDecimal mixerSurcharge) {
    }

    /** Terminal con su caja abierta (si la hay). */
    public record Terminal(UUID id, String name, UUID venueId, String venueName, UUID warehouseId,
                           String warehouseName, TerminalType type, boolean active, UUID openSessionId,
                           Instant openSessionSince, String openedByName,
                           java.util.Set<com.bloop.pos.entity.CategoryKind> allowedKinds) {
    }

    /**
     * Todo lo que necesita la pantalla tactil: el terminal, su caja abierta (o null) y los productos
     * vendibles agrupados por categoria con las existencias del almacen del terminal.
     */
    public record PosScreen(Terminal terminal, CashSessionResponse session, List<PosCategory> categories,
                            List<PosMixer> mixers) {
    }

    /** Refresco que se puede elegir para un combinado. surcharge = lo que suma al precio de la copa. */
    public record PosMixer(UUID variantId, String name, BigDecimal surcharge, Integer stock) {
    }

    public record PosCategory(UUID id, String name, String color, com.bloop.pos.entity.CategoryKind kind,
                              List<PosProduct> products) {
    }

    public record PosProduct(UUID id, String name, boolean trackStock, List<PosVariant> variants) {
    }

    /**
     * @param stock         raciones que se pueden servir con las existencias del almacen del terminal
     *                      (null si no controla existencias)
     * @param requiresMixer al tocarla, el TPV pide elegir el refresco
     */
    public record PosVariant(UUID id, String name, BigDecimal price, Integer stock, boolean requiresMixer) {
    }
}
