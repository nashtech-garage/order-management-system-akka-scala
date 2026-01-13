export const API_ENDPOINTS = {
  AUTH: {
    LOGIN: '/users/login',
    LOGOUT: '/users/logout',
    REGISTER: '/users/register',
    REFRESH: '/auth/refresh',
    VERIFY: '/users/verify',
  },
  PROFILE: {
    GET: '/users/profile',
    UPDATE: '/users/profile',
    CHANGE_PASSWORD: '/users/profile/password',
  },
  ORDERS: {
    BASE: '/orders',
    BY_ID: (id: string) => `/orders/${id}`,
    BY_CUSTOMER: (customerId: string) => `/orders/customer/${customerId}`,
    STATUS: (id: string) => `/orders/${id}/status`,
  },
};
