import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { EmptyState } from '../../shared/ui/empty-state';
import { Modal } from '../../shared/ui/modal';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { CommissionType, Promoter, PromoterCandidate } from './promoter.models';
import { PromotersService } from './promoters.service';

/** Listado de promotores / RRPP con sus ventas y alta de nuevos (a partir de empleados con rol Promotor). */
@Component({
  selector: 'vf-promoter-list-page',
  imports: [CurrencyPipe, ReactiveFormsModule, RouterLink, EmptyState, Modal, PageHeader, Spinner],
  template: `
    <vf-page-header title="Promotores / RRPP" subtitle="Enlaces de venta, comisiones y objetivos">
      <a routerLink="/admin/codigos" class="vf-btn-secondary">Codigos promocionales</a>
      <button type="button" class="vf-btn-primary" (click)="openCreate()">+ Nuevo promotor</button>
    </vf-page-header>

    @if (loading()) {
      <vf-spinner />
    } @else if (error()) {
      <p class="vf-card text-red-400">{{ error() }}</p>
    } @else if (promoters().length === 0) {
      <vf-empty-state title="Sin promotores" description="Crea un empleado con el rol Promotor / RRPP en Equipo y dalo de alta aqui." icon="megaphone" />
    } @else {
      <div class="vf-card overflow-x-auto p-0">
        <table class="vf-table">
          <thead><tr><th>Promotor</th><th>Codigo</th><th>Comision</th><th class="text-right">Eventos</th><th class="text-right">Entradas</th><th class="text-right">Ventas</th><th class="text-right">Comision</th></tr></thead>
          <tbody>
            @for (p of promoters(); track p.id) {
              <tr [class.opacity-50]="!p.active">
                <td><a [routerLink]="['/admin/promotores', p.id]" class="text-white hover:underline">{{ p.name }}</a>
                  <p class="text-xs text-zinc-500">{{ p.email }}{{ p.active ? '' : ' · inactivo' }}</p></td>
                <td class="font-mono text-sm text-zinc-300">{{ p.referralCode }}</td>
                <td class="text-zinc-300">{{ p.commissionType === 'PERCENTAGE' ? p.commissionValue + ' %' : (p.commissionValue | currency: 'EUR') + '/entrada' }}</td>
                <td class="text-right text-zinc-300">{{ p.eventsAssigned }}</td>
                <td class="text-right text-zinc-300">{{ p.ticketsSold }}</td>
                <td class="text-right text-white">{{ p.revenue | currency: 'EUR' }}</td>
                <td class="text-right text-emerald-300">{{ p.commission | currency: 'EUR' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    <vf-modal [open]="showCreate()" title="Nuevo promotor" (closed)="showCreate.set(false)">
      @if (candidates().length === 0) {
        <p class="text-sm text-zinc-400">No hay empleados con el rol Promotor / RRPP pendientes de alta.
          Crealos primero en <a routerLink="/admin/usuarios" class="vf-link" (click)="showCreate.set(false)">Equipo y roles</a>.</p>
      } @else {
        <form class="space-y-4" [formGroup]="form" (ngSubmit)="create()">
          <div>
            <label class="vf-label" for="userId">Empleado</label>
            <select id="userId" class="vf-input" formControlName="userId">
              @for (c of candidates(); track c.userId) { <option [value]="c.userId">{{ c.name }} · {{ c.email }}</option> }
            </select>
          </div>
          <div>
            <label class="vf-label" for="referralCode">Codigo del enlace (opcional)</label>
            <input id="referralCode" class="vf-input uppercase" formControlName="referralCode" placeholder="Se genera si lo dejas vacio" />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="vf-label" for="commissionType">Comision</label>
              <select id="commissionType" class="vf-input" formControlName="commissionType">
                <option value="PERCENTAGE">Porcentaje (%)</option>
                <option value="FIXED_PER_TICKET">Fija por entrada (€)</option>
              </select>
            </div>
            <div>
              <label class="vf-label" for="commissionValue">Valor</label>
              <input id="commissionValue" type="number" min="0" step="0.01" class="vf-input" formControlName="commissionValue" />
            </div>
          </div>
          @if (formError()) { <p class="text-sm text-red-400">{{ formError() }}</p> }
          <button type="submit" class="vf-btn-primary" [disabled]="saving() || form.invalid">Dar de alta</button>
        </form>
      }
    </vf-modal>
  `,
})
export class PromoterListPage implements OnInit {
  private readonly promotersService = inject(PromotersService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  protected readonly promoters = signal<Promoter[]>([]);
  protected readonly candidates = signal<PromoterCandidate[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly showCreate = signal(false);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    userId: ['', Validators.required],
    referralCode: ['', Validators.pattern(/^$|^[A-Za-z0-9-]{3,30}$/)],
    commissionType: ['PERCENTAGE' as CommissionType],
    commissionValue: [10, [Validators.required, Validators.min(0)]],
  });

  ngOnInit(): void {
    this.promotersService.list().subscribe({
      next: (p) => {
        this.promoters.set(p);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected openCreate(): void {
    this.formError.set(null);
    this.promotersService.candidates().subscribe({
      next: (c) => {
        this.candidates.set(c);
        this.form.patchValue({ userId: c[0]?.userId ?? '' });
        this.showCreate.set(true);
      },
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }

  protected create(): void {
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.promotersService
      .create({ userId: v.userId, referralCode: v.referralCode || null, commissionType: v.commissionType, commissionValue: v.commissionValue })
      .subscribe({
        next: (d) => {
          this.toast.success('Promotor dado de alta');
          this.router.navigate(['/admin/promotores', d.promoter.id]);
        },
        error: (err) => {
          this.formError.set(errorMessage(err));
          this.saving.set(false);
        },
      });
  }
}
