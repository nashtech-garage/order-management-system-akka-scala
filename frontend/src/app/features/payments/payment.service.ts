import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api.service';
import { API_ENDPOINTS } from '@core/constants/api-endpoints';
import { Payment, CreatePaymentRequest } from '@shared/models/payment.model';

@Injectable({
  providedIn: 'root'
})
export class PaymentService {
  private apiService = inject(ApiService);

  getPayments(): Observable<Payment[]> {
    return this.apiService.get<Payment[]>(API_ENDPOINTS.PAYMENTS.BASE);
  }

  getPaymentById(id: string): Observable<Payment> {
    return this.apiService.get<Payment>(API_ENDPOINTS.PAYMENTS.BY_ID(id));
  }

  getPaymentsByOrder(orderId: string): Observable<Payment[]> {
    return this.apiService.get<Payment[]>(API_ENDPOINTS.PAYMENTS.BY_ORDER(orderId));
  }

  createPayment(payment: CreatePaymentRequest): Observable<Payment> {
    return this.apiService.post<Payment>(API_ENDPOINTS.PAYMENTS.BASE, payment);
  }
}
