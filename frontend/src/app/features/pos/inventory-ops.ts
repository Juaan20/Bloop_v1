import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { StockItem, Warehouse, formatStock } from './pos.models';

interface Supplier {
  id: string;
  name: string;
  contactName: string | null;
  phone: string | null;
  email: string | null;
  active: boolean;
}
type Unit = { stockUnit: 'UNIT' | 'ML'; unitSizeMl: number | null };
interface ControlRow extends Unit {
  variantId: string;
  productName: string;
  variantName: string;
  opening: number;
  received: number;
  sent: number;
  sold: number;
  merma: number;
  closing: number;
  mermaValue: number;
}
interface Control {
  from: string;
  to: string;
  rows: ControlRow[];
  totalMermaValue: number;
}
interface Suggested extends Unit {
  variantId: string;
  productName: string;
  variantName: string;
  quantity: number;
  minQuantity: number;
  missing: number;
  supplierName: string | null;
}
interface Container {
  supplierId: string;
  supplierName: string;
  variantId: string;
  productName: string;
  variantName: string;
  received: number;
  returned: number;
  pending: number;
}
type Tab = 'entry' | 'transfer' | 'count' | 'control' | 'order' | 'containers' | 'suppliers';

/**
 * Operativa de inventario como en una discoteca: proveedor (entrada con albaran) -> almacen ->
 * traspaso a la barra -> ventas (descuentan, nunca bloquean) -> recuento al cerrar -> control de
 * merma, pedido sugerido y envases vacios que se lleva el proveedor.
 * Las botellas se escriben en botellas (con decimales) y se guardan en ml.
 */
@Component({
  selector: 'vf-inventory-ops',
  imports: [CurrencyPipe, DatePipe, FormsModule],
  template: `
    <section class="vf-card mb-8">
      <nav class="mb-4 flex flex-wrap gap-1" role="tablist" aria-label="Operaciones de inventario">
        @for (t of tabs; track t.key) {
          <button
            type="button"
            role="tab"
            [attr.aria-selected]="tab() === t.key"
            class="h-9 cursor-pointer rounded-lg px-3 text-sm transition"
            [class]="tab() === t.key ? 'bg-zinc-800 text-white' : 'text-zinc-400 hover:text-white'"
            (click)="open(t.key)"
          >
            {{ t.label }}
          </button>
        }
      </nav>
      @switch (tab()) {
        @case ('entry') {
          <form class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3" (ngSubmit)="saveEntry()">
            <div>
              <label class="vf-label" for="eSup">Proveedor</label>
              <select id="eSup" name="eSup" class="vf-input" [(ngModel)]="entry.supplierId">
                <option value="">Sin proveedor</option>
                @for (s of activeSuppliers(); track s.id) {
                  <option [value]="s.id">{{ s.name }}</option>
                }
              </select>
            </div>
            <div>
              <label class="vf-label" for="eProd">Producto</label>
              <select id="eProd" name="eProd" class="vf-input" [(ngModel)]="entry.variantId">
                @for (i of stock(); track i.variantId) {
                  <option [value]="i.variantId">{{ i.productName }} · {{ i.variantName }}</option>
                }
              </select>
            </div>
            <div>
              <label class="vf-label" for="eDoc">N.º de albaran</label
              ><input
                id="eDoc"
                name="eDoc"
                class="vf-input"
                maxlength="60"
                [(ngModel)]="entry.documentRef"
              />
            </div>
            <div>
              <label class="vf-label" for="eBox">Cajas</label
              ><input
                id="eBox"
                name="eBox"
                type="number"
                min="0"
                class="vf-input"
                [(ngModel)]="entry.boxes"
              />
            </div>
            <div>
              <label class="vf-label" for="ePer">{{ word(entry.variantId) }} por caja</label
              ><input
                id="ePer"
                name="ePer"
                type="number"
                min="1"
                class="vf-input"
                [(ngModel)]="entry.perBox"
              />
            </div>
            <div>
              <label class="vf-label" for="eLoose">{{ word(entry.variantId) }} sueltas</label
              ><input
                id="eLoose"
                name="eLoose"
                type="number"
                min="0"
                class="vf-input"
                [(ngModel)]="entry.loose"
              />
            </div>
            <div>
              <label class="vf-label" for="eCost"
                >Coste por {{ word(entry.variantId, true) }} (EUR)</label
              ><input
                id="eCost"
                name="eCost"
                type="number"
                min="0"
                step="0.01"
                class="vf-input"
                [(ngModel)]="entry.unitCost"
              />
            </div>
            <p class="self-end text-sm text-zinc-400 sm:col-span-2">
              Entran <b class="text-white">{{ entryUnits() }}</b>
              {{ word(entry.variantId).toLowerCase() }} en {{ warehouse()?.name }}.
            </p>
            <div class="sm:col-span-2 lg:col-span-3">
              <button
                type="submit"
                class="vf-btn-primary"
                [disabled]="busy() || !entry.variantId || entryUnits() <= 0"
              >
                Registrar entrada
              </button>
            </div>
          </form>
        }
        @case ('transfer') {
          <form class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4" (ngSubmit)="saveTransfer()">
            <div>
              <label class="vf-label" for="tFrom">Desde</label
              ><select id="tFrom" name="tFrom" class="vf-input" [(ngModel)]="transfer.from">
                @for (w of warehouses(); track w.id) {
                  <option [value]="w.id">{{ w.name }}</option>
                }
              </select>
            </div>
            <div>
              <label class="vf-label" for="tTo">Hacia</label
              ><select id="tTo" name="tTo" class="vf-input" [(ngModel)]="transfer.to">
                @for (w of warehouses(); track w.id) {
                  <option [value]="w.id">{{ w.name }}</option>
                }
              </select>
            </div>
            <div>
              <label class="vf-label" for="tProd">Producto</label
              ><select id="tProd" name="tProd" class="vf-input" [(ngModel)]="transfer.variantId">
                @for (i of stock(); track i.variantId) {
                  <option [value]="i.variantId">{{ i.productName }} · {{ i.variantName }}</option>
                }
              </select>
            </div>
            <div>
              <label class="vf-label" for="tQty">{{ word(transfer.variantId) }}</label
              ><input
                id="tQty"
                name="tQty"
                type="number"
                min="0"
                step="0.5"
                class="vf-input"
                [(ngModel)]="transfer.quantity"
              />
            </div>
            <div class="sm:col-span-2 lg:col-span-4">
              <button
                type="submit"
                class="vf-btn-primary"
                [disabled]="
                  busy() ||
                  !transfer.variantId ||
                  transfer.from === transfer.to ||
                  !(transfer.quantity > 0)
                "
              >
                Traspasar
              </button>
              <span class="ml-3 text-xs text-zinc-500"
                >Ej.: del almacen a la barra antes de abrir (o al cambiar un casco vacio por una
                botella llena).</span
              >
            </div>
          </form>
        }
        @case ('count') {
          <p class="mb-3 text-sm text-zinc-400">
            Cuenta lo que queda en <b class="text-zinc-200">{{ warehouse()?.name }}</b> al cerrar.
            Botellas empezadas con decimales (0,5 = media). Deja en blanco lo que no cuentes.
          </p>
          <form (ngSubmit)="saveCount()">
            <div class="max-h-[28rem] overflow-y-auto rounded-lg border border-zinc-800">
              <table class="vf-table">
                <thead>
                  <tr>
                    <th>Producto</th>
                    <th class="text-right">Teorico</th>
                    <th class="w-36 text-right">Contado</th>
                  </tr>
                </thead>
                <tbody>
                  @for (i of stock(); track i.variantId) {
                    <tr>
                      <td class="text-zinc-200">
                        {{ i.productName }} <span class="text-zinc-500">· {{ i.variantName }}</span>
                      </td>
                      <td class="text-right text-zinc-400 tabular-nums">
                        {{ fmt(i.quantity, i) }}
                      </td>
                      <td class="text-right">
                        <input
                          type="number"
                          min="0"
                          [step]="isMl(i) ? 0.25 : 1"
                          class="vf-input ml-auto w-28 text-right"
                          [name]="'c' + i.variantId"
                          [(ngModel)]="counted[i.variantId]"
                          [attr.aria-label]="'Contado de ' + i.productName"
                        />
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <div class="mt-3 flex justify-end">
              <button
                type="submit"
                class="vf-btn-primary"
                [disabled]="busy() || countedItems().length === 0"
              >
                Guardar recuento ({{ countedItems().length }})
              </button>
            </div>
          </form>
        }
        @case ('control') {
          <div class="mb-3 flex flex-wrap items-end gap-2">
            <div>
              <label class="vf-label" for="cFrom">Desde</label
              ><input
                id="cFrom"
                type="datetime-local"
                class="vf-input h-9 py-0"
                [(ngModel)]="controlFrom"
              />
            </div>
            <div>
              <label class="vf-label" for="cTo">Hasta</label
              ><input
                id="cTo"
                type="datetime-local"
                class="vf-input h-9 py-0"
                [(ngModel)]="controlTo"
              />
            </div>
            <button type="button" class="vf-btn-secondary vf-btn-sm" (click)="loadControl()">
              Ver
            </button>
            <span class="text-xs text-zinc-500"
              >Sin fechas: la ultima noche (entre los dos ultimos recuentos).</span
            >
          </div>
          @if (control(); as c) {
            <p class="mb-2 text-sm text-zinc-400">
              {{ c.from | date: 'd MMM HH:mm' }} – {{ c.to | date: 'd MMM HH:mm' }} · Merma
              valorada:
              <b [class]="c.totalMermaValue > 0 ? 'text-red-300' : 'text-emerald-300'">{{
                c.totalMermaValue | currency: 'EUR'
              }}</b>
            </p>
            <div class="overflow-x-auto">
              <table class="vf-table">
                <thead>
                  <tr>
                    <th>Producto</th>
                    <th class="text-right">Inicio</th>
                    <th class="text-right">Entradas</th>
                    <th class="text-right">Salidas</th>
                    <th class="text-right">Vendido</th>
                    <th class="text-right">Merma</th>
                    <th class="text-right">Final</th>
                    <th class="text-right">EUR</th>
                  </tr>
                </thead>
                <tbody>
                  @for (r of c.rows; track r.variantId) {
                    <tr>
                      <td class="text-zinc-200">
                        {{ r.productName }} <span class="text-zinc-500">· {{ r.variantName }}</span>
                      </td>
                      <td class="text-right tabular-nums">{{ fmt(r.opening, r) }}</td>
                      <td class="text-right tabular-nums">{{ fmt(r.received, r) }}</td>
                      <td class="text-right tabular-nums">{{ fmt(r.sent, r) }}</td>
                      <td class="text-right tabular-nums">{{ fmt(r.sold, r) }}</td>
                      <td
                        class="text-right tabular-nums"
                        [class]="r.merma < 0 ? 'text-red-300' : 'text-zinc-400'"
                      >
                        {{ fmt(r.merma, r) }}
                      </td>
                      <td class="text-right tabular-nums">{{ fmt(r.closing, r) }}</td>
                      <td class="text-right tabular-nums">{{ r.mermaValue | currency: 'EUR' }}</td>
                    </tr>
                  } @empty {
                    <tr>
                      <td colspan="8" class="text-zinc-500">Sin movimientos en el periodo.</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <p class="mt-2 text-xs text-zinc-500">
              Merma = lo que corrigio el recuento (invitaciones, copas cargadas, roturas, perdidas).
              Una copa son 50 ml: unas 14 por botella de 70 cl.
            </p>
          }
        }
        @case ('order') {
          <table class="vf-table">
            <thead>
              <tr>
                <th>Proveedor</th>
                <th>Producto</th>
                <th class="text-right">Hay</th>
                <th class="text-right">Minimo</th>
                <th class="text-right">Pedir</th>
              </tr>
            </thead>
            <tbody>
              @for (o of order(); track o.variantId) {
                <tr>
                  <td class="text-zinc-300">{{ o.supplierName ?? 'Sin proveedor' }}</td>
                  <td class="text-zinc-200">
                    {{ o.productName }} <span class="text-zinc-500">· {{ o.variantName }}</span>
                  </td>
                  <td class="text-right tabular-nums">{{ fmt(o.quantity, o) }}</td>
                  <td class="text-right tabular-nums">{{ fmt(o.minQuantity, o) }}</td>
                  <td class="text-right font-semibold text-amber-300 tabular-nums">
                    {{ fmt(o.missing, o) }}
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="5" class="text-zinc-500">
                    Todo por encima del minimo. Define minimos en la tabla de existencias.
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
        @case ('containers') {
          <form class="mb-4 grid gap-3 sm:grid-cols-4" (ngSubmit)="saveReturn()">
            <div>
              <label class="vf-label" for="rSup">Proveedor</label
              ><select id="rSup" name="rSup" class="vf-input" [(ngModel)]="ret.supplierId">
                @for (s of activeSuppliers(); track s.id) {
                  <option [value]="s.id">{{ s.name }}</option>
                }
              </select>
            </div>
            <div>
              <label class="vf-label" for="rProd">Envase</label
              ><select id="rProd" name="rProd" class="vf-input" [(ngModel)]="ret.variantId">
                @for (i of stock(); track i.variantId) {
                  <option [value]="i.variantId">{{ i.productName }} · {{ i.variantName }}</option>
                }
              </select>
            </div>
            <div>
              <label class="vf-label" for="rQty">Vacios que se lleva</label
              ><input
                id="rQty"
                name="rQty"
                type="number"
                min="1"
                class="vf-input"
                [(ngModel)]="ret.quantity"
              />
            </div>
            <div>
              <label class="vf-label" for="rDoc">Albaran</label
              ><input
                id="rDoc"
                name="rDoc"
                class="vf-input"
                maxlength="60"
                [(ngModel)]="ret.documentRef"
              />
            </div>
            <div class="sm:col-span-4">
              <button
                type="submit"
                class="vf-btn-primary"
                [disabled]="busy() || !ret.supplierId || !ret.variantId || !(ret.quantity > 0)"
              >
                Registrar devolucion de vacios
              </button>
            </div>
          </form>
          <table class="vf-table">
            <thead>
              <tr>
                <th>Proveedor</th>
                <th>Envase</th>
                <th class="text-right">Recibidos</th>
                <th class="text-right">Devueltos</th>
                <th class="text-right">Pendientes</th>
              </tr>
            </thead>
            <tbody>
              @for (b of containers(); track b.supplierId + b.variantId) {
                <tr>
                  <td class="text-zinc-300">{{ b.supplierName }}</td>
                  <td class="text-zinc-200">
                    {{ b.productName }} <span class="text-zinc-500">· {{ b.variantName }}</span>
                  </td>
                  <td class="text-right tabular-nums">{{ b.received }}</td>
                  <td class="text-right tabular-nums">{{ b.returned }}</td>
                  <td
                    class="text-right font-semibold tabular-nums"
                    [class]="b.pending > 0 ? 'text-amber-300' : 'text-emerald-300'"
                  >
                    {{ b.pending }}
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="5" class="text-zinc-500">
                    Sin envases retornables recibidos. Marca "Envase retornable" en el catalogo y
                    registra las entradas con su proveedor.
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
        @case ('suppliers') {
          <form class="mb-4 grid gap-3 sm:grid-cols-5" (ngSubmit)="saveSupplier()">
            <div class="sm:col-span-2">
              <label class="vf-label" for="sName">Nombre</label
              ><input
                id="sName"
                name="sName"
                class="vf-input"
                maxlength="150"
                required
                [(ngModel)]="sup.name"
              />
            </div>
            <div>
              <label class="vf-label" for="sCont">Contacto</label
              ><input
                id="sCont"
                name="sCont"
                class="vf-input"
                maxlength="150"
                [(ngModel)]="sup.contactName"
              />
            </div>
            <div>
              <label class="vf-label" for="sPhone">Telefono</label
              ><input
                id="sPhone"
                name="sPhone"
                class="vf-input"
                maxlength="40"
                [(ngModel)]="sup.phone"
              />
            </div>
            <div>
              <label class="vf-label" for="sMail">Email</label
              ><input
                id="sMail"
                name="sMail"
                type="email"
                class="vf-input"
                maxlength="255"
                [(ngModel)]="sup.email"
              />
            </div>
            <div class="flex items-center gap-3 sm:col-span-5">
              <label class="flex items-center gap-2 text-sm text-zinc-300"
                ><input
                  type="checkbox"
                  name="sAct"
                  class="accent-brand-500"
                  [(ngModel)]="sup.active"
                />
                Activo</label
              >
              <button
                type="submit"
                class="vf-btn-primary vf-btn-sm"
                [disabled]="busy() || !sup.name.trim()"
              >
                {{ sup.id ? 'Guardar proveedor' : 'Anadir proveedor' }}
              </button>
              @if (sup.id) {
                <button
                  type="button"
                  class="vf-btn-ghost vf-btn-sm"
                  (click)="sup = emptySupplier()"
                >
                  Nuevo
                </button>
              }
            </div>
          </form>
          <ul class="divide-y divide-zinc-800">
            @for (s of suppliers(); track s.id) {
              <li
                class="flex items-center justify-between gap-3 py-2 text-sm"
                [class.opacity-50]="!s.active"
              >
                <span class="text-zinc-200"
                  >{{ s.name }}
                  <span class="text-zinc-500"
                    >{{ s.contactName ? '· ' + s.contactName : '' }}
                    {{ s.phone ? '· ' + s.phone : '' }}</span
                  ></span
                >
                <button type="button" class="vf-btn-ghost vf-btn-sm" (click)="editSupplier(s)">
                  Editar
                </button>
              </li>
            } @empty {
              <li class="py-2 text-sm text-zinc-500">Aun no hay proveedores.</li>
            }
          </ul>
        }
      }
    </section>
  `,
})
export class InventoryOps {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly api = `${environment.apiUrl}/inventory`;

  readonly warehouse = input<Warehouse | null>(null);
  readonly warehouses = input<Warehouse[]>([]);
  readonly stock = input<StockItem[]>([]);
  /** Algo cambio las existencias: la pagina las recarga. */
  readonly changed = output<void>();

  protected readonly tabs: { key: Tab; label: string }[] = [
    { key: 'entry', label: 'Entrada de proveedor' },
    { key: 'transfer', label: 'Traspaso' },
    { key: 'count', label: 'Recuento de cierre' },
    { key: 'control', label: 'Control y merma' },
    { key: 'order', label: 'Pedido sugerido' },
    { key: 'containers', label: 'Envases' },
    { key: 'suppliers', label: 'Proveedores' },
  ];
  protected readonly tab = signal<Tab>('entry');
  protected readonly busy = signal(false);
  protected readonly suppliers = signal<Supplier[]>([]);
  protected readonly activeSuppliers = computed(() => this.suppliers().filter((s) => s.active));
  protected readonly control = signal<Control | null>(null);
  protected readonly order = signal<Suggested[]>([]);
  protected readonly containers = signal<Container[]>([]);

  protected entry = {
    supplierId: '',
    variantId: '',
    documentRef: '',
    boxes: 0,
    perBox: 6,
    loose: 0,
    unitCost: null as number | null,
  };
  protected transfer = { from: '', to: '', variantId: '', quantity: 1 };
  protected counted: Record<string, number | null> = {};
  protected controlFrom = '';
  protected controlTo = '';
  protected ret = { supplierId: '', variantId: '', quantity: 1, documentRef: '' };
  protected sup = this.emptySupplier();

  constructor() {
    this.loadSuppliers();
    effect(() => {
      const w = this.warehouse();
      const first = this.stock()[0]?.variantId ?? '';
      if (!w) return;
      if (!this.entry.variantId) this.entry.variantId = first;
      if (!this.transfer.variantId) this.transfer.variantId = first;
      if (!this.ret.variantId) this.ret.variantId = first;
      this.transfer.from = this.warehouses().find((x) => x.id !== w.id)?.id ?? w.id;
      this.transfer.to = w.id;
    });
  }

  protected emptySupplier() {
    return { id: '', name: '', contactName: '', phone: '', email: '', active: true };
  }

  protected open(t: Tab): void {
    this.tab.set(t);
    if (t === 'control') this.loadControl();
    if (t === 'order') this.loadOrder();
    if (t === 'containers') this.loadContainers();
    if (t === 'count') this.counted = {};
  }

  private item(variantId: string): StockItem | undefined {
    return this.stock().find((i) => i.variantId === variantId);
  }

  protected isMl(i: Unit): boolean {
    return i.stockUnit === 'ML' && !!i.unitSizeMl;
  }

  protected word(variantId: string, singular = false): string {
    const i = this.item(variantId);
    return i && this.isMl(i)
      ? singular
        ? 'botella'
        : 'Botellas'
      : singular
        ? 'unidad'
        : 'Unidades';
  }

  /** Botellas (o unidades) escritas por el usuario -> unidad de inventario (ml o unidades). */
  private toBase(value: number, variantId: string): number {
    const i = this.item(variantId);
    return i && this.isMl(i)
      ? Math.round(Number(value) * i.unitSizeMl!)
      : Math.round(Number(value));
  }

  protected fmt(q: number, r: Unit): string {
    return formatStock(q, r.stockUnit, r.unitSizeMl);
  }

  protected entryUnits(): number {
    return (
      (Number(this.entry.boxes) || 0) * (Number(this.entry.perBox) || 0) +
      (Number(this.entry.loose) || 0)
    );
  }

  protected countedItems(): { variantId: string; quantity: number }[] {
    return Object.entries(this.counted)
      .filter(([, v]) => v !== null && v !== undefined && String(v) !== '')
      .map(([variantId, v]) => ({ variantId, quantity: this.toBase(Number(v), variantId) }));
  }

  private run(obs: Observable<unknown>, ok: string, after?: () => void): void {
    this.busy.set(true);
    obs.subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.success(ok);
        this.changed.emit();
        after?.();
      },
      error: (err) => {
        this.busy.set(false);
        this.toast.error(errorMessage(err));
      },
    });
  }

  protected saveEntry(): void {
    const w = this.warehouse();
    if (!w) return;
    const e = this.entry;
    this.run(
      this.http.post(`${this.api}/warehouses/${w.id}/movements`, {
        variantId: e.variantId,
        type: 'IN',
        quantity: this.toBase(this.entryUnits(), e.variantId),
        reason: 'Entrada de proveedor',
        supplierId: e.supplierId || null,
        documentRef: e.documentRef || null,
        unitCost: e.unitCost,
      }),
      'Entrada registrada',
      () => (this.entry = { ...e, boxes: 0, loose: 0, documentRef: '' }),
    );
  }

  protected saveTransfer(): void {
    const t = this.transfer;
    this.run(
      this.http.post(`${this.api}/transfers`, {
        fromWarehouseId: t.from,
        toWarehouseId: t.to,
        variantId: t.variantId,
        quantity: this.toBase(t.quantity, t.variantId),
        reason: null,
      }),
      'Traspaso hecho',
    );
  }

  protected saveCount(): void {
    const w = this.warehouse();
    if (!w) return;
    this.run(
      this.http.post(`${this.api}/warehouses/${w.id}/count`, {
        items: this.countedItems(),
        reason: null,
      }),
      'Recuento guardado',
      () => this.open('control'),
    );
  }

  protected loadControl(): void {
    const w = this.warehouse();
    if (!w) return;
    const params: Record<string, string> = {};
    if (this.controlFrom) params['from'] = new Date(this.controlFrom).toISOString();
    if (this.controlTo) params['to'] = new Date(this.controlTo).toISOString();
    this.http
      .get<Control>(`${this.api}/warehouses/${w.id}/control`, { params })
      .subscribe({
        next: (c) => this.control.set(c),
        error: (err) => this.toast.error(errorMessage(err)),
      });
  }

  private loadOrder(): void {
    const w = this.warehouse();
    if (!w) return;
    this.http
      .get<Suggested[]>(`${this.api}/warehouses/${w.id}/suggested-order`)
      .subscribe({
        next: (o) => this.order.set(o),
        error: (err) => this.toast.error(errorMessage(err)),
      });
  }

  private loadContainers(): void {
    this.http
      .get<Container[]>(`${this.api}/containers`)
      .subscribe({
        next: (c) => this.containers.set(c),
        error: (err) => this.toast.error(errorMessage(err)),
      });
  }

  protected saveReturn(): void {
    const r = this.ret;
    this.run(
      this.http.post(`${this.api}/container-returns`, { ...r, documentRef: r.documentRef || null }),
      'Devolucion de vacios registrada',
      () => this.loadContainers(),
    );
  }

  private loadSuppliers(): void {
    this.http
      .get<Supplier[]>(`${this.api}/suppliers`)
      .subscribe({ next: (s) => this.suppliers.set(s), error: () => this.suppliers.set([]) });
  }

  protected editSupplier(s: Supplier): void {
    this.sup = {
      id: s.id,
      name: s.name,
      contactName: s.contactName ?? '',
      phone: s.phone ?? '',
      email: s.email ?? '',
      active: s.active,
    };
    setTimeout(() => (document.getElementById('sName') as HTMLInputElement | null)?.focus());
  }

  protected saveSupplier(): void {
    const s = this.sup;
    const body = {
      name: s.name.trim(),
      contactName: s.contactName || null,
      phone: s.phone || null,
      email: s.email || null,
      active: s.active,
    };
    const req = s.id
      ? this.http.put(`${this.api}/suppliers/${s.id}`, body)
      : this.http.post(`${this.api}/suppliers`, body);
    this.run(req, s.id ? 'Proveedor guardado' : 'Proveedor anadido', () => {
      this.sup = this.emptySupplier();
      this.loadSuppliers();
    });
  }
}
