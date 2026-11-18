import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Permission } from '../../core/auth/permissions';
import { ToastService } from '../../core/services/toast.service';
import { Modal } from '../../shared/ui/modal';
import { errorMessage } from '../../core/services/api-error';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { StatusBadge } from '../../shared/ui/status-badge';
import { OrderDetail } from './order.models';
import { OrdersService } from './orders.service';

/** Detalle de un pedido: lineas, intentos de pago y entradas emitidas con su estado de acceso. */
@Component({
  selector: 'vf-order-detail-page',
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, StatusLabelPipe, Modal, PageHeader, Spinner, StatusBadge],
  template: `
    @if (error()) {
      <p class="vf-card text-red-400">{{ error() }}</p>
    } @else if (detail(); as d) {
      <vf-page-header [title]="'Pedido ' + d.order.reference" [subtitle]="d.order.eventName + ' · ' + (d.order.sessionStartsAt | date: 'EEE d MMM, HH:mm')">
        <vf-status-badge [status]="d.order.status" />
        @if (canManage && d.order.status === 'PAID') {
          <button type="button" class="vf-btn-secondary" [disabled]="busy()" (click)="resend()">Reenviar entradas</button>
        }
        @if (canRefund && d.order.status === 'PAID') {
          <button type="button" class="vf-btn-danger" [disabled]="busy()" (click)="openRefund()">Reembolsar</button>
        }
        <a routerLink="/admin/ventas" class="vf-btn-secondary">Volver</a>
      </vf-page-header>
      <div class="grid gap-6 lg:grid-cols-2">
        <div class="vf-card">
          <h2 class="font-display font-semibold text-white">Cliente y lineas</h2>
          <p class="mt-3 text-sm text-zinc-300">{{ d.order.buyerName }} · {{ d.order.buyerEmail }}</p>
          <ul class="mt-4 space-y-2 text-sm">
            @for (i of d.order.items; track i.ticketTypeId) {
              <li class="flex justify-between text-zinc-300"><span>{{ i.quantity }} × {{ i.ticketTypeName }} ({{ i.unitPrice | currency: d.order.currency }})</span><span>{{ i.lineTotal | currency: d.order.currency }}</span></li>
            }
          </ul>
          @if (d.order.discountAmount > 0) {
            <p class="mt-4 flex justify-between border-t border-zinc-800 pt-3 text-sm text-zinc-400"><span>Subtotal</span><span>{{ d.order.subtotalAmount | currency: d.order.currency }}</span></p>
            <p class="flex justify-between text-sm text-emerald-300"><span>Descuento {{ d.order.promoCode }}</span><span>-{{ d.order.discountAmount | currency: d.order.currency }}</span></p>
          }
          <p class="mt-4 flex justify-between border-t border-zinc-800 pt-3 font-semibold text-white"><span>Total</span><span>{{ d.order.totalAmount | currency: d.order.currency }}</span></p>
          @if (d.promoterName) {
            <p class="mt-3 text-sm text-zinc-400">Venta de <span class="text-zinc-200">{{ d.promoterName }}</span> · comision {{ d.commissionAmount | currency: d.order.currency }}</p>
          }
          <p class="mt-3 text-xs text-zinc-500">Creado {{ d.order.createdAt | date: 'd/M/yyyy HH:mm' }}{{ d.order.paidAt ? ' · Pagado ' + (d.order.paidAt | date: 'd/M/yyyy HH:mm') : '' }}</p>
          @if (d.order.refundedAt) {
            <p class="mt-3 rounded-lg border border-amber-400/30 bg-amber-400/5 px-3 py-2 text-sm text-amber-200">
              Reembolsado el {{ d.order.refundedAt | date: 'd/M/yyyy HH:mm' }}{{ d.order.refundReason ? ': ' + d.order.refundReason : '' }}
            </p>
          }
        </div>
        <div class="vf-card">
          <h2 class="font-display font-semibold text-white">Pagos</h2>
          <ul class="mt-3 space-y-2 text-sm">
            @for (p of d.payments; track p.id) {
              <li class="flex items-center justify-between gap-2">
                <span class="text-zinc-300">{{ p.provider | statusLabel }} · {{ p.createdAt | date: 'd MMM HH:mm:ss' }}
                  @if (p.failureReason) { <span class="block text-xs text-zinc-500">{{ p.failureReason }}</span> }</span>
                <vf-status-badge [status]="p.status" />
              </li>
            } @empty {
              <li class="text-zinc-500">Sin intentos de pago.</li>
            }
          </ul>
        </div>
        <div class="vf-card lg:col-span-2">
          <h2 class="font-display font-semibold text-white">Entradas emitidas</h2>
          @if (d.tickets.length === 0) {
            <p class="mt-3 text-sm text-zinc-500">Las entradas se emiten al confirmarse el pago.</p>
          } @else {
            <table class="vf-table mt-3">
              <thead><tr><th>Tipo</th><th>Titular</th><th>Estado</th><th>Acceso</th>@if (canManage) { <th></th> }</tr></thead>
              <tbody>
                @for (t of d.tickets; track t.id) {
                  <tr><td class="text-zinc-200">{{ t.ticketTypeName }}</td><td class="text-zinc-300">{{ t.holderName }}</td>
                    <td><vf-status-badge [status]="t.status" /></td><td class="text-zinc-400">{{ t.usedAt ? (t.usedAt | date: 'd MMM HH:mm') : '—' }}</td>
                    @if (canManage) { <td>@if (t.status === 'VALID') { <button type="button" class="vf-btn-ghost text-xs" (click)="openRename(t.id, t.holderName)">Cambiar titular</button> }</td> }</tr>
                }
              </tbody>
            </table>
          }
        </div>
      </div>

      <vf-modal [open]="!!renaming()" title="Cambiar titular" (closed)="renaming.set(null)">
        <form class="space-y-4" (ngSubmit)="rename()">
          <div>
            <label class="vf-label" for="holderName">Nombre que figurara en la entrada</label>
            <input id="holderName" name="holderName" class="vf-input" maxlength="200" [(ngModel)]="holderName" required />
          </div>
          <p class="text-xs text-zinc-500">El QR no cambia. Para pasar la entrada a otra cuenta, el cliente puede transferirla desde "Mis entradas".</p>
          <button type="submit" class="vf-btn-primary" [disabled]="busy() || !holderName.trim()">Guardar</button>
        </form>
      </vf-modal>

      <vf-modal [open]="refunding()" title="Reembolsar pedido" (closed)="refunding.set(false)">
        <form class="space-y-4" (ngSubmit)="refund()">
          <p class="text-sm text-zinc-300">
            Se devolveran <span class="font-semibold text-white">{{ d.order.totalAmount | currency: d.order.currency }}</span>
            al cliente por el mismo medio de pago, sus {{ d.order.ticketCount }} entradas dejaran de valer
            y volveran a estar a la venta.
          </p>
          <div>
            <label class="vf-label" for="refundReason">Motivo (lo vera el cliente en el email)</label>
            <input id="refundReason" name="refundReason" class="vf-input" maxlength="255" [(ngModel)]="refundReason" />
          </div>
          <button type="submit" class="vf-btn-danger" [disabled]="busy() || !refundReason.trim()">
            {{ busy() ? 'Reembolsando...' : 'Confirmar reembolso' }}
          </button>
        </form>
      </vf-modal>
    } @else {
      <vf-spinner />
    }
  `,
})
export class OrderDetailPage {
  private readonly ordersService = inject(OrdersService);
  private readonly toast = inject(ToastService);
  readonly id = input.required<string>();
  protected readonly canManage = inject(AuthService).hasPermission(Permission.ORDER_MANAGE);
  protected readonly detail = signal<OrderDetail | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  /** Id de la entrada cuyo titular se esta cambiando. */
  protected readonly renaming = signal<string | null>(null);
  protected holderName = '';
  protected readonly canRefund = inject(AuthService).hasPermission(Permission.ORDER_REFUND);
  protected readonly refunding = signal(false);
  protected refundReason = '';

  constructor() {
    effect(() => this.load(this.id()));
  }

  protected resend(): void {
    this.busy.set(true);
    this.ordersService.resend(this.id()).subscribe({
      next: () => {
        this.toast.success('Entradas reenviadas al comprador');
        this.busy.set(false);
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.busy.set(false);
      },
    });
  }

  protected openRefund(): void {
    this.refundReason = '';
    this.refunding.set(true);
  }

  protected refund(): void {
    this.busy.set(true);
    this.ordersService.refund(this.id(), this.refundReason.trim()).subscribe({
      next: () => {
        this.toast.success('Pedido reembolsado. Hemos avisado al cliente por email');
        this.busy.set(false);
        this.refunding.set(false);
        this.load(this.id());
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.busy.set(false);
      },
    });
  }

  protected openRename(ticketId: string, currentName: string): void {
    this.holderName = currentName;
    this.renaming.set(ticketId);
  }

  protected rename(): void {
    const ticketId = this.renaming();
    if (!ticketId) return;
    this.busy.set(true);
    this.ordersService.changeHolder(this.id(), ticketId, this.holderName.trim()).subscribe({
      next: () => {
        this.toast.success('Titular actualizado');
        this.busy.set(false);
        this.renaming.set(null);
        this.load(this.id());
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.busy.set(false);
      },
    });
  }

  private load(id: string): void {
    this.ordersService.get(id).subscribe({
      next: (d) => this.detail.set(d),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
