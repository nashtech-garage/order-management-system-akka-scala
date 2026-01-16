// User Models and Interfaces
export interface User {
  id: number;
  username: string;
  email: string;
  role: string;
  status?: UserStatus;
  phoneNumber?: string;
  lastLogin?: string;
  createdAt: string;
  updatedAt?: string;
}

export type UserStatus = 'active' | 'locked';

export enum UserRole {
  ADMIN = 'admin',
  USER = 'user',
}

export interface CreateUserRequest {
  username: string;
  email: string;
  password: string;
  role?: string;
  phoneNumber?: string;
}

export interface UpdateUserRequest {
  email?: string;
  role?: string;
  status?: string;
  phoneNumber?: string;
}

export interface LoginRequest {
  usernameOrEmail: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  user: User;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface UpdateProfileRequest {
  email?: string;
  username?: string;
  phoneNumber?: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface AccountStatusRequest {
  status: UserStatus;
  reason?: string;
}

export interface UserSearchRequest {
  query?: string;
  role?: string;
  status?: string;
  offset?: number;
  limit?: number;
}

export interface UserListResponse {
  users: User[];
  total: number;
  offset: number;
  limit: number;
}

export interface UserStatsResponse {
  totalUsers: number;
  activeUsers: number;
  lockedUsers: number;
}

export interface BulkUserActionRequest {
  userIds: number[];
  action: 'activate' | 'suspend' | 'delete';
  params?: Record<string, string>;
}

export interface MessageResponse {
  message: string;
}

export interface ErrorResponse {
  error: string;
}

// UI Helper Constants
export const USER_STATUS_LABELS: Record<UserStatus, string> = {
  active: 'Active',
  locked: 'Locked',
};

export const USER_STATUS_COLORS: Record<UserStatus, string> = {
  active: 'bg-green-100 text-green-800',
  locked: 'bg-red-100 text-red-800',
};

export const USER_ROLE_LABELS: Record<string, string> = {
  user: 'User',
  admin: 'Admin',
};
