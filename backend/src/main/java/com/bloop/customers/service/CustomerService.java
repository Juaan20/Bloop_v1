package com.bloop.customers.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.customers.dto.request.UpdateCustomerRequest;
import com.bloop.customers.dto.response.CustomerDetailResponse;
import com.bloop.customers.dto.response.CustomerResponse;
import com.bloop.customers.entity.ConsentSource;
import com.bloop.customers.entity.ConsentType;
import com.bloop.customers.entity.Customer;
import com.bloop.customers.entity.CustomerConsent;
import com.bloop.customers.entity.CustomerSegment;
import com.bloop.customers.mapper.CustomerMapper;
import com.bloop.customers.repository.CustomerConsentRepository;
import com.bloop.customers.repository.CustomerRepository;
import com.bloop.orders.repository.TicketOrderRepository;
import com.bloop.reservations.repository.ReservationRepository;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.response.PageResponse;
import com.bloop.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CRM de clientes de la empresa del usuario autenticado.
 *
 * Las fichas se crean solas: OrderPaymentService llama a {@link #registerPaidOrder} al
 * confirmar un pago, y las reservas web llaman a {@link #ensureCustomer}.
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerConsentRepository consentRepository;
    private final TicketOrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final CustomerMapper customerMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(String search, CustomerSegment segment, boolean marketingOnly,
                                               int page, int size) {
        UUID tenantId = currentUserProvider.requireTenantId();
        Instant now = Instant.now(clock);
        var result = customerRepository.search(tenantId, search == null ? "" : search.trim(),
                segment == null ? "" : segment.name(), CustomerSegment.VIP_MIN_SPENT,
                now.minus(CustomerSegment.INACTIVE_AFTER), marketingOnly, PageRequest.of(page, Math.min(size, 100)));
        return PageResponse.of(result, c -> customerMapper.toResponse(c, now));
    }

    @Transactional(readOnly = true)
    public CustomerDetailResponse get(UUID id) {
        return toDetail(findInMyTenant(id));
    }

    @Transactional
    public CustomerDetailResponse update(UUID id, UpdateCustomerRequest request) {
        Customer customer = findInMyTenant(id);
        customer.setPhone(blankToNull(request.phone()));
        customer.setNotes(blankToNull(request.notes()));
        customer.setTags(normalizeTags(request.tags()));
        if (customer.isMarketingConsent() != request.marketingConsent()) {
            changeConsent(customer, request.marketingConsent(), ConsentSource.BACKOFFICE);
        }
        auditService.record(AuditAction.CUSTOMER_UPDATED, "Customer", id, customer.getEmail());
        return toDetail(customer);
    }

    /**
     * Suma un pedido pagado a la ficha del cliente (la crea si es su primera compra en la empresa).
     * Se ejecuta dentro de la transaccion que confirma el pago: si algo falla, el pago tampoco se confirma.
     *
     * @param marketingConsent si el cliente marco la casilla de comunicaciones en la compra. Una casilla
     *                         sin marcar NO retira un consentimiento dado antes.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registerPaidOrder(UUID tenantId, UUID userId, BigDecimal total, int tickets, Instant paidAt,
                                  boolean marketingConsent) {
        Customer customer = ensureCustomer(tenantId, userId);
        customer.setOrdersCount(customer.getOrdersCount() + 1);
        customer.setTicketsCount(customer.getTicketsCount() + tickets);
        customer.setTotalSpent(customer.getTotalSpent().add(total));
        if (customer.getFirstPurchaseAt() == null) {
            customer.setFirstPurchaseAt(paidAt);
        }
        customer.setLastPurchaseAt(paidAt);
        if (marketingConsent && !customer.isMarketingConsent()) {
            changeConsent(customer, true, ConsentSource.CHECKOUT);
        }
    }

    /**
     * Resta de la ficha del cliente una compra reembolsada, para que el gasto y las entradas
     * del CRM reflejen solo lo que realmente ha pagado. Nunca deja valores negativos.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registerRefund(UUID tenantId, UUID userId, BigDecimal total, int tickets) {
        Customer customer = ensureCustomer(tenantId, userId);
        customer.setOrdersCount(Math.max(0, customer.getOrdersCount() - 1));
        customer.setTicketsCount(Math.max(0, customer.getTicketsCount() - tickets));
        customer.setTotalSpent(customer.getTotalSpent().subtract(total).max(BigDecimal.ZERO));
    }

    /**
     * Devuelve la ficha del cliente en la empresa, creandola si no existe.
     * La ficha queda bloqueada (SELECT ... FOR UPDATE) hasta el final de la transaccion.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Customer ensureCustomer(UUID tenantId, UUID userId) {
        customerRepository.insertIfMissing(tenantId, userId);
        return customerRepository.findByTenantAndUserForUpdate(tenantId, userId)
                .orElseThrow(() -> NotFoundException.of("Cliente"));
    }

    /** Ficha activa (no anonimizada) con ese email en la empresa, si existe. */
    @Transactional(readOnly = true)
    public java.util.Optional<Customer> findByEmail(UUID tenantId, String email) {
        return customerRepository.findFirstByTenantIdAndEmailIgnoreCaseAndAnonymizedAtIsNull(tenantId, email);
    }

    /** Cambia el consentimiento y deja constancia en el historial. */
    void changeConsent(Customer customer, boolean granted, ConsentSource source) {
        customer.setMarketingConsent(granted);
        CustomerConsent consent = new CustomerConsent();
        consent.setTenantId(customer.getTenantId());
        consent.setCustomerId(customer.getId());
        consent.setType(ConsentType.MARKETING);
        consent.setGranted(granted);
        consent.setSource(source);
        consent.setCreatedAt(Instant.now(clock));
        consentRepository.save(consent);
    }

    CustomerDetailResponse toDetail(Customer customer) {
        Instant now = Instant.now(clock);
        var orders = customer.getUserId() == null ? List.<CustomerDetailResponse.OrderSummary>of()
                : orderRepository.findAllOfCustomerInTenant(customer.getTenantId(), customer.getUserId()).stream()
                        .map(customerMapper::toOrderSummary).toList();
        var reservations = reservationRepository.findAllOfCustomer(customer.getId()).stream()
                .map(customerMapper::toReservationSummary).toList();
        var consents = consentRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).stream()
                .map(customerMapper::toConsent).toList();
        return new CustomerDetailResponse(customerMapper.toResponse(customer, now), customer.getNotes(), orders,
                reservations, consents);
    }

    /** Ficha del tenant actual (404 si no existe, es de otra empresa o esta anonimizada). */
    Customer findInMyTenant(UUID id) {
        return customerRepository.findByIdAndTenantId(id, currentUserProvider.requireTenantId())
                .filter(c -> !c.isAnonymized())
                .orElseThrow(() -> NotFoundException.of("Cliente"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** "  VIP,cumple , ,vip " -> "VIP, cumple, vip" */
    private static String normalizeTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return null;
        }
        String joined = String.join(", ", java.util.Arrays.stream(tags.split(","))
                .map(String::trim).filter(t -> !t.isEmpty()).distinct().toList());
        return joined.isEmpty() ? null : joined;
    }
}
