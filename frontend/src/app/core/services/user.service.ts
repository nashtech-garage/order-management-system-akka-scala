import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api-service';
import {
  User,
  CreateUserRequest,
  UpdateUserRequest,
  UpdateProfileRequest,
  ChangePasswordRequest,
  AccountStatusRequest,
  UserSearchRequest,
  UserListResponse,
  UserStatsResponse,
  BulkUserActionRequest,
  LoginRequest,
  LoginResponse,
} from '@shared/models/user.model';

@Injectable({
  providedIn: 'root',
})
export class UserService {
  private apiService = inject(ApiService);

  // Authentication
  login(request: LoginRequest): Observable<LoginResponse> {
    return this.apiService.post<LoginResponse>('/users/login', request);
  }

  logout(): Observable<{ message: string }> {
    return this.apiService.post<{ message: string }>('/users/logout');
  }

  verifyToken(): Observable<{ valid: string }> {
    return this.apiService.get<{ valid: string }>('/users/verify');
  }

  // User Registration
  register(request: CreateUserRequest): Observable<User> {
    return this.apiService.post<User>('/users/register', request);
  }

  // Profile Management
  getCurrentProfile(): Observable<User> {
    return this.apiService.get<User>('/users/profile');
  }

  updateProfile(request: UpdateProfileRequest): Observable<{ message: string }> {
    return this.apiService.put<{ message: string }>('/users/profile', request);
  }

  changePassword(request: ChangePasswordRequest): Observable<{ message: string }> {
    return this.apiService.put<{ message: string }>('/users/profile/password', request);
  }

  // User Management (Admin)
  getAllUsers(offset = 0, limit = 20): Observable<User[]> {
    return this.apiService.get<User[]>(`/users?offset=${offset}&limit=${limit}`);
  }

  getUserById(id: number): Observable<User> {
    return this.apiService.get<User>(`/users/${id}`);
  }

  searchUsers(request: UserSearchRequest): Observable<UserListResponse> {
    return this.apiService.post<UserListResponse>('/users/search', request);
  }

  updateUser(id: number, request: UpdateUserRequest): Observable<{ message: string }> {
    return this.apiService.put<{ message: string }>(`/users/${id}`, request);
  }

  deleteUser(id: number): Observable<{ message: string }> {
    return this.apiService.delete<{ message: string }>(`/users/${id}`);
  }

  // Account Status Management
  updateAccountStatus(id: number, request: AccountStatusRequest): Observable<{ message: string }> {
    return this.apiService.put<{ message: string }>(`/users/${id}/status`, request);
  }

  // Statistics
  getUserStats(): Observable<UserStatsResponse> {
    return this.apiService.get<UserStatsResponse>('/users/stats');
  }

  // Bulk Operations
  bulkAction(request: BulkUserActionRequest): Observable<{ message: string; affected: string }> {
    return this.apiService.post<{ message: string; affected: string }>('/users/bulk', request);
  }
}
