import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { Permission, PermissionCode } from '../core/auth/permissions';
import { AlertsService } from '../features/alerts/alerts.service';
import { statusLabel } from '../shared/pipes/status-label.pipe';
import { Icon, IconName } from '../shared/ui/icon';
import { Logo } from '../shared/ui/logo';

interface NavItem {
  label: string;
  icon: IconName;
  route: string;
  /** null = cualquier empleado de una empresa. */
  permission: PermissionCode | null;
}

interface NavGroup {
  title: string;
  items: NavItem[];
}

/** Cada cuanto se consulta el numero de alertas sin leer (campana). */
const ALERTS_REFRESH_MS = 60_000;
const COLLAPSED_KEY = 'vf.sidebar.collapsed';

/**
 * Estructura del backoffice: barra lateral con los modulos y barra superior.
 * El menu se filtra por permisos (solo visual: el backend vuelve a comprobarlos).
 */
@Component({
  selector: 'vf-admin-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, Logo],
  templateUrl: './admin-layout.html',
})
export class AdminLayout {
  protected readonly auth = inject(AuthService);
  protected readonly alerts = inject(AlertsService);
  /** Menu lateral en movil (cajon). */
  protected readonly sidebarOpen = signal(false);
  /** Barra lateral plegada en escritorio (solo iconos). Se recuerda en el navegador. */
  protected readonly collapsed = signal(localStorage.getItem(COLLAPSED_KEY) === '1');
  /** Las alertas son de la empresa: el super admin de la plataforma no tiene. */
  protected readonly hasTenant = computed(() => !!this.auth.user()?.tenantId);

  /**
   * Menu agrupado. Los tipos de entrada se gestionan dentro de cada evento y los roles dentro de Equipo,
   * por eso no tienen entrada propia (no hay pantallas separadas para ellos).
   */
  private readonly groups: NavGroup[] = [
    {
      title: 'General',
      items: [
        { label: 'Dashboard', icon: 'dashboard', route: '/admin/dashboard', permission: Permission.DASHBOARD_VIEW },
        { label: 'Mi panel RRPP', icon: 'trending', route: '/admin/mi-panel', permission: Permission.PROMOTER_PANEL },
        { label: 'Empresas', icon: 'building', route: '/admin/empresas', permission: Permission.PLATFORM_ADMIN },
      ],
    },
    {
      title: 'Gestion',
      items: [
        { label: 'Establecimientos', icon: 'pin', route: '/admin/locales', permission: Permission.VENUE_VIEW },
        { label: 'Eventos y entradas', icon: 'ticket', route: '/admin/eventos', permission: Permission.EVENT_VIEW },
        { label: 'Pedidos', icon: 'card', route: '/admin/ventas', permission: Permission.ORDER_VIEW },
        { label: 'Clientes', icon: 'user', route: '/admin/clientes', permission: Permission.CUSTOMER_VIEW },
      ],
    },
    {
      title: 'Operaciones',
      items: [
        { label: 'Control de acceso', icon: 'scan', route: '/admin/acceso', permission: Permission.TICKET_VALIDATE },
        { label: 'Plano y reservas VIP', icon: 'wine', route: '/admin/vip', permission: Permission.RESERVATION_VIEW },
        { label: 'Servicio VIP', icon: 'activity', route: '/servicio', permission: Permission.VIP_SERVICE },
        { label: 'Abrir TPV', icon: 'receipt', route: '/tpv', permission: Permission.POS_SELL },
        { label: 'Catalogo TPV', icon: 'beer', route: '/admin/tpv/catalogo', permission: Permission.POS_MANAGE },
        { label: 'Terminales y caja', icon: 'wallet', route: '/admin/tpv/terminales', permission: Permission.POS_MANAGE },
        { label: 'Inventario', icon: 'box', route: '/admin/inventario', permission: Permission.INVENTORY_VIEW },
      ],
    },
    {
      title: 'Comercial',
      items: [
        { label: 'Promotores y comisiones', icon: 'megaphone', route: '/admin/promotores', permission: Permission.PROMOTER_MANAGE },
        { label: 'Codigos promocionales', icon: 'tag', route: '/admin/codigos', permission: Permission.PROMOTER_MANAGE },
        { label: 'Analytics e informes', icon: 'chart', route: '/admin/analitica', permission: Permission.REPORT_VIEW },
      ],
    },
    {
      title: 'Administracion',
      items: [
        { label: 'Usuarios y roles', icon: 'users', route: '/admin/usuarios', permission: Permission.USER_MANAGE },
        { label: 'Notificaciones', icon: 'bell', route: '/admin/notificaciones', permission: null },
        { label: 'Configuracion', icon: 'settings', route: '/admin/configuracion', permission: Permission.TENANT_SETTINGS },
        { label: 'Auditoria', icon: 'shield', route: '/admin/auditoria', permission: Permission.AUDIT_VIEW },
      ],
    },
  ];


  protected readonly visibleGroups = computed(() => {
    this.auth.user(); // dependencia: se recalcula si cambia el usuario
    return this.groups
      .map((g) => ({
        ...g,
        items: g.items.filter((i) => (i.permission ? this.auth.hasPermission(i.permission) : this.hasTenant())),
      }))
      .filter((g) => g.items.length > 0);
  });

  protected readonly roleLabel = computed(() => (this.auth.user()?.roles ?? []).map(statusLabel).join(', '));

  /** Iniciales para el avatar de la barra superior. */
  protected readonly initials = computed(() =>
    this.auth
      .displayName()
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((w) => w[0].toUpperCase())
      .join(''),
  );

  protected readonly canSettings = computed(() => {
    this.auth.user();
    return this.auth.hasPermission(Permission.TENANT_SETTINGS);
  });

  protected toggleCollapsed(): void {
    const next = !this.collapsed();
    this.collapsed.set(next);
    localStorage.setItem(COLLAPSED_KEY, next ? '1' : '0');
  }

  constructor() {
    if (this.hasTenant()) {
      const refresh = () => this.alerts.latest().subscribe({ error: () => undefined });
      refresh();
      const timer = setInterval(refresh, ALERTS_REFRESH_MS);
      inject(DestroyRef).onDestroy(() => clearInterval(timer));
    }
  }
}
