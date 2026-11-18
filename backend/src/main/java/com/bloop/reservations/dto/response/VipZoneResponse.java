package com.bloop.reservations.dto.response;

import com.bloop.reservations.entity.VipTableShape;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Zona VIP con sus mesas (configuracion y plano del local en el backoffice). */
public record VipZoneResponse(UUID id, UUID venueId, String name, String description, boolean active, int sortOrder,
                              List<Table> tables, Integer capacity, String color,
                              com.bloop.reservations.dto.request.FloorPlanRequest layout) {

    public record Table(UUID id, String name, int capacity, BigDecimal minSpend, BigDecimal depositAmount,
                        boolean active, BigDecimal posX, BigDecimal posY, BigDecimal width, BigDecimal height,
                        VipTableShape shape, List<PackageItem> packageItems) {
    }

    public record PackageItem(UUID variantId, String productName, int quantity, BigDecimal unitPrice) {
    }
}
