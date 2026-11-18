import { CurrencyPipe, DatePipe, DecimalPipe, NgTemplateOutlet, PercentPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { Permission } from '../../core/auth/permissions';
import { errorMessage } from '../../core/services/api-error';
import { EmptyState } from '../../shared/ui/empty-state';
import { Icon, IconName } from '../../shared/ui/icon';
import { StatusBadge } from '../../shared/ui/status-badge';
import { Analytics, AnalyticsService } from '../analytics/analytics.service';
import { isoDate } from '../analytics/analytics.utils';
import { Order } from '../orders/order.models';
import { OrdersService } from '../orders/orders.service';
import { Reservation } from '../reservations/reservation.models';
import { ReservationsService } from '../reservations/reservations.service';
import { Venue } from '../venues/venue.models';
import { VenuesService } from '../venues/venues.service';
import { Dashboard, DashboardService } from './dashboard.service';
import { Trend, comparisonRanges, trend } from './dashboard.utils';

/** Medidas del grafico SVG de ingresos (unidades del viewBox). */
const CHART_W = 600;
const CHART_H = 180;

/** Elemento de la actividad reciente (pedidos y reservas mezclados por fecha). */
interface FeedItem {
  id: string;
  icon: IconName;
  title: string;
  detail: string;
  date: string;
  amount: number | null;
  status: string;
  link: string[];
}

/**
 * Panel de indicadores. Todos los datos salen del backend:
 * - /dashboard (DashboardRepository.java): KPIs, ventas por dia, sesiones y locales.
 * - /analytics (solo con REPORT_VIEW): ventas del TPV y comparativa con el periodo anterior.
 * - /reservations (solo con RESERVATION_VIEW): reservas del periodo.
 * Si un dato no existe o el usuario no tiene permiso, la tarjeta no se muestra: nunca se inventan cifras.
 */
@Component({
  selector: 'vf-dashboard-page',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, PercentPipe, NgTemplateOutlet, FormsModule, RouterLink, EmptyState, Icon, StatusBadge],
  templateUrl: './dashboard.page.html',
})
export class DashboardPage implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly venuesService = inject(VenuesService);
  private readonly ordersService = inject(OrdersService);
  private readonly analyticsService = inject(AnalyticsService);
  private readonly reservationsService = inject(ReservationsService);
  private readonly auth = inject(AuthService);

  protected readonly data = signal<Dashboard | null>(null);
  protected readonly venues = signal<Venue[]>([]);
  protected readonly recentOrders = signal<Order[] | null>(null);
  /** Analitica del periodo actual y del anterior (para las comparativas). */
  protected readonly analytics = signal<{ current: Analytics; previous: Analytics } | null>(null);
  protected readonly reservations = signal<{ current: number; previous: number; latest: Reservation[] } | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly can = {
    orders: this.auth.hasPermission(Permission.ORDER_VIEW),
    analytics: this.auth.hasPermission(Permission.REPORT_VIEW),
    reservations: this.auth.hasPermission(Permission.RESERVATION_VIEW),
    createEvent: this.auth.hasPermission(Permission.EVENT_MANAGE),
    pos: this.auth.hasPermission(Permission.POS_SELL),
    scan: this.auth.hasPermission(Permission.TICKET_VALIDATE),
  };
  protected readonly periods = [
    { days: 7, label: '7 d' },
    { days: 30, label: '30 d' },
    { days: 90, label: '90 d' },
    { days: 365, label: '1 año' },
  ];
  protected days = 30;
  protected venueId = '';

  protected readonly tenantName = computed(() => this.auth.user()?.tenantName ?? 'Bloop');
  protected readonly venueLabel = computed(() => this.venues().find((v) => v.id === this.venueIdSignal())?.name ?? 'Todos los locales');
  private readonly venueIdSignal = signal('');
  protected readonly range = signal(comparisonRanges(30));

  protected readonly greeting = computed(() => {
    const h = new Date().getHours();
    const hello = h < 6 ? 'Buenas noches' : h < 14 ? 'Buenos dias' : h < 21 ? 'Buenas tardes' : 'Buenas noches';
    const name = this.auth.user()?.firstName;
    return name ? `${hello}, ${name}` : hello;
  });

  /** Tendencias frente al periodo anterior (null = sin base de comparacion). */
  protected readonly trends = computed(() => {
    const a = this.analytics();
    const r = this.reservations();
    return {
      revenue: a ? trend(a.current.summary.ticketRevenue, a.previous.summary.ticketRevenue) : null,
      tickets: a ? trend(a.current.summary.ticketsSold, a.previous.summary.ticketsSold) : null,
      pos: a ? trend(a.current.summary.posRevenue, a.previous.summary.posRevenue) : null,
      reservations: r ? trend(r.current, r.previous) : null,
    };
  });

  /** Puntos del grafico de area: x/y en el viewBox y posicion en % para los tooltips HTML. */
  protected readonly chart = computed(() => {
    const days = this.data()?.salesByDay ?? [];
    if (days.length === 0) return null;
    const max = Math.max(1, ...days.map((d) => d.revenue));
    const step = days.length > 1 ? CHART_W / (days.length - 1) : 0;
    const points = days.map((d, i) => {
      const x = days.length > 1 ? i * step : CHART_W / 2;
      const y = CHART_H - (d.revenue / max) * (CHART_H - 12);
      return { ...d, x, y, left: (x / CHART_W) * 100, top: (y / CHART_H) * 100 };
    });
    const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');
    const area = `${line} L${points[points.length - 1].x.toFixed(1)},${CHART_H} L${points[0].x.toFixed(1)},${CHART_H} Z`;
    const total = days.reduce((sum, d) => sum + d.revenue, 0);
    const tickets = days.reduce((sum, d) => sum + d.tickets, 0);
    return { points, line, area, max, total, tickets, width: CHART_W, height: CHART_H };
  });

  protected readonly hovered = signal<number | null>(null);

  /** Reparto de ingresos entre entradas y TPV (solo con analitica). */
  protected readonly distribution = computed(() => {
    const s = this.analytics()?.current.summary;
    if (!s || s.ticketRevenue + s.posRevenue <= 0) return null;
    const total = s.ticketRevenue + s.posRevenue;
    return { tickets: s.ticketRevenue, pos: s.posRevenue, ticketShare: s.ticketRevenue / total, posShare: s.posRevenue / total, total };
  });

  /** Eventos con mas ingresos en el periodo (analitica). */
  protected readonly topEvents = computed(() => {
    const events = this.analytics()?.current.topEvents ?? [];
    const max = Math.max(1, ...events.map((e) => e.revenue));
    return events.slice(0, 5).map((e) => ({ ...e, share: e.revenue / max }));
  });

  /** Proximas sesiones (las que aun no han empezado), de la mas cercana a la mas lejana. */
  protected readonly upcoming = computed(() => {
    const now = Date.now();
    return (this.data()?.sessions ?? [])
      .filter((s) => new Date(s.startsAt).getTime() > now)
      .sort((a, b) => a.startsAt.localeCompare(b.startsAt))
      .slice(0, 5);
  });

  /** Ultimos pedidos y reservas, mezclados y ordenados por fecha de creacion. */
  protected readonly feed = computed<FeedItem[] | null>(() => {
    const orders = this.recentOrders();
    const reservations = this.reservations()?.latest;
    if (this.can.orders && orders === null) return null;
    const items: FeedItem[] = [
      ...(orders ?? []).map((o) => ({
        id: 'o' + o.id,
        icon: 'card' as IconName,
        title: `${o.buyerName} · ${o.ticketCount} ${o.ticketCount === 1 ? 'entrada' : 'entradas'}`,
        detail: o.eventName,
        date: o.createdAt,
        amount: o.totalAmount,
        status: o.status,
        link: ['/admin/ventas', o.id],
      })),
      ...(reservations ?? []).map((r) => ({
        id: 'r' + r.id,
        icon: 'wine' as IconName,
        title: `${r.contactName} · ${r.tableName} (${r.guests} pers.)`,
        detail: `${r.zoneName} · ${r.venueName}`,
        date: r.createdAt,
        amount: r.minSpend > 0 ? r.minSpend : null,
        status: r.status,
        link: ['/admin/vip'],
      })),
    ];
    return items.sort((a, b) => b.date.localeCompare(a.date)).slice(0, 7);
  });

  ngOnInit(): void {
    this.venuesService.list().subscribe({ next: (v) => this.venues.set(v), error: () => this.venues.set([]) });
    if (this.can.orders) {
      this.ordersService.list({ page: 0 }).subscribe({
        next: (p) => this.recentOrders.set(p.content.slice(0, 6)),
        error: () => this.recentOrders.set([]),
      });
    }
    this.load();
  }

  protected setPeriod(days: number): void {
    if (this.days === days) return;
    this.days = days;
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.venueIdSignal.set(this.venueId);
    const range = comparisonRanges(this.days);
    this.range.set(range);

    this.dashboardService.summary(this.days, this.venueId).subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });

    if (this.can.analytics) {
      const filters = (r: { from: Date; to: Date }) => ({ from: isoDate(r.from), to: isoDate(r.to), venueId: this.venueId });
      forkJoin({
        current: this.analyticsService.overview(filters(range.current)),
        previous: this.analyticsService.overview(filters(range.previous)),
      }).subscribe({ next: (a) => this.analytics.set(a), error: () => this.analytics.set(null) });
    }

    if (this.can.reservations) {
      const query = (r: { from: Date; to: Date }) =>
        this.reservationsService.list({ from: r.from.toISOString(), to: r.to.toISOString(), venueId: this.venueId });
      forkJoin({ current: query(range.current), previous: query(range.previous) }).subscribe({
        next: ({ current, previous }) =>
          this.reservations.set({
            current: current.totalElements,
            previous: previous.totalElements,
            latest: [...current.content].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 4),
          }),
        error: () => this.reservations.set(null),
      });
    }
  }

  protected ratio(part: number, total: number): number {
    return total > 0 ? part / total : 0;
  }

  protected trendLabel(t: Trend): string {
    const pct = Math.abs(t.change * 100);
    const sign = t.direction === 'up' ? '+' : t.direction === 'down' ? '−' : '';
    return `${sign}${pct < 10 ? pct.toFixed(1) : Math.round(pct)} %`;
  }
}
