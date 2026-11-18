package com.bloop.orders.service;

import java.util.UUID;

/**
 * Evento de aplicacion (Spring ApplicationEvent) que se publica al pagarse un pedido.
 * OrderNotificationListener lo recibe DESPUES del commit para enviar el email de confirmacion.
 */
public record OrderPaidEvent(UUID orderId) {
}
