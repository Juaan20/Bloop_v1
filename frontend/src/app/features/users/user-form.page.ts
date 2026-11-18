import { Component, OnInit, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { FieldError } from '../../shared/ui/field-error';
import { PageHeader } from '../../shared/ui/page-header';
import { Venue } from '../venues/venue.models';
import { VenuesService } from '../venues/venues.service';
import { Role, StaffUser, UserStatus, UsersService } from './users.service';

/** Alta y edicion de empleados: datos, roles (permisos) y locales asignados. */
@Component({
  selector: 'vf-user-form-page',
  imports: [ReactiveFormsModule, RouterLink, PageHeader, FieldError],
  templateUrl: './user-form.page.html',
})
export class UserFormPage implements OnInit {
  private readonly usersService = inject(UsersService);
  private readonly venuesService = inject(VenuesService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly id = input<string>();

  protected readonly user = signal<StaffUser | null>(null);
  protected readonly roles = signal<Role[]>([]);
  protected readonly venues = signal<Venue[]>([]);
  protected readonly selectedRoles = signal<Set<string>>(new Set());
  protected readonly selectedVenues = signal<Set<string>>(new Set());
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(120)]],
    phone: [''],
    password: ['', [Validators.required, Validators.minLength(8)]],
    status: ['ACTIVE' as UserStatus],
  });

  ngOnInit(): void {
    this.usersService.roles().subscribe((r) => this.roles.set(r));
    this.venuesService.list().subscribe((v) => this.venues.set(v));
    const id = this.id();
    if (id) {
      // Al editar, el email no cambia y la contrasena no se pide.
      this.form.controls.email.disable();
      this.form.controls.password.disable();
      this.usersService.get(id).subscribe((u) => {
        this.user.set(u);
        this.form.patchValue({ email: u.email, firstName: u.firstName, lastName: u.lastName, phone: u.phone ?? '', status: u.status });
        this.selectedRoles.set(new Set(u.roles));
        this.selectedVenues.set(new Set(u.venueIds));
      });
    }
  }

  protected toggle(set: 'roles' | 'venues', value: string): void {
    const target = set === 'roles' ? this.selectedRoles : this.selectedVenues;
    target.update((current) => {
      const next = new Set(current);
      if (next.has(value)) {
        next.delete(value);
      } else {
        next.add(value);
      }
      return next;
    });
  }

  protected save(): void {
    if (this.form.invalid || this.selectedRoles().size === 0) {
      this.form.markAllAsTouched();
      this.error.set(this.selectedRoles().size === 0 ? 'Selecciona al menos un rol' : null);
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const roles = [...this.selectedRoles()];
    const venueIds = [...this.selectedVenues()];
    const id = this.id();
    const request = id
      ? this.usersService.update(id, { firstName: v.firstName, lastName: v.lastName, phone: v.phone || null, status: v.status, roles, venueIds })
      : this.usersService.create({ email: v.email, firstName: v.firstName, lastName: v.lastName, phone: v.phone || null, password: v.password, roles, venueIds });
    request.subscribe({
      next: () => {
        this.toast.success(id ? 'Usuario actualizado' : 'Usuario creado');
        this.router.navigateByUrl('/admin/usuarios');
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.saving.set(false);
      },
    });
  }
}
