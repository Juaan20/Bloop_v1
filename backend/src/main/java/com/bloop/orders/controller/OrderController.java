package com.bloop.orders.controller;

import com.bloop.events.entity.TicketCategory;
import com.bloop.orders.dto.response.OrderSummaryResponse;
import com.bloop.orders.service.OrderSearchCriteria;
import com.bloop.payments.entity.PaymentProviderType;
import com.bloop.payments.entity.PaymentStatus;
import java.time.Instant;

import com.bloop.orders.dto.request.RefundOrderRequest;
import com.bloop.orders.dto.response.OrderDetailResponse;
import com.bloop.orders.dto.response.OrderResponse;
import com.bloop.orders.entity.OrderStatus;
import com.bloop.orders.service.OrderQueryService;
import com.bloop.orders.service.OrderRefundService;
import com.bloop.orders.service.OrderResendService;
import com.bloop.shared.response.PageResponse;
import com.bloop.ticketing.dto.request.ChangeHolderRequest;
import com.bloop.ticketing.service.TicketTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Ventas de la empresa (backoffice). Consultar exige ORDER_VIEW; reenviar entradas y
 * cambiar titulares exige ORDER_MANAGE (la anotacion del metodo sustituye a la de la clase).
 */
@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasAuthority('ORDER_VIEW')")
@RequiredArgsConstructor
public class OrderController {

    private final OrderQueryService orderQueryService;
    private final OrderResendService orderResendService;
    private final TicketTransferService ticketTransferService;
    private final OrderRefundService orderRefundService;

    /** Listado con filtros combinables. from/to en ISO-8601 (UTC). sort: recent | oldest | amount_desc | amount_asc. */
    @GetMapping
    public PageResponse<OrderResponse> list(@RequestParam(required = false) OrderStatus status,
                                            @RequestParam(required = false) UUID eventId,
                                            @RequestParam(required = false) UUID sessionId,
                                            @RequestParam(required = false) UUID venueId,
                                            @RequestParam(required = false) Instant from,
                                            @RequestParam(required = false) Instant to,
                                            @RequestParam(required = false) PaymentStatus paymentStatus,
                                            @RequestParam(required = false) PaymentProviderType paymentProvider,
                                            @RequestParam(required = false) TicketCategory ticketCategory,
                                            @RequestParam(required = false) String search,
                                            @RequestParam(required = false) String sort,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return orderQueryService.listForTenant(new OrderSearchCriteria(status, eventId, sessionId, venueId, from, to,
                paymentStatus, paymentProvider, ticketCategory, search), sort, page, size);
    }

    /** Totales de los pedidos que cumplen los mismos filtros que el listado. */
    @GetMapping("/summary")
    public OrderSummaryResponse summary(@RequestParam(required = false) OrderStatus status,
                                            @RequestParam(required = false) UUID eventId,
                                            @RequestParam(required = false) UUID sessionId,
                                            @RequestParam(required = false) UUID venueId,
                                            @RequestParam(required = false) Instant from,
                                            @RequestParam(required = false) Instant to,
                                            @RequestParam(required = false) PaymentStatus paymentStatus,
                                            @RequestParam(required = false) PaymentProviderType paymentProvider,
                                            @RequestParam(required = false) TicketCategory ticketCategory,
                                            @RequestParam(required = false) String search) {
        return orderQueryService.summaryForTenant(new OrderSearchCriteria(status, eventId, sessionId, venueId, from,
                to, paymentStatus, paymentProvider, ticketCategory, search));
    }

    /** Reembolsa un pedido pagado: devuelve el dinero, anula sus entradas y libera el stock. */
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('ORDER_REFUND')")
    public OrderResponse refund(@PathVariable UUID id,
                                @Valid @RequestBody RefundOrderRequest request) {
        return orderRefundService.refundAsStaff(id, request.reason());
    }

    @GetMapping("/{id}")
    public OrderDetailResponse get(@PathVariable UUID id) {
        return orderQueryService.getForTenant(id);
    }

    @PostMapping("/{id}/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    public void resend(@PathVariable UUID id) {
        orderResendService.resendForTenant(id);
    }

    @PutMapping("/{orderId}/tickets/{ticketId}/holder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    public void changeHolder(@PathVariable UUID orderId, @PathVariable UUID ticketId,
                             @Valid @RequestBody ChangeHolderRequest request) {
        ticketTransferService.renameByStaff(orderId, ticketId, request);
    }
}
