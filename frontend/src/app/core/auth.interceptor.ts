import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Añade el token a las llamadas a la API. Si una llamada devuelve 401, renueva el token una vez
 * y la repite; si tampoco se puede renovar, manda al login.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/') || request.url.startsWith('/api/auth/')) {
    return next(request);
  }
  const auth = inject(AuthService);
  const token = auth.accessToken();
  const withToken = (value: string | null) => {
    let headers = request.headers;
    if (value) headers = headers.set('Authorization', `Bearer ${value}`);
    const session = auth.sessionId();
    if (session && request.url.startsWith('/api/me'))
      headers = headers.set('X-Session-Id', session);
    return request.clone({ headers });
  };

  return next(withToken(token)).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !token) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        catchError(() => {
          auth.sessionExpired();
          return throwError(() => error);
        }),
        switchMap((response) => next(withToken(response.accessToken))),
      );
    }),
  );
};
