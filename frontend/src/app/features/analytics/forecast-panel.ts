import { CurrencyPipe, DatePipe, DecimalPipe, PercentPipe, TitleCasePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { errorMessage } from '../../core/services/api-error';
import { Icon } from '../../shared/ui/icon';
import { AnalyticsService, Forecast, ForecastConfidence, UpcomingSession } from './analytics.service';

const W = 600;
const H = 200;

const CONFIDENCE: Record<ForecastConfidence, { label: string; tone: string }> = {
  HIGH: { label: 'Confianza alta', tone: 'bg-emerald-500/10 text-emerald-300 ring-emerald-500/25' },
  MEDIUM: { label: 'Confianza media', tone: 'bg-amber-500/10 text-amber-300 ring-amber-500/25' },
  LOW: { label: 'Confianza baja', tone: 'bg-rose-500/10 text-rose-300 ring-rose-500/25' },
  NONE: { label: 'Sin datos suficientes', tone: 'bg-zinc-500/10 text-zinc-300 ring-zinc-500/25' },
};

/**
 * Prevision de asistencia de una sesion y comparativa con noches similares (mismo local y dia de la semana).
 * Todo el calculo lo hace el backend con datos reales; aqui solo se pinta y se explica.
 */
@Component({
  selector: 'vf-forecast-panel',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, PercentPipe, TitleCasePipe, FormsModule, Icon],
  template: `
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h2 class="font-display text-lg font-semibold text-white">Prevision de asistencia</h2>
        <p class="text-sm text-zinc-400">Basada en el ritmo de venta actual y en noches comparables del mismo local.</p>
      </div>
      <select class="vf-input h-9 w-auto max-w-full py-0" [ngModel]="sessionId()" (ngModelChange)="select($event)" aria-label="Sesion a prever">
        @for (s of sessions(); track s.sessionId) {
          <option [value]="s.sessionId">{{ s.startsAt | date: 'EEE d MMM HH:mm' }} · {{ s.eventName }} ({{ s.venueName }})</option>
        }
      </select>
    </div>

    @if (error()) {
      <p class="vf-card mt-4 text-sm text-red-300">{{ error() }}</p>
    } @else if (sessions().length === 0 && !loading()) {
      <p class="vf-card mt-4 text-sm text-zinc-400">No hay sesiones en los proximos 60 dias.</p>
    } @else if (loading() || !data()) {
      <div class="mt-4 grid gap-4 lg:grid-cols-3"><div class="vf-skeleton h-44"></div><div class="vf-skeleton h-44 lg:col-span-2"></div></div>
    } @else if (data(); as f) {
      <div class="mt-4 grid gap-4 lg:grid-cols-3">
        <!-- Estimacion -->
        <section class="vf-card relative overflow-hidden" aria-labelledby="fc-title">
          <div class="pointer-events-none absolute -top-12 -right-12 size-32 rounded-full bg-accent-500/15 blur-2xl"></div>
          <div class="flex items-center justify-between gap-2">
            <h3 id="fc-title" class="text-xs font-medium text-zinc-400">{{ f.session.future ? 'Asistencia estimada' : 'Asistencia real' }}</h3>
            <span class="vf-badge" [class]="confidence(f.forecast.confidence).tone">{{ confidence(f.forecast.confidence).label }}</span>
          </div>
          @if (f.forecast.method === 'NONE') {
            <p class="mt-4 font-display text-2xl font-semibold text-zinc-300">No disponible</p>
          } @else if (f.forecast.method === 'ACTUAL') {
            <p class="mt-3 font-display text-[32px] font-semibold tracking-tight text-white tabular-nums">{{ f.forecast.attendanceMid | number }}</p>
            <p class="text-xs text-zinc-500">de {{ f.session.ticketsSold | number }} vendidas ({{ f.forecast.attendanceRate | percent: '1.0-0' }})</p>
          } @else {
            <p class="mt-3 font-display text-[32px] font-semibold tracking-tight text-white tabular-nums">{{ f.forecast.attendanceLow | number }}–{{ f.forecast.attendanceHigh | number }}</p>
            <p class="text-xs text-zinc-500">personas · valor central {{ f.forecast.attendanceMid | number }}</p>
            <!-- Barra de aforo -->
            <div class="relative mt-4 h-2 rounded-full bg-zinc-800" role="img" [attr.aria-label]="'Rango estimado sobre un aforo de ' + f.session.capacity">
              <div class="absolute inset-y-0 rounded-full bg-accent-400/70" [style.left.%]="pct(f.forecast.attendanceLow, f.session.capacity)" [style.width.%]="pct(f.forecast.attendanceHigh - f.forecast.attendanceLow, f.session.capacity)"></div>
              <div class="absolute inset-y-0 w-0.5 bg-white" [style.left.%]="pct(f.session.ticketsSold, f.session.capacity)" title="Vendidas hoy"></div>
            </div>
            <p class="mt-1.5 flex justify-between text-[11px] text-zinc-500"><span>Vendidas hoy {{ f.session.ticketsSold | number }}</span><span>Aforo {{ f.session.capacity | number }}</span></p>
          }
          <p class="mt-4 text-sm leading-relaxed text-zinc-300">{{ f.forecast.explanation }}</p>
          @if (f.forecast.method === 'PACE' || f.forecast.method === 'HISTORICAL') {
            <dl class="mt-4 grid grid-cols-2 gap-2 text-xs">
              <div class="rounded-lg bg-zinc-950/50 p-2.5"><dt class="text-zinc-500">Ventas finales estimadas</dt><dd class="mt-0.5 font-medium text-zinc-100 tabular-nums">{{ f.forecast.soldLow | number }}–{{ f.forecast.soldHigh | number }}</dd></div>
              <div class="rounded-lg bg-zinc-950/50 p-2.5"><dt class="text-zinc-500">Tasa de asistencia historica</dt><dd class="mt-0.5 font-medium text-zinc-100 tabular-nums">{{ f.forecast.attendanceRate | percent: '1.0-0' }}</dd></div>
            </dl>
            <p class="mt-3 flex items-start gap-1.5 text-[11px] text-zinc-500"><vf-icon name="sparkle" class="mt-0.5 size-3" />
              Datos usados: {{ f.forecast.dataPoints }} {{ plural(f.session.weekday) }} del mismo local, entradas vendidas y escaneadas en puerta. Es una estimacion, no una garantia.</p>
          }
        </section>

        <!-- Curva de ventas acumuladas -->
        <section class="vf-card lg:col-span-2" aria-labelledby="curve-title">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h3 id="curve-title" class="text-sm font-medium text-zinc-300">Evolucion de ventas antes del evento</h3>
            <ul class="flex gap-4 text-xs text-zinc-400">
              <li class="flex items-center gap-1.5"><span class="h-0.5 w-4 rounded bg-accent-400"></span>Esta sesion</li>
              <li class="flex items-center gap-1.5"><span class="h-0.5 w-4 rounded border-t-2 border-dashed border-brand-300"></span>Media de {{ f.comparables.length }} comparables</li>
            </ul>
          </div>
          @if (curve(); as c) {
            <div class="relative mt-5 h-52" (mouseleave)="hover.set(null)">
              <svg [attr.viewBox]="'0 0 ' + c.w + ' ' + c.h" preserveAspectRatio="none" class="absolute inset-0 size-full overflow-visible" aria-hidden="true">
                @for (g of [0.25, 0.5, 0.75]; track g) {
                  <line x1="0" [attr.x2]="c.w" [attr.y1]="c.h * g" [attr.y2]="c.h * g" stroke="var(--color-zinc-800)" stroke-dasharray="3 4" vector-effect="non-scaling-stroke" />
                }
                @if (c.avgPath) { <path [attr.d]="c.avgPath" fill="none" stroke="var(--color-brand-300)" stroke-width="2" stroke-dasharray="5 5" vector-effect="non-scaling-stroke" /> }
                @if (c.curPath) { <path [attr.d]="c.curPath" fill="none" stroke="var(--color-accent-400)" stroke-width="2.5" vector-effect="non-scaling-stroke" /> }
              </svg>
              <div class="absolute inset-0 flex">
                @for (p of c.points; track p.daysBefore; let i = $index) { <div class="h-full flex-1" (mouseenter)="hover.set(i)"></div> }
              </div>
              @if (hover() !== null) {
                @let p = c.points[hover()!];
                <div class="pointer-events-none absolute top-0 bottom-0 w-px bg-zinc-700" [style.left.%]="p.left"></div>
                <div class="pointer-events-none absolute -top-2 z-10 -translate-x-1/2 -translate-y-full rounded-lg border border-zinc-700 bg-zinc-900 px-2.5 py-1.5 text-xs whitespace-nowrap shadow-(--shadow-pop)"
                     [style.left.%]="p.left < 15 ? 15 : p.left > 85 ? 85 : p.left">
                  <p class="text-zinc-400">{{ p.daysBefore === 0 ? 'Dia del evento' : p.daysBefore + ' dias antes' }}</p>
                  @if (p.current !== null) { <p class="text-accent-300 tabular-nums">Esta sesion: {{ p.current | number }}</p> }
                  @if (p.comparableAverage !== null) { <p class="text-brand-300 tabular-nums">Media comparables: {{ p.comparableAverage | number }}</p> }
                </div>
              }
            </div>
            <div class="mt-2 flex justify-between text-[11px] text-zinc-500"><span>21 dias antes</span><span>Dia del evento</span></div>
          } @else {
            <p class="py-16 text-center text-sm text-zinc-500">Aun no hay ventas que dibujar.</p>
          }
        </section>
      </div>

      <!-- Noches comparables -->
      <section class="vf-panel mt-4 overflow-hidden" aria-labelledby="cmp-title">
        <h3 id="cmp-title" class="px-5 pt-5 pb-3 text-sm font-medium text-zinc-300">{{ plural(f.session.weekday) | titlecase }} anteriores en {{ f.session.venueName }}</h3>
        @if (f.comparables.length === 0) {
          <p class="px-5 pb-5 text-sm text-zinc-500">No hay noches comparables con ventas en el ultimo ano.</p>
        } @else {
          <div class="overflow-x-auto">
            <table class="vf-table">
              <thead><tr><th>Noche</th><th class="text-right">Vendidas</th><th class="text-right">A esta antelacion</th><th class="text-right">Asistencia</th><th class="text-right">% usadas</th><th class="text-right">Reservas VIP</th><th class="text-right">Ingresos</th><th class="text-right">vs. esta sesion</th></tr></thead>
              <tbody>
                @for (c of f.comparables; track c.sessionId) {
                  <tr>
                    <td><p class="font-medium text-white">{{ c.eventName }} @if (c.sameEvent) { <span class="vf-badge ml-1 bg-brand-500/10 text-brand-300 ring-brand-500/25">Edicion anterior</span> }</p>
                      <p class="text-xs text-zinc-500">{{ c.startsAt | date: 'EEE d MMM yyyy' }}</p></td>
                    <td class="text-right tabular-nums">{{ c.ticketsSold | number }}</td>
                    <td class="text-right tabular-nums">{{ f.session.future ? (c.soldAtSameLead | number) : '—' }}</td>
                    <td class="text-right tabular-nums">{{ c.checkedIn | number }}</td>
                    <td class="text-right tabular-nums">{{ c.attendanceRate | percent: '1.0-0' }}</td>
                    <td class="text-right tabular-nums">{{ c.vipReservations }}</td>
                    <td class="text-right tabular-nums">{{ c.revenue | currency: 'EUR' : 'symbol' : '1.0-0' }}</td>
                    <td class="text-right text-xs tabular-nums" [class]="delta(f, c) >= 0 ? 'text-emerald-300' : 'text-rose-300'">
                      {{ f.session.future ? deltaLabel(f, c) : '—' }}
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <p class="px-5 py-3 text-[11px] text-zinc-500">"vs. esta sesion" compara las entradas vendidas hoy con las que llevaba esa noche a la misma antelacion.</p>
        }
      </section>
    }
  `,
})
export class ForecastPanel implements OnInit {
  private readonly analytics = inject(AnalyticsService);

  protected readonly sessions = signal<UpcomingSession[]>([]);
  protected readonly sessionId = signal('');
  protected readonly data = signal<Forecast | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly hover = signal<number | null>(null);

  protected readonly curve = computed(() => {
    const f = this.data();
    if (!f) return null;
    const pts = f.salesCurve;
    const max = Math.max(1, ...pts.map((p) => Math.max(p.current ?? 0, p.comparableAverage ?? 0)));
    if (pts.every((p) => !p.current && !p.comparableAverage)) return null;
    const step = W / (pts.length - 1);
    const y = (v: number) => H - (v / max) * (H - 10);
    const path = (key: 'current' | 'comparableAverage') => {
      const valid = pts.map((p, i) => ({ v: p[key], x: i * step })).filter((p) => p.v !== null);
      return valid.length ? valid.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${y(p.v!).toFixed(1)}`).join(' ') : null;
    };
    return {
      w: W,
      h: H,
      curPath: path('current'),
      avgPath: path('comparableAverage'),
      points: pts.map((p, i) => ({ ...p, left: ((i * step) / W) * 100 })),
    };
  });

  ngOnInit(): void {
    this.analytics.upcomingSessions().subscribe({
      next: (s) => {
        this.sessions.set(s);
        if (s.length) this.select(s[0].sessionId);
        else this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected select(id: string): void {
    this.sessionId.set(id);
    this.loading.set(true);
    this.analytics.forecast(id).subscribe({
      next: (f) => {
        this.data.set(f);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected confidence(c: ForecastConfidence) {
    return CONFIDENCE[c];
  }

  /** "viernes" -> "viernes", "sabado" -> "sabados". */
  protected plural(weekday: string): string {
    return weekday.endsWith('s') ? weekday : `${weekday}s`;
  }

  protected pct(value: number, total: number): number {
    return total > 0 ? Math.min(100, Math.max(0, (value / total) * 100)) : 0;
  }

  protected delta(f: Forecast, c: Forecast['comparables'][number]): number {
    return f.session.ticketsSold - c.soldAtSameLead;
  }

  protected deltaLabel(f: Forecast, c: Forecast['comparables'][number]): string {
    const d = this.delta(f, c);
    if (c.soldAtSameLead === 0) return d > 0 ? `+${d}` : '0';
    const pct = Math.round((d / c.soldAtSameLead) * 100);
    return `${d >= 0 ? '+' : ''}${d} (${pct >= 0 ? '+' : ''}${pct} %)`;
  }
}
