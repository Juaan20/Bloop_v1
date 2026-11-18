import { Routes } from '@angular/router';
import { Permission } from './core/auth/permissions';
import { adminHomeRedirect, authGuard, customerAreaGuard, guestGuard, permissionGuard, staffGuard } from './core/guards/auth.guards';
import { AdminLayout } from './layout/admin-layout';
import { PublicLayout } from './layout/public-layout';

/**
 * Rutas de la aplicacion. Cada pagina se carga bajo demanda (loadComponent = lazy loading),
 * asi el navegador solo descarga el codigo de las pantallas que se visitan.
 */
export const routes: Routes = [
  // ---------------- TPV tactil (pantalla completa, sin la estructura del backoffice) ----------------
  { path: 'tpv', canActivate: [authGuard, staffGuard, permissionGuard], data: { permission: Permission.POS_SELL }, loadComponent: () => import('./features/pos/pos-select.page').then((m) => m.PosSelectPage), title: 'TPV' },
  { path: 'tpv/:terminalId', canActivate: [authGuard, staffGuard, permissionGuard], data: { permission: Permission.POS_SELL }, loadComponent: () => import('./features/pos/pos-screen.page').then((m) => m.PosScreenPage), title: 'TPV' },

  // ---------------- Servicio VIP (tablet del personal, pantalla completa) ----------------
  { path: 'servicio', canActivate: [authGuard, staffGuard, permissionGuard], data: { permission: Permission.VIP_SERVICE }, loadComponent: () => import('./features/vip/service-board.page').then((m) => m.ServiceBoardPage), title: 'Servicio VIP' },
  // ---------------- Carta de la mesa VIP (QR, publica y sin la cabecera de la web) ----------------
  { path: 'mesa/:token', loadComponent: () => import('./public/vip-table/vip-table.page').then((m) => m.VipTablePage), title: 'Tu mesa' },

  // ---------------- Web publica ----------------
  {
    path: '',
    component: PublicLayout,
    // El personal no entra en la web de clientes (va a su panel); el backend tambien lo bloquea.
    canActivateChild: [customerAreaGuard],
    children: [
      { path: '', loadComponent: () => import('./public/home/home.page').then((m) => m.HomePage), title: 'Bloop · Ocio nocturno' },
      { path: 'eventos', loadComponent: () => import('./public/events/events.page').then((m) => m.EventsPage), title: 'Eventos · Bloop' },
      { path: 'eventos/:slug', loadComponent: () => import('./public/event-detail/event-detail.page').then((m) => m.EventDetailPage) },
      { path: 'locales/:slug', loadComponent: () => import('./public/venue-detail/venue-detail.page').then((m) => m.VenueDetailPage) },
      { path: 'login', canActivate: [guestGuard], loadComponent: () => import('./public/auth/login.page').then((m) => m.LoginPage), title: 'Entrar · Bloop' },
      { path: 'registro', canActivate: [guestGuard], loadComponent: () => import('./public/auth/register.page').then((m) => m.RegisterPage), title: 'Crear cuenta · Bloop' },
      { path: 'recuperar-contrasena', loadComponent: () => import('./public/auth/forgot-password.page').then((m) => m.ForgotPasswordPage) },
      { path: 'restablecer-contrasena', loadComponent: () => import('./public/auth/reset-password.page').then((m) => m.ResetPasswordPage) },
      { path: 'checkout/:orderId', canActivate: [authGuard], loadComponent: () => import('./public/checkout/checkout.page').then((m) => m.CheckoutPage), title: 'Pago · Bloop' },
      {
        path: 'cuenta',
        canActivate: [authGuard],
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'entradas' },
          { path: 'entradas', loadComponent: () => import('./public/account/my-tickets.page').then((m) => m.MyTicketsPage), title: 'Mis entradas' },
          { path: 'pedidos', loadComponent: () => import('./public/account/my-orders.page').then((m) => m.MyOrdersPage), title: 'Mis pedidos' },
          { path: 'reservas', loadComponent: () => import('./public/account/my-reservations.page').then((m) => m.MyReservationsPage), title: 'Mis reservas' },
          { path: 'privacidad', loadComponent: () => import('./public/account/privacy.page').then((m) => m.PrivacyPage), title: 'Privacidad' },
          { path: 'perfil', loadComponent: () => import('./public/account/profile.page').then((m) => m.ProfilePage), title: 'Mi perfil' },
        ],
      },
    ],
  },

  // ---------------- Backoffice ----------------
  {
    path: 'admin',
    component: AdminLayout,
    canActivate: [authGuard, staffGuard],
    canActivateChild: [permissionGuard],
    children: [
      { path: '', pathMatch: 'full', canActivate: [adminHomeRedirect], children: [] },
      { path: 'dashboard', data: { permission: Permission.DASHBOARD_VIEW }, loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage), title: 'Dashboard' },
      { path: 'analitica', data: { permission: Permission.REPORT_VIEW }, loadComponent: () => import('./features/analytics/analytics.page').then((m) => m.AnalyticsPage), title: 'Analitica' },
      { path: 'locales', data: { permission: Permission.VENUE_VIEW }, loadComponent: () => import('./features/venues/venue-list.page').then((m) => m.VenueListPage), title: 'Locales' },
      { path: 'locales/nuevo', data: { permission: Permission.VENUE_MANAGE }, loadComponent: () => import('./features/venues/venue-form.page').then((m) => m.VenueFormPage), title: 'Nuevo local' },
      { path: 'locales/:id', data: { permission: Permission.VENUE_VIEW }, loadComponent: () => import('./features/venues/venue-form.page').then((m) => m.VenueFormPage), title: 'Local' },
      { path: 'eventos', data: { permission: Permission.EVENT_VIEW }, loadComponent: () => import('./features/events/event-list.page').then((m) => m.EventListPage), title: 'Eventos' },
      { path: 'eventos/nuevo', data: { permission: Permission.EVENT_MANAGE }, loadComponent: () => import('./features/events/event-create.page').then((m) => m.EventCreatePage), title: 'Nuevo evento' },
      { path: 'eventos/:id', data: { permission: Permission.EVENT_VIEW }, loadComponent: () => import('./features/events/event-detail.page').then((m) => m.EventDetailAdminPage), title: 'Evento' },
      { path: 'ventas', data: { permission: Permission.ORDER_VIEW }, loadComponent: () => import('./features/orders/order-list.page').then((m) => m.OrderListPage), title: 'Ventas' },
      { path: 'ventas/:id', data: { permission: Permission.ORDER_VIEW }, loadComponent: () => import('./features/orders/order-detail.page').then((m) => m.OrderDetailPage), title: 'Pedido' },
      { path: 'acceso', data: { permission: Permission.TICKET_VALIDATE }, loadComponent: () => import('./features/access-control/scanner.page').then((m) => m.ScannerPage), title: 'Control de acceso' },
      { path: 'usuarios', data: { permission: Permission.USER_MANAGE }, loadComponent: () => import('./features/users/user-list.page').then((m) => m.UserListPage), title: 'Equipo' },
      { path: 'usuarios/nuevo', data: { permission: Permission.USER_MANAGE }, loadComponent: () => import('./features/users/user-form.page').then((m) => m.UserFormPage), title: 'Nuevo usuario' },
      { path: 'usuarios/:id', data: { permission: Permission.USER_MANAGE }, loadComponent: () => import('./features/users/user-form.page').then((m) => m.UserFormPage), title: 'Usuario' },
      { path: 'empresas', data: { permission: Permission.PLATFORM_ADMIN }, loadComponent: () => import('./features/tenants/tenant-list.page').then((m) => m.TenantListPage), title: 'Empresas' },
      { path: 'empresas/nueva', data: { permission: Permission.PLATFORM_ADMIN }, loadComponent: () => import('./features/tenants/tenant-form.page').then((m) => m.TenantFormPage), title: 'Nueva empresa' },
      { path: 'empresas/:id', data: { permission: Permission.PLATFORM_ADMIN }, loadComponent: () => import('./features/tenants/tenant-form.page').then((m) => m.TenantFormPage), title: 'Empresa' },
      { path: 'configuracion', data: { permission: Permission.TENANT_SETTINGS }, loadComponent: () => import('./features/settings/settings.page').then((m) => m.SettingsPage), title: 'Configuracion' },
      { path: 'auditoria', data: { permission: Permission.AUDIT_VIEW }, loadComponent: () => import('./features/audit/audit.page').then((m) => m.AuditPage), title: 'Auditoria' },
      // ---- Fase 2 ----
      { path: 'clientes', data: { permission: Permission.CUSTOMER_VIEW }, loadComponent: () => import('./features/customers/customer-list.page').then((m) => m.CustomerListPage), title: 'Clientes' },
      { path: 'clientes/:id', data: { permission: Permission.CUSTOMER_VIEW }, loadComponent: () => import('./features/customers/customer-detail.page').then((m) => m.CustomerDetailPage), title: 'Cliente' },
      { path: 'promotores', data: { permission: Permission.PROMOTER_MANAGE }, loadComponent: () => import('./features/promoters/promoter-list.page').then((m) => m.PromoterListPage), title: 'Promotores' },
      { path: 'promotores/:id', data: { permission: Permission.PROMOTER_MANAGE }, loadComponent: () => import('./features/promoters/promoter-detail.page').then((m) => m.PromoterDetailPage), title: 'Promotor' },
      { path: 'codigos', data: { permission: Permission.PROMOTER_MANAGE }, loadComponent: () => import('./features/promoters/promo-codes.page').then((m) => m.PromoCodesPage), title: 'Codigos promocionales' },
      { path: 'mi-panel', data: { permission: Permission.PROMOTER_PANEL }, loadComponent: () => import('./features/promoters/promoter-panel.page').then((m) => m.PromoterPanelPage), title: 'Mi panel RRPP' },
      { path: 'vip', data: { permission: Permission.RESERVATION_VIEW }, loadComponent: () => import('./features/vip/vip-plan.page').then((m) => m.VipPlanPage), title: 'Plano VIP' },
      // Las reservas VIP se gestionan desde el plano (cada mesa); la direccion antigua lleva alli.
      { path: 'reservas', pathMatch: 'full', redirectTo: 'vip' },
      { path: 'notificaciones', loadComponent: () => import('./features/alerts/notifications.page').then((m) => m.NotificationsPage), title: 'Notificaciones' },
      // ---- Fase 3 ----
      { path: 'tpv/catalogo', data: { permission: Permission.POS_MANAGE }, loadComponent: () => import('./features/pos/catalog.page').then((m) => m.PosCatalogPage), title: 'Catalogo TPV' },
      { path: 'tpv/terminales', data: { permission: Permission.POS_MANAGE }, loadComponent: () => import('./features/pos/terminals.page').then((m) => m.PosTerminalsPage), title: 'Terminales y cajas' },
      { path: 'inventario', data: { permission: Permission.INVENTORY_VIEW }, loadComponent: () => import('./features/pos/inventory.page').then((m) => m.InventoryPage), title: 'Inventario' },
    ],
  },

  { path: '**', redirectTo: '' },
];
