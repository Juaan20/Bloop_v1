import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Permission } from '../../core/auth/permissions';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { Modal } from '../../shared/ui/modal';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { StatusBadge } from '../../shared/ui/status-badge';
import { downloadJson } from '../../shared/utils/download';
import { CustomerDetail } from './customer.models';
import { CustomersService } from './customers.service';

/** Ficha del cliente: datos, notas, historial de compras y reservas, consentimientos y RGPD. */
@Component({
  selector: 'vf-customer-detail-page',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink, StatusLabelPipe, Modal, PageHeader, Spinner, StatusBadge],
  template: `
    @if (error()) {
      <p class="vf-card text-red-400">{{ error() }}</p>
    } @else if (detail(); as d) {
      <vf-page-header [title]="d.customer.firstName + ' ' + d.customer.lastName" [subtitle]="d.customer.email">
        @if (canManage) {
          <button type="button" class="vf-btn-secondary" (click)="export()">Exportar datos (RGPD)</button>
          <button type="button" class="vf-btn-danger" (click)="confirmDelete.set(true)">Suprimir datos</button>
        }
        <a routerLink="/admin/clientes" class="vf-btn-secondary">Volver</a>
      </vf-page-header>

      <div class="mb-6 grid gap-4 sm:grid-cols-4">
        <div class="vf-card"><p class="text-xs text-zinc-500">Pedidos</p><p class="text-2xl font-semibold text-white">{{ d.customer.ordersCount }}</p></div>
        <div class="vf-card"><p class="text-xs text-zinc-500">Entradas</p><p class="text-2xl font-semibold text-white">{{ d.customer.ticketsCount }}</p></div>
        <div class="vf-card"><p class="text-xs text-zinc-500">Gastado</p><p class="text-2xl font-semibold text-white">{{ d.customer.totalSpent | currency: 'EUR' }}</p></div>
        <div class="vf-card"><p class="text-xs text-zinc-500">Segmentos</p>
          <div class="mt-2 flex flex-wrap gap-1">@for (s of d.customer.segments; track s) { <vf-status-badge [status]="s" /> }</div></div>
      </div>

      <div class="grid gap-6 lg:grid-cols-2">
        <form class="vf-card space-y-4" [formGroup]="form" (ngSubmit)="save()">
          <h2 class="font-display font-semibold text-white">Ficha</h2>
          <div><label class="vf-label" for="phone">Telefono</label><input id="phone" class="vf-input" formControlName="phone" /></div>
          <div><label class="vf-label" for="tags">Etiquetas (separadas por comas)</label><input id="tags" class="vf-input" formControlName="tags" placeholder="cumpleanos, grupo empresa" /></div>
          <div><label class="vf-label" for="notes">Notas internas</label><textarea id="notes" rows="3" class="vf-input" formControlName="notes"></textarea></div>
          <label class="flex items-start gap-2 text-sm text-zinc-300">
            <input type="checkbox" formControlName="marketingConsent" class="mt-1" />
            <span>Acepta comunicaciones comerciales. <span class="text-zinc-500">Cambialo solo si el cliente lo pide: queda registrado en el historial.</span></span>
          </label>
          @if (canManage) {
            <button type="submit" class="vf-btn-primary" [disabled]="saving()">{{ saving() ? 'Guardando...' : 'Guardar' }}</button>
          }
        </form>

        <div class="vf-card">
          <h2 class="font-display font-semibold text-white">Historial de consentimientos</h2>
          <ul class="mt-3 space-y-2 text-sm">
            @for (c of d.consents; track c.createdAt) {
              <li class="flex justify-between text-zinc-300">
                <span>{{ c.granted ? 'Concedido' : 'Retirado' }} · {{ c.source | statusLabel }}</span>
                <span class="text-zinc-500">{{ c.createdAt | date: 'd/M/yyyy HH:mm' }}</span>
              </li>
            } @empty {
              <li class="text-zinc-500">Sin cambios registrados.</li>
            }
          </ul>
        </div>

        <div class="vf-card lg:col-span-2">
          <h2 class="font-display font-semibold text-white">Compras</h2>
          @if (d.orders.length === 0) {
            <p class="mt-3 text-sm text-zinc-500">Sin compras.</p>
          } @else {
            <table class="vf-table mt-3">
              <thead><tr><th>Referencia</th><th>Evento</th><th class="text-right">Entradas</th><th class="text-right">Total</th><th>Estado</th></tr></thead>
              <tbody>
                @for (o of d.orders; track o.id) {
                  <tr>
                    <td><a [routerLink]="['/admin/ventas', o.id]" class="font-mono text-sm text-white hover:underline">{{ o.reference }}</a></td>
                    <td class="text-zinc-300">{{ o.eventName }} <span class="text-xs text-zinc-500">{{ o.sessionStartsAt | date: 'd MMM yyyy' }}</span></td>
                    <td class="text-right text-zinc-300">{{ o.ticketCount }}</td>
                    <td class="text-right text-white">{{ o.totalAmount | currency: o.currency }}</td>
                    <td><vf-status-badge [status]="o.status" /></td>
                  </tr>
                }
              </tbody>
            </table>
          }
        </div>

        <div class="vf-card lg:col-span-2">
          <h2 class="font-display font-semibold text-white">Reservas VIP</h2>
          @if (d.reservations.length === 0) {
            <p class="mt-3 text-sm text-zinc-500">Sin reservas.</p>
          } @else {
            <ul class="mt-3 space-y-2 text-sm">
              @for (r of d.reservations; track r.id) {
                <li class="flex items-center justify-between text-zinc-300">
                  <span><span class="font-mono">{{ r.reference }}</span> · {{ r.zoneName }} / {{ r.tableName }} · {{ r.guests }} personas · {{ r.startsAt | date: 'd MMM yyyy HH:mm' }}</span>
                  <vf-status-badge [status]="r.status" />
                </li>
              }
            </ul>
          }
        </div>
      </div>

      <vf-modal [open]="confirmDelete()" title="Suprimir datos del cliente" (closed)="confirmDelete.set(false)">
        <p class="text-sm text-zinc-300">Se borraran su nombre, email, telefono, notas y etiquetas de esta ficha. <strong>No se puede deshacer.</strong></p>
        <p class="mt-2 text-sm text-zinc-500">Los pedidos se conservan porque la empresa debe guardar sus registros contables. Si tiene reservas activas, cancelalas antes.</p>
        <div class="mt-5 flex gap-3">
          <button type="button" class="vf-btn-danger" [disabled]="saving()" (click)="anonymize()">Suprimir definitivamente</button>
          <button type="button" class="vf-btn-ghost" (click)="confirmDelete.set(false)">Cancelar</button>
        </div>
      </vf-modal>
    } @else {
      <vf-spinner />
    }
  `,
})
export class CustomerDetailPage {
  private readonly customersService = inject(CustomersService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  readonly id = input.required<string>();

  protected readonly canManage = inject(AuthService).hasPermission(Permission.CUSTOMER_MANAGE);
  protected readonly detail = signal<CustomerDetail | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly confirmDelete = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    phone: ['', Validators.maxLength(40)],
    tags: ['', Validators.maxLength(500)],
    notes: ['', Validators.maxLength(2000)],
    marketingConsent: [false],
  });

  constructor() {
    effect(() => this.load(this.id()));
    if (!this.canManage) {
      this.form.disable();
    }
  }

  protected save(): void {
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.customersService
      .update(this.id(), { phone: v.phone || null, tags: v.tags || null, notes: v.notes || null, marketingConsent: v.marketingConsent })
      .subscribe({
        next: (d) => {
          this.setDetail(d);
          this.toast.success('Ficha actualizada');
          this.saving.set(false);
        },
        error: (err) => {
          this.toast.error(errorMessage(err));
          this.saving.set(false);
        },
      });
  }

  protected export(): void {
    this.customersService.export(this.id()).subscribe({
      next: (data) => downloadJson(data, `cliente-${this.id()}.json`),
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }

  protected anonymize(): void {
    this.saving.set(true);
    this.customersService.anonymize(this.id()).subscribe({
      next: () => {
        this.toast.success('Datos del cliente suprimidos');
        this.router.navigateByUrl('/admin/clientes');
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.saving.set(false);
        this.confirmDelete.set(false);
      },
    });
  }

  private load(id: string): void {
    this.customersService.get(id).subscribe({
      next: (d) => this.setDetail(d),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  private setDetail(d: CustomerDetail): void {
    this.detail.set(d);
    this.form.patchValue({
      phone: d.customer.phone ?? '',
      tags: d.customer.tags ?? '',
      notes: d.notes ?? '',
      marketingConsent: d.customer.marketingConsent,
    });
  }
}
