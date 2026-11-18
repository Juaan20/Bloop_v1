package com.bloop.orders.service;

import com.bloop.orders.dto.response.OrderDetailResponse;
import com.bloop.orders.dto.response.OrderResponse;
import com.bloop.orders.entity.OrderStatus;
import com.bloop.orders.entity.TicketOrder;
import com.bloop.orders.mapper.OrderMapper;
import com.bloop.orders.repository.TicketOrderRepository;
import com.bloop.payments.service.PaymentService;
import com.bloop.promoters.repository.PromoterRepository;
import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.response.PageResponse;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.ticketing.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.bloop.orders.dto.response.OrderSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Consultas de pedidos: ventas de la empresa (backoffice) y compras del cliente (web). */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final TicketOrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final PaymentService paymentService;
    private final PromoterRepository promoterRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listForTenant(OrderSearchCriteria f, String sort, int page, int size) {
        UUID tenantId = currentUserProvider.requireTenantId();
        var result = orderRepository.search(tenantId, f.status(), f.eventId(), f.sessionId(), f.venueId(), f.from(),
                f.to(), f.paymentStatus(), f.paymentProvider(), f.ticketCategory(), f.search(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), sortOf(sort)));
        return PageResponse.of(result, orderMapper::toResponse);
    }

    /** Totales de todos los pedidos que cumplen los filtros (no solo de la pagina visible). */
    @Transactional(readOnly = true)
    public OrderSummaryResponse summaryForTenant(OrderSearchCriteria f) {
        UUID tenantId = currentUserProvider.requireTenantId();
        Object[] row = orderRepository.summarize(tenantId, f.status(), f.eventId(), f.sessionId(), f.venueId(),
                f.from(), f.to(), f.paymentStatus(), f.paymentProvider(), f.ticketCategory(), f.search()).getFirst();
        long tickets = orderRepository.countPaidTickets(tenantId, f.status(), f.eventId(), f.sessionId(), f.venueId(),
                f.from(), f.to(), f.paymentStatus(), f.paymentProvider(), f.ticketCategory(), f.search());
        return new OrderSummaryResponse(number(row[0]).longValue(), number(row[1]).longValue(),
                number(row[2]).longValue(), number(row[3]).longValue(), tickets, money(row[4]), money(row[5]),
                money(row[6]));
    }

    /** Orden permitido: recientes (por defecto), antiguos, importe mayor o menor. */
    private static Sort sortOf(String sort) {
        return switch (sort == null ? "" : sort) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "amount_desc" -> Sort.by(Sort.Direction.DESC, "totalAmount").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "amount_asc" -> Sort.by(Sort.Direction.ASC, "totalAmount").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    private static Number number(Object value) {
        return value == null ? 0 : (Number) value;
    }

    private static java.math.BigDecimal money(Object value) {
        return value == null ? java.math.BigDecimal.ZERO
                : new java.math.BigDecimal(value.toString()).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getForTenant(UUID id) {
        TicketOrder order = orderRepository.findByIdAndTenantId(id, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Pedido"));
        var payments = paymentService.findByOrder(order.getId()).stream()
                .map(p -> new OrderDetailResponse.PaymentInfo(p.getId(), p.getProvider(), p.getStatus(), p.getAmount(),
                        p.getFailureReason(), p.getCreatedAt()))
                .toList();
        var tickets = ticketRepository.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId()).stream()
                .map(t -> new OrderDetailResponse.TicketInfo(t.getId(), t.getTicketType().getName(), t.getHolderName(),
                        t.getStatus(), t.getUsedAt()))
                .toList();
        String promoterName = order.getPromoterId() == null ? null
                : promoterRepository.findById(order.getPromoterId())
                        .flatMap(p -> userRepository.findById(p.getUserId()))
                        .map(User::getFullName).orElse(null);
        return new OrderDetailResponse(orderMapper.toResponse(order), payments, tickets, promoterName,
                order.getCommissionAmount());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listMine() {
        return orderRepository.findAllOfCustomer(currentUserProvider.get().userId()).stream()
                .map(orderMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getMine(UUID id) {
        return orderRepository.findByIdAndCustomerUserId(id, currentUserProvider.get().userId())
                .map(orderMapper::toResponse)
                .orElseThrow(() -> NotFoundException.of("Pedido"));
    }
}
