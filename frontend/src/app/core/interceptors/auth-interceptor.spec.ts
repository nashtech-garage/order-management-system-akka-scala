import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './auth-interceptor';
import { HttpClient } from '@angular/common/http';

describe('authInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
    localStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should add Authorization header when token exists', () => {
    const testToken = 'test-jwt-token-123';
    localStorage.setItem('auth_token', testToken);

    httpClient.get('/api/test').subscribe();

    const req = httpMock.expectOne('/api/test');
    expect(req.request.headers.has('Authorization')).toBeTruthy();
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${testToken}`);
    req.flush({});
  });

  it('should not add Authorization header when token does not exist', () => {
    httpClient.get('/api/test').subscribe();

    const req = httpMock.expectOne('/api/test');
    expect(req.request.headers.has('Authorization')).toBeFalsy();
    req.flush({});
  });

  it('should not modify request when token is null', () => {
    httpClient.get('/api/test').subscribe();

    const req = httpMock.expectOne('/api/test');
    expect(req.request.headers.has('Authorization')).toBeFalsy();
    req.flush({});
  });

  it('should add Authorization header with correct Bearer format', () => {
    const testToken = 'my-token-value';
    localStorage.setItem('auth_token', testToken);

    httpClient.post('/api/data', { test: 'data' }).subscribe();

    const req = httpMock.expectOne('/api/data');
    const authHeader = req.request.headers.get('Authorization');
    expect(authHeader).toBe('Bearer my-token-value');
    expect(authHeader?.startsWith('Bearer ')).toBeTruthy();
    req.flush({});
  });

  it('should not interfere with existing headers', () => {
    const testToken = 'token-with-headers';
    localStorage.setItem('auth_token', testToken);

    httpClient
      .get('/api/test', {
        headers: {
          'Content-Type': 'application/json',
          'X-Custom-Header': 'custom-value',
        },
      })
      .subscribe();

    const req = httpMock.expectOne('/api/test');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${testToken}`);
    expect(req.request.headers.get('Content-Type')).toBe('application/json');
    expect(req.request.headers.get('X-Custom-Header')).toBe('custom-value');
    req.flush({});
  });

  it('should handle empty string token', () => {
    localStorage.setItem('auth_token', '');

    httpClient.get('/api/test').subscribe();

    const req = httpMock.expectOne('/api/test');
    expect(req.request.headers.has('Authorization')).toBeFalsy();
    req.flush({});
  });

  it('should update Authorization header when token changes', () => {
    // First request with token1
    localStorage.setItem('auth_token', 'token1');
    httpClient.get('/api/test1').subscribe();
    const req1 = httpMock.expectOne('/api/test1');
    expect(req1.request.headers.get('Authorization')).toBe('Bearer token1');
    req1.flush({});

    // Second request with token2
    localStorage.setItem('auth_token', 'token2');
    httpClient.get('/api/test2').subscribe();
    const req2 = httpMock.expectOne('/api/test2');
    expect(req2.request.headers.get('Authorization')).toBe('Bearer token2');
    req2.flush({});
  });
});
