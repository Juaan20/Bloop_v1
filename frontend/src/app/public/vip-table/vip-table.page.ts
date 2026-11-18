import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { errorMessage } from '../../core/services/api-error';
import { Icon } from '../../shared/ui/icon';
import { Logo } from '../../shared/ui/logo';
import { GuestMenuItem, GuestTable, ORDER_STATUS, OrderLine, VipOrderStatus, newIdempotencyKey } from '../../features/vip/vip.models';
import { VipService } from '../../features/vip/vip.service';
import { eventArt } from '../events/event-card';

/** Los pedidos de la mesa se actualizan solos mientras la pagina esta abierta. */
const REFRESH_MS = 15_000;

/** Pasos del pedido que ve el cliente (version simplificada de los estados del personal). */
const GUEST_STEPS: { label: string; statuses: VipOrderStatus[] }[] = [
  { label: 'Recibido', statuses: ['NEW', 'PENDING'] },
  { label: 'Preparando', statuses: ['PREPARING', 'READY'] },
  { label: 'De camino', statuses: ['ON_THE_WAY'] },
  { label: 'Entregado', statuses: ['DELIVERED'] },
];

/**
 * Pagina que abre el QR de una mesa VIP (movil, sin app ni login).
 * Carta de botellas con fotos y precios, paquete incluido, carrito, pedidos y su estado.
 * Lo que sale del paquete no se cobra; el resto queda pendiente de pago en la cuenta de la mesa.
 */
@Component({
  selector: 'vf-vip-table-page',
  imports: [CurrencyPipe, DatePipe, FormsModule, Icon, Logo],
  template: `
    <div class="min-h-screen bg-zinc-950 pb-32 text-zinc-100">
      @if (loading()) {
        <div class="mx-auto max-w-lg space-y-4 p-5"><div class="vf-skeleton h-28 rounded-2xl"></div><div class="vf-skeleton h-40 rounded-2xl"></div><div class="vf-skeleton h-40 rounded-2xl"></div></div>
      } @else if (error()) {
        <div class="mx-auto grid min-h-screen max-w-sm place-items-center p-6 text-center">
          <div>
            <vf-logo [size]="36" [compact]="true" />
            <h1 class="mt-6 font-display text-2xl font-semibold text-white">Mesa no disponible</h1>
            <p class="mt-2 text-sm text-zinc-400">Este QR ya no esta activo o la reserva ha terminado. Pide ayuda al personal.</p>
          </div>
        </div>
      } @else if (data(); as d) {
        <!-- Cabecera de la mesa -->
        <header class="relative overflow-hidden px-5 pt-6 pb-8" [style.background]="art(d.venueName + d.tableName)">
          <div class="absolute inset-0 bg-gradient-to-b from-black/30 to-zinc-950"></div>
          <div class="relative mx-auto max-w-lg">
            <vf-logo [size]="22" />
            <p class="mt-6 text-xs font-medium tracking-[0.2em] text-zinc-300 uppercase">{{ d.venueName }} · {{ d.zoneName }}</p>
            <h1 class="mt-1 font-display text-4xl font-semibold tracking-tight text-white">{{ d.tableName }}</h1>
            <p class="mt-1 text-sm text-zinc-300">Hola, {{ d.hostFirstName }}@if (d.eventName) { · {{ d.eventName }} }</p>
          </div>
        </header>

        <main class="mx-auto max-w-lg space-y-6 px-4">
          @if (!d.canOrder) {
            <p class="flex items-start gap-2 rounded-xl border border-amber-400/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-200" role="status">
              <vf-icon name="clock" class="mt-0.5 size-4 shrink-0" /> {{ d.cannotOrderReason }}
            </p>
          }

          <!-- Paquete incluido -->
          @if (d.packageItems.length) {
            <section class="rounded-2xl border border-emerald-400/20 bg-emerald-500/5 p-4" aria-labelledby="pkg-title">
              <h2 id="pkg-title" class="flex items-center gap-2 text-sm font-semibold text-emerald-200"><vf-icon name="check" class="size-4" /> Incluido en vuestra reserva</h2>
              <ul class="mt-3 space-y-2 text-sm">
                @for (p of d.packageItems; track p.variantId) {
                  <li class="flex justify-between gap-3"><span class="text-zinc-100">{{ p.productName }}</span>
                    <span class="text-zinc-400 tabular-nums">quedan {{ p.remaining }} de {{ p.included }}</span></li>
                }
              </ul>
              <p class="mt-3 text-xs text-zinc-400">Pidelo desde la carta: lo incluido no se cobra.</p>
            </section>
          }

          <!-- Pedidos -->
          @if (d.orders.length) {
            <section aria-labelledby="orders-title">
              <h2 id="orders-title" class="text-sm font-semibold text-zinc-200">Vuestros pedidos</h2>
              <ul class="mt-3 space-y-3">
                @for (o of d.orders; track o.id) {
                  <li class="rounded-2xl border border-zinc-800 bg-zinc-900/60 p-4">
                    <div class="flex items-center justify-between text-xs text-zinc-500">
                      <span>{{ o.createdAt | date: 'HH:mm' }}</span>
                      @if (o.status === 'CANCELLED') { <span class="text-zinc-400">Cancelado</span> }
                    </div>
                    @for (i of o.items; track $index) {
                      <p class="mt-1 flex justify-between gap-2 text-sm"><span>{{ i.quantity }} × {{ i.productName }}</span>
                        <span class="tabular-nums" [class]="i.included ? 'text-emerald-300' : 'text-zinc-300'">{{ i.included ? 'Incluido' : (i.lineTotal | currency: 'EUR') }}</span></p>
                    }
                    @if (o.status !== 'CANCELLED') {
                      <ol class="mt-3 grid grid-cols-4 gap-1" [attr.aria-label]="'Estado: ' + orderStatus[o.status].label">
                        @for (s of steps; track s.label; let i = $index) {
                          <li class="text-center">
                            <span class="block h-1 rounded-full" [class]="stepIndex(o.status) >= i ? 'bg-brand-400' : 'bg-zinc-800'"></span>
                            <span class="mt-1 block text-[10px]" [class]="stepIndex(o.status) === i ? 'font-semibold text-white' : 'text-zinc-500'">{{ s.label }}</span>
                          </li>
                        }
                      </ol>
                    }
                  </li>
                }
              </ul>
              <p class="mt-3 flex justify-between rounded-xl bg-zinc-900/60 px-4 py-3 text-sm">
                <span class="text-zinc-400">Consumiciones adicionales pendientes</span>
                <span class="font-semibold text-white tabular-nums">{{ d.pendingTotal | currency: 'EUR' }}</span>
              </p>
              <p class="mt-1 text-xs text-zinc-500">Se abonan al personal al cerrar la cuenta de la mesa.</p>
            </section>
          }

          <!-- Carta -->
          @for (cat of d.menu; track cat.name) {
            <section [attr.aria-label]="cat.name">
              <h2 class="text-sm font-semibold text-zinc-200">{{ cat.name }}</h2>
              <ul class="mt-3 space-y-3">
                @for (item of cat.items; track item.variantId) {
                  <li class="flex gap-3 rounded-2xl border border-zinc-800 bg-zinc-900/40 p-3">
                    @if (item.imageUrl) {
                      <img [src]="item.imageUrl" [alt]="item.productName" class="size-20 shrink-0 rounded-xl object-cover" loading="lazy" />
                    } @else {
                      <span class="size-20 shrink-0 rounded-xl" [style.background]="art(item.productName)" aria-hidden="true"></span>
                    }
                    <div class="min-w-0 flex-1">
                      <p class="font-medium text-white">{{ item.productName }} <span class="text-xs font-normal text-zinc-500">{{ item.variantName }}</span></p>
                      @if (item.description) { <p class="mt-0.5 line-clamp-2 text-xs text-zinc-400">{{ item.description }}</p> }
                      <div class="mt-2 flex items-center justify-between gap-2">
                        <span class="text-sm font-semibold tabular-nums">
                          @if (item.remainingInPackage > 0) { <span class="text-emerald-300">Incluido ({{ item.remainingInPackage }})</span> }
                          @else { {{ item.price | currency: 'EUR' }} }
                        </span>
                        @if (d.canOrder) {
                          <div class="flex items-center gap-2">
                            @if (qty(item.variantId) > 0) {
                              <button type="button" class="grid size-9 cursor-pointer place-items-center rounded-full bg-zinc-800 active:scale-95" (click)="change(item, -1)" [attr.aria-label]="'Quitar ' + item.productName"><span class="block h-0.5 w-3 rounded bg-current"></span></button>
                              <span class="w-5 text-center font-semibold tabular-nums" aria-live="polite">{{ qty(item.variantId) }}</span>
                            }
                            <button type="button" class="grid size-9 cursor-pointer place-items-center rounded-full bg-white text-zinc-950 active:scale-95 disabled:opacity-30" [disabled]="qty(item.variantId) >= 20" (click)="change(item, 1)" [attr.aria-label]="'Anadir ' + item.productName"><vf-icon name="plus" class="size-4" [stroke]="2.5" /></button>
                          </div>
                        }
                      </div>
                    </div>
                  </li>
                }
              </ul>
            </section>
          } @empty {
            <p class="text-center text-sm text-zinc-500">La carta VIP aun no tiene productos.</p>
          }
        </main>

        <!-- Carrito -->
        @if (cartCount() > 0) {
          <div class="fixed inset-x-0 bottom-0 z-30 animate-fade-in border-t border-zinc-800 bg-zinc-950/95 p-4 backdrop-blur">
            <div class="mx-auto max-w-lg space-y-3">
              <div class="flex items-baseline justify-between text-sm">
                <span class="text-zinc-400">{{ cartCount() }} {{ cartCount() === 1 ? 'producto' : 'productos' }}</span>
                <span><span class="text-zinc-400">Adicional:</span> <span class="font-semibold text-white tabular-nums">{{ cartExtra() | currency: 'EUR' }}</span></span>
              </div>
              <input class="vf-input" maxlength="300" placeholder="Nota para el personal (opcional)" [(ngModel)]="notes" aria-label="Nota para el personal" />
              @if (orderError()) { <p class="text-sm text-red-300" role="alert">{{ orderError() }}</p> }
              <button type="button" class="vf-btn-primary min-h-13 w-full text-base" [disabled]="sending()" (click)="send()">
                {{ sending() ? 'Enviando...' : 'Pedir a la mesa' }}
              </button>
              <p class="text-center text-[11px] text-zinc-500">Lo adicional queda pendiente de pago en la cuenta de la mesa.</p>
            </div>
          </div>
        }
      }
    </div>
  `,
})
export class VipTablePage implements OnInit {
  private readonly vip = inject(VipService);

  /** Token del QR (parametro de la ruta /mesa/:token). */
  readonly token = input.required<string>();

  protected readonly art = eventArt;
  protected readonly steps = GUEST_STEPS;
  protected readonly orderStatus = ORDER_STATUS;
  protected readonly data = signal<GuestTable | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly sending = signal(false);
  protected readonly orderError = signal<string | null>(null);
  private readonly cart = signal<Map<string, number>>(new Map());
  protected notes = '';
  /** Se conserva mientras no se confirme el pedido: un reintento no lo duplica. */
  private orderKey = newIdempotencyKey();

  private readonly items = computed(() => new Map((this.data()?.menu ?? []).flatMap((c) => c.items).map((i) => [i.variantId, i])));
  protected readonly cartCount = computed(() => [...this.cart().values()].reduce((s, q) => s + q, 0));
  /** Importe adicional estimado: lo que no cubre el paquete (el backend hace el calculo definitivo). */
  protected readonly cartExtra = computed(() =>
    [...this.cart()].reduce((sum, [id, q]) => {
      const item = this.items().get(id);
      return item ? sum + Math.max(0, q - item.remainingInPackage) * item.price : sum;
    }, 0),
  );

  constructor() {
    const timer = setInterval(() => this.load(false), REFRESH_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  ngOnInit(): void {
    this.load(true);
  }

  private load(first: boolean): void {
    this.vip.guestView(this.token()).subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (err) => {
        if (first || err?.status === 404) this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected qty(variantId: string): number {
    return this.cart().get(variantId) ?? 0;
  }

  protected change(item: GuestMenuItem, delta: number): void {
    const next = new Map(this.cart());
    const q = Math.max(0, Math.min(20, this.qty(item.variantId) + delta));
    if (q === 0) next.delete(item.variantId);
    else next.set(item.variantId, q);
    this.cart.set(next);
  }

  protected stepIndex(status: VipOrderStatus): number {
    return GUEST_STEPS.findIndex((s) => s.statuses.includes(status));
  }

  protected send(): void {
    const lines: OrderLine[] = [...this.cart()].map(([variantId, quantity]) => ({ variantId, quantity }));
    this.sending.set(true);
    this.orderError.set(null);
    this.vip.guestOrder(this.token(), lines, this.orderKey, this.notes.trim() || null).subscribe({
      next: () => {
        this.cart.set(new Map());
        this.notes = '';
        this.orderKey = newIdempotencyKey();
        this.sending.set(false);
        this.load(false);
      },
      error: (err) => {
        this.sending.set(false);
        this.orderError.set(errorMessage(err));
      },
    });
  }
}
