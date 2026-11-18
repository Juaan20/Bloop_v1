import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../models/api.models';

/** Extrae un mensaje legible de un error HTTP para mostrarlo al usuario. */
export function errorMessage(error: unknown, fallback = 'Ha ocurrido un error inesperado'): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'No se puede conectar con el servidor. ¿Esta arrancado el backend?';
    }
    const body = error.error as Partial<ApiError> | null;
    if (body?.fieldErrors) {
      const first = Object.values(body.fieldErrors)[0];
      if (first) {
        return `${body.message ?? 'Datos no validos'}: ${first}`;
      }
    }
    if (body?.message) {
      return body.message;
    }
  }
  return fallback;
}

/** Errores de validacion por campo (respuesta 400 del backend). */
export function fieldErrors(error: unknown): Record<string, string> {
  if (error instanceof HttpErrorResponse) {
    return (error.error as Partial<ApiError> | null)?.fieldErrors ?? {};
  }
  return {};
}
