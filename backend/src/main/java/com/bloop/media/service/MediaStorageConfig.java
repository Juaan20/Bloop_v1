package com.bloop.media.service;

import com.bloop.shared.config.BloopProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Elige donde se guardan las imagenes segun MEDIA_PROVIDER (local o cloudinary). */
@Configuration
public class MediaStorageConfig {

    @Bean
    public ImageStorage imageStorage(BloopProperties properties, Clock clock) {
        BloopProperties.Media media = properties.media();
        return switch (media.provider() == null ? "local" : media.provider().trim().toLowerCase()) {
            case "cloudinary" -> new CloudinaryImageStorage(media, clock);
            case "local" -> new LocalImageStorage(media);
            default -> throw new IllegalStateException("MEDIA_PROVIDER debe ser 'local' o 'cloudinary'");
        };
    }
}
