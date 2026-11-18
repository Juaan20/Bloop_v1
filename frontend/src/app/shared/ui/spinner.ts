import { Component, input } from '@angular/core';

/**
 * Estado de carga con esqueletos (filas que "brillan") en lugar de un simple texto.
 * rows = numero de filas. El texto queda para lectores de pantalla.
 */
@Component({
  selector: 'vf-spinner',
  template: `
    <div class="space-y-4 py-4" role="status" aria-live="polite">
      <span class="sr-only">{{ label() }}</span>
      @for (r of rowList(); track $index) {
        <div class="flex items-center gap-4" aria-hidden="true">
          <div class="vf-skeleton size-9 shrink-0 rounded-lg"></div>
          <div class="flex-1 space-y-2">
            <div class="vf-skeleton h-3" [style.width.%]="60 - ($index % 3) * 12"></div>
            <div class="vf-skeleton h-2.5 w-1/4 opacity-60"></div>
          </div>
          <div class="vf-skeleton hidden h-3 w-16 sm:block"></div>
        </div>
      }
    </div>
  `,
})
export class Spinner {
  readonly label = input('Cargando...');
  readonly rows = input(4);
  protected rowList(): number[] {
    return Array.from({ length: this.rows() });
  }
}
