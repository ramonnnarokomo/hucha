import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, finalize, firstValueFrom, shareReplay, tap } from 'rxjs';
import { AuthResponse, UserSummary } from './models';

const SESSION_HINT = 'hucha:session';

/**
 * Sesión del usuario. El access token (JWT, 15 min) vive solo en memoria; el refresh token va en
 * una cookie HttpOnly que el navegador manda solo a /api/auth. Al recargar la página se recupera
 * la sesión llamando a /api/auth/refresh.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly token = signal<string | null>(null);
  readonly user = signal<UserSummary | null>(null);
  readonly sessionId = signal<string | null>(null);
  readonly isAuthenticated = computed(() => this.user() !== null);
  readonly isAdmin = computed(() => this.user()?.role === 'ADMIN');
  readonly firstName = computed(() => this.user()?.fullName.split(' ')[0] ?? '');

  private refreshInFlight: Observable<AuthResponse> | null = null;
  private refreshTimer: ReturnType<typeof setTimeout> | undefined;

  accessToken(): string | null {
    return this.token();
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/login', { email, password })
      .pipe(tap((response) => this.store(response)));
  }

  register(fullName: string, email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/register', { fullName, email, password })
      .pipe(tap((response) => this.store(response)));
  }

  /** Pide un access token nuevo. Si ya hay una petición en marcha se reutiliza (no se rota dos veces). */
  refresh(): Observable<AuthResponse> {
    this.refreshInFlight ??= this.http.post<AuthResponse>('/api/auth/refresh', {}).pipe(
      tap((response) => this.store(response)),
      finalize(() => (this.refreshInFlight = null)),
      shareReplay(1),
    );
    return this.refreshInFlight;
  }

  /** Al arrancar la app: si la cookie de refresco sigue viva, la sesión continúa sin volver a entrar. */
  async restore(): Promise<void> {
    // La cookie es HttpOnly y no se puede leer; una marca en localStorage evita un 401 inútil a quien nunca entró.
    if (!this.hadSession()) {
      return;
    }
    try {
      await firstValueFrom(this.refresh());
    } catch {
      this.clear();
    }
  }

  logout(): void {
    this.http
      .post('/api/auth/logout', {})
      .subscribe({ complete: () => this.finish(), error: () => this.finish() });
  }

  /** La sesión ya no se puede renovar (caducada o cerrada desde otro dispositivo). */
  sessionExpired(): void {
    this.clear();
    void this.router.navigate(['/login'], { queryParams: { expired: 1 } });
  }

  private finish(): void {
    this.clear();
    void this.router.navigate(['/login']);
  }

  private store(response: AuthResponse): void {
    this.rememberSession(true);
    this.token.set(response.accessToken);
    this.user.set(response.user);
    this.sessionId.set(response.sessionId);
    clearTimeout(this.refreshTimer);
    // Renueva un minuto antes de que caduque, para que nunca falle una petición por token caducado.
    const delay = Math.max(10, response.expiresIn - 60) * 1000;
    this.refreshTimer = setTimeout(
      () => this.refresh().subscribe({ error: () => this.sessionExpired() }),
      delay,
    );
  }

  private clear(): void {
    this.rememberSession(false);
    clearTimeout(this.refreshTimer);
    this.token.set(null);
    this.user.set(null);
    this.sessionId.set(null);
  }

  private hadSession(): boolean {
    try {
      return localStorage.getItem(SESSION_HINT) === '1';
    } catch {
      return true;
    }
  }

  private rememberSession(active: boolean): void {
    try {
      if (active) {
        localStorage.setItem(SESSION_HINT, '1');
      } else {
        localStorage.removeItem(SESSION_HINT);
      }
    } catch {
      // Sin almacenamiento (modo privado estricto): restore() siempre lo intentará.
    }
  }
}
