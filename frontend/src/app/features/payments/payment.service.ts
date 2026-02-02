import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api-service';
import { API_ENDPOINTS } from '@core/constants/api-endpoints';
import { Payment, PaymentStats } from '@shared/models/payment.model';

@Injectable({
  providedIn: 'root',
})
export class PaymentService {
  private apiService = inject(ApiService);

  // Get all payments with optional filtering
  getPayments(params?: {
    status?: string;
    createdBy?: number;
    offset?: number;
    limit?: number;
  }): Observable<Payment[]> {
    const queryParams = new URLSearchParams();
    if (params?.status) queryParams.append('status', params.status);
    if (params?.createdBy) queryParams.append('createdBy', params.createdBy.toString());
    if (params?.offset) queryParams.append('offset', params.offset.toString());
    if (params?.limit) queryParams.append('limit', params.limit.toString());

    const url = queryParams.toString()
      ? `${API_ENDPOINTS.PAYMENTS.BASE}?${queryParams}`
      : API_ENDPOINTS.PAYMENTS.BASE;
    return this.apiService.get<Payment[]>(url);
  }

  // Get single payment by ID
  getPaymentById(id: number): Observable<Payment> {
    return this.apiService.get<Payment>(API_ENDPOINTS.PAYMENTS.BY_ID(id.toString()));
  }

  // Get payment by order ID
  getPaymentByOrderId(orderId: number): Observable<Payment> {
    return this.apiService.get<Payment>(API_ENDPOINTS.PAYMENTS.BY_ORDER(orderId.toString()));
  }

  // Get payments by status
  getPaymentsByStatus(status: string): Observable<Payment[]> {
    return this.getPayments({ status });
  }

  // Get payment statistics
  getPaymentStats(): Observable<PaymentStats> {
    return this.apiService.get<PaymentStats>(API_ENDPOINTS.PAYMENTS.STATS);
  }
}

