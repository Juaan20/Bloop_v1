import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { authGuard, permissionGuard, staffGuard } from './auth.guards';

/** Doble de AuthService con permisos configurables. */
function setup(permissions: string[] | null) {
  const fakeAuth = {
    isAuthenticated: () => permissions !== null,
    isStaff: () => (permissions?.length ?? 0) > 0,
    hasPermission: (p: string) => permissions?.includes(p) ?? false,
  };
  TestBed.configureTestingModule({
    providers: [provideRouter([]), { provide: AuthService, useValue: fakeAuth }],
  });
}

const state = { url: '/admin/eventos' } as RouterStateSnapshot;
const route = (permission?: string) => ({ data: permission ? { permission } : {} }) as unknown as ActivatedRouteSnapshot;
const run = (guard: typeof authGuard, r = route()) => TestBed.runInInjectionContext(() => guard(r, state));

describe('guards', () => {
  it('authGuard redirige al login conservando la URL pedida', () => {
    setup(null);
    const result = run(authGuard) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/login?returnUrl=%2Fadmin%2Feventos');
  });

  it('staffGuard bloquea a los clientes sin permisos de backoffice', () => {
    setup([]);
    expect(run(staffGuard)).toBeInstanceOf(UrlTree);
  });

  it('permissionGuard deja pasar solo con el permiso de la ruta', () => {
    setup(['EVENT_VIEW']);
    expect(run(permissionGuard, route('EVENT_VIEW'))).toBe(true);
    expect(run(permissionGuard, route('USER_MANAGE'))).toBeInstanceOf(UrlTree);
  });
});
