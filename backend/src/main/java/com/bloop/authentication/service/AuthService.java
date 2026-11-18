package com.bloop.authentication.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.authentication.security.LoginAttemptService;
import com.bloop.authentication.dto.request.LoginRequest;
import com.bloop.authentication.dto.request.RegisterRequest;
import com.bloop.authentication.dto.response.AuthResponse;
import com.bloop.authentication.dto.response.AuthUserResponse;
import com.bloop.shared.enums.RoleCode;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.exception.UnauthorizedException;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.tenants.entity.Tenant;
import com.bloop.tenants.entity.TenantStatus;
import com.bloop.tenants.repository.TenantRepository;
import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import com.bloop.users.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Casos de uso de autenticacion: login, registro de clientes, renovacion y cierre de sesion.
 *
 * Flujo de login:
 * 1. Angular envia email y contrasena a POST /api/auth/login.
 * 2. Comprobamos la contrasena con BCrypt y que la cuenta y su empresa esten activas.
 * 3. Devolvemos un JWT de acceso (JSON) y un refresh token (cookie HttpOnly).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Hash de referencia: se compara aunque el email no exista, para no revelarlo por el tiempo de respuesta. */
    private volatile String dummyHash;

    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;
    private final TenantRepository tenantRepository;
    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthSession login(LoginRequest request) {
        String email = UserAccountService.normalizeEmail(request.email());
        // Fuerza bruta: si hay demasiados fallos recientes, 429 sin comprobar la contrasena.
        loginAttemptService.assertLoginAllowed(email);
        Optional<User> found = userRepository.findByEmail(email);
        String hash = found.map(User::getPasswordHash).orElseGet(this::dummyHash);
        boolean passwordOk = passwordEncoder.matches(request.password(), hash);

        if (found.isEmpty() || !passwordOk) {
            auditService.recordFor(found.map(User::getTenantId).orElse(null), found.map(User::getId).orElse(null),
                    email, AuditAction.LOGIN_FAILED, "Credenciales incorrectas");
            if (loginAttemptService.registerFailure(email)) {
                auditService.recordFor(found.map(User::getTenantId).orElse(null), found.map(User::getId).orElse(null),
                        email, AuditAction.LOGIN_BLOCKED, "Bloqueo temporal por intentos fallidos");
            }
            throw new UnauthorizedException("Email o contrasena incorrectos");
        }
        User user = found.get();
        assertCanLogin(user);
        loginAttemptService.registerSuccess(email);
        user.setLastLoginAt(Instant.now(clock));
        auditService.recordFor(user.getTenantId(), user.getId(), user.getEmail(), AuditAction.LOGIN_SUCCESS, null);
        return startSession(user);
    }

    /** Registro de cliente final: crea la cuenta con rol CUSTOMER y abre sesion directamente. */
    @Transactional
    public AuthSession register(RegisterRequest request) {
        User user = userAccountService.createAccount(null, request.email(), request.password(),
                request.firstName(), request.lastName(), request.phone(), Set.of(RoleCode.CUSTOMER.name()), Set.of());
        user.setPrivacyAcceptedAt(Instant.now(clock));
        user.setMarketingConsent(request.marketingConsent());
        return startSession(user);
    }

    /** Canjea un refresh token valido por un JWT nuevo (y un refresh token nuevo). */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthSession refresh(String rawRefreshToken) {
        UUID userId = refreshTokenService.consume(rawRefreshToken);
        User user = userRepository.findById(userId).orElseThrow(() -> new UnauthorizedException("Sesion no valida"));
        assertCanLogin(user);
        return startSession(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public AuthUserResponse me() {
        User user = userRepository.findById(currentUserProvider.get().userId())
                .orElseThrow(() -> NotFoundException.of("Usuario"));
        return toUserResponse(user);
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("bloop-dummy-password");
        }
        return dummyHash;
    }

    private AuthSession startSession(User user) {
        TokenService.AccessToken accessToken = tokenService.createAccessToken(user);
        String refreshToken = refreshTokenService.issue(user.getId());
        return new AuthSession(new AuthResponse(accessToken.value(), accessToken.expiresAt(), toUserResponse(user)),
                refreshToken);
    }

    private void assertCanLogin(User user) {
        if (!user.isActive()) {
            throw new UnauthorizedException("La cuenta esta desactivada");
        }
        if (user.getTenantId() != null) {
            Tenant tenant = tenantRepository.findById(user.getTenantId())
                    .orElseThrow(() -> new UnauthorizedException("Empresa no encontrada"));
            if (tenant.getStatus() != TenantStatus.ACTIVE) {
                throw new UnauthorizedException("La empresa esta suspendida");
            }
        }
    }

    private AuthUserResponse toUserResponse(User user) {
        String tenantName = user.getTenantId() == null ? null
                : tenantRepository.findById(user.getTenantId()).map(Tenant::getName).orElse(null);
        return new AuthUserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getTenantId(), tenantName, user.getRoleCodes(), user.getPermissionCodes());
    }
}
