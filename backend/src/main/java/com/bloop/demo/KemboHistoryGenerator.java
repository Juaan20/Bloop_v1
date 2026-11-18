package com.bloop.demo;

import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.util.SecureTokens;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Historico de KEMBO (Zaragoza): 3 meses de funcionamiento ya pasados y el mes que viene.
 *
 * Calendario: abre jueves (universitario), viernes y sabado de 23:30 a 06:00; uno de cada tres sabados
 * hay ademas tardeo (18:00-22:30). Noches especiales: Fiestas del Pilar y Halloween; en el futuro, el
 * puente de diciembre. Un jueves se cancelo por una averia electrica (pedidos reembolsados).
 *
 * Que se genera (con SQL directo porque las fechas son pasadas):
 * - ~1.600 clientes, pedidos de entradas (pagados, reembolsados, caducados), entradas nominales y puerta.
 * - Reservados VIP de las dos salas por fiesta: paquete prepagado, pedidos de botellas, cobro de extras
 *   y cuenta cerrada ("Liberar mesa"). Alguna reserva cancelada o sin presentarse.
 * - TPV: una caja por barra y noche (Sala L y Sala U) y la taquilla, con arqueo.
 * - Inventario como en una discoteca: albaran semanal de cada proveedor al almacen (con coste),
 *   traspasos almacen -> barra antes de abrir, ventas por receta (50 ml por cubata), recuento de cierre
 *   con merma pequena y devolucion de envases vacios al proveedor.
 * - Mes siguiente: fiestas publicadas con preventa y reservados ya confirmados.
 *
 * Solo perfil dev con DEMO_DATASET=kembo (por defecto). Se ejecuta UNA vez (si existe el evento
 * "viernes-kembo" no hace nada) y en una transaccion. Semilla fija: siempre los mismos datos.
 */
@Component
@Profile("dev")
@Order(2)
@RequiredArgsConstructor
public class KemboHistoryGenerator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KemboHistoryGenerator.class);
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final String EMAIL_DOMAIN = "@correo-kembo.test";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final BloopProperties properties;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void run(ApplicationArguments args) {
        var demo = properties.demoData();
        if (demo == null || !demo.isDataset("kembo")) {
            return;
        }
        List<UUID> tenant = jdbc.queryForList("SELECT id FROM tenants WHERE slug = ?", UUID.class, KemboDemoSeeder.SLUG);
        if (tenant.isEmpty()) {
            return;
        }
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE slug = 'viernes-kembo'", Integer.class);
        if (existing != null && existing > 0) {
            return; // Ya generado.
        }
        log.info("Generando los 4 meses de actividad de Kembo (puede tardar un minuto)...");
        long start = System.currentTimeMillis();
        Generation generation = new Generation(tenant.getFirst(), demo.password());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> generation.run());
        log.info("""

                ================ HISTORICO DE KEMBO GENERADO ({} s) ================
                {}
                Clientes: <nombre>.<apellido><n>{} (contrasena DEMO_PASSWORD)
                ====================================================================""",
                (System.currentTimeMillis() - start) / 1000, generation.summary(), EMAIL_DOMAIN);
    }

    // =====================================================================================
    // Sentencias SQL (en el orden de las claves foraneas: padres antes que hijos)
    // =====================================================================================

    private static final String INS_USER = """
            INSERT INTO users (id, tenant_id, email, password_hash, first_name, last_name, phone, status, last_login_at,
                               privacy_accepted_at, marketing_consent, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?)""";
    private static final String INS_USER_ROLE = "INSERT INTO user_roles (user_id, role_code) VALUES (?, ?)";
    private static final String INS_USER_VENUE = "INSERT INTO user_venues (user_id, venue_id) VALUES (?, ?)";
    private static final String INS_PROMOTER = """
            INSERT INTO promoters (id, tenant_id, user_id, referral_code, commission_type, commission_value, active,
                                   created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?)""";
    private static final String INS_EVENT = """
            INSERT INTO events (id, tenant_id, venue_id, name, slug, description, image_url, min_age, status,
                                published_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NULL, ?, 'PUBLISHED', ?, ?, ?)""";
    private static final String INS_PROMOTER_EVENT =
            "INSERT INTO promoter_events (promoter_id, event_id, target_tickets) VALUES (?, ?, ?) ON CONFLICT DO NOTHING";
    private static final String INS_PROMO_CODE = """
            INSERT INTO promo_codes (id, tenant_id, code, description, discount_type, discount_value, event_id,
                                     promoter_id, max_uses, times_used, valid_from, valid_until, active, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, TRUE, ?, ?)""";
    private static final String INS_SESSION = """
            INSERT INTO event_sessions (id, tenant_id, event_id, starts_at, ends_at, doors_open_at, capacity, status,
                                        title, description, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_TICKET_TYPE = """
            INSERT INTO ticket_types (id, tenant_id, session_id, name, description, category, price, currency,
                                      quantity_total, quantity_sold, max_per_order, sales_start_at, sales_end_at, active,
                                      sort_order, created_at, updated_at)
            VALUES (?, ?, ?, ?, NULL, ?, ?, 'EUR', ?, ?, ?, ?, ?, TRUE, ?, ?, ?)""";
    private static final String INS_ORDER = """
            INSERT INTO ticket_orders (id, tenant_id, reference, customer_user_id, session_id, status, total_amount,
                                       currency, buyer_email, buyer_name, buyer_phone, idempotency_key, expires_at, paid_at,
                                       cancelled_at, created_at, updated_at, marketing_consent, subtotal_amount,
                                       discount_amount, promo_code_id, promo_code, promoter_id, commission_amount,
                                       refunded_at, refund_reason)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'EUR', ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_ORDER_ITEM = """
            INSERT INTO ticket_order_items (id, order_id, ticket_type_id, ticket_type_name, quantity, unit_price, line_total)
            VALUES (?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_PAYMENT = """
            INSERT INTO payments (id, tenant_id, order_id, provider, provider_payment_id, status, amount, currency,
                                  failure_reason, created_at, updated_at, provider_reference, refunded_amount,
                                  provider_refund_id, refunded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'EUR', ?, ?, ?, NULL, ?, ?, ?)""";
    private static final String INS_TICKET = """
            INSERT INTO tickets (id, tenant_id, order_id, order_item_id, ticket_type_id, session_id, owner_user_id, code,
                                 holder_name, status, used_at, used_by_user_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?)""";
    private static final String INS_ACCESS = """
            INSERT INTO access_validations (id, tenant_id, session_id, ticket_id, validated_by, result, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_CASH_SESSION = """
            INSERT INTO cash_sessions (id, tenant_id, terminal_id, status, opened_by, opened_at, opening_float, closed_by,
                                       closed_at, expected_cash, counted_cash, difference, closing_notes, created_at, updated_at)
            VALUES (?, ?, ?, 'CLOSED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_CASH_MOVEMENT = """
            INSERT INTO cash_movements (id, tenant_id, cash_session_id, type, amount, reason, user_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_POS_SALE = """
            INSERT INTO pos_sales (id, tenant_id, terminal_id, cash_session_id, reference, status, subtotal_amount,
                                   discount_amount, total_amount, payment_method, cash_received, change_given,
                                   discount_reason, discount_authorized_by, sold_by, idempotency_key, voided_by,
                                   voided_at, void_reason, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_POS_ITEM = """
            INSERT INTO pos_sale_items (id, sale_id, variant_id, product_name, variant_name, quantity, unit_price, line_total)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_STOCK_MOVEMENT = """
            INSERT INTO stock_movements (id, tenant_id, warehouse_id, variant_id, type, quantity, reason, sale_id,
                                         user_id, supplier_id, document_ref, unit_cost, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_CONTAINER_RETURN = """
            INSERT INTO container_returns (id, tenant_id, supplier_id, variant_id, quantity, document_ref, user_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_RESERVATION = """
            INSERT INTO reservations (id, tenant_id, venue_id, table_id, session_id, customer_id, customer_user_id,
                                      reference, contact_name, contact_email, contact_phone, guests, starts_at, ends_at,
                                      status, min_spend, deposit_amount, deposit_paid_at, notes, source, cancelled_at,
                                      prepaid_method, account_closed_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_RES_PACKAGE = """
            INSERT INTO reservation_package_items (id, tenant_id, reservation_id, variant_id, product_name, quantity,
                                                   created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_VIP_ORDER = """
            INSERT INTO vip_orders (id, tenant_id, venue_id, reservation_id, source, status, idempotency_key, notes,
                                    acknowledged_at, preparing_at, prepared_by_id, prepared_by_name, ready_at,
                                    on_the_way_at, delivered_by_id, delivered_by_name, delivered_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'DELIVERED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_VIP_ORDER_ITEM = """
            INSERT INTO vip_order_items (id, tenant_id, order_id, variant_id, product_name, quantity, unit_price, included,
                                         line_total, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_VIP_PAYMENT = """
            INSERT INTO vip_account_payments (id, tenant_id, reservation_id, amount, method, idempotency_key,
                                              created_by_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_AUDIT = """
            INSERT INTO audit_logs (id, tenant_id, user_id, user_email, action, entity_type, entity_id, details,
                                    ip_address, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, '127.0.0.1', ?)""";

    private static final List<String> STATEMENT_ORDER = List.of(INS_USER, INS_USER_ROLE, INS_USER_VENUE,
            INS_PROMOTER, INS_EVENT, INS_PROMOTER_EVENT, INS_PROMO_CODE, INS_SESSION, INS_TICKET_TYPE, INS_ORDER,
            INS_ORDER_ITEM, INS_PAYMENT, INS_TICKET, INS_ACCESS, INS_CASH_SESSION, INS_CASH_MOVEMENT, INS_POS_SALE,
            INS_POS_ITEM, INS_STOCK_MOVEMENT, INS_CONTAINER_RETURN, INS_RESERVATION, INS_RES_PACKAGE, INS_VIP_ORDER,
            INS_VIP_ORDER_ITEM, INS_VIP_PAYMENT, INS_AUDIT);

    // =====================================================================================
    // Modelo en memoria
    // =====================================================================================

    private record Customer(UUID id, String email, String name, String phone, Instant createdAt, double weight,
                            boolean marketing) {
    }

    private record Staff(UUID id, String email, String name) {
    }

    private record Promoter(UUID id, String code, BigDecimal percentage) {
    }

    private record PromoCode(UUID id, String code, String type, BigDecimal value, Instant validFrom, Instant validUntil,
                             int maxUses) {
    }

    /** Variante del catalogo: unidad de inventario, coste y si su envase vuelve al proveedor. */
    private record Variant(UUID id, String product, String variant, BigDecimal price, boolean trackStock, int unitSize,
                           BigDecimal cost, boolean returnable) {

        String label() {
            return product + " · " + variant;
        }
    }

    private record Terminal(UUID id, String name, UUID warehouseId, Staff cashier) {
    }

    private record PackageItem(Variant variant, int quantity) {
    }

    private record VipTable(UUID id, String zone, String name, int capacity, BigDecimal minSpend, BigDecimal deposit,
                            List<PackageItem> packageItems) {

        boolean premium() {
            return name.startsWith("Premium");
        }
    }

    /** Linea de reposicion: que producto, a que proveedor se pide, nivel del almacen y de cada barra (en unidades). */
    private record Supply(Variant variant, UUID supplierId, String supplierCode, int storePar, int barPar, int caseSize) {
    }

    /** Tipo de entrada de una sesion generada. */
    private static final class TypeSpec {
        final UUID id = UUID.randomUUID();
        final String name;
        final String category;
        final BigDecimal price;
        final int total;
        final int maxPerOrder;
        final Instant salesEnd;
        int sold;

        TypeSpec(String name, String category, String price, int total, int maxPerOrder, Instant salesEnd) {
            this.name = name;
            this.category = category;
            this.price = new BigDecimal(price);
            this.total = total;
            this.maxPerOrder = maxPerOrder;
            this.salesEnd = salesEnd;
        }

        int available() {
            return total - sold;
        }
    }

    /** Una fiesta del calendario. */
    private record Night(UUID eventId, LocalDate day, LocalTime start, LocalTime end, String title, String description,
                         double demand, boolean tardeo, boolean special, boolean cancelled) {
    }

    // =====================================================================================
    // Generacion
    // =====================================================================================

    private final class Generation {

        private final Random random = new Random(20_261_118L);
        private final BatchInserter batch = new BatchInserter(jdbc, STATEMENT_ORDER);
        private final UUID tenantId;
        private final LocalDate today = LocalDate.now(MADRID);
        private final LocalDate firstDay = today.minusMonths(3);
        private final LocalDate lastDay = today.plusMonths(1);
        private final Instant now = Instant.now();
        private final String passwordHash;

        private UUID venueId;
        private Staff admin;
        private Staff manager;
        private Staff door;
        private Staff waiter;
        private final List<Customer> customers = new ArrayList<>();
        private double totalWeight;
        private final Set<String> orderReferences = new HashSet<>();
        private final Set<String> saleReferences = new HashSet<>();
        private final Set<String> reservationReferences = new HashSet<>();
        private final Map<UUID, Integer> promoUses = new HashMap<>();
        private final List<Promoter> promoters = new ArrayList<>();
        private final List<PromoCode> promoCodes = new ArrayList<>();

        private final Map<String, Variant> catalog = new LinkedHashMap<>();
        private final Map<UUID, Map<UUID, Integer>> recipes = new HashMap<>();
        private final List<VipTable> vipTables = new ArrayList<>();
        private final List<Variant> vipExtras = new ArrayList<>();
        private UUID store;
        private Terminal barL;
        private Terminal barU;
        private Terminal boxOffice;
        private final List<Supply> supplies = new ArrayList<>();
        /** Existencias teoricas por almacen y variante (en la unidad de inventario: ml o unidades). */
        private final Map<UUID, Map<UUID, Integer>> stock = new HashMap<>();
        /** Envases vacios pendientes de devolver (unidades o ml en botellas) por variante. */
        private final Map<UUID, Integer> empties = new HashMap<>();
        private final Map<String, Integer> documentNumbers = new HashMap<>();
        private final List<Object[]> usedTickets = new ArrayList<>();

        private int pastSessions;
        private int futureSessions;
        private int paidOrders;
        private int ticketsIssued;
        private int posSales;
        private int reservations;
        private BigDecimal ticketRevenue = BigDecimal.ZERO;
        private BigDecimal posRevenue = BigDecimal.ZERO;
        private BigDecimal vipRevenue = BigDecimal.ZERO;

        Generation(UUID tenantId, String password) {
            this.tenantId = tenantId;
            this.passwordHash = passwordEncoder.encode(password);
        }

        String summary() {
            return """
                    Periodo: %s a %s | Fiestas pasadas: %d | Proximas: %d
                    Pedidos pagados: %d | Entradas: %d | Ventas TPV: %d | Reservados: %d
                    Entradas: %s EUR | TPV: %s EUR | VIP: %s EUR | Filas: %d""".formatted(firstDay, lastDay,
                    pastSessions, futureSessions, paidOrders, ticketsIssued, posSales, reservations, ticketRevenue,
                    posRevenue, vipRevenue, batch.totalRows());
        }

        void run() {
            loadSeedData();
            createStaffAndPromoters();
            createCustomers();
            createPromoCodes();
            batch.flush();

            UUID jueves = createEvent("Jueves Universitario", "jueves-universitario-kembo",
                    "Todos los jueves: la noche universitaria de Zaragoza. Reggaeton y comercial en la Sala L, "
                            + "electronica en la Sala U. Entrada con copa.");
            UUID viernes = createEvent("Viernes Kembo", "viernes-kembo",
                    "Los viernes de Kembo: dos salas, dos ambientes y los residentes de la casa.");
            UUID sabado = createEvent("Sabado Kembo", "sabado-kembo",
                    "La noche grande de la semana con DJ invitado en la Sala U y la mejor comercial en la Sala L.");
            UUID tardeo = createEvent("Tardeo Kembo", "tardeo-kembo",
                    "Tardeo de sabado de 18:00 a 22:30: remember, pop y los temas de siempre para empezar pronto.");
            UUID pilar = createEvent("Fiestas del Pilar en Kembo", "fiestas-del-pilar-kembo-" + today.getYear(),
                    "La noche mas grande de las Fiestas del Pilar: cachirulo obligatorio y sorpresas toda la noche.");
            UUID halloween = createEvent("Halloween Kembo", "halloween-kembo-" + today.getYear(),
                    "Disfraces, decorado de terror en las dos salas y premio al mejor disfraz.");
            UUID puente = createEvent("Puente de Diciembre", "puente-de-diciembre-kembo-" + today.getYear(),
                    "Sabado del puente de la Constitucion con DJ invitado y apertura de las dos salas.");
            for (Promoter p : promoters) {
                for (UUID e : List.of(jueves, viernes, sabado, pilar, halloween, puente)) {
                    batch.add(INS_PROMOTER_EVENT, p.id(), e, e.equals(jueves) ? 150 : 100);
                }
            }
            promoCodes.add(newPromoCode(uniqueCode("PILAR15"), "15 % en las Fiestas del Pilar", "PERCENTAGE", "15.00",
                    pilar, at(LocalDate.of(today.getYear(), 9, 20), LocalTime.NOON),
                    at(LocalDate.of(today.getYear(), 10, 11), LocalTime.of(2, 0)), 200));
            batch.flush();

            List<Night> nights = calendar(jueves, viernes, sabado, tardeo, pilar, halloween, puente);
            initialStock();
            LocalDate lastDelivery = null;
            for (Night night : nights) {
                if (!night.day().isBefore(today)) {
                    futureNight(night);
                    continue;
                }
                // Albaranes de los proveedores el jueves por la manana (o antes de la primera noche).
                LocalDate deliveryDay = night.day().with(TemporalAdjusters.previousOrSame(DayOfWeek.THURSDAY));
                if (!deliveryDay.equals(lastDelivery)) {
                    weeklyDelivery(at(deliveryDay, LocalTime.of(11, 0)));
                    lastDelivery = deliveryDay;
                }
                pastNight(night);
            }
            batch.flush();
            finish();
        }

        // ---------------------------------------------------------------- Datos creados por KemboDemoSeeder

        private void loadSeedData() {
            venueId = jdbc.queryForObject("SELECT id FROM venues WHERE tenant_id = ? AND slug = 'kembo-zaragoza'",
                    UUID.class, tenantId);
            admin = staff("admin@kembo.test");
            manager = staff("encargado@kembo.test");
            door = staff("puerta@kembo.test");
            waiter = staff("servicio@kembo.test");
            orderReferences.addAll(jdbc.queryForList("SELECT reference FROM ticket_orders", String.class));
            saleReferences.addAll(jdbc.queryForList("SELECT reference FROM pos_sales", String.class));
            reservationReferences.addAll(jdbc.queryForList("SELECT reference FROM reservations", String.class));

            Map<UUID, Variant> byId = new HashMap<>();
            jdbc.query("""
                    SELECT v.id, p.name AS product, v.name AS variant, v.price, p.track_stock, v.stock_unit,
                           v.unit_size_ml, v.cost, v.returnable, c.kind
                    FROM product_variants v JOIN products p ON p.id = v.product_id
                    JOIN product_categories c ON c.id = p.category_id
                    WHERE v.tenant_id = ? AND v.active AND p.active""", rs -> {
                boolean ml = "ML".equals(rs.getString("stock_unit"));
                Variant v = new Variant(rs.getObject("id", UUID.class), rs.getString("product"), rs.getString("variant"),
                        rs.getBigDecimal("price"), rs.getBoolean("track_stock"), ml ? rs.getInt("unit_size_ml") : 1,
                        rs.getBigDecimal("cost"), rs.getBoolean("returnable"));
                if (catalog.put(v.product() + "|" + v.variant(), v) != null) {
                    throw new IllegalStateException("Producto repetido en la carta de Kembo: " + v.label());
                }
                byId.put(v.id(), v);
                String kind = rs.getString("kind");
                if (kind.equals("VIP_BOTTLE") || kind.equals("SERVICE")) {
                    vipExtras.add(v);
                }
            }, tenantId);
            jdbc.query("SELECT variant_id, component_variant_id, quantity FROM variant_recipe_items WHERE tenant_id = ?",
                    rs -> {
                        recipes.computeIfAbsent(rs.getObject("variant_id", UUID.class), k -> new LinkedHashMap<>())
                                .put(rs.getObject("component_variant_id", UUID.class), rs.getInt("quantity"));
                    }, tenantId);

            Map<UUID, List<PackageItem>> packages = new HashMap<>();
            jdbc.query("SELECT table_id, variant_id, quantity FROM vip_table_package_items WHERE tenant_id = ?", rs -> {
                packages.computeIfAbsent(rs.getObject("table_id", UUID.class), k -> new ArrayList<>())
                        .add(new PackageItem(byId.get(rs.getObject("variant_id", UUID.class)), rs.getInt("quantity")));
            }, tenantId);
            jdbc.query("""
                    SELECT t.id, z.name AS zone, t.name, t.capacity, t.min_spend, t.deposit_amount
                    FROM vip_tables t JOIN vip_zones z ON z.id = t.zone_id
                    WHERE t.tenant_id = ? AND t.active AND z.active AND t.pos_x IS NOT NULL
                    ORDER BY z.sort_order, t.name""", rs -> {
                UUID id = rs.getObject("id", UUID.class);
                vipTables.add(new VipTable(id, rs.getString("zone"), rs.getString("name"), rs.getInt("capacity"),
                        rs.getBigDecimal("min_spend"), rs.getBigDecimal("deposit_amount"),
                        packages.getOrDefault(id, List.of())));
            }, tenantId);

            store = jdbc.queryForObject("SELECT id FROM warehouses WHERE tenant_id = ? AND name = 'Almacen'", UUID.class,
                    tenantId);
            Map<String, UUID> suppliers = new HashMap<>();
            jdbc.query("SELECT id, name FROM suppliers WHERE tenant_id = ?",
                    rs -> { suppliers.put(rs.getString("name"), rs.getObject("id", UUID.class)); }, tenantId);
            UUID licores = suppliers.get("Distribuciones Ebro Licores");
            UUID bebidas = suppliers.get("Bebidas del Valle S.L.");
            UUID aguas = suppliers.get("Hielo y Aguas Zaragoza");

            // Niveles de reposicion: botellas en ml (cajas de 6), resto en unidades (cajas de 24).
            int[][] bottles = {{84, 14}, {96, 16}, {60, 10}, {84, 14}, {24, 4}}; // almacen, barra (botellas)
            for (int i = 0; i < KemboDemoSeeder.ALCOHOLS.size(); i++) {
                Variant v = catalog.get(KemboDemoSeeder.ALCOHOLS.get(i) + "|Botella 70 cl");
                supplies.add(new Supply(v, licores, "EL", bottles[i][0] * 700, bottles[i][1] * 700, 6 * 700));
            }
            supplies.add(new Supply(catalog.get("Cerveza|Botellin"), bebidas, "BV", 1680, 300, 24));
            supplies.add(new Supply(catalog.get("Cerveza sin alcohol|Botellin"), bebidas, "BV", 192, 48, 24));
            supplies.add(new Supply(catalog.get("Refresco|Botellin"), bebidas, "BV", 576, 120, 24));
            supplies.add(new Supply(catalog.get("Red Bull|Lata"), bebidas, "BV", 432, 96, 24));
            supplies.add(new Supply(catalog.get("Agua|Botella 50 cl"), aguas, "HA", 864, 150, 24));

            Map<String, UUID[]> terminals = new HashMap<>();
            jdbc.query("SELECT id, name, warehouse_id FROM pos_terminals WHERE tenant_id = ?", rs -> {
                terminals.put(rs.getString("name"), new UUID[]{rs.getObject("id", UUID.class),
                        rs.getObject("warehouse_id", UUID.class)});
            }, tenantId);
            Staff caja = staff("caja@kembo.test");
            Instant joined = at(firstDay.minusDays(20), LocalTime.of(11, 0));
            Staff lucia = newStaff("lucia.marin@kembo.test", "Lucia", "Marin", "CASHIER", joined);
            Staff dario = newStaff("dario.pina@kembo.test", "Dario", "Pina", "CASHIER", joined);
            barL = new Terminal(terminals.get("Barra Sala L")[0], "Barra Sala L", terminals.get("Barra Sala L")[1], caja);
            barU = new Terminal(terminals.get("Barra Sala U")[0], "Barra Sala U", terminals.get("Barra Sala U")[1], lucia);
            boxOffice = new Terminal(terminals.get("Taquilla")[0], "Taquilla", terminals.get("Taquilla")[1], dario);
            for (UUID w : List.of(store, barL.warehouseId(), barU.warehouseId())) {
                stock.put(w, new HashMap<>());
            }
        }

        private Staff staff(String email) {
            return jdbc.queryForObject("SELECT id, first_name, last_name FROM users WHERE email = ?",
                    (rs, n) -> new Staff(rs.getObject("id", UUID.class), email,
                            rs.getString("first_name") + " " + rs.getString("last_name")), email);
        }

        // ---------------------------------------------------------------- Personas

        private Staff newStaff(String email, String first, String last, String role, Instant createdAt) {
            UUID id = UUID.randomUUID();
            batch.add(INS_USER, id, tenantId, email, passwordHash, first, last, null, ts(now.minus(1, ChronoUnit.DAYS)),
                    null, false, ts(createdAt), ts(createdAt));
            batch.add(INS_USER_ROLE, id, role);
            batch.add(INS_USER_VENUE, id, venueId);
            return new Staff(id, email, first + " " + last);
        }

        private void createStaffAndPromoters() {
            Instant joined = at(firstDay.minusDays(20), LocalTime.of(12, 0));
            Staff marcos = staff("rrpp@kembo.test");
            Staff sandra = newStaff("sandra.gil@kembo.test", "Sandra", "Gil", "PROMOTER", joined);
            Staff hector = newStaff("hector.ruiz@kembo.test", "Hector", "Ruiz", "PROMOTER", joined);
            newStaff("raul.cebrian@kembo.test", "Raul", "Cebrian", "DOOR_STAFF", joined);
            promoters.add(newPromoter(marcos, "MARCOS", "10.00", joined));
            promoters.add(newPromoter(sandra, "SANDRA", "8.00", joined));
            promoters.add(newPromoter(hector, "HECTOR", "8.00", joined));
        }

        private Promoter newPromoter(Staff user, String base, String percentage, Instant createdAt) {
            String code = base;
            int n = 2;
            while (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM promoters WHERE referral_code = ?)",
                    Boolean.class, code))) {
                code = base + n++;
            }
            UUID id = UUID.randomUUID();
            batch.add(INS_PROMOTER, id, tenantId, user.id(), code, "PERCENTAGE", new BigDecimal(percentage),
                    ts(createdAt), ts(createdAt));
            return new Promoter(id, code, new BigDecimal(percentage));
        }

        /** Clientes con distinta fidelidad: unos pocos vienen casi todas las semanas y la mayoria, de vez en cuando. */
        private void createCustomers() {
            int count = 1600;
            for (int i = 0; i < count; i++) {
                String first = DemoNames.FIRST_NAMES[random.nextInt(DemoNames.FIRST_NAMES.length)];
                String last = DemoNames.LAST_NAMES[random.nextInt(DemoNames.LAST_NAMES.length)];
                String last2 = DemoNames.LAST_NAMES[random.nextInt(DemoNames.LAST_NAMES.length)];
                String email = (plain(first) + "." + plain(last) + (i + 1) + EMAIL_DOMAIN).toLowerCase(Locale.ROOT);
                Instant created = random.nextDouble() < 0.35
                        ? at(firstDay.minusDays(5 + random.nextInt(60)), LocalTime.of(10 + random.nextInt(12), random.nextInt(60)))
                        : at(firstDay.plusDays(random.nextInt((int) ChronoUnit.DAYS.between(firstDay, today))),
                        LocalTime.of(9 + random.nextInt(14), random.nextInt(60)));
                boolean marketing = random.nextDouble() < 0.45;
                double weight = 1.0 / Math.pow(i + 1, 0.5);
                String phone = random.nextDouble() < 0.8 ? "+34 6" + (10_000_000 + random.nextInt(89_999_999)) : null;
                Customer c = new Customer(UUID.randomUUID(), email, first + " " + last + " " + last2, phone, created,
                        weight, marketing);
                customers.add(c);
                totalWeight += weight;
                batch.add(INS_USER, c.id(), null, email, passwordHash, first, last + " " + last2, phone, null,
                        ts(created), marketing, ts(created), ts(created));
                batch.add(INS_USER_ROLE, c.id(), "CUSTOMER");
            }
        }

        private Customer pickCustomer(Instant time) {
            for (int attempt = 0; attempt < 30; attempt++) {
                double r = random.nextDouble() * totalWeight;
                for (Customer c : customers) {
                    r -= c.weight();
                    if (r <= 0) {
                        if (c.createdAt().isBefore(time)) {
                            return c;
                        }
                        break;
                    }
                }
            }
            return customers.stream().filter(c -> c.createdAt().isBefore(time)).findFirst().orElse(customers.getFirst());
        }

        private String randomName() {
            return DemoNames.FIRST_NAMES[random.nextInt(DemoNames.FIRST_NAMES.length)] + " "
                    + DemoNames.LAST_NAMES[random.nextInt(DemoNames.LAST_NAMES.length)];
        }

        // ---------------------------------------------------------------- Codigos y eventos

        private void createPromoCodes() {
            promoCodes.add(newPromoCode(uniqueCode("BIENVENIDA"), "Primera compra: 10 % de descuento", "PERCENTAGE",
                    "10.00", null, null, null, 600));
            promoCodes.add(newPromoCode(uniqueCode("UNIZAR"), "2 EUR menos los jueves universitarios", "FIXED_AMOUNT",
                    "2.00", null, at(firstDay.plusDays(14), LocalTime.NOON), null, 800));
        }

        private PromoCode newPromoCode(String code, String description, String type, String value, UUID eventId,
                                       Instant from, Instant until, int maxUses) {
            UUID id = UUID.randomUUID();
            Instant created = from != null ? from : at(firstDay.minusDays(10), LocalTime.NOON);
            batch.add(INS_PROMO_CODE, id, tenantId, code, description, type, new BigDecimal(value), eventId, null,
                    maxUses, ts(from), ts(until), ts(created), ts(created));
            return new PromoCode(id, code, type, new BigDecimal(value), from, until, maxUses);
        }

        private String uniqueCode(String base) {
            String code = base;
            int n = 2;
            while (Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM promo_codes WHERE tenant_id = ? AND code = ?)", Boolean.class, tenantId, code))) {
                code = base + n++;
            }
            return code;
        }

        private UUID createEvent(String name, String slug, String description) {
            UUID id = UUID.randomUUID();
            Instant created = at(firstDay.minusDays(25), LocalTime.NOON);
            String unique = slug;
            int n = 2;
            while (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM events WHERE slug = ?)",
                    Boolean.class, unique))) {
                unique = slug + "-" + n++;
            }
            batch.add(INS_EVENT, id, tenantId, venueId, name, unique, description, 18, ts(created), ts(created), ts(created));
            audit(admin, "EVENT_CREATED", "Event", id, name, created);
            audit(admin, "EVENT_PUBLISHED", "Event", id, name, created.plus(10, ChronoUnit.MINUTES));
            return id;
        }

        /** Jueves, viernes y sabados del periodo; tardeo uno de cada tres sabados; noches especiales. */
        private List<Night> calendar(UUID jueves, UUID viernes, UUID sabado, UUID tardeo, UUID pilar, UUID halloween,
                                     UUID puente) {
            LocalDate pilarDay = LocalDate.of(today.getYear(), 10, 12).with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
            LocalDate halloweenDay = LocalDate.of(today.getYear(), 10, 31);
            LocalDate puenteDay = LocalDate.of(today.getYear(), 12, 6).with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
            LocalDate outage = firstDay.plusWeeks(4).with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY));
            String[] thursday = {"Reggaeton & Perreo", "Noche de Erasmus", "Remember Universitario", "Old School Hits"};
            String[] friday = {"Comercial & Latino", "Kembo Residents", "Noche de los 2000", "House Session", "Viernes Blanco"};
            String[] saturday = {"DJ invitado: Sala U", "Kembo Big Night", "Techno en la Sala U", "Neon Party",
                    "Saturday Classics", "Aniversario Kembo"};
            List<Night> nights = new ArrayList<>();
            int saturdays = 0;
            for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
                int week = (int) ChronoUnit.WEEKS.between(firstDay, day);
                LocalTime start = LocalTime.of(23, 30);
                LocalTime end = LocalTime.of(6, 0);
                switch (day.getDayOfWeek()) {
                    case THURSDAY -> nights.add(new Night(jueves, day, start, end, thursday[week % thursday.length],
                            "Entrada con copa. Lista gratis hasta la 1:00.", 0.62, false, false, day.equals(outage)));
                    case FRIDAY -> nights.add(new Night(viernes, day, start, end, friday[week % friday.length],
                            "Dos salas abiertas y reservados VIP.", 0.74, false, false, false));
                    case SATURDAY -> {
                        if (saturdays++ % 3 == 1) {
                            nights.add(new Night(tardeo, day, LocalTime.of(18, 0), LocalTime.of(22, 30), "Tardeo Remember",
                                    "Remember, pop y rock nacional de 18:00 a 22:30 en la Sala L.", 0.66, true, false, false));
                        }
                        if (day.equals(pilarDay)) {
                            nights.add(new Night(pilar, day, start, end, "Fiestas del Pilar",
                                    "La noche grande del Pilar en las dos salas.", 1.0, false, true, false));
                        } else if (day.equals(halloweenDay)) {
                            nights.add(new Night(halloween, day, start, end, "Halloween",
                                    "Decorado de terror y premio al mejor disfraz.", 1.0, false, true, false));
                        } else if (day.equals(puenteDay)) {
                            nights.add(new Night(puente, day, start, end, "Puente de Diciembre",
                                    "DJ invitado en la Sala U.", 0.95, false, true, false));
                        } else {
                            nights.add(new Night(sabado, day, start, end, saturday[week % saturday.length],
                                    "La noche grande de la semana.", 0.86, false, false, false));
                        }
                    }
                    default -> {
                    }
                }
            }
            return nights;
        }

        private List<TypeSpec> ticketTypes(Night night, Instant startsAt) {
            if (night.tardeo()) {
                return List.of(new TypeSpec("Anticipada con copa", "EARLY_BIRD", "8.00", 150, 10, startsAt.minus(2, ChronoUnit.DAYS)),
                        new TypeSpec("General con copa", "GENERAL", "10.00", 300, 10, null));
            }
            if (night.special()) {
                return List.of(new TypeSpec("Anticipada con copa", "EARLY_BIRD", "15.00", 250, 10, startsAt.minus(7, ChronoUnit.DAYS)),
                        new TypeSpec("General con copa", "GENERAL", "22.00", 590, 10, null),
                        new TypeSpec("VIP con 2 copas", "VIP", "40.00", 60, 4, null));
            }
            return switch (night.day().getDayOfWeek()) {
                case THURSDAY -> List.of(new TypeSpec("Anticipada con copa", "EARLY_BIRD", "8.00", 200, 10, startsAt.minus(1, ChronoUnit.DAYS)),
                        new TypeSpec("General con copa", "GENERAL", "12.00", 500, 10, null),
                        new TypeSpec("Lista gratis hasta la 1:00", "FREE", "0.00", 150, 4, startsAt.plus(90, ChronoUnit.MINUTES)));
                case FRIDAY -> List.of(new TypeSpec("Anticipada con copa", "EARLY_BIRD", "10.00", 250, 10, startsAt.minus(2, ChronoUnit.DAYS)),
                        new TypeSpec("General con copa", "GENERAL", "15.00", 590, 10, null),
                        new TypeSpec("VIP con 2 copas", "VIP", "30.00", 60, 4, null));
                default -> List.of(new TypeSpec("Anticipada con copa", "EARLY_BIRD", "12.00", 250, 10, startsAt.minus(2, ChronoUnit.DAYS)),
                        new TypeSpec("General con copa", "GENERAL", "18.00", 590, 10, null),
                        new TypeSpec("VIP con 2 copas", "VIP", "35.00", 60, 4, null));
            };
        }

        /** Final de agosto flojo (vacaciones), septiembre normal, octubre y noviembre fuertes. */
        private double season(LocalDate day) {
            return switch (day.getMonth()) {
                case AUGUST -> 0.78;
                case SEPTEMBER -> day.getDayOfMonth() < 15 ? 0.88 : 1.0;
                case OCTOBER, NOVEMBER -> 1.08;
                case DECEMBER -> 1.12;
                default -> 1.0;
            };
        }

        // ---------------------------------------------------------------- Una fiesta pasada

        private record Times(Instant startsAt, Instant endsAt, Instant doorsAt) {
        }

        private Times times(Night night) {
            Instant startsAt = at(night.day(), night.start());
            LocalDate endDay = night.end().isBefore(night.start()) ? night.day().plusDays(1) : night.day();
            return new Times(startsAt, at(endDay, night.end()), startsAt.minus(30, ChronoUnit.MINUTES));
        }

        private UUID insertSession(Night night, Times t, List<TypeSpec> types, Instant salesOpen, Instant created) {
            UUID sessionId = UUID.randomUUID();
            batch.add(INS_SESSION, sessionId, tenantId, night.eventId(), ts(t.startsAt()), ts(t.endsAt()), ts(t.doorsAt()),
                    night.tardeo() ? 450 : 900, night.cancelled() ? "CANCELLED" : "SCHEDULED", night.title(),
                    night.description(), ts(created), ts(created));
            for (int i = 0; i < types.size(); i++) {
                TypeSpec type = types.get(i);
                batch.add(INS_TICKET_TYPE, type.id, tenantId, sessionId, type.name, type.category, type.price, type.total,
                        0, type.maxPerOrder, ts(salesOpen), ts(type.salesEnd), i, ts(created), ts(created));
            }
            return sessionId;
        }

        private void pastNight(Night night) {
            Times t = times(night);
            Instant created = t.startsAt().minus(30, ChronoUnit.DAYS);
            Instant salesOpen = t.startsAt().minus(21, ChronoUnit.DAYS);
            List<TypeSpec> types = ticketTypes(night, t.startsAt());
            UUID sessionId = insertSession(night, t, types, salesOpen, created);
            batch.flush();
            pastSessions++;

            double progress = (double) ChronoUnit.DAYS.between(firstDay, night.day()) / ChronoUnit.DAYS.between(firstDay, today);
            double demand = Math.min(1.0, night.demand() * (0.88 + 0.22 * progress) * season(night.day())
                    * (0.9 + random.nextDouble() * 0.2));
            int configured = types.stream().mapToInt(x -> x.total).sum();
            int target = (int) Math.round(configured * demand * 0.82); // el resto compra en la puerta
            Instant cancelAt = night.cancelled() ? t.startsAt().minus(26, ChronoUnit.HOURS) : null;
            Instant salesClose = night.cancelled() ? cancelAt : t.startsAt().plus(90, ChronoUnit.MINUTES);
            List<UUID> admitted = sellTickets(sessionId, types, target, salesOpen, salesClose, t.startsAt(),
                    night.cancelled() ? "Fiesta cancelada por una averia electrica en el local" : null, cancelAt);
            if (night.cancelled()) {
                audit(admin, "SESSION_CHANGED", "EventSession", sessionId, "Fiesta cancelada (averia electrica)", cancelAt);
                batch.flush();
                return;
            }
            int attendees = registerAttendance(sessionId, admitted, t.doorsAt());
            int doorSales = (int) Math.round(configured * demand * 0.14);
            int people = attendees + doorSales;

            // Antes de abrir: el encargado sube a cada barra lo que falta hasta su nivel.
            Instant prep = t.doorsAt().minus(90, ChronoUnit.MINUTES);
            List<Terminal> bars = night.tardeo() ? List.of(barL) : List.of(barL, barU);
            for (Terminal bar : bars) {
                transferToBar(bar.warehouseId(), prep);
            }
            double perPerson = night.tardeo() ? 1.25 : 1.45;
            for (int i = 0; i < bars.size(); i++) {
                double share = bars.size() == 1 ? 1.0 : (i == 0 ? 0.56 : 0.44);
                cashSession(bars.get(i), (int) Math.round(people * perPerson * share), t.doorsAt(), t.endsAt(),
                        night.tardeo() ? TARDEO_MENU : BAR_MENU);
            }
            cashSession(boxOffice, doorSales + (int) Math.round(people * 0.25), t.doorsAt(),
                    t.startsAt().plus(3, ChronoUnit.HOURS), DOOR_MENU, doorSales);
            for (Terminal bar : bars) {
                closingCount(bar.warehouseId(), t.endsAt().plus(35, ChronoUnit.MINUTES));
            }
            vipNight(sessionId, night, t, false);
            audit(manager, "LOGIN_SUCCESS", "User", null, null, t.doorsAt().minus(2, ChronoUnit.HOURS));
            audit(door, "LOGIN_SUCCESS", "User", null, null, t.doorsAt().minus(20, ChronoUnit.MINUTES));
            batch.flush();
        }

        // ---------------------------------------------------------------- Venta de entradas

        private List<UUID> sellTickets(UUID sessionId, List<TypeSpec> types, int target, Instant salesOpen,
                                       Instant salesClose, Instant startsAt, String cancelReason, Instant cancelAt) {
            List<Instant> times = new ArrayList<>();
            List<Integer> sizes = new ArrayList<>();
            int planned = 0;
            while (planned < target) {
                int size = pickOrderSize();
                sizes.add(size);
                planned += size;
                times.add(purchaseTime(salesOpen, salesClose, startsAt));
            }
            times.sort(Instant::compareTo);
            List<UUID> valid = new ArrayList<>();
            for (int i = 0; i < times.size(); i++) {
                Instant time = times.get(i);
                TypeSpec type = chooseType(types, sizes.get(i), time);
                if (type == null) {
                    continue;
                }
                int quantity = Math.min(sizes.get(i), Math.min(type.maxPerOrder, type.available()));
                if (quantity <= 0) {
                    continue;
                }
                Customer customer = pickCustomer(time);
                double roll = random.nextDouble();
                if (roll < 0.03) {
                    expiredOrder(sessionId, customer, type, quantity, time);
                    continue;
                }
                String status = cancelReason != null || roll < 0.04 ? "REFUNDED" : "PAID";
                valid.addAll(paidOrder(sessionId, customer, type, quantity, time, status,
                        cancelReason != null ? cancelReason : "El cliente no puede asistir", cancelAt));
            }
            return valid;
        }

        private int pickOrderSize() {
            double r = random.nextDouble();
            if (r < 0.25) return 1;
            if (r < 0.60) return 2;
            if (r < 0.72) return 3;
            if (r < 0.86) return 4;
            if (r < 0.93) return 5;
            return 6;
        }

        /** La mayoria compra en los dos ultimos dias; algunos, una o dos semanas antes. */
        private Instant purchaseTime(Instant open, Instant close, Instant startsAt) {
            double r = random.nextDouble();
            Instant from = r < 0.5 ? startsAt.minus(2, ChronoUnit.DAYS) : r < 0.8 ? startsAt.minus(7, ChronoUnit.DAYS) : open;
            Instant to = r < 0.5 ? close : r < 0.8 ? startsAt.minus(2, ChronoUnit.DAYS) : startsAt.minus(7, ChronoUnit.DAYS);
            if (from.isBefore(open)) from = open;
            if (to.isAfter(close)) to = close;
            if (to.isAfter(now)) to = now.minus(5, ChronoUnit.MINUTES);
            if (!to.isAfter(from)) return from;
            return from.plusSeconds((long) (random.nextDouble() * Duration.between(from, to).getSeconds()));
        }

        private TypeSpec chooseType(List<TypeSpec> types, int size, Instant time) {
            List<TypeSpec> onSale = types.stream()
                    .filter(t -> t.available() > 0 && (t.salesEnd == null || time.isBefore(t.salesEnd))).toList();
            if (onSale.isEmpty()) {
                return null;
            }
            for (TypeSpec t : onSale) {
                if (t.category.equals("EARLY_BIRD") && random.nextDouble() < 0.8) return t;
                if (t.category.equals("VIP") && size <= t.maxPerOrder && random.nextDouble() < 0.08) return t;
                if (t.category.equals("FREE") && random.nextDouble() < 0.18) return t;
            }
            return onSale.stream().filter(t -> t.category.equals("GENERAL")).findFirst().orElse(onSale.getFirst());
        }

        /** Pedido pagado (o reembolsado despues) con entradas nominales. Devuelve las entradas validas. */
        private List<UUID> paidOrder(UUID sessionId, Customer customer, TypeSpec type, int quantity, Instant time,
                                     String status, String refundReason, Instant cancelAt) {
            UUID orderId = UUID.randomUUID();
            BigDecimal subtotal = type.price.multiply(BigDecimal.valueOf(quantity));
            BigDecimal discount = BigDecimal.ZERO;
            PromoCode code = null;
            if (subtotal.signum() > 0 && random.nextDouble() < 0.08) {
                code = pickPromo(time);
                if (code != null) {
                    discount = code.type().equals("PERCENTAGE")
                            ? subtotal.multiply(code.value()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                            : code.value().min(subtotal);
                    promoUses.merge(code.id(), 1, Integer::sum);
                }
            }
            BigDecimal total = subtotal.subtract(discount);
            Promoter promoter = null;
            BigDecimal commission = BigDecimal.ZERO;
            if (total.signum() > 0 && random.nextDouble() < 0.25) {
                promoter = promoters.get(random.nextInt(promoters.size()));
                commission = total.multiply(promoter.percentage()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }
            boolean refunded = status.equals("REFUNDED");
            Instant paidAt = time.plusSeconds(40 + random.nextInt(200));
            Instant refundedAt = !refunded ? null
                    : cancelAt != null ? cancelAt.plusSeconds(60 + random.nextInt(600)) : paidAt.plus(1, ChronoUnit.DAYS);
            if (refundedAt != null && refundedAt.isAfter(now)) {
                refundedAt = now.minus(1, ChronoUnit.HOURS);
            }
            batch.add(INS_ORDER, orderId, tenantId, orderReference(), customer.id(), sessionId, status, total,
                    customer.email(), customer.name(), customer.phone(), "hist-" + orderId, null, ts(paidAt), ts(time),
                    ts(refundedAt != null ? refundedAt : paidAt), customer.marketing(), subtotal, discount,
                    code == null ? null : code.id(), code == null ? null : code.code(),
                    promoter == null ? null : promoter.id(), refunded ? BigDecimal.ZERO : commission,
                    ts(refundedAt), refunded ? refundReason : null);
            UUID itemId = UUID.randomUUID();
            batch.add(INS_ORDER_ITEM, itemId, orderId, type.id, type.name, quantity, type.price, subtotal);
            if (total.signum() > 0 && random.nextDouble() < 0.04) {
                batch.add(INS_PAYMENT, UUID.randomUUID(), tenantId, orderId, "SIMULATED", "sim_k_" + SecureTokens.randomToken(12),
                        "FAILED", total, "Pago rechazado por el banco", ts(time.plusSeconds(20)), ts(time.plusSeconds(20)),
                        BigDecimal.ZERO, null, null);
            }
            batch.add(INS_PAYMENT, UUID.randomUUID(), tenantId, orderId, total.signum() == 0 ? "FREE" : "SIMULATED",
                    total.signum() == 0 ? "free_" + orderId : "sim_k_" + SecureTokens.randomToken(12),
                    refunded ? "REFUNDED" : "SUCCEEDED", total, null, ts(paidAt), ts(refundedAt != null ? refundedAt : paidAt),
                    refunded ? total : BigDecimal.ZERO,
                    refunded && total.signum() > 0 ? "sim_re_k_" + SecureTokens.randomToken(10) : null, ts(refundedAt));
            List<UUID> valid = new ArrayList<>();
            for (int i = 0; i < quantity; i++) {
                UUID ticketId = UUID.randomUUID();
                // Compra nominal: la primera entrada a nombre del comprador y el resto de sus acompanantes.
                String holder = i == 0 ? customer.name() : randomName();
                batch.add(INS_TICKET, ticketId, tenantId, orderId, itemId, type.id, sessionId, customer.id(),
                        SecureTokens.randomToken(20), holder, refunded ? "CANCELLED" : "VALID", ts(paidAt));
                if (!refunded) {
                    valid.add(ticketId);
                }
            }
            if (!refunded) {
                type.sold += quantity;
                paidOrders++;
                ticketsIssued += quantity;
                ticketRevenue = ticketRevenue.add(total);
            } else {
                audit(cancelAt != null ? admin : manager, "ORDER_REFUNDED", "TicketOrder", orderId,
                        "importe=" + total + " EUR motivo=" + refundReason, refundedAt);
            }
            return valid;
        }

        private void expiredOrder(UUID sessionId, Customer customer, TypeSpec type, int quantity, Instant time) {
            UUID orderId = UUID.randomUUID();
            BigDecimal total = type.price.multiply(BigDecimal.valueOf(quantity));
            Instant expires = time.plus(15, ChronoUnit.MINUTES);
            batch.add(INS_ORDER, orderId, tenantId, orderReference(), customer.id(), sessionId, "EXPIRED", total,
                    customer.email(), customer.name(), customer.phone(), "hist-" + orderId, ts(expires), null, ts(time),
                    ts(expires), customer.marketing(), total, BigDecimal.ZERO, null, null, null, BigDecimal.ZERO, null, null);
            batch.add(INS_ORDER_ITEM, UUID.randomUUID(), orderId, type.id, type.name, quantity, type.price, total);
        }

        private PromoCode pickPromo(Instant time) {
            List<PromoCode> usable = promoCodes.stream()
                    .filter(p -> (p.validFrom() == null || !time.isBefore(p.validFrom()))
                            && (p.validUntil() == null || time.isBefore(p.validUntil()))
                            && promoUses.getOrDefault(p.id(), 0) < p.maxUses())
                    .toList();
            return usable.isEmpty() ? null : usable.get(random.nextInt(usable.size()));
        }

        /** ~88 % de asistencia, escaneos sobre todo en la primera hora y media, algun reintento y QR falsos. */
        private int registerAttendance(UUID sessionId, List<UUID> tickets, Instant doorsAt) {
            int attendees = 0;
            for (UUID ticket : tickets) {
                if (random.nextDouble() > 0.88) {
                    continue;
                }
                double peak = Math.min(1.0, Math.abs(random.nextGaussian()) * 0.35);
                Instant usedAt = doorsAt.plusSeconds((long) (peak * 3.5 * 3600) + random.nextInt(60));
                usedTickets.add(new Object[]{ts(usedAt), door.id(), ticket});
                batch.add(INS_ACCESS, UUID.randomUUID(), tenantId, sessionId, ticket, door.id(), "ACCEPTED", ts(usedAt));
                if (random.nextDouble() < 0.015) {
                    batch.add(INS_ACCESS, UUID.randomUUID(), tenantId, sessionId, ticket, door.id(), "ALREADY_USED",
                            ts(usedAt.plusSeconds(120 + random.nextInt(1800))));
                }
                attendees++;
            }
            for (int i = 0; i < 2 + random.nextInt(5); i++) {
                batch.add(INS_ACCESS, UUID.randomUUID(), tenantId, sessionId, null, door.id(), "INVALID_CODE",
                        ts(doorsAt.plusSeconds(random.nextInt(3 * 3600))));
            }
            return attendees;
        }

        // ---------------------------------------------------------------- TPV

        /** Carta de barra con su peso (lo que mas se pide en una discoteca de Zaragoza). */
        private static final String[][] BAR_MENU = {
                {"Ginebra|Cubata", "12"}, {"Ginebra|Con Red Bull", "2"}, {"Ron|Cubata", "9"}, {"Ron|Con Red Bull", "3"},
                {"Whisky|Cubata", "7"}, {"Whisky|Con Red Bull", "2"}, {"Vodka|Cubata", "6"}, {"Vodka|Con Red Bull", "6"},
                {"Tequila|Cubata", "2"}, {"Tequila|Con Red Bull", "1"}, {"Cerveza|Botellin", "14"},
                {"Cerveza sin alcohol|Botellin", "2"}, {"Refresco|Botellin", "6"}, {"Agua|Botella 50 cl", "8"},
                {"Red Bull|Lata", "4"}, {"Chupito|Chupito", "10"}};
        /** En el tardeo se bebe mas cerveza y menos cubata. */
        private static final String[][] TARDEO_MENU = {
                {"Ginebra|Cubata", "8"}, {"Ron|Cubata", "6"}, {"Whisky|Cubata", "4"}, {"Vodka|Cubata", "3"},
                {"Cerveza|Botellin", "26"}, {"Cerveza sin alcohol|Botellin", "4"}, {"Refresco|Botellin", "10"},
                {"Agua|Botella 50 cl", "8"}, {"Chupito|Chupito", "4"}};
        private static final String[][] DOOR_MENU = {{"Guardarropa|Prenda", "1"}};

        private void cashSession(Terminal terminal, int sales, Instant openAt, Instant closeAt, String[][] menu) {
            cashSession(terminal, sales, openAt, closeAt, menu, 0);
        }

        /**
         * Una caja de una noche: apertura con fondo, ventas con hora punta hacia las 2:30, retirada a la caja
         * fuerte si se acumula efectivo y arqueo al cerrar. En la taquilla, las primeras "doorTickets" ventas son
         * entradas en puerta.
         */
        private void cashSession(Terminal terminal, int sales, Instant openAt, Instant closeAt, String[][] menu,
                                 int doorTickets) {
            UUID sessionId = UUID.randomUUID();
            BigDecimal opening = new BigDecimal("200.00");
            BigDecimal cash = BigDecimal.ZERO;
            Instant opened = openAt.minus(25, ChronoUnit.MINUTES);
            Instant closed = closeAt.plus(15 + random.nextInt(20), ChronoUnit.MINUTES);
            List<Instant> times = new ArrayList<>();
            for (int i = 0; i < sales; i++) {
                double x = Math.min(1.0, Math.max(0.0, 0.45 + random.nextGaussian() * 0.22));
                times.add(openAt.plusSeconds((long) (x * Duration.between(openAt, closeAt).getSeconds())));
            }
            times.sort(Instant::compareTo);
            List<Object[]> saleRows = new ArrayList<>();
            List<Object[]> itemRows = new ArrayList<>();
            int doorLeft = doorTickets;
            for (Instant time : times) {
                UUID saleId = UUID.randomUUID();
                Map<Variant, Integer> chosen = new LinkedHashMap<>();
                if (doorLeft > 0) {
                    chosen.put(catalog.get("Entrada en puerta|General"), 1 + (random.nextDouble() < 0.4 ? 1 : 0));
                    doorLeft -= chosen.values().iterator().next();
                } else {
                    int lines = random.nextDouble() < 0.68 ? 1 : (random.nextDouble() < 0.7 ? 2 : 3);
                    for (int l = 0; l < lines; l++) {
                        Variant v = pickVariant(menu);
                        if (v != null) {
                            chosen.merge(v, 1 + (random.nextDouble() < 0.3 ? random.nextInt(3) : 0), Integer::sum);
                        }
                    }
                }
                if (chosen.isEmpty()) {
                    continue;
                }
                BigDecimal subtotal = BigDecimal.ZERO;
                for (Map.Entry<Variant, Integer> e : chosen.entrySet()) {
                    Variant v = e.getKey();
                    BigDecimal line = v.price().multiply(BigDecimal.valueOf(e.getValue()));
                    subtotal = subtotal.add(line);
                    itemRows.add(new Object[]{UUID.randomUUID(), saleId, v.id(), v.product(), v.variant(), e.getValue(),
                            v.price(), line});
                }
                boolean discounted = random.nextDouble() < 0.012;
                BigDecimal discount = discounted
                        ? subtotal.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                BigDecimal total = subtotal.subtract(discount);
                boolean card = random.nextDouble() < 0.64;
                BigDecimal received = card ? null : cashReceived(total);
                boolean voided = random.nextDouble() < 0.005;
                Instant voidedAt = voided ? time.plusSeconds(60 + random.nextInt(480)) : null;
                saleRows.add(new Object[]{saleId, tenantId, terminal.id(), sessionId, saleReference(),
                        voided ? "VOIDED" : "COMPLETED", subtotal, discount, total, card ? "CARD" : "CASH", received,
                        received == null ? null : received.subtract(total), discounted ? "Invitacion del encargado" : null,
                        discounted ? manager.id() : null, terminal.cashier().id(), "hist-" + saleId,
                        voided ? manager.id() : null, ts(voidedAt), voided ? "Cobro duplicado" : null, ts(time),
                        ts(voided ? voidedAt : time)});
                for (Map.Entry<Variant, Integer> e : chosen.entrySet()) {
                    consume(terminal.warehouseId(), e.getKey(), e.getValue(), saleId, terminal.cashier(), time);
                    if (voided) {
                        consumeVoid(terminal.warehouseId(), e.getKey(), e.getValue(), saleId, voidedAt);
                    }
                }
                if (voided) {
                    audit(manager, "POS_SALE_VOIDED", "PosSale", saleId, "Cobro duplicado", voidedAt);
                } else {
                    posSales++;
                    posRevenue = posRevenue.add(total);
                    if (!card) {
                        cash = cash.add(total);
                    }
                }
            }
            BigDecimal out = BigDecimal.ZERO;
            List<Object[]> movements = new ArrayList<>();
            if (cash.compareTo(new BigDecimal("1200")) > 0) {
                out = new BigDecimal("1000.00");
                movements.add(new Object[]{UUID.randomUUID(), tenantId, sessionId, "CASH_OUT", out,
                        "Retirada a caja fuerte", manager.id(), ts(openAt.plus(3, ChronoUnit.HOURS))});
            }
            BigDecimal expected = opening.add(cash).subtract(out);
            BigDecimal difference = random.nextDouble() < 0.82 ? BigDecimal.ZERO
                    : BigDecimal.valueOf((random.nextInt(1600) - 800) / 100.0).setScale(2, RoundingMode.HALF_UP);
            BigDecimal counted = expected.add(difference).max(BigDecimal.ZERO);
            batch.add(INS_CASH_SESSION, sessionId, tenantId, terminal.id(), terminal.cashier().id(), ts(opened), opening,
                    terminal.cashier().id(), ts(closed), expected, counted, counted.subtract(expected),
                    difference.signum() == 0 ? null : "Descuadre revisado con el encargado", ts(opened), ts(closed));
            movements.forEach(m -> batch.add(INS_CASH_MOVEMENT, m));
            saleRows.forEach(s -> batch.add(INS_POS_SALE, s));
            itemRows.forEach(i -> batch.add(INS_POS_ITEM, i));
            audit(terminal.cashier(), "CASH_SESSION_OPENED", "CashSession", sessionId, terminal.name(), opened);
            audit(terminal.cashier(), "CASH_SESSION_CLOSED", "CashSession", sessionId,
                    "esperado=" + expected + " contado=" + counted, closed);
        }

        private Variant pickVariant(String[][] menu) {
            int total = 0;
            for (String[] m : menu) {
                if (catalog.containsKey(m[0])) total += Integer.parseInt(m[1]);
            }
            int r = total == 0 ? 0 : random.nextInt(total);
            for (String[] m : menu) {
                if (!catalog.containsKey(m[0])) continue;
                r -= Integer.parseInt(m[1]);
                if (r < 0) return catalog.get(m[0]);
            }
            return null;
        }

        private BigDecimal cashReceived(BigDecimal total) {
            if (random.nextDouble() < 0.3) {
                return total;
            }
            for (int note : new int[]{5, 10, 20, 50}) {
                if (BigDecimal.valueOf(note).compareTo(total) >= 0 && random.nextDouble() < 0.7) {
                    return BigDecimal.valueOf(note).setScale(2, RoundingMode.HALF_UP);
                }
            }
            return total.setScale(0, RoundingMode.UP).setScale(2, RoundingMode.HALF_UP);
        }

        // ---------------------------------------------------------------- Inventario

        /** Lo que descuenta una venta: la receta (50 ml de la botella) o la propia variante; nada si no se controla. */
        private Map<UUID, Integer> needs(Variant v, int quantity) {
            Map<UUID, Integer> recipe = recipes.get(v.id());
            Map<UUID, Integer> result = new LinkedHashMap<>();
            if (recipe != null) {
                recipe.forEach((component, ml) -> result.put(component, ml * quantity));
            } else if (v.trackStock()) {
                result.put(v.id(), v.unitSize() * quantity);
            }
            return result;
        }

        /** Venta: descuenta sin bloquear (el stock teorico puede quedar en negativo hasta el recuento). */
        private void consume(UUID warehouse, Variant v, int quantity, UUID saleId, Staff user, Instant when) {
            needs(v, quantity).forEach((variant, amount) -> {
                int left = stock.get(warehouse).merge(variant, -amount, Integer::sum);
                empties.merge(variant, amount, Integer::sum);
                stockMovement(warehouse, variant, "SALE", -amount, null, saleId, user, null, null, null, when);
                if (left < 0 && !warehouse.equals(store)) {
                    refillDuringNight(warehouse, variant, when);
                }
            });
        }

        /** Si una barra se queda sin algo a mitad de noche, se sube una caja mas del almacen. */
        private void refillDuringNight(UUID bar, UUID variant, Instant when) {
            supplies.stream().filter(s -> s.variant().id().equals(variant)).findFirst().ifPresent(s -> {
                int quantity = s.caseSize() - stock.get(bar).get(variant) / s.variant().unitSize() * s.variant().unitSize();
                quantity = ((quantity + s.variant().unitSize() - 1) / s.variant().unitSize()) * s.variant().unitSize();
                if (stock.get(store).getOrDefault(variant, 0) < quantity) {
                    receive(s, s.caseSize() * 2, when.minus(1, ChronoUnit.HOURS), "Pedido urgente");
                }
                Instant at = when.plusSeconds(1);
                stock.get(store).merge(variant, -quantity, Integer::sum);
                stock.get(bar).merge(variant, quantity, Integer::sum);
                stockMovement(store, variant, "TRANSFER_OUT", -quantity, "Reposicion durante la noche", null, manager,
                        null, null, null, at);
                stockMovement(bar, variant, "TRANSFER_IN", quantity, "Reposicion durante la noche", null, manager,
                        null, null, null, at);
            });
        }

        private void consumeVoid(UUID warehouse, Variant v, int quantity, UUID saleId, Instant when) {
            needs(v, quantity).forEach((variant, amount) -> {
                stock.get(warehouse).merge(variant, amount, Integer::sum);
                empties.merge(variant, -amount, Integer::sum);
                stockMovement(warehouse, variant, "SALE_VOID", amount, "Anulacion de venta", saleId, manager, null, null,
                        null, when);
            });
        }

        /** Antes de empezar: entrada inicial de cada proveedor y primeras botellas en las barras. */
        private void initialStock() {
            Instant when = at(firstDay.minusDays(2), LocalTime.of(11, 0));
            for (Supply s : supplies) {
                receive(s, s.storePar(), when, "Inventario inicial de temporada");
            }
        }

        /**
         * Albaran semanal: cada proveedor trae lo que falta hasta el nivel del almacen (en cajas completas) y se
         * lleva los envases vacios de la semana.
         */
        private void weeklyDelivery(Instant when) {
            Set<UUID> suppliersToday = new HashSet<>();
            for (Supply s : supplies) {
                int missing = s.storePar() - stock.get(store).getOrDefault(s.variant().id(), 0);
                if (missing > 0) {
                    int cases = (missing + s.caseSize() - 1) / s.caseSize();
                    receive(s, cases * s.caseSize(), when, null);
                    suppliersToday.add(s.supplierId());
                }
            }
            for (Supply s : supplies) {
                if (!s.variant().returnable() || !suppliersToday.contains(s.supplierId())) {
                    continue;
                }
                int containers = empties.getOrDefault(s.variant().id(), 0) / s.variant().unitSize();
                int lost = s.variant().unitSize() > 1 ? 0 : (int) Math.round(containers * 0.015); // botellines rotos
                int returned = containers - lost;
                if (returned > 0) {
                    batch.add(INS_CONTAINER_RETURN, UUID.randomUUID(), tenantId, s.supplierId(), s.variant().id(),
                            returned, document(s.supplierCode(), when), manager.id(), ts(when.plus(20, ChronoUnit.MINUTES)));
                }
                empties.merge(s.variant().id(), -containers * s.variant().unitSize(), Integer::sum);
            }
        }

        private void receive(Supply s, int quantity, Instant when, String reason) {
            stock.get(store).merge(s.variant().id(), quantity, Integer::sum);
            BigDecimal cost = s.variant().cost() == null ? null : s.variant().cost();
            stockMovement(store, s.variant().id(), "IN", quantity, reason, null, manager, s.supplierId(),
                    document(s.supplierCode(), when), cost, when);
        }

        /** Numero de albaran por proveedor y dia (mismo numero para todas sus lineas de ese dia). */
        private String document(String supplierCode, Instant when) {
            String key = supplierCode + LocalDate.ofInstant(when, MADRID);
            Integer number = documentNumbers.get(key);
            if (number == null) {
                number = 1000 + (int) documentNumbers.keySet().stream().filter(k -> k.startsWith(supplierCode)).count() + 1;
                documentNumbers.put(key, number);
            }
            return "ALB-" + supplierCode + "-" + number;
        }

        /** Sube del almacen a la barra lo que le falta hasta su nivel (botellas enteras / unidades). */
        private void transferToBar(UUID bar, Instant when) {
            for (Supply s : supplies) {
                int unit = s.variant().unitSize();
                int current = stock.get(bar).getOrDefault(s.variant().id(), 0);
                int missing = s.barPar() - current;
                if (missing <= 0) {
                    continue;
                }
                int quantity = ((missing + unit - 1) / unit) * unit;
                int available = (stock.get(store).getOrDefault(s.variant().id(), 0) / unit) * unit;
                if (available < quantity) {
                    // Pedido urgente: el proveedor trae una entrega extra antes de abrir.
                    int cases = (quantity - available + s.caseSize() - 1) / s.caseSize();
                    receive(s, cases * s.caseSize(), when.minus(3, ChronoUnit.HOURS), "Pedido urgente");
                }
                stock.get(store).merge(s.variant().id(), -quantity, Integer::sum);
                stock.get(bar).merge(s.variant().id(), quantity, Integer::sum);
                String reason = "Subida a barra antes de abrir";
                stockMovement(store, s.variant().id(), "TRANSFER_OUT", -quantity, reason, null, manager, null, null, null, when);
                stockMovement(bar, s.variant().id(), "TRANSFER_IN", quantity, reason, null, manager, null, null, null, when);
            }
        }

        /**
         * Recuento de cierre de una barra: botellas empezadas contadas "a ojo" (en cuartos de botella) y una merma
         * pequena (invitaciones, copas cargadas, alguna rotura). El ajuste es la diferencia con lo teorico.
         */
        private void closingCount(UUID bar, Instant when) {
            for (Supply s : supplies) {
                int theoretical = stock.get(bar).getOrDefault(s.variant().id(), 0);
                int counted;
                if (s.variant().unitSize() > 1) {
                    int lost = (int) Math.round(Math.abs(random.nextGaussian()) * 60); // ml perdidos
                    int quarter = s.variant().unitSize() / 4;
                    counted = Math.max(0, Math.round((float) (theoretical - lost) / quarter) * quarter);
                } else {
                    int broken = random.nextDouble() < 0.25 ? 1 + random.nextInt(2) : 0;
                    counted = Math.max(0, theoretical - broken);
                }
                int delta = counted - theoretical;
                stock.get(bar).put(s.variant().id(), counted);
                if (delta != 0) {
                    stockMovement(bar, s.variant().id(), "ADJUSTMENT", delta, "Recuento de cierre", null, manager, null,
                            null, null, when);
                }
            }
            audit(manager, "STOCK_MOVEMENT", "Warehouse", bar, "Recuento de cierre", when);
        }

        private void stockMovement(UUID warehouse, UUID variant, String type, int quantity, String reason, UUID saleId,
                                   Staff user, UUID supplierId, String documentRef, BigDecimal unitCost, Instant when) {
            batch.add(INS_STOCK_MOVEMENT, UUID.randomUUID(), tenantId, warehouse, variant, type, quantity, reason, saleId,
                    user == null ? null : user.id(), supplierId, documentRef, unitCost, ts(when));
        }

        // ---------------------------------------------------------------- Reservados VIP (por fiesta)

        /**
         * Reservados de una fiesta. Pasada: prepago del paquete, pedido del paquete, botellas extra cobradas y
         * cuenta cerrada al irse (mesa liberada). Futura: reservas confirmadas o pendientes de pago.
         */
        private void vipNight(UUID sessionId, Night night, Times t, boolean upcoming) {
            double daysAway = Duration.between(now, t.startsAt()).toHours() / 24.0;
            for (VipTable table : vipTables) {
                if (night.tardeo() && !table.zone().equals("Sala L")) {
                    continue; // el tardeo solo abre la Sala L
                }
                double chance = switch (night.day().getDayOfWeek()) {
                    case THURSDAY -> 0.30;
                    case FRIDAY -> 0.55;
                    default -> 0.75;
                };
                if (night.tardeo()) chance = 0.35;
                if (night.special()) chance = 0.97;
                if (table.premium()) chance = Math.min(1.0, chance + 0.2);
                if (upcoming) chance *= Math.max(0.1, 1.0 - daysAway / 25.0);
                if (random.nextDouble() > chance) {
                    continue;
                }
                Instant created = t.startsAt().minus(1 + random.nextInt(12), ChronoUnit.DAYS);
                if (created.isAfter(now)) {
                    created = now.minus(1 + random.nextInt(48), ChronoUnit.HOURS);
                }
                Customer customer = pickCustomer(created);
                double r = random.nextDouble();
                String status = upcoming ? (r < 0.75 ? "CONFIRMED" : "PENDING")
                        : (r < 0.86 ? "COMPLETED" : r < 0.92 ? "NO_SHOW" : "CANCELLED");
                boolean paid = !status.equals("PENDING") && !status.equals("CANCELLED");
                Instant paidAt = paid ? created.plus(random.nextInt(20), ChronoUnit.MINUTES) : null;
                String method = paid ? (random.nextDouble() < 0.8 ? "CARD" : "CASH") : null;
                Instant cancelledAt = status.equals("CANCELLED") ? t.startsAt().minus(1, ChronoUnit.DAYS) : null;
                Instant closedAt = status.equals("COMPLETED")
                        ? t.endsAt().minus(random.nextInt(90), ChronoUnit.MINUTES) : null;
                int guests = Math.max(2, table.capacity() - random.nextInt(Math.max(1, table.capacity() / 2)));
                String source = r < 0.55 ? "WEB" : r < 0.9 ? "BACKOFFICE" : "WALK_IN";
                if (upcoming && source.equals("WALK_IN")) source = "WEB";
                UUID reservationId = UUID.randomUUID();
                batch.add(INS_RESERVATION, reservationId, tenantId, venueId, table.id(), sessionId, customer.id(),
                        reservationReference(), customer.name(), customer.email(), customer.phone(), guests,
                        ts(t.startsAt()), ts(t.endsAt()), status, table.minSpend(), table.deposit(), ts(paidAt),
                        table.premium() && random.nextDouble() < 0.3 ? "Cumpleanos: traer bengalas con la botella" : null,
                        source, ts(cancelledAt), method, ts(closedAt), ts(created),
                        ts(closedAt != null ? closedAt : cancelledAt != null ? cancelledAt : created));
                for (PackageItem p : table.packageItems()) {
                    batch.add(INS_RES_PACKAGE, UUID.randomUUID(), tenantId, reservationId, p.variant().id(),
                            p.variant().label(), p.quantity(), ts(created), ts(created));
                }
                reservations++;
                if (paid) {
                    vipRevenue = vipRevenue.add(table.deposit());
                }
                if (status.equals("COMPLETED")) {
                    serviceOrders(reservationId, table, t);
                }
            }
        }

        /** Pedidos de la mesa: el paquete al llegar y, a veces, botellas o servicio extra que se cobran al cerrar. */
        private void serviceOrders(UUID reservationId, VipTable table, Times t) {
            Instant arrival = t.startsAt().plus(30 + random.nextInt(90), ChronoUnit.MINUTES);
            List<Object[]> items = new ArrayList<>();
            for (PackageItem p : table.packageItems()) {
                items.add(new Object[]{p.variant(), p.quantity(), true});
            }
            if (!items.isEmpty()) {
                vipOrder(reservationId, items, arrival, "STAFF");
            }
            BigDecimal extras = BigDecimal.ZERO;
            int extraOrders = random.nextDouble() < (table.premium() ? 0.75 : 0.45) ? 1 + random.nextInt(2) : 0;
            Instant time = arrival;
            for (int i = 0; i < extraOrders && !vipExtras.isEmpty(); i++) {
                time = time.plus(40 + random.nextInt(60), ChronoUnit.MINUTES);
                Variant v = vipExtras.get(random.nextInt(vipExtras.size()));
                int qty = v.price().compareTo(new BigDecimal("50")) > 0 ? 1 : 1 + random.nextInt(2);
                vipOrder(reservationId, List.<Object[]>of(new Object[]{v, qty, false}), time,
                        random.nextBoolean() ? "GUEST" : "STAFF");
                extras = extras.add(v.price().multiply(BigDecimal.valueOf(qty)));
            }
            if (extras.signum() > 0) {
                Instant payAt = t.endsAt().minus(100, ChronoUnit.MINUTES);
                batch.add(INS_VIP_PAYMENT, UUID.randomUUID(), tenantId, reservationId, extras,
                        random.nextDouble() < 0.85 ? "CARD" : "CASH", "hist-" + UUID.randomUUID(), waiter.id(), ts(payAt),
                        ts(payAt));
                vipRevenue = vipRevenue.add(extras);
            }
        }

        private void vipOrder(UUID reservationId, List<Object[]> items, Instant created, String source) {
            UUID orderId = UUID.randomUUID();
            Instant ack = created.plusSeconds(30 + random.nextInt(90));
            Instant preparing = ack.plusSeconds(30 + random.nextInt(120));
            Instant ready = preparing.plusSeconds(120 + random.nextInt(240));
            Instant onTheWay = ready.plusSeconds(20 + random.nextInt(60));
            Instant delivered = onTheWay.plusSeconds(30 + random.nextInt(90));
            batch.add(INS_VIP_ORDER, orderId, tenantId, venueId, reservationId, source, "hist-" + orderId, null, ts(ack),
                    ts(preparing), waiter.id(), waiter.name(), ts(ready), ts(onTheWay), waiter.id(), waiter.name(),
                    ts(delivered), ts(created), ts(delivered));
            for (Object[] item : items) {
                Variant v = (Variant) item[0];
                int qty = (Integer) item[1];
                boolean included = (Boolean) item[2];
                BigDecimal unit = included ? BigDecimal.ZERO : v.price();
                batch.add(INS_VIP_ORDER_ITEM, UUID.randomUUID(), tenantId, orderId, v.id(), v.label(), qty, unit, included,
                        unit.multiply(BigDecimal.valueOf(qty)), ts(created), ts(created));
            }
        }

        // ---------------------------------------------------------------- Mes que viene

        /** Fiesta publicada: preventa ya hecha (mas cuanto mas cerca esta) y reservados de las mas proximas. */
        private void futureNight(Night night) {
            Times t = times(night);
            Instant created = t.startsAt().minus(30, ChronoUnit.DAYS);
            if (created.isAfter(now)) {
                created = now.minus(2, ChronoUnit.DAYS);
            }
            Instant salesOpen = created.plus(1, ChronoUnit.HOURS);
            List<TypeSpec> types = ticketTypes(night, t.startsAt());
            UUID sessionId = insertSession(night, t, types, salesOpen, created);
            batch.flush();
            futureSessions++;
            double daysAway = Duration.between(now, t.startsAt()).toHours() / 24.0;
            double fraction = Math.max(0.04, (night.special() ? 0.6 : 0.42) - daysAway * 0.012);
            int target = (int) (types.stream().mapToInt(x -> x.total).sum() * fraction);
            int sold = 0;
            int attempts = 0;
            while (sold < target && attempts++ < target * 3) {
                Instant time = salesOpen.plusSeconds((long) (random.nextDouble()
                        * Duration.between(salesOpen, now.minus(10, ChronoUnit.MINUTES)).getSeconds()));
                TypeSpec type = chooseType(types, 2, time);
                if (type == null) {
                    continue;
                }
                int qty = Math.min(pickOrderSize(), Math.min(type.maxPerOrder, type.available()));
                if (qty <= 0) {
                    continue;
                }
                paidOrder(sessionId, pickCustomer(time), type, qty, time, "PAID", null, null);
                sold += qty;
            }
            if (daysAway < 18) {
                vipNight(sessionId, night, t, true);
            }
            batch.flush();
        }

        // ---------------------------------------------------------------- Cierre

        private void finish() {
            for (int from = 0; from < usedTickets.size(); from += 2000) {
                jdbc.batchUpdate("UPDATE tickets SET status = 'USED', used_at = ?, used_by_user_id = ? WHERE id = ?",
                        usedTickets.subList(from, Math.min(usedTickets.size(), from + 2000)));
            }
            promoUses.forEach((id, uses) -> jdbc.update("UPDATE promo_codes SET times_used = times_used + ? WHERE id = ?",
                    uses, id));
            jdbc.update("""
                    UPDATE ticket_types t SET quantity_sold = sub.sold
                    FROM (SELECT i.ticket_type_id, SUM(i.quantity) AS sold FROM ticket_order_items i
                          JOIN ticket_orders o ON o.id = i.order_id
                          WHERE o.tenant_id = ? AND o.status IN ('PAID', 'PENDING_PAYMENT')
                          GROUP BY i.ticket_type_id) sub
                    WHERE t.id = sub.ticket_type_id AND t.tenant_id = ?""", tenantId, tenantId);
            // Existencias actuales y minimos (avisos de stock bajo y pedido sugerido).
            for (Supply s : supplies) {
                level(store, s.variant().id(), s.storePar() * 3 / 10);
                level(barL.warehouseId(), s.variant().id(), s.barPar() * 4 / 10);
                level(barU.warehouseId(), s.variant().id(), s.barPar() * 4 / 10);
            }
            jdbc.update("""
                    INSERT INTO customers (id, tenant_id, user_id, email, first_name, last_name, phone, notes, tags,
                                           marketing_consent, orders_count, tickets_count, total_spent, first_purchase_at,
                                           last_purchase_at, created_at, updated_at)
                    SELECT gen_random_uuid(), o.tenant_id, u.id, u.email, u.first_name, u.last_name, u.phone, NULL,
                           CASE WHEN COUNT(*) >= 8 AND SUM(o.total_amount) >= 150 THEN 'habitual, vip'
                                WHEN COUNT(*) >= 8 THEN 'habitual' ELSE NULL END,
                           u.marketing_consent, COUNT(*),
                           SUM((SELECT SUM(i.quantity) FROM ticket_order_items i WHERE i.order_id = o.id)),
                           SUM(o.total_amount), MIN(o.paid_at), MAX(o.paid_at), MIN(o.paid_at), MAX(o.paid_at)
                    FROM ticket_orders o JOIN users u ON u.id = o.customer_user_id
                    WHERE o.tenant_id = ? AND o.status = 'PAID' AND u.email LIKE ?
                    GROUP BY o.tenant_id, u.id, u.email, u.first_name, u.last_name, u.phone, u.marketing_consent
                    ON CONFLICT (tenant_id, user_id) WHERE user_id IS NOT NULL DO NOTHING""",
                    tenantId, "%" + EMAIL_DOMAIN);
            jdbc.update("""
                    INSERT INTO customer_consents (id, tenant_id, customer_id, type, granted, source, created_at)
                    SELECT gen_random_uuid(), c.tenant_id, c.id, 'MARKETING', TRUE, 'CHECKOUT', c.first_purchase_at
                    FROM customers c WHERE c.tenant_id = ? AND c.marketing_consent AND c.email LIKE ?""",
                    tenantId, "%" + EMAIL_DOMAIN);
            jdbc.update("""
                    UPDATE reservations r SET customer_id = c.id FROM customers c
                    WHERE r.tenant_id = ? AND r.customer_id IS NULL AND c.tenant_id = r.tenant_id
                      AND c.user_id = r.customer_user_id""", tenantId);
            jdbc.update("""
                    UPDATE users u SET last_login_at = sub.last FROM (
                        SELECT customer_user_id, MAX(created_at) AS last FROM ticket_orders GROUP BY customer_user_id) sub
                    WHERE u.id = sub.customer_user_id AND u.email LIKE ?""", "%" + EMAIL_DOMAIN);
        }

        private void level(UUID warehouse, UUID variant, int minimum) {
            int quantity = stock.get(warehouse).getOrDefault(variant, 0);
            jdbc.update("""
                    INSERT INTO stock_levels (id, tenant_id, warehouse_id, variant_id, quantity, min_quantity, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (warehouse_id, variant_id) DO UPDATE SET quantity = EXCLUDED.quantity,
                        min_quantity = EXCLUDED.min_quantity, updated_at = EXCLUDED.updated_at""",
                    UUID.randomUUID(), tenantId, warehouse, variant, quantity, minimum, Timestamp.from(now));
        }

        // ---------------------------------------------------------------- Utilidades

        private void audit(Staff user, String action, String entityType, UUID entityId, String details, Instant when) {
            batch.add(INS_AUDIT, UUID.randomUUID(), tenantId, user == null ? null : user.id(),
                    user == null ? null : user.email(), action, entityType, entityId == null ? null : entityId.toString(),
                    details, ts(when));
        }

        private String orderReference() {
            return unique("VF-", 8, orderReferences);
        }

        private String saleReference() {
            return unique("TPV-", 8, saleReferences);
        }

        private String reservationReference() {
            return unique("VR-", 6, reservationReferences);
        }

        private String unique(String prefix, int length, Set<String> used) {
            String reference;
            do {
                reference = prefix + SecureTokens.randomReference(length);
            } while (!used.add(reference));
            return reference;
        }

        private Instant at(LocalDate day, LocalTime time) {
            return ZonedDateTime.of(day, time, MADRID).toInstant();
        }

        private Timestamp ts(Instant instant) {
            return instant == null ? null : Timestamp.from(instant);
        }

        private String plain(String text) {
            return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace(" ", "");
        }
    }
}
