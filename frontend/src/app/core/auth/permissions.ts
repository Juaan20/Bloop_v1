/**
 * Codigos de permisos. Deben coincidir con el backend (Permissions.java y la migracion V2).
 * En Angular solo sirven para mostrar u ocultar opciones: la seguridad real la aplica el backend.
 */
export const Permission = {
  PLATFORM_ADMIN: 'PLATFORM_ADMIN',
  TENANT_SETTINGS: 'TENANT_SETTINGS',
  USER_MANAGE: 'USER_MANAGE',
  VENUE_MANAGE: 'VENUE_MANAGE',
  VENUE_VIEW: 'VENUE_VIEW',
  EVENT_MANAGE: 'EVENT_MANAGE',
  EVENT_VIEW: 'EVENT_VIEW',
  ORDER_VIEW: 'ORDER_VIEW',
  TICKET_VALIDATE: 'TICKET_VALIDATE',
  DASHBOARD_VIEW: 'DASHBOARD_VIEW',
  AUDIT_VIEW: 'AUDIT_VIEW',
  // Fase 2 (migraciones V5, V6 y V7)
  CUSTOMER_VIEW: 'CUSTOMER_VIEW',
  CUSTOMER_MANAGE: 'CUSTOMER_MANAGE',
  ORDER_MANAGE: 'ORDER_MANAGE',
  PROMOTER_MANAGE: 'PROMOTER_MANAGE',
  PROMOTER_PANEL: 'PROMOTER_PANEL',
  RESERVATION_VIEW: 'RESERVATION_VIEW',
  RESERVATION_MANAGE: 'RESERVATION_MANAGE',
  // Fase 3 (migracion V9)
  POS_SELL: 'POS_SELL',
  POS_SUPERVISE: 'POS_SUPERVISE',
  POS_MANAGE: 'POS_MANAGE',
  INVENTORY_VIEW: 'INVENTORY_VIEW',
  INVENTORY_MANAGE: 'INVENTORY_MANAGE',
  // Fase 4
  ORDER_REFUND: 'ORDER_REFUND',
  REPORT_VIEW: 'REPORT_VIEW',
  // Servicio VIP (migracion V11)
  VIP_SERVICE: 'VIP_SERVICE',
  // Imagenes en la nube (migracion V14)
  MEDIA_MANAGE: 'MEDIA_MANAGE',
} as const;

export type PermissionCode = (typeof Permission)[keyof typeof Permission];
