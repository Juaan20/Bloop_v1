import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthResponse } from './auth.models';
import { AuthService } from './auth.service';
import { Permission } from './permissions';

const RESPONSE: AuthResponse = {
  accessToken: 'jwt-123',
  expiresAt: '2030-01-01T00:00:00Z',
  user: {
    id: 'u1', email: 'admin@test.local', firstName: 'Ada', lastName: 'Admin', tenantId: 't1', tenantName: 'Club',
    roles: ['TENANT_ADMIN'], permissions: ['EVENT_MANAGE', 'DASHBOARD_VIEW'],
  },
};

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('guarda el usuario y el token tras el login', () => {
    service.login({ email: 'admin@test.local', password: 'x' }).subscribe();
    http.expectOne('/api/auth/login').flush(RESPONSE);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.isStaff()).toBe(true);
    expect(service.token()).toBe('jwt-123');
    expect(service.displayName()).toBe('Ada Admin');
    expect(service.hasPermission(Permission.EVENT_MANAGE)).toBe(true);
    expect(service.hasPermission(Permission.PLATFORM_ADMIN)).toBe(false);
  });

  it('si el refresh falla, la sesion queda cerrada', () => {
    let token: string | null = 'sin-valor';
    service.refresh().subscribe((t) => (token = t));
    http.expectOne('/api/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(token).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('comparte una unica llamada de refresh entre peticiones simultaneas', () => {
    service.refresh().subscribe();
    service.refresh().subscribe();
    http.expectOne('/api/auth/refresh').flush(RESPONSE);
    expect(service.token()).toBe('jwt-123');
  });
});
