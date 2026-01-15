import { TestBed } from '@angular/core/testing';
import { UserProfileService } from './user-profile.service';
import { ApiService } from '@core/services/api-service';
import { of } from 'rxjs';

describe('UserProfileService', () => {
  let service: UserProfileService;
  let mockApiService: jasmine.SpyObj<ApiService>;

  const mockUser = {
    id: 1,
    username: 'testuser',
    email: 'test@example.com',
    role: 'USER',
    createdAt: '2026-01-13T10:00:00',
  };

  beforeEach(() => {
    mockApiService = jasmine.createSpyObj('ApiService', ['get', 'put']);

    TestBed.configureTestingModule({
      providers: [UserProfileService, { provide: ApiService, useValue: mockApiService }],
    });

    service = TestBed.inject(UserProfileService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should get user profile', (done) => {
    mockApiService.get.and.returnValue(of(mockUser));

    service.getProfile().subscribe((user) => {
      expect(user).toEqual(mockUser);
      expect(mockApiService.get).toHaveBeenCalledWith('/users/profile');
      done();
    });
  });

  it('should update user profile', (done) => {
    const updateData = { email: 'newemail@example.com' };
    const response = { message: 'Profile updated successfully' };
    mockApiService.put.and.returnValue(of(response));

    service.updateProfile(updateData).subscribe((res) => {
      expect(res).toEqual(response);
      expect(mockApiService.put).toHaveBeenCalledWith('/users/profile', updateData);
      done();
    });
  });

  it('should change password', (done) => {
    const passwordData = {
      currentPassword: 'oldpass',
      newPassword: 'newpass123',
    };
    const response = { message: 'Password changed successfully' };
    mockApiService.put.and.returnValue(of(response));

    service.changePassword(passwordData).subscribe((res) => {
      expect(res).toEqual(response);
      expect(mockApiService.put).toHaveBeenCalledWith('/users/profile/password', passwordData);
      done();
    });
  });
});
