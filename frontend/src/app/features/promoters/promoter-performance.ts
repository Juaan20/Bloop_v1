import { CurrencyPipe } from '@angular/common';
import { Component, inject, input } from '@angular/core';
import { ToastService } from '../../core/services/toast.service';
import { StatusBadge } from '../../shared/ui/status-badge';
import { PromoterDetail } from './promoter.models';

/**
 * Rendimiento de un promotor: resumen, eventos con enlace de venta y objetivo, y sus codigos.
 * Lo usan la ficha del promotor (admin) y el panel del propio promotor.
 */
@Component({
  selector: 'vf-promoter-performance',
  imports: [CurrencyPipe, StatusBadge],
  template: `
    @let d = detail();
    <div class="mb-6 grid gap-4 sm:grid-cols-4">
      <div class="vf-card"><p class="text-xs text-zinc-500">Entradas vendidas</p><p class="text-2xl font-semibold text-white">{{ d.promoter.ticketsSold }}</p></div>
      <div class="vf-card"><p class="text-xs text-zinc-500">Pedidos</p><p class="text-2xl font-semibold text-white">{{ d.promoter.orders }}</p></div>
      <div class="vf-card"><p class="text-xs text-zinc-500">Ventas</p><p class="text-2xl font-semibold text-white">{{ d.promoter.revenue | currency: 'EUR' }}</p></div>
      <div class="vf-card"><p class="text-xs text-zinc-500">Comision</p><p class="text-2xl font-semibold text-emerald-300">{{ d.promoter.commission | currency: 'EUR' }}</p></div>
    </div>

    <div class="vf-card mb-6 overflow-x-auto">
      <h2 class="font-display font-semibold text-white">Eventos</h2>
      <p class="mt-1 text-sm text-zinc-500">
        Comision: {{ d.promoter.commissionType === 'PERCENTAGE' ? d.promoter.commissionValue + ' % del importe pagado' : (d.promoter.commissionValue | currency: 'EUR') + ' por entrada' }}
        · Solo cuentan los pedidos pagados.
      </p>
      @if (d.events.length === 0) {
        <p class="mt-4 text-sm text-zinc-500">Todavia no hay eventos asignados.</p>
      } @else {
        <table class="vf-table mt-3">
          <thead><tr><th>Evento</th><th class="text-right">Entradas</th><th>Objetivo</th><th class="text-right">Ventas</th><th class="text-right">Comision</th><th>Enlace de venta</th></tr></thead>
          <tbody>
            @for (e of d.events; track e.eventId) {
              <tr>
                <td>
                  <p class="text-zinc-200">{{ e.eventName }}</p>
                  <div class="mt-1 flex gap-1"><vf-status-badge [status]="e.eventStatus" />
                    @if (!e.assigned) { <span class="text-xs text-zinc-500">ya no asignado</span> }</div>
                </td>
                <td class="text-right text-zinc-300">{{ e.ticketsSold }}</td>
                <td class="min-w-36">
                  @if (e.targetTickets) {
                    <div class="h-2 overflow-hidden rounded-full bg-zinc-800">
                      <div class="h-full bg-gradient-to-r from-brand-500 to-fuchsia-500" [style.width.%]="e.targetProgress! > 100 ? 100 : e.targetProgress"></div>
                    </div>
                    <p class="mt-1 text-xs text-zinc-400">{{ e.ticketsSold }} / {{ e.targetTickets }} ({{ e.targetProgress }} %)</p>
                  } @else {
                    <span class="text-xs text-zinc-500">Sin objetivo</span>
                  }
                </td>
                <td class="text-right text-white">{{ e.revenue | currency: 'EUR' }}</td>
                <td class="text-right text-emerald-300">{{ e.commission | currency: 'EUR' }}</td>
                <td>
                  @if (e.assigned) {
                    <button type="button" class="vf-btn-ghost text-xs" (click)="copy(e.salesLink)" [title]="e.salesLink">Copiar enlace</button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>

    <div class="vf-card">
      <h2 class="font-display font-semibold text-white">Codigos promocionales</h2>
      @if (d.promoCodes.length === 0) {
        <p class="mt-3 text-sm text-zinc-500">Sin codigos propios.</p>
      } @else {
        <ul class="mt-3 space-y-2 text-sm">
          @for (c of d.promoCodes; track c.id) {
            <li class="flex flex-wrap items-center justify-between gap-2 text-zinc-300">
              <span><span class="font-mono font-semibold text-white">{{ c.code }}</span>
                · {{ c.discountType === 'PERCENTAGE' ? c.discountValue + ' %' : (c.discountValue | currency: 'EUR') }}
                {{ c.eventName ? '· ' + c.eventName : '· todos los eventos' }}</span>
              <span class="text-zinc-500">{{ c.timesUsed }}{{ c.maxUses ? ' / ' + c.maxUses : '' }} usos {{ c.active ? '' : '· inactivo' }}</span>
            </li>
          }
        </ul>
      }
    </div>
  `,
})
export class PromoterPerformance {
  private readonly toast = inject(ToastService);
  readonly detail = input.required<PromoterDetail>();

  protected copy(link: string): void {
    navigator.clipboard.writeText(link).then(
      () => this.toast.success('Enlace copiado'),
      () => this.toast.info(link),
    );
  }
}
