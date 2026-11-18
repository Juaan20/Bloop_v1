import { Component, ElementRef, computed, input, output, signal, viewChild } from '@angular/core';
import { ELEMENT_STYLE, FloorLayout, defaultLayout } from './floor-layout';
import { TABLE_STATUS, VipTableShape, VipTableStatus } from './vip.models';

/** Mesa que pinta el plano (modo en vivo y editor). */
export interface PlanTable {
  id: string;
  name: string;
  capacity: number;
  posX: number | null;
  posY: number | null;
  width: number;
  height: number;
  shape: VipTableShape;
  status?: VipTableStatus;
  /** Numero pequeno en la esquina (p. ej. pedidos pendientes). */
  badge?: number;
  subtitle?: string;
}

export interface TableGeometry {
  id: string;
  posX: number;
  posY: number;
  width: number;
  height: number;
}

/** Que se esta arrastrando: una mesa (id) o un elemento del local (indice). */
type DragTarget = { kind: 'table'; id: string } | { kind: 'element'; index: number };

/**
 * Plano de un local. Coordenadas en % del lienzo (16:10), asi se adapta a cualquier pantalla.
 * Cada local tiene su propia forma (celdas pintadas: en L, en U, libre...) y sus elementos.
 * - "live": mesas coloreadas por estado; se seleccionan con clic o teclado.
 * - "edit": se mueven y redimensionan las mesas (raton, tactil o flechas).
 * - "shape": se pintan las celdas del local y se colocan barra, escenario, DJ, pista...
 * El componente no guarda nada: emite los cambios y la pagina decide cuando guardarlos.
 */
@Component({
  selector: 'vf-floor-plan',
  template: `
    <div #stage class="relative aspect-[16/10] w-full touch-none overflow-hidden rounded-2xl border border-zinc-800 bg-[repeating-linear-gradient(135deg,rgb(255_255_255/0.025)_0_6px,transparent_6px_14px)] select-none"
         role="group" [attr.aria-label]="mode() === 'shape' ? 'Editor de la forma del local' : mode() === 'edit' ? 'Editor de mesas' : 'Plano VIP'"
         (pointerdown)="onStageDown($event)" (pointermove)="onMove($event)" (pointerup)="endDrag()" (pointercancel)="endDrag()" (pointerleave)="endDrag()">
      <!-- Suelo del local: celdas pintadas -->
      <svg class="pointer-events-none absolute inset-0 size-full" [attr.viewBox]="'0 0 ' + layoutOrDefault().cols + ' ' + layoutOrDefault().rows" preserveAspectRatio="none" aria-hidden="true">
        @for (c of floorCells(); track c.i) {
          <rect [attr.x]="c.x" [attr.y]="c.y" width="1.02" height="1.02" fill="var(--color-zinc-800)" fill-opacity="0.7" />
        }
        @if (mode() === 'shape') {
          @for (c of voidCells(); track c.i) {
            <rect [attr.x]="c.x + 0.08" [attr.y]="c.y + 0.08" width="0.84" height="0.84" fill="none" stroke="var(--color-zinc-800)" stroke-width="0.04" />
          }
          @for (c of floorCells(); track c.i) {
            <rect [attr.x]="c.x" [attr.y]="c.y" width="1" height="1" fill="none" stroke="var(--color-zinc-700)" stroke-width="0.03" />
          }
        }
      </svg>

      <!-- Elementos fijos del local -->
      @for (e of placedElements(); track $index; let i = $index) {
        <div class="absolute grid place-items-center overflow-hidden border text-center text-[clamp(8px,0.9vw,11px)] font-medium tracking-wide uppercase"
             [class]="elementCss(e.type, i)"
             [class.rounded-full]="e.type === 'COLUMN'" [class.rounded-lg]="e.type !== 'COLUMN'"
             [class.pointer-events-none]="mode() !== 'shape'"
             [style.left.%]="e.x" [style.top.%]="e.y" [style.width.%]="e.w" [style.height.%]="e.h"
             [attr.role]="mode() === 'shape' ? 'button' : null" [attr.tabindex]="mode() === 'shape' ? 0 : null"
             [attr.aria-label]="mode() === 'shape' ? (e.label || defaultLabel(e.type)) + '. Flechas para mover' : null"
             (pointerdown)="startDrag($event, { kind: 'element', index: i }, 'move', e.x, e.y, e.w, e.h)"
             (keydown)="onKey($event, { kind: 'element', index: i }, e.x, e.y, e.w, e.h)">
          <span class="px-1">{{ e.label ?? defaultLabel(e.type) }}</span>
          @if (mode() === 'shape' && selectedElement() === i) {
            <span class="absolute right-0 bottom-0 size-3 cursor-se-resize bg-white" aria-hidden="true"
                  (pointerdown)="startDrag($event, { kind: 'element', index: i }, 'resize', e.x, e.y, e.w, e.h)"></span>
          }
        </div>
      }

      <!-- Mesas -->
      @for (t of placed(); track t.id) {
        <button type="button"
                class="group absolute flex flex-col items-center justify-center text-center ring-2 transition-[box-shadow,background-color] duration-150 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
                [class]="tableClasses(t)"
                [class.rounded-full]="t.shape === 'ROUND'" [class.rounded-xl]="t.shape === 'RECT'"
                [class.pointer-events-none]="mode() === 'shape'" [class.opacity-40]="mode() === 'shape'"
                [style.left.%]="t.x" [style.top.%]="t.y" [style.width.%]="t.width"
                [style.height.%]="t.shape === 'ROUND' ? null : t.height"
                [style.aspect-ratio]="t.shape === 'ROUND' ? '1 / 1' : null"
                [attr.tabindex]="mode() === 'shape' ? -1 : 0"
                [attr.aria-pressed]="selectedId() === t.id"
                [attr.aria-label]="ariaLabel(t)"
                (pointerdown)="startDrag($event, { kind: 'table', id: t.id }, 'move', t.x, t.y, t.width, t.height)"
                (click)="selected.emit(t.id)"
                (keydown)="onKey($event, { kind: 'table', id: t.id }, t.x, t.y, t.width, t.height)">
          <span class="max-w-full truncate px-1 text-[clamp(9px,1.1vw,13px)] font-semibold text-white">{{ t.name }}</span>
          <span class="text-[clamp(8px,0.85vw,11px)] text-zinc-300/80">{{ t.subtitle ?? t.capacity + ' pers.' }}</span>
          @if (t.badge) {
            <span class="absolute -top-1.5 -right-1.5 grid size-5 place-items-center rounded-full bg-rose-500 text-[10px] font-bold text-white ring-2 ring-zinc-950">{{ t.badge }}</span>
          }
          @if (mode() === 'edit' && selectedId() === t.id) {
            <span class="absolute -right-1.5 -bottom-1.5 size-3.5 cursor-se-resize rounded-sm border-2 border-zinc-950 bg-white"
                  aria-hidden="true" (pointerdown)="startDrag($event, { kind: 'table', id: t.id }, 'resize', t.x, t.y, t.width, t.height)"></span>
          }
        </button>
      }
      @if (placed().length === 0 && mode() === 'live') {
        <p class="absolute inset-x-0 bottom-4 text-center text-sm text-zinc-500">Este local aun no tiene mesas colocadas en el plano.</p>
      }
    </div>
  `,
})
export class FloorPlan {
  readonly tables = input.required<PlanTable[]>();
  /** Forma del local (null = rectangulo por defecto). */
  readonly layout = input<FloorLayout | null>(null);
  readonly mode = input<'live' | 'edit' | 'shape'>('live');
  readonly selectedId = input<string | null>(null);
  readonly selectedElement = input<number | null>(null);
  readonly selected = output<string>();
  /** Fin de un arrastre o redimensionado de mesa (y cada paso de teclado). */
  readonly changed = output<TableGeometry>();
  /** Modo forma: celda pintada (indice) y si queda como suelo. */
  readonly paint = output<{ index: number; floor: boolean }>();
  readonly elementSelected = output<number>();
  readonly elementChanged = output<{ index: number; x: number; y: number; w: number; h: number }>();

  private readonly stage = viewChild.required<ElementRef<HTMLDivElement>>('stage');
  private readonly draft = signal<{ target: DragTarget; x: number; y: number; w: number; h: number } | null>(null);
  private drag: { target: DragTarget; kind: 'move' | 'resize'; sx: number; sy: number; base: { x: number; y: number; w: number; h: number }; moved: boolean } | null = null;
  /** Pintando celdas: valor que se aplica mientras se arrastra (suelo o fuera). */
  private painting: boolean | null = null;

  protected readonly layoutOrDefault = computed(() => this.layout() ?? defaultLayout());
  private readonly cells = computed(() => {
    const l = this.layoutOrDefault();
    return [...l.cells].map((v, i) => ({ i, x: i % l.cols, y: Math.floor(i / l.cols), floor: v === '1' }));
  });
  protected readonly floorCells = computed(() => this.cells().filter((c) => c.floor));
  protected readonly voidCells = computed(() => this.cells().filter((c) => !c.floor));

  protected readonly placedElements = computed(() => {
    const d = this.draft();
    return this.layoutOrDefault().elements.map((e, i) =>
      d && d.target.kind === 'element' && d.target.index === i ? { ...e, x: d.x, y: d.y, w: d.w, h: d.h } : e,
    );
  });

  protected readonly placed = computed(() => {
    const d = this.draft();
    return this.tables()
      .filter((t) => t.posX !== null && t.posY !== null)
      .map((t) => {
        const own = d && d.target.kind === 'table' && d.target.id === t.id;
        return { ...t, x: own ? d.x : t.posX!, y: own ? d.y : t.posY!, width: own ? d.w : t.width, height: own ? d.h : t.height };
      });
  });

  protected defaultLabel(type: keyof typeof ELEMENT_STYLE): string {
    return ELEMENT_STYLE[type].label;
  }

  protected elementCss(type: keyof typeof ELEMENT_STYLE, index: number): string {
    const selected = this.mode() === 'shape' && this.selectedElement() === index;
    return `${ELEMENT_STYLE[type].css} ${this.mode() === 'shape' ? 'cursor-grab z-10' : ''} ${selected ? 'ring-2 ring-white z-20' : ''}`;
  }

  protected tableClasses(t: PlanTable): string {
    const selected = this.selectedId() === t.id;
    if (this.mode() !== 'live') {
      return `${selected && this.mode() === 'edit' ? 'bg-brand-500/30 ring-brand-300 z-10' : 'bg-zinc-800/90 ring-zinc-600 hover:ring-zinc-400'} cursor-grab active:cursor-grabbing`;
    }
    const s = TABLE_STATUS[t.status ?? 'AVAILABLE'];
    const pulse = t.status === 'SERVICE_REQUESTED' ? ' animate-pulse' : '';
    return `${s.fill} ${s.ring}${pulse} cursor-pointer hover:brightness-125 ${selected ? 'z-10 shadow-[0_0_0_4px_rgb(255_255_255/0.25)]' : ''}`;
  }

  protected ariaLabel(t: PlanTable): string {
    const status = t.status ? `, ${TABLE_STATUS[t.status].label}` : '';
    const hint = this.mode() === 'edit' ? '. Flechas para mover' : '';
    return `${t.name}, ${t.capacity} personas${status}${hint}`;
  }

  // ---------------- Pintar celdas (modo forma) ----------------
  protected onStageDown(event: PointerEvent): void {
    if (this.mode() !== 'shape' || this.drag) return;
    const index = this.cellAt(event);
    if (index === null) return;
    this.painting = this.layoutOrDefault().cells[index] !== '1';
    this.paint.emit({ index, floor: this.painting });
  }

  private cellAt(event: PointerEvent): number | null {
    const rect = this.stage().nativeElement.getBoundingClientRect();
    const l = this.layoutOrDefault();
    const c = Math.floor(((event.clientX - rect.left) / rect.width) * l.cols);
    const r = Math.floor(((event.clientY - rect.top) / rect.height) * l.rows);
    return c < 0 || r < 0 || c >= l.cols || r >= l.rows ? null : r * l.cols + c;
  }

  // ---------------- Arrastrar mesas y elementos ----------------
  protected startDrag(event: PointerEvent, target: DragTarget, kind: 'move' | 'resize', x: number, y: number, w: number, h: number): void {
    const allowed = (target.kind === 'table' && this.mode() === 'edit') || (target.kind === 'element' && this.mode() === 'shape');
    if (!allowed) return;
    event.stopPropagation();
    (event.target as HTMLElement).setPointerCapture?.(event.pointerId);
    if (target.kind === 'table') this.selected.emit(target.id);
    else this.elementSelected.emit(target.index);
    this.drag = { target, kind, sx: event.clientX, sy: event.clientY, base: { x, y, w, h }, moved: false };
  }

  protected onMove(event: PointerEvent): void {
    if (this.painting !== null && !this.drag) {
      const index = this.cellAt(event);
      if (index !== null && (this.layoutOrDefault().cells[index] === '1') !== this.painting) {
        this.paint.emit({ index, floor: this.painting });
      }
      return;
    }
    if (!this.drag) return;
    const rect = this.stage().nativeElement.getBoundingClientRect();
    const dx = ((event.clientX - this.drag.sx) / rect.width) * 100;
    const dy = ((event.clientY - this.drag.sy) / rect.height) * 100;
    if (Math.abs(dx) + Math.abs(dy) > 0.3) this.drag.moved = true;
    const b = this.drag.base;
    const g = this.drag.kind === 'move' ? { ...b, x: b.x + dx, y: b.y + dy } : { ...b, w: b.w + dx, h: b.h + dy };
    this.draft.set({ target: this.drag.target, ...this.clamp(g, this.drag.target.kind) });
  }

  protected endDrag(): void {
    this.painting = null;
    const d = this.draft();
    if (this.drag && d && this.drag.moved) this.emitGeometry(d.target, d);
    this.drag = null;
    this.draft.set(null);
  }

  protected onKey(event: KeyboardEvent, target: DragTarget, x: number, y: number, w: number, h: number): void {
    const allowed = (target.kind === 'table' && this.mode() === 'edit') || (target.kind === 'element' && this.mode() === 'shape');
    if (!allowed) return;
    if (target.kind === 'element' && (event.key === 'Enter' || event.key === ' ')) {
      event.preventDefault();
      this.elementSelected.emit(target.index);
      return;
    }
    const step = event.shiftKey ? 5 : 1;
    const moves: Record<string, [number, number]> = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] };
    const m = moves[event.key];
    if (!m) return;
    event.preventDefault();
    this.emitGeometry(target, this.clamp({ x: x + m[0], y: y + m[1], w, h }, target.kind));
  }

  private emitGeometry(target: DragTarget, g: { x: number; y: number; w: number; h: number }): void {
    const r = (n: number) => Math.round(n * 100) / 100;
    if (target.kind === 'table') {
      this.changed.emit({ id: target.id, posX: r(g.x), posY: r(g.y), width: r(g.w), height: r(g.h) });
    } else {
      this.elementChanged.emit({ index: target.index, x: r(g.x), y: r(g.y), w: r(g.w), h: r(g.h) });
    }
  }

  /** Mantiene el objeto dentro del lienzo y con un tamano razonable. */
  private clamp(g: { x: number; y: number; w: number; h: number }, kind: 'table' | 'element') {
    const [min, max] = kind === 'table' ? [3, 40] : [1, 100];
    const w = Math.min(max, Math.max(min, g.w));
    const h = Math.min(max, Math.max(min, g.h));
    return { w, h, x: Math.min(100 - w, Math.max(0, g.x)), y: Math.min(100 - h, Math.max(0, g.y)) };
  }
}
