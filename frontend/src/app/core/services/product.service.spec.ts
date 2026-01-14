import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProductService } from './product.service';
import { environment } from '../../../environments/environment';
import { CreateProductRequest, UpdateProductRequest } from '@shared/models/product.model';

describe('ProductService', () => {
  let service: ProductService;
  let httpMock: HttpTestingController;
  const apiUrl = `${environment.apiUrl}/products`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProductService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getProducts', () => {
    it('should return products with default params', () => {
      const mockProducts = [
        { id: 1, name: 'Product 1', price: 100, stockQuantity: 10, createdAt: '' },
      ];

      service.getProducts().subscribe((products) => {
        expect(products).toEqual(mockProducts);
      });

      const req = httpMock.expectOne(`${apiUrl}?offset=0&limit=20`);
      expect(req.request.method).toBe('GET');
      req.flush(mockProducts);
    });

    it('should return products with search and category filters', () => {
      const mockProducts = [
        { id: 1, name: 'Product 1', price: 100, stockQuantity: 10, createdAt: '' },
      ];
      const search = 'test';
      const categoryId = 123;
      const offset = 10;
      const limit = 5;

      service.getProducts(offset, limit, search, categoryId).subscribe((products) => {
        expect(products).toEqual(mockProducts);
      });

      const req = httpMock.expectOne(
        (request) =>
          request.url === apiUrl &&
          request.params.get('offset') === offset.toString() &&
          request.params.get('limit') === limit.toString() &&
          request.params.get('search') === search &&
          request.params.get('categoryId') === categoryId.toString(),
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockProducts);
    });
  });

  describe('getProduct', () => {
    it('should return a single product', () => {
      const mockProduct = {
        id: 1,
        name: 'Product 1',
        price: 100,
        stockQuantity: 10,
        createdAt: '',
      };

      service.getProduct(1).subscribe((product) => {
        expect(product).toEqual(mockProduct);
      });

      const req = httpMock.expectOne(`${apiUrl}/1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockProduct);
    });
  });

  describe('createProduct', () => {
    it('should create a product', () => {
      const mockRequest: CreateProductRequest = {
        name: 'New Product',
        price: 100,
        stockQuantity: 10,
      };
      const mockResponse = { id: 1, ...mockRequest, createdAt: '' };

      service.createProduct(mockRequest).subscribe((product) => {
        expect(product).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(mockRequest);
      req.flush(mockResponse);
    });
  });

  describe('updateProduct', () => {
    it('should update a product', () => {
      const mockRequest: UpdateProductRequest = { name: 'Updated Product' };
      const mockResponse = { message: 'Product updated successfully' };

      service.updateProduct(1, mockRequest).subscribe((response) => {
        expect(response).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(`${apiUrl}/1`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(mockRequest);
      req.flush(mockResponse);
    });
  });

  describe('deleteProduct', () => {
    it('should delete a product', () => {
      const mockResponse = { message: 'Product deleted successfully' };

      service.deleteProduct(1).subscribe((response) => {
        expect(response).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(`${apiUrl}/1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(mockResponse);
    });
  });

  describe('updateStock', () => {
    it('should update stock', () => {
      const mockResponse = { message: 'Stock updated' };
      const quantity = 5;

      service.updateStock(1, quantity).subscribe((response) => {
        expect(response).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(`${apiUrl}/1/stock`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ quantity });
      req.flush(mockResponse);
    });
  });

  describe('getCategories', () => {
    it('should return categories', () => {
      const mockCategories = [
        { id: 1, name: 'Category 1' },
        { id: 2, name: 'Category 2' },
      ];

      service.getCategories().subscribe((categories) => {
        expect(categories).toEqual(mockCategories);
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/categories`);
      expect(req.request.method).toBe('GET');
      req.flush(mockCategories);
    });
  });
});
