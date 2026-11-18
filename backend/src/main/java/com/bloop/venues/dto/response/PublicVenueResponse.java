package com.bloop.venues.dto.response;

/** Datos de un local visibles en la web publica (sin ids internos ni datos de la empresa). */
public record PublicVenueResponse(String name, String slug, String description, String addressLine, String city,
                                  String postalCode, String country, String phone, String email,
                                  String openingHours, String imageUrl) {
}
