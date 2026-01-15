import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api-service';
import { API_ENDPOINTS } from '@core/constants/api-endpoints';
import {
  User,
  UpdateProfileRequest,
  ChangePasswordRequest,
  MessageResponse,
} from '@shared/models/user.model';

@Injectable({
  providedIn: 'root',
})
export class UserProfileService {
  private apiService = inject(ApiService);

  /**
   * Get the current user's profile
   */
  getProfile(): Observable<User> {
    return this.apiService.get<User>(API_ENDPOINTS.PROFILE.GET);
  }

  /**
   * Update the current user's profile (email and/or username)
   */
  updateProfile(data: UpdateProfileRequest): Observable<MessageResponse> {
    return this.apiService.put<MessageResponse>(API_ENDPOINTS.PROFILE.UPDATE, data);
  }

  /**
   * Change the current user's password
   */
  changePassword(data: ChangePasswordRequest): Observable<MessageResponse> {
    return this.apiService.put<MessageResponse>(API_ENDPOINTS.PROFILE.CHANGE_PASSWORD, data);
  }
}
