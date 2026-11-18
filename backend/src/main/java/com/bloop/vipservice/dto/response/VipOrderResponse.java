package com.bloop.vipservice.dto.response;

import com.bloop.vipservice.entity.VipOrderSource;
import com.bloop.vipservice.entity.VipOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pedido de mesa VIP (panel de servicio, ficha de la mesa y pagina del QR). */
public record VipOrderResponse(UUID id, UUID reservationId, String tableName, String zoneName,
                               VipOrderStatus status, VipOrderSource source, String notes, Instant createdAt,
                               Instant acknowledgedAt, Instant preparingAt, String preparedByName, Instant readyAt,
                               Instant onTheWayAt, Instant deliveredAt, String deliveredByName, Instant cancelledAt,
                               BigDecimal extraTotal, List<Item> items) {

    public record Item(UUID variantId, String productName, int quantity, BigDecimal unitPrice, boolean included,
                       BigDecimal lineTotal) {
    }
}
