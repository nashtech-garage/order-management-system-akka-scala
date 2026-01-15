import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UserProfileComponent } from './user-profile';
import { UserProfileService } from '../auth/user-profile.service';
import { AuthService } from '../auth/auth.service';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';

describe('UserProfileComponent', () => {
  let component: UserProfileComponent;
  let fixture: ComponentFixture<UserProfileComponent>;
  let mockUserProfileService: jasmine.SpyObj<UserProfileService>;
  let mockAuthService: jasmine.SpyObj<AuthService>;

  const mockUser = {
    id: 1,
    username: 'testuser',
    email: 'test@example.com',
    role: 'USER',
    createdAt: '2026-01-13T10:00:00',
  };

  beforeEach(async () => {
    mockUserProfileService = jasmine.createSpyObj('UserProfileService', [
      'getProfile',
      'updateProfile',
      'changePassword',
    ]);
    mockAuthService = jasmine.createSpyObj('AuthService', ['getToken'], {
      currentUser: signal(mockUser),
    });

    await TestBed.configureTestingModule({
      imports: [UserProfileComponent],
      providers: [
        { provide: UserProfileService, useValue: mockUserProfileService },
        { provide: AuthService, useValue: mockAuthService },
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(UserProfileComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load user profile on init', () => {
    mockUserProfileService.getProfile.and.returnValue(of(mockUser));

    fixture.detectChanges();

    expect(mockUserProfileService.getProfile).toHaveBeenCalled();
    expect(component.user()).toEqual(mockUser);
    expect(component.profileForm.get('username')?.value).toBe(mockUser.username);
    expect(component.profileForm.get('email')?.value).toBe(mockUser.email);
  });

  it('should handle profile load error', () => {
    mockUserProfileService.getProfile.and.returnValue(throwError(() => new Error('Network error')));

    fixture.detectChanges();

    expect(component.error()).toBeTruthy();
  });

  it('should update profile successfully', () => {
    mockUserProfileService.getProfile.and.returnValue(of(mockUser));
    mockUserProfileService.updateProfile.and.returnValue(
      of({ message: 'Profile updated successfully' }),
    );

    fixture.detectChanges();

    component.profileForm.patchValue({
      username: 'newusername',
      email: 'newemail@example.com',
    });

    component.updateProfile();

    expect(mockUserProfileService.updateProfile).toHaveBeenCalledWith({
      username: 'newusername',
      email: 'newemail@example.com',
    });
  });

  it('should change password successfully', () => {
    mockUserProfileService.getProfile.and.returnValue(of(mockUser));
    mockUserProfileService.changePassword.and.returnValue(
      of({ message: 'Password changed successfully' }),
    );

    fixture.detectChanges();

    component.passwordForm.patchValue({
      currentPassword: 'oldpass',
      newPassword: 'newpass123',
      confirmPassword: 'newpass123',
    });

    component.changePassword();

    expect(mockUserProfileService.changePassword).toHaveBeenCalledWith({
      currentPassword: 'oldpass',
      newPassword: 'newpass123',
    });
  });

  it('should validate password mismatch', () => {
    mockUserProfileService.getProfile.and.returnValue(of(mockUser));
    fixture.detectChanges();

    component.passwordForm.patchValue({
      currentPassword: 'oldpass',
      newPassword: 'newpass123',
      confirmPassword: 'different',
    });

    expect(component.passwordForm.hasError('mismatch')).toBe(true);
  });

  it('should switch tabs', () => {
    expect(component.activeTab()).toBe('profile');

    component.setActiveTab('password');
    expect(component.activeTab()).toBe('password');

    component.setActiveTab('profile');
    expect(component.activeTab()).toBe('profile');
  });
});
