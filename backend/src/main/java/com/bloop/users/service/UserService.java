package com.bloop.users.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.response.PageResponse;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.users.dto.request.CreateUserRequest;
import com.bloop.users.dto.request.UpdateUserRequest;
import com.bloop.users.dto.response.RoleResponse;
import com.bloop.users.dto.response.UserResponse;
import com.bloop.users.entity.Role;
import com.bloop.users.entity.User;
import com.bloop.users.entity.UserStatus;
import com.bloop.users.mapper.UserMapper;
import com.bloop.users.repository.RoleRepository;
import com.bloop.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Gestion de los empleados de la empresa del usuario autenticado.
 * Todas las consultas filtran por el tenant del token: un admin nunca ve usuarios de otra empresa.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserAccountService userAccountService;
    private final UserMapper userMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search, int page, int size) {
        UUID tenantId = currentUserProvider.requireTenantId();
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("firstName", "lastName"));
        return PageResponse.of(userRepository.searchByTenant(tenantId, search == null ? "" : search.trim(), pageable),
                userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return userMapper.toResponse(findInMyTenant(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        UUID tenantId = currentUserProvider.requireTenantId();
        assertAssignableRoles(request.roles());
        User user = userAccountService.createAccount(tenantId, request.email(), request.password(),
                request.firstName(), request.lastName(), request.phone(), request.roles(), request.venueIds());
        auditService.record(AuditAction.USER_CREATED, "User", user.getId(),
                user.getEmail() + " roles=" + request.roles());
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = findInMyTenant(id);
        assertAssignableRoles(request.roles());
        boolean isSelf = user.getId().equals(currentUserProvider.get().userId());
        if (isSelf && request.status() == UserStatus.DISABLED) {
            throw new BadRequestException("No puedes desactivar tu propia cuenta");
        }
        if (isSelf && !request.roles().contains("TENANT_ADMIN") && user.getRoleCodes().contains("TENANT_ADMIN")) {
            throw new BadRequestException("No puedes quitarte tu propio rol de administrador");
        }
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(request.phone());
        user.setStatus(request.status());
        user.setRoles(userAccountService.loadRoles(request.roles()));
        user.setVenueIds(userAccountService.validateVenues(user.getTenantId(), request.venueIds()));
        auditService.record(AuditAction.USER_UPDATED, "User", id,
                "status=" + request.status() + " roles=" + request.roles() + " venues=" + user.getVenueIds().size());
        return userMapper.toResponse(user);
    }

    /** Roles que un administrador de empresa puede asignar (excluye SUPER_ADMIN y CUSTOMER). */
    @Transactional(readOnly = true)
    public List<RoleResponse> assignableRoles() {
        return roleRepository.findByAssignableByTenantTrueOrderByCode().stream().map(userMapper::toResponse).toList();
    }

    private void assertAssignableRoles(Set<String> roleCodes) {
        Set<String> allowed = roleRepository.findByAssignableByTenantTrueOrderByCode().stream()
                .map(Role::getCode).collect(java.util.stream.Collectors.toSet());
        if (!allowed.containsAll(roleCodes)) {
            throw new BadRequestException("No puedes asignar alguno de los roles indicados");
        }
    }

    private User findInMyTenant(UUID id) {
        return userRepository.findByIdAndTenantId(id, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Usuario"));
    }
}
