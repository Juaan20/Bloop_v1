import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { Permission } from '../../core/auth/permissions';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { QrCode } from '../../shared/components/qr-code';
import { Icon } from '../../shared/ui/icon';
import { StatusBadge } from '../../shared/ui/status-badge';
import { Product } from '../pos/pos.models';
import { ReservationsService } from '../reservations/reservations.service';
import { ORDER_STATUS, TABLE_STATUS, VipMapTable, VipReservationDetail, newIdempotencyKey } from './vip.models';
import { VipService } from './vip.service';

/**
 * Ficha lateral de una mesa del plano en vivo: reserva, pago previo, paquete, pedidos,
 * cuenta (cobro y cierre) y QR de la mesa. Todas las acciones van al backend, que valida las reglas.
 */
@Component({
  selector: 'vf-vip-table-panel',
  imports: [CurrencyPipe, DatePipe, FormsModule, Icon, QrCode, StatusBadge],
  template: `
    @let t = table();
    <div class="flex items-start justify-between gap-3">
      <div>
        <p class="text-xs text-zinc-500">{{ t.zoneName }}</p>
        <h2 class="font-display text-xl font-semibold text-white">{{ t.name }}</h2>
        <p class="mt-1 flex items-center gap-2 text-xs text-zinc-400">
          <span class="size-2 rounded-full" [class]="statusInfo().dot"></span>{{ statusInfo().label }} · {{ t.capacity }} pers.
        </p>
      </div>
      <button type="button" class="vf-btn-icon size-8" (click)="closed.emit()" aria-label="Cerrar ficha"><vf-icon name="x" class="size-4" /></button>
    </div>

    @if (!t.reservation) {
      <dl class="mt-5 grid grid-cols-2 gap-3 text-sm">
        <div class="rounded-lg bg-zinc-950/50 p-3"><dt class="text-xs text-zinc-500">Precio reserva + paquete</dt><dd class="mt-1 font-semibold text-white tabular-nums">{{ t.price | currency: 'EUR' }}</dd></div>
        <div class="rounded-lg bg-zinc-950/50 p-3"><dt class="text-xs text-zinc-500">Consumo minimo</dt><dd class="mt-1 font-semibold text-white tabular-nums">{{ t.minSpend | currency: 'EUR' }}</dd></div>
      </dl>
      @if (!t.onPlan) {
        <p class="mt-4 rounded-lg bg-amber-500/10 p-3 text-sm text-amber-200">Esta mesa no esta colocada en el plano: colocala en "Mesas" para poder venderla.</p>
      } @else if (!sessionId()) {
        <p class="mt-4 text-sm text-zinc-400">Elige una fiesta arriba para reservar o vender esta mesa.</p>
      } @else if (canManage && t.active) {
        <div class="mt-5 inline-flex h-9 w-full items-center rounded-lg border border-zinc-800 bg-zinc-950/60 p-0.5" role="tablist" aria-label="Tipo de alta">
          <button type="button" role="tab" class="h-full flex-1 cursor-pointer rounded-md text-xs font-medium transition" [attr.aria-selected]="newMode() === 'walkin'"
                  [class]="newMode() === 'walkin' ? 'bg-zinc-800 text-white' : 'text-zinc-400'" (click)="newMode.set('walkin')">Venta en taquilla</button>
          <button type="button" role="tab" class="h-full flex-1 cursor-pointer rounded-md text-xs font-medium transition" [attr.aria-selected]="newMode() === 'booking'"
                  [class]="newMode() === 'booking' ? 'bg-zinc-800 text-white' : 'text-zinc-400'" (click)="newMode.set('booking')">Reservar</button>
        </div>
        <form class="mt-4 space-y-3" (ngSubmit)="createBooking()">
          <p class="text-xs text-zinc-500">{{ newMode() === 'walkin' ? 'Grupo que llega sin reserva: se cobra la mesa y pasa directamente.' : 'Reserva de la mesa para toda la fiesta (queda pendiente de pago).' }}</p>
          <div class="grid grid-cols-[1fr_5rem] gap-2">
            <div><label class="vf-label" for="nbName">Nombre del grupo</label><input id="nbName" name="nbName" class="vf-input" maxlength="200" required [(ngModel)]="nb.name" /></div>
            <div><label class="vf-label" for="nbGuests">Pers.</label><input id="nbGuests" name="nbGuests" type="number" min="1" [max]="t.capacity" class="vf-input" [(ngModel)]="nb.guests" /></div>
          </div>
          <div class="grid grid-cols-2 gap-2">
            <div><label class="vf-label" for="nbPhone">Telefono</label><input id="nbPhone" name="nbPhone" type="tel" class="vf-input" maxlength="40" [(ngModel)]="nb.phone" /></div>
            <div><label class="vf-label" for="nbEmail">Email <span class="text-zinc-600">opcional</span></label><input id="nbEmail" name="nbEmail" type="email" class="vf-input" maxlength="255" [(ngModel)]="nb.email" /></div>
          </div>
          @if (newMode() === 'walkin' && t.price > 0) {
            <div><label class="vf-label" for="nbMethod">Cobro {{ t.price | currency: 'EUR' }}</label>
              <select id="nbMethod" name="nbMethod" class="vf-input" [(ngModel)]="nb.method"><option value="CARD">Tarjeta</option><option value="CASH">Efectivo</option></select></div>
          }
          <button type="submit" class="vf-btn-primary w-full" [disabled]="busy() || !nb.name.trim() || nb.guests < 1 || nb.guests > t.capacity">
            {{ newMode() === 'walkin' ? (t.price > 0 ? 'Cobrar ' + (t.price | currency: 'EUR') + ' y sentar' : 'Sentar al grupo') : 'Crear reserva' }}
          </button>
        </form>
      } @else {
        <p class="mt-4 text-sm text-zinc-400">Mesa libre en esta fiesta.</p>
      }
      @if (t.history.length) {
        <section class="mt-5 border-t border-zinc-800 pt-4">
          <h3 class="text-xs font-medium text-zinc-400">Antes en esta fiesta</h3>
          <ul class="mt-2 space-y-1 text-sm">
            @for (h of t.history; track h.id) {
              <li class="flex justify-between gap-2"><span class="text-zinc-300">{{ h.contactName }} <span class="text-xs text-zinc-500">· {{ h.guests }} pers.</span></span><vf-status-badge [status]="h.status" /></li>
            }
          </ul>
        </section>
      }
    } @else if (loading() && !detail()) {
      <div class="mt-6 space-y-3"><div class="vf-skeleton h-16"></div><div class="vf-skeleton h-24"></div><div class="vf-skeleton h-32"></div></div>
    } @else if (error()) {
      <p class="mt-6 text-sm text-red-300">{{ error() }}</p>
    } @else if (detail(); as d) {
      <!-- Reserva -->
      <section class="mt-5 rounded-xl border border-zinc-800 bg-zinc-950/40 p-4">
        <div class="flex items-center justify-between gap-2">
          <p class="font-medium text-white">{{ d.reservation.contactName }}</p>
          <vf-status-badge [status]="d.reservation.status" />
        </div>
        <p class="mt-1 text-xs text-zinc-400">{{ d.reservation.guests }} pers. · toda la fiesta ({{ d.reservation.startsAt | date: 'EEE d MMM' }})</p>
        @if (d.reservation.eventName) { <p class="text-xs text-zinc-500">{{ d.reservation.eventName }}</p> }
        <p class="mt-1 font-mono text-[11px] text-zinc-600">{{ d.reservation.reference }}</p>
        @if (canManage) {
          <div class="mt-3 flex flex-wrap gap-2">
            @if (d.reservation.status === 'PENDING') {
              <button type="button" class="vf-btn-secondary vf-btn-sm" [disabled]="busy()" (click)="setStatus('CONFIRMED')">Confirmar</button>
            }
            @if (d.reservation.status === 'CONFIRMED') {
              <button type="button" class="vf-btn-primary vf-btn-sm" [disabled]="busy() || !d.account.prepaid" (click)="setStatus('SEATED')"
                      [attr.title]="d.account.prepaid ? null : 'Primero registra el pago de la reserva'">Dar acceso a la mesa</button>
              <button type="button" class="vf-btn-ghost vf-btn-sm" [disabled]="busy()" (click)="setStatus('NO_SHOW')">No se presento</button>
            }
            @if (d.reservation.status === 'PENDING' || d.reservation.status === 'CONFIRMED') {
              <button type="button" class="vf-btn-ghost vf-btn-sm text-red-300" [disabled]="busy()" (click)="setStatus('CANCELLED')">Cancelar</button>
            }
          </div>
        }
      </section>

      <!-- Cuenta -->
      <section class="mt-4" aria-labelledby="account-title">
        <h3 id="account-title" class="text-xs font-medium text-zinc-400">Cuenta de la mesa</h3>
        <dl class="mt-2 divide-y divide-zinc-800/70 rounded-xl border border-zinc-800 text-sm">
          <div class="flex items-center justify-between gap-3 px-4 py-2.5">
            <dt class="text-zinc-400">Reserva + paquete <span class="text-xs text-zinc-600">(antes de entrar)</span></dt>
            <dd class="flex items-center gap-2 tabular-nums">
              <span class="text-white">{{ d.account.prepaidAmount | currency: 'EUR' }}</span>
              @if (d.account.prepaidAmount > 0) {
                <span class="vf-badge" [class]="d.account.prepaid ? 'bg-emerald-500/10 text-emerald-300 ring-emerald-500/20' : 'bg-amber-500/10 text-amber-300 ring-amber-500/20'">{{ d.account.prepaid ? 'Pagado' : 'Pendiente' }}</span>
              }
            </dd>
          </div>
          <div class="flex justify-between px-4 py-2.5"><dt class="text-zinc-400">Botellas adicionales</dt><dd class="text-white tabular-nums">{{ d.account.extrasTotal | currency: 'EUR' }}</dd></div>
          <div class="flex justify-between px-4 py-2.5"><dt class="text-zinc-400">Cobrado</dt><dd class="text-white tabular-nums">{{ d.account.paidTotal | currency: 'EUR' }}</dd></div>
          <div class="flex justify-between bg-zinc-900/60 px-4 py-2.5 font-medium"><dt class="text-zinc-200">Pendiente de pago</dt>
            <dd class="tabular-nums" [class]="d.account.pendingTotal > 0 ? 'text-amber-300' : 'text-emerald-300'">{{ d.account.pendingTotal | currency: 'EUR' }}</dd></div>
        </dl>
        @if (canManage) {
          @if (!d.account.prepaid && d.reservation.status !== 'CANCELLED') {
            <div class="mt-3 flex gap-2">
              <select class="vf-input w-28" [(ngModel)]="prepayMethod" aria-label="Metodo de pago de la reserva"><option value="CARD">Tarjeta</option><option value="CASH">Efectivo</option></select>
              <button type="button" class="vf-btn-primary flex-1" [disabled]="busy()" (click)="registerPrepayment()">
                <vf-icon name="check" class="size-4" /> Cobrar reserva ({{ d.account.prepaidAmount | currency: 'EUR' }})
              </button>
            </div>
          }
          @if (d.account.pendingTotal > 0 && !d.account.closed) {
            <form class="mt-3 flex flex-wrap items-end gap-2" (ngSubmit)="pay()">
              <div class="w-28"><label class="vf-label" for="payAmount">Importe</label>
                <input id="payAmount" name="payAmount" type="number" min="0.01" step="0.01" [max]="d.account.pendingTotal" class="vf-input" [(ngModel)]="payAmount" /></div>
              <div><label class="vf-label" for="payMethod">Metodo</label>
                <select id="payMethod" name="payMethod" class="vf-input" [(ngModel)]="payMethod"><option value="CARD">Tarjeta</option><option value="CASH">Efectivo</option></select></div>
              <button type="submit" class="vf-btn-secondary" [disabled]="busy() || !(payAmount > 0)">Cobrar</button>
            </form>
          }
          @if (d.reservation.status === 'SEATED') {
            <button type="button" class="vf-btn-secondary mt-3 w-full" [disabled]="busy() || !d.account.canClose" (click)="closeAccount()">
              Liberar la mesa (los clientes se han ido)
            </button>
            @if (!d.account.canClose) {
              <p class="mt-1 text-xs text-zinc-500">
                {{ d.account.openOrders > 0 ? 'Hay pedidos sin entregar.' : d.account.pendingTotal > 0 ? 'Cobra lo pendiente antes de cerrar.' : '' }}
              </p>
            }
          }
        }
      </section>

      <!-- Paquete -->
      @if (d.account.packageItems.length) {
        <section class="mt-4">
          <h3 class="text-xs font-medium text-zinc-400">Paquete incluido</h3>
          <ul class="mt-2 space-y-1.5 text-sm">
            @for (p of d.account.packageItems; track p.variantId) {
              <li class="flex justify-between gap-3"><span class="text-zinc-200">{{ p.productName }}</span>
                <span class="text-xs text-zinc-500 tabular-nums">{{ p.consumed }}/{{ p.included }} servidas</span></li>
            }
          </ul>
        </section>
      }

      <!-- Pedidos -->
      <section class="mt-4">
        <h3 class="text-xs font-medium text-zinc-400">Pedidos de la mesa</h3>
        <ul class="mt-2 space-y-2">
          @for (o of d.orders; track o.id) {
            <li class="rounded-lg border border-zinc-800 p-3 text-sm">
              <div class="flex items-center justify-between gap-2">
                <span class="text-xs text-zinc-500">{{ o.createdAt | date: 'HH:mm' }} · {{ o.source === 'GUEST' ? 'Desde el QR' : 'Personal' }}</span>
                <span class="vf-badge" [class]="orderStatus[o.status].tone">{{ orderStatus[o.status].label }}</span>
              </div>
              @for (i of o.items; track $index) {
                <p class="mt-1 flex justify-between gap-2"><span class="text-zinc-200">{{ i.quantity }} × {{ i.productName }}</span>
                  <span class="text-xs tabular-nums" [class]="i.included ? 'text-emerald-300' : 'text-zinc-300'">{{ i.included ? 'Incluido' : (i.lineTotal | currency: 'EUR') }}</span></p>
              }
            </li>
          } @empty {
            <li class="text-sm text-zinc-500">Sin pedidos.</li>
          }
        </ul>
        @if (canStaffOrder && d.reservation.status === 'SEATED') {
          <form class="mt-3 flex flex-wrap items-end gap-2" (ngSubmit)="staffOrder()">
            <div class="min-w-40 flex-1"><label class="vf-label" for="soVariant">Anadir pedido</label>
              <select id="soVariant" name="soVariant" class="vf-input" [(ngModel)]="orderVariant">
                <option value="">Producto...</option>
                @for (o of orderOptions(); track o.id) { <option [value]="o.id">{{ o.label }}</option> }
              </select></div>
            <div class="w-20"><label class="vf-label" for="soQty">Uds.</label><input id="soQty" name="soQty" type="number" min="1" max="20" class="vf-input" [(ngModel)]="orderQty" /></div>
            <button type="submit" class="vf-btn-secondary" [disabled]="busy() || !orderVariant">Pedir</button>
          </form>
        }
      </section>

      <!-- QR de la mesa -->
      <section class="mt-4 rounded-xl border border-zinc-800 p-4">
        <h3 class="text-xs font-medium text-zinc-400">QR de la mesa</h3>
        @if (d.qr.token && d.qr.active) {
          <div class="mt-3 flex items-center gap-4">
            <div class="rounded-lg bg-white p-1.5"><vf-qr-code [value]="qrUrl(d.qr.token)" [size]="112" /></div>
            <div class="min-w-0 text-xs text-zinc-400">
              <p>Los clientes lo escanean para ver la carta y pedir desde el movil.</p>

            </div>
          </div>
        } @else {
          <p class="mt-2 text-sm text-zinc-500">{{ d.qr.token ? 'QR desactivado.' : 'Se genera al dar acceso a la mesa.' }}</p>
        }
        @if (canManage && (d.reservation.status === 'CONFIRMED' || d.reservation.status === 'SEATED')) {
          <div class="mt-3 flex gap-2">
            <button type="button" class="vf-btn-secondary vf-btn-sm" [disabled]="busy()" (click)="regenerateQr()">{{ d.qr.token ? 'Regenerar QR' : 'Generar QR' }}</button>
            @if (d.qr.active) {
              <button type="button" class="vf-btn-ghost vf-btn-sm" [disabled]="busy()" (click)="deactivateQr()">Desactivar</button>
            }
          </div>
        }
      </section>
    }
  `,
})
export class VipTablePanel {
  private readonly vip = inject(VipService);
  private readonly reservations = inject(ReservationsService);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);

  readonly table = input.required<VipMapTable>();
  /** Productos para anotar pedidos (solo si el usuario puede ver el catalogo). */
  readonly products = input<Product[]>([]);
  readonly closed = output<void>();
  /** Algo cambio (pago, estado...): la pagina recarga el plano. */
  readonly changed = output<void>();

  protected readonly canManage = this.auth.hasPermission(Permission.RESERVATION_MANAGE);
  protected readonly canStaffOrder = this.auth.hasPermission(Permission.POS_MANAGE)
    && (this.auth.hasPermission(Permission.VIP_SERVICE) || this.canManage);
  protected readonly orderStatus = ORDER_STATUS;
  protected readonly detail = signal<VipReservationDetail | null>(null);
  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected payAmount = 0;
  protected payMethod: 'CASH' | 'CARD' = 'CARD';
  protected prepayMethod: 'CASH' | 'CARD' = 'CARD';
  /** Alta desde el plano: venta en taquilla (ahora) o reserva para mas tarde. */
  protected readonly newMode = signal<'walkin' | 'booking'>('walkin');
  protected nb = { name: '', guests: 4, phone: '', email: '', method: 'CARD' as 'CASH' | 'CARD' };
  /** Fiesta del plano: las reservas y ventas son para la fiesta completa. */
  readonly sessionId = input<string | null>(null);
  protected orderVariant = '';
  protected orderQty = 1;
  /** Clave del cobro/pedido en curso: si se reintenta, el backend no lo duplica. */
  private payKey = newIdempotencyKey();
  private orderKey = newIdempotencyKey();

  protected readonly statusInfo = computed(() => TABLE_STATUS[this.table().status]);
  protected readonly orderOptions = computed(() =>
    this.products()
      .filter((p) => p.active)
      .flatMap((p) => p.variants.filter((v) => v.active && v.id).map((v) => ({ id: v.id!, label: `${p.name} · ${v.name}` }))),
  );

  constructor() {
    effect(() => {
      const r = this.table().reservation;
      this.detail.set(null);
      this.error.set(null);
      if (r) this.load(r.id);
      else untracked(() => this.resetNewBooking());
    });
  }

  private resetNewBooking(): void {
    this.nb = { name: '', guests: Math.min(4, this.table().capacity), phone: '', email: '', method: 'CARD' };
  }

  protected createBooking(): void {
    const t = this.table();
    const sessionId = this.sessionId();
    if (!sessionId) return;
    const base = { guests: Number(this.nb.guests), contactName: this.nb.name.trim(), contactPhone: this.nb.phone.trim() || null, contactEmail: this.nb.email.trim() || null };
    if (this.newMode() === 'walkin') {
      this.run(
        this.vip.walkIn({ ...base, tableId: t.id, sessionId, paymentMethod: t.price > 0 ? this.nb.method : null, notes: null }),
        'Mesa vendida: el grupo ya puede pasar',
      );
    } else {
      this.run(this.reservations.create({ ...base, tableId: t.id, sessionId, notes: null }), 'Reserva creada para la fiesta');
    }
  }

  protected qrUrl(token: string): string {
    return `${location.origin}/mesa/${token}`;
  }

  private load(id: string): void {
    this.loading.set(true);
    this.vip.detail(id).subscribe({
      next: (d) => this.apply(d),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  private apply(d: VipReservationDetail): void {
    this.detail.set(d);
    this.loading.set(false);
    this.busy.set(false);
    this.payAmount = d.account.pendingTotal;
  }

  private run<T>(obs: Observable<T>, ok: string, after?: (v: T) => void): void {
    this.busy.set(true);
    obs.subscribe({
      next: (v) => {
        this.toast.success(ok);
        after?.(v);
        this.busy.set(false);
        this.changed.emit();
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.busy.set(false);
      },
    });
  }

  protected setStatus(status: 'CONFIRMED' | 'SEATED' | 'NO_SHOW' | 'CANCELLED'): void {
    const id = this.detail()!.reservation.id;
    this.run(this.reservations.changeStatus(id, status), 'Reserva actualizada', () => this.load(id));
  }

  protected registerPrepayment(): void {
    const id = this.detail()!.reservation.id;
    this.run(this.reservations.registerDeposit(id, this.prepayMethod), 'Pago de la reserva registrado', () => this.load(id));
  }

  protected pay(): void {
    const id = this.detail()!.reservation.id;
    this.run(this.vip.pay(id, this.payAmount, this.payMethod, this.payKey), 'Cobro registrado', (d) => {
      this.payKey = newIdempotencyKey();
      this.apply(d);
    });
  }

  protected closeAccount(): void {
    this.run(this.vip.close(this.detail()!.reservation.id), 'Cuenta cerrada: mesa libre', (d) => this.apply(d));
  }

  protected regenerateQr(): void {
    this.run(this.vip.generateQr(this.detail()!.reservation.id), 'QR generado', (d) => this.apply(d));
  }

  protected deactivateQr(): void {
    this.run(this.vip.deactivateQr(this.detail()!.reservation.id), 'QR desactivado', (d) => this.apply(d));
  }

  protected staffOrder(): void {
    const id = this.detail()!.reservation.id;
    const lines = [{ variantId: this.orderVariant, quantity: Math.max(1, Math.min(20, Number(this.orderQty) || 1)) }];
    this.run(this.vip.staffOrder(id, lines, this.orderKey, null), 'Pedido enviado al panel de servicio', () => {
      this.orderKey = newIdempotencyKey();
      this.orderVariant = '';
      this.orderQty = 1;
      this.load(id);
    });
  }
}
