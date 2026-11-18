import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Page } from '../../core/models/api.models';
import { errorMessage } from '../../core/services/api-error';
import { EmptyState } from '../../shared/ui/empty-state';
import { Icon } from '../../shared/ui/icon';
import { PageHeader } from '../../shared/ui/page-header';
import { Pagination } from '../../shared/ui/pagination';
import { Spinner } from '../../shared/ui/spinner';
import { StatusBadge } from '../../shared/ui/status-badge';
import { localInputToIso } from '../../shared/utils/date-time';
import { EventSession, EventSummary } from '../events/event.models';
import { EventsService } from '../events/events.service';
import { Venue } from '../venues/venue.models';
import { VenuesService } from '../venues/venues.service';
import { Order, OrderFilters, OrderSummary } from './order.models';
import { OrdersService } from './orders.service';

type Draft = Required<Omit<OrderFilters, 'from' | 'to'>> & { from: string; to: string };

const EMPTY: Draft = {
  status: '',
  eventId: '',
  sessionId: '',
  venueId: '',
  from: '',
  to: '',
  paymentStatus: '',
  paymentProvider: '',
  ticketCategory: '',
  search: '',
  sort: 'recent',
};

/**
 * Pedidos de entradas con filtros combinables que consulta el backend (no filtra lo ya cargado):
 * evento, dia del evento, local, fechas de compra, estado del pedido y del pago, metodo de pago,
 * tipo de entrada y cliente. Arriba, el resumen (totales de TODAS las paginas).
 */
@Component({
  selector: 'vf-order-list-page',
  imports: [
    Pagination,
    CurrencyPipe,
    DatePipe,
    FormsModule,
    RouterLink,
    Icon,
    PageHeader,
    Spinner,
    EmptyState,
    StatusBadge,
  ],
  template: `
    <vf-page-header title="Pedidos" subtitle="Ventas de entradas de todos tus eventos" />

    <form class="vf-card mb-5 space-y-3" (ngSubmit)="search()">
      <div class="flex flex-wrap gap-2">
        <input
          name="search"
          type="search"
          class="vf-input-search min-w-56 flex-1"
          placeholder="Referencia, nombre o email del cliente"
          [(ngModel)]="draft.search"
        />
        <select
          name="event"
          class="vf-input w-auto min-w-48"
          [(ngModel)]="draft.eventId"
          (ngModelChange)="selectEvent($event)"
          aria-label="Evento"
        >
          <option value="">Todos los eventos</option>
          @for (e of events(); track e.id) {
            <option [value]="e.id">{{ e.name }}</option>
          }
        </select>
        <select
          name="session"
          class="vf-input w-auto min-w-44"
          [(ngModel)]="draft.sessionId"
          (ngModelChange)="search()"
          [disabled]="!draft.eventId"
          aria-label="Dia del evento"
        >
          <option value="">{{ draft.eventId ? 'Todos los dias' : 'Elige un evento' }}</option>
          @for (s of sessions(); track s.id) {
            <option [value]="s.id">
              {{ s.title ? s.title + ' · ' : '' }}{{ s.startsAt | date: 'EEE d MMM, HH:mm' }}
            </option>
          }
        </select>
        <select
          name="status"
          class="vf-input w-auto"
          [(ngModel)]="draft.status"
          (ngModelChange)="search()"
          aria-label="Estado del pedido"
        >
          <option value="">Todos los estados</option>
          <option value="PAID">Pagados</option>
          <option value="PENDING_PAYMENT">Pendientes de pago</option>
          <option value="REFUNDED">Reembolsados</option>
          <option value="EXPIRED">Caducados</option>
          <option value="CANCELLED">Cancelados</option>
        </select>
        <button
          type="button"
          class="vf-btn-ghost"
          (click)="advanced.set(!advanced())"
          [attr.aria-expanded]="advanced()"
        >
          <vf-icon name="filter" class="size-4" /> Mas filtros{{
            activeAdvanced() ? ' (' + activeAdvanced() + ')' : ''
          }}
        </button>
        <button type="submit" class="vf-btn-secondary">Buscar</button>
      </div>
      @if (advanced()) {
        <div class="grid gap-3 border-t border-zinc-800 pt-3 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <label class="vf-label" for="from">Comprados desde</label
            ><input
              id="from"
              name="from"
              type="datetime-local"
              class="vf-input"
              [(ngModel)]="draft.from"
            />
          </div>
          <div>
            <label class="vf-label" for="to">Hasta</label
            ><input
              id="to"
              name="to"
              type="datetime-local"
              class="vf-input"
              [(ngModel)]="draft.to"
            />
          </div>
          <div>
            <label class="vf-label" for="pstatus">Estado del pago</label>
            <select id="pstatus" name="pstatus" class="vf-input" [(ngModel)]="draft.paymentStatus">
              <option value="">Cualquiera</option>
              <option value="SUCCEEDED">Cobrado</option>
              <option value="PENDING">Pendiente</option>
              <option value="FAILED">Fallido</option>
              <option value="REFUNDED">Devuelto</option>
            </select>
          </div>
          <div>
            <label class="vf-label" for="method">Metodo de pago</label>
            <select id="method" name="method" class="vf-input" [(ngModel)]="draft.paymentProvider">
              <option value="">Cualquiera</option>
              <option value="STRIPE">Tarjeta (Stripe)</option>
              <option value="SIMULATED">Pago simulado (pruebas)</option>
              <option value="FREE">Gratuito</option>
            </select>
          </div>
          <div>
            <label class="vf-label" for="ttype">Tipo de entrada</label>
            <select id="ttype" name="ttype" class="vf-input" [(ngModel)]="draft.ticketCategory">
              <option value="">Cualquiera</option>
              <option value="GENERAL">General</option>
              <option value="EARLY_BIRD">Anticipada</option>
              <option value="VIP">VIP</option>
              <option value="FREE">Gratuita</option>
            </select>
          </div>
          <div>
            <label class="vf-label" for="venue">Establecimiento</label>
            <select id="venue" name="venue" class="vf-input" [(ngModel)]="draft.venueId">
              <option value="">Todos</option>
              @for (v of venues(); track v.id) {
                <option [value]="v.id">{{ v.name }}</option>
              }
            </select>
          </div>
          <div>
            <label class="vf-label" for="sort">Ordenar por</label>
            <select id="sort" name="sort" class="vf-input" [(ngModel)]="draft.sort">
              <option value="recent">Mas recientes</option>
              <option value="oldest">Mas antiguos</option>
              <option value="amount_desc">Importe mayor</option>
              <option value="amount_asc">Importe menor</option>
            </select>
          </div>
          <div class="flex items-end">
            <button type="button" class="vf-btn-ghost w-full" (click)="reset()">
              Quitar filtros
            </button>
          </div>
        </div>
      }
    </form>

    <!-- Resumen de los resultados (todas las paginas) -->
    @if (summary(); as s) {
      <dl class="mb-5 grid grid-cols-2 gap-3 lg:grid-cols-5">
        <div class="vf-card py-3">
          <dt class="text-xs text-zinc-500">Pedidos</dt>
          <dd class="mt-1 font-display text-2xl font-semibold text-white tabular-nums">
            {{ s.orders }}
          </dd>
        </div>
        <div class="vf-card py-3">
          <dt class="text-xs text-zinc-500">Cobrado</dt>
          <dd class="mt-1 font-display text-2xl font-semibold text-emerald-300 tabular-nums">
            {{ s.paidAmount | currency: 'EUR' }}
          </dd>
          <dd class="text-xs text-zinc-500">{{ s.paidOrders }} pagados</dd>
        </div>
        <div class="vf-card py-3">
          <dt class="text-xs text-zinc-500">Entradas vendidas</dt>
          <dd class="mt-1 font-display text-2xl font-semibold text-white tabular-nums">
            {{ s.tickets }}
          </dd>
        </div>
        <div class="vf-card py-3">
          <dt class="text-xs text-zinc-500">Pendientes de pago</dt>
          <dd class="mt-1 font-display text-2xl font-semibold text-amber-300 tabular-nums">
            {{ s.pendingOrders }}
          </dd>
        </div>
        <div class="vf-card py-3">
          <dt class="text-xs text-zinc-500">Reembolsado</dt>
          <dd class="mt-1 font-display text-2xl font-semibold text-red-300 tabular-nums">
            {{ s.refundedAmount | currency: 'EUR' }}
          </dd>
          <dd class="text-xs text-zinc-500">
            {{ s.refundedOrders }} pedidos · descuentos {{ s.discountAmount | currency: 'EUR' }}
          </dd>
        </div>
      </dl>
    }

    @if (loading() && !result()) {
      <vf-spinner />
    } @else if (error()) {
      <p class="vf-card text-red-400">{{ error() }}</p>
    } @else if (result(); as page) {
      @if (page.content.length === 0) {
        <vf-empty-state
          title="Sin pedidos"
          [description]="
            filtered()
              ? 'Ningun pedido cumple estos filtros.'
              : 'Cuando los clientes compren entradas, apareceran aqui.'
          "
          icon="card"
        />
      } @else {
        <div class="vf-card overflow-x-auto p-0" [class.opacity-60]="loading()">
          <table class="vf-table">
            <thead>
              <tr>
                <th>Referencia</th>
                <th>Cliente</th>
                <th>Evento y dia</th>
                <th class="text-right">Entradas</th>
                <th class="text-right">Total</th>
                <th>Estado</th>
                <th>Comprado</th>
              </tr>
            </thead>
            <tbody>
              @for (o of page.content; track o.id) {
                <tr>
                  <td>
                    <a
                      [routerLink]="['/admin/ventas', o.id]"
                      class="font-mono text-sm text-white hover:underline"
                      >{{ o.reference }}</a
                    >
                  </td>
                  <td>
                    <p class="text-zinc-200">{{ o.buyerName }}</p>
                    <p class="text-xs text-zinc-500">{{ o.buyerEmail }}</p>
                  </td>
                  <td>
                    <p class="text-zinc-200">{{ o.eventName }}</p>
                    <p class="text-xs text-zinc-500">
                      {{ o.sessionTitle ? o.sessionTitle + ' · ' : ''
                      }}{{ o.sessionStartsAt | date: 'EEE d MMM, HH:mm' }}
                    </p>
                  </td>
                  <td class="text-right text-zinc-300 tabular-nums">{{ o.ticketCount }}</td>
                  <td class="text-right font-medium text-white tabular-nums">
                    {{ o.totalAmount | currency: o.currency }}
                  </td>
                  <td><vf-status-badge [status]="o.status" /></td>
                  <td class="text-zinc-400">{{ o.createdAt | date: 'd MMM HH:mm' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <vf-pagination [page]="page" (pageChange)="goToPage($event)" />
      }
    }
  `,
})
export class OrderListPage implements OnInit {
  private readonly ordersService = inject(OrdersService);
  private readonly eventsService = inject(EventsService);
  private readonly venuesService = inject(VenuesService);

  protected readonly result = signal<Page<Order> | null>(null);
  protected readonly summary = signal<OrderSummary | null>(null);
  protected readonly events = signal<EventSummary[]>([]);
  protected readonly sessions = signal<EventSession[]>([]);
  protected readonly venues = signal<Venue[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly advanced = signal(false);
  protected readonly applied = signal<Draft>({ ...EMPTY });
  protected draft: Draft = { ...EMPTY };
  private page = 0;

  protected readonly activeAdvanced = computed(() => {
    const a = this.applied();
    return [a.from, a.to, a.paymentStatus, a.paymentProvider, a.ticketCategory, a.venueId].filter(
      Boolean,
    ).length;
  });
  protected readonly filtered = computed(() =>
    Object.entries(this.applied()).some(([k, v]) => k !== 'sort' && !!v),
  );

  ngOnInit(): void {
    this.eventsService
      .list({ page: 0, size: 100 })
      .subscribe({ next: (p) => this.events.set(p.content), error: () => this.events.set([]) });
    this.venuesService
      .list()
      .subscribe({ next: (v) => this.venues.set(v), error: () => this.venues.set([]) });
    this.load();
  }

  /** Al elegir un evento se cargan sus dias para poder filtrar por uno (p. ej. el sabado). */
  protected selectEvent(eventId: string): void {
    this.draft.sessionId = '';
    this.sessions.set([]);
    if (eventId) {
      this.eventsService
        .get(eventId)
        .subscribe({
          next: (e) => this.sessions.set(e.sessions),
          error: () => this.sessions.set([]),
        });
    }
    this.search();
  }

  protected search(): void {
    this.page = 0;
    this.load();
  }

  protected reset(): void {
    this.draft = { ...EMPTY };
    this.sessions.set([]);
    this.search();
  }

  protected goToPage(page: number): void {
    this.page = page;
    this.load();
  }

  private load(): void {
    const f: OrderFilters = {
      ...this.draft,
      from: localInputToIso(this.draft.from),
      to: localInputToIso(this.draft.to),
    };
    this.applied.set({ ...this.draft });
    this.loading.set(true);
    this.error.set(null);
    this.ordersService.list({ ...f, page: this.page }).subscribe({
      next: (page) => {
        this.result.set(page);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
    this.ordersService
      .summary(f)
      .subscribe({ next: (s) => this.summary.set(s), error: () => this.summary.set(null) });
  }
}
