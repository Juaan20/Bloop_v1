package com.bloop.users.service;

import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.users.dto.request.ChangePasswordRequest;
import com.bloop.users.dto.request.UpdateProfileRequest;
import com.bloop.users.dto.response.ProfileResponse;
import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestion de la propia cuenta (cualquier usuario autenticado). */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public ProfileResponse get() {
        return toResponse(currentUser());
    }

    @Transactional
    public ProfileResponse update(UpdateProfileRequest request) {
        User user = currentUser();
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(request.phone());
        user.setMarketingConsent(request.marketingConsent());
        return toResponse(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = currentUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("La contrasena actual no es correcta");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private User currentUser() {
        return userRepository.findById(currentUserProvider.get().userId())
                .orElseThrow(() -> NotFoundException.of("Usuario"));
    }

    private static ProfileResponse toResponse(User u) {
        return new ProfileResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getPhone(),
                u.isMarketingConsent(), u.getPrivacyAcceptedAt(), u.getCreatedAt());
    }
}
