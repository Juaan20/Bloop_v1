import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/services/api-error';
import { Spinner } from '../../shared/ui/spinner';
import { Terminal } from './pos.models';
import { PosService } from './pos.service';

/** Eleccion de terminal (solo los de los locales donde trabaja el usuario). */
@Component({
  selector: 'vf-pos-select-page',
  imports: [DatePipe, RouterLink, Spinner],
  template: `
    <div class="min-h-screen bg-zinc-950 px-6 py-10">
      <div class="mx-auto max-w-4xl">
        <div class="mb-8 flex items-center justify-between">
          <h1 class="font-display text-3xl font-bold text-white">TPV · Elige terminal</h1>
          <a routerLink="/admin" class="vf-btn-secondary">Volver al panel</a>
        </div>
        @if (loading()) {
          <vf-spinner />
        } @else if (error()) {
          <p class="vf-card text-red-400">{{ error() }}</p>
        } @else if (terminals().length === 0) {
          <p class="vf-card text-zinc-400">No hay terminales activos en tus locales. Pide a un encargado que cree uno o que te asigne a un local.</p>
        } @else {
          <div class="grid gap-4 sm:grid-cols-2">
            @for (t of terminals(); track t.id) {
              <a [routerLink]="['/tpv', t.id]" class="rounded-2xl border border-zinc-800 bg-zinc-900 p-6 transition hover:border-brand-500">
                <p class="text-xs tracking-widest text-zinc-500 uppercase">{{ t.type === 'BAR' ? 'Barra' : t.type === 'VIP_SERVICE' ? 'Servicio VIP' : 'Taquilla' }} · {{ t.venueName }}</p>
                <p class="mt-1 font-display text-2xl font-semibold text-white">{{ t.name }}</p>
                <p class="mt-3 text-sm" [class]="t.openSessionId ? 'text-emerald-300' : 'text-zinc-500'">
                  {{ t.openSessionId ? 'Caja abierta por ' + t.openedByName + ' · ' + (t.openSessionSince | date: 'HH:mm') : 'Caja cerrada' }}
                </p>
              </a>
            }
          </div>
        }
      </div>
    </div>
  `,
})
export class PosSelectPage implements OnInit {
  private readonly posService = inject(PosService);
  protected readonly terminals = signal<Terminal[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.posService.myTerminals().subscribe({
      next: (t) => {
        this.terminals.set(t);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
