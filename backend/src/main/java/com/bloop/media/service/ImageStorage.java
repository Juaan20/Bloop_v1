package com.bloop.media.service;

import com.bloop.media.entity.MediaProvider;

/**
 * Donde se guardan los ficheros de imagen. Hay dos implementaciones:
 * - CloudinaryImageStorage (MEDIA_PROVIDER=cloudinary): recomendada, CDN y sin servidor propio.
 * - LocalImageStorage (MEDIA_PROVIDER=local): carpeta fuera del proyecto, solo para desarrollo.
 */
public interface ImageStorage {

    /** Resultado de guardar: identificador en el proveedor y URL publica (https en Cloudinary). */
    record Stored(String key, String url) {
    }

    MediaProvider provider();

    /** @param key nombre unico y sin caracteres peligrosos, p. ej. "tenant/uuid.jpg" */
    Stored store(String key, byte[] bytes, String contentType);

    void delete(String key);
}
