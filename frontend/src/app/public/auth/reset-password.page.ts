import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/services/api-error';
import { FieldError } from '../../shared/ui/field-error';

/** Pagina del enlace de recuperacion: /restablecer-contrasena?token=... */
@Component({
  selector: 'vf-reset-password-page',
  imports: [ReactiveFormsModule, RouterLink, FieldError],
  template: `
    <section class="flex min-h-[calc(100vh-4rem)] items-center justify-center px-4 py-16">
      <div class="w-full max-w-md rounded-2xl border border-white/10 bg-zinc-950/80 p-8">
        <h1 class="font-display text-2xl font-semibold text-white">Nueva contrasena</h1>
        @if (done()) {
          <p class="mt-4 text-sm text-zinc-300">Contrasena actualizada. Ya puedes entrar con ella.</p>
          <a routerLink="/login" class="vf-btn-primary mt-6">Entrar</a>
        } @else if (!token()) {
          <p class="mt-4 text-sm text-red-400">El enlace no es valido.</p>
        } @else {
          <form class="mt-6 space-y-4" [formGroup]="form" (ngSubmit)="submit()" novalidate>
            <div>
              <label class="vf-label" for="password">Nueva contrasena</label>
              <input id="password" type="password" class="vf-input" formControlName="password" autocomplete="new-password" />
              <vf-field-error [control]="form.controls.password" />
            </div>
            @if (error()) {
              <p class="text-sm text-red-400">{{ error() }}</p>
            }
            <button type="submit" class="vf-btn-primary w-full" [disabled]="loading()">Guardar</button>
          </form>
        }
      </div>
    </section>
  `,
})
export class ResetPasswordPage {
  private readonly auth = inject(AuthService);
  readonly token = input<string>();
  protected readonly done = signal(false);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.auth.resetPassword(this.token() ?? '', this.form.getRawValue().password).subscribe({
      next: () => this.done.set(true),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
