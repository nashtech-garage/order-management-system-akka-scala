import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api.service';
import { API_ENDPOINTS } from '@core/constants/api-endpoints';
import { Product, CreateProductRequest } from '@shared/models/product.model';

@Injectable({
  providedIn: 'root',
})
export class ProductService {
  private apiService = inject(ApiService);

  getProducts(): Observable<Product[]> {
    return this.apiService.get<Product[]>(API_ENDPOINTS.PRODUCTS.BASE);
  }

  getProductById(id: string): Observable<Product> {
    return this.apiService.get<Product>(API_ENDPOINTS.PRODUCTS.BY_ID(id));
  }

  createProduct(product: CreateProductRequest): Observable<Product> {
    return this.apiService.post<Product>(API_ENDPOINTS.PRODUCTS.BASE, product);
  }

  updateProduct(id: string, product: Partial<Product>): Observable<Product> {
    return this.apiService.put<Product>(API_ENDPOINTS.PRODUCTS.BY_ID(id), product);
  }

  deleteProduct(id: string): Observable<void> {
    return this.apiService.delete<void>(API_ENDPOINTS.PRODUCTS.BY_ID(id));
  }
}
