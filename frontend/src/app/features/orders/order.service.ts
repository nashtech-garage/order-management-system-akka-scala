import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api-service';
import { API_ENDPOINTS } from '@core/constants/api-endpoints';
import { Order, CreateOrderRequest, PaymentInfo, OrderStats } from '@shared/models/order.model';

@Injectable({
  providedIn: 'root',
})
export class OrderService {
  private apiService = inject(ApiService);

  getOrders(params?: {
    status?: string;
    customerId?: number;
    offset?: number;
    limit?: number;
  }): Observable<Order[]> {
    const queryParams = new URLSearchParams();
    if (params?.status) queryParams.append('status', params.status);
    if (params?.customerId) queryParams.append('customerId', params.customerId.toString());
    if (params?.offset) queryParams.append('offset', params.offset.toString());
    if (params?.limit) queryParams.append('limit', params.limit.toString());

    const url = queryParams.toString()
      ? `${API_ENDPOINTS.ORDERS.BASE}?${queryParams}`
      : API_ENDPOINTS.ORDERS.BASE;
    return this.apiService.get<Order[]>(url);
  }

  getOrderById(id: number): Observable<Order> {
    return this.apiService.get<Order>(API_ENDPOINTS.ORDERS.BY_ID(id.toString()));
  }

  getOrdersByCustomer(customerId: number): Observable<Order[]> {
    return this.getOrders({ customerId });
  }

  getOrdersByStatus(status: string): Observable<Order[]> {
    return this.getOrders({ status });
  }

  getOrderStats(): Observable<OrderStats> {
    return this.apiService.get<OrderStats>(API_ENDPOINTS.ORDERS.STATS);
  }

  createOrder(order: CreateOrderRequest): Observable<Order> {
    return this.apiService.post<Order>(API_ENDPOINTS.ORDERS.BASE, order);
  }

  confirmOrder(id: number): Observable<{ message: string }> {
    return this.apiService.post<{ message: string }>(
      API_ENDPOINTS.ORDERS.CONFIRM(id.toString()),
      {},
    );
  }

  payOrder(id: number, paymentMethod: string): Observable<PaymentInfo> {
    return this.apiService.post<PaymentInfo>(API_ENDPOINTS.ORDERS.PAY(id.toString()), {
      paymentMethod,
    });
  }

  shipOrder(id: number): Observable<{ message: string }> {
    return this.apiService.post<{ message: string }>(API_ENDPOINTS.ORDERS.SHIP(id.toString()), {});
  }

  completeOrder(id: number): Observable<{ message: string }> {
    return this.apiService.post<{ message: string }>(
      API_ENDPOINTS.ORDERS.COMPLETE(id.toString()),
      {},
    );
  }

  cancelOrder(id: number): Observable<{ message: string }> {
    return this.apiService.post<{ message: string }>(
      API_ENDPOINTS.ORDERS.CANCEL(id.toString()),
      {},
    );
  }

  updateOrderStatus(id: number, status: string): Observable<{ message: string }> {
    return this.apiService.put<{ message: string }>(API_ENDPOINTS.ORDERS.BY_ID(id.toString()), {
      status,
    });
  }
}
