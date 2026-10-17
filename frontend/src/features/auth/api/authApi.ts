import { apiPost } from "../../../shared/api/client";
import {
  authResponseSchema,
  type AuthResponse,
} from "../../../shared/api/types";

export interface LoginCredentials {
  identifier: string;
  password: string;
}
export interface RegisterDetails {
  username: string;
  email: string;
  password: string;
  displayName?: string;
}
export async function login(
  credentials: LoginCredentials,
): Promise<AuthResponse> {
  return apiPost("/api/auth/login", credentials, undefined, authResponseSchema);
}
export async function register(details: RegisterDetails): Promise<void> {
  return apiPost("/api/auth/register", details);
}
export async function verifyEmail(token: string): Promise<void> {
  return apiPost("/api/auth/verify-email", { token });
}
export async function requestPasswordReset(email: string): Promise<void> {
  return apiPost("/api/auth/forgot-password", { email });
}
export async function resetPassword(
  token: string,
  password: string,
): Promise<void> {
  return apiPost("/api/auth/reset-password", { token, password });
}
export async function exchangeOAuthCode(code: string): Promise<AuthResponse> {
  return apiPost(
    "/api/auth/oauth/exchange",
    { code },
    undefined,
    authResponseSchema,
  );
}
export async function logout(): Promise<void> {
  return apiPost("/api/auth/logout");
}
let sessionRestore: Promise<AuthResponse> | null = null;
export function restoreSession(): Promise<AuthResponse> {
  if (!sessionRestore) {
    sessionRestore = apiPost(
      "/api/auth/refresh",
      undefined,
      undefined,
      authResponseSchema,
    ).finally(() => {
      sessionRestore = null;
    });
  }
  return sessionRestore;
}
export async function completeOAuthOnboarding(
  username: string,
): Promise<AuthResponse> {
  return apiPost(
    "/api/auth/oauth/onboarding",
    { username },
    undefined,
    authResponseSchema,
  );
}
