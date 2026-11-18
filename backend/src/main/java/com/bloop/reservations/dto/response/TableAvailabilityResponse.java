package com.bloop.reservations.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Mesa reservable y si esta libre en el horario consultado. */
public record TableAvailabilityResponse(UUID tableId, String zoneName, String zoneDescription, String tableName,
                                        int capacity, BigDecimal minSpend, BigDecimal depositAmount,
                                        boolean available) {
}
