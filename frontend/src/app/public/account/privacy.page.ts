import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { Spinner } from '../../shared/ui/spinner';
import { MyConsent, Profile } from '../public.models';
import { AccountService } from './account.service';
import { AccountNav } from './account-nav';

/**
 * "Mi cuenta > Privacidad" (RGPD): empresas que tienen tus datos y tu consentimiento comercial,
 * resumen visual de tus datos y supresion de la cuenta. (La exportacion tecnica /api/me/data-export
 * sigue existiendo en la API para atender solicitudes formales, pero no se ofrece como boton.)
 */
@Component({
  selector: 'vf-privacy-page',
  imports: [DatePipe, FormsModule, RouterLink, Spinner, AccountNav],
  template: `
    <section class="mx-auto max-w-5xl px-4 py-12 sm:px-6">
      <h1 class="mb-6 font-display text-3xl font-bold text-white">Mi cuenta</h1>
      <vf-account-nav />

      <div class="space-y-6">
        <div class="rounded-2xl border border-white/10 bg-zinc-950 p-6">
          <h2 class="font-display text-xl font-semibold text-white">Empresas que tienen tus datos</h2>
          <p class="mt-1 text-sm text-zinc-400">Cada organizador al que compras guarda tu ficha. Decide si puede enviarte comunicaciones comerciales.</p>
          @if (loading()) {
            <vf-spinner />
          } @else if (error()) {
            <p class="mt-4 text-red-400">{{ error() }}</p>
          } @else if (consents().length === 0) {
            <p class="mt-4 text-sm text-zinc-500">Ninguna empresa tiene todavia una ficha tuya.</p>
          } @else {
            <ul class="mt-4 divide-y divide-white/5">
              @for (c of consents(); track c.customerId) {
                <li class="flex flex-wrap items-center justify-between gap-3 py-3">
                  <div>
                    <p class="font-medium text-white">{{ c.companyName }}</p>
                    @if (c.lastPurchaseAt) { <p class="text-xs text-zinc-500">Ultima compra {{ c.lastPurchaseAt | date: 'd MMM yyyy' }}</p> }
                  </div>
                  <label class="flex items-center gap-2 text-sm text-zinc-300">
                    <input type="checkbox" [checked]="c.marketingConsent" [disabled]="busy()" (change)="toggle(c)" />
                    Acepto comunicaciones comerciales
                  </label>
                </li>
              }
            </ul>
          }
        </div>

        <!-- Tus datos, presentados de forma visual (sin descargas tecnicas) -->
        <div class="rounded-2xl border border-white/10 bg-zinc-950 p-6">
          <h2 class="font-display text-xl font-semibold text-white">Tus datos en Bloop</h2>
          <p class="mt-1 text-sm text-zinc-400">Esto es todo lo que guardamos de ti en tu cuenta.</p>
          @if (profile(); as p) {
            <dl class="mt-4 grid gap-3 sm:grid-cols-2">
              <div class="rounded-xl bg-white/[0.03] p-3"><dt class="text-xs text-zinc-500">Nombre</dt><dd class="text-zinc-100">{{ p.firstName }} {{ p.lastName }}</dd></div>
              <div class="rounded-xl bg-white/[0.03] p-3"><dt class="text-xs text-zinc-500">Email</dt><dd class="truncate text-zinc-100">{{ p.email }}</dd></div>
              <div class="rounded-xl bg-white/[0.03] p-3"><dt class="text-xs text-zinc-500">Telefono</dt><dd class="text-zinc-100">{{ p.phone || 'No indicado' }}</dd></div>
              <div class="rounded-xl bg-white/[0.03] p-3"><dt class="text-xs text-zinc-500">Cuenta creada</dt><dd class="text-zinc-100">{{ p.createdAt | date: 'd MMM yyyy' }}{{ p.privacyAcceptedAt ? ' · privacidad aceptada el ' + (p.privacyAcceptedAt | date: 'd MMM yyyy') : '' }}</dd></div>
            </dl>
          }
          <div class="mt-3 grid grid-cols-3 gap-3 text-center">
            <a routerLink="/cuenta/entradas" class="rounded-xl bg-white/[0.03] p-3 hover:bg-white/[0.06]"><p class="font-display text-2xl font-semibold text-white">{{ counts().tickets ?? '—' }}</p><p class="text-xs text-zinc-500">Entradas</p></a>
            <a routerLink="/cuenta/pedidos" class="rounded-xl bg-white/[0.03] p-3 hover:bg-white/[0.06]"><p class="font-display text-2xl font-semibold text-white">{{ counts().orders ?? '—' }}</p><p class="text-xs text-zinc-500">Pedidos</p></a>
            <a routerLink="/cuenta/reservas" class="rounded-xl bg-white/[0.03] p-3 hover:bg-white/[0.06]"><p class="font-display text-2xl font-semibold text-white">{{ counts().reservations ?? '—' }}</p><p class="text-xs text-zinc-500">Reservas</p></a>
          </div>
          <p class="mt-4 text-xs text-zinc-500">
            Puedes corregir tu nombre y telefono en <a routerLink="/cuenta/perfil" class="vf-link">Mi perfil</a>. Si necesitas una copia formal de tus datos
            (derecho de acceso y portabilidad del RGPD), solicitala al organizador o al soporte de Bloop y te la enviaremos.
          </p>
        </div>

        <div class="rounded-2xl border border-red-500/30 bg-red-500/5 p-6">
          <h2 class="font-display text-xl font-semibold text-white">Eliminar mi cuenta</h2>
          <p class="mt-1 text-sm text-zinc-400">
            Se borraran tu nombre, email y telefono de la cuenta, de tus pedidos, entradas y reservas. No podras volver a entrar.
            Los organizadores conservan los importes de las compras por obligacion contable, sin tus datos personales.
          </p>
          <p class="mt-2 text-sm text-zinc-500">No es posible si tienes entradas para eventos que aun no se han celebrado o reservas activas.</p>
          <form class="mt-4 flex flex-wrap items-end gap-3" (ngSubmit)="deleteAccount()">
            <div>
              <label class="vf-label" for="password">Confirma con tu contrasena</label>
              <input id="password" name="password" type="password" class="vf-input" autocomplete="current-password" [(ngModel)]="password" />
            </div>
            <button type="submit" class="vf-btn-danger" [disabled]="busy() || !password">Eliminar mi cuenta</button>
          </form>
          @if (deleteError()) { <p class="mt-3 text-sm text-red-400">{{ deleteError() }}</p> }
        </div>
      </div>
    </section>
  `,
})
export class PrivacyPage implements OnInit {
  private readonly account = inject(AccountService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  protected readonly consents = signal<MyConsent[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly deleteError = signal<string | null>(null);
  protected password = '';
  protected readonly profile = signal<Profile | null>(null);
  protected readonly counts = signal<{ tickets?: number; orders?: number; reservations?: number }>({});

  ngOnInit(): void {
    this.account.profile().subscribe({ next: (p) => this.profile.set(p), error: () => this.profile.set(null) });
    this.account.tickets().subscribe({ next: (t) => this.counts.update((c) => ({ ...c, tickets: t.length })), error: () => {} });
    this.account.orders().subscribe({ next: (o) => this.counts.update((c) => ({ ...c, orders: o.length })), error: () => {} });
    this.account.reservations().subscribe({ next: (r) => this.counts.update((c) => ({ ...c, reservations: r.length })), error: () => {} });
    this.account.consents().subscribe({
      next: (c) => {
        this.consents.set(c);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected toggle(consent: MyConsent): void {
    this.busy.set(true);
    this.account.updateConsent(consent.customerId, !consent.marketingConsent).subscribe({
      next: (updated) => {
        this.consents.update((list) => list.map((c) => (c.customerId === updated.customerId ? updated : c)));
        this.toast.success(updated.marketingConsent ? 'Consentimiento concedido' : 'Consentimiento retirado');
        this.busy.set(false);
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.busy.set(false);
      },
    });
  }

  protected deleteAccount(): void {
    if (!confirm('¿Seguro que quieres eliminar tu cuenta? No se puede deshacer.')) return;
    this.busy.set(true);
    this.deleteError.set(null);
    this.account.deleteAccount(this.password).subscribe({
      next: () => {
        this.toast.success('Tu cuenta se ha eliminado');
        this.auth.logout();
      },
      error: (err) => {
        this.deleteError.set(errorMessage(err));
        this.busy.set(false);
      },
    });
  }
}
