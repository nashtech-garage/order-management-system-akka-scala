import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
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
      })
    );
  }

  logout(): Observable<void> {
    return this.apiService.post<void>(API_ENDPOINTS.AUTH.LOGOUT, {}).pipe(
      tap(() => {
        this.removeToken();
        this.currentUser.set(null);
      })
    );
  }

  register(userData: RegisterRequest): Observable<LoginResponse> {
    return this.apiService.post<LoginResponse>(API_ENDPOINTS.AUTH.REGISTER, userData).pipe(
      tap((response) => {
        this.setToken(response.token);
        this.currentUser.set(response.user);
      })
    );
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

  isAuthenticated(): boolean {
    return !!this.getToken();
  }
}
