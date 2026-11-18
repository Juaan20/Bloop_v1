import { Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { FieldError } from '../../shared/ui/field-error';
import { PageHeader } from '../../shared/ui/page-header';
import { Venue } from '../venues/venue.models';
import { VenuesService } from '../venues/venues.service';
import { EventsService } from './events.service';
import { SessionForm } from './session-form';
import { ImagePicker } from '../media/image-picker';

/** Alta de un evento en borrador con su primera sesion. Despues se configuran las entradas. */
@Component({
  selector: 'vf-event-create-page',
  imports: [ReactiveFormsModule, RouterLink, PageHeader, FieldError, SessionForm, ImagePicker],
  template: `
    <vf-page-header title="Nuevo evento" subtitle="Se crea en borrador: podras anadir entradas y publicarlo despues">
      <a routerLink="/admin/eventos" class="vf-btn-secondary">Volver</a>
    </vf-page-header>

    @if (venues().length === 0 && !loadingVenues()) {
      <div class="vf-card max-w-2xl text-zinc-300">
        Primero necesitas un local. <a routerLink="/admin/locales/nuevo" class="vf-link">Crear local</a>
      </div>
    } @else {
      <div class="max-w-3xl space-y-6">
        <form class="vf-card grid gap-5 sm:grid-cols-2" [formGroup]="form" novalidate>
          <div class="sm:col-span-2">
            <label class="vf-label" for="venueId">Local</label>
            <select id="venueId" class="vf-input" formControlName="venueId">
              @for (v of venues(); track v.id) {
                <option [value]="v.id">{{ v.name }} · {{ v.city }} (aforo {{ v.capacity }})</option>
              }
            </select>
          </div>
          <div class="sm:col-span-2">
            <label class="vf-label" for="name">Nombre del evento</label>
            <input id="name" class="vf-input" formControlName="name" />
            <vf-field-error [control]="form.controls.name" />
          </div>
          <div class="sm:col-span-2">
            <label class="vf-label" for="description">Descripcion</label>
            <textarea id="description" rows="4" class="vf-input" formControlName="description"></textarea>
          </div>
          <div>
            <p class="vf-label">Imagen principal del evento <span class="text-zinc-600">opcional</span></p>
            <vf-image-picker formControlName="imageUrl" label="imagen principal" hint="Se usa en la cartelera, la ficha y al compartir." />
          </div>
          <div>
            <label class="vf-label" for="minAge">Edad minima (opcional)</label>
            <input id="minAge" type="number" min="0" max="99" class="vf-input" formControlName="minAge" />
          </div>
        </form>

        <div class="vf-card">
          <h2 class="mb-4 font-display font-semibold text-white">Primera sesion</h2>
          <vf-session-form [maxCapacity]="selectedVenue()?.capacity ?? 100000" [showSubmit]="false" />
        </div>

        @if (error()) {
          <p class="rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm text-red-300">{{ error() }}</p>
        }
        <button type="button" class="vf-btn-primary" [disabled]="saving()" (click)="save()">
          {{ saving() ? 'Creando...' : 'Crear evento' }}
        </button>
      </div>
    }
  `,
})
export class EventCreatePage implements OnInit {
  private readonly eventsService = inject(EventsService);
  private readonly venuesService = inject(VenuesService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly sessionForm = viewChild(SessionForm);

  protected readonly venues = signal<Venue[]>([]);
  protected readonly loadingVenues = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly venueId = signal('');
  protected readonly selectedVenue = computed(() => this.venues().find((v) => v.id === this.venueId()));

  protected readonly form = inject(NonNullableFormBuilder).group({
    venueId: ['', Validators.required],
    name: ['', [Validators.required, Validators.maxLength(200)]],
    description: [''],
    imageUrl: ['' as string | null],
    minAge: [18 as number | null],
  });

  ngOnInit(): void {
    this.form.controls.venueId.valueChanges.subscribe((id) => this.venueId.set(id));
    this.venuesService.list().subscribe((venues) => {
      const active = venues.filter((v) => v.status === 'ACTIVE');
      this.venues.set(active);
      this.loadingVenues.set(false);
      if (active.length) {
        this.form.controls.venueId.setValue(active[0].id);
      }
    });
  }

  protected save(): void {
    const session = this.sessionForm()?.payload();
    if (this.form.invalid || !session) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.eventsService
      .create({ ...v, description: v.description || null, imageUrl: v.imageUrl || null, minAge: v.minAge ?? null, firstSession: session })
      .subscribe({
        next: (event) => {
          this.toast.success('Evento creado. Ahora configura sus entradas.');
          this.router.navigate(['/admin/eventos', event.id]);
        },
        error: (err) => {
          this.error.set(errorMessage(err));
          this.saving.set(false);
        },
      });
  }
}
