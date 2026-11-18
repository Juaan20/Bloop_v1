package com.bloop.vipservice.mapper;

import com.bloop.vipservice.dto.response.VipOrderResponse;
import com.bloop.vipservice.entity.VipOrder;
import org.springframework.stereotype.Component;

/** Convierte pedidos VIP a DTO. Se llama dentro de una transaccion (la reserva y la mesa son LAZY). */
@Component
public class VipServiceMapper {

    public VipOrderResponse toResponse(VipOrder o) {
        var table = o.getReservation().getTable();
        var items = o.getItems().stream()
                .map(i -> new VipOrderResponse.Item(i.getVariantId(), i.getProductName(), i.getQuantity(),
                        i.getUnitPrice(), i.isIncluded(), i.getLineTotal()))
                .toList();
        return new VipOrderResponse(o.getId(), o.getReservation().getId(), table.getName(), table.getZone().getName(),
                o.getStatus(), o.getSource(), o.getNotes(), o.getCreatedAt(), o.getAcknowledgedAt(),
                o.getPreparingAt(), o.getPreparedByName(), o.getReadyAt(), o.getOnTheWayAt(), o.getDeliveredAt(),
                o.getDeliveredByName(), o.getCancelledAt(), o.extraTotal(), items);
    }
}
