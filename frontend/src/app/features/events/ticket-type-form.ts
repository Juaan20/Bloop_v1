import { Component, OnInit, inject, input, output } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { FieldError } from '../../shared/ui/field-error';
import { isoToLocalInput, localInputToIso } from '../../shared/utils/date-time';
import { TicketCategory, TicketType, TicketTypePayload } from './event.models';

/** Formulario de un tipo de entrada: precio, stock, limite por pedido y ventana de venta. */
@Component({
  selector: 'vf-ticket-type-form',
  imports: [ReactiveFormsModule, FieldError],
  template: `
    <form class="grid gap-4 sm:grid-cols-2" [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <div class="sm:col-span-2">
        <label class="vf-label" for="tt-name">Nombre</label>
        <input id="tt-name" class="vf-input" placeholder="General, Anticipada, VIP..." formControlName="name" />
        <vf-field-error [control]="form.controls.name" />
      </div>
      <div class="sm:col-span-2">
        <label class="vf-label" for="tt-description">Descripcion (opcional)</label>
        <input id="tt-description" class="vf-input" placeholder="Incluye una consumicion" formControlName="description" />
      </div>
      <div>
        <label class="vf-label" for="tt-category">Categoria</label>
        <select id="tt-category" class="vf-input" formControlName="category" (change)="onCategory()">
          <option value="GENERAL">General</option>
          <option value="EARLY_BIRD">Anticipada</option>
          <option value="VIP">Entrada VIP (acceso)</option>
          <option value="FREE">Gratuita</option>
        </select>
        @if (form.controls.category.value === 'VIP') {
          <p class="mt-1 text-xs text-zinc-500">Es una entrada con QR. Las <strong>mesas</strong> VIP no se venden como entrada: se configuran en
            Locales &gt; Zonas y mesas VIP y los clientes las reservan desde la ficha del evento.</p>
        }
      </div>
      <div>
        <label class="vf-label" for="tt-price">Precio (EUR)</label>
        <input id="tt-price" type="number" step="0.01" min="0" class="vf-input" formControlName="price" />
        <vf-field-error [control]="form.controls.price" />
      </div>
      <div>
        <label class="vf-label" for="tt-quantity">Entradas disponibles</label>
        <input id="tt-quantity" type="number" min="0" class="vf-input" formControlName="quantityTotal" />
        <vf-field-error [control]="form.controls.quantityTotal" />
        @if (ticketType(); as t) {
          <p class="mt-1 text-xs text-zinc-500">Vendidas: {{ t.quantitySold }}</p>
        }
      </div>
      <div>
        <label class="vf-label" for="tt-max">Maximo por pedido</label>
        <input id="tt-max" type="number" min="1" max="50" class="vf-input" formControlName="maxPerOrder" />
        <vf-field-error [control]="form.controls.maxPerOrder" />
      </div>
      <div>
        <label class="vf-label" for="tt-start">Inicio de venta (opcional)</label>
        <input id="tt-start" type="datetime-local" class="vf-input" formControlName="salesStartAt" />
      </div>
      <div>
        <label class="vf-label" for="tt-end">Fin de venta (opcional)</label>
        <input id="tt-end" type="datetime-local" class="vf-input" formControlName="salesEndAt" />
      </div>
      <div>
        <label class="vf-label" for="tt-order">Orden</label>
        <input id="tt-order" type="number" min="0" class="vf-input" formControlName="sortOrder" />
      </div>
      <label class="flex items-center gap-2 self-end pb-2 text-sm text-zinc-300">
        <input type="checkbox" class="accent-brand-500" formControlName="active" /> Activo (visible en la web)
      </label>
      <div class="vf-modal-actions flex justify-end sm:col-span-2">
        <button type="submit" class="vf-btn-primary" [disabled]="busy()">Guardar entrada</button>
      </div>
    </form>
  `,
})
export class TicketTypeForm implements OnInit {
  readonly ticketType = input<TicketType | null>(null);
  readonly busy = input(false);
  readonly saved = output<TicketTypePayload>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    description: [''],
    category: ['GENERAL' as TicketCategory],
    price: [10, [Validators.required, Validators.min(0)]],
    quantityTotal: [100, [Validators.required, Validators.min(0)]],
    maxPerOrder: [10, [Validators.required, Validators.min(1), Validators.max(50)]],
    salesStartAt: [''],
    salesEndAt: [''],
    sortOrder: [0, [Validators.min(0)]],
    active: [true],
  });

  ngOnInit(): void {
    const t = this.ticketType();
    if (t) {
      this.form.setValue({
        name: t.name,
        description: t.description ?? '',
        category: t.category,
        price: t.price,
        quantityTotal: t.quantityTotal,
        maxPerOrder: t.maxPerOrder,
        salesStartAt: isoToLocalInput(t.salesStartAt),
        salesEndAt: isoToLocalInput(t.salesEndAt),
        sortOrder: t.sortOrder,
        active: t.active,
      });
    }
  }

  protected onCategory(): void {
    if (this.form.controls.category.value === 'FREE') {
      this.form.controls.price.setValue(0);
    }
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saved.emit({
      ...v,
      description: v.description || null,
      salesStartAt: localInputToIso(v.salesStartAt),
      salesEndAt: localInputToIso(v.salesEndAt),
    });
  }
}
