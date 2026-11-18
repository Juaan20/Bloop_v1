package com.bloop.shared.security;

/** Nombres de los claims propios que incluimos en el JWT de acceso. */
public final class JwtClaims {

    public static final String EMAIL = "email";
    public static final String TENANT_ID = "tenantId";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "permissions";

    private JwtClaims() {
    }
}
