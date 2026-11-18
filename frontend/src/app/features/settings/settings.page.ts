import { Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { FieldError } from '../../shared/ui/field-error';
import { PageHeader } from '../../shared/ui/page-header';
import { Icon } from '../../shared/ui/icon';
import { AlertsService } from '../alerts/alerts.service';
import { Tenant, TenantsService } from '../tenants/tenants.service';

/** Configuracion de la propia empresa (TENANT_SETTINGS). */
@Component({
  selector: 'vf-settings-page',
  imports: [ReactiveFormsModule, PageHeader, FieldError, Icon],
  template: `
    <vf-page-header title="Configuracion" subtitle="Datos fiscales y de contacto de tu empresa" />
    <form class="vf-card grid max-w-3xl gap-4 sm:grid-cols-2" [formGroup]="form" (ngSubmit)="save()" novalidate>
      <div class="sm:col-span-2">
        <label class="vf-label" for="name">Nombre comercial</label>
        <input id="name" class="vf-input" formControlName="name" />
        <vf-field-error [control]="form.controls.name" />
      </div>
      <div>
        <label class="vf-label" for="legalName">Razon social</label>
        <input id="legalName" class="vf-input" formControlName="legalName" />
      </div>
      <div>
        <label class="vf-label" for="taxId">CIF / NIF</label>
        <input id="taxId" class="vf-input" formControlName="taxId" />
      </div>
      <div>
        <label class="vf-label" for="contactEmail">Email de contacto</label>
        <input id="contactEmail" type="email" class="vf-input" formControlName="contactEmail" />
        <vf-field-error [control]="form.controls.contactEmail" />
      </div>
      <div>
        <label class="vf-label" for="contactPhone">Telefono</label>
        <input id="contactPhone" class="vf-input" formControlName="contactPhone" />
      </div>
      @if (tenant(); as t) {
        <p class="text-xs text-zinc-500 sm:col-span-2">Identificador interno: {{ t.slug }}</p>
      }
      @if (error()) {
        <p class="text-sm text-red-400 sm:col-span-2">{{ error() }}</p>
      }
      <div class="sm:col-span-2">
        <button type="submit" class="vf-btn-primary" [disabled]="saving()">Guardar</button>
      </div>
    </form>

    <!-- Diagnostico del correo -->
    <section class="vf-card mt-6 max-w-3xl">
      <h2 class="font-display text-base font-semibold text-white">Correo electronico</h2>
      <p class="mt-1 text-sm text-zinc-400">Envia un correo de prueba a tu propia direccion para comprobar la configuracion SMTP del servidor.</p>
      <button type="button" class="vf-btn-secondary mt-3" [disabled]="testing()" (click)="sendTest()"><vf-icon name="mail" class="size-4" /> {{ testing() ? 'Enviando...' : 'Enviar correo de prueba' }}</button>
      @if (testResult(); as r) {
        <div class="mt-3 rounded-lg p-3 text-sm" role="status"
             [class]="r.status === 'SENT' ? 'bg-emerald-500/10 text-emerald-200' : r.status === 'LOGGED' ? 'bg-zinc-800 text-zinc-300' : 'bg-red-500/10 text-red-200'">
          @switch (r.status) {
            @case ('SENT') { El servidor de correo ha aceptado el envio a {{ r.recipient }}. Revisa tu bandeja de entrada (y la de spam). }
            @case ('LOGGED') { El envio real esta desactivado (MAIL_ENABLED=false): el correo NO ha salido. Puedes verlo en Notificaciones > Correos enviados. }
            @default { No se pudo enviar: {{ r.errorMessage }} }
          }
        </div>
      }
    </section>
  `,
})
export class SettingsPage implements OnInit {
  private readonly tenantsService = inject(TenantsService);
  private readonly toast = inject(ToastService);
  protected readonly tenant = signal<Tenant | null>(null);
  protected readonly saving = signal(false);
  private readonly alerts = inject(AlertsService);
  protected readonly testing = signal(false);
  protected readonly testResult = signal<{ recipient: string; status: 'SENT' | 'LOGGED' | 'FAILED'; errorMessage: string | null } | null>(null);

  protected sendTest(): void {
    this.testing.set(true);
    this.testResult.set(null);
    this.alerts.testEmail().subscribe({
      next: (r) => {
        this.testResult.set(r);
        this.testing.set(false);
      },
      error: (err) => {
        this.testResult.set({ recipient: '', status: 'FAILED', errorMessage: errorMessage(err) });
        this.testing.set(false);
      },
    });
  }
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    legalName: [''],
    taxId: [''],
    contactEmail: ['', [Validators.required, Validators.email]],
    contactPhone: [''],
  });

  ngOnInit(): void {
    this.tenantsService.myTenant().subscribe((t) => {
      this.tenant.set(t);
      this.form.setValue({ name: t.name, legalName: t.legalName ?? '', taxId: t.taxId ?? '', contactEmail: t.contactEmail, contactPhone: t.contactPhone ?? '' });
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const v = this.form.getRawValue();
    this.tenantsService
      .updateMyTenant({ ...v, legalName: v.legalName || null, taxId: v.taxId || null, contactPhone: v.contactPhone || null })
      .subscribe({
        next: (t) => {
          this.tenant.set(t);
          this.toast.success('Configuracion guardada');
          this.saving.set(false);
        },
        error: (err) => {
          this.error.set(errorMessage(err));
          this.saving.set(false);
        },
      });
  }
}
