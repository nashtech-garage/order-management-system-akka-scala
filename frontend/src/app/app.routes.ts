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
      {
        path: 'create',
        loadComponent: () =>
          import('@features/orders/order-form/order-form').then((m) => m.OrderForm),
      },
      {
        path: ':id',
        loadComponent: () =>
          import('@features/orders/order-detail/order-detail').then((m) => m.OrderDetail),
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
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () => import('@layout/main-layout/main-layout').then((m) => m.MainLayout),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('@features/user-profile/user-profile').then((m) => m.UserProfileComponent),
      },
    ],
  },
  {
    path: 'products',
    canActivate: [authGuard],
    loadComponent: () => import('@layout/main-layout/main-layout').then((m) => m.MainLayout),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('@features/products/product-list/product-list').then((m) => m.ProductList),
      },
      {
        path: 'new',
        loadComponent: () =>
          import('@features/products/product-form/product-form').then((m) => m.ProductForm),
      },
      {
        path: ':id/edit',
        loadComponent: () =>
          import('@features/products/product-form/product-form').then((m) => m.ProductForm),
      },
    ],
  },
  {
    path: 'categories',
    canActivate: [authGuard],
    loadComponent: () => import('@layout/main-layout/main-layout').then((m) => m.MainLayout),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('@features/categories/category-list/category-list').then((m) => m.CategoryList),
      },
      {
        path: 'new',
        loadComponent: () =>
          import('@features/categories/category-form/category-form').then((m) => m.CategoryForm),
      },
    ],
  },
  {
    path: 'reports',
    canActivate: [authGuard],
    loadComponent: () => import('@layout/main-layout/main-layout').then((m) => m.MainLayout),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('@features/reports/reports-dashboard/reports-dashboard').then((m) => m.ReportsDashboard),
      },
      {
        path: 'list',
        loadComponent: () =>
          import('@features/reports/reports-list/reports-list').then((m) => m.ReportsList),
      },
    ],
  },
  {
    path: 'users',
    canActivate: [authGuard],
    loadComponent: () => import('@layout/main-layout/main-layout').then((m) => m.MainLayout),
    children: [
      {
        path: '',
        loadComponent: () => import('@features/users/user-list/user-list').then((m) => m.UserList),
      },
      {
        path: 'new',
        loadComponent: () =>
          import('@features/users/user-create/user-create').then((m) => m.UserCreate),
      },
      {
        path: ':id',
        loadComponent: () =>
          import('@features/users/user-detail/user-detail').then((m) => m.UserDetail),
      },
      {
        path: ':id/edit',
        loadComponent: () =>
          import('@features/users/user-detail/user-detail').then((m) => m.UserDetail),
      },
    ],
  },
  {
    path: 'payments',
    canActivate: [authGuard],
    loadComponent: () => import('@layout/main-layout/main-layout').then((m) => m.MainLayout),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('@features/payments/payment-list/payment-list').then((m) => m.PaymentList),
      },
    ],
  },
  {
    path: '**',
    loadComponent: () => import('@features/not-found/not-found').then((m) => m.NotFound),
  },
];
