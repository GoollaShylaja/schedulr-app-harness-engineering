import { request } from "../api-client";
import type {
  AuthResponse,
  LoginRequest,
  PasswordChangeRequest,
  ProfileUpdateRequest,
  UserResponse,
} from "../types";

export function login(body: LoginRequest): Promise<AuthResponse> {
  return request<AuthResponse>("/auth/login", { method: "POST", body });
}

export function getCurrentUser(): Promise<UserResponse> {
  return request<UserResponse>("/auth/me");
}

export function updateProfile(body: ProfileUpdateRequest): Promise<UserResponse> {
  return request<UserResponse>("/auth/me/profile", { method: "PATCH", body });
}

export function changePassword(body: PasswordChangeRequest): Promise<void> {
  return request<void>("/auth/me/change-password", { method: "POST", body });
}
