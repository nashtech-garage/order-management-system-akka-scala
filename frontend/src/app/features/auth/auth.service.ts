import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap, of, map, catchError } from 'rxjs';
import { ApiService } from '@core/services/api-service';
import { API_ENDPOINTS } from '@core/constants/api-endpoints';
import { LoginRequest, LoginResponse, RegisterRequest, User } from '@shared/models/user.model';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private apiService = inject(ApiService);
  currentUser = signal<User | null>(null);

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.apiService.post<LoginResponse>(API_ENDPOINTS.AUTH.LOGIN, credentials).pipe(
      tap((response) => {
        this.setToken(response.token);
        this.currentUser.set(response.user);
      }),
    );
  }

  logout(): Observable<void> {
    const token = this.getToken();
    if (!token) {
      // No token to invalidate, just clear local state
      this.removeToken();
      this.currentUser.set(null);
      return of(undefined);
    }

    return this.apiService.post<void>(API_ENDPOINTS.AUTH.LOGOUT, {}).pipe(
      tap(() => {
        this.removeToken();
        this.currentUser.set(null);
      }),
    );
  }

  register(userData: RegisterRequest): Observable<User> {
    return this.apiService.post<User>(API_ENDPOINTS.AUTH.REGISTER, userData);
  }

  getToken(): string | null {
    return localStorage.getItem('auth_token');
  }

  setToken(token: string): void {
    localStorage.setItem('auth_token', token);
  }

  removeToken(): void {
    localStorage.removeItem('auth_token');
  }

  validateToken(): Observable<boolean> {
    const token = this.getToken();
    if (!token) {
      return of(false);
    }

    // Call backend to verify token is valid
    return this.apiService.get<{ valid: boolean }>(API_ENDPOINTS.AUTH.VERIFY).pipe(
      map((response) => {
        if (!response.valid) {
          this.removeToken();
          this.currentUser.set(null);
          return false;
        }
        return true;
      }),
      catchError(() => {
        // If error occurs (e.g., 401), token is invalid
        this.removeToken();
        this.currentUser.set(null);
        return of(false);
      }),
    );
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }
}
