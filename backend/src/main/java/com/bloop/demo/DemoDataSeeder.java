package com.bloop.demo;

import com.bloop.events.entity.Event;
import com.bloop.events.entity.EventSession;
import com.bloop.events.entity.EventStatus;
import com.bloop.events.entity.TicketCategory;
import com.bloop.events.entity.TicketType;
import com.bloop.events.repository.EventRepository;
import com.bloop.inventory.entity.StockLevel;
import com.bloop.inventory.entity.Warehouse;
import com.bloop.inventory.repository.StockLevelRepository;
import com.bloop.inventory.repository.WarehouseRepository;
import com.bloop.pos.entity.PosTerminal;
import com.bloop.pos.entity.Product;
import com.bloop.pos.entity.ProductCategory;
import com.bloop.pos.entity.ProductVariant;
import com.bloop.pos.entity.TerminalType;
import com.bloop.pos.repository.PosTerminalRepository;
import com.bloop.pos.repository.ProductCategoryRepository;
import com.bloop.pos.repository.ProductRepository;
import com.bloop.promoters.entity.CommissionType;
import com.bloop.promoters.entity.DiscountType;
import com.bloop.promoters.entity.PromoCode;
import com.bloop.promoters.entity.Promoter;
import com.bloop.promoters.entity.PromoterAssignment;
import com.bloop.promoters.repository.PromoCodeRepository;
import com.bloop.promoters.repository.PromoterRepository;
import com.bloop.reservations.entity.VipTable;
import com.bloop.reservations.entity.VipZone;
import com.bloop.reservations.repository.VipZoneRepository;
import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.enums.RoleCode;
import com.bloop.tenants.entity.Tenant;
import com.bloop.tenants.repository.TenantRepository;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Datos de demostracion para desarrollo local (perfil dev y bloop.demo-data.enabled=true).
 * Los datos de la fase 1 solo se crean si la base de datos no tiene usuarios; los de la fase 2
 * (mesas VIP, promotor, codigo) se anaden tambien a una base de datos demo ya existente. NO crea ventas: las estadisticas
 * del dashboard reflejan unicamente compras reales hechas desde la web.
 *
 * Todas las cuentas usan la contrasena de DEMO_PASSWORD (por defecto "Demo1234!").
 */
@Component
@org.springframework.core.annotation.Order(1)
@Profile("dev")
// Juego de datos antiguo: solo con DEMO_DATASET=noctambula (por defecto se siembra Kembo).
@ConditionalOnExpression("${bloop.demo-data.enabled:false} and '${bloop.demo-data.dataset:kembo}' == 'noctambula'")
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final VenueRepository venueRepository;
    private final EventRepository eventRepository;
    private final UserAccountService userAccountService;
    private final PromoterRepository promoterRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final VipZoneRepository vipZoneRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockLevelRepository stockLevelRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PosTerminalRepository terminalRepository;
    private final BloopProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() == 0) {
            seedBase();
        }
        // Se ejecuta siempre: completa con los datos de la fase 2 una base de datos demo creada antes.
        seedCommercialDemo();
    }

    /** Datos de la fase 1 (solo con la base de datos vacia). */
    private void seedBase() {
        String password = properties.demoData().password();

        // Plataforma y cliente final
        userAccountService.createAccount(null, "superadmin@bloop.test", password, "Super", "Admin", null,
                Set.of(RoleCode.SUPER_ADMIN.name()), Set.of());
        User customer = userAccountService.createAccount(null, "cliente@bloop.test", password, "Lucia",
                "Martin", "+34 600 000 001", Set.of(RoleCode.CUSTOMER.name()), Set.of());
        customer.setPrivacyAcceptedAt(java.time.Instant.now());

        // Empresa 1: Noctambula Group, con dos locales
        Tenant noctambula = tenant("Noctambula Group", "Noctambula Ocio S.L.", "B00000001", "hola@noctambula.test");
        Venue aurora = venue(noctambula, "Sala Aurora", "Madrid", "Calle de la Luna 12", "28004", 800,
                "Jue-Sab 23:30-06:00", "Club de musica electronica en el centro de Madrid.");
        Venue nebula = venue(noctambula, "Terraza Nebula", "Valencia", "Paseo Maritimo 40", "46011", 400,
                "Vie-Dom 18:00-03:00", "Terraza frente al mar con sesiones al atardecer.");

        userAccountService.createAccount(noctambula.getId(), "admin@noctambula.test", password, "Carlos", "Ruiz",
                null, Set.of(RoleCode.TENANT_ADMIN.name()), Set.of());
        userAccountService.createAccount(noctambula.getId(), "encargado@noctambula.test", password, "Marta",
                "Gil", null, Set.of(RoleCode.MANAGER.name()), Set.of(aurora.getId(), nebula.getId()));
        userAccountService.createAccount(noctambula.getId(), "puerta@noctambula.test", password, "Javier",
                "Soto", null, Set.of(RoleCode.DOOR_STAFF.name()), Set.of(aurora.getId()));
        userAccountService.createAccount(noctambula.getId(), "caja@noctambula.test", password, "Elena",
                "Vidal", null, Set.of(RoleCode.CASHIER.name()), Set.of(aurora.getId()));
        userAccountService.createAccount(noctambula.getId(), "rrpp@noctambula.test", password, "Dani",
                "Perez", null, Set.of(RoleCode.PROMOTER.name()), Set.of(aurora.getId()));

        LocalDate nextFriday = LocalDate.now(MADRID).with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
        LocalDate nextSaturday = LocalDate.now(MADRID).with(TemporalAdjusters.next(DayOfWeek.SATURDAY));

        Event opening = event(aurora, "Aurora Opening Night", EventStatus.PUBLISHED, 18,
                "La noche de apertura de temporada con DJs residentes y produccion audiovisual.");
        EventSession openingSession = session(opening, nextFriday, LocalTime.of(23, 30), 7, 800);
        ticket(openingSession, "Anticipada", TicketCategory.EARLY_BIRD, "12.00", 150, 0);
        ticket(openingSession, "General", TicketCategory.GENERAL, "18.00", 450, 1);
        ticket(openingSession, "VIP con consumicion", TicketCategory.VIP, "45.00", 60, 2);
        eventRepository.save(opening);

        Event techno = event(aurora, "Techno Sessions", EventStatus.PUBLISHED, 18,
                "Ciclo mensual de techno con artistas invitados.");
        EventSession techno1 = session(techno, nextSaturday, LocalTime.of(23, 59), 6, 700);
        ticket(techno1, "General", TicketCategory.GENERAL, "15.00", 600, 0);
        ticket(techno1, "Lista gratuita hasta la 1:00", TicketCategory.FREE, "0.00", 100, 1);
        EventSession techno2 = session(techno, nextSaturday.plusWeeks(1), LocalTime.of(23, 59), 6, 700);
        ticket(techno2, "General", TicketCategory.GENERAL, "15.00", 650, 0);
        eventRepository.save(techno);

        Event sunset = event(nebula, "Nebula Sunset", EventStatus.PUBLISHED, null,
                "Sesion de house al atardecer frente al Mediterraneo.");
        EventSession sunsetSession = session(sunset, nextSaturday, LocalTime.of(18, 0), 7, 400);
        ticket(sunsetSession, "General", TicketCategory.GENERAL, "10.00", 350, 0);
        // Una entrada VIP es un ACCESO (se valida con QR). Las mesas se reservan aparte (zonas VIP del local).
        ticket(sunsetSession, "VIP con copa de bienvenida", TicketCategory.VIP, "25.00", 60, 1);
        eventRepository.save(sunset);

        // Sesion que empieza 1 hora despues del primer arranque: permite probar ya el control de acceso.
        ZonedDateTime soon = ZonedDateTime.now(MADRID).plusHours(1).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        Event lateNight = event(aurora, "Late Night Demo", EventStatus.PUBLISHED, 18,
                "Sesion de demostracion que empieza poco despues de arrancar Bloop: compra una entrada "
                        + "y validala con el usuario de puerta.");
        EventSession lateSession = session(lateNight, soon.toLocalDate(), soon.toLocalTime(), 6, 500);
        ticket(lateSession, "General", TicketCategory.GENERAL, "10.00", 400, 0);
        eventRepository.save(lateNight);

        Event newYear = event(aurora, "Fin de Ano 2026", EventStatus.DRAFT, 18,
                "Borrador: configura las entradas y publicalo desde el panel.");
        session(newYear, LocalDate.of(2026, 12, 31), LocalTime.of(23, 30), 7, 800);
        eventRepository.save(newYear);

        // Empresa 2: sirve para comprobar el aislamiento multi-tenant.
        Tenant sonora = tenant("Sonora Live", "Sonora Directo S.L.", "B00000002", "hola@sonora.test");
        Venue sonoraHall = venue(sonora, "Sonora Hall", "Barcelona", "Carrer del So 5", "08001", 1200,
                "Segun programacion", "Sala de conciertos en directo.");
        userAccountService.createAccount(sonora.getId(), "admin@sonora.test", password, "Laura", "Ferrer", null,
                Set.of(RoleCode.TENANT_ADMIN.name()), Set.of());
        Event indie = event(sonoraHall, "Indie Live Showcase", EventStatus.PUBLISHED, null,
                "Cuatro bandas emergentes en directo.");
        EventSession indieSession = session(indie, nextFriday.plusWeeks(1), LocalTime.of(21, 0), 4, 1200);
        ticket(indieSession, "General", TicketCategory.GENERAL, "22.00", 1000, 0);
        eventRepository.save(indie);

        log.info("""

                ==================== DATOS DE DEMOSTRACION CREADOS ====================
                Contrasena de todas las cuentas: la definida en DEMO_PASSWORD (por defecto Demo1234!)
                  superadmin@bloop.test   -> SUPER_ADMIN (gestion de empresas)
                  admin@noctambula.test       -> TENANT_ADMIN de Noctambula Group
                  encargado@noctambula.test   -> MANAGER
                  puerta@noctambula.test      -> DOOR_STAFF (Sala Aurora)
                  caja@noctambula.test        -> CASHIER
                  rrpp@noctambula.test        -> PROMOTER (enlace ?rrpp=DANI, codigo DANI10)
                  admin@sonora.test           -> TENANT_ADMIN de Sonora Live (otra empresa)
                  cliente@bloop.test      -> CUSTOMER (web publica)
                =======================================================================""");
    }

    /**
     * Datos de la fase 2 para la empresa demo Noctambula: zonas y mesas VIP, promotor DANI y codigo DANI10.
     * Es idempotente: solo crea lo que falta, asi funciona tambien sobre una base de datos de la fase 1.
     */
    private void seedCommercialDemo() {
        User rrpp = userRepository.findByEmail("rrpp@noctambula.test").orElse(null);
        if (rrpp == null || rrpp.getTenantId() == null) {
            return; // no es la base de datos de demostracion
        }
        UUID tenantId = rrpp.getTenantId();
        Map<String, Venue> venues = venueRepository.findByTenantIdOrderByName(tenantId).stream()
                .collect(Collectors.toMap(Venue::getName, Function.identity(), (a, b) -> a));
        boolean changed = false;

        Venue aurora = venues.get("Sala Aurora");
        if (aurora != null && hasNoVipZones(aurora)) {
            vipZone(aurora, "Reservado escenario", "Junto a la cabina del DJ", 0,
                    new String[][] {{"Mesa 1", "6", "300.00", "100.00"}, {"Mesa 2", "6", "300.00", "100.00"},
                            {"Mesa 3", "10", "500.00", "150.00"}});
            vipZone(aurora, "Altillo", "Vista a toda la pista", 1,
                    new String[][] {{"Altillo A", "4", "150.00", "50.00"}, {"Altillo B", "4", "150.00", "0.00"}});
            changed = true;
        }
        Venue nebula = venues.get("Terraza Nebula");
        if (nebula != null && hasNoVipZones(nebula)) {
            vipZone(nebula, "Terraza mar", "Primera linea frente al Mediterraneo", 0,
                    new String[][] {{"Sofa 1", "8", "250.00", "80.00"}, {"Sofa 2", "8", "250.00", "80.00"}});
            changed = true;
        }

        // En la fase 1 la "Mesa VIP (4 personas)" era un tipo de ENTRADA. Ahora las mesas se reservan
        // (zonas VIP), asi que se deja de vender para no confundirlas. Las ya vendidas siguen siendo validas.
        for (EventSession session : eventRepository.findBySlugAndStatus("nebula-sunset", EventStatus.PUBLISHED)
                .map(Event::getSessions).orElse(List.of())) {
            for (TicketType type : session.getTicketTypes()) {
                if (type.isActive() && type.getName().startsWith("Mesa VIP")) {
                    type.setActive(false);
                    changed = true;
                }
            }
        }

        if (!promoterRepository.existsByUserId(rrpp.getId())) {
            Promoter promoter = new Promoter();
            promoter.setTenantId(tenantId);
            promoter.setUserId(rrpp.getId());
            promoter.setReferralCode(promoterRepository.existsByReferralCode("DANI") ? "DANI-DEMO" : "DANI");
            promoter.setCommissionType(CommissionType.PERCENTAGE);
            promoter.setCommissionValue(new BigDecimal("10.00"));
            eventRepository.findBySlugAndStatus("aurora-opening-night", EventStatus.PUBLISHED)
                    .ifPresent(e -> promoter.getAssignments().add(new PromoterAssignment(e.getId(), 100)));
            eventRepository.findBySlugAndStatus("techno-sessions", EventStatus.PUBLISHED)
                    .ifPresent(e -> promoter.getAssignments().add(new PromoterAssignment(e.getId(), null)));
            Promoter saved = promoterRepository.save(promoter);
            if (!promoCodeRepository.existsByTenantIdAndCode(tenantId, "DANI10")) {
                PromoCode promo = new PromoCode();
                promo.setTenantId(tenantId);
                promo.setCode("DANI10");
                promo.setDescription("10 % de descuento del RRPP Dani");
                promo.setDiscountType(DiscountType.PERCENTAGE);
                promo.setDiscountValue(new BigDecimal("10.00"));
                promo.setPromoterId(saved.getId());
                promo.setMaxUses(200);
                promoCodeRepository.save(promo);
            }
            changed = true;
        }
        if (changed) {
            log.info("Datos demo de la fase 2 anadidos: mesas VIP en Sala Aurora y Terraza Nebula, promotor DANI "
                    + "(rrpp@noctambula.test) y codigo DANI10.");
        }
        if (aurora != null && warehouseRepository.findByTenantIdOrderByName(tenantId).isEmpty()) {
            seedPosDemo(aurora);
        }
    }

    /** Fase 3: almacen, catalogo con existencias y dos terminales (barra y taquilla) en Sala Aurora. */
    private void seedPosDemo(Venue aurora) {
        UUID tenantId = aurora.getTenantId();
        Warehouse warehouse = new Warehouse();
        warehouse.setTenantId(tenantId);
        warehouse.setVenueId(aurora.getId());
        warehouse.setName("Barra principal");
        warehouse = warehouseRepository.save(warehouse);

        ProductCategory beers = category(tenantId, "Cervezas", "amber", 0);
        ProductCategory drinks = category(tenantId, "Copas", "violet", 1);
        ProductCategory soft = category(tenantId, "Refrescos", "sky", 2);
        ProductCategory door = category(tenantId, "Taquilla", "emerald", 3);

        // {nombre variante, precio, existencias iniciales, minimo de aviso}
        product(beers, "Cerveza", true, warehouse, new String[][] {{"Cana", "3.00", "120", "20"}, {"Pinta", "5.00", "80", "15"}});
        product(beers, "Cerveza sin alcohol", true, warehouse, new String[][] {{"Botellin", "3.50", "24", "6"}});
        product(drinks, "Gin tonic", true, warehouse, new String[][] {{"Copa", "10.00", "60", "10"}});
        product(drinks, "Ron cola", true, warehouse, new String[][] {{"Copa", "9.00", "60", "10"}});
        product(drinks, "Botella de cava", true, warehouse, new String[][] {{"Botella", "45.00", "6", "2"}});
        product(drinks, "Mojito", false, warehouse, new String[][] {{"Copa", "9.00", "0", "0"}});
        product(soft, "Refresco", true, warehouse, new String[][] {{"Lata", "3.00", "48", "12"}});
        product(soft, "Agua", true, warehouse, new String[][] {{"Botella", "2.00", "48", "12"}});
        product(door, "Entrada en puerta", false, warehouse, new String[][] {{"General", "20.00", "0", "0"}});
        product(door, "Guardarropa", false, warehouse, new String[][] {{"Prenda", "2.00", "0", "0"}});

        terminal(aurora, warehouse, "Barra 1", TerminalType.BAR);
        terminal(aurora, warehouse, "Taquilla", TerminalType.BOX_OFFICE);
        log.info("Datos demo de la fase 3 anadidos: TPV en Sala Aurora (terminales Barra 1 y Taquilla, "
                + "usuario caja@noctambula.test).");
    }

    private ProductCategory category(UUID tenantId, String name, String color, int sortOrder) {
        ProductCategory category = new ProductCategory();
        category.setTenantId(tenantId);
        category.setName(name);
        category.setColor(color);
        category.setSortOrder(sortOrder);
        return categoryRepository.save(category);
    }

    private void product(ProductCategory category, String name, boolean trackStock, Warehouse warehouse,
                         String[][] variants) {
        Product product = new Product();
        product.setTenantId(category.getTenantId());
        product.setCategory(category);
        product.setName(name);
        product.setTrackStock(trackStock);
        int order = 0;
        for (String[] v : variants) {
            ProductVariant variant = new ProductVariant();
            variant.setName(v[0]);
            variant.setPrice(new BigDecimal(v[1]));
            variant.setSortOrder(order++);
            product.addVariant(variant);
        }
        product = productRepository.save(product);
        if (!trackStock) {
            return;
        }
        for (int i = 0; i < variants.length; i++) {
            StockLevel level = new StockLevel();
            level.setTenantId(category.getTenantId());
            level.setWarehouseId(warehouse.getId());
            level.setVariantId(product.getVariants().get(i).getId());
            level.setQuantity(Integer.parseInt(variants[i][2]));
            level.setMinQuantity(Integer.parseInt(variants[i][3]));
            level.setUpdatedAt(java.time.Instant.now());
            stockLevelRepository.save(level);
        }
    }

    private void terminal(Venue venue, Warehouse warehouse, String name, TerminalType type) {
        PosTerminal terminal = new PosTerminal();
        terminal.setTenantId(venue.getTenantId());
        terminal.setVenueId(venue.getId());
        terminal.setWarehouseId(warehouse.getId());
        terminal.setName(name);
        terminal.setType(type);
        terminalRepository.save(terminal);
    }

    private boolean hasNoVipZones(Venue venue) {
        return vipZoneRepository.findByVenueIdAndTenantIdOrderBySortOrderAscNameAsc(venue.getId(), venue.getTenantId())
                .isEmpty();
    }

    /** Zona VIP con mesas: cada mesa es {nombre, capacidad, consumo minimo, anticipo}. */
    private void vipZone(Venue venue, String name, String description, int sortOrder, String[][] tables) {
        VipZone zone = new VipZone();
        zone.setTenantId(venue.getTenantId());
        zone.setVenueId(venue.getId());
        zone.setName(name);
        zone.setDescription(description);
        zone.setSortOrder(sortOrder);
        for (String[] t : tables) {
            VipTable table = new VipTable();
            table.setName(t[0]);
            table.setCapacity(Integer.parseInt(t[1]));
            table.setMinSpend(new BigDecimal(t[2]));
            table.setDepositAmount(new BigDecimal(t[3]));
            zone.addTable(table);
        }
        vipZoneRepository.save(zone);
    }

    private Tenant tenant(String name, String legalName, String taxId, String email) {
        Tenant tenant = new Tenant();
        tenant.setName(name);
        tenant.setSlug(com.bloop.shared.util.SlugUtils.slugify(name));
        tenant.setLegalName(legalName);
        tenant.setTaxId(taxId);
        tenant.setContactEmail(email);
        return tenantRepository.save(tenant);
    }

    private Venue venue(Tenant tenant, String name, String city, String address, String postalCode, int capacity,
                        String hours, String description) {
        Venue venue = new Venue();
        venue.setTenantId(tenant.getId());
        venue.setName(name);
        venue.setSlug(com.bloop.shared.util.SlugUtils.slugify(name + " " + city));
        venue.setCity(city);
        venue.setAddressLine(address);
        venue.setPostalCode(postalCode);
        venue.setCountry("ES");
        venue.setCapacity(capacity);
        venue.setOpeningHours(hours);
        venue.setDescription(description);
        return venueRepository.save(venue);
    }

    private Event event(Venue venue, String name, EventStatus status, Integer minAge, String description) {
        Event event = new Event();
        event.setTenantId(venue.getTenantId());
        event.setVenue(venue);
        event.setName(name);
        event.setSlug(com.bloop.shared.util.SlugUtils.slugify(name));
        event.setDescription(description);
        event.setMinAge(minAge);
        event.setStatus(status);
        if (status == EventStatus.PUBLISHED) {
            event.setPublishedAt(java.time.Instant.now());
        }
        return event;
    }

    private EventSession session(Event event, LocalDate day, LocalTime start, int hours, int capacity) {
        ZonedDateTime startsAt = ZonedDateTime.of(day, start, MADRID);
        EventSession session = new EventSession();
        session.setStartsAt(startsAt.toInstant());
        session.setEndsAt(startsAt.plusHours(hours).toInstant());
        session.setDoorsOpenAt(startsAt.minusMinutes(30).toInstant());
        session.setCapacity(capacity);
        event.addSession(session);
        return session;
    }

    private void ticket(EventSession session, String name, TicketCategory category, String price, int quantity,
                        int sortOrder) {
        TicketType type = new TicketType();
        type.setName(name);
        type.setCategory(category);
        type.setPrice(new BigDecimal(price));
        type.setQuantityTotal(quantity);
        type.setMaxPerOrder(category == TicketCategory.VIP ? 4 : 10);
        type.setSortOrder(sortOrder);
        session.addTicketType(type);
    }
}
