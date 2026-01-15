export interface User {
  id: number;
  username: string;
  email: string;
  role: string;
  createdAt: string;
}

export enum UserRole {
  ADMIN = 'admin',
  USER = 'user',
}

export interface LoginRequest {
  username: string;
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
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface MessageResponse {
  message: string;
}

export interface ErrorResponse {
  error: string;
}
