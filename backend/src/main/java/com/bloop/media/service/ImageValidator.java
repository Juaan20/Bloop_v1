package com.bloop.media.service;

import com.bloop.shared.exception.BadRequestException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Comprueba que un fichero subido es de verdad una imagen permitida.
 *
 * - El tipo se decide por los primeros bytes ("firma" del formato), NO por la extension ni por el
 *   Content-Type que manda el navegador: un .php renombrado a .jpg se rechaza.
 * - Solo JPEG, PNG y WebP. SVG no se admite: puede llevar JavaScript dentro.
 * - Se leen solo las dimensiones de la cabecera (sin decodificar la imagen entera) y se limitan,
 *   para evitar "bombas" de pixeles que agoten la memoria.
 */
public final class ImageValidator {

    /** Formato detectado: tipo MIME y extension del fichero guardado. */
    public record Detected(String contentType, String extension, Integer width, Integer height) {
    }

    static final int MAX_DIMENSION = 8000;

    private ImageValidator() {
    }

    public static Detected validate(byte[] bytes, long maxBytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("El fichero esta vacio");
        }
        if (bytes.length > maxBytes) {
            throw new BadRequestException("La imagen supera el tamano maximo de " + (maxBytes / (1024 * 1024)) + " MB");
        }
        Detected type = detect(bytes);
        if (type == null) {
            throw new BadRequestException("Formato no permitido: sube una imagen JPG, PNG o WebP");
        }
        if (type.width() != null && (type.width() > MAX_DIMENSION || type.height() > MAX_DIMENSION)) {
            throw new BadRequestException("La imagen es demasiado grande (maximo " + MAX_DIMENSION + " px por lado)");
        }
        return type;
    }

    static Detected detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return withDimensions("image/jpeg", "jpg", b);
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return withDimensions("image/png", "png", b);
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            // Java no trae lector de WebP: aceptamos por firma y sin dimensiones.
            return new Detected("image/webp", "webp", null, null);
        }
        return null;
    }

    /** Lee ancho y alto de la cabecera. Si no se puede leer, el fichero no es una imagen valida. */
    private static Detected withDimensions(String contentType, String extension, byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new BadRequestException("La imagen esta danada o no se puede leer");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return new Detected(contentType, extension, reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BadRequestException("La imagen esta danada o no se puede leer");
        }
    }
}
