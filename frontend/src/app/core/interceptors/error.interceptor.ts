import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '../services/toast.service';
import { errorMessage } from '../services/api-error';

/**
 * Muestra un aviso global para errores que el componente no puede resolver:
 * sin conexion (0), error interno (5xx) o falta de permisos (403).
 * Los errores de negocio (400, 404, 409) los muestra cada pantalla con su contexto.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && !req.url.endsWith('/auth/refresh')) {
        if (error.status === 0 || error.status >= 500 || error.status === 403) {
          toast.error(errorMessage(error));
        }
      }
      return throwError(() => error);
    }),
  );
};
