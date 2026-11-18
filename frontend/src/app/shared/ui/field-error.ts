import { Component, input } from '@angular/core';
import { AbstractControl } from '@angular/forms';

/**
 * Muestra el error de validacion de un campo de formulario reactivo cuando ya se ha tocado.
 * Uso: <vf-field-error [control]="form.controls.email" />
 */
@Component({
  selector: 'vf-field-error',
  template: `
    @if (message(); as msg) {
      <p class="mt-1 text-xs text-red-400">{{ msg }}</p>
    }
  `,
})
export class FieldError {
  readonly control = input.required<AbstractControl>();
  /** Error devuelto por el backend para este campo (opcional). */
  readonly serverError = input<string | undefined>();

  protected message(): string | null {
    const control = this.control();
    if (this.serverError()) {
      return this.serverError() ?? null;
    }
    if (!control.touched || !control.errors) {
      return null;
    }
    const errors = control.errors;
    if (errors['required']) return 'Este campo es obligatorio';
    if (errors['email']) return 'Introduce un email valido';
    if (errors['minlength']) return `Minimo ${errors['minlength'].requiredLength} caracteres`;
    if (errors['maxlength']) return `Maximo ${errors['maxlength'].requiredLength} caracteres`;
    if (errors['min']) return `El valor minimo es ${errors['min'].min}`;
    if (errors['max']) return `El valor maximo es ${errors['max'].max}`;
    if (errors['pattern']) return 'Formato no valido';
    if (errors['requiredTrue']) return 'Debes aceptar esta condicion';
    return 'Valor no valido';
  }
}
