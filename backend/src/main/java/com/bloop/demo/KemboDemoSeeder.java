package com.bloop.demo;

import com.bloop.inventory.entity.Supplier;
import com.bloop.inventory.entity.Warehouse;
import com.bloop.inventory.repository.SupplierRepository;
import com.bloop.inventory.repository.WarehouseRepository;
import com.bloop.pos.entity.CategoryKind;
import com.bloop.pos.entity.PosTerminal;
import com.bloop.pos.entity.Product;
import com.bloop.pos.entity.ProductCategory;
import com.bloop.pos.entity.ProductVariant;
import com.bloop.pos.entity.StockUnit;
import com.bloop.pos.entity.TerminalType;
import com.bloop.pos.entity.VariantRecipeItem;
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
import com.bloop.tenants.entity.Tenant;
import com.bloop.tenants.repository.TenantRepository;
import com.bloop.users.repository.UserRepository;
import com.bloop.users.service.UserAccountService;
import com.bloop.venues.entity.Venue;
import com.bloop.venues.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Datos de demostracion de KEMBO (Zaragoza), solo perfil dev con bloop.demo-data.dataset=kembo.
 *
 * - Empresa Kembo y su discoteca (abre jueves, viernes y sabado; algun sabado hay tardeo).
 * - Dos salas: "Sala L" (forma en L) y "Sala U" (forma en U), cada una con 10 reservados colocados en
 *   el plano; 2 de cada sala son "Premium DJ", junto a la cabina, con mas personas y mas botellas.
 * - Carta de barra (Combinados por tipo de alcohol 9/11 EUR, Refrescos y aguas, Cervezas, Chupitos 3 EUR),
 *   botellas para inventario (en ml, retornables), botellas VIP para los reservados y taquilla.
 * - Almacen y una barra por sala, terminales, proveedores y cuentas del equipo.
 * El historico (3 meses pasados y el mes que viene) lo genera KemboHistoryGenerator.
 * Idempotente: si ya existe la empresa "kembo" no hace nada.
 */
@Component
@Order(1)
@Profile("dev")
@RequiredArgsConstructor
public class KemboDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KemboDemoSeeder.class);
    static final String SLUG = "kembo";
    static final List<String> ALCOHOLS = List.of("Ron", "Ginebra", "Whisky", "Vodka", "Tequila");

    private final BloopProperties properties;
    private final TenantRepository tenantRepository;
    private final VenueRepository venueRepository;
    private final UserRepository userRepository;
    private final UserAccountService userAccountService;
    private final VipZoneRepository zoneRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final PosTerminalRepository terminalRepository;
    private final SupplierRepository supplierRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var demo = properties.demoData();
        if (demo == null || !demo.isDataset("kembo")
                || tenantRepository.findAll().stream().anyMatch(t -> SLUG.equals(t.getSlug()))) {
            return;
        }
        String password = demo.password();

        Tenant tenant = new Tenant();
        tenant.setName("Kembo");
        tenant.setSlug(SLUG);
        tenant.setLegalName("Kembo Ocio Nocturno S.L.");
        tenant.setTaxId("B50999001");
        tenant.setContactEmail("hola@kembo.test");
        tenant = tenantRepository.save(tenant);
        UUID tenantId = tenant.getId();

        Venue venue = new Venue();
        venue.setTenantId(tenantId);
        venue.setName("Kembo");
        venue.setSlug("kembo-zaragoza");
        venue.setCity("Zaragoza");
        venue.setAddressLine("Calle Doctor Cerrada 30");
        venue.setPostalCode("50005");
        venue.setCountry("ES");
        venue.setCapacity(900);
        venue.setOpeningHours("Jue-Sab 23:30-06:00 · Tardeo algunos sabados 18:00-22:30");
        venue.setDescription("Discoteca en el centro de Zaragoza con dos salas: Sala L (house y reggaeton) "
                + "y Sala U (techno y electronica). Reservados VIP con botella y zona premium junto al DJ.");
        venue = venueRepository.save(venue);
        Set<UUID> venues = Set.of(venue.getId());

        // ---------------------------------------------------------------- Cuentas
        if (!userRepository.existsByEmail("superadmin@bloop.test")) {
            userAccountService.createAccount(null, "superadmin@bloop.test", password, "Super", "Admin", null,
                    Set.of(RoleCode.SUPER_ADMIN.name()), Set.of());
        }
        userAccountService.createAccount(tenantId, "admin@kembo.test", password, "Alberto", "Lahoz", null,
                Set.of(RoleCode.TENANT_ADMIN.name()), Set.of());
        userAccountService.createAccount(tenantId, "encargado@kembo.test", password, "Nuria", "Gracia", null,
                Set.of(RoleCode.MANAGER.name()), venues);
        userAccountService.createAccount(tenantId, "caja@kembo.test", password, "Sergio", "Lacasa", null,
                Set.of(RoleCode.CASHIER.name()), venues);
        userAccountService.createAccount(tenantId, "puerta@kembo.test", password, "Ivan", "Royo", null,
                Set.of(RoleCode.DOOR_STAFF.name()), venues);
        userAccountService.createAccount(tenantId, "servicio@kembo.test", password, "Paula", "Sanz", null,
                Set.of(RoleCode.VIP_WAITER.name()), venues);
        userAccountService.createAccount(tenantId, "rrpp@kembo.test", password, "Marcos", "Aznar", null,
                Set.of(RoleCode.PROMOTER.name()), venues);
        userAccountService.createAccount(null, "cliente@kembo.test", password, "Laura", "Bielsa", "+34 600 100 200",
                Set.of(RoleCode.CUSTOMER.name()), Set.of()).setPrivacyAcceptedAt(Instant.now());

        // ---------------------------------------------------------------- Carta
        ProductCategory combinados = category(tenantId, "Combinados", "violet", 0, CategoryKind.BAR);
        ProductCategory soft = category(tenantId, "Refrescos y aguas", "sky", 1, CategoryKind.BAR);
        ProductCategory beers = category(tenantId, "Cervezas", "amber", 2, CategoryKind.BAR);
        ProductCategory shots = category(tenantId, "Chupitos", "rose", 3, CategoryKind.BAR);
        ProductCategory supply = category(tenantId, "Botellas (inventario)", "zinc", 10, CategoryKind.SUPPLY);
        ProductCategory vipBottles = category(tenantId, "Botellas VIP", "violet", 11, CategoryKind.VIP_BOTTLE);
        ProductCategory service = category(tenantId, "Servicio de mesa", "sky", 12, CategoryKind.SERVICE);
        ProductCategory door = category(tenantId, "Taquilla", "emerald", 13, CategoryKind.OTHER);

        // Botella por tipo de alcohol (inventario en ml, retornable) y su combinado (50 ml)
        String[] bottleCost = {"13.50", "14.80", "15.20", "13.90", "16.40"};
        for (int i = 0; i < ALCOHOLS.size(); i++) {
            Product bottleProduct = newProduct(supply, ALCOHOLS.get(i), i, true);
            ProductVariant bottle = variant("Botella 70 cl", "0.00", bottleCost[i], 0);
            bottle.setStockUnit(StockUnit.ML);
            bottle.setUnitSizeMl(700);
            bottle.setReturnable(true);
            bottleProduct.addVariant(bottle);
            bottle = productRepository.save(bottleProduct).getVariants().getFirst();
            Product cubata = newProduct(combinados, ALCOHOLS.get(i), i, true);
            cubata.addVariant(variant("Cubata", "9.00", null, 0));
            cubata.addVariant(variant("Con Red Bull", "11.00", null, 1));
            cubata.getVariants().forEach(v -> v.getRecipe().add(recipe(v, bottle(cubata, bottleProduct), 50)));
            productRepository.save(cubata);
        }
        single(soft, "Refresco", "Botellin", "3.50", "0.55", true, true);
        single(soft, "Agua", "Botella 50 cl", "2.50", "0.25", true, false);
        single(soft, "Red Bull", "Lata", "4.00", "1.05", true, false);
        single(beers, "Cerveza", "Botellin", "4.00", "0.70", true, true);
        single(beers, "Cerveza sin alcohol", "Botellin", "4.00", "0.75", true, true);
        single(shots, "Chupito", "Chupito", "3.00", null, false, false);

        Map<String, ProductVariant> vip = new LinkedHashMap<>();
        vip.put("ron", single(vipBottles, "Ron anejo", "Botella 70 cl", "130.00", null, false, false));
        vip.put("gin", single(vipBottles, "Ginebra premium", "Botella 70 cl", "150.00", null, false, false));
        vip.put("vodka", single(vipBottles, "Vodka premium", "Botella 70 cl", "140.00", null, false, false));
        vip.put("whisky", single(vipBottles, "Whisky reserva", "Botella 70 cl", "150.00", null, false, false));
        vip.put("champagne", single(vipBottles, "Champagne brut", "Botella 75 cl", "220.00", null, false, false));
        single(service, "Refrescos para la mesa", "Pack 6", "18.00", null, false, false);
        single(service, "Cubitera de hielo", "Cubitera", "5.00", null, false, false);
        single(service, "Bengala", "Unidad", "10.00", null, false, false);
        single(door, "Entrada en puerta", "General", "15.00", null, false, false);
        single(door, "Guardarropa", "Prenda", "2.00", null, false, false);

        // ---------------------------------------------------------------- Almacenes, terminales y proveedores
        Warehouse store = warehouse(venue, "Almacen");
        Warehouse barL = warehouse(venue, "Barra Sala L");
        Warehouse barU = warehouse(venue, "Barra Sala U");
        terminal(venue, barL, "Barra Sala L", TerminalType.BAR, "BAR");
        terminal(venue, barU, "Barra Sala U", TerminalType.BAR, "BAR");
        terminal(venue, store, "Taquilla", TerminalType.BOX_OFFICE, "OTHER");
        terminal(venue, store, "Servicio VIP", TerminalType.VIP_SERVICE, "SERVICE,VIP_BOTTLE");
        supplier(tenantId, "Distribuciones Ebro Licores", "Javier Pardo", "976 100 200");
        supplier(tenantId, "Bebidas del Valle S.L.", "Ana Clemente", "976 300 400");
        supplier(tenantId, "Hielo y Aguas Zaragoza", "Luis Ballarin", "976 500 600");

        // ---------------------------------------------------------------- Salas y reservados
        zone(venue, "Sala L", "House, comercial y reggaeton. Forma de L.", 0, 450, "violet",
                layout(20, 12, (r, c) -> !(r < 6 && c >= 10),
                        "{\"type\":\"DJ\",\"label\":\"Cabina DJ\",\"x\":4,\"y\":4,\"w\":14,\"h\":10},"
                                + "{\"type\":\"DANCEFLOOR\",\"label\":\"Pista\",\"x\":4,\"y\":20,\"w\":34,\"h\":32},"
                                + "{\"type\":\"BAR\",\"label\":\"Barra Sala L\",\"x\":55,\"y\":86,\"w\":40,\"h\":8},"
                                + "{\"type\":\"ENTRANCE\",\"label\":\"Entrada\",\"x\":2,\"y\":88,\"w\":10,\"h\":8}"),
                new Object[][] {
                        {"Premium DJ 1", 10, "320.00", 22, 5, 10, 16, "champagne", "gin"},
                        {"Premium DJ 2", 10, "320.00", 34, 5, 10, 16, "champagne", "vodka"},
                        {"L1", 6, "150.00", 6, 58, 8, 12.8, "gin"}, {"L2", 6, "150.00", 18, 58, 8, 12.8, "ron"},
                        {"L3", 6, "150.00", 30, 58, 8, 12.8, "vodka"}, {"L4", 6, "150.00", 44, 60, 8, 12.8, "gin"},
                        {"L5", 6, "150.00", 56, 60, 8, 12.8, "whisky"}, {"L6", 6, "150.00", 68, 60, 8, 12.8, "ron"},
                        {"L7", 6, "150.00", 80, 60, 8, 12.8, "gin"}, {"L8", 8, "180.00", 88, 72, 9, 14.4, "vodka"}},
                vip);
        zone(venue, "Sala U", "Techno y electronica. Forma de U con el DJ en el fondo.", 1, 450, "sky",
                layout(20, 12, (r, c) -> !(r < 7 && c >= 6 && c < 14),
                        "{\"type\":\"DJ\",\"label\":\"Cabina DJ\",\"x\":42,\"y\":80,\"w\":16,\"h\":10},"
                                + "{\"type\":\"DANCEFLOOR\",\"label\":\"Pista\",\"x\":32,\"y\":62,\"w\":36,\"h\":16},"
                                + "{\"type\":\"BAR\",\"label\":\"Barra Sala U\",\"x\":2,\"y\":4,\"w\":24,\"h\":8},"
                                + "{\"type\":\"TOILETS\",\"label\":\"Banos\",\"x\":76,\"y\":4,\"w\":20,\"h\":8}"),
                new Object[][] {
                        {"Premium DJ 1", 10, "320.00", 28, 80, 10, 16, "champagne", "gin"},
                        {"Premium DJ 2", 10, "320.00", 62, 80, 10, 16, "champagne", "whisky"},
                        {"U1", 6, "150.00", 6, 20, 8, 12.8, "gin"}, {"U2", 6, "150.00", 6, 38, 8, 12.8, "ron"},
                        {"U3", 6, "150.00", 6, 56, 8, 12.8, "vodka"}, {"U4", 6, "150.00", 86, 20, 8, 12.8, "gin"},
                        {"U5", 6, "150.00", 86, 38, 8, 12.8, "whisky"}, {"U6", 6, "150.00", 86, 56, 8, 12.8, "ron"},
                        {"U7", 8, "180.00", 14, 76, 9, 14.4, "vodka"}, {"U8", 8, "180.00", 78, 76, 9, 14.4, "gin"}},
                vip);

        log.info("""

                ======================== DATOS DEMO DE KEMBO (ZARAGOZA) ========================
                Contrasena de todas las cuentas: DEMO_PASSWORD (por defecto Demo1234!)
                  admin@kembo.test       -> administrador        encargado@kembo.test -> encargado
                  caja@kembo.test        -> TPV de barra          puerta@kembo.test    -> control de acceso
                  servicio@kembo.test    -> camarero VIP          rrpp@kembo.test      -> relaciones publicas
                  cliente@kembo.test     -> cliente (web)         superadmin@bloop.test -> plataforma
                ================================================================================""");
    }

    // ---------------------------------------------------------------- Auxiliares

    private static ProductVariant bottle(Product ignored, Product bottleProduct) {
        return bottleProduct.getVariants().getFirst();
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

    private static Product newProduct(ProductCategory category, String name, int order, boolean trackStock) {
        Product p = new Product();
        p.setTenantId(category.getTenantId());
        p.setCategory(category);
        p.setName(name);
        p.setSortOrder(order);
        p.setTrackStock(trackStock);
        return p;
    }

    private static ProductVariant variant(String name, String price, String cost, int order) {
        ProductVariant v = new ProductVariant();
        v.setName(name);
        v.setPrice(new BigDecimal(price));
        v.setCost(cost == null ? null : new BigDecimal(cost));
        v.setSortOrder(order);
        return v;
    }

    private static VariantRecipeItem recipe(ProductVariant v, ProductVariant component, int ml) {
        VariantRecipeItem item = new VariantRecipeItem();
        item.setTenantId(v.getTenantId());
        item.setVariant(v);
        item.setComponent(component);
        item.setQuantity(ml);
        return item;
    }

    /** Producto de una sola variante. Devuelve la variante guardada. */
    private ProductVariant single(ProductCategory category, String name, String variantName, String price,
                                  String cost, boolean trackStock, boolean returnable) {
        Product p = newProduct(category, name, 0, trackStock);
        ProductVariant v = variant(variantName, price, cost, 0);
        v.setReturnable(returnable);
        p.addVariant(v);
        return productRepository.save(p).getVariants().getFirst();
    }

    private Warehouse warehouse(Venue venue, String name) {
        Warehouse w = new Warehouse();
        w.setTenantId(venue.getTenantId());
        w.setVenueId(venue.getId());
        w.setName(name);
        return warehouseRepository.save(w);
    }

    private void terminal(Venue venue, Warehouse warehouse, String name, TerminalType type, String kinds) {
        PosTerminal t = new PosTerminal();
        t.setTenantId(venue.getTenantId());
        t.setVenueId(venue.getId());
        t.setWarehouseId(warehouse.getId());
        t.setName(name);
        t.setType(type);
        t.setAllowedKinds(kinds);
        terminalRepository.save(t);
    }

    private void supplier(UUID tenantId, String name, String contact, String phone) {
        Supplier s = new Supplier();
        s.setTenantId(tenantId);
        s.setName(name);
        s.setContactName(contact);
        s.setPhone(phone);
        supplierRepository.save(s);
    }

    interface Cell {
        boolean floor(int row, int col);
    }

    /** JSON de la forma de la sala: rejilla de celdas (1 = suelo) y elementos fijos. */
    private static String layout(int cols, int rows, Cell cell, String elements) {
        StringBuilder cells = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cells.append(cell.floor(r, c) ? '1' : '0');
            }
        }
        return "{\"cols\":" + cols + ",\"rows\":" + rows + ",\"cells\":\"" + cells + "\",\"elements\":[" + elements + "]}";
    }

    /** Cada mesa: {nombre, personas, precio reserva+paquete, x, y, ancho, alto, botellas del paquete...}. */
    private void zone(Venue venue, String name, String description, int order, int capacity, String color,
                      String layout, Object[][] tables, Map<String, ProductVariant> vip) {
        VipZone zone = new VipZone();
        zone.setTenantId(venue.getTenantId());
        zone.setVenueId(venue.getId());
        zone.setName(name);
        zone.setDescription(description);
        zone.setSortOrder(order);
        zone.setCapacity(capacity);
        zone.setColor(color);
        zone.setLayout(layout);
        for (Object[] t : tables) {
            VipTable table = new VipTable();
            table.setName((String) t[0]);
            table.setCapacity((Integer) t[1]);
            table.setDepositAmount(new BigDecimal((String) t[2]));
            table.setMinSpend(BigDecimal.ZERO);
            table.setPosX(BigDecimal.valueOf(((Number) t[3]).doubleValue()));
            table.setPosY(BigDecimal.valueOf(((Number) t[4]).doubleValue()));
            table.setWidth(BigDecimal.valueOf(((Number) t[5]).doubleValue()));
            table.setHeight(BigDecimal.valueOf(((Number) t[6]).doubleValue()));
            table.setShape(((String) t[0]).startsWith("Premium") ? VipTableShape.RECT : VipTableShape.ROUND);
            zone.addTable(table);
            for (int i = 7; i < t.length; i++) {
                VipTablePackageItem item = new VipTablePackageItem();
                item.setTenantId(venue.getTenantId());
                item.setTable(table);
                item.setVariant(vip.get((String) t[i]));
                item.setQuantity(1);
                table.getPackageItems().add(item);
            }
        }
        zoneRepository.save(zone);
    }
}
