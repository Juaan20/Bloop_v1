package com.bloop.media.service;

import com.bloop.media.entity.MediaProvider;
import com.bloop.shared.config.BloopProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Guarda las imagenes en una carpeta del disco FUERA del proyecto (MEDIA_LOCAL_DIR).
 * Pensado para desarrollo: no requiere cuenta en ningun proveedor. Con Docker, esa carpeta es un
 * volumen, asi que no se pierde al reconstruir la imagen. Para produccion usa Cloudinary.
 *
 * Los ficheros se sirven en /api/public/media/{key} (PublicMediaController).
 */
public class LocalImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalImageStorage.class);

    private final Path root;
    private final String publicBaseUrl;

    public LocalImageStorage(BloopProperties.Media config) {
        this.root = Path.of(config.localDir()).toAbsolutePath().normalize();
        this.publicBaseUrl = config.publicBaseUrl().replaceAll("/+$", "");
        log.info("Imagenes en disco local: {} (solo desarrollo; en produccion usa MEDIA_PROVIDER=cloudinary)", root);
    }

    @Override
    public MediaProvider provider() {
        return MediaProvider.LOCAL;
    }

    @Override
    public Stored store(String key, byte[] bytes, String contentType) {
        Path target = resolve(key).orElseThrow(() -> new IllegalArgumentException("Clave no valida"));
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen", e);
        }
        return new Stored(key, publicBaseUrl + "/api/public/media/" + key);
    }

    @Override
    public void delete(String key) {
        resolve(key).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("No se pudo borrar {}: {}", path, e.getMessage());
            }
        });
    }

    /**
     * Ruta del fichero de una clave, o vacio si la clave intenta salir de la carpeta
     * ("../../etc/passwd") o tiene caracteres no permitidos.
     */
    public Optional<Path> resolve(String key) {
        if (key == null || !key.matches("[a-f0-9\\-]{36}/[a-f0-9\\-]{36}\\.(jpg|png|webp)")) {
            return Optional.empty();
        }
        Path path = root.resolve(key).normalize();
        return path.startsWith(root) ? Optional.of(path) : Optional.empty();
    }
}
