package com.bloop.media.entity;

/** Donde se guarda el fichero de una imagen. */
public enum MediaProvider {
    /** Cloudinary (recomendado en produccion): CDN, URLs publicas https y sin servidor propio. */
    CLOUDINARY,
    /** Carpeta local fuera del proyecto (solo desarrollo; con Docker, un volumen). */
    LOCAL
}
