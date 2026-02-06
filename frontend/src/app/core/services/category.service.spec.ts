import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CategoryService } from './category.service';
import { environment } from '../../../environments/environment';
import { Category, CreateCategoryRequest } from '@shared/models/product.model';
import { provideHttpClient } from '@angular/common/http';

describe('CategoryService', () => {
  let service: CategoryService;
  let httpMock: HttpTestingController;
  const apiUrl = `${environment.apiUrl}/categories`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CategoryService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getCategories', () => {
    it('should retrieve all categories', () => {
      const mockCategories: Category[] = [
        { id: 1, name: 'Electronics', description: 'Electronic items' },
        { id: 2, name: 'Books', description: 'Books and magazines' },
      ];

      service.getCategories().subscribe((categories) => {
        expect(categories).toEqual(mockCategories);
        expect(categories.length).toBe(2);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('GET');
      req.flush(mockCategories);
    });

    it('should handle empty categories list', () => {
      const mockCategories: Category[] = [];

      service.getCategories().subscribe((categories) => {
        expect(categories).toEqual([]);
        expect(categories.length).toBe(0);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('GET');
      req.flush(mockCategories);
    });

    it('should handle HTTP error', () => {
      const errorMessage = 'Failed to load categories';

      service.getCategories().subscribe({
        next: () => fail('should have failed with 500 error'),
        error: (error) => {
          expect(error.status).toBe(500);
        },
      });

      const req = httpMock.expectOne(apiUrl);
      req.flush(errorMessage, { status: 500, statusText: 'Server Error' });
    });
  });

  describe('createCategory', () => {
    it('should create a new category', () => {
      const request: CreateCategoryRequest = {
        name: 'Clothing',
        description: 'Apparel and accessories',
      };
      const mockResponse: Category = {
        id: 3,
        name: 'Clothing',
        description: 'Apparel and accessories',
      };

      service.createCategory(request).subscribe((category) => {
        expect(category).toEqual(mockResponse);
        expect(category.id).toBe(3);
        expect(category.name).toBe('Clothing');
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush(mockResponse);
    });

    it('should handle validation error', () => {
      const request: CreateCategoryRequest = {
        name: '',
        description: 'Invalid category',
      };

      service.createCategory(request).subscribe({
        next: () => fail('should have failed with 400 error'),
        error: (error) => {
          expect(error.status).toBe(400);
        },
      });

      const req = httpMock.expectOne(apiUrl);
      req.flush('Invalid category name', { status: 400, statusText: 'Bad Request' });
    });
  });

  describe('deleteCategory', () => {
    it('should delete a category by id', () => {
      const categoryId = 5;
      const mockResponse = { message: 'Category deleted successfully' };

      service.deleteCategory(categoryId).subscribe((response) => {
        expect(response).toEqual(mockResponse);
        expect(response.message).toBe('Category deleted successfully');
      });

      const req = httpMock.expectOne(`${apiUrl}/${categoryId}`);
      expect(req.request.method).toBe('DELETE');
      req.flush(mockResponse);
    });

    it('should handle not found error', () => {
      const categoryId = 999;

      service.deleteCategory(categoryId).subscribe({
        next: () => fail('should have failed with 404 error'),
        error: (error) => {
          expect(error.status).toBe(404);
        },
      });

      const req = httpMock.expectOne(`${apiUrl}/${categoryId}`);
      req.flush('Category not found', { status: 404, statusText: 'Not Found' });
    });

    it('should handle conflict error when category is in use', () => {
      const categoryId = 1;

      service.deleteCategory(categoryId).subscribe({
        next: () => fail('should have failed with 409 error'),
        error: (error) => {
          expect(error.status).toBe(409);
        },
      });

      const req = httpMock.expectOne(`${apiUrl}/${categoryId}`);
      req.flush('Category is in use', { status: 409, statusText: 'Conflict' });
    });
  });
});
