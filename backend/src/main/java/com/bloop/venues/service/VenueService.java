package com.bloop.venues.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.shared.util.SlugUtils;
import com.bloop.venues.dto.request.CreateVenueRequest;
import com.bloop.venues.dto.request.UpdateVenueRequest;
import com.bloop.venues.dto.response.PublicVenueResponse;
import com.bloop.venues.dto.response.VenueResponse;
import com.bloop.venues.entity.Venue;
import com.bloop.venues.entity.VenueStatus;
import com.bloop.venues.mapper.VenueMapper;
import com.bloop.venues.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;
    private final VenueMapper venueMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<VenueResponse> listMyVenues() {
        return venueRepository.findByTenantIdOrderByName(currentUserProvider.requireTenantId()).stream()
                .map(venueMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public VenueResponse get(UUID id) {
        return venueMapper.toResponse(findInMyTenant(id));
    }

    @Transactional
    public VenueResponse create(CreateVenueRequest request) {
        Venue venue = new Venue();
        venue.setTenantId(currentUserProvider.requireTenantId());
        venueMapper.applyCreate(venue, request);
        venue.setSlug(SlugUtils.uniqueSlug(request.name() + " " + request.city(), venueRepository::existsBySlug));
        venue = venueRepository.save(venue);
        auditService.record(AuditAction.VENUE_CREATED, "Venue", venue.getId(), venue.getName());
        return venueMapper.toResponse(venue);
    }

    @Transactional
    public VenueResponse update(UUID id, UpdateVenueRequest request) {
        Venue venue = findInMyTenant(id);
        venueMapper.applyUpdate(venue, request);
        auditService.record(AuditAction.VENUE_UPDATED, "Venue", id, venue.getName() + " status=" + venue.getStatus());
        return venueMapper.toResponse(venue);
    }

    @Transactional(readOnly = true)
    public PublicVenueResponse getPublic(String slug) {
        return venueRepository.findBySlugAndStatus(slug, VenueStatus.ACTIVE)
                .map(venueMapper::toPublicResponse)
                .orElseThrow(() -> NotFoundException.of("Local"));
    }

    /** Busca un local del tenant actual. Lo usan otros modulos (eventos) para validar pertenencia. */
    @Transactional(readOnly = true)
    public Venue findInMyTenant(UUID id) {
        return venueRepository.findByIdAndTenantId(id, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Local"));
    }
}
