import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../core/services/api-error';
import { FieldError } from '../../shared/ui/field-error';

/** Solicitud de recuperacion de contrasena. En local, el enlace aparece en el log del backend. */
@Component({
  selector: 'vf-forgot-password-page',
  imports: [ReactiveFormsModule, RouterLink, FieldError],
  template: `
    <section class="flex min-h-[calc(100vh-4rem)] items-center justify-center px-4 py-16">
      <div class="w-full max-w-md rounded-2xl border border-white/10 bg-zinc-950/80 p-8">
        <h1 class="font-display text-2xl font-semibold text-white">Recuperar contrasena</h1>
        @if (sent()) {
          <p class="mt-4 text-sm text-zinc-300">
            Si existe una cuenta con ese email, recibiras un enlace para elegir una nueva contrasena.
            En desarrollo local, el enlace se muestra en el log del backend.
          </p>
          <a routerLink="/login" class="vf-btn-secondary mt-6">Volver a entrar</a>
        } @else {
          <p class="mt-2 text-sm text-zinc-400">Te enviaremos un enlace valido durante 1 hora.</p>
          <form class="mt-6 space-y-4" [formGroup]="form" (ngSubmit)="submit()" novalidate>
            <div>
              <label class="vf-label" for="email">Email</label>
              <input id="email" type="email" class="vf-input" formControlName="email" />
              <vf-field-error [control]="form.controls.email" />
            </div>
            @if (error()) {
              <p class="text-sm text-red-400">{{ error() }}</p>
            }
            <button type="submit" class="vf-btn-primary w-full" [disabled]="loading()">Enviar enlace</button>
          </form>
        }
      </div>
    </section>
  `,
})
export class ForgotPasswordPage {
  private readonly auth = inject(AuthService);
  protected readonly sent = signal(false);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.auth.forgotPassword(this.form.getRawValue().email).subscribe({
      next: () => this.sent.set(true),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
