import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let token: string | null;
  const refresh = vi.fn();

  beforeEach(() => {
    token = 'viejo';
    refresh.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { token: () => token, refresh } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  it('anade la cabecera Authorization', () => {
    http.get('/api/venues').subscribe();
    const req = backend.expectOne('/api/venues');
    expect(req.request.headers.get('Authorization')).toBe('Bearer viejo');
    req.flush([]);
  });

  it('no envia el token a los endpoints de login', () => {
    http.post('/api/auth/login', {}).subscribe();
    expect(backend.expectOne('/api/auth/login').request.headers.has('Authorization')).toBe(false);
  });

  it('ante un 401 renueva el token y repite la peticion', () => {
    refresh.mockImplementation(() => {
      token = 'nuevo';
      return of('nuevo');
    });
    let body: unknown;
    http.get('/api/venues').subscribe((b) => (body = b));

    backend.expectOne('/api/venues').flush({}, { status: 401, statusText: 'Unauthorized' });
    const retry = backend.expectOne('/api/venues');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer nuevo');
    retry.flush(['ok']);

    expect(refresh).toHaveBeenCalledTimes(1);
    expect(body).toEqual(['ok']);
  });
});
