import axios, { AxiosError, type AxiosRequestConfig } from "axios";
import { z } from "zod";
import {
  authResponseSchema,
  type AppResponse,
  type AuthResponse,
  type BackendError,
} from "./types";

const envelopeSchema = z.object({
  success: z.boolean(),
  code: z.number().nullable().optional(),
  errorCode: z.string().nullable().optional(),
  message: z.string(),
  data: z.unknown().nullable().optional(),
  errors: z.record(z.string(), z.string()).nullable().optional(),
  timestamp: z.string().nullable().optional(),
});

const baseURL = import.meta.env.VITE_API_BASE_URL ?? "";
export const apiClient = axios.create({
  baseURL,
  withCredentials: true,
  headers: { "Content-Type": "application/json" },
});

let accessToken: string | null = null;
let refreshPromise: Promise<AuthResponse> | null = null;
let onSessionExpired: (() => void) | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}
export function getAccessToken(): string | null {
  return accessToken;
}
export function setSessionExpiredHandler(handler: () => void): void {
  onSessionExpired = handler;
}

apiClient.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});

apiClient.interceptors.response.use(
  (response) => {
    if (
      typeof response.data === "object" &&
      response.data !== null &&
      "success" in response.data
    ) {
      const envelope = envelopeSchema.parse(
        response.data,
      ) as AppResponse<unknown>;
      if (!envelope.success)
        throw new ApiError(
          envelope.errorCode ?? "REQUEST_FAILED",
          envelope.message,
          envelope.code ?? 400,
        );
      response.data = envelope.data;
    }
    return response;
  },
  async (error: AxiosError) => {
    const config = error.config as
      (AxiosRequestConfig & { _retried?: boolean }) | undefined;
    if (
      error.response?.status === 401 &&
      config &&
      !config._retried &&
      !config.url?.includes("/api/auth/")
    ) {
      config._retried = true;
      const refreshed = await refreshAuthSession();
      config.headers = {
        ...config.headers,
        Authorization: `Bearer ${refreshed.accessToken}`,
      };
      return apiClient(config);
    }
    const body = error.response?.data as Partial<BackendError> | undefined;
    throw new ApiError(
      body?.errorCode ?? "NETWORK_ERROR",
      body?.message ?? error.message,
      error.response?.status ?? 0,
    );
  },
);

async function refreshAuthSessionRequest(): Promise<AuthResponse> {
  try {
    const response = await axios.post<AppResponse<unknown>>(
      `${baseURL}/api/auth/refresh`,
      {},
      { withCredentials: true },
    );
    const parsed = envelopeSchema.parse(response.data) as AppResponse<unknown>;
    if (!parsed.success || parsed.data == null) {
      throw new ApiError(
        parsed.errorCode ?? "REFRESH_FAILED",
        parsed.message,
        parsed.code ?? 401,
      );
    }
    const auth = authResponseSchema.parse(parsed.data);
    setAccessToken(auth.accessToken);
    return auth;
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) onSessionExpired?.();
      throw error;
    }
    if (axios.isAxiosError(error)) {
      const body = error.response?.data as Partial<BackendError> | undefined;
      const refreshError = new ApiError(
        body?.errorCode ?? "NETWORK_ERROR",
        body?.message ?? error.message,
        error.response?.status ?? 0,
      );
      if (refreshError.status === 401) onSessionExpired?.();
      throw refreshError;
    }
    throw error;
  }
}

export function refreshAuthSession(): Promise<AuthResponse> {
  refreshPromise ??= refreshAuthSessionRequest().finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

export function refreshSessionAccessToken(): Promise<string | null> {
  return refreshAuthSession()
    .then((auth) => auth.accessToken)
    .catch(() => null);
}

export class ApiError extends Error {
  constructor(
    public readonly errorCode: string,
    message: string,
    public readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export async function apiGet<T>(
  path: string,
  config?: AxiosRequestConfig,
  schema?: z.ZodType<T>,
): Promise<T> {
  const response = await apiClient.get<T>(path, config);
  return schema ? schema.parse(response.data) : response.data;
}

export async function apiPost<TResponse, TRequest = unknown>(
  path: string,
  data?: TRequest,
  config?: AxiosRequestConfig,
  schema?: z.ZodType<TResponse>,
): Promise<TResponse> {
  const response = await apiClient.post<TResponse>(path, data, config);
  return schema ? schema.parse(response.data) : response.data;
}

export async function apiPut<TResponse, TRequest = unknown>(
  path: string,
  data: TRequest,
  schema?: z.ZodType<TResponse>,
): Promise<TResponse> {
  const response = await apiClient.put<TResponse>(path, data);
  return schema ? schema.parse(response.data) : response.data;
}

export async function apiDelete(path: string): Promise<void> {
  await apiClient.delete(path);
}
export async function apiDownloadText(path: string): Promise<string> {
  const response = await apiClient.get<string>(path, { responseType: "text" });
  return response.data;
}
