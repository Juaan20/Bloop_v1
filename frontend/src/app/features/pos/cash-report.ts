import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { CashSession } from './pos.models';

/** Informe de una caja (informe Z): ventas por metodo, efectivo esperado, arqueo y mas vendidos. */
@Component({
  selector: 'vf-cash-report',
  imports: [CurrencyPipe, DatePipe],
  template: `
    @let s = session();
    <div class="space-y-4 text-sm">
      <p class="text-zinc-400">
        Abierta {{ s.openedAt | date: 'd MMM yyyy HH:mm' }} por {{ s.openedByName }}
        @if (s.closedAt) { · cerrada {{ s.closedAt | date: 'd MMM HH:mm' }} por {{ s.closedByName }} }
      </p>
      <dl class="grid grid-cols-2 gap-x-6 gap-y-1">
        <dt class="text-zinc-400">Ventas en efectivo</dt><dd class="text-right text-white">{{ s.summary.cashSales | currency: 'EUR' }}</dd>
        <dt class="text-zinc-400">Ventas con tarjeta</dt><dd class="text-right text-white">{{ s.summary.cardSales | currency: 'EUR' }}</dd>
        <dt class="font-semibold text-zinc-200">Total vendido</dt><dd class="text-right font-semibold text-white">{{ s.summary.totalSales | currency: 'EUR' }}</dd>
        <dt class="text-zinc-400">Descuentos</dt><dd class="text-right text-zinc-300">{{ s.summary.discounts | currency: 'EUR' }}</dd>
        <dt class="text-zinc-400">Tickets / anulados</dt><dd class="text-right text-zinc-300">{{ s.summary.salesCount }} / {{ s.summary.voidedCount }}</dd>
      </dl>
      <dl class="grid grid-cols-2 gap-x-6 gap-y-1 border-t border-zinc-800 pt-3">
        <dt class="text-zinc-400">Fondo inicial</dt><dd class="text-right text-zinc-300">{{ s.openingFloat | currency: 'EUR' }}</dd>
        <dt class="text-zinc-400">+ Ventas en efectivo</dt><dd class="text-right text-zinc-300">{{ s.summary.cashSales | currency: 'EUR' }}</dd>
        <dt class="text-zinc-400">+ Entradas de efectivo</dt><dd class="text-right text-zinc-300">{{ s.summary.cashIn | currency: 'EUR' }}</dd>
        <dt class="text-zinc-400">- Salidas de efectivo</dt><dd class="text-right text-zinc-300">{{ s.summary.cashOut | currency: 'EUR' }}</dd>
        <dt class="font-semibold text-zinc-200">Efectivo esperado</dt><dd class="text-right font-semibold text-white">{{ s.summary.expectedCash | currency: 'EUR' }}</dd>
        @if (s.countedCash !== null) {
          <dt class="text-zinc-400">Efectivo contado</dt><dd class="text-right text-white">{{ s.countedCash | currency: 'EUR' }}</dd>
          <dt class="font-semibold text-zinc-200">Diferencia</dt>
          <dd class="text-right font-semibold" [class]="(s.difference ?? 0) < 0 ? 'text-red-400' : (s.difference ?? 0) > 0 ? 'text-amber-300' : 'text-emerald-300'">
            {{ s.difference | currency: 'EUR' }}
          </dd>
        }
      </dl>
      @if (s.closingNotes) { <p class="text-zinc-400">Notas: {{ s.closingNotes }}</p> }
      @if (s.summary.movements.length) {
        <div class="border-t border-zinc-800 pt-3">
          <p class="mb-1 font-semibold text-zinc-200">Movimientos de efectivo</p>
          @for (m of s.summary.movements; track m.id) {
            <p class="flex justify-between text-zinc-400"><span>{{ m.type === 'CASH_IN' ? '+' : '-' }} {{ m.reason }} · {{ m.userName }}</span><span>{{ m.amount | currency: 'EUR' }}</span></p>
          }
        </div>
      }
      @if (s.summary.topProducts.length) {
        <div class="border-t border-zinc-800 pt-3">
          <p class="mb-1 font-semibold text-zinc-200">Mas vendidos</p>
          @for (p of s.summary.topProducts; track p.productName + p.variantName) {
            <p class="flex justify-between text-zinc-400"><span>{{ p.quantity }} × {{ p.productName }} ({{ p.variantName }})</span><span>{{ p.total | currency: 'EUR' }}</span></p>
          }
        </div>
      }
    </div>
  `,
})
export class CashReport {
  readonly session = input.required<CashSession>();
}
