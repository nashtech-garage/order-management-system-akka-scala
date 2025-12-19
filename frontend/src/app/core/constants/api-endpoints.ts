export const API_ENDPOINTS = {
  AUTH: {
    LOGIN: '/auth/login',
    LOGOUT: '/auth/logout',
    REGISTER: '/auth/register',
    REFRESH: '/auth/refresh',
  },
  ORDERS: {
    BASE: '/orders',
    BY_ID: (id: string) => `/orders/${id}`,
    BY_CUSTOMER: (customerId: string) => `/orders/customer/${customerId}`,
    STATUS: (id: string) => `/orders/${id}/status`,
  },
  PRODUCTS: {
    BASE: '/products',
    BY_ID: (id: string) => `/products/${id}`,
    CATEGORIES: '/products/categories',
  },
  CUSTOMERS: {
    BASE: '/customers',
    BY_ID: (id: string) => `/customers/${id}`,
  },
  USERS: {
    BASE: '/users',
    BY_ID: (id: string) => `/users/${id}`,
    PROFILE: '/users/profile',
  },
  PAYMENTS: {
    BASE: '/payments',
    BY_ID: (id: string) => `/payments/${id}`,
    BY_ORDER: (orderId: string) => `/payments/order/${orderId}`,
  },
  REPORTS: {
    SALES: '/reports/sales',
    ORDERS: '/reports/orders',
    REVENUE: '/reports/revenue',
  },
};
