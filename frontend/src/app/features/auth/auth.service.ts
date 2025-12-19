import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api.service';
import { API_ENDPOINTS } from '@core/constants/api-endpoints';
import { LoginRequest, LoginResponse, User } from '@shared/models/user.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiService = inject(ApiService);

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.apiService.post<LoginResponse>(API_ENDPOINTS.AUTH.LOGIN, credentials);
  }

  logout(): Observable<void> {
    return this.apiService.post<void>(API_ENDPOINTS.AUTH.LOGOUT, {});
  }

  register(userData: any): Observable<User> {
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

  isAuthenticated(): boolean {
    return !!this.getToken();
  }
}
