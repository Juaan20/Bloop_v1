package com.bloop.demo;

import com.bloop.pos.entity.CategoryKind;
import com.bloop.pos.entity.Product;
import com.bloop.pos.entity.ProductCategory;
import com.bloop.pos.entity.ProductVariant;
import com.bloop.pos.entity.StockUnit;
import com.bloop.pos.entity.VariantRecipeItem;
import com.bloop.pos.repository.ProductCategoryRepository;
import com.bloop.pos.repository.ProductRepository;
import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Carta de barra simplificada (solo perfil dev), como trabaja la discoteca:
 *
 *   Combinados       Cubata 9 EUR / Cubata con Red Bull 11 EUR (todo lo que lleva alcohol menos chupitos)
 *   Refrescos y aguas  sin control de existencias salvo el agua
 *   Cervezas
 *   Chupitos         cualquier chupito, 3 EUR
 *
 * El alcohol se controla en "Botellas (inventario)" (tipo SUPPLY: no sale en ningun TPV) contado en ml:
 * cada cubata descuenta 50 ml y cada chupito 30 ml de la botella. Para anadir otro tipo de alcohol
 * basta crear su botella y su variante de cubata con la receta: no hay que tocar el codigo.
 * Las existencias de botellas empiezan a 0: se registran en Inventario con una entrada de mercancia.
 *
 * El resto de categorias de barra se desactivan (no se borran: tienen ventas en el historico).
 * Idempotente: si ya existe la categoria "Chupitos" no hace nada.
 */
@Component
@Order(4)
@Profile("dev")
// Juego de datos antiguo: solo con DEMO_DATASET=noctambula (por defecto se siembra Kembo).
@ConditionalOnExpression("${bloop.demo-data.enabled:false} and '${bloop.demo-data.dataset:kembo}' == 'noctambula'")
@RequiredArgsConstructor
public class BarCatalogDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BarCatalogDemoSeeder.class);
    static final int CUBATA_ML = 50;

    private final UserRepository userRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User admin = userRepository.findByEmail("admin@noctambula.test").orElse(null);
        if (admin == null || admin.getTenantId() == null) {
            return;
        }
        UUID tenantId = admin.getTenantId();
        List<ProductCategory> categories = categoryRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId);
        if (categories.isEmpty()) {
            return;
        }
        if (categories.stream().noneMatch(c -> c.getName().equalsIgnoreCase("Chupitos"))) {
            createBase(tenantId, categories);
        }
        alcoholTypes(tenantId);
    }

    /** Primera vez: las 4 categorias de barra y la categoria de botellas (solo inventario). */
    private void createBase(UUID tenantId, List<ProductCategory> categories) {
        List<Product> products = productRepository.findAllOfTenant(tenantId);

        ProductCategory combinados = find(categories, "Combinados");
        ProductCategory soft = find(categories, "Refrescos y aguas");
        if (soft == null) {
            soft = find(categories, "Refrescos");
        }
        ProductCategory beers = find(categories, "Cervezas");

        // Las demas categorias de barra (Copas, Cubatas, Ron, Ginebra...) dejan de verse en el TPV.
        for (ProductCategory c : categories) {
            if (c.getKind() == CategoryKind.BAR && c != combinados && c != soft && c != beers) {
                c.setActive(false);
            }
        }
        combinados = prepare(combinados, tenantId, "Combinados", "violet", 0);
        soft = prepare(soft, tenantId, "Refrescos y aguas", "sky", 1);
        beers = prepare(beers, tenantId, "Cervezas", "amber", 2);
        ProductCategory shots = prepare(null, tenantId, "Chupitos", "rose", 3);
        category(tenantId, "Botellas (inventario)", "zinc", 10, CategoryKind.SUPPLY);
        Product chupito = newProduct(shots, "Chupito");
        chupito.setTrackStock(false);
        ProductVariant v = new ProductVariant();
        v.setName("Chupito");
        v.setPrice(new BigDecimal("3.00"));
        chupito.addVariant(v);
        productRepository.save(chupito);

        for (Product p : products) {
            String name = p.getName().toLowerCase();
            if (p.getCategory() == combinados) {
                p.setActive(false); // productos antiguos de "Combinados" (tinto, sangria...)
            } else if (name.contains("agua")) {
                p.setCategory(soft);
                p.setTrackStock(true); // las aguas si se controlan
            }
        }
    }

    /** Tipos de alcohol de los combinados (todos al mismo precio: hoy hay un unico alcohol por tipo). */
    static final List<String> ALCOHOL_TYPES = List.of("Ron", "Ginebra", "Whisky", "Vodka", "Tequila");

    /**
     * Combinados por tipo de alcohol, como trabaja la discoteca:
     * - En Combinados, un boton por tipo (Ron, Ginebra...) con Cubata 9 EUR y Con Red Bull 11 EUR.
     * - Cada tipo tiene su botella de 70 cl en "Botellas (inventario)": cada cubata descuenta 50 ml, la venta
     *   nunca se bloquea y el recuento de cierre da la merma. La botella es retornable (se devuelve vacia).
     * - Cervezas y refrescos se llevan por unidades y su envase es retornable; los chupitos, sin inventario.
     * Idempotente: si ya existe el producto "Ron" en Combinados no hace nada.
     */
    private void alcoholTypes(UUID tenantId) {
        List<ProductCategory> categories = categoryRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId);
        ProductCategory combinados = find(categories, "Combinados");
        ProductCategory bottles = categories.stream().filter(c -> c.getKind() == CategoryKind.SUPPLY).findFirst()
                .orElseGet(() -> category(tenantId, "Botellas (inventario)", "zinc", 10, CategoryKind.SUPPLY));
        List<Product> products = productRepository.findAllOfTenant(tenantId);
        if (combinados == null || products.stream().anyMatch(p -> p.getCategory() == combinados
                && p.getName().equalsIgnoreCase("Ron"))) {
            return;
        }
        ProductCategory soft = find(categories, "Refrescos y aguas");
        ProductCategory beers = find(categories, "Cervezas");
        for (Product p : products) {
            String name = p.getName().toLowerCase();
            if ((p.getCategory() == combinados && name.equals("cubata")) || name.equals("alcohol para combinados")) {
                p.setActive(false);
            } else if (name.equals("chupito")) {
                p.setTrackStock(false);
                p.getVariants().forEach(v -> v.getRecipe().clear());
            } else if (p.getCategory() == soft || p.getCategory() == beers) {
                p.setTrackStock(true);
                boolean returnable = !name.contains("agua");
                p.getVariants().forEach(v -> v.setReturnable(returnable));
            }
        }
        int order = 0;
        for (String type : ALCOHOL_TYPES) {
            ProductVariant bottle = bottle(bottles, type, "Botella 70 cl", 700);
            Product cubata = newProduct(combinados, type);
            cubata.setSortOrder(order++);
            cubataVariant(cubata, "Cubata", "9.00", 0, bottle);
            cubataVariant(cubata, "Con Red Bull", "11.00", 1, bottle);
            productRepository.save(cubata);
        }
        log.info("Combinados por tipo de alcohol (" + String.join(", ", ALCOHOL_TYPES)
                + "): 9 EUR / 11 EUR, 50 ml por cubata de su botella.");
    }

    private void cubataVariant(Product p, String name, String price, int order, ProductVariant bottle) {
        ProductVariant v = new ProductVariant();
        v.setName(name);
        v.setPrice(new BigDecimal(price));
        v.setSortOrder(order);
        p.addVariant(v);
        VariantRecipeItem item = new VariantRecipeItem();
        item.setTenantId(p.getTenantId());
        item.setVariant(v);
        item.setComponent(bottle);
        item.setQuantity(CUBATA_ML);
        v.getRecipe().add(item);
    }

    private static ProductCategory find(List<ProductCategory> categories, String name) {
        return categories.stream().filter(c -> c.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    /** Reutiliza la categoria si ya existe (activandola y renombrandola) o la crea. */
    private ProductCategory prepare(ProductCategory existing, UUID tenantId, String name, String color, int order) {
        if (existing == null) {
            return category(tenantId, name, color, order, CategoryKind.BAR);
        }
        existing.setName(name);
        existing.setActive(true);
        existing.setSortOrder(order);
        existing.setKind(CategoryKind.BAR);
        return existing;
    }

    private ProductCategory category(UUID tenantId, String name, String color, int order, CategoryKind kind) {
        ProductCategory c = new ProductCategory();
        c.setTenantId(tenantId);
        c.setName(name);
        c.setColor(color);
        c.setSortOrder(order);
        c.setKind(kind);
        return categoryRepository.save(c);
    }

    private ProductVariant bottle(ProductCategory category, String name, String variantName, int ml) {
        Product p = newProduct(category, name);
        ProductVariant v = new ProductVariant();
        v.setName(variantName);
        v.setPrice(BigDecimal.ZERO); // no se vende entera en la barra
        v.setStockUnit(StockUnit.ML);
        v.setUnitSizeMl(ml);
        v.setReturnable(true); // el proveedor se lleva la botella vacia
        p.addVariant(v);
        return productRepository.save(p).getVariants().getFirst();
    }

    private static Product newProduct(ProductCategory category, String name) {
        Product p = new Product();
        p.setTenantId(category.getTenantId());
        p.setCategory(category);
        p.setName(name);
        p.setTrackStock(true);
        return p;
    }
}
