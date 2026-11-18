import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { Permission } from '../../core/auth/permissions';
import { errorMessage } from '../../core/services/api-error';
import { ToastService } from '../../core/services/toast.service';
import { EmptyState } from '../../shared/ui/empty-state';
import { Reveal } from '../../shared/ui/reveal';
import { StatusBadge } from '../../shared/ui/status-badge';
import { Icon } from '../../shared/ui/icon';
import { PageHeader } from '../../shared/ui/page-header';
import { Product } from '../pos/pos.models';
import { PosService } from '../pos/pos.service';
import { VipTable, VipZone } from '../reservations/reservation.models';
import { ReservationsService } from '../reservations/reservations.service';
import { Venue } from '../venues/venue.models';
import { VenuesService } from '../venues/venues.service';
import { FloorPlan, PlanTable, TableGeometry } from './floor-plan';
import { ELEMENT_STYLE, FloorElementType, FloorLayout, SHAPE_PRESETS, ShapePreset, defaultLayout, presetCells, resizeCells } from './floor-layout';
import { ROOM_COLORS, RoomColor, TABLE_STATUS, TablePosition, VipMap, VipParty, VipTableShape, VipTableStatus } from './vip.models';
import { VipService } from './vip.service';
import { VipTablePanel } from './vip-table-panel';

/** El plano en vivo se refresca solo mientras se mira "ahora". */
const LIVE_REFRESH_MS = 20_000;

/**
 * Plano VIP del local:
 * - En vivo: estado de cada mesa (disponible, pendiente de pago, pagada, ocupada, servicio...) y su ficha.
 * - Editar (VENUE_MANAGE): colocar, mover y redimensionar mesas, cambiar forma, precio y paquete.
 */
@Component({
  selector: 'vf-vip-plan-page',
  imports: [DatePipe, FormsModule, EmptyState, FloorPlan, Icon, PageHeader, Reveal, StatusBadge, VipTablePanel],
  template: `
    <vf-page-header title="Plano VIP" subtitle="Mesas, reservas y servicio de cada local de un vistazo">
      <select class="vf-input h-9 w-auto py-0" [ngModel]="venueId()" (ngModelChange)="selectVenue($event)" aria-label="Local">
        @for (v of venues(); track v.id) { <option [value]="v.id">{{ v.name }}</option> }
      </select>
      @if (canEdit) {
        <div class="inline-flex h-9 items-center rounded-lg border border-zinc-800 bg-zinc-900/60 p-0.5" role="tablist" aria-label="Modo">
          <button type="button" role="tab" class="h-full cursor-pointer rounded-md px-3 text-xs font-medium transition" [attr.aria-selected]="mode() === 'live'"
                  [class]="mode() === 'live' ? 'bg-zinc-800 text-white' : 'text-zinc-400 hover:text-white'" (click)="setMode('live')">En vivo</button>
          <button type="button" role="tab" class="h-full cursor-pointer rounded-md px-3 text-xs font-medium transition" [attr.aria-selected]="mode() === 'edit'"
                  [class]="mode() === 'edit' ? 'bg-zinc-800 text-white' : 'text-zinc-400 hover:text-white'" (click)="setMode('edit')">Mesas</button>
          <button type="button" role="tab" class="h-full cursor-pointer rounded-md px-3 text-xs font-medium transition" [attr.aria-selected]="mode() === 'shape'"
                  [class]="mode() === 'shape' ? 'bg-zinc-800 text-white' : 'text-zinc-400 hover:text-white'" (click)="setMode('shape')">Forma de la sala</button>
          <button type="button" role="tab" class="h-full cursor-pointer rounded-md px-3 text-xs font-medium transition" [attr.aria-selected]="mode() === 'rooms'"
                  [class]="mode() === 'rooms' ? 'bg-zinc-800 text-white' : 'text-zinc-400 hover:text-white'" (click)="setMode('rooms')">Salas</button>
        </div>
      }
    </vf-page-header>

    <!-- Selector de salas: cada sala tiene su plano y sus mesas -->
    @if (rooms().length && mode() !== 'rooms') {
      <nav class="mb-4 flex gap-2 overflow-x-auto" role="tablist" aria-label="Salas">
        @for (r of rooms(); track r.id) {
          <button type="button" role="tab" [attr.aria-selected]="r.id === zoneId()"
                  class="inline-flex h-9 shrink-0 cursor-pointer items-center gap-2 rounded-lg border px-3 text-sm transition"
                  [class]="r.id === zoneId() ? 'border-zinc-600 bg-zinc-800 text-white' : 'border-zinc-800 text-zinc-400 hover:text-white'"
                  (click)="selectRoom(r.id)">
            <span class="size-2 rounded-full" [class]="roomDot(r.color)"></span>{{ r.name }}
            @if (!r.active) { <span class="text-[10px] text-zinc-500">(inactiva)</span> }
            @if (mode() === 'live') { <span class="text-xs text-zinc-500 tabular-nums">{{ occupiedIn(r.id) }}/{{ tablesIn(r.id) }}</span> }
          </button>
        }
      </nav>
    }

    @if (venues().length === 0 && !loading()) {
      <vf-empty-state title="Sin locales" description="Crea un local para disenar su plano VIP." icon="pin" />
    } @else if (mode() === 'rooms') {
      <!-- ===================== SALAS ===================== -->
      <div class="grid gap-4 xl:grid-cols-[1fr_24rem]">
        <ul class="space-y-2">
          @for (r of zones(); track r.id) {
            <li class="vf-card flex flex-wrap items-center justify-between gap-3 py-3" [class.opacity-60]="!r.active">
              <div class="flex min-w-0 items-center gap-3">
                <span class="size-3 shrink-0 rounded-full" [class]="roomDot(r.color)"></span>
                <div class="min-w-0">
                  <p class="font-medium text-white">{{ r.name }} @if (!r.active) { <span class="text-xs text-zinc-500">· inactiva</span> }</p>
                  <p class="truncate text-xs text-zinc-500">{{ r.tables.length }} mesas{{ r.capacity ? ' · aforo ' + r.capacity : '' }}{{ r.description ? ' · ' + r.description : '' }}</p>
                </div>
              </div>
              <div class="flex gap-2">
                <button type="button" class="vf-btn-ghost vf-btn-sm" (click)="editRoom(r)">Editar</button>
                <button type="button" class="vf-btn-secondary vf-btn-sm" (click)="openRoomPlan(r.id)">Abrir plano</button>
              </div>
            </li>
          } @empty {
            <li class="vf-card text-sm text-zinc-500">Este local aun no tiene salas. Crea la primera (Sala principal, Terraza...).</li>
          }
        </ul>
        <form class="vf-card space-y-3 self-start" vfReveal (ngSubmit)="saveRoom()" #roomFormEl>
          <p class="text-sm font-medium text-white">{{ roomForm.id ? 'Editar sala' : 'Nueva sala' }}</p>
          <div><label class="vf-label" for="rName">Nombre</label><input id="rName" name="rName" class="vf-input" maxlength="100" required [(ngModel)]="roomForm.name" placeholder="Sala principal, Terraza..." /></div>
          <div><label class="vf-label" for="rDesc">Descripcion</label><input id="rDesc" name="rDesc" class="vf-input" maxlength="500" [(ngModel)]="roomForm.description" /></div>
          <div class="grid grid-cols-2 gap-3">
            <div><label class="vf-label" for="rCap">Aforo</label><input id="rCap" name="rCap" type="number" min="1" class="vf-input" [(ngModel)]="roomForm.capacity" placeholder="—" /></div>
            <div><label class="vf-label" for="rOrder">Orden</label><input id="rOrder" name="rOrder" type="number" min="0" class="vf-input" [(ngModel)]="roomForm.sortOrder" /></div>
          </div>
          <fieldset><legend class="vf-label">Color</legend>
            <div class="flex gap-2">
              @for (col of roomColors; track col) {
                <button type="button" class="size-8 cursor-pointer rounded-full ring-2 ring-offset-2 ring-offset-zinc-900" [class]="roomDot(col)"
                        [class.ring-white]="roomForm.color === col" [class.ring-transparent]="roomForm.color !== col" (click)="roomForm.color = col" [attr.aria-label]="'Color ' + col"></button>
              }
            </div>
          </fieldset>
          <label class="flex items-center gap-2 text-sm text-zinc-300"><input type="checkbox" name="rActive" class="accent-brand-500" [(ngModel)]="roomForm.active" /> Activa (sus mesas se pueden reservar)</label>
          <div class="flex flex-wrap gap-2">
            <button type="submit" class="vf-btn-primary vf-btn-sm" [disabled]="saving() || !roomForm.name.trim()">{{ roomForm.id ? 'Guardar sala' : 'Crear sala' }}</button>
            @if (roomForm.id) {
              <button type="button" class="vf-btn-ghost vf-btn-sm" (click)="newRoom()">Nueva sala</button>
              <button type="button" class="vf-btn-danger vf-btn-sm" [disabled]="saving()" (click)="deleteRoom()">Borrar</button>
            }
          </div>
          <p class="text-xs text-zinc-500">Una sala con reservas no se puede borrar: desactivala para conservar su historial.</p>
        </form>
      </div>
    } @else if (mode() === 'live') {
      <!-- ===================== EN VIVO ===================== -->
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div class="flex flex-wrap items-center gap-2">
          <!-- 1. Fiesta: las mesas se reservan para la fiesta completa -->
          <label class="sr-only" for="party">Fiesta</label>
          <select id="party" class="vf-input h-9 w-auto min-w-64 py-0" [ngModel]="partyId()" (ngModelChange)="selectParty($event)">
            @if (parties().length === 0) { <option value="">No hay fiestas programadas en este local</option> }
            @for (f of parties(); track f.sessionId) {
              <option [value]="f.sessionId">{{ f.now ? 'AHORA · ' : '' }}{{ f.eventName }}{{ f.title ? ' · ' + f.title : '' }} — {{ f.startsAt | date: 'EEE d MMM' }}</option>
            }
          </select>
          <label class="flex items-center gap-1.5 text-xs text-zinc-400"><input type="checkbox" class="accent-brand-500" [ngModel]="showPast()" (ngModelChange)="togglePast($event)" /> Ver fiestas pasadas</label>
          @if (currentParty()?.now) { <span class="inline-flex items-center gap-1.5 text-xs text-emerald-300"><span class="size-1.5 animate-pulse rounded-full bg-emerald-400"></span>En curso</span> }
        </div>
        <ul class="flex flex-wrap gap-x-4 gap-y-1 text-xs text-zinc-400" aria-label="Leyenda">
          @for (s of legend; track s.key) {
            <li class="flex items-center gap-1.5"><span class="size-2.5 rounded-full" [class]="s.dot"></span>{{ s.label }} <span class="text-zinc-600 tabular-nums">{{ counts()[s.key] ?? 0 }}</span></li>
          }
        </ul>
      </div>
      <div class="grid gap-4 xl:grid-cols-[1fr_22rem]">
        <div>
          @if (error()) { <p class="vf-card mb-3 text-sm text-red-300">{{ error() }}</p> }
          @if (map(); as m) {
            <vf-floor-plan [tables]="liveTables()" [layout]="layout()" mode="live" [selectedId]="selectedId()" (selected)="selectedId.set($event)" />
            @if (unplacedLive().length) {
              <p class="mt-2 text-xs text-zinc-500">{{ unplacedLive().length }} mesas sin colocar en el plano. Colocalas en "Mesas".</p>
            }
          } @else {
            <div class="vf-skeleton aspect-[16/10] w-full rounded-2xl"></div>
          }
        </div>
        <aside class="vf-card self-start" aria-live="polite">
          @if (selectedLive(); as t) {
            <vf-vip-table-panel [table]="t" [sessionId]="partyId() || null" [products]="vipProducts()" (closed)="selectedId.set(null)" (changed)="reload()" />
          } @else {
            <p class="text-sm font-medium text-zinc-200">Mesas que necesitan atencion</p>
            <ul class="mt-3 space-y-2">
              @for (t of attention(); track t.id) {
                <li><button type="button" class="flex w-full cursor-pointer items-center justify-between gap-2 rounded-lg border border-zinc-800 px-3 py-2 text-left text-sm transition hover:border-zinc-600" (click)="selectedId.set(t.id)">
                  <span class="text-zinc-100">{{ t.name }} <span class="text-xs text-zinc-500">· {{ t.reservation?.contactName }}</span></span>
                  <span class="flex items-center gap-1.5 text-xs" [class]="t.status === 'SERVICE_REQUESTED' ? 'text-rose-300' : 'text-amber-300'"><span class="size-1.5 rounded-full bg-current"></span>{{ statusLabel(t.status) }}</span>
                </button></li>
              } @empty {
                <li class="text-sm text-zinc-500">Ninguna: todo en orden.</li>
              }
            </ul>
            <p class="mt-4 text-xs text-zinc-500">Toca una mesa para reservarla, cobrar, sentar al grupo o ver su cuenta, pedidos y QR.</p>
            <div class="mt-5 border-t border-zinc-800 pt-4">
              <p class="text-sm font-medium text-zinc-200">Reservas de esta fiesta en {{ currentRoom()?.name }}</p>
              <ul class="mt-2 space-y-1.5">
                @for (r of partyBookings(); track r.id) {
                  <li><button type="button" class="flex w-full cursor-pointer items-center justify-between gap-2 rounded-lg px-2 py-1.5 text-left text-sm hover:bg-zinc-800/60" (click)="selectedId.set(r.tableId)">
                    <span class="min-w-0"><span class="text-zinc-100">{{ r.tableName }}</span> <span class="text-xs text-zinc-500">· {{ r.contactName }} · {{ r.guests }} pers.</span></span>
                    <vf-status-badge [status]="r.status" />
                  </button></li>
                } @empty {
                  <li class="text-xs text-zinc-500">Sin reservas.</li>
                }
              </ul>
            </div>
          }
        </aside>
      </div>
    } @else if (mode() === 'shape') {
      <!-- ===================== FORMA DEL LOCAL ===================== -->
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <p class="text-sm text-zinc-400">Forma de <b class="text-zinc-200">{{ currentRoom()?.name }}</b>: elige una forma base (L, U, T...) y retocala tocando las celdas. Anade la barra, el escenario, la cabina, la pista... y arrastralos a su sitio.</p>
        <div class="flex items-center gap-2">
          @if (shapeDirty()) { <span class="text-xs text-amber-300" role="status">Cambios sin guardar</span> }
          <button type="button" class="vf-btn-primary" [disabled]="saving() || !shapeDirty()" (click)="saveShape()"><vf-icon name="check" class="size-4" /> Guardar forma de la sala</button>
        </div>
      </div>
      <div class="grid gap-4 xl:grid-cols-[1fr_22rem]">
        <vf-floor-plan [tables]="editTables()" [layout]="shapeDraft()" mode="shape" [selectedElement]="selectedElement()"
                       (paint)="paintCell($event)" (elementSelected)="selectedElement.set($event)" (elementChanged)="moveElement($event)" />
        <aside class="vf-card space-y-5 self-start">
          <div>
            <p class="text-xs font-medium text-zinc-400">Forma base</p>
            <div class="mt-2 grid grid-cols-3 gap-2">
              @for (p of presets; track p.value) {
                <button type="button" class="vf-btn-secondary vf-btn-sm" (click)="applyPreset(p.value)">{{ p.label }}</button>
              }
            </div>
            <p class="mt-2 text-xs text-zinc-500">Despues pinta o borra celdas directamente en el plano (toca y arrastra).</p>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div><label class="vf-label" for="gridCols">Columnas</label><input id="gridCols" type="number" min="8" max="40" class="vf-input" [ngModel]="shapeDraft().cols" (change)="resizeGrid($any($event.target).value, shapeDraft().rows)" /></div>
            <div><label class="vf-label" for="gridRows">Filas</label><input id="gridRows" type="number" min="6" max="30" class="vf-input" [ngModel]="shapeDraft().rows" (change)="resizeGrid(shapeDraft().cols, $any($event.target).value)" /></div>
          </div>
          <div class="border-t border-zinc-800 pt-4">
            <p class="text-xs font-medium text-zinc-400">Anadir elemento</p>
            <div class="mt-2 flex flex-wrap gap-1.5">
              @for (t of elementTypes; track t) {
                <button type="button" class="vf-btn-secondary vf-btn-sm" (click)="addElement(t)"><vf-icon name="plus" class="size-3" /> {{ elementLabel(t) }}</button>
              }
            </div>
          </div>
          @if (selectedElement() !== null && shapeDraft().elements[selectedElement()!]; as el) {
            <div class="space-y-3 border-t border-zinc-800 pt-4">
              <p class="text-xs font-medium text-zinc-400">Elemento seleccionado · {{ elementLabel(el.type) }}</p>
              <div><label class="vf-label" for="elLabel">Texto</label>
                <input id="elLabel" class="vf-input" maxlength="40" [ngModel]="el.label" (ngModelChange)="setElementLabel($event)" placeholder="Barra principal, VIP 2..." /></div>
              <p class="text-xs text-zinc-500">Arrastralo para moverlo; el cuadro blanco de la esquina cambia su tamano. Con el teclado: flechas.</p>
              <button type="button" class="vf-btn-danger vf-btn-sm" (click)="removeElement()">Quitar elemento</button>
            </div>
          }
        </aside>
      </div>
    } @else {
      <!-- ===================== EDITOR ===================== -->
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <p class="text-sm text-zinc-400">Arrastra las mesas, usa el tirador de la esquina para cambiar su tamano o las flechas del teclado para afinar.</p>
        <div class="flex items-center gap-2">
          @if (dirty()) { <span class="text-xs text-amber-300" role="status">Cambios sin guardar</span> }
          <button type="button" class="vf-btn-primary" [disabled]="saving() || !dirty()" (click)="saveLayout()"><vf-icon name="check" class="size-4" /> Guardar plano</button>
        </div>
      </div>
      <div class="grid gap-4 xl:grid-cols-[1fr_22rem]">
        <div class="space-y-3">
          <vf-floor-plan [tables]="editTables()" [layout]="layout()" mode="edit" [selectedId]="selectedId()" (selected)="selectTable($event)" (changed)="move($event)" />
          @if (unplacedEdit().length) {
            <div class="vf-card">
              <p class="text-xs font-medium text-zinc-400">Mesas sin colocar</p>
              <div class="mt-2 flex flex-wrap gap-2">
                @for (t of unplacedEdit(); track t.id) {
                  <button type="button" class="vf-btn-secondary vf-btn-sm" (click)="place(t.id)"><vf-icon name="plus" class="size-3.5" /> {{ t.name }}</button>
                }
              </div>
            </div>
          }
        </div>
        <aside class="vf-card space-y-5 self-start">
          <!-- Nueva mesa / zona -->
          <form class="space-y-2" (ngSubmit)="addTable()">
            <p class="text-xs font-medium text-zinc-400">Mesas de {{ currentRoom()?.name ?? 'la sala' }}</p>
            <button type="submit" class="vf-btn-secondary w-full" [disabled]="saving() || !zoneId()"><vf-icon name="plus" class="size-4" /> Anadir mesa a esta sala</button>
            @if (!zoneId()) { <p class="text-xs text-zinc-500">Crea primero una sala en la pestana "Salas".</p> }
          </form>

          @if (selectedEdit(); as t) {
            <form class="space-y-3 border-t border-zinc-800 pt-4" vfReveal (ngSubmit)="saveTable()">
              <p class="text-xs font-medium text-zinc-400">Mesa seleccionada · {{ zoneOf(t.id) }}</p>
              <div><label class="vf-label" for="tZone">Sala</label>
                <select id="tZone" name="tZone" class="vf-input" [(ngModel)]="moveToZone">
                  @for (z of zones(); track z.id) { <option [value]="z.id">{{ z.name }}</option> }
                </select></div>
              <div class="grid grid-cols-2 gap-3">
                <div class="col-span-2"><label class="vf-label" for="tName">Nombre</label><input id="tName" name="tName" class="vf-input" maxlength="60" [(ngModel)]="form.name" required /></div>
                <div><label class="vf-label" for="tCap">Capacidad</label><input id="tCap" name="tCap" type="number" min="1" max="100" class="vf-input" [(ngModel)]="form.capacity" /></div>
                <div><label class="vf-label" for="tShape">Forma</label>
                  <select id="tShape" name="tShape" class="vf-input" [(ngModel)]="form.shape" (ngModelChange)="reshape($event)"><option value="ROUND">Redonda</option><option value="RECT">Rectangular</option></select></div>
                <div><label class="vf-label" for="tPrice">Precio reserva + paquete</label><input id="tPrice" name="tPrice" type="number" min="0" step="0.01" class="vf-input" [(ngModel)]="form.depositAmount" /></div>
                <div><label class="vf-label" for="tMin">Consumo minimo</label><input id="tMin" name="tMin" type="number" min="0" step="0.01" class="vf-input" [(ngModel)]="form.minSpend" /></div>
              </div>
              <label class="flex items-center gap-2 text-sm text-zinc-300"><input type="checkbox" name="tActive" class="accent-brand-500" [(ngModel)]="form.active" /> Se puede reservar</label>
              <div class="flex flex-wrap gap-2">
                <button type="submit" class="vf-btn-primary vf-btn-sm" [disabled]="saving() || !form.name.trim()">Guardar mesa</button>
                <button type="button" class="vf-btn-ghost vf-btn-sm" (click)="unplace(t.id)">Quitar del plano</button>
                <button type="button" class="vf-btn-danger vf-btn-sm" [disabled]="saving()" (click)="deleteTable(t.id)">Borrar</button>
              </div>
            </form>

            <!-- Paquete VIP -->
            <div class="space-y-2 border-t border-zinc-800 pt-4">
              <p class="text-xs font-medium text-zinc-400">Paquete incluido en el precio</p>
              @if (!canCatalog) {
                <p class="text-xs text-zinc-500">Necesitas permiso de catalogo TPV para editar el paquete.</p>
              } @else {
                @for (line of packageDraft(); track $index; let i = $index) {
                  <div class="flex items-center gap-2">
                    <select class="vf-input" [ngModel]="line.variantId" (ngModelChange)="setPackageLine(i, $event, line.quantity)" [attr.aria-label]="'Producto ' + (i + 1)">
                      @for (o of variantOptions(); track o.id) { <option [value]="o.id">{{ o.label }}</option> }
                    </select>
                    <input type="number" min="1" max="99" class="vf-input w-16" [ngModel]="line.quantity" (ngModelChange)="setPackageLine(i, line.variantId, $event)" aria-label="Unidades" />
                    <button type="button" class="vf-btn-icon size-8 shrink-0" (click)="removePackageLine(i)" aria-label="Quitar"><vf-icon name="x" class="size-4" /></button>
                  </div>
                }
                <div class="flex gap-2">
                  <button type="button" class="vf-btn-ghost vf-btn-sm" [disabled]="variantOptions().length === 0" (click)="addPackageLine()"><vf-icon name="plus" class="size-3.5" /> Producto</button>
                  <button type="button" class="vf-btn-secondary vf-btn-sm" [disabled]="saving()" (click)="savePackage()">Guardar paquete</button>
                </div>
                @if (variantOptions().length === 0) { <p class="text-xs text-zinc-500">Crea productos en Catalogo TPV (categoria "Botellas VIP").</p> }
              }
            </div>
          } @else {
            <p class="border-t border-zinc-800 pt-4 text-sm text-zinc-500">Selecciona una mesa para editar su nombre, precio, forma y paquete.</p>
          }
        </aside>
      </div>
    }
  `,
})
export class VipPlanPage implements OnInit {
  private readonly vip = inject(VipService);
  private readonly reservations = inject(ReservationsService);
  private readonly venuesService = inject(VenuesService);
  private readonly pos = inject(PosService);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);

  protected readonly canEdit = this.auth.hasPermission(Permission.VENUE_MANAGE);
  protected readonly canCatalog = this.auth.hasPermission(Permission.POS_MANAGE);
  protected readonly legend = (Object.keys(TABLE_STATUS) as VipTableStatus[])
    .filter((k) => k !== 'INACTIVE')
    .map((key) => ({ key, ...TABLE_STATUS[key] }));

  protected readonly venues = signal<Venue[]>([]);
  protected readonly venueId = signal('');
  protected readonly mode = signal<'live' | 'edit' | 'shape' | 'rooms'>('live');
  /** Sala que se esta viendo. */
  protected readonly zoneId = signal('');
  protected readonly roomColors = Object.keys(ROOM_COLORS) as RoomColor[];
  /** Fiestas del local y la elegida (vacio = la que esta en curso, si la hay). */
  protected readonly parties = signal<VipParty[]>([]);
  protected readonly partyId = signal('');
  protected readonly showPast = signal(false);
  protected readonly currentParty = computed(() => this.parties().find((p) => p.sessionId === this.partyId()) ?? null);
  protected roomForm = this.emptyRoom();
  protected moveToZone = '';
  /** Forma guardada del local (null = aun sin disenar) y borrador del editor de forma. */
  protected readonly layout = signal<FloorLayout | null>(null);
  protected readonly shapeDraft = signal<FloorLayout>(defaultLayout());
  protected readonly shapeDirty = signal(false);
  protected readonly selectedElement = signal<number | null>(null);
  protected readonly presets = SHAPE_PRESETS;
  protected readonly elementTypes = Object.keys(ELEMENT_STYLE) as FloorElementType[];
  protected readonly map = signal<VipMap | null>(null);
  protected readonly zones = signal<VipZone[]>([]);
  protected readonly products = signal<Product[]>([]);
  private readonly vipCategoryIds = signal<Set<string>>(new Set());
  /** Lo que se puede pedir para una mesa: botellas VIP y servicio (el backend aplica la misma regla). */
  protected readonly vipProducts = computed(() => this.products().filter((p) => this.vipCategoryIds().has(p.categoryId)));
  protected readonly selectedId = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Geometria editada pendiente de guardar (id -> posicion). */
  private readonly edits = signal<Map<string, TablePosition>>(new Map());
  protected readonly dirty = computed(() => this.edits().size > 0);
  protected readonly packageDraft = signal<{ variantId: string; quantity: number }[]>([]);
  protected form = { name: '', capacity: 4, depositAmount: 0, minSpend: 0, shape: 'ROUND' as VipTableShape, active: true };

  // ---------------- En vivo ----------------
  /** Salas del local (del plano en vivo o, en edicion, de la configuracion). */
  protected readonly rooms = computed(() =>
    this.mode() === 'live' ? (this.map()?.zones ?? []) : this.zones().map((z) => ({ id: z.id, name: z.name, active: z.active, color: z.color as RoomColor, capacity: z.capacity })),
  );
  protected readonly currentRoom = computed(() => this.rooms().find((r) => r.id === this.zoneId()) ?? null);
  private readonly roomTables = computed(() => (this.map()?.tables ?? []).filter((t) => t.zoneId === this.zoneId()));

  protected readonly liveTables = computed<PlanTable[]>(() =>
    this.roomTables().map((t) => ({
      ...t,
      badge: t.reservation?.openOrders || undefined,
      subtitle: t.reservation ? t.reservation.contactName.split(' ')[0] : `${t.capacity} pers.`,
    })),
  );
  protected readonly unplacedLive = computed(() => this.roomTables().filter((t) => t.posX === null));
  protected readonly selectedLive = computed(() => this.map()?.tables.find((t) => t.id === this.selectedId()) ?? null);
  protected readonly counts = computed(() => {
    const c: Partial<Record<VipTableStatus, number>> = {};
    this.roomTables().forEach((t) => (c[t.status] = (c[t.status] ?? 0) + 1));
    return c;
  });
  protected readonly attention = computed(() =>
    (this.map()?.tables ?? []).filter((t) => t.status === 'SERVICE_REQUESTED' || t.status === 'PENDING_PAYMENT'),
  );

  // ---------------- Editor ----------------
  private readonly allTables = computed(() => this.zones().filter((z) => z.id === this.zoneId()).flatMap((z) => z.tables));
  protected readonly editTables = computed<PlanTable[]>(() => {
    const edits = this.edits();
    return this.allTables().map((t) => ({ ...t, ...(edits.get(t.id) ?? {}) }));
  });
  protected readonly unplacedEdit = computed(() => this.editTables().filter((t) => t.posX === null));
  protected readonly selectedEdit = computed(() => this.editTables().find((t) => t.id === this.selectedId()) ?? null);
  protected readonly variantOptions = computed(() =>
    this.products()
      .filter((p) => p.active)
      .flatMap((p) => p.variants.filter((v) => v.active && v.id).map((v) => ({ id: v.id!, label: `${p.name} · ${v.name} (${v.price} EUR)` }))),
  );

  constructor() {
    const timer = setInterval(() => {
      if (this.mode() === 'live' && this.venueId()) this.reload();
    }, LIVE_REFRESH_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  ngOnInit(): void {
    this.venuesService.list().subscribe({
      next: (v) => {
        this.venues.set(v);
        this.loading.set(false);
        if (v.length) this.selectVenue(v[0].id);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
    if (this.canCatalog) {
      this.pos.products().subscribe({ next: (p) => this.products.set(p), error: () => this.products.set([]) });
      this.pos.categories().subscribe({
        next: (c) => this.vipCategoryIds.set(new Set(c.filter((x) => x.kind === 'VIP_BOTTLE' || x.kind === 'SERVICE').map((x) => x.id))),
        error: () => this.vipCategoryIds.set(new Set()),
      });
    }
  }

  protected selectVenue(id: string): void {
    this.venueId.set(id);
    this.zoneId.set('');
    this.partyId.set('');
    this.selectedId.set(null);
    this.edits.set(new Map());
    this.shapeDirty.set(false);
    this.loadParties();
    this.loadZones();
  }

  protected selectRoom(id: string): void {
    if (id === this.zoneId()) return;
    if ((this.dirty() || this.shapeDirty()) && !confirm('Hay cambios sin guardar. ¿Descartarlos?')) return;
    this.zoneId.set(id);
    this.selectedId.set(null);
    this.edits.set(new Map());
    this.shapeDirty.set(false);
    this.selectedElement.set(null);
    this.loadLayout();
  }

  protected roomDot(color: string): string {
    return ROOM_COLORS[color as RoomColor] ?? ROOM_COLORS.violet;
  }

  protected tablesIn(zoneId: string): number {
    return (this.map()?.tables ?? []).filter((t) => t.zoneId === zoneId).length;
  }

  protected occupiedIn(zoneId: string): number {
    return (this.map()?.tables ?? []).filter((t) => t.zoneId === zoneId && t.reservation).length;
  }

  /** Reservas de la fiesta en la sala elegida: activas e historial (liberadas, canceladas...). */
  protected readonly partyBookings = computed(() =>
    this.roomTables().flatMap((t) => [
      ...(t.reservation ? [{ id: t.reservation.id, tableId: t.id, tableName: t.name, contactName: t.reservation.contactName, guests: t.reservation.guests, status: t.reservation.status as string }] : []),
      ...t.history.map((h) => ({ id: h.id, tableId: t.id, tableName: t.name, contactName: h.contactName, guests: h.guests, status: h.status as string })),
    ]),
  );

  private loadParties(): void {
    this.vip.parties(this.venueId(), this.showPast()).subscribe({
      next: (list) => {
        this.parties.set(list);
        if (!list.some((p) => p.sessionId === this.partyId())) {
          // Por defecto, la fiesta en curso o la proxima.
          this.partyId.set((list.find((p) => p.now) ?? list.find((p) => new Date(p.startsAt).getTime() > Date.now()) ?? list[0])?.sessionId ?? '');
        }
        this.reload();
      },
      error: () => this.parties.set([]),
    });
  }

  protected selectParty(id: string): void {
    this.partyId.set(id);
    this.selectedId.set(null);
    this.reload();
  }

  protected togglePast(value: boolean): void {
    this.showPast.set(value);
    this.loadParties();
  }

  private loadLayout(): void {
    if (!this.zoneId()) {
      this.layout.set(null);
      this.shapeDraft.set(defaultLayout());
      return;
    }
    this.vip.floorPlan(this.zoneId()).subscribe({
      next: (l) => {
        this.layout.set(l);
        this.shapeDraft.set(structuredClone(l ?? defaultLayout()));
      },
      error: () => this.layout.set(null),
    });
  }

  // ---------------- Forma del local ----------------
  protected elementLabel(t: FloorElementType): string {
    return ELEMENT_STYLE[t].label || 'Columna';
  }

  private updateShape(fn: (l: FloorLayout) => FloorLayout): void {
    this.shapeDraft.update((l) => fn(structuredClone(l)));
    this.shapeDirty.set(true);
  }

  protected applyPreset(p: ShapePreset): void {
    this.updateShape((l) => ({ ...l, cells: presetCells(p, l.cols, l.rows) }));
  }

  protected resizeGrid(cols: number | string, rows: number | string): void {
    const c = Math.min(40, Math.max(8, Number(cols) || 20));
    const r = Math.min(30, Math.max(6, Number(rows) || 12));
    this.updateShape((l) => ({ ...l, cells: resizeCells(l, c, r), cols: c, rows: r }));
  }

  protected paintCell(e: { index: number; floor: boolean }): void {
    this.updateShape((l) => ({ ...l, cells: l.cells.slice(0, e.index) + (e.floor ? '1' : '0') + l.cells.slice(e.index + 1) }));
  }

  protected addElement(type: FloorElementType): void {
    const small = type === 'COLUMN';
    this.updateShape((l) => ({
      ...l,
      elements: [...l.elements, { type, label: ELEMENT_STYLE[type].label || null, x: 40, y: 40, w: small ? 3 : 18, h: small ? 5 : 10 }],
    }));
    this.selectedElement.set(this.shapeDraft().elements.length - 1);
  }

  protected moveElement(e: { index: number; x: number; y: number; w: number; h: number }): void {
    this.updateShape((l) => ({ ...l, elements: l.elements.map((el, i) => (i === e.index ? { ...el, x: e.x, y: e.y, w: e.w, h: e.h } : el)) }));
  }

  protected setElementLabel(label: string): void {
    const index = this.selectedElement();
    if (index === null) return;
    this.updateShape((l) => ({ ...l, elements: l.elements.map((el, i) => (i === index ? { ...el, label: label.slice(0, 40) || null } : el)) }));
  }

  protected removeElement(): void {
    const index = this.selectedElement();
    if (index === null) return;
    this.updateShape((l) => ({ ...l, elements: l.elements.filter((_, i) => i !== index) }));
    this.selectedElement.set(null);
  }

  protected saveShape(): void {
    this.saving.set(true);
    this.vip.saveFloorPlan(this.zoneId(), this.shapeDraft()).subscribe({
      next: (l) => {
        this.layout.set(l);
        this.shapeDirty.set(false);
        this.saving.set(false);
        this.toast.success('Forma de la sala guardada');
      },
      error: (err) => this.fail(err),
    });
  }

  protected setMode(mode: 'live' | 'edit' | 'shape' | 'rooms'): void {
    if (this.mode() === mode) return;
    if ((this.dirty() || this.shapeDirty()) && !confirm('Hay cambios sin guardar. ¿Descartarlos?')) return;
    this.edits.set(new Map());
    this.shapeDirty.set(false);
    this.shapeDraft.set(structuredClone(this.layout() ?? defaultLayout()));
    this.selectedElement.set(null);
    this.mode.set(mode);
    this.selectedId.set(null);
    this.loadZones();
    if (mode === 'live') this.reload();
  }

  protected reload(): void {
    if (!this.venueId()) return;
    this.vip.map(this.venueId(), this.partyId() || null).subscribe({
      next: (m) => {
        this.map.set(m);
        this.error.set(null);
      },
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  protected statusLabel(s: VipTableStatus): string {
    return TABLE_STATUS[s].label;
  }

  // ---------------- Acciones del editor ----------------
  private loadZones(): void {
    this.reservations.zones(this.venueId()).subscribe({
      next: (z) => {
        this.zones.set(z);
        // Al cambiar de local (o si la sala ya no existe) se abre la primera sala activa.
        if (!z.some((zone) => zone.id === this.zoneId())) {
          this.zoneId.set((z.find((zone) => zone.active) ?? z[0])?.id ?? '');
          this.loadLayout();
        }
        this.syncForm();
      },
      error: (err) => this.toast.error(errorMessage(err)),
    });
  }

  protected zoneOf(tableId: string): string {
    return this.zones().find((z) => z.tables.some((t) => t.id === tableId))?.name ?? '';
  }

  private geometry(id: string): TablePosition {
    const t = this.editTables().find((x) => x.id === id)!;
    return { id, posX: t.posX, posY: t.posY, width: t.width, height: t.height, shape: t.shape };
  }

  private setEdit(position: TablePosition): void {
    const next = new Map(this.edits());
    next.set(position.id, position);
    this.edits.set(next);
  }

  protected move(g: TableGeometry): void {
    this.setEdit({ ...this.geometry(g.id), posX: g.posX, posY: g.posY, width: g.width, height: g.height });
  }

  protected place(id: string): void {
    this.setEdit({ ...this.geometry(id), posX: 45, posY: 40 });
    this.selectedId.set(id);
    this.syncForm();
  }

  protected unplace(id: string): void {
    this.setEdit({ ...this.geometry(id), posX: null, posY: null });
  }

  /** Las redondas mantienen proporcion 1:1 en pantalla (el lienzo es 16:10). */
  protected reshape(shape: VipTableShape): void {
    const id = this.selectedId();
    if (!id) return;
    const g = this.geometry(id);
    this.setEdit({ ...g, shape, height: shape === 'ROUND' ? Math.min(40, g.width * 1.6) : g.height });
  }

  protected saveLayout(): void {
    this.saving.set(true);
    this.vip.saveLayout(this.venueId(), [...this.edits().values()]).subscribe({
      next: (zones) => {
        this.zones.set(zones);
        this.edits.set(new Map());
        this.saving.set(false);
        this.toast.success('Plano guardado');
      },
      error: (err) => this.fail(err),
    });
  }

  /** Copia los datos de la mesa seleccionada al formulario lateral. */
  private syncForm(): void {
    const t = this.allTables().find((x) => x.id === this.selectedId());
    if (!t) return;
    this.form = { name: t.name, capacity: t.capacity, depositAmount: t.depositAmount, minSpend: t.minSpend, shape: t.shape, active: t.active };
    this.moveToZone = this.zoneId();
    this.packageDraft.set(t.packageItems.map((p) => ({ variantId: p.variantId, quantity: p.quantity })));
  }

  protected saveTable(): void {
    const id = this.selectedId()!;
    const g = this.geometry(id);
    this.saving.set(true);
    const zoneId = this.moveToZone && this.moveToZone !== this.zoneId() ? this.moveToZone : undefined;
    this.reservations
      .updateTable(id, { ...this.form, name: this.form.name.trim(), shape: g.shape, width: g.width, height: g.height, zoneId })
      .subscribe({ next: () => this.done(zoneId ? 'Mesa movida a otra sala' : 'Mesa guardada'), error: (err) => this.fail(err) });
  }

  protected addTable(): void {
    const zone = this.zones().find((z) => z.id === this.zoneId());
    if (!zone) return;
    const name = `Mesa ${this.zones().flatMap((z) => z.tables).length + 1}`;
    this.saving.set(true);
    this.reservations
      .createTable(zone.id, { name, capacity: 6, minSpend: 0, depositAmount: 0, active: true, shape: 'ROUND', width: 8, height: 12.8 })
      .subscribe({
        next: (z) => {
          const created = z.tables.find((t) => t.name === name);
          this.done('Mesa anadida: colocala en el plano');
          if (created) this.selectedId.set(created.id);
        },
        error: (err) => this.fail(err),
      });
  }

  // ---------------- Salas ----------------
  private emptyRoom() {
    return { id: '', name: '', description: '', capacity: null as number | null, sortOrder: 0, color: 'violet' as RoomColor, active: true };
  }

  protected newRoom(): void {
    this.roomForm = { ...this.emptyRoom(), sortOrder: this.zones().length };
  }

  protected editRoom(z: VipZone): void {
    this.roomForm = { id: z.id, name: z.name, description: z.description ?? '', capacity: z.capacity, sortOrder: z.sortOrder, color: (z.color as RoomColor) ?? 'violet', active: z.active };
    // El formulario esta al lado de la lista: en pantallas estrechas queda debajo, asi que lo traemos a la vista.
    setTimeout(() => document.getElementById('rName')?.scrollIntoView({ block: 'center', behavior: 'smooth' }));
    setTimeout(() => (document.getElementById('rName') as HTMLInputElement | null)?.focus({ preventScroll: true }), 50);
  }

  protected openRoomPlan(zoneId: string): void {
    this.mode.set('live');
    this.zoneId.set('');
    this.selectRoom(zoneId);
    this.reload();
  }

  protected saveRoom(): void {
    const f = this.roomForm;
    const payload = { name: f.name.trim(), description: f.description.trim() || null, active: f.active, sortOrder: Number(f.sortOrder) || 0,
      capacity: f.capacity ? Number(f.capacity) : null, color: f.color };
    this.saving.set(true);
    const request = f.id ? this.reservations.updateZone(f.id, payload) : this.reservations.createZone(this.venueId(), payload);
    request.subscribe({
      next: (z) => {
        this.roomForm = { ...this.emptyRoom(), sortOrder: this.zones().length + 1 };
        if (!this.zoneId()) this.zoneId.set(z.id);
        this.done(f.id ? 'Sala guardada' : 'Sala creada');
        this.reload();
      },
      error: (err) => this.fail(err),
    });
  }

  protected deleteRoom(): void {
    const id = this.roomForm.id;
    if (!id || !confirm('¿Borrar la sala y sus mesas? Solo es posible si ninguna mesa tiene reservas.')) return;
    this.saving.set(true);
    this.vip.deleteZone(id).subscribe({
      next: () => {
        if (this.zoneId() === id) this.zoneId.set('');
        this.newRoom();
        this.done('Sala borrada');
        this.reload();
      },
      error: (err) => this.fail(err),
    });
  }

  protected deleteTable(id: string): void {
    if (!confirm('¿Borrar esta mesa? Si tiene reservas, desactivala en su lugar.')) return;
    this.saving.set(true);
    this.vip.deleteTable(id).subscribe({
      next: () => {
        this.selectedId.set(null);
        const next = new Map(this.edits());
        next.delete(id);
        this.edits.set(next);
        this.done('Mesa borrada');
      },
      error: (err) => this.fail(err),
    });
  }

  protected addPackageLine(): void {
    const first = this.variantOptions()[0];
    if (first) this.packageDraft.update((l) => [...l, { variantId: first.id, quantity: 1 }]);
  }

  protected setPackageLine(index: number, variantId: string, quantity: number): void {
    this.packageDraft.update((l) => l.map((x, i) => (i === index ? { variantId, quantity: Math.max(1, Math.min(99, Number(quantity) || 1)) } : x)));
  }

  protected removePackageLine(index: number): void {
    this.packageDraft.update((l) => l.filter((_, i) => i !== index));
  }

  protected savePackage(): void {
    this.saving.set(true);
    this.vip.setPackage(this.selectedId()!, this.packageDraft()).subscribe({
      next: () => this.done('Paquete guardado'),
      error: (err) => this.fail(err),
    });
  }

  private done(message: string): void {
    this.saving.set(false);
    this.toast.success(message);
    this.loadZones();
  }

  private fail(err: unknown): void {
    this.saving.set(false);
    this.toast.error(errorMessage(err));
  }

  protected readonly selectTable = (id: string) => {
    this.selectedId.set(id);
    this.syncForm();
  };
}
