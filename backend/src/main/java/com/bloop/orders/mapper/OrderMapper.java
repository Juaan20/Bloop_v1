package com.bloop.orders.mapper;

import com.bloop.orders.dto.response.OrderItemResponse;
import com.bloop.orders.dto.response.OrderResponse;
import com.bloop.orders.entity.TicketOrder;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderResponse toResponse(TicketOrder order) {
        var session = order.getSession();
        var event = session.getEvent();
        var items = order.getItems().stream()
                .map(i -> new OrderItemResponse(i.getTicketType().getId(), i.getTicketTypeName(), i.getQuantity(),
                        i.getUnitPrice(), i.getLineTotal()))
                .toList();
        return new OrderResponse(order.getId(), order.getReference(), order.getStatus(), order.getSubtotalAmount(),
                order.getDiscountAmount(), order.getTotalAmount(), order.getCurrency(), order.getPromoCode(),
                event.getName(), event.getSlug(), event.getVenue().getName(), session.getId(), session.getStartsAt(), order.getBuyerName(), order.getBuyerEmail(), order.getTicketCount(), items,
                order.getExpiresAt(), order.getPaidAt(), order.getCreatedAt(),
                order.getRefundedAt(), order.getRefundReason(), session.getTitle());
    }
}
