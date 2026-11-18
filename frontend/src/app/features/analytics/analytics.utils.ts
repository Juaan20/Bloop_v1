/**
 * Funciones puras de la pantalla de analitica (faciles de probar con tests unitarios).
 */

/** Fecha local en formato yyyy-MM-dd (el que espera el backend y el input type="date"). */
export function isoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Periodo de los ultimos N dias, incluido hoy. */
export function presetRange(days: number, today = new Date()): { from: string; to: string } {
  const from = new Date(today);
  from.setDate(from.getDate() - (days - 1));
  return { from: isoDate(from), to: isoDate(today) };
}

/** Periodo anterior de la misma duracion (termina el dia antes de "from"). Para las comparativas. */
export function previousRange(from: string, to: string): { from: string; to: string } {
  const start = new Date(`${from}T12:00:00`);
  const end = new Date(`${to}T12:00:00`);
  const days = Math.round((end.getTime() - start.getTime()) / 86_400_000) + 1;
  const prevEnd = new Date(start);
  prevEnd.setDate(prevEnd.getDate() - 1);
  const prevStart = new Date(prevEnd);
  prevStart.setDate(prevStart.getDate() - (days - 1));
  return { from: isoDate(prevStart), to: isoDate(prevEnd) };
}

export interface DayBar {
  day: string;
  ticketRevenue: number;
  posRevenue: number;
  total: number;
  /** Alturas en % respecto al dia de mayor facturacion. */
  ticketHeight: number;
  posHeight: number;
}

/** Calcula las alturas de las barras apiladas (entradas + TPV) de cada dia. */
export function barHeights(days: { day: string; ticketRevenue: number; posRevenue: number }[]): DayBar[] {
  const max = Math.max(1, ...days.map((d) => d.ticketRevenue + d.posRevenue));
  return days.map((d) => ({
    ...d,
    total: d.ticketRevenue + d.posRevenue,
    ticketHeight: (d.ticketRevenue / max) * 100,
    posHeight: (d.posRevenue / max) * 100,
  }));
}
