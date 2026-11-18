import { CurrencyPipe, DatePipe, DecimalPipe, NgTemplateOutlet, PercentPipe } from '@angular/common';
import { trend } from '../dashboard/dashboard.utils';
import { ForecastPanel } from './forecast-panel';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { downloadBlob, fileNameFrom } from '../../shared/utils/download';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { Venue } from '../venues/venue.models';
import { VenuesService } from '../venues/venues.service';
import { Analytics, AnalyticsFilters, AnalyticsService, ReportType } from './analytics.service';
import { barHeights, isoDate, presetRange, previousRange } from './analytics.utils';

/**
 * Analitica avanzada: entradas, TPV, reembolsos y promotores de un periodo, con informes CSV.
 * Todos los datos vienen de consultas SQL reales; sin actividad se muestran ceros.
 */
@Component({
  selector: 'vf-analytics-page',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, PercentPipe, NgTemplateOutlet, FormsModule, ForecastPanel, PageHeader, Spinner],
  templateUrl: './analytics.page.html',
})
export class AnalyticsPage implements OnInit {
  private readonly analyticsService = inject(AnalyticsService);
  private readonly venuesService = inject(VenuesService);
  private readonly toast = inject(ToastService);

  protected readonly data = signal<Analytics | null>(null);
  /** Mismo periodo inmediatamente anterior (para las variaciones). */
  protected readonly previous = signal<Analytics | null>(null);
  protected readonly tab = signal<'summary' | 'forecast'>('summary');
  protected readonly previousRangeLabel = signal('');
  protected readonly trends = computed(() => {
    const c = this.data();
    const p = this.previous();
    return {
      total: c && p ? trend(c.summary.totalRevenue, p.summary.totalRevenue) : null,
      tickets: c && p ? trend(c.summary.ticketRevenue, p.summary.ticketRevenue) : null,
      pos: c && p ? trend(c.summary.posRevenue, p.summary.posRevenue) : null,
      vip: c && p ? trend(c.vip.reservations, p.vip.reservations) : null,
    };
  });
  protected readonly venues = signal<Venue[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly downloading = signal<ReportType | null>(null);
  protected filters: AnalyticsFilters = { ...presetRange(30), venueId: '' };
  protected readonly today = isoDate(new Date());

  protected readonly reports: { type: ReportType; label: string }[] = [
    { type: 'orders', label: 'Pedidos de entradas' },
    { type: 'pos-sales', label: 'Ventas del TPV' },
    { type: 'products', label: 'Productos vendidos' },
    { type: 'promoters', label: 'Promotores' },
  ];

  /** Barras apiladas del grafico diario (entradas + TPV). */
  protected readonly dayBars = computed(() => barHeights(this.data()?.revenueByDay ?? []));
  /** Barras del grafico por hora (0-23 h), rellenando las horas sin ventas. */
  protected readonly hourBars = computed(() => {
    const byHour = new Map((this.data()?.posByHour ?? []).map((h) => [h.hour, h]));
    const hours = Array.from({ length: 24 }, (_, hour) => byHour.get(hour) ?? { hour, sales: 0, revenue: 0 });
    const max = Math.max(1, ...hours.map((h) => h.revenue));
    return hours.map((h) => ({ ...h, height: Math.round((h.revenue / max) * 100) }));
  });

  ngOnInit(): void {
    this.venuesService.list().subscribe({ next: (v) => this.venues.set(v), error: () => this.venues.set([]) });
    this.load();
  }

  protected applyPreset(days: number): void {
    this.filters = { ...presetRange(days), venueId: this.filters.venueId };
    this.load();
  }

  protected load(): void {
    if (!this.filters.from || !this.filters.to || this.filters.to < this.filters.from) {
      this.error.set('Revisa las fechas: "desde" no puede ser posterior a "hasta"');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const prev = previousRange(this.filters.from, this.filters.to);
    this.previousRangeLabel.set(`${prev.from} a ${prev.to}`);
    this.previous.set(null);
    this.analyticsService.overview({ ...prev, venueId: this.filters.venueId }).subscribe({
      next: (p) => this.previous.set(p),
      error: () => this.previous.set(null),
    });
    this.analyticsService.overview(this.filters).subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected download(type: ReportType): void {
    this.downloading.set(type);
    this.analyticsService.report(type, this.filters).subscribe({
      next: (response) => {
        const name = fileNameFrom(response.headers.get('Content-Disposition'), `bloop-${type}.csv`);
        if (response.body) downloadBlob(response.body, name);
        this.downloading.set(null);
      },
      error: (err) => {
        this.toast.error(errorMessage(err, 'No se pudo generar el informe'));
        this.downloading.set(null);
      },
    });
  }

  protected absPct(change: number): number {
    return Math.abs(change * 100);
  }

  protected ratio(part: number, total: number): number {
    return total > 0 ? part / total : 0;
  }
}
