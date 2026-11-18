package com.bloop.media.service;

import com.bloop.media.dto.response.MediaAssetResponse;
import com.bloop.media.entity.MediaAsset;
import com.bloop.media.repository.MediaAssetRepository;
import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.response.PageResponse;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUser;
import com.bloop.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Biblioteca de imagenes de cada empresa: subir, listar (para reutilizar) y borrar.
 *
 * Seguridad:
 * - El fichero se valida por su contenido real (ImageValidator) y con un tamano maximo.
 * - El nombre en el almacenamiento lo genera el servidor ({tenant}/{uuid}.{ext}): el nombre original
 *   no se usa para guardar, asi que no hay colisiones ni rutas peligrosas.
 * - Todas las consultas filtran por la empresa del usuario: no se puede ver ni borrar la imagen de otra.
 */
@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaAssetRepository repository;
    private final ImageStorage storage;
    private final CurrentUserProvider currentUserProvider;
    private final BloopProperties properties;
    private final Clock clock;

    @Transactional
    public MediaAssetResponse upload(MultipartFile file) {
        CurrentUser user = currentUserProvider.get();
        UUID tenantId = currentUserProvider.requireTenantId();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer el fichero");
        }
        ImageValidator.Detected type = ImageValidator.validate(bytes, properties.media().maxBytes());

        String key = tenantId + "/" + UUID.randomUUID() + "." + type.extension();
        ImageStorage.Stored stored = storage.store(key, bytes, type.contentType());

        MediaAsset asset = new MediaAsset();
        asset.setTenantId(tenantId);
        asset.setProvider(storage.provider());
        asset.setStorageKey(stored.key());
        asset.setUrl(stored.url());
        asset.setContentType(type.contentType());
        asset.setSizeBytes(bytes.length);
        asset.setWidth(type.width());
        asset.setHeight(type.height());
        asset.setOriginalName(cleanName(file.getOriginalFilename()));
        asset.setUploadedBy(user.userId());
        asset.setCreatedAt(Instant.now(clock));
        return toResponse(repository.save(asset));
    }

    @Transactional(readOnly = true)
    public PageResponse<MediaAssetResponse> list(int page, int size) {
        var result = repository.findByTenantIdOrderByCreatedAtDesc(currentUserProvider.requireTenantId(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 60)));
        return PageResponse.of(result, this::toResponse);
    }

    /** Borra una imagen que ya no se usa. Si un evento, un dia, un producto o un local la usa: 409. */
    @Transactional
    public void delete(UUID id) {
        UUID tenantId = currentUserProvider.requireTenantId();
        MediaAsset asset = repository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> NotFoundException.of("Imagen"));
        long usages = repository.countUsages(tenantId, asset.getUrl());
        if (usages > 0) {
            throw new ConflictException("La imagen se esta usando en " + usages
                    + (usages == 1 ? " sitio" : " sitios") + ". Cambiala alli antes de borrarla.");
        }
        repository.delete(asset);
        if (asset.getProvider() == storage.provider()) {
            storage.delete(asset.getStorageKey());
        }
    }

    private MediaAssetResponse toResponse(MediaAsset a) {
        return new MediaAssetResponse(a.getId(), a.getUrl(), a.getContentType(), a.getSizeBytes(), a.getWidth(),
                a.getHeight(), a.getOriginalName(), a.getCreatedAt());
    }

    /** El nombre original solo se muestra en la biblioteca: se recorta y se quitan caracteres raros. */
    private static String cleanName(String name) {
        if (name == null) {
            return null;
        }
        String clean = name.replaceAll("[\\\\/]", "_").replaceAll("[^\\p{L}\\p{N} ._()-]", "").trim();
        return clean.isEmpty() ? null : clean.substring(0, Math.min(200, clean.length()));
    }
}
