/**
 * Funciones puras del dashboard (probadas en dashboard.utils.spec.ts).
 */

export interface Trend {
  /** Variacion en tanto por uno (0.12 = +12 %). */
  change: number;
  direction: 'up' | 'down' | 'flat';
}

/**
 * Compara un valor con el del periodo anterior.
 * Devuelve null si no hay base de comparacion (periodo anterior a cero o sin datos): asi nunca
 * se muestra un porcentaje inventado.
 */
export function trend(current: number | null | undefined, previous: number | null | undefined): Trend | null {
  if (current == null || previous == null || previous <= 0) return null;
  const change = (current - previous) / previous;
  const direction = Math.abs(change) < 0.005 ? 'flat' : change > 0 ? 'up' : 'down';
  return { change, direction };
}

/** Periodo de N dias que termina hoy y el periodo inmediatamente anterior de la misma duracion. */
export function comparisonRanges(days: number, today = new Date()): {
  current: { from: Date; to: Date };
  previous: { from: Date; to: Date };
} {
  const end = new Date(today);
  end.setHours(23, 59, 59, 999);
  const start = new Date(today);
  start.setHours(0, 0, 0, 0);
  start.setDate(start.getDate() - (days - 1));
  const prevEnd = new Date(start.getTime() - 1);
  const prevStart = new Date(start);
  prevStart.setDate(prevStart.getDate() - days);
  return { current: { from: start, to: end }, previous: { from: prevStart, to: prevEnd } };
}
