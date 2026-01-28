export interface Order {
  id: number;
  customerId: number;
  customerName?: string;
  createdBy: number;
  status: OrderStatus;
  totalAmount: number;
  items: OrderItem[];
  createdAt: string;
  updatedAt?: string;
}

export enum OrderStatus {
  DRAFT = 'draft',
  CREATED = 'created',
  PAID = 'paid',
  SHIPPING = 'shipping',
  COMPLETED = 'completed',
  CANCELLED = 'cancelled',
}

export interface OrderItem {
  id: number;
  productId: number;
  productName?: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface CreateOrderRequest {
  customerId: number;
  items: {
    productId: number;
    quantity: number;
  }[];
}

export interface PaymentInfo {
  paymentId: string;
  orderId: number;
  status: string;
  message: string;
}

export interface OrderStats {
  totalOrders: number;
  completedOrders: number;
  cancelledOrders: number;
  pendingOrders: number;
  totalRevenue: number;
}
