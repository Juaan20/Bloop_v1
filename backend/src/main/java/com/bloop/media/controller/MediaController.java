package com.bloop.media.controller;

import com.bloop.media.dto.response.MediaAssetResponse;
import com.bloop.media.service.MediaService;
import com.bloop.shared.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Biblioteca de imagenes de la empresa (subir, reutilizar, borrar). Exige MEDIA_MANAGE. */
@RestController
@RequestMapping("/api/media")
@PreAuthorize("hasAuthority('MEDIA_MANAGE')")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediaAssetResponse upload(@RequestPart("file") MultipartFile file) {
        return mediaService.upload(file);
    }

    @GetMapping
    public PageResponse<MediaAssetResponse> list(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "24") int size) {
        return mediaService.list(page, size);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        mediaService.delete(id);
    }
}
