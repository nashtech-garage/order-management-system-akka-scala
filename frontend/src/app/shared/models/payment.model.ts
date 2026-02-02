export interface Payment {
  id: number;
  orderId: number;
  createdBy: number;
  amount: number;
  paymentMethod: string;
  status: string; // 'success' or 'failed'
  createdAt: string;
}

export enum PaymentStatus {
  SUCCESS = 'success',
  FAILED = 'failed',
}

export enum PaymentMethod {
  AUTO = 'auto',
}

// Removed - payments are no longer created from payment page
// export interface CreatePaymentRequest
// export interface ProcessPaymentRequest

export interface PaymentStats {
  totalPayments: number;
  successfulPayments: number;
  failedPayments: number;
  totalAmount: number;
  successAmount: number;
}
