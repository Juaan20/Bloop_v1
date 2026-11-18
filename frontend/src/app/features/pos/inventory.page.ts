import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { Permission } from '../../core/auth/permissions';
import { Page } from '../../core/models/api.models';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { EmptyState } from '../../shared/ui/empty-state';
import { Modal } from '../../shared/ui/modal';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { Venue } from '../venues/venue.models';
import { VenuesService } from '../venues/venues.service';
import { InventoryService } from './inventory.service';
import { InventoryOps } from './inventory-ops';
import { Pagination } from '../../shared/ui/pagination';
import { StockItem, StockMovement, StockMovementType, Warehouse, formatStock } from './pos.models';

const MOVEMENT_LABELS: Record<StockMovementType, string> = {
  IN: 'Entrada',
  OUT: 'Salida',
  ADJUSTMENT: 'Recuento',
  SALE: 'Venta TPV',
  SALE_VOID: 'Anulacion TPV',
};

/** Inventario por almacen: existencias con minimo de aviso, entradas, salidas, recuentos e historial. */
@Component({
  selector: 'vf-inventory-page',
  imports: [InventoryOps, Pagination, DatePipe, FormsModule, ReactiveFormsModule, EmptyState, Modal, PageHeader, Spinner],
  template: `
    <vf-page-header title="Inventario" subtitle="Existencias de cada almacen. Las ventas del TPV descuentan solas">
      @if (canManage) {
        <button type="button" class="vf-btn-secondary" (click)="openWarehouse(null)">+ Almacen</button>
        @if (selected()) {
          <button type="button" class="vf-btn-secondary" (click)="openWarehouse(selected())">Editar almacen</button>
          <button type="button" class="vf-btn-primary" [disabled]="stock().length === 0" (click)="openMovement(null)">+ Movimiento</button>
        }
      }
    </vf-page-header>

    @if (loading()) {
      <vf-spinner />
    } @else if (error()) {
      <p class="vf-card text-red-400">{{ error() }}</p>
    } @else if (warehouses().length === 0) {
      <vf-empty-state title="Sin almacenes" description="Crea un almacen por cada lugar donde guardas existencias (barra, sotano...)." icon="box" />
    } @else {
      <div class="mb-5 flex flex-wrap items-center gap-3">
        <select class="vf-input w-auto" [ngModel]="selected()?.id" (ngModelChange)="select($event)">
          @for (w of warehouses(); track w.id) { <option [value]="w.id">{{ w.venueName }} · {{ w.name }}{{ w.active ? '' : ' (inactivo)' }}</option> }
        </select>
        <label class="flex items-center gap-2 text-sm text-zinc-300"><input type="checkbox" class="size-4 accent-brand-500" [(ngModel)]="onlyLow" /> Solo stock bajo</label>
        @if (lowCount() > 0) { <span class="inline-flex items-center gap-1.5 rounded-full bg-amber-500/10 px-3 py-1 text-xs text-amber-300 ring-1 ring-amber-500/20" role="status">{{ lowCount() }} por debajo del minimo</span> }
      </div>

      @if (stock().length === 0) {
        <p class="vf-card text-sm text-zinc-500">No hay productos con control de existencias. Marca "Controla existencias" en el catalogo del TPV.</p>
      } @else {
        <div class="vf-card mb-8 overflow-x-auto p-0">
          <table class="vf-table">
            <thead><tr><th>Producto</th><th>Categoria</th><th class="text-right">Existencias</th><th>Estado</th><th class="text-right">Minimo</th>@if (canManage) { <th></th> }</tr></thead>
            <tbody>
              @for (i of visibleStock(); track i.variantId) {
                <tr [class.opacity-50]="!i.variantActive">
                  <td class="text-zinc-200">{{ i.productName }} <span class="text-zinc-500">· {{ i.variantName }}</span></td>
                  <td class="text-zinc-400">{{ i.categoryName }}</td>
                  <td class="text-right font-semibold tabular-nums" [class]="i.quantity <= 0 ? 'text-red-300' : i.low ? 'text-amber-300' : 'text-white'">{{ show(i.quantity, i) }}</td>
                  <td>
                    @if (i.quantity <= 0) { <span class="vf-badge bg-red-500/10 text-red-300 ring-red-500/20"><span class="size-1.5 rounded-full bg-current"></span>Agotado</span> }
                    @else if (i.low) { <span class="vf-badge bg-amber-500/10 text-amber-300 ring-amber-500/20"><span class="size-1.5 rounded-full bg-current"></span>Stock bajo</span> }
                    @else { <span class="vf-badge bg-emerald-500/10 text-emerald-300 ring-emerald-500/20"><span class="size-1.5 rounded-full bg-current"></span>Correcto</span> }
                  </td>
                  <td class="text-right">
                    @if (canManage) {
                      <input type="number" min="0" [step]="isMl(i) ? 0.5 : 1" class="vf-input ml-auto w-20 text-right" [ngModel]="toInput(i.minQuantity, i)" (change)="setMinimum(i, $any($event.target).value)"
                             [attr.aria-label]="'Minimo de ' + i.productName + (isMl(i) ? ' en botellas' : ' en unidades')" />
                      @if (isMl(i)) { <span class="text-[11px] text-zinc-500">botellas</span> }
                    } @else { {{ show(i.minQuantity, i) }} }
                  </td>
                  @if (canManage) { <td><button type="button" class="vf-btn-ghost text-xs" (click)="openMovement(i)">Mover</button></td> }
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      <vf-inventory-ops [warehouse]="selected()" [warehouses]="warehouses()" [stock]="stock()" (changed)="refresh()" />
      <h2 class="mb-3 font-display text-lg font-semibold text-white">Ultimos movimientos</h2>
      @if (movements(); as page) {
        @if (page.content.length === 0) {
          <p class="vf-card text-sm text-zinc-500">Sin movimientos.</p>
        } @else {
          <div class="vf-card overflow-x-auto p-0">
            <table class="vf-table">
              <thead><tr><th>Fecha</th><th>Producto</th><th>Tipo</th><th class="text-right">Cantidad</th><th>Motivo</th><th>Usuario</th></tr></thead>
              <tbody>
                @for (m of page.content; track m.id) {
                  <tr>
                    <td class="text-zinc-400">{{ m.createdAt | date: 'd MMM HH:mm' }}</td>
                    <td class="text-zinc-200">{{ m.productName }} <span class="text-zinc-500">· {{ m.variantName }}</span></td>
                    <td class="text-zinc-300">{{ movementLabel(m.type) }}</td>
                    <td class="text-right font-mono" [class]="m.quantity < 0 ? 'text-red-300' : 'text-emerald-300'">{{ m.quantity > 0 ? '+' : '−' }}{{ showMovement(m) }}</td>
                    <td class="text-zinc-400">{{ m.reason ?? '—' }}</td>
                    <td class="text-zinc-400">{{ m.userName ?? '—' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <vf-pagination [page]="page" (pageChange)="loadMovements($event)" />
        }
      }
    }

    <vf-modal [open]="warehouseModal()" [title]="editingWarehouse() ? 'Editar almacen' : 'Nuevo almacen'" (closed)="warehouseModal.set(false)">
      <form class="space-y-4" [formGroup]="warehouseForm" (ngSubmit)="saveWarehouse()">
        <div>
          <label class="vf-label" for="wVenue">Local</label>
          <select id="wVenue" class="vf-input" formControlName="venueId">
            @for (v of venues(); track v.id) { <option [value]="v.id">{{ v.name }}</option> }
          </select>
        </div>
        <div><label class="vf-label" for="wName">Nombre</label><input id="wName" class="vf-input" formControlName="name" placeholder="Barra principal" /></div>
        <label class="flex items-center gap-2 text-sm text-zinc-300"><input type="checkbox" formControlName="active" /> Activo</label>
        <div class="vf-modal-actions flex justify-end"><button type="submit" class="vf-btn-primary" [disabled]="saving() || warehouseForm.invalid">Guardar</button></div>
      </form>
    </vf-modal>

    <vf-modal [open]="movementModal()" title="Registrar movimiento" (closed)="movementModal.set(false)">
      <form class="space-y-4" [formGroup]="movementForm" (ngSubmit)="saveMovement()">
        <div>
          <label class="vf-label" for="mVariant">Producto</label>
          <select id="mVariant" class="vf-input" formControlName="variantId">
            @for (i of stock(); track i.variantId) { <option [value]="i.variantId">{{ i.productName }} · {{ i.variantName }} (hay {{ show(i.quantity, i) }})</option> }
          </select>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="vf-label" for="mType">Tipo</label>
            <select id="mType" class="vf-input" formControlName="type">
              <option value="IN">Entrada (compra, reposicion)</option>
              <option value="OUT">Salida (rotura, merma, invitacion)</option>
              <option value="ADJUSTMENT">Recuento (cantidad contada)</option>
            </select>
          </div>
          <div>
            <label class="vf-label" for="mQty">{{ movementForm.controls.type.value === 'ADJUSTMENT' ? 'Cantidad contada' : 'Cantidad' }} ({{ movementItem() && isMl(movementItem()!) ? 'botellas de ' + movementItem()!.unitSizeMl + ' ml' : 'unidades' }})</label>
            <input id="mQty" type="number" min="0" [step]="movementItem() && isMl(movementItem()!) ? 0.1 : 1" class="vf-input" formControlName="quantity" />
            @if (movementItem() && isMl(movementItem()!)) { <p class="mt-1 text-xs text-zinc-500">Se guarda en ml: {{ toBase(movementForm.controls.quantity.value, movementItem()!) }} ml. Para una botella empezada puedes poner decimales (0,5).</p> }
          </div>
        </div>
        <div><label class="vf-label" for="mReason">Motivo</label><input id="mReason" class="vf-input" formControlName="reason" placeholder="Pedido proveedor, rotura..." /></div>
        <div class="vf-modal-actions flex justify-end"><button type="submit" class="vf-btn-primary" [disabled]="saving() || movementForm.invalid">Registrar</button></div>
      </form>
    </vf-modal>
  `,
})
export class InventoryPage implements OnInit {
  private readonly inventoryService = inject(InventoryService);
  private readonly venuesService = inject(VenuesService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly canManage = inject(AuthService).hasPermission(Permission.INVENTORY_MANAGE);
  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly venues = signal<Venue[]>([]);
  protected readonly selected = signal<Warehouse | null>(null);
  protected readonly stock = signal<StockItem[]>([]);
  protected readonly movements = signal<Page<StockMovement> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly warehouseModal = signal(false);
  protected readonly movementModal = signal(false);
  protected readonly editingWarehouse = signal<Warehouse | null>(null);
  protected readonly lowCount = computed(() => this.stock().filter((i) => i.low).length);
  protected onlyLow = false;

  protected readonly warehouseForm = this.fb.group({
    venueId: ['', Validators.required],
    name: ['', [Validators.required, Validators.maxLength(80)]],
    active: [true],
  });

  protected readonly movementForm = this.fb.group({
    variantId: ['', Validators.required],
    type: ['IN' as StockMovementType],
    quantity: [1, [Validators.required, Validators.min(0)]],
    reason: [''],
  });

  ngOnInit(): void {
    this.venuesService.list().subscribe((v) => this.venues.set(v));
    this.loadWarehouses();
  }

  protected visibleStock(): StockItem[] {
    return this.onlyLow ? this.stock().filter((i) => i.low) : this.stock();
  }

  protected movementLabel(type: StockMovementType): string {
    return MOVEMENT_LABELS[type];
  }

  protected select(id: string): void {
    const warehouse = this.warehouses().find((w) => w.id === id) ?? null;
    this.selected.set(warehouse);
    if (warehouse) {
      this.loadStock();
      this.loadMovements(0);
    }
  }

  protected openWarehouse(warehouse: Warehouse | null): void {
    this.editingWarehouse.set(warehouse);
    this.warehouseForm.reset({ venueId: warehouse?.venueId ?? this.venues()[0]?.id ?? '', name: warehouse?.name ?? '', active: warehouse?.active ?? true });
    if (warehouse) this.warehouseForm.controls.venueId.disable();
    else this.warehouseForm.controls.venueId.enable();
    this.warehouseModal.set(true);
  }

  protected openMovement(item: StockItem | null): void {
    this.movementForm.reset({ variantId: item?.variantId ?? this.stock()[0]?.variantId ?? '', type: 'IN', quantity: 1, reason: '' });
    this.movementModal.set(true);
  }

  protected saveWarehouse(): void {
    this.saving.set(true);
    this.inventoryService.saveWarehouse(this.editingWarehouse()?.id ?? null, this.warehouseForm.getRawValue()).subscribe({
      next: (w) => {
        this.toast.success('Almacen guardado');
        this.saving.set(false);
        this.warehouseModal.set(false);
        this.loadWarehouses(w.id);
      },
      error: (err) => this.fail(err),
    });
  }

  /** Producto elegido en el formulario de movimiento (para saber si se cuenta en botellas). */
  protected readonly movementItem = signal<StockItem | null>(null);
  private readonly syncMovementItem = this.movementForm.controls.variantId.valueChanges
    .pipe(takeUntilDestroyed())
    .subscribe((id) => this.movementItem.set(this.stock().find((i) => i.variantId === id) ?? null));

  protected isMl(i: StockItem): boolean {
    return i.stockUnit === 'ML' && !!i.unitSizeMl;
  }

  /** Existencias legibles: botellas (y ml) o unidades. */
  protected show(quantity: number, i: StockItem): string {
    return formatStock(quantity, i.stockUnit, i.unitSizeMl);
  }

  protected showMovement(m: StockMovement): string {
    const item = this.stock().find((i) => i.variantId === m.variantId);
    return item ? this.show(Math.abs(m.quantity), item) : String(Math.abs(m.quantity));
  }

  /** Lo que escribe el usuario (botellas o unidades) -> unidad de inventario (ml o unidades). */
  protected toBase(value: number, i: StockItem): number {
    return this.isMl(i) ? Math.round(Number(value) * i.unitSizeMl!) : Math.round(Number(value));
  }

  protected toInput(quantity: number, i: StockItem): number {
    return this.isMl(i) ? Math.round((quantity / i.unitSizeMl!) * 10) / 10 : quantity;
  }

  protected saveMovement(): void {
    const warehouse = this.selected();
    if (!warehouse) return;
    const v = this.movementForm.getRawValue();
    const item = this.stock().find((i) => i.variantId === v.variantId);
    const quantity = item ? this.toBase(v.quantity, item) : Math.round(v.quantity);
    this.saving.set(true);
    this.inventoryService.registerMovement(warehouse.id, v.variantId, v.type, quantity, v.reason || null).subscribe({
      next: (stock) => {
        this.stock.set(stock);
        this.toast.success('Movimiento registrado');
        this.saving.set(false);
        this.movementModal.set(false);
        this.loadMovements(0);
      },
      error: (err) => this.fail(err),
    });
  }

  protected setMinimum(item: StockItem, value: string): void {
    const warehouse = this.selected();
    const min = this.toBase(Number(value), item);
    if (!warehouse || !Number.isFinite(min) || min < 0 || min === item.minQuantity) return;
    this.inventoryService.setMinimum(warehouse.id, item.variantId, min).subscribe({
      next: (stock) => {
        this.stock.set(stock);
        this.toast.success('Minimo actualizado');
      },
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }

  protected loadMovements(page: number): void {
    const warehouse = this.selected();
    if (!warehouse) return;
    this.inventoryService.movements(warehouse.id, page).subscribe((p) => this.movements.set(p));
  }

  /** Tras una entrada, traspaso o recuento: existencias y movimientos al dia. */
  protected refresh(): void {
    this.loadStock();
    this.loadMovements(0);
  }

  private loadStock(): void {
    const warehouse = this.selected();
    if (!warehouse) return;
    this.inventoryService.stock(warehouse.id).subscribe({
      next: (s) => this.stock.set(s),
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }

  private loadWarehouses(selectId?: string): void {
    this.inventoryService.warehouses().subscribe({
      next: (w) => {
        this.warehouses.set(w);
        this.loading.set(false);
        const target = selectId ?? this.selected()?.id ?? w[0]?.id;
        if (target) this.select(target);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  private fail(err: unknown): void {
    this.toast.error(errorMessage(err));
    this.saving.set(false);
  }
}
