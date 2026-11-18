package com.bloop.users.service;

import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.users.entity.Role;
import com.bloop.users.entity.User;
import com.bloop.users.repository.RoleRepository;
import com.bloop.users.repository.UserRepository;
import com.bloop.venues.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Creacion de cuentas de usuario. Es el UNICO sitio donde se crea un User, para que
 * las reglas (email unico, hash de contrasena, roles validos) se apliquen siempre igual.
 * Lo usan el registro de clientes, el alta de empresas y el alta de empleados.
 */
@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VenueRepository venueRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createAccount(UUID tenantId, String email, String rawPassword, String firstName, String lastName,
                              String phone, Set<String> roleCodes, Set<UUID> venueIds) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Ya existe una cuenta con ese email");
        }
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setPhone(phone);
        user.setRoles(loadRoles(roleCodes));
        user.setVenueIds(validateVenues(tenantId, venueIds));
        return userRepository.save(user);
    }

    public Set<Role> loadRoles(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new BadRequestException("El usuario debe tener al menos un rol");
        }
        List<Role> roles = roleRepository.findAllById(roleCodes);
        if (roles.size() != roleCodes.size()) {
            throw new BadRequestException("Alguno de los roles indicados no existe");
        }
        return new HashSet<>(roles);
    }

    /** Comprueba que todos los locales pertenecen al tenant (evita asignar locales de otra empresa). */
    public Set<UUID> validateVenues(UUID tenantId, Set<UUID> venueIds) {
        if (venueIds == null || venueIds.isEmpty()) {
            return new HashSet<>();
        }
        if (tenantId == null || venueRepository.countByIdInAndTenantId(venueIds, tenantId) != venueIds.size()) {
            throw new BadRequestException("Alguno de los locales indicados no existe");
        }
        return new HashSet<>(venueIds);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
