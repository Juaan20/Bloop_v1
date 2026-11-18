import { DecimalPipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { Page } from '../../core/models/api.models';
import { Icon } from './icon';

/**
 * Pie de tabla con el rango mostrado y botones Anterior/Siguiente.
 * Uso: <vf-pagination [page]="page" (pageChange)="goToPage($event)" />
 */
@Component({
  selector: 'vf-pagination',
  imports: [DecimalPipe, Icon],
  template: `
    @let p = page();
    @if (p.totalPages > 1) {
      <nav class="mt-4 flex flex-wrap items-center justify-between gap-3 text-xs text-zinc-500" aria-label="Paginacion">
        <p class="tabular-nums">
          {{ p.page * p.size + 1 | number }}–{{ p.page * p.size + p.content.length | number }}
          de {{ p.totalElements | number }}
        </p>
        <div class="flex items-center gap-1.5">
          <button type="button" class="vf-btn-secondary vf-btn-sm" [disabled]="p.page === 0" (click)="pageChange.emit(p.page - 1)">
            <vf-icon name="chevron-left" class="size-3.5" /> Anterior
          </button>
          <span class="px-2 text-zinc-400 tabular-nums">{{ p.page + 1 }} / {{ p.totalPages }}</span>
          <button type="button" class="vf-btn-secondary vf-btn-sm" [disabled]="p.page + 1 >= p.totalPages" (click)="pageChange.emit(p.page + 1)">
            Siguiente <vf-icon name="chevron-right" class="size-3.5" />
          </button>
        </div>
      </nav>
    }
  `,
})
export class Pagination {
  readonly page = input.required<Page<unknown>>();
  readonly pageChange = output<number>();
}
