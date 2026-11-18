import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, AuthUser, LoginRequest, RegisterRequest } from './auth.models';
import { PermissionCode } from './permissions';

/**
 * Estado de la sesion con Angular Signals.
 *
 * - El access token (JWT de 15 min) se guarda SOLO en memoria: no es accesible tras recargar.
 * - El refresh token esta en una cookie HttpOnly que gestiona el navegador; al arrancar la app
 *   llamamos a /auth/refresh para recuperar la sesion sin pedir de nuevo la contrasena.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly baseUrl = `${environment.apiUrl}/auth`;

  private readonly _user = signal<AuthUser | null>(null);
  private accessToken: string | null = null;
  private refreshInFlight$: Observable<string | null> | null = null;

  /** Usuario actual (null si no hay sesion). */
  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);
  /** Empleados de empresa o super admin: cualquier permiso de backoffice. */
  readonly isStaff = computed(() => (this._user()?.permissions.length ?? 0) > 0);
  readonly displayName = computed(() => {
    const u = this._user();
    return u ? `${u.firstName} ${u.lastName}` : '';
  });

  token(): string | null {
    return this.accessToken;
  }

  hasPermission(permission: PermissionCode): boolean {
    return this._user()?.permissions.includes(permission) ?? false;
  }

  login(request: LoginRequest): Observable<AuthUser> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, request).pipe(
      tap((res) => this.setSession(res)),
      map((res) => res.user),
    );
  }

  register(request: RegisterRequest): Observable<AuthUser> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/register`, request).pipe(
      tap((res) => this.setSession(res)),
      map((res) => res.user),
    );
  }

  /**
   * Pide un JWT nuevo usando la cookie de refresh. Si varias peticiones fallan con 401 a la vez,
   * comparten la misma llamada (shareReplay) en lugar de lanzar varias.
   */
  refresh(): Observable<string | null> {
    if (!this.refreshInFlight$) {
      this.refreshInFlight$ = this.http.post<AuthResponse>(`${this.baseUrl}/refresh`, {}).pipe(
        tap((res) => this.setSession(res)),
        map((res) => res.accessToken),
        catchError(() => {
          this.clearSession();
          return of(null);
        }),
        finalize(() => (this.refreshInFlight$ = null)),
        shareReplay(1),
      );
    }
    return this.refreshInFlight$;
  }

  /** Se ejecuta al arrancar la aplicacion (provideAppInitializer). */
  restoreSession(): Observable<unknown> {
    return this.refresh();
  }

  logout(): void {
    this.http.post(`${this.baseUrl}/logout`, {}).pipe(catchError(() => of(null))).subscribe();
    this.clearSession();
    this.router.navigateByUrl('/');
  }

  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/reset-password`, { token, newPassword });
  }

  /** Actualiza el nombre mostrado tras editar el perfil. */
  patchUser(changes: Partial<AuthUser>): void {
    this._user.update((u) => (u ? { ...u, ...changes } : u));
  }

  clearSession(): void {
    this.accessToken = null;
    this._user.set(null);
  }

  private setSession(res: AuthResponse): void {
    this.accessToken = res.accessToken;
    this._user.set(res.user);
  }
}
