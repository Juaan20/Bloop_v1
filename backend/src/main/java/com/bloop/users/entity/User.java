package com.bloop.users.entity;

import com.bloop.shared.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Usuario de la plataforma: empleado de una empresa, super administrador o cliente final.
 * tenantId se guarda como columna simple (sin relacion JPA) porque solo lo usamos para filtrar.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "tenant_id")
    private UUID tenantId;

    /** Siempre en minusculas (lo normaliza el servicio). */
    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** RGPD: momento en que el cliente acepto la politica de privacidad. */
    @Column(name = "privacy_accepted_at")
    private Instant privacyAcceptedAt;

    /** RGPD: consentimiento explicito para comunicaciones comerciales (por defecto no). */
    @Column(name = "marketing_consent", nullable = false)
    private boolean marketingConsent;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_code"))
    private Set<Role> roles = new HashSet<>();

    /** Locales asignados al empleado (tabla user_venues). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_venues", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "venue_id")
    private Set<UUID> venueIds = new HashSet<>();

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public Set<String> getRoleCodes() {
        return roles.stream().map(Role::getCode).collect(Collectors.toSet());
    }

    /** Union de los permisos de todos los roles del usuario. */
    public Set<String> getPermissionCodes() {
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toSet());
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
