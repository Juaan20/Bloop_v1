import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/services/api-error';
import { Order } from '../../features/orders/order.models';
import { StatusBadge } from '../../shared/ui/status-badge';
import { Spinner } from '../../shared/ui/spinner';
import { Icon } from '../../shared/ui/icon';
import { CheckoutService } from './checkout.service';

/**
 * Pago de un pedido pendiente. Muestra la cuenta atras de la reserva y los medios de pago activos:
 * - Stripe Checkout (modo test): redirige a la pagina de pago de Stripe. Al volver (?pago=ok),
 *   la pagina consulta el pedido hasta que el webhook de Stripe lo marca como pagado.
 * - Pasarela simulada (solo desarrollo): permite simular un pago aprobado o rechazado.
 */
@Component({
  selector: 'vf-checkout-page',
  imports: [Icon, CurrencyPipe, DatePipe, RouterLink, Spinner, StatusBadge],
  templateUrl: './checkout.page.html',
})
export class CheckoutPage {
  private readonly checkout = inject(CheckoutService);
  private readonly router = inject(Router);

  readonly orderId = input.required<string>();
  /** ?pago=ok | cancelado: vuelta desde la pagina de Stripe. */
  readonly pago = input<string>();

  protected readonly methods = signal<{ simulated: boolean; stripe: boolean } | null>(null);
  /** Esperando a que llegue el webhook de Stripe tras volver de su pagina de pago. */
  protected readonly confirmingStripe = signal(false);
  protected readonly order = signal<Order | null>(null);
  protected readonly loading = signal(true);
  protected readonly processing = signal(false);
  protected readonly message = signal<{ ok: boolean; text: string } | null>(null);
  private readonly now = signal(Date.now());

  protected readonly secondsLeft = computed(() => {
    const expires = this.order()?.expiresAt;
    return expires ? Math.max(0, Math.floor((new Date(expires).getTime() - this.now()) / 1000)) : 0;
  });
  protected readonly countdown = computed(() => {
    const s = this.secondsLeft();
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  });

  private pollTimer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    effect(() => this.load(this.orderId()));
    const timer = setInterval(() => this.now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(timer);
      this.stopPolling();
    });
    this.checkout.paymentMethods().subscribe({
      next: (m) => this.methods.set(m),
      error: () => this.methods.set({ simulated: false, stripe: false }),
    });
    effect(() => {
      if (this.pago() === 'ok') {
        this.waitForStripeConfirmation();
      } else if (this.pago() === 'cancelado') {
        this.message.set({ ok: false, text: 'Has cancelado el pago en Stripe. Puedes intentarlo de nuevo.' });
      }
    });
  }

  /** Redirige a la pagina de pago de Stripe (el navegador sale de Bloop). */
  protected payWithStripe(): void {
    this.processing.set(true);
    this.message.set(null);
    this.checkout.stripeSession(this.orderId()).subscribe({
      next: (session) => window.location.assign(session.checkoutUrl),
      error: (err) => {
        this.message.set({ ok: false, text: errorMessage(err) });
        this.processing.set(false);
      },
    });
  }

  /** Stripe confirma el pago por webhook: consultamos el pedido cada 2 s durante un minuto. */
  private waitForStripeConfirmation(): void {
    this.confirmingStripe.set(true);
    let attempts = 0;
    this.stopPolling();
    this.pollTimer = setInterval(() => {
      attempts++;
      this.checkout.myOrder(this.orderId()).subscribe((order) => {
        this.order.set(order);
        if (order.status !== 'PENDING_PAYMENT' || attempts >= 30) {
          this.stopPolling();
          this.confirmingStripe.set(false);
          if (order.status === 'PENDING_PAYMENT') {
            this.message.set({ ok: false, text: 'Aun no hemos recibido la confirmacion de Stripe. Revisa "Mis pedidos" en unos minutos.' });
          }
        }
      });
    }, 2000);
  }

  private stopPolling(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  protected pay(approve: boolean): void {
    this.processing.set(true);
    this.message.set(null);
    this.checkout.paySimulated(this.orderId(), approve).subscribe({
      next: (result) => {
        this.order.set(result.order);
        this.message.set({ ok: result.approved, text: result.message });
        this.processing.set(false);
      },
      error: (err) => {
        this.message.set({ ok: false, text: errorMessage(err) });
        this.processing.set(false);
        this.load(this.orderId());
      },
    });
  }

  protected cancel(): void {
    this.processing.set(true);
    this.checkout.cancel(this.orderId()).subscribe({
      next: (order) => this.router.navigate(['/eventos', order.eventSlug]),
      error: (err) => {
        this.message.set({ ok: false, text: errorMessage(err) });
        this.processing.set(false);
      },
    });
  }

  private load(id: string): void {
    this.checkout.myOrder(id).subscribe({
      next: (order) => {
        this.order.set(order);
        this.loading.set(false);
      },
      error: (err) => {
        this.message.set({ ok: false, text: errorMessage(err) });
        this.loading.set(false);
      },
    });
  }
}
