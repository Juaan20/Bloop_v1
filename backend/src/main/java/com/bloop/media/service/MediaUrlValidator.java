package com.bloop.media.service;

import com.bloop.media.repository.MediaAssetRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Comprueba que la imagen que se asigna a un evento, un dia o un producto es de la biblioteca
 * de la propia empresa. Asi nadie enlaza imagenes de otra empresa ni URLs externas arbitrarias.
 * Una URL que ya tenia guardada (de antes de existir la biblioteca) se acepta sin cambios.
 */
@Component
@RequiredArgsConstructor
public class MediaUrlValidator {

    private final MediaAssetRepository repository;
    private final CurrentUserProvider currentUserProvider;

    /** @return la URL limpia (o null si viene vacia) */
    public String requireOwnUrl(String url, String currentValue) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String clean = url.trim();
        if (Objects.equals(clean, currentValue)) {
            return clean;
        }
        if (!repository.existsByTenantIdAndUrl(currentUserProvider.requireTenantId(), clean)) {
            throw new BadRequestException("La imagen debe subirse desde el ordenador a la biblioteca de la empresa");
        }
        return clean;
    }
}
