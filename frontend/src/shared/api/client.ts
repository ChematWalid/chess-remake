import axios, { AxiosError, type AxiosRequestConfig } from "axios";
import { z } from "zod";
import type { AppResponse, BackendError } from "./types";

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
let refreshPromise: Promise<string | null> | null = null;
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
      refreshPromise ??= refreshAccessToken().finally(() => {
        refreshPromise = null;
      });
      const refreshed = await refreshPromise;
      if (refreshed) {
        config.headers = {
          ...config.headers,
          Authorization: `Bearer ${refreshed}`,
        };
        return apiClient(config);
      }
      onSessionExpired?.();
    }
    const body = error.response?.data as Partial<BackendError> | undefined;
    throw new ApiError(
      body?.errorCode ?? "NETWORK_ERROR",
      body?.message ?? error.message,
      error.response?.status ?? 0,
    );
  },
);

async function refreshAccessToken(): Promise<string | null> {
  try {
    const response = await axios.post<AppResponse<{ accessToken: string }>>(
      `${baseURL}/api/auth/refresh`,
      {},
      { withCredentials: true },
    );
    const parsed = envelopeSchema.parse(response.data) as AppResponse<{
      accessToken: string;
    }>;
    if (!parsed.success || !parsed.data) return null;
    setAccessToken(parsed.data.accessToken);
    return parsed.data.accessToken;
  } catch {
    return null;
  }
}

export function refreshSessionAccessToken(): Promise<string | null> {
  refreshPromise ??= refreshAccessToken().finally(() => {
    refreshPromise = null;
  });
  return refreshPromise.then((token) => {
    if (!token && !accessToken) onSessionExpired?.();
    return token;
  });
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
