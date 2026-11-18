import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage, fieldErrors } from '../../core/services/api-error';
import { FieldError } from '../../shared/ui/field-error';

/** Registro de clientes. La aceptacion de la politica de privacidad es obligatoria (RGPD). */
@Component({
  selector: 'vf-register-page',
  imports: [ReactiveFormsModule, RouterLink, FieldError],
  templateUrl: './register.page.html',
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly returnUrl = input<string>();

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly serverErrors = signal<Record<string, string>>({});

  protected readonly form = inject(NonNullableFormBuilder).group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    password: ['', [Validators.required, Validators.minLength(8)]],
    acceptPrivacy: [false, [Validators.requiredTrue]],
    marketingConsent: [false],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    this.auth.register({ ...value, phone: value.phone || null }).subscribe({
      next: () => this.router.navigateByUrl(this.returnUrl() || '/eventos'),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.serverErrors.set(fieldErrors(err));
        this.loading.set(false);
      },
    });
  }
}
