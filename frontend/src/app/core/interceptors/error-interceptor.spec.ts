import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { errorInterceptor } from './error-interceptor';
import { ToastService } from '@shared/services/toast.service';

describe('errorInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;
  let router: jasmine.SpyObj<Router>;
  let toastService: jasmine.SpyObj<ToastService>;

  beforeEach(() => {
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    const toastServiceSpy = jasmine.createSpyObj('ToastService', [
      'error',
      'success',
      'warning',
      'info',
    ]);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: routerSpy },
        { provide: ToastService, useValue: toastServiceSpy },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    toastService = TestBed.inject(ToastService) as jasmine.SpyObj<ToastService>;
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(errorInterceptor).toBeTruthy();
  });

  describe('401 Unauthorized errors', () => {
    it('should redirect to login and show toast for non-login endpoints', (done) => {
      httpClient.get('/api/protected').subscribe({
        error: (error) => {
          expect(error.status).toBe(401);
          expect(toastService.error).toHaveBeenCalledWith('Session expired. Please login again.');
          expect(router.navigate).toHaveBeenCalledWith(['/auth/login']);
          done();
        },
      });

      const req = httpMock.expectOne('/api/protected');
      req.flush(null, { status: 401, statusText: 'Unauthorized' });
    });

    it('should not show toast for login endpoint 401 errors', (done) => {
      httpClient.post('/api/auth/login', { username: 'test', password: 'wrong' }).subscribe({
        error: (error) => {
          expect(error.status).toBe(401);
          expect(toastService.error).not.toHaveBeenCalled();
          expect(router.navigate).not.toHaveBeenCalled();
          done();
        },
      });

      const req = httpMock.expectOne('/api/auth/login');
      req.flush(null, { status: 401, statusText: 'Unauthorized' });
    });

    it('should not show toast for register endpoint 401 errors', (done) => {
      httpClient.post('/api/auth/register', { username: 'test' }).subscribe({
        error: (error) => {
          expect(error.status).toBe(401);
          expect(toastService.error).not.toHaveBeenCalled();
          expect(router.navigate).not.toHaveBeenCalled();
          done();
        },
      });

      const req = httpMock.expectOne('/api/auth/register');
      req.flush(null, { status: 401, statusText: 'Unauthorized' });
    });
  });

  describe('403 Forbidden errors', () => {
    it('should show forbidden error toast', (done) => {
      httpClient.get('/api/admin/users').subscribe({
        error: (error) => {
          expect(error.status).toBe(403);
          expect(toastService.error).toHaveBeenCalledWith(
            'Access forbidden. You do not have permission to perform this action.',
          );
          done();
        },
      });

      const req = httpMock.expectOne('/api/admin/users');
      req.flush(null, { status: 403, statusText: 'Forbidden' });
    });
  });

  describe('400 Bad Request errors', () => {
    it('should show error message from backend', (done) => {
      const errorResponse = {
        error: 'Invalid email format',
      };

      httpClient.post('/api/users', {}).subscribe({
        error: (error) => {
          expect(error.status).toBe(400);
          expect(toastService.error).toHaveBeenCalledWith('Invalid email format');
          done();
        },
      });

      const req = httpMock.expectOne('/api/users');
      req.flush(errorResponse, { status: 400, statusText: 'Bad Request' });
    });

    it('should show default message when backend error is not available', (done) => {
      httpClient.post('/api/users', {}).subscribe({
        error: (error) => {
          expect(error.status).toBe(400);
          expect(toastService.error).toHaveBeenCalledWith(
            'Invalid request. Please check your input.',
          );
          done();
        },
      });

      const req = httpMock.expectOne('/api/users');
      req.flush({}, { status: 400, statusText: 'Bad Request' });
    });
  });

  describe('500 Internal Server Error', () => {
    it('should show server error toast', (done) => {
      httpClient.get('/api/data').subscribe({
        error: (error) => {
          expect(error.status).toBe(500);
          expect(toastService.error).toHaveBeenCalledWith(
            'Server error occurred. Please try again later.',
          );
          done();
        },
      });

      const req = httpMock.expectOne('/api/data');
      req.flush(null, { status: 500, statusText: 'Internal Server Error' });
    });
  });

  describe('Other errors', () => {
    it('should not show toast for 404 errors', (done) => {
      httpClient.get('/api/notfound').subscribe({
        error: (error) => {
          expect(error.status).toBe(404);
          expect(toastService.error).not.toHaveBeenCalled();
          expect(router.navigate).not.toHaveBeenCalled();
          done();
        },
      });

      const req = httpMock.expectOne('/api/notfound');
      req.flush(null, { status: 404, statusText: 'Not Found' });
    });

    it('should not show toast for 502 errors', (done) => {
      httpClient.get('/api/service').subscribe({
        error: (error) => {
          expect(error.status).toBe(502);
          expect(toastService.error).not.toHaveBeenCalled();
          done();
        },
      });

      const req = httpMock.expectOne('/api/service');
      req.flush(null, { status: 502, statusText: 'Bad Gateway' });
    });
  });

  describe('URL detection', () => {
    it('should detect login URL correctly', (done) => {
      httpClient.post('/auth/login', {}).subscribe({
        error: () => {
          expect(toastService.error).not.toHaveBeenCalled();
          done();
        },
      });

      const req = httpMock.expectOne('/auth/login');
      req.flush(null, { status: 401, statusText: 'Unauthorized' });
    });

    it('should detect register URL correctly', (done) => {
      httpClient.post('/auth/register', {}).subscribe({
        error: () => {
          expect(toastService.error).not.toHaveBeenCalled();
          done();
        },
      });

      const req = httpMock.expectOne('/auth/register');
      req.flush(null, { status: 401, statusText: 'Unauthorized' });
    });

    it('should handle URL with query parameters', (done) => {
      httpClient.get('/api/data?page=1&limit=10').subscribe({
        error: () => {
          expect(toastService.error).toHaveBeenCalled();
          expect(router.navigate).toHaveBeenCalledWith(['/auth/login']);
          done();
        },
      });

      const req = httpMock.expectOne('/api/data?page=1&limit=10');
      req.flush(null, { status: 401, statusText: 'Unauthorized' });
    });
  });

  describe('Error propagation', () => {
    it('should propagate error after handling', (done) => {
      httpClient.get('/api/test').subscribe({
        next: () => fail('Should not succeed'),
        error: (error) => {
          expect(error).toBeDefined();
          expect(error.status).toBe(500);
          done();
        },
      });

      const req = httpMock.expectOne('/api/test');
      req.flush(null, { status: 500, statusText: 'Internal Server Error' });
    });
  });

  describe('Multiple error scenarios', () => {
    it('should handle consecutive errors independently', (done) => {
      let errorCount = 0;

      // First request - 401 error
      httpClient.get('/api/protected1').subscribe({
        error: () => {
          errorCount++;
          if (errorCount === 2) {
            expect(toastService.error).toHaveBeenCalledTimes(2);
            expect(toastService.error).toHaveBeenCalledWith('Session expired. Please login again.');
            expect(toastService.error).toHaveBeenCalledWith(
              'Access forbidden. You do not have permission to perform this action.',
            );
            done();
          }
        },
      });

      // Second request - 403 error
      httpClient.get('/api/admin').subscribe({
        error: () => {
          errorCount++;
          if (errorCount === 2) {
            expect(toastService.error).toHaveBeenCalledTimes(2);
            done();
          }
        },
      });

      const req1 = httpMock.expectOne('/api/protected1');
      req1.flush(null, { status: 401, statusText: 'Unauthorized' });

      const req2 = httpMock.expectOne('/api/admin');
      req2.flush(null, { status: 403, statusText: 'Forbidden' });
    });
  });
});
