package com.bloop.media.controller;

import com.bloop.media.service.LocalMediaFiles;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Sirve las imagenes publicas guardadas en disco (solo MEDIA_PROVIDER=local; con Cloudinary las
 * sirve su CDN). nosniff impide que el navegador interprete el fichero como otra cosa.
 */
@RestController
@RequiredArgsConstructor
public class PublicMediaController {

    private final LocalMediaFiles localMediaFiles;

    @GetMapping("/api/public/media/{tenant}/{file}")
    public ResponseEntity<Resource> get(@PathVariable String tenant, @PathVariable String file) {
        LocalMediaFiles.LocalFile found = localMediaFiles.find(tenant + "/" + file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(found.contentType()))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .body(new FileSystemResource(found.path()));
    }
}
