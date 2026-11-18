import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Page } from '../../core/models/api.models';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { EmptyState } from '../../shared/ui/empty-state';
import { Modal } from '../../shared/ui/modal';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { StatusBadge } from '../../shared/ui/status-badge';
import { Venue } from '../venues/venue.models';
import { VenuesService } from '../venues/venues.service';
import { InventoryService } from './inventory.service';
import { CashReport } from './cash-report';
import { CATEGORY_KINDS, CashSession, CategoryKind, Terminal, TerminalType, Warehouse } from './pos.models';
import { Pagination } from '../../shared/ui/pagination';
import { PosService } from './pos.service';

/** Terminales del TPV y historial de cajas (cierres con su arqueo e informe). */
@Component({
  selector: 'vf-pos-terminals-page',
  imports: [Pagination, CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink, EmptyState, Modal, PageHeader, Spinner, StatusBadge, CashReport],
  template: `
    <vf-page-header title="Terminales y cajas" subtitle="Puntos de venta de cada local y sus cierres de caja">
      <a routerLink="/admin/tpv/catalogo" class="vf-btn-secondary">Catalogo</a>
      <a routerLink="/tpv" class="vf-btn-secondary">Abrir TPV</a>
      <button type="button" class="vf-btn-primary" (click)="openTerminal(null)">+ Terminal</button>
    </vf-page-header>

    @if (loading()) {
      <vf-spinner />
    } @else if (error()) {
      <p class="vf-card text-red-400">{{ error() }}</p>
    } @else {
      @if (terminals().length === 0) {
        <vf-empty-state title="Sin terminales" description="Crea antes un almacen en Inventario (de el se descontaran las existencias) y despues el terminal." icon="receipt" />
      } @else {
        <div class="mb-8 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          @for (t of terminals(); track t.id) {
            <div class="vf-card" [class.opacity-50]="!t.active">
              <div class="flex items-start justify-between gap-2">
                <div>
                  <p class="font-display text-lg font-semibold text-white">{{ t.name }}</p>
                  <p class="text-sm text-zinc-400">{{ t.venueName }} · {{ t.type === 'BAR' ? 'Barra' : t.type === 'VIP_SERVICE' ? 'Servicio VIP' : 'Taquilla' }} · almacen {{ t.warehouseName }}</p>
                  <p class="mt-1 flex flex-wrap gap-1">@for (k of t.allowedKinds; track k) { <span class="vf-badge bg-brand-500/10 text-brand-300 ring-brand-500/25">Vende: {{ kindLabel(k) }}</span> }</p>
                </div>
                <button type="button" class="vf-btn-ghost text-xs" (click)="openTerminal(t)">Editar</button>
              </div>
              <p class="mt-3 text-sm" [class]="t.openSessionId ? 'text-emerald-300' : 'text-zinc-500'">
                {{ t.openSessionId ? 'Caja abierta por ' + t.openedByName + ' desde ' + (t.openSessionSince | date: 'd MMM HH:mm') : 'Caja cerrada' }}
              </p>
            </div>
          }
        </div>
      }

      <h2 class="mb-3 font-display text-lg font-semibold text-white">Historial de cajas</h2>
      @if (history(); as page) {
        @if (page.content.length === 0) {
          <p class="vf-card text-sm text-zinc-500">Todavia no se ha abierto ninguna caja.</p>
        } @else {
          <div class="vf-card overflow-x-auto p-0">
            <table class="vf-table">
              <thead><tr><th>Terminal</th><th>Apertura</th><th>Cierre</th><th class="text-right">Ventas</th><th class="text-right">Esperado</th><th class="text-right">Diferencia</th><th>Estado</th><th></th></tr></thead>
              <tbody>
                @for (s of page.content; track s.id) {
                  <tr>
                    <td class="text-zinc-200">{{ s.terminalName }}</td>
                    <td class="text-sm text-zinc-400">{{ s.openedAt | date: 'd MMM HH:mm' }} · {{ s.openedByName }}</td>
                    <td class="text-sm text-zinc-400">{{ s.closedAt ? (s.closedAt | date: 'd MMM HH:mm') + ' · ' + s.closedByName : '—' }}</td>
                    <td class="text-right text-white">{{ s.summary.totalSales | currency: 'EUR' }}</td>
                    <td class="text-right text-zinc-300">{{ s.summary.expectedCash | currency: 'EUR' }}</td>
                    <td class="text-right" [class]="(s.difference ?? 0) < 0 ? 'text-red-400' : 'text-zinc-300'">{{ s.difference === null ? '—' : (s.difference | currency: 'EUR') }}</td>
                    <td><vf-status-badge [status]="s.status" /></td>
                    <td><button type="button" class="vf-btn-ghost text-xs" (click)="report.set(s)">Informe</button></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <vf-pagination [page]="page" (pageChange)="loadHistory($event)" />
        }
      }
    }

    <vf-modal [open]="terminalModal()" [title]="editing() ? 'Editar terminal' : 'Nuevo terminal'" (closed)="terminalModal.set(false)">
      <form class="space-y-4" [formGroup]="form" (ngSubmit)="save()">
        <div class="grid grid-cols-2 gap-4">
          <div><label class="vf-label" for="tName">Nombre</label><input id="tName" class="vf-input" formControlName="name" placeholder="Barra 1" /></div>
          <div>
            <label class="vf-label" for="tType">Tipo</label>
            <select id="tType" class="vf-input" formControlName="type">
              <option value="BAR">Barra</option>
              <option value="BOX_OFFICE">Taquilla</option>
              <option value="VIP_SERVICE">Servicio VIP (botellas a mesas)</option>
            </select>
          </div>
        </div>
        <div>
          <label class="vf-label" for="tVenue">Local</label>
          <select id="tVenue" class="vf-input" formControlName="venueId">
            @for (v of venues(); track v.id) { <option [value]="v.id">{{ v.name }}</option> }
          </select>
        </div>
        <div>
          <label class="vf-label" for="tWarehouse">Almacen del que descuenta</label>
          <select id="tWarehouse" class="vf-input" formControlName="warehouseId">
            <option value="">Elige un almacen</option>
            @for (w of warehousesOfVenue(); track w.id) { <option [value]="w.id">{{ w.name }}</option> }
          </select>
          @if (warehousesOfVenue().length === 0) {
            <p class="mt-1 text-xs text-zinc-500">Este local no tiene almacenes: crealo en <a routerLink="/admin/inventario" class="vf-link" (click)="terminalModal.set(false)">Inventario</a>.</p>
          }
        </div>
        <fieldset>
          <legend class="vf-label">Que vende este terminal</legend>
          <p class="mb-2 text-xs text-zinc-500">La barra normal solo deberia vender productos de barra: asi el personal no navega por todo el catalogo.</p>
          <div class="grid gap-2 sm:grid-cols-2">
            @for (k of kinds; track k.value) {
              <label class="flex cursor-pointer items-start gap-2 rounded-lg border border-zinc-800 p-2.5 text-sm hover:border-zinc-600">
                <input type="checkbox" class="mt-1 accent-brand-500" [checked]="allowedKinds().includes(k.value)" (change)="toggleKind(k.value)" />
                <span><span class="font-medium text-zinc-100">{{ k.label }}</span><br /><span class="text-xs text-zinc-500">{{ k.hint }}</span></span>
              </label>
            }
          </div>
        </fieldset>
        <label class="flex items-center gap-2 text-sm text-zinc-300"><input type="checkbox" formControlName="active" /> Activo</label>
        <div class="vf-modal-actions flex justify-end"><button type="submit" class="vf-btn-primary" [disabled]="saving() || form.invalid">Guardar</button></div>
      </form>
    </vf-modal>

    <vf-modal [open]="!!report()" [title]="'Caja de ' + (report()?.terminalName ?? '')" (closed)="report.set(null)">
      @if (report(); as s) { <vf-cash-report [session]="s" /> }
    </vf-modal>
  `,
})
export class PosTerminalsPage implements OnInit {
  private readonly posService = inject(PosService);
  private readonly inventoryService = inject(InventoryService);
  private readonly venuesService = inject(VenuesService);
  private readonly toast = inject(ToastService);

  protected readonly terminals = signal<Terminal[]>([]);
  protected readonly venues = signal<Venue[]>([]);
  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly history = signal<Page<CashSession> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly terminalModal = signal(false);
  protected readonly editing = signal<Terminal | null>(null);
  protected readonly report = signal<CashSession | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    type: ['BAR' as TerminalType],
    venueId: ['', Validators.required],
    warehouseId: ['', Validators.required],
    active: [true],
  });

  ngOnInit(): void {
    this.venuesService.list().subscribe((v) => this.venues.set(v));
    this.inventoryService.warehouses().subscribe((w) => this.warehouses.set(w));
    this.load();
    this.loadHistory(0);
  }

  protected warehousesOfVenue(): Warehouse[] {
    return this.warehouses().filter((w) => w.venueId === this.form.controls.venueId.value && w.active);
  }

  protected readonly kinds = CATEGORY_KINDS;
  /** Tipos de categoria que vende el terminal (casillas del formulario). */
  protected readonly allowedKinds = signal<CategoryKind[]>(['BAR']);

  protected kindLabel(kind: CategoryKind): string {
    return CATEGORY_KINDS.find((k) => k.value === kind)?.label ?? kind;
  }

  protected toggleKind(kind: CategoryKind): void {
    const current = this.allowedKinds();
    this.allowedKinds.set(current.includes(kind) ? current.filter((k) => k !== kind) : [...current, kind]);
  }

  protected openTerminal(terminal: Terminal | null): void {
    this.editing.set(terminal);
    this.allowedKinds.set(terminal?.allowedKinds?.length ? [...terminal.allowedKinds] : ['BAR']);
    this.form.reset({
      name: terminal?.name ?? '',
      type: terminal?.type ?? 'BAR',
      venueId: terminal?.venueId ?? this.venues()[0]?.id ?? '',
      warehouseId: terminal?.warehouseId ?? '',
      active: terminal?.active ?? true,
    });
    // El local de un terminal existente no se puede cambiar.
    if (terminal) this.form.controls.venueId.disable();
    else this.form.controls.venueId.enable();
    this.terminalModal.set(true);
  }

  protected save(): void {
    this.saving.set(true);
    this.posService.saveTerminal(this.editing()?.id ?? null, { ...this.form.getRawValue(), allowedKinds: this.allowedKinds() }).subscribe({
      next: () => {
        this.toast.success('Terminal guardado');
        this.saving.set(false);
        this.terminalModal.set(false);
        this.load();
      },
      error: (err) => {
        this.toast.error(errorMessage(err));
        this.saving.set(false);
      },
    });
  }

  protected loadHistory(page: number): void {
    this.posService.sessionHistory(null, page).subscribe({
      next: (p) => this.history.set(p),
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }

  private load(): void {
    this.posService.terminals().subscribe({
      next: (t) => {
        this.terminals.set(t);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
