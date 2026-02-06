import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CustomerService } from './customer.service';
import { environment } from '../../../environments/environment';
import {
  Customer,
  CreateCustomerRequest,
  UpdateCustomerRequest,
  CreateAddressRequest,
  Address,
  CustomerResponse,
} from '@shared/models/customer.model';
import { provideHttpClient } from '@angular/common/http';

describe('CustomerService', () => {
  let service: CustomerService;
  let httpMock: HttpTestingController;
  const apiUrl = `${environment.apiUrl}/customers`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CustomerService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getCustomers', () => {
    it('should retrieve customers with default pagination', () => {
      const mockCustomers: Customer[] = [
        {
          id: 1,
          firstName: 'John',
          lastName: 'Doe',
          email: 'john@example.com',
          phone: '123-456-7890',
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
        },
      ];

      service.getCustomers().subscribe((customers) => {
        expect(customers).toEqual(mockCustomers);
        expect(customers.length).toBe(1);
      });

      const req = httpMock.expectOne(`${apiUrl}?offset=0&limit=20`);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('offset')).toBe('0');
      expect(req.request.params.get('limit')).toBe('20');
      req.flush(mockCustomers);
    });

    it('should retrieve customers with custom pagination', () => {
      const mockCustomers: Customer[] = [];
      const offset = 40;
      const limit = 10;

      service.getCustomers(offset, limit).subscribe((customers) => {
        expect(customers).toEqual(mockCustomers);
      });

      const req = httpMock.expectOne(`${apiUrl}?offset=${offset}&limit=${limit}`);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('offset')).toBe(offset.toString());
      expect(req.request.params.get('limit')).toBe(limit.toString());
      req.flush(mockCustomers);
    });
  });

  describe('getCustomer', () => {
    it('should retrieve a customer by id', () => {
      const customerId = 1;
      const mockResponse: CustomerResponse = {
        id: 1,
        firstName: 'John',
        lastName: 'Doe',
        email: 'john@example.com',
        phone: '123-456-7890',
        createdAt: '2024-01-01T00:00:00Z',
        addresses: [],
      };

      service.getCustomer(customerId).subscribe((response) => {
        expect(response).toEqual(mockResponse);
        expect(response.id).toBe(customerId);
      });

      const req = httpMock.expectOne(`${apiUrl}/${customerId}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });

    it('should handle not found error', () => {
      const customerId = 999;

      service.getCustomer(customerId).subscribe({
        next: () => fail('should have failed with 404 error'),
        error: (error) => {
          expect(error.status).toBe(404);
        },
      });

      const req = httpMock.expectOne(`${apiUrl}/${customerId}`);
      req.flush('Customer not found', { status: 404, statusText: 'Not Found' });
    });
  });

  describe('createCustomer', () => {
    it('should create a new customer', () => {
      const request: CreateCustomerRequest = {
        firstName: 'Jane',
        lastName: 'Smith',
        email: 'jane@example.com',
        phone: '098-765-4321',
      };
      const mockResponse: Customer = {
        id: 2,
        firstName: 'Jane',
        lastName: 'Smith',
        email: 'jane@example.com',
        phone: '098-765-4321',
        createdAt: '2024-01-02T00:00:00Z',
        updatedAt: '2024-01-02T00:00:00Z',
      };

      service.createCustomer(request).subscribe((customer) => {
        expect(customer).toEqual(mockResponse);
        expect(customer.firstName).toBe('Jane');
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush(mockResponse);
    });

    it('should handle validation error', () => {
      const request: CreateCustomerRequest = {
        firstName: '',
        lastName: '',
        email: 'invalid-email',
        phone: '',
      };

      service.createCustomer(request).subscribe({
        next: () => fail('should have failed with 400 error'),
        error: (error) => {
          expect(error.status).toBe(400);
        },
      });

      const req = httpMock.expectOne(apiUrl);
      req.flush('Invalid customer data', { status: 400, statusText: 'Bad Request' });
    });
  });

  describe('updateCustomer', () => {
    it('should update an existing customer', () => {
      const customerId = 1;
      const request: UpdateCustomerRequest = {
        firstName: 'John',
        lastName: 'Updated',
        email: 'john.updated@example.com',
      };
      const mockResponse = { message: 'Customer updated successfully' };

      service.updateCustomer(customerId, request).subscribe((response) => {
        expect(response).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(`${apiUrl}/${customerId}`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(request);
      req.flush(mockResponse);
    });

    it('should handle not found error', () => {
      const customerId = 999;
      const request: UpdateCustomerRequest = { firstName: 'Test' };

      service.updateCustomer(customerId, request).subscribe({
        next: () => fail('should have failed with 404 error'),
        error: (error) => {
          expect(error.status).toBe(404);
        },
      });

      const req = httpMock.expectOne(`${apiUrl}/${customerId}`);
      req.flush('Customer not found', { status: 404, statusText: 'Not Found' });
    });
  });

  describe('deleteCustomer', () => {
    it('should delete a customer by id', () => {
      const customerId = 5;
      const mockResponse = { message: 'Customer deleted successfully' };

      service.deleteCustomer(customerId).subscribe((response) => {
        expect(response).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(`${apiUrl}/${customerId}`);
      expect(req.request.method).toBe('DELETE');
      req.flush(mockResponse);
    });

    it('should handle conflict error', () => {
      const customerId = 1;

      service.deleteCustomer(customerId).subscribe({
        next: () => fail('should have failed with 409 error'),
        error: (error) => {
          expect(error.status).toBe(409);
        },
      });

      const req = httpMock.expectOne(`${apiUrl}/${customerId}`);
      req.flush('Customer has orders', { status: 409, statusText: 'Conflict' });
    });
  });

  describe('getAddresses', () => {
    it('should retrieve addresses for a customer', () => {
      const customerId = 1;
      const mockAddresses: Address[] = [
        {
          id: 1,
          customerId: 1,
          street: '123 Main St',
          city: 'Springfield',
          state: 'IL',
          postalCode: '62701',
          country: 'USA',
          isDefault: false,
        },
      ];

      service.getAddresses(customerId).subscribe((addresses) => {
        expect(addresses).toEqual(mockAddresses);
        expect(addresses.length).toBe(1);
      });

      const req = httpMock.expectOne(`${apiUrl}/${customerId}/addresses`);
      expect(req.request.method).toBe('GET');
      req.flush(mockAddresses);
    });

    it('should handle empty addresses', () => {
      const customerId = 2;
      const mockAddresses: Address[] = [];

      service.getAddresses(customerId).subscribe((addresses) => {
        expect(addresses).toEqual([]);
      });

      const req = httpMock.expectOne(`${apiUrl}/${customerId}/addresses`);
      req.flush(mockAddresses);
    });
  });

  describe('addAddress', () => {
    it('should add an address to a customer', () => {
      const customerId = 1;
      const request: CreateAddressRequest = {
        street: '456 Oak Ave',
        city: 'Chicago',
        state: 'IL',
        postalCode: '60601',
        country: 'USA',
        isDefault: true,
      };
      const mockResponse: Address = {
        id: 2,
        customerId: 1,
        street: request.street,
        city: request.city,
        state: request.state,
        postalCode: request.postalCode,
        country: request.country,
        isDefault: request.isDefault ?? false,
      };

      service.addAddress(customerId, request).subscribe((address) => {
        expect(address).toEqual(mockResponse);
        expect(address.customerId).toBe(customerId);
      });

      const req = httpMock.expectOne(`${apiUrl}/${customerId}/addresses`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush(mockResponse);
    });

    it('should handle validation error', () => {
      const customerId = 1;
      const request: CreateAddressRequest = {
        street: '',
        city: '',
        state: '',
        postalCode: '',
        country: '',
      };

      service.addAddress(customerId, request).subscribe({
        next: () => fail('should have failed with 400 error'),
        error: (error) => {
          expect(error.status).toBe(400);
        },
      });

      const req = httpMock.expectOne(`${apiUrl}/${customerId}/addresses`);
      req.flush('Invalid address data', { status: 400, statusText: 'Bad Request' });
    });
  });

  describe('deleteAddress', () => {
    it('should delete an address by id', () => {
      const addressId = 3;
      const mockResponse = { message: 'Address deleted successfully' };

      service.deleteAddress(addressId).subscribe((response) => {
        expect(response).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(`${apiUrl}/addresses/${addressId}`);
      expect(req.request.method).toBe('DELETE');
      req.flush(mockResponse);
    });

    it('should handle not found error', () => {
      const addressId = 999;

      service.deleteAddress(addressId).subscribe({
        next: () => fail('should have failed with 404 error'),
        error: (error) => {
          expect(error.status).toBe(404);
        },
      });

      const req = httpMock.expectOne(`${apiUrl}/addresses/${addressId}`);
      req.flush('Address not found', { status: 404, statusText: 'Not Found' });
    });
  });
});
