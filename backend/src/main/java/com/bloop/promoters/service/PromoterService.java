package com.bloop.promoters.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.events.entity.Event;
import com.bloop.events.repository.EventRepository;
import com.bloop.orders.repository.EventTicketsRow;
import com.bloop.orders.repository.PromoterSalesRow;
import com.bloop.orders.repository.TicketOrderRepository;
import com.bloop.promoters.dto.request.CreatePromoterRequest;
import com.bloop.promoters.dto.request.PromoterAssignmentsRequest;
import com.bloop.promoters.dto.request.UpdatePromoterRequest;
import com.bloop.promoters.dto.response.PromoterCandidateResponse;
import com.bloop.promoters.dto.response.PromoterDetailResponse;
import com.bloop.promoters.dto.response.PromoterResponse;
import com.bloop.promoters.entity.CommissionType;
import com.bloop.promoters.entity.Promoter;
import com.bloop.promoters.entity.PromoterAssignment;
import com.bloop.promoters.repository.PromoCodeRepository;
import com.bloop.promoters.repository.PromoterRepository;
import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.enums.RoleCode;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.shared.util.SecureTokens;
import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Promotores / RRPP: alta, comisiones, eventos asignados con objetivos y estadisticas.
 *
 * Atribucion de ventas (la hace CheckoutService):
 * 1. Si el pedido usa un codigo promocional de un promotor, la venta es de ese promotor.
 * 2. Si no, y el cliente llego por un enlace ?rrpp=CODIGO de un promotor activo asignado
 *    al evento, la venta es de ese promotor.
 * La comision se calcula al crear el pedido, pero solo cuenta en las estadisticas si se paga.
 */
@Service
@RequiredArgsConstructor
public class PromoterService {

    private final PromoterRepository promoterRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final TicketOrderRepository orderRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final PromoCodeService promoCodeService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final BloopProperties properties;

    @Transactional(readOnly = true)
    public List<PromoterResponse> list() {
        List<Promoter> promoters = promoterRepository.findByTenantIdOrderByCreatedAtDesc(
                currentUserProvider.requireTenantId());
        Map<UUID, User> users = usersOf(promoters);
        return promoters.stream().map(p -> summary(p, users.get(p.getUserId()), stats(p))).toList();
    }

    /** Empleados con rol PROMOTER que aun no estan dados de alta como promotores. */
    @Transactional(readOnly = true)
    public List<PromoterCandidateResponse> candidates() {
        UUID tenantId = currentUserProvider.requireTenantId();
        return userRepository.findByTenantIdAndRole(tenantId, RoleCode.PROMOTER.name()).stream()
                .filter(u -> !promoterRepository.existsByUserId(u.getId()))
                .map(u -> new PromoterCandidateResponse(u.getId(), u.getFullName(), u.getEmail()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PromoterDetailResponse get(UUID id) {
        return detail(findInMyTenant(id));
    }

    /** Panel del propio promotor (usuario autenticado con PROMOTER_PANEL). */
    @Transactional(readOnly = true)
    public PromoterDetailResponse myPanel() {
        return promoterRepository.findByUserId(currentUserProvider.get().userId())
                .map(this::detail)
                .orElseThrow(() -> new NotFoundException(
                        "Tu cuenta de promotor aun no esta configurada. Pide a tu empresa que te de de alta"));
    }

    @Transactional
    public PromoterDetailResponse create(CreatePromoterRequest request) {
        UUID tenantId = currentUserProvider.requireTenantId();
        User user = userRepository.findByIdAndTenantId(request.userId(), tenantId)
                .orElseThrow(() -> NotFoundException.of("Usuario"));
        if (!user.getRoleCodes().contains(RoleCode.PROMOTER.name())) {
            throw new BadRequestException("El usuario debe tener el rol Promotor / RRPP");
        }
        if (promoterRepository.existsByUserId(user.getId())) {
            throw new ConflictException("Este usuario ya esta dado de alta como promotor");
        }
        validateCommission(request.commissionType(), request.commissionValue());

        Promoter promoter = new Promoter();
        promoter.setTenantId(tenantId);
        promoter.setUserId(user.getId());
        promoter.setReferralCode(chooseReferralCode(request.referralCode(), user));
        promoter.setCommissionType(request.commissionType());
        promoter.setCommissionValue(request.commissionValue());
        promoter = promoterRepository.save(promoter);
        auditService.record(AuditAction.PROMOTER_CREATED, "Promoter", promoter.getId(),
                user.getEmail() + " codigo=" + promoter.getReferralCode());
        return detail(promoter);
    }

    @Transactional
    public PromoterDetailResponse update(UUID id, UpdatePromoterRequest request) {
        Promoter promoter = findInMyTenant(id);
        validateCommission(request.commissionType(), request.commissionValue());
        promoter.setCommissionType(request.commissionType());
        promoter.setCommissionValue(request.commissionValue());
        promoter.setActive(request.active());
        auditService.record(AuditAction.PROMOTER_UPDATED, "Promoter", id,
                "comision=" + request.commissionValue() + " " + request.commissionType() + " activo="
                        + request.active());
        return detail(promoter);
    }

    /** Sustituye la lista de eventos asignados (y sus objetivos). */
    @Transactional
    public PromoterDetailResponse updateAssignments(UUID id, PromoterAssignmentsRequest request) {
        Promoter promoter = findInMyTenant(id);
        Set<UUID> eventIds = new HashSet<>();
        for (var item : request.assignments()) {
            if (!eventIds.add(item.eventId())) {
                throw new BadRequestException("Un evento esta repetido en la lista");
            }
        }
        if (!eventIds.isEmpty()
                && eventRepository.countByIdInAndTenantId(eventIds, promoter.getTenantId()) != eventIds.size()) {
            throw new BadRequestException("Alguno de los eventos indicados no existe");
        }
        promoter.getAssignments().clear();
        request.assignments().forEach(item -> promoter.getAssignments()
                .add(new PromoterAssignment(item.eventId(), item.targetTickets())));
        auditService.record(AuditAction.PROMOTER_UPDATED, "Promoter", id, eventIds.size() + " eventos asignados");
        return detail(promoter);
    }

    /**
     * Promotor de un enlace de venta (?rrpp=CODIGO) valido para el evento: activo, de la misma
     * empresa y con el evento asignado. Un codigo de enlace incorrecto NO impide comprar.
     */
    @Transactional(readOnly = true)
    public Optional<Promoter> findForReferral(String referralCode, UUID tenantId, UUID eventId) {
        if (referralCode == null || referralCode.isBlank()) {
            return Optional.empty();
        }
        return promoterRepository.findByReferralCode(referralCode.trim().toUpperCase(Locale.ROOT))
                .filter(p -> p.isActive() && p.getTenantId().equals(tenantId) && p.isAssignedTo(eventId));
    }

    @Transactional(readOnly = true)
    public Optional<Promoter> findActive(UUID promoterId) {
        return promoterRepository.findById(promoterId).filter(Promoter::isActive);
    }

    // ----------------------------------------------------------------- Construccion de respuestas

    private PromoterDetailResponse detail(Promoter promoter) {
        Stats stats = stats(promoter);
        User user = userRepository.findById(promoter.getUserId()).orElse(null);

        // Eventos asignados + eventos que ya no estan asignados pero tienen ventas atribuidas.
        Set<UUID> eventIds = new LinkedHashSet<>();
        promoter.getAssignments().forEach(a -> eventIds.add(a.getEventId()));
        eventIds.addAll(stats.sales().keySet());
        Map<UUID, Event> events = eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        Map<UUID, Integer> targets = new java.util.HashMap<>();
        promoter.getAssignments().forEach(a -> targets.put(a.getEventId(), a.getTargetTickets()));

        List<PromoterDetailResponse.EventPerformance> performance = new ArrayList<>();
        for (UUID eventId : eventIds) {
            Event event = events.get(eventId);
            if (event == null) {
                continue;
            }
            PromoterSalesRow sales = stats.sales().get(eventId);
            long tickets = stats.tickets().getOrDefault(eventId, 0L);
            Integer target = targets.get(eventId);
            Integer progress = target == null ? null : (int) Math.min(999, Math.round(tickets * 100.0 / target));
            performance.add(new PromoterDetailResponse.EventPerformance(eventId, event.getName(), event.getSlug(),
                    event.getStatus(), targets.containsKey(eventId),
                    properties.publicUrl() + "/eventos/" + event.getSlug() + "?rrpp=" + promoter.getReferralCode(),
                    target, tickets, sales == null ? 0 : sales.orders(),
                    sales == null ? BigDecimal.ZERO : sales.revenue(),
                    sales == null ? BigDecimal.ZERO : sales.commission(), progress));
        }
        var codes = promoCodeRepository.findByPromoterIdOrderByCreatedAtDesc(promoter.getId()).stream()
                .map(promoCodeService::toResponse).toList();
        return new PromoterDetailResponse(summary(promoter, user, stats), performance, codes);
    }

    private PromoterResponse summary(Promoter p, User user, Stats stats) {
        long tickets = stats.tickets().values().stream().mapToLong(Long::longValue).sum();
        long orders = stats.sales().values().stream().mapToLong(PromoterSalesRow::orders).sum();
        BigDecimal revenue = stats.sales().values().stream().map(PromoterSalesRow::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal commission = stats.sales().values().stream().map(PromoterSalesRow::commission)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PromoterResponse(p.getId(), p.getUserId(), user == null ? "" : user.getFullName(),
                user == null ? "" : user.getEmail(), p.getReferralCode(), p.getCommissionType(),
                p.getCommissionValue(), p.isActive(), p.getAssignments().size(), tickets, orders, revenue, commission,
                p.getCreatedAt());
    }

    /** Ventas pagadas del promotor por evento (dos consultas agregadas: importes y entradas). */
    private Stats stats(Promoter promoter) {
        Map<UUID, PromoterSalesRow> sales = orderRepository.sumPromoterSalesByEvent(promoter.getId()).stream()
                .collect(Collectors.toMap(PromoterSalesRow::eventId, Function.identity()));
        Map<UUID, Long> tickets = orderRepository.sumPromoterTicketsByEvent(promoter.getId()).stream()
                .collect(Collectors.toMap(EventTicketsRow::eventId, EventTicketsRow::tickets));
        return new Stats(sales, tickets);
    }

    private record Stats(Map<UUID, PromoterSalesRow> sales, Map<UUID, Long> tickets) {
    }

    private Map<UUID, User> usersOf(List<Promoter> promoters) {
        return userRepository.findAllById(promoters.stream().map(Promoter::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    // ----------------------------------------------------------------- Auxiliares

    Promoter findInMyTenant(UUID id) {
        return promoterRepository.findByIdAndTenantId(id, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Promotor"));
    }

    private static void validateCommission(CommissionType type, BigDecimal value) {
        if (type == CommissionType.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("Una comision porcentual no puede superar el 100 %");
        }
    }

    /** Usa el codigo pedido (si esta libre) o genera uno a partir del nombre: "DANI-7KX2". */
    private String chooseReferralCode(String requested, User user) {
        if (requested != null && !requested.isBlank()) {
            String code = requested.trim().toUpperCase(Locale.ROOT);
            if (promoterRepository.existsByReferralCode(code)) {
                throw new ConflictException("El codigo de enlace '" + code + "' ya esta en uso");
            }
            return code;
        }
        String base = Normalizer.normalize(user.getFirstName(), Normalizer.Form.NFD)
                .replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        base = base.isEmpty() ? "RRPP" : base.substring(0, Math.min(10, base.length()));
        String code;
        do {
            code = base + "-" + SecureTokens.randomReference(4);
        } while (promoterRepository.existsByReferralCode(code));
        return code;
    }
}
