package com.bloop.orders.service;

import java.util.UUID;

/** Se publica al reembolsar un pedido; OrderNotificationListener envia el email tras el commit. */
public record OrderRefundedEvent(UUID orderId) {
}
