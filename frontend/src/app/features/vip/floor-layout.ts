/**
 * Forma de un local: rejilla de celdas ('1' suelo, '0' fuera) + elementos fijos (barra, escenario...).
 * Igual que FloorPlanRequest en el backend. Funciones puras para los presets (L, U, T...).
 */
export type FloorElementType =
  | 'STAGE' | 'DJ' | 'BAR' | 'DANCEFLOOR' | 'ENTRANCE' | 'TOILETS' | 'COLUMN' | 'STAIRS' | 'CLOAKROOM' | 'LABEL';

export interface FloorElement {
  type: FloorElementType;
  label: string | null;
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface FloorLayout {
  cols: number;
  rows: number;
  cells: string;
  elements: FloorElement[];
}

/** Aspecto y texto por defecto de cada elemento. */
export const ELEMENT_STYLE: Record<FloorElementType, { label: string; css: string }> = {
  STAGE: { label: 'Escenario', css: 'bg-brand-500/20 border-brand-400/60 text-brand-200' },
  DJ: { label: 'Cabina DJ', css: 'bg-brand-600/30 border-brand-300/70 text-brand-100' },
  BAR: { label: 'Barra', css: 'bg-amber-500/15 border-amber-400/60 text-amber-200' },
  DANCEFLOOR: { label: 'Pista', css: 'bg-accent-500/10 border-accent-400/50 border-dashed text-accent-200' },
  ENTRANCE: { label: 'Entrada', css: 'bg-emerald-500/15 border-emerald-400/60 text-emerald-200' },
  TOILETS: { label: 'Banos', css: 'bg-zinc-700/40 border-zinc-500/60 text-zinc-200' },
  COLUMN: { label: '', css: 'bg-zinc-600 border-zinc-400 text-zinc-200' },
  STAIRS: { label: 'Escaleras', css: 'bg-[repeating-linear-gradient(0deg,rgb(255_255_255/0.12)_0_2px,transparent_2px_8px)] border-zinc-500/60 text-zinc-200' },
  CLOAKROOM: { label: 'Guardarropa', css: 'bg-zinc-700/40 border-zinc-500/60 text-zinc-200' },
  LABEL: { label: 'Texto', css: 'border-transparent text-zinc-400' },
};

export type ShapePreset = 'RECT' | 'L' | 'L_FLIP' | 'U' | 'T';

export const SHAPE_PRESETS: { value: ShapePreset; label: string }[] = [
  { value: 'RECT', label: 'Rectangulo' },
  { value: 'L', label: 'En L' },
  { value: 'L_FLIP', label: 'En L (espejo)' },
  { value: 'U', label: 'En U' },
  { value: 'T', label: 'En T' },
];

/** Rejilla de una forma tipica. El usuario puede retocarla celda a celda despues. */
export function presetCells(preset: ShapePreset, cols: number, rows: number): string {
  let out = '';
  const thirdC = Math.round(cols / 3);
  const halfR = Math.round(rows / 2);
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      let floor = true;
      if (preset === 'L') floor = !(r < halfR && c >= cols - Math.round(cols / 2.2));
      if (preset === 'L_FLIP') floor = !(r < halfR && c < Math.round(cols / 2.2));
      if (preset === 'U') floor = !(r < rows - Math.round(rows / 2.6) && c >= thirdC && c < cols - thirdC);
      if (preset === 'T') floor = r < Math.round(rows / 2.6) || (c >= thirdC && c < cols - thirdC);
      out += floor ? '1' : '0';
    }
  }
  return out;
}

/** Plano por defecto mientras el local no disena el suyo: rectangulo con escenario y barra. */
export function defaultLayout(): FloorLayout {
  return {
    cols: 20,
    rows: 12,
    cells: presetCells('RECT', 20, 12),
    elements: [
      { type: 'STAGE', label: 'Escenario', x: 32, y: 2, w: 36, h: 8 },
      { type: 'DANCEFLOOR', label: 'Pista', x: 25, y: 35, w: 50, h: 35 },
      { type: 'BAR', label: 'Barra', x: 2, y: 85, w: 30, h: 10 },
    ],
  };
}

/** Cambia el tamano de la rejilla conservando lo pintado (lo nuevo es suelo). */
export function resizeCells(layout: FloorLayout, cols: number, rows: number): string {
  let out = '';
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      out += r < layout.rows && c < layout.cols ? layout.cells[r * layout.cols + c] : '1';
    }
  }
  return out;
}
