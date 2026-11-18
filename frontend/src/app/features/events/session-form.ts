import { Component, OnInit, effect, inject, input, output } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { FieldError } from '../../shared/ui/field-error';
import { ImagePicker } from '../media/image-picker';
import { isoToLocalInput, localInputToIso } from '../../shared/utils/date-time';
import { EventSession, SessionPayload } from './event.models';

/**
 * Formulario de fechas y aforo de una sesion. Lo reutilizan la creacion de eventos
 * (primera sesion) y el detalle del evento (anadir/editar sesiones).
 * Emite el payload listo para la API; quien lo usa decide a que endpoint enviarlo.
 */
@Component({
  selector: 'vf-session-form',
  imports: [ReactiveFormsModule, FieldError, ImagePicker],
  template: `
    <form class="grid gap-4 sm:grid-cols-2" [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <div>
        <label class="vf-label" for="startsAt">Inicio</label>
        <input id="startsAt" type="datetime-local" class="vf-input" formControlName="startsAt" />
        <vf-field-error [control]="form.controls.startsAt" />
      </div>
      <div>
        <label class="vf-label" for="endsAt">Fin</label>
        <input id="endsAt" type="datetime-local" class="vf-input" formControlName="endsAt" />
        <vf-field-error [control]="form.controls.endsAt" />
      </div>
      <div>
        <label class="vf-label" for="doorsOpenAt">Apertura de puertas</label>
        <input id="doorsOpenAt" type="datetime-local" class="vf-input" formControlName="doorsOpenAt" />
      </div>
      <div>
        <label class="vf-label" for="capacity">Aforo (max. {{ maxCapacity() }})</label>
        <input id="capacity" type="number" class="vf-input" formControlName="capacity" />
        <vf-field-error [control]="form.controls.capacity" />
      </div>
      @if (showDetails()) {
        <div class="sm:col-span-2">
          <label class="vf-label" for="sTitle">Titulo del dia <span class="text-zinc-600">opcional</span></label>
          <input id="sTitle" class="vf-input" maxlength="120" formControlName="title" placeholder="Viernes · Noche techno" />
        </div>
        <div class="sm:col-span-2">
          <label class="vf-label" for="sDesc">Descripcion del dia <span class="text-zinc-600">opcional</span></label>
          <textarea id="sDesc" rows="2" class="vf-input" maxlength="1000" formControlName="description"></textarea>
        </div>
        <div class="sm:col-span-2">
          <p class="vf-label">Cartel de este dia</p>
          <vf-image-picker formControlName="imageUrl" label="cartel del dia" hint="Si no subes uno, se usa la imagen general del evento." />
        </div>
        <label class="flex items-center gap-2 text-sm text-zinc-300 sm:col-span-2">
          <input type="checkbox" class="accent-brand-500" formControlName="salesPaused" /> Pausar la venta de este dia
        </label>
      }
      @if (showSubmit()) {
        <div class="vf-modal-actions flex justify-end gap-2 sm:col-span-2">
          <button type="submit" class="vf-btn-primary" [disabled]="busy()">{{ submitLabel() }}</button>
        </div>
      }
    </form>
  `,
})
export class SessionForm implements OnInit {
  readonly session = input<EventSession | null>(null);
  readonly maxCapacity = input(100000);
  readonly submitLabel = input('Guardar sesion');
  readonly showSubmit = input(true);
  /** Titulo, descripcion, cartel y pausa de venta (en la creacion del evento se omiten). */
  readonly showDetails = input(true);
  readonly busy = input(false);
  readonly saved = output<SessionPayload>();

  readonly form = inject(NonNullableFormBuilder).group({
    startsAt: ['', Validators.required],
    endsAt: ['', Validators.required],
    doorsOpenAt: [''],
    capacity: [100, [Validators.required, Validators.min(1)]],
    title: ['', Validators.maxLength(120)],
    description: ['', Validators.maxLength(1000)],
    imageUrl: ['' as string | null],
    salesPaused: [false],
  });

  constructor() {
    // Si cambia el aforo maximo (p. ej. se elige otro local), actualizamos la validacion.
    effect(() => {
      const max = this.maxCapacity();
      const capacity = this.form.controls.capacity;
      capacity.setValidators([Validators.required, Validators.min(1), Validators.max(max)]);
      if (!this.session() && !capacity.dirty) {
        capacity.setValue(max);
      }
      capacity.updateValueAndValidity();
    });
  }

  ngOnInit(): void {
    const s = this.session();
    if (s) {
      this.form.setValue({
        startsAt: isoToLocalInput(s.startsAt),
        endsAt: isoToLocalInput(s.endsAt),
        doorsOpenAt: isoToLocalInput(s.doorsOpenAt),
        capacity: s.capacity,
        title: s.title ?? '',
        description: s.description ?? '',
        imageUrl: s.imageUrl ?? null,
        salesPaused: s.salesPaused,
      });
    }
  }

  /** Devuelve el payload si el formulario es valido (para usarlo desde un formulario padre). */
  payload(): SessionPayload | null {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return null;
    }
    const v = this.form.getRawValue();
    return {
      startsAt: localInputToIso(v.startsAt)!,
      endsAt: localInputToIso(v.endsAt)!,
      doorsOpenAt: localInputToIso(v.doorsOpenAt),
      capacity: v.capacity,
      title: v.title.trim() || null,
      description: v.description.trim() || null,
      imageUrl: v.imageUrl || null,
      salesPaused: v.salesPaused,
    };
  }

  protected submit(): void {
    const payload = this.payload();
    if (payload) {
      this.saved.emit(payload);
    }
  }
}
