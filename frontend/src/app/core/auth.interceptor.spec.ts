import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { AuthResponse } from './models';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: {
    token: string | null;
    accessToken: () => string | null;
    sessionId: () => string | null;
    refresh: ReturnType<typeof vi.fn>;
    sessionExpired: ReturnType<typeof vi.fn>;
  };

  const refreshed: AuthResponse = {
    accessToken: 'nuevo',
    expiresIn: 900,
    sessionId: 's1',
    user: { id: 'u1', email: 'lucia@hucha.dev', fullName: 'Lucía', role: 'CUSTOMER' },
  };

  beforeEach(() => {
    auth = {
      token: 'viejo',
      accessToken: () => auth.token,
      sessionId: () => 's1',
      refresh: vi.fn(),
      sessionExpired: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('añade el token a las llamadas a la API', () => {
    http.get('/api/accounts').subscribe();
    const request = backend.expectOne('/api/accounts');
    expect(request.request.headers.get('Authorization')).toBe('Bearer viejo');
    expect(request.request.headers.has('X-Session-Id')).toBe(false);
    request.flush([]);
  });

  it('manda el id de sesión solo a /api/me', () => {
    http.get('/api/me/sessions').subscribe();
    const request = backend.expectOne('/api/me/sessions');
    expect(request.request.headers.get('X-Session-Id')).toBe('s1');
    request.flush([]);
  });

  it('no toca las llamadas de autenticación ni las de fuera de la API', () => {
    http.post('/api/auth/login', {}).subscribe();
    http.get('/assets/x.json').subscribe();
    expect(backend.expectOne('/api/auth/login').request.headers.has('Authorization')).toBe(false);
    expect(backend.expectOne('/assets/x.json').request.headers.has('Authorization')).toBe(false);
    backend.match(() => true).forEach((r) => r.flush({}));
  });

  it('ante un 401 renueva el token una vez y repite la petición', () => {
    const renewal = new Subject<AuthResponse>();
    auth.refresh.mockReturnValue(renewal);
    let result: unknown;
    http.get('/api/accounts').subscribe((body) => (result = body));

    backend.expectOne('/api/accounts').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(auth.refresh).toHaveBeenCalledTimes(1);
    renewal.next(refreshed);
    renewal.complete();

    const retry = backend.expectOne('/api/accounts');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer nuevo');
    retry.flush([{ id: 'a1' }]);
    expect(result).toEqual([{ id: 'a1' }]);
  });

  it('si no se puede renovar, da la sesión por caducada y propaga el 401', () => {
    auth.refresh.mockReturnValue(throwError(() => new Error('cookie caducada')));
    let status = 0;
    http.get('/api/accounts').subscribe({ error: (error) => (status = error.status) });

    backend.expectOne('/api/accounts').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.sessionExpired).toHaveBeenCalled();
    expect(status).toBe(401);
  });

  it('no intenta renovar si no había token o si el error no es 401', () => {
    auth.refresh.mockReturnValue(of(refreshed));
    http.get('/api/accounts').subscribe({ error: () => undefined });
    backend.expectOne('/api/accounts').flush(null, { status: 422, statusText: 'Unprocessable' });

    auth.token = null;
    http.get('/api/cards').subscribe({ error: () => undefined });
    backend.expectOne('/api/cards').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.refresh).not.toHaveBeenCalled();
  });
});
