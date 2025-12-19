import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api.service';
import { API_ENDPOINTS } from '@core/constants/api-endpoints';
import { Order, CreateOrderRequest } from '@shared/models/order.model';

@Injectable({
  providedIn: 'root',
})
export class OrderService {
  private apiService = inject(ApiService);

  getOrders(): Observable<Order[]> {
    return this.apiService.get<Order[]>(API_ENDPOINTS.ORDERS.BASE);
  }

  getOrderById(id: string): Observable<Order> {
    return this.apiService.get<Order>(API_ENDPOINTS.ORDERS.BY_ID(id));
  }

  getOrdersByCustomer(customerId: string): Observable<Order[]> {
    return this.apiService.get<Order[]>(API_ENDPOINTS.ORDERS.BY_CUSTOMER(customerId));
  }

  createOrder(order: CreateOrderRequest): Observable<Order> {
    return this.apiService.post<Order>(API_ENDPOINTS.ORDERS.BASE, order);
  }

  updateOrderStatus(id: string, status: string): Observable<Order> {
    return this.apiService.patch<Order>(API_ENDPOINTS.ORDERS.STATUS(id), { status });
  }

  deleteOrder(id: string): Observable<void> {
    return this.apiService.delete<void>(API_ENDPOINTS.ORDERS.BY_ID(id));
  }
}
