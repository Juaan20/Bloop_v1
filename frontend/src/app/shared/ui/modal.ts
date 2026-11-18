import { Component, ElementRef, effect, inject, input, output } from '@angular/core';
import { Icon } from './icon';

/**
 * Ventana modal. Uso:
 * <vf-modal [open]="showForm()" title="Nueva sesion" (closed)="showForm.set(false)"> ...formulario... </vf-modal>
 *
 * - Siempre centrada en la pantalla, sea cual sea el scroll de la pagina.
 * - Si el contenido es largo, la cabecera queda fija y se desplaza solo el cuerpo; un bloque con la
 *   clase "vf-modal-actions" (botones Guardar/Cancelar) queda pegado abajo, siempre a la vista.
 * - Al abrirse, el foco va al primer campo; Escape la cierra.
 */
@Component({
  selector: 'vf-modal',
  imports: [Icon],
  host: { '(document:keydown.escape)': 'open() && closed.emit()' },
  template: `
    @if (open()) {
      <div
        class="fixed inset-0 z-50 flex animate-fade-in items-center justify-center bg-zinc-950/70 p-3 backdrop-blur-sm sm:p-4"
        (click)="closed.emit()"
      >
        <div
          class="flex max-h-[92vh] w-full animate-pop-in flex-col rounded-2xl border border-zinc-800 bg-zinc-900 shadow-(--shadow-pop)"
          [class]="size() === 'lg' ? 'max-w-3xl' : 'max-w-lg'"
          role="dialog"
          aria-modal="true"
          [attr.aria-label]="title()"
          (click)="$event.stopPropagation()"
        >
          <div
            class="flex shrink-0 items-center justify-between gap-4 border-b border-zinc-800/70 px-5 py-3.5"
          >
            <h2 class="font-display text-base font-semibold text-white">{{ title() }}</h2>
            <button
              type="button"
              class="vf-btn-icon -mr-2 size-8"
              (click)="closed.emit()"
              aria-label="Cerrar"
            >
              <vf-icon name="x" class="size-4" />
            </button>
          </div>
          <div class="vf-modal-body min-h-0 flex-1 overflow-y-auto px-5 pt-5">
            <ng-content />
            <div class="h-5"></div>
          </div>
        </div>
      </div>
    }
  `,
})
export class Modal {
  readonly open = input(false);
  readonly title = input('');
  /** "md" (formularios normales) o "lg" (formularios largos). */
  readonly size = input<'md' | 'lg'>('md');
  readonly closed = output<void>();
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  constructor() {
    effect(() => {
      if (!this.open()) return;
      // Tras pintar el contenido: foco en el primer campo (sin desplazar la pagina de fondo).
      setTimeout(() => {
        const field = this.host.nativeElement.querySelector<HTMLElement>(
          '[autofocus], input:not([type=hidden]):not([disabled]), select:not([disabled]), textarea:not([disabled])',
        );
        field?.focus({ preventScroll: true });
      });
    });
  }
}
