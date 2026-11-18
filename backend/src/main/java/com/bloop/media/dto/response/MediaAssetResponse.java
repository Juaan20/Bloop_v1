package com.bloop.media.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Imagen de la biblioteca de la empresa (para elegirla y reutilizarla). */
public record MediaAssetResponse(UUID id, String url, String contentType, int sizeBytes, Integer width,
                                 Integer height, String originalName, Instant createdAt) {
}
