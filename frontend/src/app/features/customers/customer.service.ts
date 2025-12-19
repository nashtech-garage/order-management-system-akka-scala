import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api.service';
import { API_ENDPOINTS } from '@core/constants/api-endpoints';
import { Customer, CreateCustomerRequest } from '@shared/models/customer.model';

@Injectable({
  providedIn: 'root'
})
export class CustomerService {
  private apiService = inject(ApiService);

  getCustomers(): Observable<Customer[]> {
    return this.apiService.get<Customer[]>(API_ENDPOINTS.CUSTOMERS.BASE);
  }

  getCustomerById(id: string): Observable<Customer> {
    return this.apiService.get<Customer>(API_ENDPOINTS.CUSTOMERS.BY_ID(id));
  }

  createCustomer(customer: CreateCustomerRequest): Observable<Customer> {
    return this.apiService.post<Customer>(API_ENDPOINTS.CUSTOMERS.BASE, customer);
  }

  updateCustomer(id: string, customer: Partial<Customer>): Observable<Customer> {
    return this.apiService.put<Customer>(API_ENDPOINTS.CUSTOMERS.BY_ID(id), customer);
  }

  deleteCustomer(id: string): Observable<void> {
    return this.apiService.delete<void>(API_ENDPOINTS.CUSTOMERS.BY_ID(id));
  }
}
