import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { Reservation } from '../../features/reservations/reservation.models';
import { Spinner } from '../../shared/ui/spinner';
import { StatusBadge } from '../../shared/ui/status-badge';
import { AccountService } from './account.service';
import { AccountNav } from './account-nav';

/** Reservas VIP del cliente. Puede cancelar las pendientes o confirmadas que aun no han empezado. */
@Component({
  selector: 'vf-my-reservations-page',
  imports: [CurrencyPipe, DatePipe, RouterLink, Spinner, StatusBadge, AccountNav],
  template: `
    <section class="mx-auto max-w-5xl px-4 py-12 sm:px-6">
      <h1 class="mb-6 font-display text-3xl font-bold text-white">Mi cuenta</h1>
      <vf-account-nav />
      @if (loading()) {
        <vf-spinner />
      } @else if (error()) {
        <p class="text-red-400">{{ error() }}</p>
      } @else if (reservations().length === 0) {
        <div class="rounded-2xl border border-white/10 p-10 text-center">
          <p class="text-zinc-300">No tienes reservas VIP.</p>
          <p class="mt-1 text-sm text-zinc-500">Puedes reservar mesa desde la ficha de los eventos que la ofrecen.</p>
          <a routerLink="/eventos" class="mt-4 inline-flex rounded-xl bg-white px-5 py-2.5 font-semibold text-black">Ver eventos</a>
        </div>
      } @else {
        <div class="space-y-3">
          @for (r of reservations(); track r.id) {
            <div class="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-white/10 bg-zinc-950 p-5">
              <div>
                <p class="font-mono text-xs text-zinc-500">{{ r.reference }}</p>
                <p class="mt-1 font-semibold text-white">{{ r.eventName ?? r.venueName }}</p>
                <p class="text-sm text-zinc-400">{{ r.startsAt | date: 'EEE d MMM, HH:mm' }} · {{ r.venueName }}</p>
                <p class="mt-1 text-sm text-zinc-300">{{ r.zoneName }} · {{ r.tableName }} · {{ r.guests }} personas · consumo minimo {{ r.minSpend | currency: 'EUR' }}</p>
                @if (r.depositAmount > 0) {
                  <p class="text-xs" [class]="r.depositPending ? 'text-amber-300' : 'text-emerald-300'">
                    Anticipo {{ r.depositAmount | currency: 'EUR' }} · {{ r.depositPending ? 'pendiente: el local te indicara como abonarlo' : 'abonado' }}
                  </p>
                }
              </div>
              <div class="flex items-center gap-3">
                <vf-status-badge [status]="r.status" />
                @if ((r.status === 'PENDING' || r.status === 'CONFIRMED') && isFuture(r.startsAt)) {
                  <button type="button" class="vf-btn-secondary" [disabled]="busy()" (click)="cancel(r)">Cancelar</button>
                }
              </div>
            </div>
          }
        </div>
      }
    </section>
  `,
})
export class MyReservationsPage implements OnInit {
  private readonly account = inject(AccountService);
  private readonly toast = inject(ToastService);
  protected readonly reservations = signal<Reservation[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.account.reservations().subscribe({
      next: (r) => {
        this.reservations.set(r);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected isFuture(iso: string): boolean {
    return new Date(iso).getTime() > Date.now();
  }

  protected cancel(r: Reservation): void {
    if (!confirm(`¿Cancelar la reserva ${r.reference}?`)) return;
    this.busy.set(true);
    this.account.cancelReservation(r.id).subscribe({
      next: (updated) => {
        this.reservations.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
        this.toast.success('Reserva cancelada');
        this.busy.set(false);
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.busy.set(false);
      },
    });
  }
}
