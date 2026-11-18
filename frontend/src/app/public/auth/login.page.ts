import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/services/api-error';
import { Icon } from '../../shared/ui/icon';
import { Logo } from '../../shared/ui/logo';
import { FieldError } from '../../shared/ui/field-error';

/**
 * Inicio de sesion comun para clientes y empleados.
 * Tras el login: vuelve a returnUrl si existe; si no, empleados al panel y clientes a sus entradas.
 */
@Component({
  selector: 'vf-login-page',
  imports: [ReactiveFormsModule, RouterLink, FieldError, Icon, Logo],
  templateUrl: './login.page.html',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Query param ?returnUrl=... (gracias a withComponentInputBinding). */
  readonly returnUrl = input<string>();

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: (user) => {
        // Cada usuario va a SU entorno: el personal al panel de gestion y los clientes a su cuenta.
        // La URL de vuelta solo se respeta si es de su propio entorno.
        const staff = user.permissions.length > 0;
        const back = this.returnUrl() ?? '';
        const backIsStaff = /^\/(admin|tpv|servicio)(\/|$)/.test(back);
        const target = back && backIsStaff === staff ? back : staff ? '/admin' : '/cuenta/entradas';
        this.router.navigateByUrl(target);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
