/** Descarga un objeto como fichero JSON (exportaciones RGPD). */
export function downloadJson(data: unknown, fileName: string): void {
  downloadBlob(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }), fileName);
}

/** Descarga un fichero recibido del backend (p. ej. un informe CSV). */
export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}

/** Extrae el nombre del fichero de la cabecera Content-Disposition (o usa el indicado). */
export function fileNameFrom(contentDisposition: string | null, fallback: string): string {
  const match = contentDisposition?.match(/filename="?([^";]+)"?/);
  return match ? match[1] : fallback;
}
