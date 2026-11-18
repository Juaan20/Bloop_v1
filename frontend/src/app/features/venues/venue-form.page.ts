import { Component, OnInit, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Permission } from '../../core/auth/permissions';
import { errorMessage, fieldErrors } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { FieldError } from '../../shared/ui/field-error';
import { PageHeader } from '../../shared/ui/page-header';
import { Venue, VenuePayload, VenueStatus } from './venue.models';
import { VenuesService } from './venues.service';

/** Alta y edicion de un local (la misma pantalla: si hay :id en la ruta, se edita). */
@Component({
  selector: 'vf-venue-form-page',
  imports: [ReactiveFormsModule, RouterLink, PageHeader, FieldError],
  templateUrl: './venue-form.page.html',
})
export class VenueFormPage implements OnInit {
  private readonly venuesService = inject(VenuesService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  /** Parametro :id de la ruta (undefined al crear). */
  readonly id = input<string>();

  protected readonly canManage = inject(AuthService).hasPermission(Permission.VENUE_MANAGE);
  protected readonly venue = signal<Venue | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly serverErrors = signal<Record<string, string>>({});

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    description: [''],
    addressLine: ['', [Validators.required, Validators.maxLength(255)]],
    city: ['', [Validators.required, Validators.maxLength(100)]],
    postalCode: [''],
    country: ['ES', [Validators.required, Validators.pattern(/^[A-Z]{2}$/)]],
    phone: [''],
    email: ['', [Validators.email]],
    capacity: [100, [Validators.required, Validators.min(1), Validators.max(100000)]],
    openingHours: [''],
    imageUrl: ['', [Validators.pattern(/^$|^https?:\/\/.+/)]],
    status: ['ACTIVE' as VenueStatus],
  });

  ngOnInit(): void {
    const id = this.id();
    if (id) {
      this.venuesService.get(id).subscribe((v) => {
        this.venue.set(v);
        this.form.patchValue({
          ...v,
          description: v.description ?? '',
          postalCode: v.postalCode ?? '',
          phone: v.phone ?? '',
          email: v.email ?? '',
          openingHours: v.openingHours ?? '',
          imageUrl: v.imageUrl ?? '',
        });
      });
    }
    if (!this.canManage) {
      this.form.disable();
    }
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const payload: VenuePayload = {
      ...v,
      description: v.description || null,
      postalCode: v.postalCode || null,
      phone: v.phone || null,
      email: v.email || null,
      openingHours: v.openingHours || null,
      imageUrl: v.imageUrl || null,
    };
    const id = this.id();
    const request = id ? this.venuesService.update(id, payload) : this.venuesService.create(payload);
    request.subscribe({
      next: () => {
        this.toast.success(id ? 'Local actualizado' : 'Local creado');
        this.router.navigateByUrl('/admin/locales');
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.serverErrors.set(fieldErrors(err));
        this.saving.set(false);
      },
    });
  }
}
