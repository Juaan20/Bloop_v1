-- =====================================================================
-- V1 - Organizacion (tenants, locales), usuarios, roles y permisos.
-- Todas las fechas son TIMESTAMPTZ (se guardan en UTC).
-- Los identificadores son UUID: no revelan volumen de negocio ni son adivinables.
-- =====================================================================

-- Empresa cliente del SaaS. Cada tenant es una empresa aislada del resto.
CREATE TABLE tenants (
    id              UUID PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    slug            VARCHAR(160) NOT NULL UNIQUE,
    legal_name      VARCHAR(200),
    tax_id          VARCHAR(40),
    contact_email   VARCHAR(255) NOT NULL,
    contact_phone   VARCHAR(40),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

-- Catalogo de permisos (datos de referencia, los gestiona Flyway).
CREATE TABLE permissions (
    code        VARCHAR(60) PRIMARY KEY,
    description VARCHAR(255) NOT NULL
);

-- Roles del sistema. Un rol agrupa permisos.
CREATE TABLE roles (
    code        VARCHAR(40) PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    -- Indica si un TENANT_ADMIN puede asignar este rol a sus empleados.
    assignable_by_tenant BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE role_permissions (
    role_code       VARCHAR(40) NOT NULL REFERENCES roles (code),
    permission_code VARCHAR(60) NOT NULL REFERENCES permissions (code),
    PRIMARY KEY (role_code, permission_code)
);

-- Usuarios. tenant_id es NULL para SUPER_ADMIN y para clientes finales (CUSTOMER),
-- que compran entradas de cualquier empresa.
CREATE TABLE users (
    id             UUID PRIMARY KEY,
    tenant_id      UUID REFERENCES tenants (id),
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    first_name     VARCHAR(80)  NOT NULL,
    last_name      VARCHAR(120) NOT NULL,
    phone          VARCHAR(40),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at  TIMESTAMPTZ,
    -- RGPD: cuando acepto la politica de privacidad y si consiente comunicaciones comerciales.
    privacy_accepted_at TIMESTAMPTZ,
    marketing_consent   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
-- El email es unico sin distinguir mayusculas/minusculas.
CREATE UNIQUE INDEX ux_users_email ON users (LOWER(email));
CREATE INDEX ix_users_tenant ON users (tenant_id);

CREATE TABLE user_roles (
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_code VARCHAR(40) NOT NULL REFERENCES roles (code),
    PRIMARY KEY (user_id, role_code)
);

-- Establecimientos (discotecas, salas...) de un tenant.
CREATE TABLE venues (
    id             UUID PRIMARY KEY,
    tenant_id      UUID         NOT NULL REFERENCES tenants (id),
    name           VARCHAR(150) NOT NULL,
    slug           VARCHAR(160) NOT NULL UNIQUE,
    description    TEXT,
    address_line   VARCHAR(255) NOT NULL,
    city           VARCHAR(100) NOT NULL,
    postal_code    VARCHAR(20),
    country        VARCHAR(2)   NOT NULL DEFAULT 'ES',
    phone          VARCHAR(40),
    email          VARCHAR(255),
    capacity       INTEGER      NOT NULL,
    opening_hours  VARCHAR(500),
    image_url      VARCHAR(500),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_venues_capacity CHECK (capacity > 0),
    CONSTRAINT ck_venues_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX ix_venues_tenant ON venues (tenant_id);

-- Asignacion de empleados a locales (p. ej. un portero solo valida en su local).
CREATE TABLE user_venues (
    user_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    venue_id UUID NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, venue_id)
);
CREATE INDEX ix_user_venues_venue ON user_venues (venue_id);

-- Refresh tokens: guardamos solo el hash SHA-256, nunca el token en claro.
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL
);
