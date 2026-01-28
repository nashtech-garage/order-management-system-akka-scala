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
    BY_CUSTOMER: (customerId: string) => `/orders?customerId=${customerId}`,
    BY_STATUS: (status: string) => `/orders?status=${status}`,
    STATUS: (id: string) => `/orders/${id}/status`,
    CONFIRM: (id: string) => `/orders/${id}/confirm`,
    PAY: (id: string) => `/orders/${id}/pay`,
    SHIP: (id: string) => `/orders/${id}/ship`,
    COMPLETE: (id: string) => `/orders/${id}/complete`,
    CANCEL: (id: string) => `/orders/${id}/cancel`,
    STATS: '/orders/stats',
  },
};
