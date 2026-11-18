import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';

/** Endpoints de autenticacion: no llevan token ni se reintentan tras un 401. */
const AUTH_ENDPOINTS = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh', '/api/auth/logout'];

/**
 * Interceptor de autenticacion:
 * 1. Anade "Authorization: Bearer <jwt>" a las peticiones a la API.
 * 2. Si la API responde 401 (token caducado), renueva el token una vez y repite la peticion.
 * 3. Si no se puede renovar, cierra la sesion y lleva al login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const isAuthEndpoint = AUTH_ENDPOINTS.some((url) => req.url.startsWith(url));

  return next(withToken(req, isAuthEndpoint ? null : auth.token())).pipe(
    catchError((error: unknown) => {
      const expired = error instanceof HttpErrorResponse && error.status === 401;
      if (!expired || isAuthEndpoint || !auth.token()) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        switchMap((newToken) => {
          if (!newToken) {
            router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
            return throwError(() => error);
          }
          return next(withToken(req, newToken));
        }),
      );
    }),
  );
};

function withToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}
