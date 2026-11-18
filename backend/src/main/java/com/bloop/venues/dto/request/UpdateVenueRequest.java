package com.bloop.venues.dto.request;

import com.bloop.venues.entity.VenueStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Igual que la creacion, mas el estado. El slug no cambia para no romper enlaces publicos. */
public record UpdateVenueRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 4000) String description,
        @NotBlank @Size(max = 255) String addressLine,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 20) String postalCode,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "Codigo de pais de 2 letras (ES, PT...)") String country,
        @Size(max = 40) String phone,
        @Email @Size(max = 255) String email,
        @Min(1) @Max(100000) int capacity,
        @Size(max = 500) String openingHours,
        @Size(max = 500) @Pattern(regexp = "^$|https?://.+", message = "Debe ser una URL http(s)") String imageUrl,
        @NotNull VenueStatus status) {
}
