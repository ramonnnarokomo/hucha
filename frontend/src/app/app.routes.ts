import { Routes } from '@angular/router';
import { adminGuard, authGuard, customerGuard, guestGuard } from './core/guards';
import { Shell } from './layout/shell';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Entrar',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'registro',
    title: 'Abrir cuenta',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register-page').then((m) => m.RegisterPage),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        title: 'Inicio',
        canActivate: [customerGuard],
        loadComponent: () =>
          import('./features/dashboard/dashboard-page').then((m) => m.DashboardPage),
      },
      {
        path: 'cuentas/:id',
        title: 'Movimientos',
        canActivate: [customerGuard],
        loadComponent: () => import('./features/accounts/account-page').then((m) => m.AccountPage),
      },
      {
        path: 'transferir',
        title: 'Transferir',
        canActivate: [customerGuard],
        loadComponent: () =>
          import('./features/transfers/transfer-page').then((m) => m.TransferPage),
      },
      {
        path: 'transferencias',
        title: 'Transferencias',
        canActivate: [customerGuard],
        loadComponent: () =>
          import('./features/transfers/transfers-page').then((m) => m.TransfersPage),
      },
      {
        path: 'beneficiarios',
        title: 'Beneficiarios',
        canActivate: [customerGuard],
        loadComponent: () => import('./features/payees/payees-page').then((m) => m.PayeesPage),
      },
      {
        path: 'huchas',
        title: 'Huchas',
        canActivate: [customerGuard],
        loadComponent: () => import('./features/savings/savings-page').then((m) => m.SavingsPage),
      },
      {
        path: 'tarjetas',
        title: 'Tarjetas',
        canActivate: [customerGuard],
        loadComponent: () => import('./features/cards/cards-page').then((m) => m.CardsPage),
      },
      {
        path: 'analitica',
        title: 'Analítica',
        canActivate: [customerGuard],
        loadComponent: () =>
          import('./features/analytics/analytics-page').then((m) => m.AnalyticsPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
