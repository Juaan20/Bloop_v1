package com.bloop.demo;

import com.bloop.pos.entity.CategoryKind;
import com.bloop.pos.entity.PosTerminal;
import com.bloop.pos.entity.Product;
import com.bloop.pos.entity.ProductCategory;
import com.bloop.pos.entity.ProductVariant;
import com.bloop.pos.entity.TerminalType;
import com.bloop.pos.repository.PosTerminalRepository;
import com.bloop.pos.repository.ProductCategoryRepository;
import com.bloop.pos.repository.ProductRepository;
import com.bloop.reservations.entity.VipTable;
import com.bloop.reservations.entity.VipTablePackageItem;
import com.bloop.reservations.entity.VipTableShape;
import com.bloop.reservations.entity.VipZone;
import com.bloop.reservations.repository.VipZoneRepository;
import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.enums.RoleCode;
import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import com.bloop.users.service.UserAccountService;
import com.bloop.venues.entity.Venue;
import com.bloop.venues.repository.VenueRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Datos demo del servicio VIP (solo perfil dev). Se anade tambien a una base de datos demo existente:
 * - Categorias "Botellas VIP" y "Servicio" con su carta.
 * - Plano colocado y paquete incluido en las mesas de Sala Aurora y Terraza Nebula.
 * - Usuario camarero VIP: servicio@noctambula.test.
 * - Taquilla vendiendo solo "otros" y barra solo productos de barra.
 * Es idempotente: si ya existe la categoria de botellas VIP no hace nada.
 */
@Component
@Order(3)
@Profile("dev")
// Juego de datos antiguo: solo con DEMO_DATASET=noctambula (por defecto se siembra Kembo).
@ConditionalOnExpression("${bloop.demo-data.enabled:false} and '${bloop.demo-data.dataset:kembo}' == 'noctambula'")
@RequiredArgsConstructor
public class VipServiceDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VipServiceDemoSeeder.class);

    private final UserRepository userRepository;
    private final UserAccountService userAccountService;
    private final VenueRepository venueRepository;
    private final VipZoneRepository zoneRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PosTerminalRepository terminalRepository;
    private final BloopProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User admin = userRepository.findByEmail("admin@noctambula.test").orElse(null);
        if (admin == null || admin.getTenantId() == null) {
            return;
        }
        seedVip(admin.getTenantId());
        seedBarCatalog(admin.getTenantId());
    }

    /**
     * Carta de barra de consumo rapido (refrescos, agua, energetica, cervezas, cubatas, combinados) y
     * terminal de servicio VIP. La botella de cava pasa a "Botellas VIP": no se vende en la barra normal.
     * Idempotente: si ya existe la categoria "Cubatas" no hace nada.
     */
    private void seedBarCatalog(UUID tenantId) {
        List<ProductCategory> categories = categoryRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId);
        if (categories.isEmpty() || categories.stream().anyMatch(c -> c.getName().equalsIgnoreCase("Cubatas"))) {
            return;
        }
        ProductCategory vipBottles = categories.stream().filter(c -> c.getKind() == CategoryKind.VIP_BOTTLE)
                .findFirst().orElse(null);
        ProductCategory soft = categories.stream().filter(c -> c.getName().equalsIgnoreCase("Refrescos"))
                .findFirst().orElse(null);
        for (Product p : productRepository.findAllOfTenant(tenantId)) {
            if (vipBottles != null && p.getName().equalsIgnoreCase("Botella de cava")) {
                p.setCategory(vipBottles);
            }
        }
        if (soft != null) {
            product(soft, "Bebida energetica", "Lata", "4.00", null);
        }
        ProductCategory cubatas = category(tenantId, "Cubatas", "violet", 2, CategoryKind.BAR);
        multi(cubatas, "Ron", "Ron con refresco", new String[][] {{"Normal", "9.00"}, {"Con energetica", "11.00"}});
        multi(cubatas, "Ginebra", "Ginebra con tonica o refresco", new String[][] {{"Normal", "10.00"}, {"Con energetica", "12.00"}});
        multi(cubatas, "Vodka", "Vodka con refresco o zumo", new String[][] {{"Normal", "9.00"}, {"Con energetica", "11.00"}});
        multi(cubatas, "Whisky", "Whisky con refresco", new String[][] {{"Normal", "10.00"}, {"Con energetica", "12.00"}});
        ProductCategory combinados = category(tenantId, "Combinados", "rose", 3, CategoryKind.BAR);
        multi(combinados, "Chupito", null, new String[][] {{"Unidad", "3.00"}, {"Ronda x4", "10.00"}});
        multi(combinados, "Tinto de verano", null, new String[][] {{"Vaso", "5.00"}});
        multi(combinados, "Sangria", null, new String[][] {{"Vaso", "6.00"}, {"Jarra", "18.00"}});

        Venue aurora = venueRepository.findByTenantIdOrderByName(tenantId).stream()
                .filter(v -> v.getName().equals("Sala Aurora")).findFirst().orElse(null);
        List<PosTerminal> terminals = terminalRepository.findByTenantIdOrderByName(tenantId);
        boolean hasVip = terminals.stream().anyMatch(t -> t.getType() == TerminalType.VIP_SERVICE);
        PosTerminal auroraBar = terminals.stream()
                .filter(t -> aurora != null && t.getVenueId().equals(aurora.getId()) && t.getType() == TerminalType.BAR)
                .findFirst().orElse(null);
        if (!hasVip && auroraBar != null) {
            PosTerminal vip = new PosTerminal();
            vip.setTenantId(tenantId);
            vip.setVenueId(auroraBar.getVenueId());
            vip.setWarehouseId(auroraBar.getWarehouseId());
            vip.setName("Servicio VIP");
            vip.setType(TerminalType.VIP_SERVICE);
            vip.setAllowedKinds("SERVICE,VIP_BOTTLE");
            terminalRepository.save(vip);
        }
        log.info("Carta de barra demo anadida (cubatas, combinados, energetica) y terminal 'Servicio VIP' en Sala Aurora.");
    }

    private void multi(ProductCategory category, String name, String description, String[][] variants) {
        Product p = new Product();
        p.setTenantId(category.getTenantId());
        p.setCategory(category);
        p.setName(name);
        p.setDescription(description);
        p.setTrackStock(false);
        int order = 0;
        for (String[] v : variants) {
            ProductVariant variant = new ProductVariant();
            variant.setName(v[0]);
            variant.setPrice(new BigDecimal(v[1]));
            variant.setSortOrder(order++);
            p.addVariant(variant);
        }
        productRepository.save(p);
    }

    private void seedVip(UUID tenantId) {
        List<ProductCategory> categories = categoryRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId);
        if (categories.isEmpty() || categories.stream().anyMatch(c -> c.getKind() == CategoryKind.VIP_BOTTLE)) {
            return; // sin catalogo TPV todavia, o ya sembrado
        }
        Map<String, Venue> venues = new HashMap<>();
        venueRepository.findByTenantIdOrderByName(tenantId).forEach(v -> venues.put(v.getName(), v));

        // Tipos de categoria y de terminal coherentes con la separacion barra / taquilla.
        categories.stream().filter(c -> c.getName().equalsIgnoreCase("Taquilla"))
                .forEach(c -> c.setKind(CategoryKind.OTHER));
        for (PosTerminal t : terminalRepository.findByTenantIdOrderByName(tenantId)) {
            t.setAllowedKinds(t.getType() == TerminalType.BOX_OFFICE ? "OTHER" : "BAR");
        }

        // Carta VIP: {producto, variante, precio, descripcion}
        ProductCategory bottles = category(tenantId, "Botellas VIP", "violet", 10, CategoryKind.VIP_BOTTLE);
        Map<String, ProductVariant> v = new HashMap<>();
        v.put("gin", product(bottles, "Ginebra premium", "Botella 70 cl", "180.00",
                "Ginebra London Dry. Incluye tonicas y botanicos."));
        v.put("vodka", product(bottles, "Vodka premium", "Botella 70 cl", "170.00",
                "Vodka destilado cinco veces, servido con zumos y hielo."));
        v.put("rum", product(bottles, "Ron anejo", "Botella 70 cl", "160.00", "Ron anejo 12 anos con refrescos de cola."));
        v.put("champagne", product(bottles, "Champagne brut", "Botella 75 cl", "220.00",
                "Champagne brut servido en cubitera con copas de cristal."));
        ProductCategory service = category(tenantId, "Servicio de mesa", "sky", 11, CategoryKind.SERVICE);
        v.put("mixers", product(service, "Pack de refrescos", "6 unidades", "18.00", "Tonica, cola, limon y naranja."));
        v.put("ice", product(service, "Cubitera de hielo", "Cubitera", "5.00", "Hielo extra para la mesa."));
        v.put("sparkler", product(service, "Bengala de servicio", "Unidad", "10.00", "Para acompanar la botella."));

        // Plano y paquetes: {nombre mesa, x, y, ancho, alto, forma, precio reserva+paquete, paquete...}
        layout(venues.get("Sala Aurora"), v, Map.of(
                "Mesa 1", new Object[] {18, 22, 10, 10, VipTableShape.ROUND, "250.00", Map.of("gin", 1, "mixers", 1)},
                "Mesa 2", new Object[] {34, 22, 10, 10, VipTableShape.ROUND, "250.00", Map.of("vodka", 1, "mixers", 1)},
                "Mesa 3", new Object[] {50, 20, 18, 12, VipTableShape.RECT, "450.00",
                        Map.of("gin", 1, "vodka", 1, "mixers", 2)},
                "Altillo A", new Object[] {74, 62, 12, 9, VipTableShape.RECT, "220.00", Map.of("champagne", 1)},
                "Altillo B", new Object[] {74, 80, 12, 9, VipTableShape.RECT, "0.00", Map.of()}));
        layout(venues.get("Terraza Nebula"), v, Map.of(
                "Sofa 1", new Object[] {25, 40, 16, 10, VipTableShape.RECT, "200.00", Map.of("rum", 1, "mixers", 1)},
                "Sofa 2", new Object[] {60, 40, 16, 10, VipTableShape.RECT, "200.00", Map.of("rum", 1, "mixers", 1)}));

        Venue aurora = venues.get("Sala Aurora");
        if (aurora != null && !userRepository.existsByEmail("servicio@noctambula.test")) {
            userAccountService.createAccount(tenantId, "servicio@noctambula.test", properties.demoData().password(),
                    "Sara", "Molina", null, Set.of(RoleCode.VIP_WAITER.name()), Set.of(aurora.getId()));
        }
        log.info("Datos demo del servicio VIP anadidos: carta de botellas, plano y paquetes de mesa, "
                + "camarero servicio@noctambula.test.");
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

    private ProductVariant product(ProductCategory category, String name, String variantName, String price,
                                   String description) {
        Product p = new Product();
        p.setTenantId(category.getTenantId());
        p.setCategory(category);
        p.setName(name);
        p.setDescription(description);
        p.setTrackStock(false);
        ProductVariant variant = new ProductVariant();
        variant.setName(variantName);
        variant.setPrice(new BigDecimal(price));
        p.addVariant(variant);
        return productRepository.save(p).getVariants().get(0);
    }

    @SuppressWarnings("unchecked")
    private void layout(Venue venue, Map<String, ProductVariant> variants, Map<String, Object[]> spec) {
        if (venue == null) {
            return;
        }
        for (VipZone zone : zoneRepository.findByVenueIdAndTenantIdOrderBySortOrderAscNameAsc(venue.getId(),
                venue.getTenantId())) {
            for (VipTable table : zone.getTables()) {
                Object[] s = spec.get(table.getName());
                if (s == null || table.getPosX() != null) {
                    continue;
                }
                table.setPosX(BigDecimal.valueOf((Integer) s[0]));
                table.setPosY(BigDecimal.valueOf((Integer) s[1]));
                table.setWidth(BigDecimal.valueOf((Integer) s[2]));
                table.setHeight(BigDecimal.valueOf((Integer) s[3]));
                table.setShape((VipTableShape) s[4]);
                table.setDepositAmount(new BigDecimal((String) s[5]));
                ((Map<String, Integer>) s[6]).forEach((key, qty) -> {
                    VipTablePackageItem item = new VipTablePackageItem();
                    item.setTenantId(table.getTenantId());
                    item.setTable(table);
                    item.setVariant(variants.get(key));
                    item.setQuantity(qty);
                    table.getPackageItems().add(item);
                });
            }
        }
    }
}
