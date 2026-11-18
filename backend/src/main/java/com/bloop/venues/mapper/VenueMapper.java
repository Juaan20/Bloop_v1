package com.bloop.venues.mapper;

import com.bloop.venues.dto.request.CreateVenueRequest;
import com.bloop.venues.dto.request.UpdateVenueRequest;
import com.bloop.venues.dto.response.PublicVenueResponse;
import com.bloop.venues.dto.response.VenueResponse;
import com.bloop.venues.entity.Venue;
import org.springframework.stereotype.Component;

@Component
public class VenueMapper {

    public VenueResponse toResponse(Venue v) {
        return new VenueResponse(v.getId(), v.getName(), v.getSlug(), v.getDescription(), v.getAddressLine(),
                v.getCity(), v.getPostalCode(), v.getCountry(), v.getPhone(), v.getEmail(), v.getCapacity(),
                v.getOpeningHours(), v.getImageUrl(), v.getStatus(), v.getCreatedAt());
    }

    public PublicVenueResponse toPublicResponse(Venue v) {
        return new PublicVenueResponse(v.getName(), v.getSlug(), v.getDescription(), v.getAddressLine(), v.getCity(),
                v.getPostalCode(), v.getCountry(), v.getPhone(), v.getEmail(), v.getOpeningHours(), v.getImageUrl());
    }

    public void applyCreate(Venue venue, CreateVenueRequest r) {
        venue.setName(r.name().trim());
        venue.setDescription(r.description());
        venue.setAddressLine(r.addressLine().trim());
        venue.setCity(r.city().trim());
        venue.setPostalCode(r.postalCode());
        venue.setCountry(r.country());
        venue.setPhone(r.phone());
        venue.setEmail(r.email());
        venue.setCapacity(r.capacity());
        venue.setOpeningHours(r.openingHours());
        venue.setImageUrl(blankToNull(r.imageUrl()));
    }

    public void applyUpdate(Venue venue, UpdateVenueRequest r) {
        venue.setName(r.name().trim());
        venue.setDescription(r.description());
        venue.setAddressLine(r.addressLine().trim());
        venue.setCity(r.city().trim());
        venue.setPostalCode(r.postalCode());
        venue.setCountry(r.country());
        venue.setPhone(r.phone());
        venue.setEmail(r.email());
        venue.setCapacity(r.capacity());
        venue.setOpeningHours(r.openingHours());
        venue.setImageUrl(blankToNull(r.imageUrl()));
        venue.setStatus(r.status());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
