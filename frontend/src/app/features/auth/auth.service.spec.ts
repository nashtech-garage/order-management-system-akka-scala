import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { API_ENDPOINTS } from '@core/constants/api-endpoints';
import { LoginRequest, LoginResponse, RegisterRequest, User } from '@shared/models/user.model';
import { environment } from '@environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  const apiUrl = environment.apiUrl;

  const mockUser: User = {
    id: 1,
    username: 'testuser',
    email: 'test@example.com',
    role: 'user',
    status: 'active',
    createdAt: '2024-01-01T00:00:00Z',
  };

  const mockLoginResponse: LoginResponse = {
    token: 'mock-jwt-token-123',
    user: mockUser,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    localStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('login', () => {
    it('should login user and store token', () => {
      const credentials: LoginRequest = {
        usernameOrEmail: 'testuser',
        password: 'password123',
      };

      service.login(credentials).subscribe((response) => {
        expect(response).toEqual(mockLoginResponse);
        expect(service.currentUser()).toEqual(mockUser);
        expect(localStorage.getItem('auth_token')).toBe('mock-jwt-token-123');
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.AUTH.LOGIN}`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(credentials);
      req.flush(mockLoginResponse);
    });

    it('should set currentUser signal on successful login', () => {
      const credentials: LoginRequest = {
        usernameOrEmail: 'testuser',
        password: 'password123',
      };

      expect(service.currentUser()).toBeNull();

      service.login(credentials).subscribe(() => {
        expect(service.currentUser()).toEqual(mockUser);
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.AUTH.LOGIN}`);
      req.flush(mockLoginResponse);
    });
  });

  describe('logout', () => {
    it('should logout user with valid token and clear state', () => {
      localStorage.setItem('auth_token', 'mock-token');
      service.currentUser.set(mockUser);

      service.logout().subscribe(() => {
        expect(localStorage.getItem('auth_token')).toBeNull();
        expect(service.currentUser()).toBeNull();
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.AUTH.LOGOUT}`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush({});
    });

    it('should clear local state without API call when no token exists', () => {
      service.currentUser.set(mockUser);

      service.logout().subscribe(() => {
        expect(localStorage.getItem('auth_token')).toBeNull();
        expect(service.currentUser()).toBeNull();
      });

      httpMock.expectNone(`${apiUrl}${API_ENDPOINTS.AUTH.LOGOUT}`);
    });
  });

  describe('register', () => {
    it('should register a new user', () => {
      const registerData: RegisterRequest = {
        username: 'newuser',
        email: 'newuser@example.com',
        password: 'password123',
      };

      service.register(registerData).subscribe((user) => {
        expect(user).toEqual(mockUser);
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.AUTH.REGISTER}`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(registerData);
      req.flush(mockUser);
    });
  });

  describe('token management', () => {
    describe('getToken', () => {
      it('should return token from localStorage', () => {
        localStorage.setItem('auth_token', 'test-token');
        expect(service.getToken()).toBe('test-token');
      });

      it('should return null when no token exists', () => {
        expect(service.getToken()).toBeNull();
      });
    });

    describe('setToken', () => {
      it('should store token in localStorage', () => {
        service.setToken('new-token');
        expect(localStorage.getItem('auth_token')).toBe('new-token');
      });
    });

    describe('removeToken', () => {
      it('should remove token from localStorage', () => {
        localStorage.setItem('auth_token', 'test-token');
        service.removeToken();
        expect(localStorage.getItem('auth_token')).toBeNull();
      });
    });
  });

  describe('validateToken', () => {
    it('should return false when no token exists', () => {
      service.validateToken().subscribe((isValid) => {
        expect(isValid).toBe(false);
      });

      httpMock.expectNone(`${apiUrl}${API_ENDPOINTS.AUTH.VERIFY}`);
    });

    it('should return true when token is valid', () => {
      localStorage.setItem('auth_token', 'valid-token');

      service.validateToken().subscribe((isValid) => {
        expect(isValid).toBe(true);
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.AUTH.VERIFY}`);
      expect(req.request.method).toBe('GET');
      req.flush({ valid: true });
    });

    it('should return false and clear state when token is invalid', () => {
      localStorage.setItem('auth_token', 'invalid-token');
      service.currentUser.set(mockUser);

      service.validateToken().subscribe((isValid) => {
        expect(isValid).toBe(false);
        expect(localStorage.getItem('auth_token')).toBeNull();
        expect(service.currentUser()).toBeNull();
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.AUTH.VERIFY}`);
      req.flush({ valid: false });
    });

    it('should return false and clear state on API error', () => {
      localStorage.setItem('auth_token', 'expired-token');
      service.currentUser.set(mockUser);

      service.validateToken().subscribe((isValid) => {
        expect(isValid).toBe(false);
        expect(localStorage.getItem('auth_token')).toBeNull();
        expect(service.currentUser()).toBeNull();
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.AUTH.VERIFY}`);
      req.error(new ProgressEvent('error'), { status: 401, statusText: 'Unauthorized' });
    });
  });

  describe('isAuthenticated', () => {
    it('should return true when token exists', () => {
      localStorage.setItem('auth_token', 'test-token');
      expect(service.isAuthenticated()).toBe(true);
    });

    it('should return false when no token exists', () => {
      expect(service.isAuthenticated()).toBe(false);
    });

    it('should return false when token is empty string', () => {
      localStorage.setItem('auth_token', '');
      expect(service.isAuthenticated()).toBe(false);
    });
  });
});
