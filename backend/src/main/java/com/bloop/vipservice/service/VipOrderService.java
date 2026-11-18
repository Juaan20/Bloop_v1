package com.bloop.vipservice.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.pos.entity.CategoryKind;
import com.bloop.pos.entity.Product;
import com.bloop.pos.entity.ProductVariant;
import com.bloop.pos.repository.ProductRepository;
import com.bloop.pos.repository.ProductVariantRepository;
import com.bloop.reservations.entity.Reservation;
import com.bloop.reservations.entity.ReservationPackageItem;
import com.bloop.reservations.entity.ReservationStatus;
import com.bloop.reservations.mapper.ReservationMapper;
import com.bloop.reservations.repository.ReservationRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUser;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import com.bloop.venues.entity.Venue;
import com.bloop.venues.repository.VenueRepository;
import com.bloop.venues.service.VenueService;
import com.bloop.vipservice.dto.request.VipOrderRequest;
import com.bloop.vipservice.dto.response.GuestTableResponse;
import com.bloop.vipservice.dto.response.VipOrderResponse;
import com.bloop.vipservice.entity.VipOrder;
import com.bloop.vipservice.entity.VipOrderItem;
import com.bloop.vipservice.entity.VipOrderSource;
import com.bloop.vipservice.entity.VipOrderStatus;
import com.bloop.vipservice.mapper.VipServiceMapper;
import com.bloop.vipservice.repository.VipAccountPaymentRepository;
import com.bloop.vipservice.repository.VipOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pedidos de las mesas VIP:
 * - El grupo pide desde el movil con el QR de su mesa (sin cuenta ni app).
 * - El personal de servicio los ve en su panel y los avanza de estado con un toque.
 * Lo que sale del paquete ya pagado va a 0 EUR; el resto queda como consumicion pendiente de pago.
 */
@Service
@RequiredArgsConstructor
public class VipOrderService {

    /** Categorias que se pueden pedir desde la mesa. */
    private static final Set<CategoryKind> ORDERABLE_KINDS = EnumSet.of(CategoryKind.VIP_BOTTLE, CategoryKind.SERVICE);
    /** El QR sigue abriendo la carta (solo lectura) un rato despues de la hora de fin. */
    private static final Duration GRACE_AFTER_END = Duration.ofHours(2);
    /** Limite de pedidos abiertos por mesa (evita abusos desde un QR filtrado). */
    private static final int MAX_OPEN_ORDERS = 15;
    /** El panel muestra tambien lo terminado en las ultimas horas (historial de la noche). */
    private static final Duration BOARD_HISTORY = Duration.ofHours(8);

    private final ReservationRepository reservationRepository;
    private final VipOrderRepository orderRepository;
    private final VipAccountPaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final VenueRepository venueRepository;
    private final VenueService venueService;
    private final UserRepository userRepository;
    private final VipServiceMapper mapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final Clock clock;

    // ----------------------------------------------------------------- Pagina publica de la mesa (QR)

    @Transactional(readOnly = true)
    public GuestTableResponse guestView(String token) {
        Reservation r = resolveToken(token);
        List<VipOrder> orders = orderRepository.findByReservation(r.getId());
        Map<UUID, Integer> remaining = VipAccountCalculator.remainingInPackage(r, orders);
        BigDecimal extras = VipAccountCalculator.extrasTotal(orders);
        BigDecimal pending = extras.subtract(paymentRepository.sumByReservation(r.getId())).max(BigDecimal.ZERO);
        String venueName = venueRepository.findById(r.getVenueId()).map(Venue::getName).orElse("");
        String firstName = r.getContactName().trim().split("\\s+")[0];
        return new GuestTableResponse(venueName, r.getTable().getZone().getName(), r.getTable().getName(),
                r.getSession() == null ? null : r.getSession().getEvent().getName(), r.getStartsAt(), r.getEndsAt(),
                firstName, cannotOrderReason(r) == null, cannotOrderReason(r),
                VipAccountCalculator.account(r, orders, List.of()).packageItems(), menu(r, remaining),
                orders.stream().map(mapper::toResponse).toList(), extras, pending);
    }

    @Transactional
    public VipOrderResponse guestOrder(String token, VipOrderRequest request) {
        Reservation r = resolveToken(token);
        String reason = cannotOrderReason(r);
        if (reason != null) {
            throw new BadRequestException(reason);
        }
        return create(r.getId(), r.getTenantId(), request, VipOrderSource.GUEST);
    }

    // ----------------------------------------------------------------- Personal

    /** Pedido anotado por el personal para una mesa (p. ej. si el cliente lo pide de palabra). */
    @Transactional
    public VipOrderResponse staffOrder(UUID reservationId, VipOrderRequest request) {
        UUID tenantId = currentUserProvider.requireTenantId();
        Reservation r = reservationRepository.findByIdAndTenantId(reservationId, tenantId)
                .orElseThrow(() -> NotFoundException.of("Reserva"));
        if (r.getStatus() != ReservationStatus.SEATED) {
            throw new BadRequestException("Solo se piden productos para un grupo que ya esta en la mesa");
        }
        return create(r.getId(), tenantId, request, VipOrderSource.STAFF);
    }

    /** Panel de servicio de un local: pedidos abiertos y terminados recientemente. */
    @Transactional(readOnly = true)
    public List<VipOrderResponse> board(UUID venueId) {
        Venue venue = venueService.findInMyTenant(venueId);
        return orderRepository.findBoard(venue.getTenantId(), venue.getId(), VipOrderStatus.OPEN,
                        Instant.now(clock).minus(BOARD_HISTORY))
                .stream().map(mapper::toResponse).toList();
    }

    /** Avanza (o cancela) un pedido. Bloquea la fila: dos tablets no pueden pisarse el cambio. */
    @Transactional
    public VipOrderResponse changeStatus(UUID orderId, VipOrderStatus target) {
        CurrentUser user = currentUserProvider.get();
        VipOrder order = orderRepository.findByIdForUpdate(orderId, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Pedido"));
        if (!order.getStatus().canChangeTo(target)) {
            throw new BadRequestException("El pedido ya no esta en un estado que permita ese cambio");
        }
        Instant now = Instant.now(clock);
        String name = userRepository.findById(user.userId()).map(User::getFullName).orElse(user.email());
        switch (target) {
            case PENDING -> order.setAcknowledgedAt(now);
            case PREPARING -> {
                order.setPreparingAt(now);
                order.setPreparedById(user.userId());
                order.setPreparedByName(name);
            }
            case READY -> order.setReadyAt(now);
            case ON_THE_WAY -> order.setOnTheWayAt(now);
            case DELIVERED -> {
                order.setDeliveredAt(now);
                order.setDeliveredById(user.userId());
                order.setDeliveredByName(name);
            }
            case CANCELLED -> order.setCancelledAt(now);
            case NEW -> throw new BadRequestException("Un pedido no puede volver a nuevo");
        }
        order.setStatus(target);
        auditService.record(AuditAction.VIP_ORDER_STATUS_CHANGED, "VipOrder", orderId, target.name());
        return mapper.toResponse(order);
    }

    // ----------------------------------------------------------------- Alta de pedidos

    /** Pedido cargado a la mesa desde un terminal de servicio VIP del TPV (ver PosVipController). */
    @Transactional
    public VipOrderResponse chargeFromTerminal(UUID reservationId, UUID tenantId, UUID venueId, VipOrderRequest request) {
        Reservation r = reservationRepository.findByIdAndTenantId(reservationId, tenantId)
                .orElseThrow(() -> NotFoundException.of("Reserva"));
        if (!r.getVenueId().equals(venueId)) {
            throw new BadRequestException("La mesa es de otro local");
        }
        if (r.getStatus() != ReservationStatus.SEATED) {
            throw new BadRequestException("Solo se carga a mesas con el grupo ya sentado");
        }
        return create(r.getId(), tenantId, request, VipOrderSource.STAFF);
    }

    private VipOrderResponse create(UUID reservationId, UUID tenantId, VipOrderRequest request, VipOrderSource source) {
        // Bloqueo de la reserva: dos pedidos simultaneos no pueden gastar dos veces el mismo paquete.
        Reservation r = reservationRepository.findByIdForUpdate(reservationId, tenantId)
                .orElseThrow(() -> NotFoundException.of("Reserva"));
        var existing = orderRepository.findByReservationIdAndIdempotencyKey(r.getId(), request.idempotencyKey());
        if (existing.isPresent()) {
            return mapper.toResponse(existing.get());
        }
        List<VipOrder> orders = orderRepository.findByReservation(r.getId());
        if (VipAccountCalculator.openOrders(orders) >= MAX_OPEN_ORDERS) {
            throw new BadRequestException("Hay demasiados pedidos pendientes en esta mesa: espera a que lleguen");
        }
        Set<UUID> ids = request.lines().stream().map(VipOrderRequest.Line::variantId).collect(Collectors.toSet());
        if (ids.size() != request.lines().size()) {
            throw new BadRequestException("Hay productos repetidos en el pedido");
        }
        Map<UUID, ProductVariant> variants = variantRepository.findAllWithProduct(ids, tenantId).stream()
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));
        Set<UUID> packageVariants = r.getPackageItems().stream().map(ReservationPackageItem::getVariantId)
                .collect(Collectors.toSet());
        Map<UUID, Integer> remaining = VipAccountCalculator.remainingInPackage(r, orders);

        VipOrder order = new VipOrder();
        order.setTenantId(tenantId);
        order.setVenueId(r.getVenueId());
        order.setReservation(r);
        order.setSource(source);
        order.setIdempotencyKey(request.idempotencyKey());
        order.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        for (VipOrderRequest.Line line : request.lines()) {
            ProductVariant v = variants.get(line.variantId());
            boolean orderable = v != null && v.isSellable()
                    && (ORDERABLE_KINDS.contains(v.getProduct().getCategory().getKind())
                    || packageVariants.contains(v.getId()));
            if (!orderable) {
                throw new BadRequestException("Producto no disponible en la carta VIP");
            }
            int[] split = VipAccountCalculator.split(line.quantity(), remaining.getOrDefault(v.getId(), 0));
            if (split[0] > 0) {
                order.addItem(item(v, split[0], true));
            }
            if (split[1] > 0) {
                order.addItem(item(v, split[1], false));
            }
        }
        order = orderRepository.save(order);
        auditService.recordInTenant(tenantId, AuditAction.VIP_ORDER_CREATED, "VipOrder", order.getId(),
                r.getReference() + " " + source + " " + order.getItems().size() + " lineas, adicional "
                        + order.extraTotal());
        return mapper.toResponse(order);
    }

    private static VipOrderItem item(ProductVariant v, int quantity, boolean included) {
        VipOrderItem i = new VipOrderItem();
        i.setVariantId(v.getId());
        i.setProductName(ReservationMapper.variantLabel(v));
        i.setQuantity(quantity);
        i.setUnitPrice(included ? BigDecimal.ZERO : v.getPrice());
        i.setIncluded(included);
        i.setLineTotal(included ? BigDecimal.ZERO : v.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return i;
    }

    // ----------------------------------------------------------------- Auxiliares

    /**
     * El token solo vale si esta activo, la reserva sigue en curso (confirmada o en la mesa) y no ha
     * pasado mucho tiempo desde la hora de fin. En cualquier otro caso: 404 generico (no se revela nada).
     */
    private Reservation resolveToken(String token) {
        if (token == null || token.length() < 20 || token.length() > 64) {
            throw NotFoundException.of("Mesa");
        }
        Reservation r = reservationRepository.findByTableToken(token).orElseThrow(() -> NotFoundException.of("Mesa"));
        boolean live = r.isTableTokenActive()
                && (r.getStatus() == ReservationStatus.CONFIRMED || r.getStatus() == ReservationStatus.SEATED)
                && Instant.now(clock).isBefore(r.getEndsAt().plus(GRACE_AFTER_END));
        if (!live) {
            throw NotFoundException.of("Mesa");
        }
        return r;
    }

    /** null si se puede pedir; si no, el motivo para mostrarlo en el movil. */
    private String cannotOrderReason(Reservation r) {
        if (!r.isPrepaid()) {
            return "La reserva aun no esta pagada. Habla con el personal de la entrada.";
        }
        if (r.getStatus() != ReservationStatus.SEATED) {
            return "Podras pedir cuando el personal os haya acomodado en la mesa.";
        }
        if (Instant.now(clock).isAfter(r.getEndsAt())) {
            return "La reserva ha terminado.";
        }
        return null;
    }

    /** Carta de la mesa: botellas VIP y productos de servicio, mas lo incluido en su paquete. */
    private List<GuestTableResponse.MenuCategory> menu(Reservation r, Map<UUID, Integer> remaining) {
        Set<UUID> packageVariants = remaining.keySet();
        Map<String, List<GuestTableResponse.MenuItem>> byCategory = new LinkedHashMap<>();
        for (Product p : productRepository.findAllOfTenant(r.getTenantId())) {
            if (!p.isActive() || !p.getCategory().isActive()) {
                continue;
            }
            boolean orderableCategory = ORDERABLE_KINDS.contains(p.getCategory().getKind());
            for (ProductVariant v : p.getVariants()) {
                if (!v.isActive() || !(orderableCategory || packageVariants.contains(v.getId()))) {
                    continue;
                }
                byCategory.computeIfAbsent(p.getCategory().getName(), k -> new ArrayList<>())
                        .add(new GuestTableResponse.MenuItem(v.getId(), p.getName(), v.getName(), p.getDescription(),
                                p.getImageUrl(), v.getPrice(), remaining.getOrDefault(v.getId(), 0)));
            }
        }
        return byCategory.entrySet().stream()
                .map(e -> new GuestTableResponse.MenuCategory(e.getKey(), e.getValue())).toList();
    }
}
