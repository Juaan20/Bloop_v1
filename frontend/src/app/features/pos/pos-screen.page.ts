import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Permission } from '../../core/auth/permissions';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { Icon } from '../../shared/ui/icon';
import { Modal } from '../../shared/ui/modal';
import { Spinner } from '../../shared/ui/spinner';
import { CashReport } from './cash-report';
import { CATEGORY_COLORS, CashMovementType, CashSession, PaymentMethod, PosSale, PosMixer, PosScreen, PosVariant, VipOccupiedTable } from './pos.models';
import { PosService } from './pos.service';
import { DiscountInput, TicketLine, addToTicket, change, changeQuantity, discountAmount, lineKey, subtotal, unitsOf } from './pos-ticket';

/**
 * Pantalla tactil del TPV.
 * Flujo: abrir caja -> tocar productos -> cobrar (efectivo o tarjeta) -> ... -> cerrar caja con arqueo.
 * Las existencias se muestran en cada boton y el backend las vuelve a comprobar al cobrar.
 */
@Component({
  selector: 'vf-pos-screen-page',
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, Modal, Spinner, CashReport, Icon],
  template: `
    <div class="flex h-screen flex-col bg-zinc-950 text-zinc-100 select-none">
      <!-- Barra superior -->
      <header class="flex h-14 shrink-0 items-center justify-between border-b border-zinc-800 bg-zinc-950/90 px-4 backdrop-blur">
        <div class="flex items-center gap-3">
          <a routerLink="/tpv" class="vf-btn-icon" aria-label="Cambiar terminal"><vf-icon name="arrow-left" class="size-5" /></a>
          <p class="font-display font-semibold text-white">{{ screen()?.terminal?.name ?? 'TPV' }}</p>
          <p class="text-sm text-zinc-500">{{ screen()?.terminal?.venueName }}</p>
        </div>
        @if (session(); as s) {
          <div class="flex items-center gap-2">
            <span class="hidden text-sm text-zinc-400 sm:inline">Caja de {{ s.openedByName }} · {{ s.summary.salesCount }} tickets · {{ s.summary.totalSales | currency: 'EUR' }}</span>
            <button type="button" class="vf-btn-secondary" (click)="openSales()">Tickets</button>
            <button type="button" class="vf-btn-secondary" (click)="movementModal.set(true)">Efectivo</button>
            <button type="button" class="vf-btn-danger" (click)="openClose()">Cerrar caja</button>
          </div>
        }
      </header>

      @if (loading()) {
        <vf-spinner />
      } @else if (error()) {
        <p class="m-6 vf-card text-red-400">{{ error() }}</p>
      } @else if (!session()) {
        <!-- Caja cerrada -->
        <div class="flex flex-1 items-center justify-center p-6">
          <form class="vf-card w-full max-w-sm space-y-4 text-center" (ngSubmit)="openSession()">
            <p class="font-display text-2xl font-semibold text-white">Caja cerrada</p>
            <p class="text-sm text-zinc-400">Indica el efectivo con el que empiezas (fondo de cambio).</p>
            <input name="openingFloat" type="number" min="0" step="0.01" class="vf-input text-center text-2xl" [(ngModel)]="openingFloat" />
            <button type="submit" class="vf-btn-primary w-full py-3 text-lg" [disabled]="busy() || openingFloat < 0">Abrir caja</button>
          </form>
        </div>
      } @else {
        <div class="flex min-h-0 flex-1">
          <!-- Productos -->
          <section class="flex min-w-0 flex-1 flex-col">
            <!-- Una pestana por categoria (Refrescos, Cervezas, Ron, Ginebra...): cada una muestra solo lo suyo -->
            <nav class="flex shrink-0 gap-2 overflow-x-auto border-b border-zinc-800 p-3" role="tablist" aria-label="Categorias">
              @if (combinados().length) {
                <button type="button" role="tab" [attr.aria-selected]="activeCategory() === COMBINADOS"
                        class="min-h-12 shrink-0 cursor-pointer rounded-xl px-5 text-base font-semibold whitespace-nowrap text-white transition"
                        [class]="activeCategory() === COMBINADOS ? 'bg-brand-600' : 'bg-zinc-900 text-zinc-300 ring-1 ring-zinc-800 hover:bg-zinc-800'"
                        (click)="activeCategory.set(COMBINADOS)">Combinados</button>
              }
              @for (c of screen()!.categories; track c.id) {
                <button type="button" role="tab" [attr.aria-selected]="activeCategory() === c.id"
                        class="min-h-12 shrink-0 cursor-pointer rounded-xl px-5 text-base font-semibold whitespace-nowrap text-white transition"
                        [class]="activeCategory() === c.id ? colors[c.color] : 'bg-zinc-900 text-zinc-300 ring-1 ring-zinc-800 hover:bg-zinc-800'"
                        (click)="activeCategory.set(c.id)">{{ c.name }}</button>
              }
            </nav>
            <div class="flex-1 overflow-y-auto p-3" role="tabpanel">
              @if (activeCategory() === COMBINADOS) {
                <!-- Todas las copas que se sirven con refresco, agrupadas por tipo de alcohol -->
                @for (g of combinados(); track g.category) {
                  <h2 class="mt-1 mb-2 text-sm font-semibold text-zinc-400">{{ g.category }}</h2>
                  <div class="mb-5 grid auto-rows-min grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-4">
                    @for (item of g.items; track item.variant.id) {
                      <button type="button" class="flex min-h-24 cursor-pointer flex-col justify-between rounded-2xl border border-zinc-800 bg-zinc-900/70 p-4 text-left transition duration-100 hover:border-zinc-600 active:scale-[0.97] disabled:pointer-events-none disabled:opacity-30"
                              [disabled]="soldOut(item.variant)" (click)="add(item.productName, item.variant)">
                        <span class="text-base font-semibold text-white">{{ item.productName }}</span>
                        <span class="mt-2 flex items-end justify-between gap-2">
                          <span class="text-xl font-semibold text-white tabular-nums">{{ item.variant.price | currency: 'EUR' }}</span>
                          <span class="text-xs text-sky-300">{{ item.variant.name }} + refresco</span>
                        </span>
                        @if (item.variant.stock !== null) { <span class="text-[11px]" [class]="left(item.variant) <= 5 ? 'text-amber-300' : 'text-zinc-500'">quedan {{ left(item.variant) }}</span> }
                      </button>
                    }
                  </div>
                }
              } @else if (activeTab(); as c) {
                <div class="grid auto-rows-min grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-4">
                  @for (p of c.products; track p.id) {
                    @if (p.variants.length === 1) {
                      @let v = p.variants[0];
                      <button type="button" class="flex min-h-24 cursor-pointer flex-col justify-between rounded-2xl border border-zinc-800 bg-zinc-900/70 p-4 text-left transition duration-100 hover:border-zinc-600 active:scale-[0.97] active:border-brand-500 disabled:pointer-events-none disabled:opacity-30"
                              [disabled]="soldOut(v)" (click)="add(p.name, v)">
                        <span class="text-base font-semibold text-white">{{ p.name }}</span>
                        <span class="mt-2 flex items-end justify-between gap-2">
                          <span class="text-xl font-semibold text-white tabular-nums">{{ v.price | currency: 'EUR' }}</span>
                          <span class="text-xs" [class]="v.requiresMixer ? 'text-sky-300' : 'text-zinc-500'">{{ v.name }}{{ v.requiresMixer ? ' + refresco' : '' }}</span>
                        </span>
                        @if (v.stock !== null) { <span class="text-[11px]" [class]="left(v) <= 5 ? 'text-amber-300' : 'text-zinc-500'">quedan {{ left(v) }}</span> }
                      </button>
                    } @else {
                      <!-- Varias opciones (Chupito / Copa / Botella): un boton grande por opcion -->
                      <div class="col-span-2 rounded-2xl border border-zinc-800 bg-zinc-900/70 p-3 md:col-span-1">
                        <p class="px-1 pb-2 text-base font-semibold text-white">{{ p.name }}</p>
                        <div class="grid grid-cols-2 gap-2">
                          @for (v of p.variants; track v.id) {
                            <button type="button" class="flex min-h-14 cursor-pointer flex-col items-start justify-center rounded-xl bg-zinc-800 px-3 py-2 text-left transition hover:bg-zinc-700 active:scale-[0.97] disabled:pointer-events-none disabled:opacity-30"
                                    [disabled]="soldOut(v)" (click)="add(p.name, v)">
                              <span class="text-sm" [class]="v.requiresMixer ? 'text-sky-200' : 'text-zinc-200'">{{ v.name }}{{ v.requiresMixer ? ' +' : '' }}</span>
                              <span class="text-lg font-semibold text-white tabular-nums">{{ v.price | currency: 'EUR' }}</span>
                            </button>
                          }
                        </div>
                      </div>
                    }
                  } @empty {
                    <p class="col-span-full p-6 text-center text-zinc-500">Sin productos en esta categoria.</p>
                  }
                </div>
              } @else {
                <p class="p-6 text-center text-zinc-500">Este terminal no tiene productos asignados. Revisa "Que vende este terminal" en Terminales y caja.</p>
              }
            </div>
          </section>

          <!-- Ticket -->
          <aside class="flex w-80 shrink-0 flex-col border-l border-zinc-800 bg-zinc-900/60 lg:w-96" aria-label="Ticket actual">
            @if (isVip()) {
              <!-- Servicio VIP: mesa a la que se carga el pedido -->
              <div class="border-b border-zinc-800 p-3">
                <label class="vf-label" for="vipTable">Mesa</label>
                <select id="vipTable" class="vf-input min-h-11 text-base" [ngModel]="vipReservation()" (ngModelChange)="vipReservation.set($event)">
                  <option value="">Venta sin mesa (cobrar ahora)</option>
                  @for (t of vipTables(); track t.reservationId) {
                    <option [value]="t.reservationId">{{ t.tableName }} · {{ t.contactName }} ({{ t.guests }})</option>
                  }
                </select>
                @if (vipTables().length === 0) { <p class="mt-1 text-xs text-zinc-500">No hay mesas ocupadas ahora mismo.</p> }
              </div>
            }
            <div class="flex-1 overflow-y-auto p-3">
              @for (l of lines(); track key(l)) {
                <div class="flex items-center justify-between gap-2 border-b border-zinc-800 py-2">
                  <div class="min-w-0">
                    <p class="truncate text-sm text-white">{{ l.label }}</p>
                    <p class="text-xs text-zinc-500">{{ l.price | currency: 'EUR' }}</p>
                  </div>
                  <div class="flex items-center gap-2">
                    <button type="button" class="grid size-10 cursor-pointer place-items-center rounded-lg bg-zinc-800 transition hover:bg-zinc-700 active:scale-95" (click)="change(key(l), -1)" aria-label="Quitar una unidad"><span class="block h-0.5 w-3.5 rounded bg-current"></span></button>
                    <span class="w-6 text-center font-semibold tabular-nums">{{ l.quantity }}</span>
                    <button type="button" class="grid size-10 cursor-pointer place-items-center rounded-lg bg-zinc-800 transition hover:bg-zinc-700 active:scale-95 disabled:opacity-30" [disabled]="l.stock !== null && l.quantity >= l.stock" (click)="change(key(l), 1)" aria-label="Anadir una unidad"><vf-icon name="plus" class="size-4" [stroke]="2.25" /></button>
                  </div>
                </div>
              } @empty {
                <p class="p-6 text-center text-sm text-zinc-500">Toca un producto para anadirlo.</p>
              }
            </div>
            <div class="space-y-2 border-t border-zinc-800 p-3">
              @if (discount(); as d) {
                <p class="flex justify-between text-sm text-emerald-300">
                  <span>Descuento {{ d.type === 'PERCENTAGE' ? d.value + ' %' : '' }} · {{ discountReason }}</span>
                  <span>-{{ discountTotal() | currency: 'EUR' }} <button type="button" class="ml-1 cursor-pointer align-middle text-zinc-400 hover:text-white" (click)="clearDiscount()" aria-label="Quitar descuento"><vf-icon name="x" class="size-3.5" /></button></span>
                </p>
              }
              <p class="flex items-baseline justify-between pt-1 font-display text-3xl font-semibold text-white tabular-nums"><span class="text-base font-medium text-zinc-400">Total</span><span>{{ total() | currency: 'EUR' }}</span></p>
              <div class="grid grid-cols-3 gap-2">
                <button type="button" class="vf-btn-secondary" [disabled]="lines().length === 0" (click)="clearTicket()">Vaciar</button>
                <button type="button" class="vf-btn-secondary col-span-2" [disabled]="lines().length === 0" (click)="openDiscount()">Descuento</button>
              </div>
              @if (isVip() && vipReservation()) {
                <button type="button" class="flex min-h-16 w-full cursor-pointer items-center justify-center gap-2 rounded-xl bg-brand-500 text-lg font-semibold text-white transition hover:bg-brand-400 active:scale-[0.98] disabled:pointer-events-none disabled:opacity-30"
                        [disabled]="lines().length === 0 || busy() || !!discount()" (click)="chargeToTable()">
                  <vf-icon name="wine" class="size-5" /> Cargar a la mesa
                </button>
                <p class="text-center text-[11px] text-zinc-500">Lo incluido en su paquete no se cobra; el resto queda pendiente en la cuenta de la mesa y aparece en el panel de servicio.</p>
              }
              <div class="grid grid-cols-2 gap-2" [class.hidden]="isVip() && vipReservation()">
                <button type="button" class="flex min-h-16 cursor-pointer items-center justify-center gap-2 rounded-xl bg-emerald-500 text-lg font-semibold text-zinc-950 transition hover:bg-emerald-400 active:scale-[0.98] disabled:pointer-events-none disabled:opacity-30" [disabled]="lines().length === 0 || busy()" (click)="openPayment('CASH')"><vf-icon name="euro" class="size-5" /> Efectivo</button>
                <button type="button" class="flex min-h-16 cursor-pointer items-center justify-center gap-2 rounded-xl bg-accent-500 text-lg font-semibold text-zinc-950 transition hover:bg-accent-400 active:scale-[0.98] disabled:pointer-events-none disabled:opacity-30" [disabled]="lines().length === 0 || busy()" (click)="openPayment('CARD')"><vf-icon name="card" class="size-5" /> Tarjeta</button>
              </div>
            </div>
          </aside>
        </div>
      }
    </div>

    <!-- Elegir refresco del combinado -->
    <vf-modal [open]="!!mixerFor()" [title]="'Refresco para ' + (mixerFor()?.label ?? '')" (closed)="mixerFor.set(null)">
      <div class="grid grid-cols-2 gap-3">
        @for (m of screen()?.mixers ?? []; track m.variantId) {
          <button type="button" class="flex min-h-20 cursor-pointer flex-col items-start justify-center rounded-2xl border border-zinc-800 bg-zinc-900 px-4 py-3 text-left transition hover:border-sky-400 active:scale-[0.97] disabled:pointer-events-none disabled:opacity-30"
                  [disabled]="m.stock !== null && m.stock <= inTicket(m.variantId)" (click)="addWithMixer(m)">
            <span class="text-base font-semibold text-white">{{ m.name }}</span>
            <span class="text-sm" [class]="m.surcharge > 0 ? 'text-amber-300' : 'text-zinc-500'">{{ m.surcharge > 0 ? '+ ' + (m.surcharge | currency: 'EUR') : 'Sin suplemento' }}</span>
          </button>
        } @empty {
          <p class="col-span-2 text-sm text-zinc-400">No hay refrescos configurados. En el catalogo marca los refrescos como "Es un refresco para combinados".</p>
        }
      </div>
    </vf-modal>

    <!-- Cobro -->
    <vf-modal [open]="!!paymentMethod()" [title]="paymentMethod() === 'CASH' ? 'Cobro en efectivo' : 'Cobro con tarjeta'" (closed)="paymentMethod.set(null)">
      <div class="space-y-4">
        <p class="text-center text-3xl font-bold text-white">{{ total() | currency: 'EUR' }}</p>
        @if (paymentMethod() === 'CASH') {
          <div>
            <label class="vf-label" for="received">Entrega el cliente</label>
            <input id="received" name="received" type="number" min="0" step="0.01" class="vf-input text-center text-2xl" [(ngModel)]="cashReceived" />
          </div>
          <div class="grid grid-cols-4 gap-2">
            @for (amount of quickAmounts(); track amount) {
              <button type="button" class="vf-btn-secondary" (click)="cashReceived = amount">{{ amount | currency: 'EUR' : 'symbol' : '1.0-2' }}</button>
            }
          </div>
          <p class="text-center text-lg" [class]="changeDue() === null ? 'text-red-400' : 'text-emerald-300'">
            {{ changeDue() === null ? 'Falta dinero' : 'Cambio: ' + (changeDue() | currency: 'EUR') }}
          </p>
        } @else {
          <p class="text-center text-sm text-zinc-400">Cobra con el datafono del local y confirma cuando el pago este aceptado. Bloop no guarda datos de la tarjeta.</p>
        }
        <button type="button" class="vf-btn-primary w-full py-3 text-lg" [disabled]="busy() || (paymentMethod() === 'CASH' && changeDue() === null)" (click)="charge()">
          {{ busy() ? 'Cobrando...' : 'Confirmar cobro' }}
        </button>
      </div>
    </vf-modal>

    <!-- Venta completada -->
    <vf-modal [open]="!!lastSale()" title="Venta completada" (closed)="lastSale.set(null)">
      @if (lastSale(); as s) {
        <div class="space-y-3 text-center">
          <p class="font-mono text-sm text-zinc-400">{{ s.reference }}</p>
          <p class="text-3xl font-bold text-white">{{ s.totalAmount | currency: 'EUR' }}</p>
          @if (s.paymentMethod === 'CASH') { <p class="text-2xl text-emerald-300">Cambio: {{ s.changeGiven | currency: 'EUR' }}</p> }
          <button type="button" class="vf-btn-primary w-full py-3" (click)="lastSale.set(null)">Siguiente cliente</button>
        </div>
      }
    </vf-modal>

    <!-- Descuento -->
    <vf-modal [open]="discountModal()" title="Descuento" (closed)="discountModal.set(false)">
      <form class="space-y-4" (ngSubmit)="applyDiscount()">
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="vf-label" for="dType">Tipo</label>
            <select id="dType" name="dType" class="vf-input" [(ngModel)]="discountDraft.type">
              <option value="PERCENTAGE">Porcentaje (%)</option>
              <option value="FIXED_AMOUNT">Importe (€)</option>
            </select>
          </div>
          <div><label class="vf-label" for="dValue">Valor</label><input id="dValue" name="dValue" type="number" min="0.01" step="0.01" class="vf-input" [(ngModel)]="discountDraft.value" /></div>
        </div>
        <div><label class="vf-label" for="dReason">Motivo</label><input id="dReason" name="dReason" class="vf-input" maxlength="255" [(ngModel)]="discountDraft.reason" placeholder="Invitacion, cliente habitual..." /></div>
        @if (!canSupervise) {
          <p class="text-sm text-amber-300">Necesita la autorizacion de un encargado:</p>
          <div class="grid grid-cols-2 gap-4">
            <div><label class="vf-label" for="sEmail">Email del encargado</label><input id="sEmail" name="sEmail" type="email" class="vf-input" autocomplete="off" [(ngModel)]="discountDraft.supervisorEmail" /></div>
            <div><label class="vf-label" for="sPassword">Contrasena</label><input id="sPassword" name="sPassword" type="password" class="vf-input" autocomplete="off" [(ngModel)]="discountDraft.supervisorPassword" /></div>
          </div>
        }
        <button type="submit" class="vf-btn-primary" [disabled]="!discountDraft.value || discountDraft.value <= 0 || !discountDraft.reason.trim()">Aplicar</button>
        <p class="text-xs text-zinc-500">La autorizacion se comprueba al cobrar y queda registrada en la auditoria.</p>
      </form>
    </vf-modal>

    <!-- Movimiento de efectivo -->
    <vf-modal [open]="movementModal()" title="Entrada o salida de efectivo" (closed)="movementModal.set(false)">
      <form class="space-y-4" (ngSubmit)="addMovement()">
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="vf-label" for="mvType">Tipo</label>
            <select id="mvType" name="mvType" class="vf-input" [(ngModel)]="movement.type">
              <option value="CASH_OUT">Salida (retirada, pago)</option>
              <option value="CASH_IN">Entrada (mas cambio)</option>
            </select>
          </div>
          <div><label class="vf-label" for="mvAmount">Importe</label><input id="mvAmount" name="mvAmount" type="number" min="0.01" step="0.01" class="vf-input" [(ngModel)]="movement.amount" /></div>
        </div>
        <div><label class="vf-label" for="mvReason">Motivo</label><input id="mvReason" name="mvReason" class="vf-input" maxlength="255" [(ngModel)]="movement.reason" /></div>
        <button type="submit" class="vf-btn-primary" [disabled]="busy() || !movement.amount || movement.amount <= 0 || !movement.reason.trim()">Registrar</button>
      </form>
    </vf-modal>

    <!-- Tickets de la caja -->
    <vf-modal [open]="salesModal()" title="Tickets de esta caja" (closed)="salesModal.set(false)">
      <div class="max-h-[60vh] space-y-2 overflow-y-auto">
        @for (s of sales(); track s.id) {
          <div class="rounded-lg border border-zinc-800 p-3 text-sm" [class.opacity-50]="s.status === 'VOIDED'">
            <div class="flex justify-between">
              <span class="font-mono text-zinc-400">{{ s.reference }} · {{ s.createdAt | date: 'HH:mm' }}</span>
              <span class="font-semibold text-white">{{ s.totalAmount | currency: 'EUR' }} · {{ s.paymentMethod === 'CASH' ? 'Efectivo' : 'Tarjeta' }}</span>
            </div>
            <p class="text-zinc-400">@for (i of s.items; track $index) { {{ i.quantity }}× {{ i.productName }} ({{ i.variantName }}{{ i.mixerName ? ' + ' + i.mixerName : '' }}) }</p>
            @if (s.status === 'VOIDED') {
              <p class="text-red-300">Anulada por {{ s.voidedByName }}: {{ s.voidReason }}</p>
            } @else if (canSupervise) {
              <button type="button" class="vf-btn-ghost mt-1 text-xs" (click)="voidTarget.set(s)">Anular</button>
            }
          </div>
        } @empty {
          <p class="text-sm text-zinc-500">Aun no hay ventas.</p>
        }
      </div>
    </vf-modal>

    <!-- Anular -->
    <vf-modal [open]="!!voidTarget()" [title]="'Anular ' + (voidTarget()?.reference ?? '')" (closed)="voidTarget.set(null)">
      <form class="space-y-4" (ngSubmit)="voidSale()">
        <p class="text-sm text-zinc-400">Las unidades vuelven al almacen y el importe deja de contar en la caja. Devuelve el dinero al cliente.</p>
        <div><label class="vf-label" for="voidReason">Motivo</label><input id="voidReason" name="voidReason" class="vf-input" maxlength="255" [(ngModel)]="voidReason" /></div>
        <button type="submit" class="vf-btn-danger" [disabled]="busy() || !voidReason.trim()">Anular venta</button>
      </form>
    </vf-modal>

    <!-- Cierre -->
    <vf-modal [open]="closeModal()" [title]="closedSession() ? 'Caja cerrada' : 'Cerrar caja'" (closed)="finishClose()">
      @if (closedSession(); as s) {
        <vf-cash-report [session]="s" />
        <button type="button" class="vf-btn-primary mt-4 w-full" (click)="finishClose()">Aceptar</button>
      } @else if (session(); as s) {
        <form class="space-y-4" (ngSubmit)="closeSession()">
          <p class="text-sm text-zinc-400">Cuenta el efectivo del cajon. El sistema lo comparara con lo esperado.</p>
          <div><label class="vf-label" for="counted">Efectivo contado</label><input id="counted" name="counted" type="number" min="0" step="0.01" class="vf-input text-center text-2xl" [(ngModel)]="countedCash" /></div>
          <div><label class="vf-label" for="closeNotes">Notas (opcional)</label><input id="closeNotes" name="closeNotes" class="vf-input" maxlength="500" [(ngModel)]="closeNotes" /></div>
          <button type="submit" class="vf-btn-danger w-full py-3" [disabled]="busy() || countedCash === null || countedCash < 0">Cerrar caja ({{ s.summary.salesCount }} tickets)</button>
        </form>
      }
    </vf-modal>
  `,
})
export class PosScreenPage {
  private readonly posService = inject(PosService);
  private readonly toast = inject(ToastService);

  readonly terminalId = input.required<string>();

  protected readonly canSupervise = inject(AuthService).hasPermission(Permission.POS_SUPERVISE);
  protected readonly colors = CATEGORY_COLORS;
  protected readonly screen = signal<PosScreen | null>(null);
  protected readonly session = computed<CashSession | null>(() => this.screen()?.session ?? null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly activeCategory = signal<string | null>(null);
  protected readonly lines = signal<TicketLine[]>([]);
  protected readonly discount = signal<DiscountInput>(null);
  protected readonly paymentMethod = signal<PaymentMethod | null>(null);
  protected readonly lastSale = signal<PosSale | null>(null);
  protected readonly sales = signal<PosSale[]>([]);
  protected readonly voidTarget = signal<PosSale | null>(null);
  protected readonly discountModal = signal(false);
  protected readonly movementModal = signal(false);
  protected readonly salesModal = signal(false);
  protected readonly closeModal = signal(false);
  protected readonly closedSession = signal<CashSession | null>(null);

  protected openingFloat = 100;
  protected cashReceived: number | null = null;
  protected discountReason = '';
  protected discountDraft = this.emptyDiscount();
  protected movement: { type: CashMovementType; amount: number | null; reason: string } = { type: 'CASH_OUT', amount: null, reason: '' };
  protected voidReason = '';
  protected countedCash: number | null = null;
  protected closeNotes = '';
  /** Se mantiene mientras el ticket no cambie: un doble toque en "Confirmar cobro" no cobra dos veces. */
  private idempotencyKey = crypto.randomUUID();
  private supervisor: { email: string | null; password: string | null } = { email: null, password: null };

  /** Terminal de servicio VIP: puede cargar el ticket a una mesa ocupada. */
  protected readonly isVip = computed(() => this.screen()?.terminal.type === 'VIP_SERVICE');
  protected readonly vipTables = signal<VipOccupiedTable[]>([]);
  protected readonly vipReservation = signal('');

  /** Pestana virtual con todas las copas que se sirven con refresco. */
  protected readonly COMBINADOS = '__combinados__';
  /** Copa a la que se esta eligiendo refresco. */
  protected readonly mixerFor = signal<{ label: string; variant: PosVariant } | null>(null);

  protected readonly combinados = computed(() =>
    (this.screen()?.categories ?? [])
      .map((c) => ({
        category: c.name,
        items: c.products.flatMap((p) => p.variants.filter((v) => v.requiresMixer).map((v) => ({ productName: p.name, variant: v }))),
      }))
      .filter((g) => g.items.length > 0),
  );

  protected readonly activeTab = computed(() => {
    const categories = this.screen()?.categories ?? [];
    return categories.find((c) => c.id === this.activeCategory()) ?? categories[0] ?? null;
  });

  protected key(l: TicketLine): string {
    return lineKey(l);
  }

  protected left(v: PosVariant): number {
    return (v.stock ?? 0) - this.inTicket(v.id);
  }

  /** Como en cualquier TPV de hosteleria, no se bloquea la venta por stock (el recuento lo corrige). */
  protected soldOut(_v: PosVariant): boolean {
    return false;
  }

  protected chargeToTable(): void {
    const reservationId = this.vipReservation();
    if (!reservationId) return;
    this.busy.set(true);
    if (this.lines().some((l) => l.mixerVariantId)) {
      this.toast.error('Las copas con refresco se cobran en barra, no se cargan a la mesa');
      this.busy.set(false);
      return;
    }
    const lines = this.lines().map((l) => ({ variantId: l.variantId, quantity: l.quantity }));
    this.posService.vipCharge(this.terminalId(), reservationId, lines, this.idempotencyKey).subscribe({
      next: (order) => {
        this.busy.set(false);
        this.toast.success(`Cargado a ${order.tableName}: ${order.extraTotal > 0 ? order.extraTotal.toFixed(2) + ' EUR pendientes' : 'incluido en su paquete'}`);
        this.lines.set([]);
        this.idempotencyKey = crypto.randomUUID();
      },
      error: (err) => {
        this.busy.set(false);
        this.toast.error(errorMessage(err));
        this.idempotencyKey = crypto.randomUUID();
      },
    });
  }

  protected readonly subtotal = computed(() => subtotal(this.lines()));
  protected readonly discountTotal = computed(() => discountAmount(this.subtotal(), this.discount()));
  protected readonly total = computed(() => Math.round((this.subtotal() - this.discountTotal()) * 100) / 100);

  constructor() {
    effect(() => this.load(this.terminalId()));
  }

  /** Unidades de una variante ya en el ticket (tambien como refresco de un combinado). */
  protected inTicket(variantId: string): number {
    return unitsOf(this.lines(), variantId);
  }

  protected add(productName: string, v: PosVariant): void {
    if (v.requiresMixer) {
      this.mixerFor.set({ label: `${productName} (${v.name})`, variant: v });
      return;
    }
    this.lines.update((lines) => addToTicket(lines, { variantId: v.id, label: `${productName} (${v.name})`, price: v.price, stock: null }));
    this.ticketChanged();
  }

  /** Copa + refresco: el precio suma el suplemento del refresco (el backend lo recalcula). */
  protected addWithMixer(m: PosMixer): void {
    const target = this.mixerFor();
    if (!target) return;
    const stock = target.variant.stock === null ? m.stock : m.stock === null ? target.variant.stock : Math.min(target.variant.stock, m.stock);
    this.lines.update((lines) =>
      addToTicket(lines, {
        variantId: target.variant.id,
        mixerVariantId: m.variantId,
        label: `${target.label} + ${m.name}`,
        price: Math.round((target.variant.price + m.surcharge) * 100) / 100,
        stock,
      }),
    );
    this.mixerFor.set(null);
    this.ticketChanged();
  }

  protected change(key: string, delta: number): void {
    this.lines.update((lines) => changeQuantity(lines, key, delta));
    this.ticketChanged();
  }

  protected clearTicket(): void {
    this.lines.set([]);
    this.clearDiscount();
  }

  protected openDiscount(): void {
    this.discountDraft = this.emptyDiscount();
    this.discountModal.set(true);
  }

  protected applyDiscount(): void {
    this.discount.set({ type: this.discountDraft.type, value: Number(this.discountDraft.value) });
    this.discountReason = this.discountDraft.reason.trim();
    this.supervisor = { email: this.discountDraft.supervisorEmail || null, password: this.discountDraft.supervisorPassword || null };
    this.discountModal.set(false);
    this.ticketChanged();
  }

  protected clearDiscount(): void {
    this.discount.set(null);
    this.discountReason = '';
    this.supervisor = { email: null, password: null };
    this.ticketChanged();
  }

  protected quickAmounts(): number[] {
    const total = this.total();
    const options = [total, ...[5, 10, 20, 50, 100].filter((n) => n > total)].slice(0, 4);
    return [...new Set(options)];
  }

  protected changeDue(): number | null {
    return change(this.total(), this.cashReceived === null ? null : Number(this.cashReceived));
  }

  protected openPayment(method: PaymentMethod): void {
    this.cashReceived = method === 'CASH' ? this.total() : null;
    this.paymentMethod.set(method);
  }

  protected charge(): void {
    const method = this.paymentMethod();
    if (!method) return;
    const d = this.discount();
    this.busy.set(true);
    this.posService
      .sell(
        this.terminalId(),
        {
          items: this.lines().map((l) => ({ variantId: l.variantId, quantity: l.quantity, mixerVariantId: l.mixerVariantId ?? null })),
          paymentMethod: method,
          cashReceived: method === 'CASH' ? Number(this.cashReceived) : null,
          discount: d
            ? { type: d.type, value: d.value, reason: this.discountReason, supervisorEmail: this.supervisor.email, supervisorPassword: this.supervisor.password }
            : null,
        },
        this.idempotencyKey,
      )
      .subscribe({
        next: (sale) => {
          this.busy.set(false);
          this.paymentMethod.set(null);
          this.lastSale.set(sale);
          this.lines.set([]);
          this.discount.set(null);
          this.discountReason = '';
          this.supervisor = { email: null, password: null };
          this.idempotencyKey = crypto.randomUUID();
          this.load(this.terminalId());
        },
        error: (err) => {
          this.busy.set(false);
          this.toast.error(errorMessage(err));
          this.idempotencyKey = crypto.randomUUID();
          this.load(this.terminalId()); // refresca existencias
        },
      });
  }

  protected openSession(): void {
    this.busy.set(true);
    this.posService.openSession(this.terminalId(), Number(this.openingFloat)).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.success('Caja abierta');
        this.load(this.terminalId());
      },
      error: (err) => this.fail(err),
    });
  }

  protected addMovement(): void {
    const s = this.session();
    if (!s || !this.movement.amount) return;
    this.busy.set(true);
    this.posService.addMovement(s.id, this.movement.type, Number(this.movement.amount), this.movement.reason.trim()).subscribe({
      next: () => {
        this.busy.set(false);
        this.movementModal.set(false);
        this.movement = { type: 'CASH_OUT', amount: null, reason: '' };
        this.toast.success('Movimiento registrado');
        this.load(this.terminalId());
      },
      error: (err) => this.fail(err),
    });
  }

  protected openSales(): void {
    const s = this.session();
    if (!s) return;
    this.posService.sessionSales(s.id).subscribe({
      next: (sales) => {
        this.sales.set(sales);
        this.salesModal.set(true);
      },
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }

  protected voidSale(): void {
    const target = this.voidTarget();
    if (!target) return;
    this.busy.set(true);
    this.posService.voidSale(target.id, this.voidReason.trim()).subscribe({
      next: (voided) => {
        this.busy.set(false);
        this.sales.update((list) => list.map((s) => (s.id === voided.id ? voided : s)));
        this.voidTarget.set(null);
        this.voidReason = '';
        this.toast.success('Venta anulada');
        this.load(this.terminalId());
      },
      error: (err) => this.fail(err),
    });
  }

  protected openClose(): void {
    this.countedCash = null;
    this.closeNotes = '';
    this.closedSession.set(null);
    this.closeModal.set(true);
  }

  protected closeSession(): void {
    const s = this.session();
    if (!s || this.countedCash === null) return;
    this.busy.set(true);
    this.posService.closeSession(s.id, Number(this.countedCash), this.closeNotes.trim() || null).subscribe({
      next: (closed) => {
        this.busy.set(false);
        this.closedSession.set(closed);
      },
      error: (err) => this.fail(err),
    });
  }

  protected finishClose(): void {
    this.closeModal.set(false);
    if (this.closedSession()) {
      this.closedSession.set(null);
      this.lines.set([]);
      this.load(this.terminalId());
    }
  }

  private ticketChanged(): void {
    this.idempotencyKey = crypto.randomUUID();
  }

  private emptyDiscount() {
    return { type: 'PERCENTAGE' as 'PERCENTAGE' | 'FIXED_AMOUNT', value: 10 as number | null, reason: '', supervisorEmail: '', supervisorPassword: '' };
  }

  private fail(err: unknown): void {
    this.busy.set(false);
    this.toast.error(errorMessage(err));
  }

  private load(terminalId: string): void {
    this.posService.screen(terminalId).subscribe({
      next: (screen) => {
        this.screen.set(screen);
        if (!this.activeCategory() && screen.categories.length) {
          this.activeCategory.set(screen.categories[0].id);
        }
        if (screen.terminal.type === 'VIP_SERVICE') {
          this.posService.vipTables(terminalId).subscribe({ next: (t) => this.vipTables.set(t), error: () => this.vipTables.set([]) });
        }
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
