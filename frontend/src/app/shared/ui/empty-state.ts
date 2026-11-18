import { Component, input } from '@angular/core';
import { Icon, IconName } from './icon';

/** Mensaje para listados sin resultados. Acepta un boton de accion proyectado. */
@Component({
  selector: 'vf-empty-state',
  imports: [Icon],
  template: `
    <div class="flex animate-fade-in flex-col items-center justify-center rounded-(--radius-card) border border-dashed border-zinc-800 bg-zinc-900/20 px-6 py-14 text-center">
      <span class="mb-4 grid size-11 place-items-center rounded-xl border border-zinc-800 bg-zinc-900 text-zinc-400 shadow-(--shadow-card)">
        <vf-icon [name]="icon()" class="size-5" />
      </span>
      <p class="font-medium text-zinc-100">{{ title() }}</p>
      @if (description()) {
        <p class="mt-1 max-w-md text-sm text-pretty text-zinc-500">{{ description() }}</p>
      }
      <div class="mt-5 empty:hidden"><ng-content /></div>
    </div>
  `,
})
export class EmptyState {
  readonly title = input.required<string>();
  readonly description = input<string>();
  readonly icon = input<IconName>('inbox');
}
