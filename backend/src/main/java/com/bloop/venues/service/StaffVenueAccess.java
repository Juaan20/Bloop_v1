package com.bloop.venues.service;

import com.bloop.shared.enums.RoleCode;
import com.bloop.shared.exception.ForbiddenException;
import com.bloop.shared.security.CurrentUser;
import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import com.bloop.venues.entity.Venue;
import com.bloop.venues.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * En que locales puede trabajar un empleado (control de acceso, TPV...).
 * TENANT_ADMIN y MANAGER: todos los locales de su empresa. El resto (puerta, caja...): solo los asignados.
 */
@Component
@RequiredArgsConstructor
public class StaffVenueAccess {

    private final VenueRepository venueRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Set<UUID> allowedVenueIds(CurrentUser user, UUID tenantId) {
        if (user.hasRole(RoleCode.TENANT_ADMIN) || user.hasRole(RoleCode.MANAGER)) {
            return venueRepository.findByTenantIdOrderByName(tenantId).stream().map(Venue::getId)
                    .collect(Collectors.toSet());
        }
        return userRepository.findById(user.userId()).map(User::getVenueIds).map(Set::copyOf).orElse(Set.of());
    }

    /** 403 si el empleado no puede trabajar en el local. */
    public void assertCanWorkAtVenue(CurrentUser user, UUID venueId) {
        if (!allowedVenueIds(user, user.tenantId()).contains(venueId)) {
            throw new ForbiddenException("No estas asignado a este local");
        }
    }
}
