package com.bloop.notifications.dto.response;

import java.util.List;

/** Ultimas alertas de la empresa y cuantas quedan sin leer (numero de la campana). */
public record AlertsResponse(long unreadCount, List<AdminAlertResponse> alerts) {
}
