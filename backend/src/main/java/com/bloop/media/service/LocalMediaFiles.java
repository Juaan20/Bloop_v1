package com.bloop.media.service;

import com.bloop.media.entity.MediaAsset;
import com.bloop.media.entity.MediaProvider;
import com.bloop.media.repository.MediaAssetRepository;
import com.bloop.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lectura de las imagenes guardadas en disco (MEDIA_PROVIDER=local). Solo se sirve un fichero si
 * existe su fila en media_assets: no se puede pedir cualquier fichero de la carpeta.
 */
@Service
@RequiredArgsConstructor
public class LocalMediaFiles {

    /** Fichero y su tipo, para que el controlador lo devuelva. */
    public record LocalFile(Path path, String contentType) {
    }

    private final MediaAssetRepository repository;
    private final ImageStorage storage;

    @Transactional(readOnly = true)
    public LocalFile find(String key) {
        if (!(storage instanceof LocalImageStorage local)) {
            throw NotFoundException.of("Imagen");
        }
        MediaAsset asset = repository.findByProviderAndStorageKey(MediaProvider.LOCAL, key)
                .orElseThrow(() -> NotFoundException.of("Imagen"));
        Path path = local.resolve(asset.getStorageKey()).filter(Files::isRegularFile)
                .orElseThrow(() -> NotFoundException.of("Imagen"));
        return new LocalFile(path, asset.getContentType());
    }
}
