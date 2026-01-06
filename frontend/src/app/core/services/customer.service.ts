import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
    Customer,
    CreateCustomerRequest,
    UpdateCustomerRequest,
    CreateAddressRequest,
    Address,
    CustomerResponse
} from '@shared/models/customer.model';

@Injectable({
    providedIn: 'root',
})
export class CustomerService {
    private http = inject(HttpClient);
    private apiUrl = `${environment.apiUrl}/customers`;

    getCustomers(offset = 0, limit = 20): Observable<Customer[]> {
        const params = new HttpParams()
            .set('offset', offset)
            .set('limit', limit);
        return this.http.get<Customer[]>(this.apiUrl, { params });
    }

    getCustomer(id: number): Observable<CustomerResponse> {
        return this.http.get<CustomerResponse>(`${this.apiUrl}/${id}`);
    }

    createCustomer(request: CreateCustomerRequest): Observable<Customer> {
        return this.http.post<Customer>(this.apiUrl, request);
    }

    updateCustomer(id: number, request: UpdateCustomerRequest): Observable<{ message: string }> {
        return this.http.put<{ message: string }>(`${this.apiUrl}/${id}`, request);
    }

    deleteCustomer(id: number): Observable<{ message: string }> {
        return this.http.delete<{ message: string }>(`${this.apiUrl}/${id}`);
    }

    getAddresses(customerId: number): Observable<Address[]> {
        return this.http.get<Address[]>(`${this.apiUrl}/${customerId}/addresses`);
    }

    addAddress(customerId: number, request: CreateAddressRequest): Observable<Address> {
        return this.http.post<Address>(`${this.apiUrl}/${customerId}/addresses`, request);
    }

    deleteAddress(addressId: number): Observable<{ message: string }> {
        return this.http.delete<{ message: string }>(`${this.apiUrl}/addresses/${addressId}`);
    }
}
