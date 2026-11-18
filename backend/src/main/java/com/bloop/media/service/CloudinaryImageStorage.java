package com.bloop.media.service;

import com.bloop.media.entity.MediaProvider;
import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Sube y borra imagenes en Cloudinary con su API REST firmada (https://cloudinary.com/documentation/image_upload_api_reference).
 *
 * Por que Cloudinary: plan gratuito sin tarjeta, CDN incluido, URLs https publicas y una API
 * de dos llamadas que se puede leer entera aqui. El secreto (MEDIA_CLOUDINARY_API_SECRET) nunca
 * sale del backend: el navegador sube al backend, que valida el fichero y despues lo envia firmado.
 *
 * Firma: SHA-1 de los parametros ordenados ("overwrite=false&public_id=...&timestamp=...") + secreto.
 */
public class CloudinaryImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryImageStorage.class);

    private final BloopProperties.Media.Cloudinary config;
    private final String folder;
    private final RestClient restClient;
    private final Clock clock;

    public CloudinaryImageStorage(BloopProperties.Media media, Clock clock) {
        this.config = media.cloudinary();
        this.folder = media.folder();
        this.clock = clock;
        if (isBlank(config.cloudName()) || isBlank(config.apiKey()) || isBlank(config.apiSecret())) {
            throw new IllegalStateException("MEDIA_PROVIDER=cloudinary necesita MEDIA_CLOUDINARY_CLOUD_NAME, "
                    + "MEDIA_CLOUDINARY_API_KEY y MEDIA_CLOUDINARY_API_SECRET");
        }
        this.restClient = RestClient.builder().baseUrl(config.apiBaseUrl() + "/v1_1/" + config.cloudName()).build();
        log.info("Imagenes en Cloudinary (cloud '{}', carpeta '{}')", config.cloudName(), folder);
    }

    @Override
    public MediaProvider provider() {
        return MediaProvider.CLOUDINARY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Stored store(String key, byte[] bytes, String contentType) {
        // Cloudinary anade la extension por su cuenta: el public_id va sin ella.
        String publicId = folder + "/" + key.replaceAll("\\.[a-z]+$", "");
        Map<String, String> params = new TreeMap<>();
        params.put("overwrite", "false");
        params.put("public_id", publicId);
        params.put("timestamp", Long.toString(clock.instant().getEpochSecond()));

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        params.forEach(form::add);
        form.add("api_key", config.apiKey());
        form.add("signature", sign(params));
        form.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return key.substring(key.lastIndexOf('/') + 1);
            }
        });
        try {
            Map<String, Object> response = restClient.post().uri("/image/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(Map.class);
            if (response == null || response.get("secure_url") == null) {
                throw new BadRequestException("Cloudinary no ha devuelto la URL de la imagen");
            }
            return new Stored((String) response.get("public_id"), (String) response.get("secure_url"));
        } catch (RestClientException e) {
            log.warn("Cloudinary rechazo la subida: {}", e.getMessage());
            throw new BadRequestException("El almacenamiento de imagenes ha rechazado la subida. Revisa las "
                    + "credenciales de Cloudinary.");
        }
    }

    @Override
    public void delete(String publicId) {
        Map<String, String> params = new TreeMap<>();
        params.put("public_id", publicId);
        params.put("timestamp", Long.toString(clock.instant().getEpochSecond()));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach(form::add);
        form.add("api_key", config.apiKey());
        form.add("signature", sign(params));
        try {
            restClient.post().uri("/image/destroy").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            // La fila de la BD se borra igualmente; el fichero huerfano se puede limpiar desde Cloudinary.
            log.warn("No se pudo borrar {} en Cloudinary: {}", publicId, e.getMessage());
        }
    }

    String sign(Map<String, String> sortedParams) {
        String toSign = sortedParams.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&")) + config.apiSecret();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(toSign.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
