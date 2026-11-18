package com.bloop.orders.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(UUID ticketTypeId, String ticketTypeName, int quantity, BigDecimal unitPrice,
                                BigDecimal lineTotal) {
}
