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
 * Genera un HISTORICO DE DEMOSTRACION: Bloop funcionando durante los ultimos N dias
 * (DEMO_HISTORY_DAYS, p. ej. 120 = 4 meses) en la empresa demo "Noctambula Group".
 *
 * Que crea (con fechas pasadas reales y respetando todas las reglas de la base de datos):
 * - ~1.100 clientes con compradores habituales y ocasionales.
 * - Viernes y sabados en Sala Aurora, sabados en Terraza Nebula, noches especiales y una sesion
 *   cancelada por tormenta (con sus pedidos reembolsados).
 * - Pedidos pagados, pagos fallidos, pedidos caducados, reembolsos, promotores y codigos promocionales.
 * - Entradas con su asistencia y el historial de escaneos de la puerta.
 * - TPV: cajas por noche y terminal (con arqueo), ventas de barra y taquilla, descuentos, anulaciones
 *   y movimientos de stock con pedidos a proveedor.
 * - Reservas VIP, fichas CRM, consentimientos y auditoria.
 * - Preventa de los eventos de las proximas semanas.
 *
 * Reglas:
 * - Solo en el perfil dev, y solo si DEMO_HISTORY_DAYS > 0.
 * - Se ejecuta UNA vez (si ya existe el evento "viernes-aurora" no hace nada).
 * - Todo en una unica transaccion: o se crea completo o no se crea nada.
 * - Semilla fija: siempre genera los mismos datos (resultados reproducibles).
 * Los datos se escriben con SQL directo (no con los servicios) porque necesitan fechas pasadas.
 */
@Component
@Profile("dev")
@Order(2)
@RequiredArgsConstructor
public class HistoricalDemoDataGenerator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HistoricalDemoDataGenerator.class);
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final String EMAIL_DOMAIN = "@correo-demo.test";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final BloopProperties properties;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void run(ApplicationArguments args) {
        var demo = properties.demoData();
        if (demo == null || !demo.isDataset("noctambula") || demo.historyDays() <= 0) {
            return;
        }
        List<UUID> tenant = jdbc.queryForList("SELECT id FROM tenants WHERE slug = 'noctambula-group'", UUID.class);
        if (tenant.isEmpty()) {
            return;
        }
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE slug = 'viernes-aurora' AND tenant_id = ?", Integer.class, tenant.getFirst());
        if (existing != null && existing > 0) {
            return; // Ya generado.
        }
        log.info("Generando historico de demostracion de {} dias (puede tardar un minuto)...", demo.historyDays());
        long start = System.currentTimeMillis();
        Generation generation = new Generation(tenant.getFirst(), demo.historyDays(), demo.password());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> generation.run());
        log.info("""

                ================ HISTORICO DEMO GENERADO ({} s) ================
                {}
                Clientes demo: <nombre>.<apellido><n>{} (contrasena DEMO_PASSWORD)
                ===============================================================""",
                (System.currentTimeMillis() - start) / 1000, generation.summary(), EMAIL_DOMAIN);
    }

    // =====================================================================================
    // Sentencias SQL (registradas en el orden de las claves foraneas)
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
                                        created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_TICKET_TYPE = """
            INSERT INTO ticket_types (id, tenant_id, session_id, name, description, category, price, currency,
                                      quantity_total, quantity_sold, max_per_order, sales_start_at, sales_end_at, active,
                                      sort_order, created_at, updated_at)
            VALUES (?, ?, ?, ?, NULL, ?, ?, 'EUR', ?, ?, ?, ?, ?, TRUE, ?, ?, ?)""";
    private static final String INS_ORDER = """
            INSERT INTO ticket_orders (id, tenant_id, reference, customer_user_id, session_id, status, total_amount,
                                       currency, buyer_email, buyer_name, idempotency_key, expires_at, paid_at,
                                       cancelled_at, created_at, updated_at, marketing_consent, subtotal_amount,
                                       discount_amount, promo_code_id, promo_code, promoter_id, commission_amount,
                                       refunded_at, refund_reason)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'EUR', ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
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
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_ACCESS = """
            INSERT INTO access_validations (id, tenant_id, session_id, ticket_id, validated_by, result, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_WAREHOUSE = """
            INSERT INTO warehouses (id, tenant_id, venue_id, name, active, created_at, updated_at)
            VALUES (?, ?, ?, ?, TRUE, ?, ?)""";
    private static final String INS_TERMINAL = """
            INSERT INTO pos_terminals (id, tenant_id, venue_id, warehouse_id, name, type, active, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?)""";
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
                                         user_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String INS_RESERVATION = """
            INSERT INTO reservations (id, tenant_id, venue_id, table_id, session_id, customer_id, customer_user_id,
                                      reference, contact_name, contact_email, contact_phone, guests, starts_at, ends_at,
                                      status, min_spend, deposit_amount, deposit_paid_at, notes, source, cancelled_at,
                                      created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?)""";
    private static final String INS_AUDIT = """
            INSERT INTO audit_logs (id, tenant_id, user_id, user_email, action, entity_type, entity_id, details,
                                    ip_address, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, '127.0.0.1', ?)""";

    private static final List<String> STATEMENT_ORDER = List.of(INS_USER, INS_USER_ROLE, INS_USER_VENUE,
            INS_PROMOTER, INS_EVENT, INS_PROMOTER_EVENT, INS_PROMO_CODE, INS_SESSION, INS_TICKET_TYPE, INS_ORDER,
            INS_ORDER_ITEM, INS_PAYMENT, INS_TICKET, INS_ACCESS, INS_WAREHOUSE, INS_TERMINAL, INS_CASH_SESSION,
            INS_CASH_MOVEMENT, INS_POS_SALE, INS_POS_ITEM, INS_STOCK_MOVEMENT, INS_RESERVATION, INS_AUDIT);

    // =====================================================================================
    // Modelo en memoria
    // =====================================================================================

    private record Customer(UUID id, String email, String name, String phone, Instant createdAt, double weight,
                            boolean marketing) {
    }

    private record Staff(UUID id, String email) {
    }

    private record Promoter(UUID id, String code, String commissionType, BigDecimal commissionValue) {
    }

    private record PromoCode(UUID id, String code, String type, BigDecimal value, Instant validFrom, Instant validUntil,
                             int maxUses) {
    }

    /** Tipo de entrada de una sesion generada. */
    private static final class TypeSpec {
        final UUID id;
        final String name;
        final String category;
        final BigDecimal price;
        final int total;
        final int maxPerOrder;
        final Instant salesEnd;
        int sold;

        TypeSpec(String name, String category, String price, int total, int maxPerOrder, Instant salesEnd) {
            this(UUID.randomUUID(), name, category, price, total, maxPerOrder, salesEnd);
        }

        /** Para tipos que ya existen en la base de datos (preventa): se conserva su id. */
        TypeSpec(UUID id, String name, String category, String price, int total, int maxPerOrder, Instant salesEnd) {
            this.id = id;
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

    private record Variant(UUID id, String product, String variant, BigDecimal price, boolean trackStock) {
    }

    private record Terminal(UUID id, String name, String type, UUID warehouseId, Staff cashier) {
    }

    private record VipTable(UUID id, UUID venueId, String name, int capacity, BigDecimal minSpend, BigDecimal deposit) {
    }

    // =====================================================================================
    // Generacion
    // =====================================================================================

    private final class Generation {

        private final Random random = new Random(20_260_717L);
        private final BatchInserter batch = new BatchInserter(jdbc, STATEMENT_ORDER);
        private final UUID tenantId;
        private final LocalDate firstDay;
        private final LocalDate today = LocalDate.now(MADRID);
        private final Instant now = Instant.now();
        private final String passwordHash;

        private final List<Customer> customers = new ArrayList<>();
        private double totalWeight;
        private final Set<String> orderReferences = new HashSet<>();
        private final Set<String> saleReferences = new HashSet<>();
        private final Set<String> reservationReferences = new HashSet<>();
        private final Map<UUID, Integer> promoUses = new HashMap<>();

        private UUID aurora;
        private UUID nebula;
        private Staff admin;
        private Staff manager;
        private Staff auroraDoor;
        private Staff nebulaDoor;
        private final List<Promoter> auroraPromoters = new ArrayList<>();
        private Promoter nebulaPromoter;
        private final List<PromoCode> promoCodes = new ArrayList<>();
        private final Map<String, Variant> catalog = new LinkedHashMap<>();
        private final List<Terminal> auroraBars = new ArrayList<>();
        private Terminal auroraBoxOffice;
        private Terminal nebulaBar;
        private final Map<UUID, Map<UUID, Integer>> stock = new HashMap<>();
        private final List<VipTable> vipTables = new ArrayList<>();

        // Contadores para el resumen final
        private int sessions;
        private int paidOrders;
        private int ticketsIssued;
        private int posSales;
        private BigDecimal ticketRevenue = BigDecimal.ZERO;
        private BigDecimal posRevenue = BigDecimal.ZERO;

        Generation(UUID tenantId, int days, String password) {
            this.tenantId = tenantId;
            this.firstDay = LocalDate.now(MADRID).minusDays(days);
            this.passwordHash = passwordEncoder.encode(password);
        }

        String summary() {
            return """
                    Sesiones pasadas: %d | Pedidos pagados: %d | Entradas: %d | Ventas TPV: %d
                    Facturacion entradas: %s EUR | Facturacion TPV: %s EUR | Filas insertadas: %d""".formatted(
                    sessions, paidOrders, ticketsIssued, posSales, ticketRevenue, posRevenue, batch.totalRows());
        }

        void run() {
            loadExistingData();
            createStaffAndPromoters();
            createCustomers();
            createPromoCodes();
            batch.flush();
            createPosInfrastructure();
            batch.flush();

            // Series de eventos. Las existentes ("techno-sessions", "nebula-sunset") reciben sesiones pasadas.
            UUID viernes = createEvent(aurora, "Viernes Aurora", "viernes-aurora",
                    "Todos los viernes: house y remember con los residentes de Sala Aurora.", 18);
            UUID techno = existingEventOrCreate("techno-sessions", aurora, "Techno Sessions",
                    "Ciclo semanal de techno con artistas invitados.", 18);
            UUID sunset = existingEventOrCreate("nebula-sunset", nebula, "Nebula Sunset",
                    "Sesion de house al atardecer frente al Mediterraneo.", null);
            UUID halloween = createEvent(aurora, "Halloween en Aurora", "halloween-en-aurora-" + today.getYear(),
                    "La noche mas terrorifica del ano: disfraces, decorado y premios.", 18);
            UUID finVerano = createEvent(nebula, "Fiesta Fin de Verano", "fiesta-fin-de-verano-" + today.getYear(),
                    "Despedimos el verano con DJ en directo y fuegos artificiales.", null);
            assignPromoters(viernes, techno, sunset, halloween, finVerano);
            createExtraPromoCodes(halloween);
            batch.flush();

            LocalDate halloweenDay = LocalDate.of(today.getYear(), 10, 31);
            LocalDate finVeranoDay = LocalDate.of(today.getYear(), 9, 12)
                    .with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            LocalDate stormDay = LocalDate.of(today.getYear(), 10, 3)
                    .with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

            for (LocalDate day = firstDay; day.isBefore(today); day = day.plusDays(1)) {
                double progress = (double) ChronoUnit.DAYS.between(firstDay, day) / ChronoUnit.DAYS.between(firstDay, today);
                double growth = 0.80 + 0.35 * progress;
                if (day.equals(halloweenDay)) {
                    pastSession(halloween, aurora, day, LocalTime.of(23, 30), 6, 800, halloweenTypes(day), 0.99, auroraDoor, false);
                } else if (day.getDayOfWeek() == DayOfWeek.FRIDAY) {
                    pastSession(viernes, aurora, day, LocalTime.of(23, 30), 6, 800, viernesTypes(day),
                            0.60 * growth * seasonAurora(day), auroraDoor, false);
                } else if (day.getDayOfWeek() == DayOfWeek.SATURDAY) {
                    pastSession(techno, aurora, day, LocalTime.of(23, 59), 6, 700, technoTypes(day),
                            0.70 * growth * seasonAurora(day), auroraDoor, false);
                    if (day.equals(finVeranoDay)) {
                        pastSession(finVerano, nebula, day, LocalTime.of(19, 0), 6, 400, finVeranoTypes(day), 0.97, nebulaDoor, false);
                    } else {
                        pastSession(sunset, nebula, day, LocalTime.of(18, 0), 7, 400, sunsetTypes(day),
                                0.72 * seasonNebula(day), nebulaDoor, day.equals(stormDay));
                    }
                }
            }
            presales();
            batch.flush();
            finish();
        }

        // ---------------------------------------------------------------- Datos existentes

        private void loadExistingData() {
            aurora = venueId("Sala Aurora");
            nebula = venueId("Terraza Nebula");
            admin = staff("admin@noctambula.test");
            manager = staff("encargado@noctambula.test");
            auroraDoor = staff("puerta@noctambula.test");
            orderReferences.addAll(jdbc.queryForList("SELECT reference FROM ticket_orders", String.class));
            saleReferences.addAll(jdbc.queryForList("SELECT reference FROM pos_sales", String.class));
            reservationReferences.addAll(jdbc.queryForList("SELECT reference FROM reservations", String.class));
            jdbc.query("""
                    SELECT v.id, p.name AS product, v.name AS variant, v.price, p.track_stock
                    FROM product_variants v JOIN products p ON p.id = v.product_id
                    WHERE v.tenant_id = ? AND v.active AND p.active""", rs -> {
                Variant variant = new Variant(rs.getObject("id", UUID.class), rs.getString("product"),
                        rs.getString("variant"), rs.getBigDecimal("price"), rs.getBoolean("track_stock"));
                catalog.put(variant.product() + "|" + variant.variant(), variant);
            }, tenantId);
            jdbc.query("""
                    SELECT t.id, z.venue_id, t.name, t.capacity, t.min_spend, t.deposit_amount
                    FROM vip_tables t JOIN vip_zones z ON z.id = t.zone_id
                    WHERE t.tenant_id = ? AND t.active AND z.active""", rs -> {
                vipTables.add(new VipTable(rs.getObject("id", UUID.class), rs.getObject("venue_id", UUID.class),
                        rs.getString("name"), rs.getInt("capacity"), rs.getBigDecimal("min_spend"),
                        rs.getBigDecimal("deposit_amount")));
            }, tenantId);
        }

        private UUID venueId(String name) {
            return jdbc.queryForObject("SELECT id FROM venues WHERE tenant_id = ? AND name = ?", UUID.class, tenantId, name);
        }

        private Staff staff(String email) {
            return new Staff(jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email), email);
        }

        // ---------------------------------------------------------------- Personas

        private void createStaffAndPromoters() {
            Instant joined = at(firstDay.minusDays(20), LocalTime.of(11, 0));
            nebulaDoor = newStaff("pablo.ortega@noctambula.test", "Pablo", "Ortega", "DOOR_STAFF", nebula, joined);
            Staff dani = staff("rrpp@noctambula.test");
            List<UUID> daniPromoter = jdbc.queryForList("SELECT id FROM promoters WHERE user_id = ?", UUID.class, dani.id());
            if (!daniPromoter.isEmpty()) {
                auroraPromoters.add(new Promoter(daniPromoter.getFirst(), "DANI", "PERCENTAGE", new BigDecimal("10.00")));
            }
            Staff sara = newStaff("sara.romero@noctambula.test", "Sara", "Romero", "PROMOTER", aurora, joined);
            Staff leo = newStaff("leo.santos@noctambula.test", "Leo", "Santos", "PROMOTER", nebula, joined);
            auroraPromoters.add(newPromoter(sara, uniqueReferral("SARA"), "PERCENTAGE", "8.00", joined));
            nebulaPromoter = newPromoter(leo, uniqueReferral("LEO"), "FIXED_PER_TICKET", "1.50", joined);
        }

        private Staff newStaff(String email, String first, String last, String role, UUID venue, Instant createdAt) {
            List<UUID> found = jdbc.queryForList("SELECT id FROM users WHERE LOWER(email) = ?", UUID.class, email);
            if (!found.isEmpty()) {
                return new Staff(found.getFirst(), email);
            }
            UUID id = UUID.randomUUID();
            batch.add(INS_USER, id, tenantId, email, passwordHash, first, last, null, ts(now.minus(1, ChronoUnit.DAYS)),
                    null, false, ts(createdAt), ts(createdAt));
            batch.add(INS_USER_ROLE, id, role);
            batch.add(INS_USER_VENUE, id, venue);
            return new Staff(id, email);
        }

        private Promoter newPromoter(Staff user, String code, String type, String value, Instant createdAt) {
            UUID id = UUID.randomUUID();
            batch.add(INS_PROMOTER, id, tenantId, user.id(), code, type, new BigDecimal(value), ts(createdAt), ts(createdAt));
            return new Promoter(id, code, type, new BigDecimal(value));
        }

        private String uniqueReferral(String base) {
            String code = base;
            int n = 2;
            while (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM promoters WHERE referral_code = ?)",
                    Boolean.class, code))) {
                code = base + n++;
            }
            return code;
        }

        /** Clientes con distinta "fidelidad": unos pocos compran mucho y la mayoria, de vez en cuando. */
        private void createCustomers() {
            int count = 1100;
            for (int i = 0; i < count; i++) {
                String first = DemoNames.FIRST_NAMES[random.nextInt(DemoNames.FIRST_NAMES.length)];
                String last = DemoNames.LAST_NAMES[random.nextInt(DemoNames.LAST_NAMES.length)];
                String last2 = DemoNames.LAST_NAMES[random.nextInt(DemoNames.LAST_NAMES.length)];
                String email = (plain(first) + "." + plain(last) + (i + 1) + EMAIL_DOMAIN).toLowerCase(Locale.ROOT);
                // 55 % ya tenia cuenta antes del periodo; el resto se registra durante el periodo (crecimiento).
                Instant created = random.nextDouble() < 0.55
                        ? at(firstDay.minusDays(30 + random.nextInt(300)), LocalTime.of(10 + random.nextInt(12), random.nextInt(60)))
                        : at(firstDay.plusDays(random.nextInt((int) ChronoUnit.DAYS.between(firstDay, today))),
                        LocalTime.of(9 + random.nextInt(14), random.nextInt(60)));
                boolean marketing = random.nextDouble() < 0.42;
                double weight = 1.0 / Math.pow(i + 1, 0.55);
                String phone = random.nextDouble() < 0.7 ? "+34 6" + (10_000_000 + random.nextInt(89_999_999)) : null;
                Customer customer = new Customer(UUID.randomUUID(), email, first + " " + last + " " + last2, phone,
                        created, weight, marketing);
                customers.add(customer);
                totalWeight += weight;
                batch.add(INS_USER, customer.id(), null, email, passwordHash, first, last + " " + last2, phone, null,
                        ts(created), marketing, ts(created), ts(created));
                batch.add(INS_USER_ROLE, customer.id(), "CUSTOMER");
            }
        }

        /** Cliente para un pedido hecho en "time": preferencia por los habituales, registrado antes de esa fecha. */
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

        // ---------------------------------------------------------------- Codigos promocionales

        private void createPromoCodes() {
            List<UUID> dani10 = jdbc.queryForList("SELECT id FROM promo_codes WHERE tenant_id = ? AND code = 'DANI10'",
                    UUID.class, tenantId);
            if (!dani10.isEmpty()) {
                Integer used = jdbc.queryForObject("SELECT times_used FROM promo_codes WHERE id = ?", Integer.class, dani10.getFirst());
                Integer max = jdbc.queryForObject("SELECT COALESCE(max_uses, 100000) FROM promo_codes WHERE id = ?", Integer.class,
                        dani10.getFirst());
                promoCodes.add(new PromoCode(dani10.getFirst(), "DANI10", "PERCENTAGE", new BigDecimal("10.00"), null, null,
                        max - used));
            }
            Instant created = at(firstDay.minusDays(10), LocalTime.of(12, 0));
            promoCodes.add(newPromoCode("VERANO5", "5 EUR de descuento en verano", "FIXED_AMOUNT", "5.00", null, null, 300,
                    at(firstDay.minusDays(5), LocalTime.MIDNIGHT), at(LocalDate.of(today.getYear(), 9, 1), LocalTime.MIDNIGHT),
                    created));
            promoCodes.add(newPromoCode(uniqueCode("BIENVENIDA"), "Primera compra: 10 % de descuento", "PERCENTAGE", "10.00",
                    null, null, 500, null, null, created));
        }

        private void createExtraPromoCodes(UUID halloween) {
            Instant created = at(LocalDate.of(today.getYear(), 10, 1), LocalTime.of(12, 0));
            promoCodes.add(newPromoCode(uniqueCode("HALLOWEEN20"), "20 % en la noche de Halloween", "PERCENTAGE", "20.00",
                    halloween, null, 150, created, at(LocalDate.of(today.getYear(), 10, 31), LocalTime.of(23, 0)), created));
        }

        private PromoCode newPromoCode(String code, String description, String type, String value, UUID eventId,
                                       UUID promoterId, int maxUses, Instant from, Instant until, Instant created) {
            UUID id = UUID.randomUUID();
            batch.add(INS_PROMO_CODE, id, tenantId, code, description, type, new BigDecimal(value), eventId, promoterId,
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

        // ---------------------------------------------------------------- Eventos

        private UUID createEvent(UUID venue, String name, String slug, String description, Integer minAge) {
            UUID id = UUID.randomUUID();
            Instant created = at(firstDay.minusDays(25), LocalTime.of(12, 0));
            batch.add(INS_EVENT, id, tenantId, venue, name, uniqueSlug(slug), description, minAge, ts(created),
                    ts(created), ts(created));
            audit(admin, "EVENT_CREATED", "Event", id, name, created);
            audit(admin, "EVENT_PUBLISHED", "Event", id, name, created.plus(10, ChronoUnit.MINUTES));
            return id;
        }

        private UUID existingEventOrCreate(String slug, UUID venue, String name, String description, Integer minAge) {
            List<UUID> found = jdbc.queryForList("SELECT id FROM events WHERE tenant_id = ? AND slug = ?", UUID.class,
                    tenantId, slug);
            return found.isEmpty() ? createEvent(venue, name, slug, description, minAge) : found.getFirst();
        }

        private String uniqueSlug(String slug) {
            String candidate = slug;
            int n = 2;
            while (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM events WHERE slug = ?)",
                    Boolean.class, candidate))) {
                candidate = slug + "-" + n++;
            }
            return candidate;
        }

        /** Viernes, techno y Halloween para los promotores de Aurora; terraza y fin de verano para Leo. */
        private void assignPromoters(UUID viernes, UUID techno, UUID sunset, UUID halloween, UUID finVerano) {
            for (Promoter p : auroraPromoters) {
                batch.add(INS_PROMOTER_EVENT, p.id(), viernes, 120);
                batch.add(INS_PROMOTER_EVENT, p.id(), techno, null);
                batch.add(INS_PROMOTER_EVENT, p.id(), halloween, 80);
            }
            batch.add(INS_PROMOTER_EVENT, nebulaPromoter.id(), sunset, 60);
            batch.add(INS_PROMOTER_EVENT, nebulaPromoter.id(), finVerano, null);
        }

        // Tipos de entrada de cada serie (precios y cupos realistas)
        private List<TypeSpec> viernesTypes(LocalDate day) {
            Instant start = at(day, LocalTime.of(23, 30));
            return List.of(new TypeSpec("Anticipada", "EARLY_BIRD", "12.00", 200, 10, start.minus(7, ChronoUnit.DAYS)),
                    new TypeSpec("General", "GENERAL", "18.00", 520, 10, null),
                    new TypeSpec("VIP con consumicion", "VIP", "40.00", 60, 4, null));
        }

        private List<TypeSpec> technoTypes(LocalDate day) {
            Instant start = at(day, LocalTime.of(23, 59));
            return List.of(new TypeSpec("General", "GENERAL", "15.00", 600, 10, null),
                    new TypeSpec("Lista gratuita hasta la 1:00", "FREE", "0.00", 100, 4, start.plus(60, ChronoUnit.MINUTES)));
        }

        private List<TypeSpec> sunsetTypes(LocalDate day) {
            return List.of(new TypeSpec("General", "GENERAL", "10.00", 350, 10, null));
        }

        private List<TypeSpec> halloweenTypes(LocalDate day) {
            Instant start = at(day, LocalTime.of(23, 30));
            return List.of(new TypeSpec("Anticipada", "EARLY_BIRD", "15.00", 250, 10, start.minus(10, ChronoUnit.DAYS)),
                    new TypeSpec("General", "GENERAL", "22.00", 500, 10, null),
                    new TypeSpec("VIP con consumicion", "VIP", "50.00", 50, 4, null));
        }

        private List<TypeSpec> finVeranoTypes(LocalDate day) {
            return List.of(new TypeSpec("General", "GENERAL", "15.00", 380, 10, null));
        }

        /** Sala Aurora baja algo en agosto (vacaciones) y sube en otono. */
        private double seasonAurora(LocalDate day) {
            return switch (day.getMonth()) {
                case AUGUST -> 0.85;
                case OCTOBER, NOVEMBER -> 1.10;
                default -> 1.0;
            };
        }

        /** La terraza vive del verano. */
        private double seasonNebula(LocalDate day) {
            return switch (day.getMonth()) {
                case JULY, AUGUST -> 1.15;
                case SEPTEMBER -> 0.95;
                case OCTOBER -> 0.65;
                default -> 0.45;
            };
        }

        // ---------------------------------------------------------------- Una noche (sesion pasada)

        private void pastSession(UUID eventId, UUID venue, LocalDate day, LocalTime start, int hours, int capacity,
                                 List<TypeSpec> types, double demand, Staff door, boolean cancelled) {
            Instant startsAt = at(day, start);
            Instant endsAt = startsAt.plus(hours, ChronoUnit.HOURS);
            Instant doorsAt = startsAt.minus(30, ChronoUnit.MINUTES);
            UUID sessionId = UUID.randomUUID();
            Instant created = startsAt.minus(28, ChronoUnit.DAYS);
            batch.add(INS_SESSION, sessionId, tenantId, eventId, ts(startsAt), ts(endsAt), ts(doorsAt), capacity,
                    cancelled ? "CANCELLED" : "SCHEDULED", ts(created), ts(created));

            int configured = types.stream().mapToInt(t -> t.total).sum();
            double noise = 0.9 + random.nextDouble() * 0.2;
            int target = (int) Math.min(configured, Math.round(configured * Math.min(1.0, demand * noise)));
            Instant salesOpen = startsAt.minus(21, ChronoUnit.DAYS);
            Instant cancelAt = cancelled ? startsAt.minus(30, ChronoUnit.HOURS) : null;
            Instant salesClose = cancelled ? cancelAt : startsAt.plus(90, ChronoUnit.MINUTES);

            List<SoldTicket> admitted = sellTickets(sessionId, venue, types, target, salesOpen, salesClose, startsAt,
                    cancelled ? "Sesion cancelada por alerta de tormenta" : null, cancelAt);
            for (TypeSpec t : types) {
                batch.add(INS_TICKET_TYPE, t.id, tenantId, sessionId, t.name, t.category, t.price, t.total, t.sold,
                        t.maxPerOrder, ts(salesOpen), ts(t.salesEnd), types.indexOf(t), ts(created), ts(created));
            }
            sessions++;
            if (cancelled) {
                audit(admin, "SESSION_CHANGED", "EventSession", sessionId, "Sesion cancelada (alerta de tormenta)", cancelAt);
                batch.flush();
                return;
            }
            int attendees = registerAttendance(sessionId, admitted, doorsAt, door);
            if (venue.equals(aurora)) {
                barNight(auroraBars, attendees, doorsAt, endsAt, 1.35);
                boxOfficeNight(attendees, doorsAt, startsAt.plus(3, ChronoUnit.HOURS));
            } else if (nebulaBar != null) {
                barNight(List.of(nebulaBar), attendees, doorsAt, endsAt, 1.15);
            }
            reservations(venue, sessionId, startsAt, endsAt, day.equals(LocalDate.of(today.getYear(), 10, 31)), false);
            audit(manager, "LOGIN_SUCCESS", "User", null, null, doorsAt.minus(45, ChronoUnit.MINUTES));
            audit(door, "LOGIN_SUCCESS", "User", null, null, doorsAt.minus(20, ChronoUnit.MINUTES));
            batch.flush();
        }

        private record SoldTicket(UUID id, Instant purchasedAt) {
        }

        /**
         * Vende entradas hasta el objetivo con pedidos de 1 a 6 entradas, repartidos en las 3 semanas
         * previas (la mayoria en los ultimos dias). Devuelve las entradas validas (para la asistencia).
         */
        private List<SoldTicket> sellTickets(UUID sessionId, UUID venue, List<TypeSpec> types, int target,
                                             Instant salesOpen, Instant salesClose, Instant startsAt,
                                             String cancelReason, Instant cancelAt) {
            List<Instant> times = new ArrayList<>();
            int planned = 0;
            List<Integer> sizes = new ArrayList<>();
            while (planned < target) {
                int size = pickOrderSize();
                sizes.add(size);
                planned += size;
                times.add(purchaseTime(salesOpen, salesClose, startsAt));
            }
            times.sort(Instant::compareTo);
            List<SoldTicket> valid = new ArrayList<>();
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
                String status = cancelReason != null ? "REFUNDED" : (roll < 0.042 ? "REFUNDED" : "PAID");
                valid.addAll(paidOrder(sessionId, venue, customer, type, quantity, time, status,
                        cancelReason != null ? cancelReason : refundReason(), cancelAt));
            }
            return valid;
        }

        private int pickOrderSize() {
            double r = random.nextDouble();
            if (r < 0.28) return 1;
            if (r < 0.63) return 2;
            if (r < 0.75) return 3;
            if (r < 0.88) return 4;
            if (r < 0.94) return 5;
            return 6;
        }

        /** 45 % de las compras en los 2 ultimos dias, 30 % en la ultima semana y el resto antes. */
        private Instant purchaseTime(Instant open, Instant close, Instant startsAt) {
            double r = random.nextDouble();
            Instant from;
            Instant to;
            if (r < 0.45) {
                from = startsAt.minus(2, ChronoUnit.DAYS);
                to = close;
            } else if (r < 0.75) {
                from = startsAt.minus(7, ChronoUnit.DAYS);
                to = startsAt.minus(2, ChronoUnit.DAYS);
            } else {
                from = open;
                to = startsAt.minus(7, ChronoUnit.DAYS);
            }
            if (from.isBefore(open)) from = open;
            if (to.isAfter(close)) to = close;
            if (!to.isAfter(from)) return from;
            long seconds = Duration.between(from, to).getSeconds();
            return from.plusSeconds((long) (random.nextDouble() * seconds));
        }

        /** Anticipada mientras quede y este a la venta; VIP para algunos grupos; si no, General. */
        private TypeSpec chooseType(List<TypeSpec> types, int size, Instant time) {
            List<TypeSpec> onSale = types.stream()
                    .filter(t -> t.available() > 0 && (t.salesEnd == null || time.isBefore(t.salesEnd))).toList();
            if (onSale.isEmpty()) {
                return null;
            }
            for (TypeSpec t : onSale) {
                if (t.category.equals("EARLY_BIRD") && random.nextDouble() < 0.85) return t;
                if (t.category.equals("VIP") && size <= t.maxPerOrder && random.nextDouble() < 0.07) return t;
                if (t.category.equals("FREE") && random.nextDouble() < 0.14) return t;
            }
            return onSale.stream().filter(t -> t.category.equals("GENERAL")).findFirst().orElse(onSale.getFirst());
        }

        private String refundReason() {
            String[] reasons = {"El cliente no puede asistir", "Compra duplicada", "Error en la fecha elegida",
                    "Enfermedad justificada"};
            return reasons[random.nextInt(reasons.length)];
        }

        /** Pedido pagado (o reembolsado despues). Devuelve sus entradas validas. */
        private List<SoldTicket> paidOrder(UUID sessionId, UUID venue, Customer customer, TypeSpec type, int quantity,
                                           Instant time, String status, String refundReason, Instant cancelAt) {
            UUID orderId = UUID.randomUUID();
            BigDecimal subtotal = type.price.multiply(BigDecimal.valueOf(quantity));
            BigDecimal discount = BigDecimal.ZERO;
            PromoCode code = null;
            if (subtotal.signum() > 0 && random.nextDouble() < 0.07) {
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
            if (total.signum() > 0 && random.nextDouble() < (venue.equals(nebula) ? 0.15 : 0.22)) {
                promoter = venue.equals(nebula) ? nebulaPromoter
                        : auroraPromoters.get(random.nextInt(auroraPromoters.size()));
                commission = promoter.commissionType().equals("PERCENTAGE")
                        ? total.multiply(promoter.commissionValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                        : promoter.commissionValue().multiply(BigDecimal.valueOf(quantity));
            }
            boolean refunded = status.equals("REFUNDED");
            Instant paidAt = time.plusSeconds(40 + random.nextInt(200));
            Instant refundedAt = refunded
                    ? (cancelAt != null ? cancelAt.plusSeconds(60 + random.nextInt(600)) : paidAt.plus(1 + random.nextInt(3), ChronoUnit.DAYS))
                    : null;
            if (refundedAt != null && cancelAt == null && refundedAt.isAfter(now)) {
                refundedAt = now.minus(1, ChronoUnit.HOURS);
            }
            batch.add(INS_ORDER, orderId, tenantId, orderReference(), customer.id(), sessionId, status, total,
                    customer.email(), customer.name(), "hist-" + orderId, null, ts(paidAt), ts(time),
                    ts(refundedAt != null ? refundedAt : paidAt), customer.marketing(), subtotal, discount,
                    code == null ? null : code.id(), code == null ? null : code.code(),
                    promoter == null ? null : promoter.id(), refunded ? BigDecimal.ZERO : commission,
                    ts(refundedAt), refunded ? refundReason : null);
            UUID itemId = UUID.randomUUID();
            batch.add(INS_ORDER_ITEM, itemId, orderId, type.id, type.name, quantity, type.price, subtotal);

            // Algun cliente falla el primer intento de pago.
            if (total.signum() > 0 && random.nextDouble() < 0.05) {
                batch.add(INS_PAYMENT, UUID.randomUUID(), tenantId, orderId, "SIMULATED", "sim_hist_" + SecureTokens.randomToken(12),
                        "FAILED", total, "Pago rechazado por el banco", ts(time.plusSeconds(20)), ts(time.plusSeconds(20)),
                        BigDecimal.ZERO, null, null);
            }
            batch.add(INS_PAYMENT, UUID.randomUUID(), tenantId, orderId, total.signum() == 0 ? "FREE" : "SIMULATED",
                    total.signum() == 0 ? "free_" + orderId : "sim_hist_" + SecureTokens.randomToken(12),
                    refunded ? "REFUNDED" : "SUCCEEDED", total, null, ts(paidAt), ts(refundedAt != null ? refundedAt : paidAt),
                    refunded ? total : BigDecimal.ZERO,
                    refunded && total.signum() > 0 ? "sim_re_hist_" + SecureTokens.randomToken(10) : null, ts(refundedAt));

            List<SoldTicket> valid = new ArrayList<>();
            for (int i = 0; i < quantity; i++) {
                UUID ticketId = UUID.randomUUID();
                batch.add(INS_TICKET, ticketId, tenantId, orderId, itemId, type.id, sessionId, customer.id(),
                        SecureTokens.randomToken(20), customer.name(), refunded ? "CANCELLED" : "VALID", null, null, ts(paidAt));
                if (!refunded) {
                    valid.add(new SoldTicket(ticketId, paidAt));
                }
            }
            if (!refunded) {
                type.sold += quantity;
                paidOrders++;
                ticketsIssued += quantity;
                ticketRevenue = ticketRevenue.add(total);
            }
            audit(null, "ORDER_PAID", "TicketOrder", orderId, "total=" + total + " EUR", paidAt);
            if (refunded) {
                audit(cancelAt != null ? admin : manager, "ORDER_REFUNDED", "TicketOrder", orderId,
                        "importe=" + total + " EUR motivo=" + refundReason, refundedAt);
            }
            return valid;
        }

        /** Pedido abandonado: nunca se pago y caduco a los 15 minutos (sin entradas ni stock). */
        private void expiredOrder(UUID sessionId, Customer customer, TypeSpec type, int quantity, Instant time) {
            UUID orderId = UUID.randomUUID();
            BigDecimal total = type.price.multiply(BigDecimal.valueOf(quantity));
            Instant expires = time.plus(15, ChronoUnit.MINUTES);
            batch.add(INS_ORDER, orderId, tenantId, orderReference(), customer.id(), sessionId, "EXPIRED", total,
                    customer.email(), customer.name(), "hist-" + orderId, ts(expires), null, ts(time), ts(expires),
                    customer.marketing(), total, BigDecimal.ZERO, null, null, null, BigDecimal.ZERO, null, null);
            batch.add(INS_ORDER_ITEM, UUID.randomUUID(), orderId, type.id, type.name, quantity, type.price, total);
            if (total.signum() > 0 && random.nextBoolean()) {
                batch.add(INS_PAYMENT, UUID.randomUUID(), tenantId, orderId, "SIMULATED", "sim_hist_" + SecureTokens.randomToken(12),
                        "FAILED", total, "Pago rechazado por el banco", ts(time.plusSeconds(60)), ts(time.plusSeconds(60)),
                        BigDecimal.ZERO, null, null);
            }
        }

        private PromoCode pickPromo(Instant time) {
            List<PromoCode> usable = promoCodes.stream()
                    .filter(p -> (p.validFrom() == null || !time.isBefore(p.validFrom()))
                            && (p.validUntil() == null || time.isBefore(p.validUntil()))
                            && promoUses.getOrDefault(p.id(), 0) < p.maxUses())
                    .filter(p -> !p.code().startsWith("HALLOWEEN"))
                    .toList();
            return usable.isEmpty() ? null : usable.get(random.nextInt(usable.size()));
        }

        // ---------------------------------------------------------------- Puerta

        /** ~86 % de asistencia, escaneos entre la apertura y las 3 primeras horas, algun reintento y QR falsos. */
        private int registerAttendance(UUID sessionId, List<SoldTicket> tickets, Instant doorsAt, Staff door) {
            int attendees = 0;
            for (SoldTicket ticket : tickets) {
                if (random.nextDouble() > 0.86) {
                    continue;
                }
                double peak = Math.min(1.0, Math.abs(random.nextGaussian()) * 0.35);
                Instant usedAt = doorsAt.plusSeconds((long) (peak * 3.5 * 3600) + random.nextInt(60));
                jdbcUpdateLater(ticket.id(), usedAt, door.id());
                batch.add(INS_ACCESS, UUID.randomUUID(), tenantId, sessionId, ticket.id(), door.id(), "ACCEPTED", ts(usedAt));
                if (random.nextDouble() < 0.015) {
                    batch.add(INS_ACCESS, UUID.randomUUID(), tenantId, sessionId, ticket.id(), door.id(), "ALREADY_USED",
                            ts(usedAt.plusSeconds(120 + random.nextInt(1800))));
                }
                attendees++;
            }
            for (int i = 0; i < 2 + random.nextInt(6); i++) {
                batch.add(INS_ACCESS, UUID.randomUUID(), tenantId, sessionId, null, door.id(), "INVALID_CODE",
                        ts(doorsAt.plusSeconds(random.nextInt(3 * 3600))));
            }
            return attendees;
        }

        /** Las entradas usadas se marcan al final con un UPDATE por lotes (se insertaron como VALID). */
        private final List<Object[]> usedTickets = new ArrayList<>();

        private void jdbcUpdateLater(UUID ticketId, Instant usedAt, UUID doorId) {
            usedTickets.add(new Object[]{ts(usedAt), doorId, ticketId});
        }

        // ---------------------------------------------------------------- TPV

        private void createPosInfrastructure() {
            if (catalog.isEmpty()) {
                return;
            }
            Instant created = at(firstDay.minusDays(15), LocalTime.of(12, 0));
            Staff barCashier = staff("caja@noctambula.test");
            Staff bea = newStaff("bea.navarro@noctambula.test", "Bea", "Navarro", "CASHIER", aurora, created);
            Staff hugo = newStaff("hugo.molina@noctambula.test", "Hugo", "Molina", "CASHIER", aurora, created);
            Staff irene = newStaff("irene.costa@noctambula.test", "Irene", "Costa", "CASHIER", nebula, created);

            jdbc.query("SELECT id, name, type, warehouse_id FROM pos_terminals WHERE tenant_id = ? AND venue_id = ?", rs -> {
                UUID id = rs.getObject("id", UUID.class);
                UUID warehouse = rs.getObject("warehouse_id", UUID.class);
                if (rs.getString("type").equals("BAR")) {
                    auroraBars.add(new Terminal(id, rs.getString("name"), "BAR", warehouse, barCashier));
                } else if (auroraBoxOffice == null) {
                    auroraBoxOffice = new Terminal(id, rs.getString("name"), "BOX_OFFICE", warehouse, hugo);
                }
            }, tenantId, aurora);
            if (auroraBars.isEmpty()) {
                return;
            }
            UUID auroraWarehouse = auroraBars.getFirst().warehouseId();
            UUID barra2 = UUID.randomUUID();
            batch.add(INS_TERMINAL, barra2, tenantId, aurora, auroraWarehouse, "Barra 2", "BAR", ts(created), ts(created));
            auroraBars.add(new Terminal(barra2, "Barra 2", "BAR", auroraWarehouse, bea));

            UUID nebulaWarehouse = UUID.randomUUID();
            batch.add(INS_WAREHOUSE, nebulaWarehouse, tenantId, nebula, "Barra terraza", ts(created), ts(created));
            UUID nebulaTerminal = UUID.randomUUID();
            batch.add(INS_TERMINAL, nebulaTerminal, tenantId, nebula, nebulaWarehouse, "Barra Terraza", "BAR", ts(created), ts(created));
            nebulaBar = new Terminal(nebulaTerminal, "Barra Terraza", "BAR", nebulaWarehouse, irene);

            // Existencias iniciales al empezar el periodo.
            Instant stockDate = at(firstDay.minusDays(1), LocalTime.of(11, 0));
            for (UUID warehouse : List.of(auroraWarehouse, nebulaWarehouse)) {
                Map<UUID, Integer> levels = new HashMap<>();
                for (Variant v : catalog.values()) {
                    if (v.trackStock()) {
                        int initial = parLevel(v, warehouse.equals(auroraWarehouse));
                        levels.put(v.id(), initial);
                        stockMovement(warehouse, v.id(), "IN", initial, "Inventario inicial de temporada", null, admin, stockDate);
                    }
                }
                stock.put(warehouse, levels);
            }
        }

        /** Nivel de reposicion por producto (lo que se pide al proveedor cada semana). */
        private int parLevel(Variant v, boolean bigVenue) {
            int base = switch (v.product()) {
                case "Cerveza" -> v.variant().equals("Cana") ? 900 : 450;
                case "Gin tonic" -> 600;
                case "Ron cola" -> 500;
                case "Refresco", "Agua" -> 400;
                case "Botella de cava" -> 30;
                default -> 150;
            };
            return bigVenue ? base : base / 2;
        }

        /** Ventas de barra de una noche: una caja por terminal, ventas con hora punta a las 2-3 de la madrugada. */
        private void barNight(List<Terminal> terminals, int attendees, Instant openAt, Instant closeAt, double salesPerPerson) {
            if (terminals.isEmpty()) {
                return;
            }
            int totalSales = (int) Math.round(attendees * salesPerPerson);
            String[][] menu = {{"Cerveza|Cana", "22"}, {"Cerveza|Pinta", "12"}, {"Cerveza sin alcohol|Botellin", "3"},
                    {"Gin tonic|Copa", "17"}, {"Ron cola|Copa", "14"}, {"Mojito|Copa", "9"}, {"Refresco|Lata", "9"},
                    {"Agua|Botella", "12"}, {"Botella de cava|Botella", "1"}};
            restock(terminals.getFirst().warehouseId(), openAt.minus(36, ChronoUnit.HOURS));
            for (int t = 0; t < terminals.size(); t++) {
                Terminal terminal = terminals.get(t);
                int sales = terminals.size() == 1 ? totalSales
                        : (int) Math.round(totalSales * (t == 0 ? 0.6 : 0.4 / (terminals.size() - 1)));
                cashSession(terminal, sales, openAt, closeAt, menu);
            }
        }

        private void boxOfficeNight(int attendees, Instant openAt, Instant closeAt) {
            if (auroraBoxOffice == null) {
                return;
            }
            String[][] menu = {{"Entrada en puerta|General", "30"}, {"Guardarropa|Prenda", "70"}};
            cashSession(auroraBoxOffice, (int) Math.round(attendees * 0.30), openAt, closeAt, menu);
        }

        private void cashSession(Terminal terminal, int sales, Instant openAt, Instant closeAt, String[][] menu) {
            UUID sessionId = UUID.randomUUID();
            BigDecimal opening = new BigDecimal("150.00");
            BigDecimal cash = BigDecimal.ZERO;
            Instant opened = openAt.minus(25, ChronoUnit.MINUTES);
            Instant closed = closeAt.plus(20 + random.nextInt(25), ChronoUnit.MINUTES);
            List<Object[]> saleRows = new ArrayList<>();
            List<Object[]> itemRows = new ArrayList<>();
            List<Instant> times = new ArrayList<>();
            for (int i = 0; i < sales; i++) {
                double x = Math.min(1.0, Math.max(0.0, 0.45 + random.nextGaussian() * 0.22));
                times.add(openAt.plusSeconds((long) (x * Duration.between(openAt, closeAt).getSeconds())));
            }
            times.sort(Instant::compareTo);
            for (Instant time : times) {
                UUID saleId = UUID.randomUUID();
                BigDecimal subtotal = BigDecimal.ZERO;
                int lines = random.nextDouble() < 0.7 ? 1 : (random.nextDouble() < 0.7 ? 2 : 3);
                Map<Variant, Integer> chosen = new LinkedHashMap<>();
                for (int l = 0; l < lines; l++) {
                    Variant v = pickVariant(menu);
                    if (v != null) {
                        int qty = v.product().equals("Botella de cava") ? 1 : 1 + (random.nextDouble() < 0.35 ? random.nextInt(3) : 0);
                        chosen.merge(v, qty, Integer::sum);
                    }
                }
                if (chosen.isEmpty()) {
                    continue;
                }
                for (Map.Entry<Variant, Integer> e : chosen.entrySet()) {
                    Variant v = e.getKey();
                    BigDecimal line = v.price().multiply(BigDecimal.valueOf(e.getValue()));
                    subtotal = subtotal.add(line);
                    itemRows.add(new Object[]{UUID.randomUUID(), saleId, v.id(), v.product(), v.variant(), e.getValue(),
                            v.price(), line});
                    if (v.trackStock()) {
                        consumeStock(terminal.warehouseId(), v, e.getValue(), saleId, terminal.cashier(), time);
                    }
                }
                boolean discounted = random.nextDouble() < 0.015;
                BigDecimal discount = discounted
                        ? subtotal.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                BigDecimal total = subtotal.subtract(discount);
                boolean card = random.nextDouble() < 0.58;
                BigDecimal received = card ? null : cashReceived(total);
                boolean voided = random.nextDouble() < 0.006;
                Instant voidedAt = voided ? time.plusSeconds(60 + random.nextInt(480)) : null;
                saleRows.add(new Object[]{saleId, tenantId, terminal.id(), sessionId, saleReference(),
                        voided ? "VOIDED" : "COMPLETED", subtotal, discount, total, card ? "CARD" : "CASH", received,
                        received == null ? null : received.subtract(total), discounted ? "Cliente habitual" : null,
                        discounted ? manager.id() : null, terminal.cashier().id(), "hist-" + saleId,
                        voided ? manager.id() : null, ts(voidedAt), voided ? "Cobro duplicado" : null, ts(time),
                        ts(voided ? voidedAt : time)});
                if (voided) {
                    for (Map.Entry<Variant, Integer> e : chosen.entrySet()) {
                        if (e.getKey().trackStock()) {
                            returnStock(terminal.warehouseId(), e.getKey(), e.getValue(), saleId, manager, voidedAt);
                        }
                    }
                    audit(manager, "POS_SALE_VOIDED", "PosSale", saleId, "Cobro duplicado", voidedAt);
                } else {
                    posSales++;
                    posRevenue = posRevenue.add(total);
                    if (!card) {
                        cash = cash.add(total);
                    }
                }
            }
            // Retirada de efectivo a la caja fuerte si se acumula mucho.
            List<Object[]> movements = new ArrayList<>();
            BigDecimal out = BigDecimal.ZERO;
            if (cash.compareTo(new BigDecimal("600")) > 0) {
                out = new BigDecimal("400.00");
                movements.add(new Object[]{UUID.randomUUID(), tenantId, sessionId, "CASH_OUT", out,
                        "Retirada a caja fuerte", manager.id(), ts(openAt.plus(3, ChronoUnit.HOURS))});
            }
            BigDecimal expected = opening.add(cash).subtract(out);
            double r = random.nextDouble();
            BigDecimal difference = r < 0.8 ? BigDecimal.ZERO
                    : BigDecimal.valueOf((random.nextInt(1500) - 750) / 100.0).setScale(2, RoundingMode.HALF_UP);
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
            if (total == 0) {
                return null;
            }
            int r = random.nextInt(total);
            for (String[] m : menu) {
                if (!catalog.containsKey(m[0])) continue;
                r -= Integer.parseInt(m[1]);
                if (r < 0) return catalog.get(m[0]);
            }
            return null;
        }

        private BigDecimal cashReceived(BigDecimal total) {
            int[] notes = {5, 10, 20, 50};
            if (random.nextDouble() < 0.3) {
                return total; // importe exacto
            }
            for (int note : notes) {
                if (BigDecimal.valueOf(note).compareTo(total) >= 0 && random.nextDouble() < 0.7) {
                    return BigDecimal.valueOf(note).setScale(2, RoundingMode.HALF_UP);
                }
            }
            return total.setScale(0, RoundingMode.UP).setScale(2, RoundingMode.HALF_UP);
        }

        /** Pedido semanal al proveedor (el jueves) para volver al nivel de reposicion. */
        private void restock(UUID warehouse, Instant when) {
            Map<UUID, Integer> levels = stock.get(warehouse);
            if (levels == null) {
                return;
            }
            boolean big = !warehouse.equals(nebulaBar == null ? null : nebulaBar.warehouseId());
            for (Variant v : catalog.values()) {
                if (!v.trackStock()) continue;
                int par = parLevel(v, big);
                int current = levels.getOrDefault(v.id(), 0);
                if (current < par) {
                    stockMovement(warehouse, v.id(), "IN", par - current, "Pedido semanal a proveedor", null, manager, when);
                    levels.put(v.id(), par);
                }
            }
        }

        private void consumeStock(UUID warehouse, Variant v, int quantity, UUID saleId, Staff user, Instant when) {
            Map<UUID, Integer> levels = stock.get(warehouse);
            int current = levels.getOrDefault(v.id(), 0);
            if (current < quantity) {
                int refill = parLevel(v, true) / 3 + quantity;
                stockMovement(warehouse, v.id(), "IN", refill, "Reposicion urgente desde el almacen", null, manager,
                        when.minusSeconds(60));
                current += refill;
            }
            levels.put(v.id(), current - quantity);
            stockMovement(warehouse, v.id(), "SALE", -quantity, null, saleId, user, when);
        }

        private void returnStock(UUID warehouse, Variant v, int quantity, UUID saleId, Staff user, Instant when) {
            Map<UUID, Integer> levels = stock.get(warehouse);
            levels.merge(v.id(), quantity, Integer::sum);
            stockMovement(warehouse, v.id(), "SALE_VOID", quantity, "Anulacion de venta", saleId, user, when);
        }

        private void stockMovement(UUID warehouse, UUID variant, String type, int quantity, String reason, UUID saleId,
                                   Staff user, Instant when) {
            batch.add(INS_STOCK_MOVEMENT, UUID.randomUUID(), tenantId, warehouse, variant, type, quantity, reason, saleId,
                    user == null ? null : user.id(), ts(when));
        }

        // ---------------------------------------------------------------- Reservas VIP

        private void reservations(UUID venue, UUID sessionId, Instant startsAt, Instant endsAt, boolean fullHouse,
                                  boolean upcoming) {
            for (VipTable table : vipTables) {
                if (!table.venueId().equals(venue) || random.nextDouble() > (fullHouse ? 1.0 : 0.55)) {
                    continue;
                }
                Customer customer = pickCustomer(startsAt.minus(3, ChronoUnit.DAYS));
                double r = random.nextDouble();
                String status = upcoming ? (r < 0.6 ? "CONFIRMED" : "PENDING")
                        : (r < 0.80 ? "COMPLETED" : r < 0.88 ? "NO_SHOW" : "CANCELLED");
                Instant created = startsAt.minus(2 + random.nextInt(12), ChronoUnit.DAYS);
                Instant depositPaid = table.deposit().signum() > 0 && !status.equals("PENDING")
                        ? created.plus(random.nextInt(30), ChronoUnit.HOURS) : null;
                Instant cancelled = status.equals("CANCELLED") ? startsAt.minus(1, ChronoUnit.DAYS) : null;
                int guests = Math.max(2, table.capacity() - random.nextInt(Math.max(1, table.capacity() / 2)));
                batch.add(INS_RESERVATION, UUID.randomUUID(), tenantId, venue, table.id(), sessionId, null, customer.id(),
                        reservationReference(), customer.name(), customer.email(), customer.phone(), guests, ts(startsAt),
                        ts(endsAt), status, table.minSpend(), table.deposit(), ts(depositPaid),
                        random.nextDouble() < 0.6 ? "WEB" : "BACKOFFICE", ts(cancelled), ts(created),
                        ts(cancelled != null ? cancelled : created));
            }
        }

        // ---------------------------------------------------------------- Preventa de proximos eventos

        /** Ventas ya hechas para las sesiones futuras (eventos publicados), con el stock disponible real. */
        private void presales() {
            jdbc.query("""
                    SELECT s.id AS session_id, s.starts_at, e.venue_id, t.id AS type_id, t.name, t.category, t.price,
                           t.quantity_total, t.quantity_sold, t.max_per_order, t.sales_end_at
                    FROM event_sessions s
                    JOIN events e ON e.id = s.event_id
                    JOIN ticket_types t ON t.session_id = s.id
                    WHERE s.tenant_id = ? AND e.status = 'PUBLISHED' AND s.status = 'SCHEDULED' AND s.starts_at > ? AND t.active
                    ORDER BY s.starts_at""", rs -> {
                Instant startsAt = rs.getTimestamp("starts_at").toInstant();
                if (Duration.between(now, startsAt).toHours() < 6) {
                    return; // La sesion "Late Night Demo" se deja para probar la compra a mano.
                }
                Timestamp end = rs.getTimestamp("sales_end_at");
                TypeSpec type = new TypeSpec(rs.getObject("type_id", UUID.class), rs.getString("name"), rs.getString("category"),
                        rs.getBigDecimal("price").toPlainString(), rs.getInt("quantity_total") - rs.getInt("quantity_sold"),
                        rs.getInt("max_per_order"), end == null ? null : end.toInstant());
                UUID sessionId = rs.getObject("session_id", UUID.class);
                UUID venue = rs.getObject("venue_id", UUID.class);
                double daysAway = Duration.between(now, startsAt).toHours() / 24.0;
                double fraction = Math.max(0.08, 0.55 - daysAway * 0.03);
                int target = (int) (type.total * fraction);
                int sold = 0;
                while (sold < target && type.available() > 0) {
                    int qty = Math.min(pickOrderSize(), Math.min(type.maxPerOrder, type.available()));
                    Instant time = now.minus(10 + random.nextInt(14 * 24 * 60), ChronoUnit.MINUTES);
                    if (type.salesEnd != null && !time.isBefore(type.salesEnd)) {
                        continue;
                    }
                    paidOrder(sessionId, venue, pickCustomer(time), type,
                            qty, time, "PAID", null, null);
                    sold += qty;
                }
            }, tenantId, Timestamp.from(now));
            // Reservas ya confirmadas para el proximo fin de semana.
            jdbc.query("""
                    SELECT s.id, s.starts_at, s.ends_at, e.venue_id FROM event_sessions s JOIN events e ON e.id = s.event_id
                    WHERE s.tenant_id = ? AND s.status = 'SCHEDULED' AND s.starts_at BETWEEN ? AND ?
                    ORDER BY s.starts_at""", rs -> {
                UUID sessionId = rs.getObject("id", UUID.class);
                Instant starts = rs.getTimestamp("starts_at").toInstant();
                Instant ends = rs.getTimestamp("ends_at").toInstant();
                // Solo mesas sin reservas activas que se solapen (la BD lo impediria igualmente).
                List<VipTable> free = vipTables.stream()
                        .filter(t -> Boolean.FALSE.equals(jdbc.queryForObject("""
                                SELECT EXISTS (SELECT 1 FROM reservations WHERE table_id = ?
                                  AND status IN ('PENDING','CONFIRMED','SEATED') AND starts_at < ? AND ends_at > ?)""",
                                Boolean.class, t.id(), Timestamp.from(ends), Timestamp.from(starts))))
                        .toList();
                List<VipTable> saved = new ArrayList<>(vipTables);
                vipTables.clear();
                vipTables.addAll(free);
                reservations(rs.getObject("venue_id", UUID.class), sessionId, starts, ends, false, true);
                vipTables.clear();
                vipTables.addAll(saved);
            }, tenantId, Timestamp.from(now.plus(6, ChronoUnit.HOURS)), Timestamp.from(now.plus(9, ChronoUnit.DAYS)));
        }

        // ---------------------------------------------------------------- Cierre

        private void finish() {
            // Entradas usadas en la puerta.
            for (int from = 0; from < usedTickets.size(); from += 2000) {
                jdbc.batchUpdate("UPDATE tickets SET status = 'USED', used_at = ?, used_by_user_id = ? WHERE id = ?",
                        usedTickets.subList(from, Math.min(usedTickets.size(), from + 2000)));
            }
            // Contadores de usos de los codigos promocionales.
            promoUses.forEach((id, uses) ->
                    jdbc.update("UPDATE promo_codes SET times_used = times_used + ? WHERE id = ?", uses, id));
            // Stock vendido en preventa de los tipos de entrada que ya existian.
            jdbc.update("""
                    UPDATE ticket_types t SET quantity_sold = sub.sold
                    FROM (SELECT i.ticket_type_id, SUM(i.quantity) AS sold FROM ticket_order_items i
                          JOIN ticket_orders o ON o.id = i.order_id
                          WHERE o.tenant_id = ? AND o.status IN ('PAID', 'PENDING_PAYMENT')
                          GROUP BY i.ticket_type_id) sub
                    WHERE t.id = sub.ticket_type_id AND t.tenant_id = ?""", tenantId, tenantId);
            // Existencias finales: lo que queda tras el periodo + el efecto de los movimientos anteriores.
            stock.forEach((warehouse, levels) -> levels.forEach((variant, quantity) -> jdbc.update("""
                    INSERT INTO stock_levels (id, tenant_id, warehouse_id, variant_id, quantity, min_quantity, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (warehouse_id, variant_id) DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = EXCLUDED.updated_at""",
                    UUID.randomUUID(), tenantId, warehouse, variant, quantity, 20, Timestamp.from(now))));
            // Fichas CRM y consentimientos de los clientes demo.
            jdbc.update("""
                    INSERT INTO customers (id, tenant_id, user_id, email, first_name, last_name, phone, notes, tags,
                                           marketing_consent, orders_count, tickets_count, total_spent, first_purchase_at,
                                           last_purchase_at, created_at, updated_at)
                    SELECT gen_random_uuid(), o.tenant_id, u.id, u.email, u.first_name, u.last_name, u.phone, NULL,
                           CASE WHEN COUNT(*) >= 6 AND SUM(o.total_amount) >= 200 THEN 'habitual, vip'
                                WHEN COUNT(*) >= 6 THEN 'habitual' ELSE NULL END,
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
            // Ultimo acceso de cada cliente: su ultima compra.
            jdbc.update("""
                    UPDATE users u SET last_login_at = sub.last FROM (
                        SELECT customer_user_id, MAX(created_at) AS last FROM ticket_orders GROUP BY customer_user_id) sub
                    WHERE u.id = sub.customer_user_id AND u.email LIKE ?""", "%" + EMAIL_DOMAIN);
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
