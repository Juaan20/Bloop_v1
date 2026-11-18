import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { Icon } from '../../shared/ui/icon';
import { Logo } from '../../shared/ui/logo';
import { Venue } from '../venues/venue.models';
import { VenuesService } from '../venues/venues.service';
import { ORDER_STATUS, VipOrder, VipOrderStatus } from './vip.models';
import { VipService } from './vip.service';

/** Cada cuanto se consultan pedidos nuevos (sin websockets: sencillo y suficiente en el local). */
const POLL_MS = 5_000;

/** Columnas del panel: que estados entran en cada una. */
const COLUMNS: { title: string; statuses: VipOrderStatus[]; empty: string }[] = [
  { title: 'Nuevos', statuses: ['NEW'], empty: 'Sin pedidos nuevos' },
  { title: 'Por preparar', statuses: ['PENDING', 'PREPARING'], empty: 'Nada en preparacion' },
  { title: 'Listos para llevar', statuses: ['READY', 'ON_THE_WAY'], empty: 'Nada pendiente de entrega' },
];

/**
 * Panel del personal de servicio VIP (pensado para tablet o pantalla tactil).
 * Los pedidos de las mesas llegan solos (consulta cada 5 s) y se avanzan con un toque:
 * Nuevo -> Pendiente -> En preparacion -> Preparado -> En camino -> Entregado.
 */
@Component({
  selector: 'vf-service-board-page',
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, Icon, Logo],
  template: `
    <div class="flex min-h-screen flex-col bg-zinc-950 text-zinc-100">
      <header class="sticky top-0 z-20 flex flex-wrap items-center justify-between gap-3 border-b border-zinc-800 bg-zinc-950/90 px-4 py-3 backdrop-blur">
        <div class="flex items-center gap-3">
          <a routerLink="/admin" class="rounded-md" aria-label="Volver al panel"><vf-logo [compact]="true" /></a>
          <div>
            <h1 class="font-display text-lg font-semibold text-white">Servicio VIP</h1>
            <p class="text-xs text-zinc-500">{{ auth.displayName() }}</p>
          </div>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <div class="hidden items-center gap-4 rounded-lg border border-zinc-800 px-3 py-1.5 text-xs sm:flex">
            <span class="inline-flex gap-1"><span class="font-semibold text-white tabular-nums">{{ waitingTables() }}</span><span class="text-zinc-500">{{ waitingTables() === 1 ? 'mesa esperando' : 'mesas esperando' }}</span></span>
            <span class="inline-flex gap-1"><span class="font-semibold text-white tabular-nums">{{ avgDelivery() ?? '—' }}</span><span class="text-zinc-500">min de media hasta entregar</span></span>
          </div>
          <select class="vf-input h-10 w-auto py-0" [ngModel]="venueId()" (ngModelChange)="selectVenue($event)" aria-label="Local">
            @for (v of venues(); track v.id) { <option [value]="v.id">{{ v.name }}</option> }
          </select>
          <span class="inline-flex items-center gap-1.5 text-xs" [class]="error() ? 'text-red-300' : 'text-emerald-300'" role="status">
            <span class="size-2 rounded-full bg-current" [class.animate-pulse]="!error()"></span>{{ error() ? 'Sin conexion' : 'En directo' }}
          </span>
        </div>
      </header>

      @if (loading()) {
        <div class="grid flex-1 gap-4 p-4 lg:grid-cols-3">@for (c of columns; track c.title) { <div class="vf-skeleton h-64 rounded-2xl"></div> }</div>
      } @else {
        <main class="grid flex-1 gap-4 p-4 lg:grid-cols-3">
          @for (col of columns; track col.title) {
            <section class="flex flex-col rounded-2xl border border-zinc-800 bg-zinc-900/30" [attr.aria-label]="col.title">
              <h2 class="flex items-center justify-between border-b border-zinc-800 px-4 py-3 text-sm font-semibold text-zinc-200">
                {{ col.title }} <span class="rounded-full bg-zinc-800 px-2 py-0.5 text-xs tabular-nums">{{ byColumn()[$index].length }}</span>
              </h2>
              <ul class="flex-1 space-y-3 overflow-y-auto p-3">
                @for (o of byColumn()[$index]; track o.id) {
                  <li class="animate-fade-in rounded-xl border bg-zinc-900 p-4 shadow-(--shadow-card)" [class]="o.status === 'NEW' ? 'border-rose-400/60' : 'border-zinc-800'">
                    <div class="flex items-start justify-between gap-2">
                      <div>
                        <p class="font-display text-2xl font-semibold text-white">{{ o.tableName }}</p>
                        <p class="text-xs text-zinc-500">{{ o.zoneName }} · {{ o.source === 'GUEST' ? 'pedido por QR' : 'anotado por personal' }}</p>
                      </div>
                      <div class="text-right">
                        <p class="text-lg font-semibold tabular-nums" [class]="minutes(o) >= 10 ? 'text-rose-300' : minutes(o) >= 5 ? 'text-amber-300' : 'text-zinc-200'">{{ minutes(o) }} min</p>
                        <p class="text-[11px] text-zinc-500">{{ o.createdAt | date: 'HH:mm' }}</p>
                      </div>
                    </div>
                    <ul class="mt-3 space-y-1.5">
                      @for (i of o.items; track $index) {
                        <li class="flex items-center justify-between gap-2 text-base">
                          <span class="text-zinc-100"><span class="font-semibold tabular-nums">{{ i.quantity }}×</span> {{ i.productName }}</span>
                          <span class="vf-badge" [class]="i.included ? 'bg-emerald-500/10 text-emerald-300 ring-emerald-500/20' : 'bg-amber-500/10 text-amber-300 ring-amber-500/20'">
                            {{ i.included ? 'Paquete' : 'Adicional ' + (i.lineTotal | currency: 'EUR') }}
                          </span>
                        </li>
                      }
                    </ul>
                    @if (o.notes) { <p class="mt-2 rounded-lg bg-zinc-950/60 px-3 py-2 text-sm text-zinc-300">“{{ o.notes }}”</p> }
                    <p class="mt-2 text-xs text-zinc-500">{{ statusInfo(o.status).label }}@if (o.preparedByName) { · prepara {{ o.preparedByName }} }</p>
                    <div class="mt-3 flex gap-2">
                      @if (statusInfo(o.status).next; as next) {
                        <button type="button" class="vf-btn-primary min-h-12 flex-1 text-base" [disabled]="busyId() === o.id" (click)="advance(o, next)">
                          {{ statusInfo(o.status).action }} <vf-icon name="arrow-right" class="size-4" />
                        </button>
                      }
                      <button type="button" class="vf-btn-ghost min-h-12" [disabled]="busyId() === o.id" (click)="cancel(o)" aria-label="Cancelar pedido">Cancelar</button>
                    </div>
                  </li>
                } @empty {
                  <li class="py-10 text-center text-sm text-zinc-500">{{ col.empty }}</li>
                }
              </ul>
            </section>
          }
        </main>

        <!-- Historial de la noche -->
        <section class="mx-4 mb-4 rounded-2xl border border-zinc-800">
          <h2 class="border-b border-zinc-800 px-4 py-3 text-sm font-semibold text-zinc-200">Historial (ultimas horas)</h2>
          <div class="overflow-x-auto">
            <table class="vf-table">
              <thead><tr><th>Mesa</th><th>Productos</th><th>Pedido</th><th>Preparado por</th><th>Entregado por</th><th class="text-right">Tiempo</th><th>Estado</th></tr></thead>
              <tbody>
                @for (o of history(); track o.id) {
                  <tr>
                    <td class="font-medium text-white">{{ o.tableName }}</td>
                    <td>@for (i of o.items; track $index) { <span class="mr-2">{{ i.quantity }}× {{ i.productName }}</span> }</td>
                    <td class="tabular-nums">{{ o.createdAt | date: 'HH:mm' }}</td>
                    <td>{{ o.preparedByName ?? '—' }}</td>
                    <td>{{ o.deliveredByName ?? '—' }}</td>
                    <td class="text-right tabular-nums">{{ o.deliveredAt ? deliveryMinutes(o) + ' min' : '—' }}</td>
                    <td><span class="vf-badge" [class]="statusInfo(o.status).tone">{{ statusInfo(o.status).label }}</span></td>
                  </tr>
                } @empty {
                  <tr><td colspan="7" class="py-6 text-center text-zinc-500">Aun no hay pedidos terminados.</td></tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      }
    </div>
  `,
})
export class ServiceBoardPage implements OnInit {
  private readonly vip = inject(VipService);
  private readonly venuesService = inject(VenuesService);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);

  protected readonly columns = COLUMNS;
  protected readonly venues = signal<Venue[]>([]);
  protected readonly venueId = signal('');
  protected readonly orders = signal<VipOrder[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busyId = signal<string | null>(null);
  /** Reloj para refrescar los minutos transcurridos sin pedir datos. */
  private readonly now = signal(Date.now());

  protected readonly byColumn = computed(() => COLUMNS.map((c) => this.orders().filter((o) => c.statuses.includes(o.status))));
  protected readonly history = computed(() =>
    this.orders()
      .filter((o) => o.status === 'DELIVERED' || o.status === 'CANCELLED')
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
  );
  protected readonly waitingTables = computed(
    () => new Set(this.orders().filter((o) => o.status !== 'DELIVERED' && o.status !== 'CANCELLED').map((o) => o.reservationId)).size,
  );
  protected readonly avgDelivery = computed(() => {
    const done = this.orders().filter((o) => o.deliveredAt);
    if (done.length === 0) return null;
    return Math.round(done.reduce((s, o) => s + this.deliveryMinutes(o), 0) / done.length);
  });

  constructor() {
    const poll = setInterval(() => this.load(), POLL_MS);
    const clock = setInterval(() => this.now.set(Date.now()), 30_000);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(poll);
      clearInterval(clock);
    });
  }

  ngOnInit(): void {
    this.venuesService.list().subscribe({
      next: (v) => {
        this.venues.set(v);
        this.selectVenue(v[0]?.id ?? '');
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected selectVenue(id: string): void {
    this.venueId.set(id);
    this.loading.set(true);
    this.load();
  }

  private load(): void {
    if (!this.venueId()) {
      this.loading.set(false);
      return;
    }
    this.vip.board(this.venueId()).subscribe({
      next: (o) => {
        this.orders.set(o);
        this.loading.set(false);
        this.error.set(null);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected statusInfo(s: VipOrderStatus) {
    return ORDER_STATUS[s];
  }

  protected minutes(o: VipOrder): number {
    return Math.max(0, Math.floor((this.now() - new Date(o.createdAt).getTime()) / 60_000));
  }

  protected deliveryMinutes(o: VipOrder): number {
    return Math.round((new Date(o.deliveredAt!).getTime() - new Date(o.createdAt).getTime()) / 60_000);
  }

  protected advance(o: VipOrder, next: VipOrderStatus): void {
    this.change(o, next, `${o.tableName}: ${ORDER_STATUS[next].label.toLowerCase()}`);
  }

  protected cancel(o: VipOrder): void {
    if (confirm(`¿Cancelar el pedido de ${o.tableName}? No se cobrara.`)) this.change(o, 'CANCELLED', 'Pedido cancelado');
  }

  private change(o: VipOrder, status: VipOrderStatus, message: string): void {
    this.busyId.set(o.id);
    this.vip.changeStatus(o.id, status).subscribe({
      next: (updated) => {
        this.orders.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
        this.busyId.set(null);
        this.toast.success(message);
      },
      error: (err) => {
        this.busyId.set(null);
        this.toast.error(errorMessage(err));
        this.load();
      },
    });
  }
}
