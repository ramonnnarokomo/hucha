import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.isAuthenticated()
    ? true
    : inject(Router).createUrlTree(['/login'], { queryParams: { redirect: state.url } });
};

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAdmin() ? true : inject(Router).createUrlTree(['/']);
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAuthenticated()
    ? inject(Router).createUrlTree([auth.isAdmin() ? '/admin' : '/'])
    : true;
};

/** Las pantallas de cliente no tienen sentido para el administrador (no tiene cuentas). */
export const customerGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAdmin() ? inject(Router).createUrlTree(['/admin']) : true;
};
