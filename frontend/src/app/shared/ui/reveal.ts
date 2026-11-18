import { Directive, ElementRef, afterNextRender, inject, input } from '@angular/core';

/**
 * Formulario o panel que aparece dentro de la pagina (no en un modal): al mostrarse se desplaza
 * hasta quedar visible y pone el foco en su primer campo. Asi, al pulsar "Editar" al final de una
 * lista larga, el usuario no tiene que buscar donde se ha abierto el formulario.
 *
 * Uso: <form vfReveal> ... </form>   ([vfRevealFocus]="false" para no mover el foco)
 * Respeta prefers-reduced-motion (desplazamiento instantaneo).
 */
@Directive({ selector: '[vfReveal]' })
export class Reveal {
  readonly vfRevealFocus = input(true);
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);

  constructor() {
    afterNextRender(() => revealElement(this.el.nativeElement, this.vfRevealFocus()));
  }
}

/** Desplaza hasta el elemento (si no se ve entero) y enfoca su primer campo editable. */
export function revealElement(element: HTMLElement, focus = true): void {
  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  const rect = element.getBoundingClientRect();
  const visible = rect.top >= 64 && rect.bottom <= window.innerHeight;
  if (!visible) {
    element.scrollIntoView({
      behavior: reduced ? 'auto' : 'smooth',
      block: rect.height > window.innerHeight ? 'start' : 'center',
    });
  }
  if (focus) {
    const field = element.querySelector<HTMLElement>(
      '[autofocus], input:not([type=hidden]):not([disabled]), select:not([disabled]), textarea:not([disabled])',
    );
    field?.focus({ preventScroll: true });
  }
}
