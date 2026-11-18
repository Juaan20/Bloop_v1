import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { Order } from '../../features/orders/order.models';
import { StatusBadge } from '../../shared/ui/status-badge';
import { Spinner } from '../../shared/ui/spinner';
import { AccountService } from './account.service';
import { AccountNav } from './account-nav';

/** Historial de compras del cliente. */
@Component({
  selector: 'vf-my-orders-page',
  imports: [CurrencyPipe, DatePipe, RouterLink, StatusBadge, Spinner, AccountNav],
  template: `
    <section class="mx-auto max-w-5xl px-4 py-12 sm:px-6">
      <h1 class="mb-6 font-display text-3xl font-bold text-white">Mi cuenta</h1>
      <vf-account-nav />
      @if (loading()) {
        <vf-spinner />
      } @else if (error()) {
        <p class="text-red-400">{{ error() }}</p>
      } @else if (orders().length === 0) {
        <p class="rounded-2xl border border-white/10 p-10 text-center text-zinc-400">Todavia no has hecho ningun pedido.</p>
      } @else {
        <div class="space-y-3">
          @for (o of orders(); track o.id) {
            <div class="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-white/10 bg-zinc-950 p-5">
              <div>
                <p class="font-mono text-xs text-zinc-500">{{ o.reference }} · {{ o.createdAt | date: 'd MMM yyyy, HH:mm' }}</p>
                <p class="mt-1 font-semibold text-white">{{ o.eventName }}</p>
                <p class="text-sm text-zinc-400">{{ o.sessionStartsAt | date: 'EEE d MMM, HH:mm' }} · {{ o.ticketCount }} entradas</p>
                @if (o.status === 'REFUNDED') {
                  <p class="mt-1 text-xs text-amber-300">Reembolsado{{ o.refundReason ? ': ' + o.refundReason : '' }}</p>
                }
              </div>
              <div class="flex items-center gap-4">
                @if (o.discountAmount > 0) { <span class="text-xs text-emerald-300">-{{ o.discountAmount | currency: o.currency }} ({{ o.promoCode }})</span> }
                <span class="font-semibold text-white">{{ o.totalAmount | currency: o.currency }}</span>
                <vf-status-badge [status]="o.status" />
                @if (o.status === 'PENDING_PAYMENT') {
                  <a [routerLink]="['/checkout', o.id]" class="vf-btn-primary">Pagar</a>
                }
                @if (o.status === 'PAID') {
                  <button type="button" class="vf-btn-secondary" [disabled]="busy()" (click)="resend(o)">Reenviar por email</button>
                }
              </div>
            </div>
          }
        </div>
      }
    </section>
  `,
})
export class MyOrdersPage implements OnInit {
  private readonly account = inject(AccountService);
  private readonly toast = inject(ToastService);
  protected readonly busy = signal(false);
  protected readonly orders = signal<Order[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected resend(order: Order): void {
    this.busy.set(true);
    this.account.resendOrder(order.id).subscribe({
      next: () => {
        this.toast.success('Te hemos reenviado las entradas a ' + order.buyerEmail);
        this.busy.set(false);
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.busy.set(false);
      },
    });
  }

  ngOnInit(): void {
    this.account.orders().subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
