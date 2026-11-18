import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { errorMessage, fieldErrors } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { EmptyState } from '../../shared/ui/empty-state';
import { FieldError } from '../../shared/ui/field-error';
import { Modal } from '../../shared/ui/modal';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { isoToLocalInput, localInputToIso } from '../../shared/utils/date-time';
import { EventSummary } from '../events/event.models';
import { EventsService } from '../events/events.service';
import { DiscountType, PromoCode, PromoCodePayload, Promoter } from './promoter.models';
import { PromotersService } from './promoters.service';

/** Codigos promocionales de la empresa: alta y edicion. */
@Component({
  selector: 'vf-promo-codes-page',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink, EmptyState, FieldError, Modal, PageHeader, Spinner],
  template: `
    <vf-page-header title="Codigos promocionales" subtitle="Descuentos que el cliente escribe al comprar">
      <a routerLink="/admin/promotores" class="vf-btn-secondary">Promotores</a>
      <button type="button" class="vf-btn-primary" (click)="open(null)">+ Nuevo codigo</button>
    </vf-page-header>

    @if (loading()) {
      <vf-spinner />
    } @else if (error()) {
      <p class="vf-card text-red-400">{{ error() }}</p>
    } @else if (codes().length === 0) {
      <vf-empty-state title="Sin codigos" description="Crea un codigo para ofrecer descuentos o para que un promotor venda con el suyo." icon="tag" />
    } @else {
      <div class="vf-card overflow-x-auto p-0">
        <table class="vf-table">
          <thead><tr><th>Codigo</th><th>Descuento</th><th>Evento</th><th>Promotor</th><th>Usos</th><th>Validez</th><th></th></tr></thead>
          <tbody>
            @for (c of codes(); track c.id) {
              <tr [class.opacity-50]="!c.active">
                <td><p class="font-mono font-semibold text-white">{{ c.code }}</p><p class="text-xs text-zinc-500">{{ c.description }}</p></td>
                <td class="text-zinc-300">{{ c.discountType === 'PERCENTAGE' ? c.discountValue + ' %' : (c.discountValue | currency: 'EUR') }}</td>
                <td class="text-zinc-300">{{ c.eventName ?? 'Todos' }}</td>
                <td class="text-zinc-300">{{ c.promoterName ?? '—' }}</td>
                <td class="text-zinc-300">{{ c.timesUsed }}{{ c.maxUses ? ' / ' + c.maxUses : '' }}</td>
                <td class="text-xs text-zinc-400">
                  {{ c.validFrom ? (c.validFrom | date: 'd/M/yy HH:mm') : '…' }} → {{ c.validUntil ? (c.validUntil | date: 'd/M/yy HH:mm') : '…' }}
                  {{ c.active ? '' : '· inactivo' }}
                </td>
                <td><button type="button" class="vf-btn-ghost text-xs" (click)="open(c)">Editar</button></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    <vf-modal [open]="showForm()" [title]="editing() ? 'Editar ' + editing()!.code : 'Nuevo codigo'" (closed)="showForm.set(false)">
      <form class="space-y-4" [formGroup]="form" (ngSubmit)="save()">
        <div>
          <label class="vf-label" for="code">Codigo</label>
          <input id="code" class="vf-input uppercase" formControlName="code" placeholder="VERANO10" />
          <vf-field-error [control]="form.controls.code" [serverError]="serverErrors()['code']" />
        </div>
        <div><label class="vf-label" for="description">Descripcion (interna)</label><input id="description" class="vf-input" formControlName="description" /></div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="vf-label" for="discountType">Tipo</label>
            <select id="discountType" class="vf-input" formControlName="discountType">
              <option value="PERCENTAGE">Porcentaje (%)</option>
              <option value="FIXED_AMOUNT">Importe fijo (€)</option>
            </select>
          </div>
          <div>
            <label class="vf-label" for="discountValue">Valor</label>
            <input id="discountValue" type="number" min="0.01" step="0.01" class="vf-input" formControlName="discountValue" />
            <vf-field-error [control]="form.controls.discountValue" />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="vf-label" for="eventId">Evento</label>
            <select id="eventId" class="vf-input" formControlName="eventId">
              <option value="">Todos los eventos</option>
              @for (e of events(); track e.id) { <option [value]="e.id">{{ e.name }}</option> }
            </select>
          </div>
          <div>
            <label class="vf-label" for="promoterId">Promotor</label>
            <select id="promoterId" class="vf-input" formControlName="promoterId">
              <option value="">Ninguno</option>
              @for (p of promoters(); track p.id) { <option [value]="p.id">{{ p.name }}</option> }
            </select>
          </div>
        </div>
        <div class="grid grid-cols-3 gap-4">
          <div><label class="vf-label" for="maxUses">Usos maximos</label><input id="maxUses" type="number" min="1" class="vf-input" formControlName="maxUses" placeholder="Ilimitado" /></div>
          <div><label class="vf-label" for="validFrom">Desde</label><input id="validFrom" type="datetime-local" class="vf-input" formControlName="validFrom" /></div>
          <div><label class="vf-label" for="validUntil">Hasta</label><input id="validUntil" type="datetime-local" class="vf-input" formControlName="validUntil" /></div>
        </div>
        <label class="flex items-center gap-2 text-sm text-zinc-300"><input type="checkbox" formControlName="active" /> Activo</label>
        @if (formError()) { <p class="text-sm text-red-400">{{ formError() }}</p> }
        <div class="vf-modal-actions flex justify-end gap-2"><button type="button" class="vf-btn-ghost" (click)="showForm.set(false)">Cancelar</button>
          <button type="submit" class="vf-btn-primary" [disabled]="saving()">{{ saving() ? 'Guardando...' : 'Guardar' }}</button></div>
      </form>
    </vf-modal>
  `,
})
export class PromoCodesPage implements OnInit {
  private readonly promotersService = inject(PromotersService);
  private readonly eventsService = inject(EventsService);
  private readonly toast = inject(ToastService);

  protected readonly codes = signal<PromoCode[]>([]);
  protected readonly events = signal<EventSummary[]>([]);
  protected readonly promoters = signal<Promoter[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly showForm = signal(false);
  protected readonly editing = signal<PromoCode | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly serverErrors = signal<Record<string, string>>({});

  protected readonly form = inject(NonNullableFormBuilder).group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9-]{3,30}$/)]],
    description: [''],
    discountType: ['PERCENTAGE' as DiscountType],
    discountValue: [10, [Validators.required, Validators.min(0.01)]],
    eventId: [''],
    promoterId: [''],
    maxUses: [null as number | null],
    validFrom: [''],
    validUntil: [''],
    active: [true],
  });

  ngOnInit(): void {
    this.load();
    this.eventsService.list({ size: 100 }).subscribe((p) => this.events.set(p.content.filter((e) => e.status !== 'CANCELLED')));
    this.promotersService.list().subscribe((p) => this.promoters.set(p));
  }

  protected open(code: PromoCode | null): void {
    this.editing.set(code);
    this.formError.set(null);
    this.serverErrors.set({});
    this.form.reset({
      code: code?.code ?? '',
      description: code?.description ?? '',
      discountType: code?.discountType ?? 'PERCENTAGE',
      discountValue: code?.discountValue ?? 10,
      eventId: code?.eventId ?? '',
      promoterId: code?.promoterId ?? '',
      maxUses: code?.maxUses ?? null,
      validFrom: isoToLocalInput(code?.validFrom),
      validUntil: isoToLocalInput(code?.validUntil),
      active: code?.active ?? true,
    });
    // El texto del codigo no se puede cambiar una vez creado.
    if (code) this.form.controls.code.disable();
    else this.form.controls.code.enable();
    this.showForm.set(true);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const payload: PromoCodePayload = {
      code: v.code,
      description: v.description || null,
      discountType: v.discountType,
      discountValue: v.discountValue,
      eventId: v.eventId || null,
      promoterId: v.promoterId || null,
      maxUses: v.maxUses || null,
      validFrom: localInputToIso(v.validFrom),
      validUntil: localInputToIso(v.validUntil),
      active: v.active,
    };
    const current = this.editing();
    const request = current ? this.promotersService.updatePromoCode(current.id, payload) : this.promotersService.createPromoCode(payload);
    this.saving.set(true);
    request.subscribe({
      next: () => {
        this.toast.success(current ? 'Codigo actualizado' : 'Codigo creado');
        this.saving.set(false);
        this.showForm.set(false);
        this.load();
      },
      error: (err) => {
        this.formError.set(errorMessage(err));
        this.serverErrors.set(fieldErrors(err));
        this.saving.set(false);
      },
    });
  }

  private load(): void {
    this.promotersService.promoCodes().subscribe({
      next: (c) => {
        this.codes.set(c);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
