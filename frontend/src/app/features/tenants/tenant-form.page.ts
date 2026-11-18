import { Component, OnInit, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { FieldError } from '../../shared/ui/field-error';
import { PageHeader } from '../../shared/ui/page-header';
import { StatusBadge } from '../../shared/ui/status-badge';
import { Tenant, TenantStatus, TenantsService } from './tenants.service';

/** Alta de empresa con su primer administrador, o edicion de una existente (SUPER_ADMIN). */
@Component({
  selector: 'vf-tenant-form-page',
  imports: [ReactiveFormsModule, RouterLink, PageHeader, FieldError, StatusBadge],
  templateUrl: './tenant-form.page.html',
})
export class TenantFormPage implements OnInit {
  private readonly tenantsService = inject(TenantsService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly id = input<string>();
  protected readonly tenant = signal<Tenant | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    legalName: [''],
    taxId: [''],
    contactEmail: ['', [Validators.required, Validators.email]],
    contactPhone: [''],
    adminFirstName: ['', Validators.required],
    adminLastName: ['', Validators.required],
    adminEmail: ['', [Validators.required, Validators.email]],
    adminPassword: ['', [Validators.required, Validators.minLength(8)]],
  });

  ngOnInit(): void {
    const id = this.id();
    if (id) {
      for (const field of ['adminFirstName', 'adminLastName', 'adminEmail', 'adminPassword'] as const) {
        this.form.controls[field].disable();
      }
      this.tenantsService.get(id).subscribe((t) => {
        this.tenant.set(t);
        this.form.patchValue({ ...t, legalName: t.legalName ?? '', taxId: t.taxId ?? '', contactPhone: t.contactPhone ?? '' });
      });
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
    const data = { name: v.name, legalName: v.legalName || null, taxId: v.taxId || null, contactEmail: v.contactEmail, contactPhone: v.contactPhone || null };
    const id = this.id();
    const request = id
      ? this.tenantsService.update(id, data)
      : this.tenantsService.create({ ...data, adminFirstName: v.adminFirstName, adminLastName: v.adminLastName, adminEmail: v.adminEmail, adminPassword: v.adminPassword });
    request.subscribe({
      next: () => {
        this.toast.success(id ? 'Empresa actualizada' : 'Empresa creada con su administrador');
        this.router.navigateByUrl('/admin/empresas');
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.saving.set(false);
      },
    });
  }

  protected setStatus(status: TenantStatus): void {
    const id = this.id();
    if (!id) return;
    this.tenantsService.changeStatus(id, status).subscribe({
      next: (t) => {
        this.tenant.set(t);
        this.toast.success(status === 'ACTIVE' ? 'Empresa reactivada' : 'Empresa suspendida');
      },
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }
}
