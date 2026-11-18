import { Component, inject } from '@angular/core';
import { ToastService } from '../../core/services/toast.service';
import { Icon } from './icon';

/** Pinta los avisos de ToastService en la esquina inferior derecha. */
@Component({
  selector: 'vf-toast-container',
  imports: [Icon],
  template: `
    <div class="pointer-events-none fixed right-4 bottom-4 z-[60] flex w-[min(22rem,calc(100vw-2rem))] flex-col gap-2" aria-live="polite">
      @for (toast of toastService.toasts(); track toast.id) {
        <div class="pointer-events-auto flex animate-pop-in items-start gap-3 rounded-xl border border-zinc-800 bg-zinc-900/95 px-4 py-3 text-sm text-zinc-100 shadow-(--shadow-pop) backdrop-blur"
             [attr.role]="toast.kind === 'error' ? 'alert' : 'status'">
          <span class="mt-0.5 grid size-5 shrink-0 place-items-center rounded-full" [class]="styles[toast.kind]">
            <vf-icon [name]="toast.kind === 'success' ? 'check' : toast.kind === 'error' ? 'alert' : 'bell'" class="size-3" [stroke]="2.5" />
          </span>
          <span class="flex-1 leading-relaxed">{{ toast.message }}</span>
          <button type="button" class="-mr-1 rounded p-0.5 text-zinc-500 transition hover:text-white" (click)="toastService.dismiss(toast.id)" aria-label="Cerrar aviso">
            <vf-icon name="x" class="size-3.5" />
          </button>
        </div>
      }
    </div>
  `,
})
export class ToastContainer {
  protected readonly toastService = inject(ToastService);
  protected readonly styles = {
    success: 'bg-emerald-500/15 text-emerald-300',
    error: 'bg-red-500/15 text-red-300',
    info: 'bg-brand-500/15 text-brand-300',
  };
}
