package com.bloop.customers.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.authentication.repository.RefreshTokenRepository;
import com.bloop.customers.dto.request.UpdateConsentRequest;
import com.bloop.customers.dto.response.CustomerDetailResponse;
import com.bloop.customers.dto.response.CustomerExportResponse;
import com.bloop.customers.dto.response.MyConsentResponse;
import com.bloop.customers.dto.response.MyDataExportResponse;
import com.bloop.customers.entity.ConsentSource;
import com.bloop.customers.entity.Customer;
import com.bloop.customers.repository.CustomerRepository;
import com.bloop.orders.repository.TicketOrderRepository;
import com.bloop.reservations.entity.ReservationStatus;
import com.bloop.reservations.repository.ReservationRepository;
import com.bloop.shared.enums.RoleCode;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.ForbiddenException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.shared.util.SecureTokens;
import com.bloop.tenants.entity.Tenant;
import com.bloop.tenants.repository.TenantRepository;
import com.bloop.ticketing.repository.TicketRepository;
import com.bloop.users.entity.User;
import com.bloop.users.entity.UserStatus;
import com.bloop.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Derechos RGPD.
 *
 * Empresa (backoffice, permiso CUSTOMER_MANAGE):
 * - Exportar la ficha de un cliente (derecho de acceso / portabilidad).
 * - Suprimir la ficha del CRM: se anonimiza (se borran nombre, email, telefono, notas y etiquetas).
 *   Los pedidos se conservan con sus datos porque la empresa debe guardar sus registros contables
 *   (art. 17.3.b RGPD); la ficha anonimizada ya no se usa para marketing ni segmentacion.
 *
 * Cliente (zona "Mi cuenta"):
 * - Ver que empresas tienen su ficha y retirar o dar el consentimiento comercial.
 * - Descargar todos sus datos.
 * - Suprimir su cuenta: se anonimizan la cuenta, sus fichas, sus pedidos, sus entradas y sus reservas.
 *   No se borran filas para no romper la contabilidad ni las estadisticas de las empresas.
 */
@Service
@RequiredArgsConstructor
public class GdprService {

    static final String ANONYMIZED_NAME = "Anonimizado";
    private static final String ANONYMIZED_DOMAIN = "@anonimizado.invalid";

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final TicketOrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final Clock clock;

    // ----------------------------------------------------------------- Empresa

    @Transactional(readOnly = true)
    public CustomerExportResponse exportForTenant(UUID customerId) {
        Customer customer = customerService.findInMyTenant(customerId);
        CustomerDetailResponse detail = customerService.toDetail(customer);
        String companyName = tenantRepository.findById(customer.getTenantId()).map(Tenant::getName).orElse("");
        auditService.record(AuditAction.CUSTOMER_EXPORTED, "Customer", customerId, "Exportacion RGPD");
        return new CustomerExportResponse(Instant.now(clock), companyName, detail.customer(), detail.notes(),
                detail.consents(), detail.orders(), detail.reservations());
    }

    @Transactional
    public void anonymizeForTenant(UUID customerId) {
        Customer customer = customerService.findInMyTenant(customerId);
        Instant now = Instant.now(clock);
        if (reservationRepository.countByCustomerIdAndStatusInAndEndsAtAfter(customerId, ReservationStatus.ACTIVE,
                now) > 0) {
            throw new ConflictException("El cliente tiene reservas activas. Cancelalas antes de suprimir sus datos");
        }
        anonymizeRecord(customer, now, ConsentSource.BACKOFFICE);
        reservationRepository.anonymizeContactOfCustomer(customerId, ANONYMIZED_NAME, anonymizedEmail(customerId));
        auditService.record(AuditAction.CUSTOMER_ANONYMIZED, "Customer", customerId, "Supresion RGPD de la ficha");
    }

    // ----------------------------------------------------------------- Cliente

    @Transactional(readOnly = true)
    public List<MyConsentResponse> myConsents() {
        List<Customer> records = myRecords();
        Map<UUID, String> companies = companyNames(records);
        return records.stream()
                .map(c -> new MyConsentResponse(c.getId(), companies.getOrDefault(c.getTenantId(), ""),
                        c.isMarketingConsent(), c.getLastPurchaseAt()))
                .toList();
    }

    @Transactional
    public MyConsentResponse updateMyConsent(UUID customerId, UpdateConsentRequest request) {
        UUID userId = currentUserProvider.get().userId();
        Customer customer = customerRepository.findById(customerId)
                .filter(c -> userId.equals(c.getUserId()))
                .orElseThrow(() -> NotFoundException.of("Empresa"));
        if (customer.isMarketingConsent() != request.marketingConsent()) {
            customerService.changeConsent(customer, request.marketingConsent(), ConsentSource.CUSTOMER);
            auditService.recordInTenant(customer.getTenantId(), AuditAction.CONSENT_CHANGED, "Customer", customerId,
                    "Consentimiento comercial " + (request.marketingConsent() ? "concedido" : "retirado")
                            + " por el cliente");
        }
        String company = tenantRepository.findById(customer.getTenantId()).map(Tenant::getName).orElse("");
        return new MyConsentResponse(customer.getId(), company, customer.isMarketingConsent(),
                customer.getLastPurchaseAt());
    }

    @Transactional(readOnly = true)
    public MyDataExportResponse exportMine() {
        User user = currentUser();
        var account = new MyDataExportResponse.Account(user.getId(), user.getEmail(), user.getFirstName(),
                user.getLastName(), user.getPhone(), user.isMarketingConsent(), user.getPrivacyAcceptedAt(),
                user.getCreatedAt());
        var orders = orderRepository.findAllOfCustomer(user.getId()).stream()
                .map(o -> new MyDataExportResponse.Order(o.getReference(), o.getSession().getEvent().getName(),
                        o.getSession().getStartsAt(), o.getStatus(), o.getTotalAmount(), o.getCurrency(),
                        o.getCreatedAt()))
                .toList();
        var tickets = ticketRepository.findAllOfOwner(user.getId()).stream()
                .map(t -> new MyDataExportResponse.Ticket(t.getSession().getEvent().getName(),
                        t.getSession().getStartsAt(), t.getTicketType().getName(), t.getHolderName(), t.getStatus()))
                .toList();
        var reservations = reservationRepository.findAllOfCustomerUser(user.getId()).stream()
                .map(r -> new MyDataExportResponse.Reservation(r.getReference(), r.getTable().getName(),
                        r.getStartsAt(), r.getGuests(), r.getStatus()))
                .toList();
        List<Customer> records = myRecords();
        Map<UUID, String> companies = companyNames(records);
        var companyRecords = records.stream()
                .map(c -> new MyDataExportResponse.CompanyRecord(companies.getOrDefault(c.getTenantId(), ""),
                        c.isMarketingConsent(), c.getOrdersCount(), c.getTotalSpent(), c.getLastPurchaseAt()))
                .toList();
        return new MyDataExportResponse(Instant.now(clock), account, orders, tickets, reservations, companyRecords);
    }

    /**
     * Derecho de supresion del propio cliente. Solo para cuentas de cliente: las cuentas de
     * empleado las gestiona el administrador de su empresa.
     */
    @Transactional
    public void deleteMyAccount(String password) {
        User user = currentUser();
        if (user.getTenantId() != null || user.getRoleCodes().contains(RoleCode.SUPER_ADMIN.name())) {
            throw new ForbiddenException("Las cuentas de empleado las gestiona el administrador de la empresa");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("La contrasena no es correcta");
        }
        Instant now = Instant.now(clock);
        if (ticketRepository.countUpcomingValidOfOwner(user.getId(), now) > 0) {
            throw new ConflictException("Tienes entradas para eventos que aun no se han celebrado. "
                    + "Transfierelas o espera a que pase el evento antes de eliminar tu cuenta");
        }
        if (reservationRepository.countByCustomerUserIdAndStatusInAndEndsAtAfter(user.getId(),
                ReservationStatus.ACTIVE, now) > 0) {
            throw new ConflictException("Tienes reservas activas. Cancelalas antes de eliminar tu cuenta");
        }

        UUID userId = user.getId();
        String email = "eliminado-" + userId + ANONYMIZED_DOMAIN;
        for (Customer record : customerRepository.findByUserIdOrderByCreatedAt(userId)) {
            anonymizeRecord(record, now, ConsentSource.CUSTOMER);
            auditService.recordInTenant(record.getTenantId(), AuditAction.CUSTOMER_ANONYMIZED, "Customer",
                    record.getId(), "El cliente suprimio su cuenta");
        }
        ticketRepository.anonymizeHolder(userId, ANONYMIZED_NAME);
        reservationRepository.anonymizeContactOfUser(userId, ANONYMIZED_NAME, email);

        user.setEmail(email);
        user.setFirstName(ANONYMIZED_NAME);
        user.setLastName(ANONYMIZED_NAME);
        user.setPhone(null);
        user.setMarketingConsent(false);
        user.setStatus(UserStatus.DISABLED);
        // Contrasena aleatoria que nadie conoce: la cuenta queda inutilizable.
        user.setPasswordHash(passwordEncoder.encode(SecureTokens.randomToken(32)));
        refreshTokenRepository.revokeAllForUser(userId, now);
        auditService.recordFor(null, userId, null, AuditAction.ACCOUNT_DELETED, "Cuenta suprimida por el usuario");
        // Al final: esta actualizacion masiva limpia el contexto de persistencia (clearAutomatically).
        orderRepository.anonymizeBuyer(userId, ANONYMIZED_NAME, email);
    }

    // ----------------------------------------------------------------- Auxiliares

    private void anonymizeRecord(Customer customer, Instant now, ConsentSource source) {
        customer.setEmail(anonymizedEmail(customer.getId()));
        customer.setFirstName("Cliente");
        customer.setLastName(ANONYMIZED_NAME.toLowerCase());
        customer.setPhone(null);
        customer.setNotes(null);
        customer.setTags(null);
        if (customer.isMarketingConsent()) {
            customerService.changeConsent(customer, false, source);
        }
        customer.setUserId(null);
        customer.setAnonymizedAt(now);
    }

    private static String anonymizedEmail(UUID id) {
        return "anonimizado-" + id + ANONYMIZED_DOMAIN;
    }

    private List<Customer> myRecords() {
        return customerRepository.findByUserIdOrderByCreatedAt(currentUserProvider.get().userId());
    }

    private Map<UUID, String> companyNames(List<Customer> records) {
        var ids = records.stream().map(Customer::getTenantId).collect(Collectors.toSet());
        return tenantRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Tenant::getId, Tenant::getName, (a, b) -> a));
    }

    private User currentUser() {
        return userRepository.findById(currentUserProvider.get().userId())
                .orElseThrow(() -> NotFoundException.of("Usuario"));
    }
}
