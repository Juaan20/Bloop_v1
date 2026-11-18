package com.bloop.shared.security;

/**
 * Codigos de permisos (tabla permissions). Se usan en @PreAuthorize, por ejemplo:
 * {@code @PreAuthorize("hasAuthority('EVENT_MANAGE')")}.
 * Mantener sincronizado con V2__seed_roles_and_permissions.sql y con Angular.
 */
public final class Permissions {

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String TENANT_SETTINGS = "TENANT_SETTINGS";
    public static final String USER_MANAGE = "USER_MANAGE";
    public static final String VENUE_MANAGE = "VENUE_MANAGE";
    public static final String VENUE_VIEW = "VENUE_VIEW";
    public static final String EVENT_MANAGE = "EVENT_MANAGE";
    public static final String EVENT_VIEW = "EVENT_VIEW";
    public static final String ORDER_VIEW = "ORDER_VIEW";
    public static final String TICKET_VALIDATE = "TICKET_VALIDATE";
    public static final String DASHBOARD_VIEW = "DASHBOARD_VIEW";
    public static final String AUDIT_VIEW = "AUDIT_VIEW";
    // Fase 2 (migraciones V5, V6 y V7)
    public static final String CUSTOMER_VIEW = "CUSTOMER_VIEW";
    public static final String CUSTOMER_MANAGE = "CUSTOMER_MANAGE";
    public static final String ORDER_MANAGE = "ORDER_MANAGE";
    public static final String PROMOTER_MANAGE = "PROMOTER_MANAGE";
    public static final String PROMOTER_PANEL = "PROMOTER_PANEL";
    public static final String RESERVATION_VIEW = "RESERVATION_VIEW";
    public static final String RESERVATION_MANAGE = "RESERVATION_MANAGE";
    // Fase 3 (migracion V9)
    public static final String POS_SELL = "POS_SELL";
    public static final String POS_SUPERVISE = "POS_SUPERVISE";
    public static final String POS_MANAGE = "POS_MANAGE";
    public static final String INVENTORY_VIEW = "INVENTORY_VIEW";
    public static final String INVENTORY_MANAGE = "INVENTORY_MANAGE";
    // Fase 4
    public static final String ORDER_REFUND = "ORDER_REFUND";
    public static final String REPORT_VIEW = "REPORT_VIEW";
    // Servicio VIP (migracion V11)
    public static final String VIP_SERVICE = "VIP_SERVICE";

    private Permissions() {
    }
}
