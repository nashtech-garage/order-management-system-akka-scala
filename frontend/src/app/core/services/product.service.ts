import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ProductResponse,
  CreateProductRequest,
  UpdateProductRequest,
} from '@shared/models/product.model';

@Injectable({
  providedIn: 'root',
})
export class ProductService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/products`;

  getProducts(
    offset: number = 0,
    limit: number = 20,
    search?: string,
    categoryId?: number | null,
  ): Observable<ProductResponse[]> {
    let params = new HttpParams().set('offset', offset).set('limit', limit);

    if (search) {
      params = params.set('search', search);
    }

    if (categoryId) {
      params = params.set('categoryId', categoryId);
    }

    return this.http.get<ProductResponse[]>(this.apiUrl, { params });
  }

  getProduct(id: number): Observable<ProductResponse> {
    return this.http.get<ProductResponse>(`${this.apiUrl}/${id}`);
  }

  createProduct(request: CreateProductRequest): Observable<ProductResponse> {
    return this.http.post<ProductResponse>(this.apiUrl, request);
  }

  updateProduct(id: number, request: UpdateProductRequest): Observable<{ message: string }> {
    return this.http.put<{ message: string }>(`${this.apiUrl}/${id}`, request);
  }

  deleteProduct(id: number): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.apiUrl}/${id}`);
  }

  updateStock(id: number, quantity: number): Observable<{ message: string }> {
    return this.http.put<{ message: string }>(`${this.apiUrl}/${id}/stock`, { quantity });
  }

  getCategories(): Observable<{ id: number; name: string; description?: string }[]> {
    return this.http.get<{ id: number; name: string; description?: string }[]>(`${environment.apiUrl}/categories`);
  }
}
