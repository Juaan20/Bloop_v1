/**
 * Logica pura del ticket del TPV (sin Angular, facil de probar).
 * El backend recalcula todos los importes: esto es solo lo que se ve en pantalla.
 */
export interface TicketLine {
  variantId: string;
  /** Refresco elegido para un combinado (null si no lleva). */
  mixerVariantId?: string | null;
  label: string;
  price: number;
  quantity: number;
  /** Existencias disponibles (null = sin control de stock). */
  stock: number | null;
}

/** Identifica una linea: la misma copa con otro refresco es otra linea. */
export function lineKey(l: Pick<TicketLine, 'variantId' | 'mixerVariantId'>): string {
  return l.mixerVariantId ? `${l.variantId}+${l.mixerVariantId}` : l.variantId;
}

/** Unidades de una variante en el ticket (sumando todas sus lineas, con cualquier refresco). */
export function unitsOf(lines: TicketLine[], variantId: string): number {
  return lines.filter((l) => l.variantId === variantId || l.mixerVariantId === variantId).reduce((n, l) => n + l.quantity, 0);
}

export type DiscountInput = { type: 'PERCENTAGE' | 'FIXED_AMOUNT'; value: number } | null;

/** Anade una unidad; no supera las existencias conocidas. */
export function addToTicket(lines: TicketLine[], item: Omit<TicketLine, 'quantity'>): TicketLine[] {
  const key = lineKey(item);
  const existing = lines.find((l) => lineKey(l) === key);
  if (existing) {
    if (existing.stock !== null && existing.quantity >= existing.stock) return lines;
    return lines.map((l) => (lineKey(l) === key ? { ...l, quantity: l.quantity + 1 } : l));
  }
  if (item.stock !== null && item.stock <= 0) return lines;
  return [...lines, { ...item, quantity: 1 }];
}

/** Suma o resta unidades; a 0 la linea desaparece. */
export function changeQuantity(lines: TicketLine[], key: string, delta: number): TicketLine[] {
  return lines
    .map((l) => {
      if (lineKey(l) !== key) return l;
      const max = l.stock ?? Number.MAX_SAFE_INTEGER;
      return { ...l, quantity: Math.min(max, l.quantity + delta) };
    })
    .filter((l) => l.quantity > 0);
}

export function subtotal(lines: TicketLine[]): number {
  return round(lines.reduce((sum, l) => sum + l.price * l.quantity, 0));
}

export function discountAmount(sub: number, discount: DiscountInput): number {
  if (!discount) return 0;
  const raw = discount.type === 'PERCENTAGE' ? (sub * Math.min(100, discount.value)) / 100 : discount.value;
  return round(Math.min(sub, raw));
}

export function change(total: number, received: number | null): number | null {
  return received === null || received < total ? null : round(received - total);
}

function round(value: number): number {
  return Math.round(value * 100) / 100;
}
