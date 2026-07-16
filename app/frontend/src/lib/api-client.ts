import type { ApiResponse } from "./types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1";
const TOKEN_KEY = "schedulr_token";

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

let onUnauthorized: (() => void) | null = null;

export function registerUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler;
}

type QueryParams = Record<string, string | number | undefined>;

interface RequestOptions {
  method?: string;
  body?: unknown;
  query?: QueryParams;
}

function buildUrl(path: string, query?: QueryParams): string {
  const url = new URL(`${API_BASE_URL}${path}`);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== "") {
        url.searchParams.set(key, String(value));
      }
    }
  }
  return url.toString();
}

function authHeaders(): HeadersInit {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function handleUnauthorized(): Promise<void> {
  clearToken();
  if (onUnauthorized) onUnauthorized();
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, query } = options;

  const response = await fetch(buildUrl(path, query), {
    method,
    headers: {
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...authHeaders(),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (response.status === 401) {
    await handleUnauthorized();
    throw new ApiError(401, "Could not validate credentials");
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const envelope = (await response.json()) as ApiResponse<T>;

  if (!response.ok || !envelope.success) {
    throw new ApiError(response.status, envelope.message ?? "Request failed");
  }

  return envelope.data;
}

export async function requestBlob(
  path: string,
  query?: RequestOptions["query"],
): Promise<{ blob: Blob; filename: string }> {
  const response = await fetch(buildUrl(path, query), {
    method: "GET",
    headers: { ...authHeaders() },
  });

  if (response.status === 401) {
    await handleUnauthorized();
    throw new ApiError(401, "Could not validate credentials");
  }

  if (!response.ok) {
    let message = "Request failed";
    try {
      const envelope = (await response.json()) as ApiResponse<unknown>;
      message = envelope.message ?? message;
    } catch {
      // response wasn't JSON — keep default message
    }
    throw new ApiError(response.status, message);
  }

  const disposition = response.headers.get("Content-Disposition") ?? "";
  const match = /filename=([^;]+)/.exec(disposition);
  const filename = match ? match[1].trim() : "download";

  return { blob: await response.blob(), filename };
}
