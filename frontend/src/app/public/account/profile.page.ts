import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { FieldError } from '../../shared/ui/field-error';
import { Spinner } from '../../shared/ui/spinner';
import { Profile } from '../public.models';
import { AccountService } from './account.service';
import { AccountNav } from './account-nav';

/** Datos personales, consentimiento comercial (RGPD) y cambio de contrasena. */
@Component({
  selector: 'vf-profile-page',
  imports: [ReactiveFormsModule, DatePipe, FieldError, Spinner, AccountNav],
  templateUrl: './profile.page.html',
})
export class ProfilePage implements OnInit {
  private readonly account = inject(AccountService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly profile = signal<Profile | null>(null);
  protected readonly saving = signal(false);
  protected readonly passwordError = signal<string | null>(null);

  protected readonly form = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(120)]],
    phone: [''],
    marketingConsent: [false],
  });

  protected readonly passwordForm = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
  });

  ngOnInit(): void {
    this.account.profile().subscribe((p) => {
      this.profile.set(p);
      this.form.setValue({ firstName: p.firstName, lastName: p.lastName, phone: p.phone ?? '', marketingConsent: p.marketingConsent });
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    this.account.updateProfile({ ...value, phone: value.phone || null }).subscribe({
      next: (p) => {
        this.profile.set(p);
        this.auth.patchUser({ firstName: p.firstName, lastName: p.lastName });
        this.toast.success('Perfil actualizado');
        this.saving.set(false);
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.saving.set(false);
      },
    });
  }

  protected changePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.passwordError.set(null);
    this.account.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.passwordForm.reset();
        this.toast.success('Contrasena actualizada');
      },
      error: (err) => this.passwordError.set(errorMessage(err)),
    });
  }
}
