package com.bloop.orders.service;

import com.bloop.events.entity.Event;
import com.bloop.events.entity.EventSession;
import com.bloop.events.entity.EventStatus;
import com.bloop.events.entity.SaleStatus;
import com.bloop.events.entity.TicketType;
import com.bloop.events.repository.TicketTypeRepository;
import com.bloop.events.service.StockAlertService;
import com.bloop.orders.dto.request.CheckoutItemRequest;
import com.bloop.orders.dto.request.CheckoutRequest;
import com.bloop.orders.dto.response.OrderResponse;
import com.bloop.orders.dto.response.PaymentResultResponse;
import com.bloop.orders.entity.OrderStatus;
import com.bloop.orders.entity.TicketOrder;
import com.bloop.orders.entity.TicketOrderItem;
import com.bloop.orders.mapper.OrderMapper;
import com.bloop.orders.repository.TicketOrderRepository;
import com.bloop.payments.entity.Payment;
import com.bloop.payments.service.PaymentService;
import com.bloop.promoters.entity.PromoCode;
import com.bloop.promoters.entity.Promoter;
import com.bloop.promoters.service.PromoCodeService;
import com.bloop.promoters.service.PromoterService;
import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.ForbiddenException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.shared.util.SecureTokens;
import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import com.bloop.venues.entity.VenueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import com.bloop.orders.dto.request.AttendeeRequest;
import com.bloop.orders.entity.OrderAttendee;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Compra de entradas desde la web publica.
 *
 * Flujo:
 * 1. POST /api/checkout (con cabecera Idempotency-Key): valida el carrito, RESERVA el stock
 *    (y un uso del codigo promocional, si lo hay), aplica el descuento, atribuye la venta a un
 *    promotor y crea el pedido en PENDING_PAYMENT durante 15 minutos.
 * 2. POST /api/checkout/orders/{id}/simulated-payment: simula la pasarela. Si se aprueba,
 *    OrderPaymentService marca el pedido como PAID y emite las entradas con QR.
 * Si no se paga a tiempo, PendingOrderExpirationJob libera el stock.
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    static final int MAX_TICKETS_PER_ORDER = 20;
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,80}");

    private final TicketOrderRepository orderRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final OrderPaymentService orderPaymentService;
    private final PromoCodeService promoCodeService;
    private final PromoterService promoterService;
    private final StockAlertService stockAlertService;
    private final OrderMapper orderMapper;
    private final CurrentUserProvider currentUserProvider;
    private final BloopProperties properties;
    private final Clock clock;

    @Transactional
    public OrderResponse checkout(CheckoutRequest request, String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new BadRequestException("Cabecera Idempotency-Key ausente o no valida");
        }
        UUID customerId = currentUserProvider.get().userId();

        // 1) Idempotencia: si este checkout ya se proceso (doble clic, reintento de red...), devolvemos el mismo pedido.
        var existing = orderRepository.findByCustomerUserIdAndIdempotencyKey(customerId, idempotencyKey);
        if (existing.isPresent()) {
            return orderMapper.toResponse(existing.get());
        }

        User buyer = userRepository.findById(customerId).orElseThrow(() -> NotFoundException.of("Usuario"));
        Map<UUID, Integer> quantities = mergeQuantities(request.items());
        Instant now = Instant.now(clock);

        // 2) Validaciones de negocio sobre los tipos de entrada.
        List<TicketType> ticketTypes = new ArrayList<>(ticketTypeRepository.findAllWithSessionByIdIn(quantities.keySet()));
        if (ticketTypes.size() != quantities.size()) {
            throw NotFoundException.of("Tipo de entrada");
        }
        EventSession session = validateSameSellableSession(ticketTypes, request.sessionId(), now);
        for (TicketType type : ticketTypes) {
            validateSellable(type, quantities.get(type.getId()), now);
        }
        List<OrderAttendee> attendees = validateAttendees(request.attendees(), quantities, session);

        // 3) Reserva atomica de stock. Ordenamos por id para que dos compras concurrentes bloqueen
        //    las filas en el mismo orden y nunca se produzca un interbloqueo (deadlock).
        ticketTypes.sort(Comparator.comparing(TicketType::getId));
        for (TicketType type : ticketTypes) {
            int reserved = ticketTypeRepository.reserveStock(type.getId(), quantities.get(type.getId()));
            if (reserved == 0) {
                // La excepcion deshace la transaccion completa, incluidas las reservas anteriores.
                throw new ConflictException("No quedan suficientes entradas de tipo '" + type.getName() + "'");
            }
        }
        // Aviso al equipo si algun tipo de entrada se esta agotando.
        ticketTypes.forEach(stockAlertService::checkStock);

        // 4) Creacion del pedido con precios congelados.
        TicketOrder order = new TicketOrder();
        order.setTenantId(session.getTenantId());
        order.setReference(newReference());
        order.setCustomerUserId(customerId);
        order.setSession(session);
        order.setBuyerEmail(buyer.getEmail());
        order.setBuyerName(buyer.getFullName());
        order.setIdempotencyKey(idempotencyKey);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setExpiresAt(now.plus(properties.orders().pendingMinutes(), ChronoUnit.MINUTES));
        order.setMarketingConsent(request.marketingConsent());
        order.setBuyerPhone(request.buyerPhone() == null || request.buyerPhone().isBlank() ? null : request.buyerPhone().trim());
        attendees.forEach(order::addAttendee);
        BigDecimal subtotal = BigDecimal.ZERO;
        for (TicketType type : ticketTypes) {
            int quantity = quantities.get(type.getId());
            BigDecimal lineTotal = type.getPrice().multiply(BigDecimal.valueOf(quantity));
            TicketOrderItem item = new TicketOrderItem();
            item.setTicketType(type);
            item.setTicketTypeName(type.getName());
            item.setQuantity(quantity);
            item.setUnitPrice(type.getPrice());
            item.setLineTotal(lineTotal);
            order.addItem(item);
            subtotal = subtotal.add(lineTotal);
        }
        order.setSubtotalAmount(subtotal);
        order.setCurrency(ticketTypes.getFirst().getCurrency());

        // 5) Codigo promocional: consume un uso de forma atomica (como el stock) y aplica el descuento.
        UUID eventId = session.getEvent().getId();
        PromoCode promo = null;
        if (request.promoCode() != null && !request.promoCode().isBlank()) {
            promo = promoCodeService.resolveForCheckout(session.getTenantId(), eventId, request.promoCode(), now);
            promoCodeService.reserveUse(promo);
            order.setPromoCodeId(promo.getId());
            order.setPromoCode(promo.getCode());
            order.setDiscountAmount(promo.discountFor(subtotal));
        }
        BigDecimal total = subtotal.subtract(order.getDiscountAmount());
        order.setTotalAmount(total);

        // 6) Atribucion al promotor: primero el del codigo promocional, si no el del enlace ?rrpp=.
        Optional<Promoter> promoter = promo != null && promo.getPromoterId() != null
                ? promoterService.findActive(promo.getPromoterId())
                : promoterService.findForReferral(request.referralCode(), session.getTenantId(), eventId);
        if (promoter.isPresent()) {
            order.setPromoterId(promoter.get().getId());
            order.setCommissionAmount(promoter.get().commissionFor(total, order.getTicketCount()));
        }
        // saveAndFlush: si otra peticion con la misma Idempotency-Key se adelanta, la restriccion
        // UNIQUE salta aqui (409) y se deshace todo, incluida la reserva de stock y del codigo.
        order = orderRepository.saveAndFlush(order);

        // 7) Pedido gratuito (o descuento del 100 %): no hay nada que cobrar, se confirma directamente.
        if (total.signum() == 0) {
            orderPaymentService.completeFreeOrder(order);
        }
        return orderMapper.toResponse(order);
    }

    /**
     * Asistentes: uno por entrada y del tipo correcto. Si el evento tiene edad minima son obligatorios,
     * con fecha de nacimiento y documento, y se comprueba la edad el dia de la sesion (en el backend:
     * la web puede saltarse). El numero de documento nunca aparece en errores ni se guarda completo.
     */
    private List<OrderAttendee> validateAttendees(List<AttendeeRequest> requested, Map<UUID, Integer> quantities,
                                                  EventSession session) {
        Integer minAge = session.getEvent().getMinAge();
        List<AttendeeRequest> list = requested == null ? List.of() : requested;
        if (list.isEmpty()) {
            if (minAge != null && minAge > 0) {
                throw new BadRequestException("Este evento es para mayores de " + minAge
                        + " anos: indica los datos de cada asistente");
            }
            return List.of();
        }
        Map<UUID, Integer> perType = new HashMap<>();
        list.forEach(a -> perType.merge(a.ticketTypeId(), 1, Integer::sum));
        if (!perType.equals(quantities)) {
            throw new BadRequestException("Indica un asistente por cada entrada");
        }
        List<OrderAttendee> result = new ArrayList<>();
        int position = 0;
        for (AttendeeRequest a : list) {
            position++;
            String who = "Asistente " + position;
            boolean hasDocument = a.documentType() != null && a.documentNumber() != null && !a.documentNumber().isBlank();
            if (hasDocument && !AttendeeRules.isValidDocument(a.documentType(), a.documentNumber())) {
                throw new BadRequestException(who + ": el " + a.documentType() + " no tiene un formato valido");
            }
            if (minAge != null && minAge > 0) {
                if (a.birthDate() == null || !hasDocument) {
                    throw new BadRequestException(who + ": fecha de nacimiento y documento obligatorios (evento +" + minAge + ")");
                }
                if (AttendeeRules.ageOn(a.birthDate(), session.getStartsAt()) < minAge) {
                    throw new BadRequestException(who + " no cumple la edad minima del evento (" + minAge + " anos)");
                }
            }
            OrderAttendee attendee = new OrderAttendee();
            attendee.setTicketTypeId(a.ticketTypeId());
            attendee.setPosition(position);
            attendee.setFirstName(a.firstName().trim());
            attendee.setLastName(a.lastName().trim());
            attendee.setBirthDate(a.birthDate());
            if (hasDocument) {
                attendee.setDocumentType(a.documentType());
                attendee.setDocumentLast4(AttendeeRules.last4(a.documentNumber()));
            }
            result.add(attendee);
        }
        return result;
    }

    /**
     * Simulador de pasarela (solo desarrollo). Bloquea el pedido para que dos clics simultaneos
     * no generen dos cobros, crea un intento de pago y lo confirma o lo rechaza.
     */
    @Transactional
    public PaymentResultResponse paySimulated(UUID orderId, boolean approve) {
        if (!properties.payments().simulatedEnabled()) {
            throw new ForbiddenException("El pago simulado no esta disponible en este entorno");
        }
        UUID customerId = currentUserProvider.get().userId();
        TicketOrder order = orderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.getCustomerUserId().equals(customerId))
                .orElseThrow(() -> NotFoundException.of("Pedido"));

        if (order.getStatus() == OrderStatus.PAID) {
            return new PaymentResultResponse(true, "El pedido ya estaba pagado", orderMapper.toResponse(order));
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("El pedido ya no admite pagos (estado " + order.getStatus() + ")");
        }
        if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(Instant.now(clock))) {
            throw new ConflictException("El tiempo para pagar ha terminado. Vuelve a seleccionar tus entradas");
        }

        Payment payment = paymentService.startSimulatedPayment(order.getTenantId(), order.getId(),
                order.getTotalAmount(), order.getCurrency(), order.getReference());
        if (approve) {
            orderPaymentService.confirmPayment(payment.getProviderPaymentId());
            return new PaymentResultResponse(true, "Pago aceptado", orderMapper.toResponse(order));
        }
        orderPaymentService.failPayment(payment.getProviderPaymentId(), "Pago rechazado por el banco (simulado)");
        return new PaymentResultResponse(false, "El pago ha sido rechazado. Puedes intentarlo de nuevo",
                orderMapper.toResponse(order));
    }

    private static Map<UUID, Integer> mergeQuantities(List<CheckoutItemRequest> items) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (CheckoutItemRequest item : items) {
            quantities.merge(item.ticketTypeId(), item.quantity(), Integer::sum);
        }
        int total = quantities.values().stream().mapToInt(Integer::intValue).sum();
        if (total > MAX_TICKETS_PER_ORDER) {
            throw new BadRequestException("Maximo " + MAX_TICKETS_PER_ORDER + " entradas por pedido");
        }
        return quantities;
    }

    private static EventSession validateSameSellableSession(List<TicketType> ticketTypes, UUID sessionId,
                                                            Instant now) {
        for (TicketType type : ticketTypes) {
            if (!type.getSession().getId().equals(sessionId)) {
                throw new BadRequestException("Todas las entradas deben ser de la misma sesion");
            }
        }
        EventSession session = ticketTypes.getFirst().getSession();
        Event event = session.getEvent();
        boolean onSale = event.getStatus() == EventStatus.PUBLISHED
                && event.getVenue().getStatus() == VenueStatus.ACTIVE
                && session.isUpcoming(now);
        if (!onSale) {
            throw new BadRequestException("Este evento no esta a la venta");
        }
        return session;
    }

    private static void validateSellable(TicketType type, int quantity, Instant now) {
        SaleStatus status = type.saleStatus(now);
        if (status == SaleStatus.SOLD_OUT) {
            throw new ConflictException("Entradas agotadas: " + type.getName());
        }
        if (status != SaleStatus.ON_SALE) {
            throw new BadRequestException("La entrada '" + type.getName() + "' no esta a la venta");
        }
        if (quantity > type.getMaxPerOrder()) {
            throw new BadRequestException("Maximo " + type.getMaxPerOrder() + " entradas '" + type.getName()
                    + "' por pedido");
        }
    }

    private String newReference() {
        String reference;
        do {
            reference = "VF-" + SecureTokens.randomReference(8);
        } while (orderRepository.existsByReference(reference));
        return reference;
    }
}
