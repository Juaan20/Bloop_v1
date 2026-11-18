import { Component, input } from '@angular/core';

/**
 * Logo de Bloop.
 * - Normal: el logotipo original "bloop" en naranja (public/brand/bloop-logo.png, fondo transparente).
 * - compact = solo el simbolo (barra lateral plegada): una "b" con una burbuja sobre el naranja de la marca.
 */
@Component({
  selector: 'vf-logo',
  template: `
    @if (compact()) {
      <svg
        [attr.width]="size()"
        [attr.height]="size()"
        viewBox="0 0 32 32"
        aria-hidden="true"
        class="shrink-0"
      >
        <defs>
          <linearGradient id="vf-logo-g" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#f39345" />
            <stop offset="1" stop-color="#e06200" />
          </linearGradient>
        </defs>
        <rect width="32" height="32" rx="8" fill="url(#vf-logo-g)" />
        <path d="M11 8v15" stroke="#fff" stroke-width="3" stroke-linecap="round" />
        <circle cx="16.5" cy="18.5" r="5" fill="none" stroke="#fff" stroke-width="3" />
        <circle cx="23" cy="9.5" r="2.2" fill="#fff" fill-opacity=".55" />
      </svg>
    } @else {
      <img
        src="brand/bloop-logo.png"
        alt="Bloop"
        [style.height.px]="size()"
        class="block w-auto select-none"
        draggable="false"
      />
    }
  `,
})
export class Logo {
  readonly size = input(28);
  readonly compact = input(false);
}
