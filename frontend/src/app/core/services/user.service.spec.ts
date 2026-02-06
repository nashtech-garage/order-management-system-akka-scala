import { TestBed } from '@angular/core/testing';
import { UserService } from './user.service';
import { ApiService } from '@core/services/api-service';
import { of, throwError } from 'rxjs';
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

describe('UserService', () => {
  let service: UserService;
  let apiServiceSpy: jasmine.SpyObj<ApiService>;

  beforeEach(() => {
    const spy = jasmine.createSpyObj('ApiService', ['get', 'post', 'put', 'delete']);

    TestBed.configureTestingModule({
      providers: [UserService, { provide: ApiService, useValue: spy }],
    });

    service = TestBed.inject(UserService);
    apiServiceSpy = TestBed.inject(ApiService) as jasmine.SpyObj<ApiService>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Authentication', () => {
    describe('login', () => {
      it('should login a user', () => {
        const request: LoginRequest = {
          usernameOrEmail: 'testuser',
          password: 'password123',
        };
        const mockResponse: LoginResponse = {
          token: 'jwt-token',
          user: {
            id: 1,
            username: 'testuser',
            email: 'test@example.com',
            role: 'USER',
            createdAt: '2024-01-01T00:00:00Z',
            updatedAt: '2024-01-01T00:00:00Z',
          },
        };

        apiServiceSpy.post.and.returnValue(of(mockResponse));

        service.login(request).subscribe((response) => {
          expect(response).toEqual(mockResponse);
          expect(response.token).toBe('jwt-token');
        });

        expect(apiServiceSpy.post).toHaveBeenCalledWith('/users/login', request);
      });

      it('should handle login error', () => {
        const request: LoginRequest = {
          usernameOrEmail: 'testuser',
          password: 'wrongpassword',
        };
        const errorResponse = { status: 401, message: 'Invalid credentials' };

        apiServiceSpy.post.and.returnValue(throwError(() => errorResponse));

        service.login(request).subscribe({
          next: () => fail('should have failed'),
          error: (error) => {
            expect(error.status).toBe(401);
          },
        });

        expect(apiServiceSpy.post).toHaveBeenCalledWith('/users/login', request);
      });
    });

    describe('logout', () => {
      it('should logout a user', () => {
        const mockResponse = { message: 'Logged out successfully' };

        apiServiceSpy.post.and.returnValue(of(mockResponse));

        service.logout().subscribe((response) => {
          expect(response).toEqual(mockResponse);
        });

        expect(apiServiceSpy.post).toHaveBeenCalledWith('/users/logout');
      });
    });

    describe('verifyToken', () => {
      it('should verify a token', () => {
        const mockResponse = { valid: 'true' };

        apiServiceSpy.get.and.returnValue(of(mockResponse));

        service.verifyToken().subscribe((response) => {
          expect(response).toEqual(mockResponse);
        });

        expect(apiServiceSpy.get).toHaveBeenCalledWith('/users/verify');
      });
    });
  });

  describe('User Registration', () => {
    describe('register', () => {
      it('should register a new user', () => {
        const request: CreateUserRequest = {
          username: 'newuser',
          email: 'new@example.com',
          password: 'password123',
          role: 'USER',
        };
        const mockResponse: User = {
          id: 2,
          username: 'newuser',
          email: 'new@example.com',
          role: 'USER',
          createdAt: '2024-01-02T00:00:00Z',
          updatedAt: '2024-01-02T00:00:00Z',
        };

        apiServiceSpy.post.and.returnValue(of(mockResponse));

        service.register(request).subscribe((user) => {
          expect(user).toEqual(mockResponse);
          expect(user.username).toBe('newuser');
        });

        expect(apiServiceSpy.post).toHaveBeenCalledWith('/users/register', request);
      });
    });
  });

  describe('Profile Management', () => {
    describe('getCurrentProfile', () => {
      it('should retrieve current user profile', () => {
        const mockUser: User = {
          id: 1,
          username: 'testuser',
          email: 'test@example.com',
          role: 'USER',
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
        };

        apiServiceSpy.get.and.returnValue(of(mockUser));

        service.getCurrentProfile().subscribe((user) => {
          expect(user).toEqual(mockUser);
        });

        expect(apiServiceSpy.get).toHaveBeenCalledWith('/users/profile');
      });
    });

    describe('updateProfile', () => {
      it('should update user profile', () => {
        const request: UpdateProfileRequest = {
          email: 'updated@example.com',
        };
        const mockResponse = { message: 'Profile updated successfully' };

        apiServiceSpy.put.and.returnValue(of(mockResponse));

        service.updateProfile(request).subscribe((response) => {
          expect(response).toEqual(mockResponse);
        });

        expect(apiServiceSpy.put).toHaveBeenCalledWith('/users/profile', request);
      });
    });

    describe('changePassword', () => {
      it('should change user password', () => {
        const request: ChangePasswordRequest = {
          currentPassword: 'oldpass',
          newPassword: 'newpass123',
        };
        const mockResponse = { message: 'Password changed successfully' };

        apiServiceSpy.put.and.returnValue(of(mockResponse));

        service.changePassword(request).subscribe((response) => {
          expect(response).toEqual(mockResponse);
        });

        expect(apiServiceSpy.put).toHaveBeenCalledWith('/users/profile/password', request);
      });
    });
  });

  describe('User Management (Admin)', () => {
    describe('getAllUsers', () => {
      it('should retrieve all users with default pagination', () => {
        const mockUsers: User[] = [
          {
            id: 1,
            username: 'user1',
            email: 'user1@example.com',
            role: 'USER',
            createdAt: '2024-01-01T00:00:00Z',
            updatedAt: '2024-01-01T00:00:00Z',
          },
        ];

        apiServiceSpy.get.and.returnValue(of(mockUsers));

        service.getAllUsers().subscribe((users) => {
          expect(users).toEqual(mockUsers);
        });

        expect(apiServiceSpy.get).toHaveBeenCalledWith('/users?offset=0&limit=20');
      });

      it('should retrieve users with custom pagination', () => {
        const mockUsers: User[] = [];
        const offset = 40;
        const limit = 10;

        apiServiceSpy.get.and.returnValue(of(mockUsers));

        service.getAllUsers(offset, limit).subscribe((users) => {
          expect(users).toEqual(mockUsers);
        });

        expect(apiServiceSpy.get).toHaveBeenCalledWith(`/users?offset=${offset}&limit=${limit}`);
      });
    });

    describe('getUserById', () => {
      it('should retrieve a user by id', () => {
        const userId = 1;
        const mockUser: User = {
          id: userId,
          username: 'testuser',
          email: 'test@example.com',
          role: 'USER',
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
        };

        apiServiceSpy.get.and.returnValue(of(mockUser));

        service.getUserById(userId).subscribe((user) => {
          expect(user).toEqual(mockUser);
        });

        expect(apiServiceSpy.get).toHaveBeenCalledWith(`/users/${userId}`);
      });
    });

    describe('searchUsers', () => {
      it('should search users', () => {
        const request: UserSearchRequest = {
          query: 'john',
          role: 'USER',
          limit: 10,
          offset: 0,
        };
        const mockResponse: UserListResponse = {
          users: [],
          total: 0,
          offset: 0,
          limit: 10,
        };

        apiServiceSpy.post.and.returnValue(of(mockResponse));

        service.searchUsers(request).subscribe((response) => {
          expect(response).toEqual(mockResponse);
        });

        expect(apiServiceSpy.post).toHaveBeenCalledWith('/users/search', request);
      });
    });

    describe('updateUser', () => {
      it('should update a user', () => {
        const userId = 1;
        const request: UpdateUserRequest = {
          email: 'newemail@example.com',
          role: 'ADMIN',
        };
        const mockResponse = { message: 'User updated successfully' };

        apiServiceSpy.put.and.returnValue(of(mockResponse));

        service.updateUser(userId, request).subscribe((response) => {
          expect(response).toEqual(mockResponse);
        });

        expect(apiServiceSpy.put).toHaveBeenCalledWith(`/users/${userId}`, request);
      });
    });

    describe('deleteUser', () => {
      it('should delete a user', () => {
        const userId = 5;
        const mockResponse = { message: 'User deleted successfully' };

        apiServiceSpy.delete.and.returnValue(of(mockResponse));

        service.deleteUser(userId).subscribe((response) => {
          expect(response).toEqual(mockResponse);
        });

        expect(apiServiceSpy.delete).toHaveBeenCalledWith(`/users/${userId}`);
      });
    });
  });

  describe('Account Status Management', () => {
    describe('updateAccountStatus', () => {
      it('should update account status', () => {
        const userId = 1;
        const request: AccountStatusRequest = {
          status: 'active',
        };
        const mockResponse = { message: 'Account status updated' };

        apiServiceSpy.put.and.returnValue(of(mockResponse));

        service.updateAccountStatus(userId, request).subscribe((response) => {
          expect(response).toEqual(mockResponse);
        });

        expect(apiServiceSpy.put).toHaveBeenCalledWith(`/users/${userId}/status`, request);
      });
    });
  });

  describe('Statistics', () => {
    describe('getUserStats', () => {
      it('should retrieve user statistics', () => {
        const mockStats: UserStatsResponse = {
          totalUsers: 100,
          activeUsers: 85,
          lockedUsers: 15,
        };

        apiServiceSpy.get.and.returnValue(of(mockStats));

        service.getUserStats().subscribe((stats) => {
          expect(stats).toEqual(mockStats);
        });

        expect(apiServiceSpy.get).toHaveBeenCalledWith('/users/stats');
      });
    });
  });

  describe('Bulk Operations', () => {
    describe('bulkAction', () => {
      it('should perform bulk action on users', () => {
        const request: BulkUserActionRequest = {
          action: 'delete',
          userIds: [1, 2, 3],
        };
        const mockResponse = {
          message: 'Bulk action completed',
          affected: '3',
        };

        apiServiceSpy.post.and.returnValue(of(mockResponse));

        service.bulkAction(request).subscribe((response) => {
          expect(response).toEqual(mockResponse);
          expect(response.affected).toBe('3');
        });

        expect(apiServiceSpy.post).toHaveBeenCalledWith('/users/bulk', request);
      });
    });
  });
});
