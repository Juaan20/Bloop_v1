/**
 * Conversion entre las fechas de la API (ISO-8601 en UTC, p. ej. "2026-11-20T22:30:00Z")
 * y el valor de un <input type="datetime-local"> (hora local, "2026-11-20T23:30").
 */
export function isoToLocalInput(iso: string | null | undefined): string {
  if (!iso) return '';
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function localInputToIso(value: string | null | undefined): string | null {
  if (!value) return null;
  return new Date(value).toISOString();
}
