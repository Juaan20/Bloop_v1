package com.bloop.vipservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pagina publica de la mesa (QR). No incluye email, telefono ni datos de pago:
 * solo lo que el grupo necesita para consultar la carta y pedir.
 */
public record GuestTableResponse(String venueName, String zoneName, String tableName, String eventName,
                                 Instant startsAt, Instant endsAt, String hostFirstName, boolean canOrder,
                                 String cannotOrderReason, List<VipAccountResponse.PackageLine> packageItems,
                                 List<MenuCategory> menu, List<VipOrderResponse> orders, BigDecimal extrasTotal,
                                 BigDecimal pendingTotal) {

    public record MenuCategory(String name, List<MenuItem> items) {
    }

    public record MenuItem(UUID variantId, String productName, String variantName, String description,
                           String imageUrl, BigDecimal price, int remainingInPackage) {
    }
}
