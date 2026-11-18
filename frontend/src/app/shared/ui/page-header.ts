import { Component, input } from '@angular/core';

/** Cabecera de pagina del backoffice. Los botones de accion se proyectan con <ng-content>. */
@Component({
  selector: 'vf-page-header',
  template: `
    <header class="mb-8 flex flex-wrap items-end justify-between gap-4">
      <div class="min-w-0">
        <h1 class="font-display text-[22px] font-semibold tracking-tight text-white">{{ title() }}</h1>
        @if (subtitle()) {
          <p class="mt-1 max-w-2xl text-sm text-zinc-400">{{ subtitle() }}</p>
        }
      </div>
      <div class="flex flex-wrap items-center gap-2"><ng-content /></div>
    </header>
  `,
})
export class PageHeader {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
}
