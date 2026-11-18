package com.bloop.shared.enums;

/** Codigos de los roles del sistema (tabla roles, sembrada por Flyway en V2). */
public enum RoleCode {
    SUPER_ADMIN,
    TENANT_ADMIN,
    MANAGER,
    CASHIER,
    DOOR_STAFF,
    PROMOTER,
    VIP_WAITER,
    CUSTOMER
}
