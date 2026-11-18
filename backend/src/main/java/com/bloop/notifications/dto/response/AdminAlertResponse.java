package com.bloop.notifications.dto.response;

import com.bloop.notifications.entity.AlertType;

import java.time.Instant;
import java.util.UUID;

public record AdminAlertResponse(UUID id, AlertType type, String title, String message, String link,
                                 boolean read, Instant createdAt) {
}
