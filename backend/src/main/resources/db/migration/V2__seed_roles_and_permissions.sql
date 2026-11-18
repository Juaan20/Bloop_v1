-- =====================================================================
-- V2 - Datos de referencia: permisos y roles del sistema.
-- Son necesarios en todos los entornos (tambien en produccion).
-- Los permisos se comprueban en el backend con @PreAuthorize("hasAuthority('...')").
-- =====================================================================

INSERT INTO permissions (code, description) VALUES
    ('PLATFORM_ADMIN',  'Administrar la plataforma: crear y gestionar empresas (tenants)'),
    ('TENANT_SETTINGS', 'Editar los datos y la configuracion de la propia empresa'),
    ('USER_MANAGE',     'Crear empleados, asignar roles y locales'),
    ('VENUE_MANAGE',    'Crear y editar establecimientos'),
    ('VENUE_VIEW',      'Consultar establecimientos'),
    ('EVENT_MANAGE',    'Crear, editar y publicar eventos, sesiones y tipos de entrada'),
    ('EVENT_VIEW',      'Consultar eventos'),
    ('ORDER_VIEW',      'Consultar pedidos y ventas'),
    ('TICKET_VALIDATE', 'Validar entradas en el control de acceso'),
    ('DASHBOARD_VIEW',  'Consultar el panel de indicadores'),
    ('AUDIT_VIEW',      'Consultar el registro de auditoria');

INSERT INTO roles (code, name, description, assignable_by_tenant) VALUES
    ('SUPER_ADMIN',  'Super administrador', 'Administrador de la plataforma VenueFlow', FALSE),
    ('TENANT_ADMIN', 'Administrador',       'Administrador de la empresa: acceso completo a su empresa', TRUE),
    ('MANAGER',      'Encargado',           'Gestiona eventos, ventas y accesos de los locales', TRUE),
    ('CASHIER',      'Cajero',              'Opera el TPV y la taquilla (TPV disponible en la fase 3)', TRUE),
    ('DOOR_STAFF',   'Personal de puerta',  'Valida entradas en los locales asignados', TRUE),
    ('PROMOTER',     'Promotor / RRPP',     'Consulta sus ventas (panel de promotor en la fase 2)', TRUE),
    ('CUSTOMER',     'Cliente',             'Cliente final de la web publica', FALSE);

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('SUPER_ADMIN', 'PLATFORM_ADMIN'),

    ('TENANT_ADMIN', 'TENANT_SETTINGS'),
    ('TENANT_ADMIN', 'USER_MANAGE'),
    ('TENANT_ADMIN', 'VENUE_MANAGE'),
    ('TENANT_ADMIN', 'VENUE_VIEW'),
    ('TENANT_ADMIN', 'EVENT_MANAGE'),
    ('TENANT_ADMIN', 'EVENT_VIEW'),
    ('TENANT_ADMIN', 'ORDER_VIEW'),
    ('TENANT_ADMIN', 'TICKET_VALIDATE'),
    ('TENANT_ADMIN', 'DASHBOARD_VIEW'),
    ('TENANT_ADMIN', 'AUDIT_VIEW'),

    ('MANAGER', 'VENUE_VIEW'),
    ('MANAGER', 'EVENT_MANAGE'),
    ('MANAGER', 'EVENT_VIEW'),
    ('MANAGER', 'ORDER_VIEW'),
    ('MANAGER', 'TICKET_VALIDATE'),
    ('MANAGER', 'DASHBOARD_VIEW'),

    ('CASHIER', 'VENUE_VIEW'),
    ('CASHIER', 'EVENT_VIEW'),

    ('DOOR_STAFF', 'VENUE_VIEW'),
    ('DOOR_STAFF', 'EVENT_VIEW'),
    ('DOOR_STAFF', 'TICKET_VALIDATE'),

    ('PROMOTER', 'EVENT_VIEW');
