import { Routes } from '@angular/router';
import { authGuard } from '@core/guards/auth-guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/auth/login',
    pathMatch: 'full',
  },
  {
    path: 'auth',
    children: [
      {
        path: 'login',
        loadComponent: () => import('@features/auth/login/login').then((m) => m.Login),
      },
      {
        path: 'signup',
        loadComponent: () => import('@app/features/auth/signup/signup').then((m) => m.Signup),
      },
    ],
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('@layout/main-layout/main-layout').then((m) => m.MainLayout),
    children: [
      {
        path: '',
        loadComponent: () => import('@features/dashboard/dashboard').then((m) => m.Dashboard),
      },
    ],
  },
  {
    path: 'orders',
    canActivate: [authGuard],
    loadComponent: () => import('@layout/main-layout/main-layout').then((m) => m.MainLayout),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('@features/orders/order-list/order-list').then((m) => m.OrderList),
      },
    ],
  },
  {
    path: 'customers',
    canActivate: [authGuard],
    loadComponent: () => import('@layout/main-layout/main-layout').then((m) => m.MainLayout),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('@features/customers/customer-list/customer-list').then((m) => m.CustomerList),
      },
      {
        path: 'new',
        loadComponent: () =>
          import('@features/customers/customer-form/customer-form').then((m) => m.CustomerForm),
      },
      {
        path: ':id/edit',
        loadComponent: () =>
          import('@features/customers/customer-form/customer-form').then((m) => m.CustomerForm),
      },
    ],
  },
  {
    path: '**',
    loadComponent: () => import('@features/not-found/not-found').then((m) => m.NotFound),
  },
];
