import { createBackendApiClient, type BackendApiClientOptions } from "@test-agent/backend-api";
import type { ApiFailure, ApiResponse } from "@test-agent/shared-types";

export type PlatformApiOptions = BackendApiClientOptions;

type RequestPayload = Record<string, unknown> | undefined;

export type ProviderAuthPayload = {
  type?: "api-key" | "oauth" | string;
  key?: string;
  token?: string;
  callbackUrl?: string;
  [key: string]: unknown;
};

export type WorktreePayload = {
  workspaceId?: string;
  sessionId?: string;
  branch?: string;
  path?: string;
  [key: string]: unknown;
};

export function createPlatformApi(options: PlatformApiOptions = {}) {
  const baseUrl = (options.baseUrl ?? readViteEnv("VITE_TEST_AGENT_API_BASE_URL") ?? "").replace(/\/$/, "");
  const api = createBackendApiClient({ ...options, baseUrl });
  const request = createRawRequest({ ...options, baseUrl });

  return {
    ...api,
    getConfig: (workspaceId?: string) => request<unknown>(`/api/config${query({ workspaceId })}`),
    updateConfig: (payload: Record<string, unknown>, workspaceId?: string) =>
      request<unknown>(`/api/config${query({ workspaceId })}`, { method: "PATCH", body: JSON.stringify(payload) }),
    disposeGlobal: () => request<unknown>("/api/global/dispose", { method: "POST" }),
    listProviderAuth: (workspaceId?: string) => request<unknown>(`/api/provider/auth${query({ workspaceId })}`),
    authorizeProviderOAuth: (providerId: string, payload?: ProviderAuthPayload) =>
      request<unknown>(`/api/provider/${encodeURIComponent(providerId)}/oauth/authorize`, post(payload)),
    completeProviderOAuth: (providerId: string, payload?: ProviderAuthPayload) =>
      request<unknown>(`/api/provider/${encodeURIComponent(providerId)}/oauth/callback`, post(payload)),
    setProviderAuth: (providerId: string, payload: ProviderAuthPayload) =>
      request<unknown>(`/api/auth/${encodeURIComponent(providerId)}`, { method: "PUT", body: JSON.stringify(payload) }),
    removeProviderAuth: (providerId: string) => request<unknown>(`/api/auth/${encodeURIComponent(providerId)}`, { method: "DELETE" }),
    listWorktrees: (workspaceId?: string) => request<unknown>(`/api/worktrees${query({ workspaceId })}`),
    createWorktree: (payload: WorktreePayload) => request<unknown>("/api/worktrees", post(payload)),
    removeWorktree: (payload: WorktreePayload) => request<unknown>("/api/worktrees", { method: "DELETE", body: JSON.stringify(payload) }),
    resetWorktree: (payload: WorktreePayload) => request<unknown>("/api/worktrees/reset", post(payload)),
    shareSession: (sessionId: string) => request<unknown>(`/api/sessions/${encodeURIComponent(sessionId)}/share`, post()),
    unshareSession: (sessionId: string) =>
      request<unknown>(`/api/sessions/${encodeURIComponent(sessionId)}/share`, { method: "DELETE" }),
    connectMcp: (name: string, payload?: RequestPayload) => request<unknown>(`/api/mcp/${encodeURIComponent(name)}/connect`, post(payload)),
    disconnectMcp: (name: string, payload?: RequestPayload) =>
      request<unknown>(`/api/mcp/${encodeURIComponent(name)}/disconnect`, post(payload)),
    startMcpAuth: (name: string, payload?: RequestPayload) => request<unknown>(`/api/mcp/${encodeURIComponent(name)}/auth`, post(payload)),
    completeMcpAuth: (name: string, payload?: RequestPayload) =>
      request<unknown>(`/api/mcp/${encodeURIComponent(name)}/auth/callback`, post(payload)),
    authenticateMcp: (name: string, payload?: RequestPayload) =>
      request<unknown>(`/api/mcp/${encodeURIComponent(name)}/auth/authenticate`, post(payload)),
    removeMcpAuth: (name: string) => request<unknown>(`/api/mcp/${encodeURIComponent(name)}/auth`, { method: "DELETE" })
  };
}

export type PlatformApi = ReturnType<typeof createPlatformApi>;

function createRawRequest(options: PlatformApiOptions & { baseUrl: string }) {
  const fetcher = options.fetcher ?? fetch;
  const traceIdFactory = options.traceIdFactory ?? defaultTraceId;

  return async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const traceId = traceIdFactory();
    const headers = new Headers(init.headers);
    headers.set("Accept", "application/json");
    headers.set("X-Trace-Id", traceId);
    if (init.body != null && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    if (options.apiToken) {
      headers.set("Authorization", `Bearer ${options.apiToken}`);
    }
    const response = await fetcher(`${options.baseUrl}${path}`, { ...init, headers });
    const body = await readJson(response);
    if (!response.ok || !isSuccess<T>(body)) {
      throw normalizeError(response.status, body, traceId);
    }
    return body.data;
  };
}

function post(payload?: RequestPayload): RequestInit {
  return {
    method: "POST",
    body: payload == null ? undefined : JSON.stringify(payload)
  };
}

function query(values: Record<string, string | number | undefined>) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== "") {
      params.set(key, String(value));
    }
  });
  const encoded = params.toString();
  return encoded ? `?${encoded}` : "";
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    return { success: true, traceId: response.headers.get("X-Trace-Id") ?? "trace_unknown", data: undefined };
  }
  try {
    return JSON.parse(text);
  } catch {
    return { success: false, traceId: response.headers.get("X-Trace-Id") ?? "trace_unknown", code: "BAD_RESPONSE", message: text };
  }
}

function isSuccess<T>(body: unknown): body is ApiResponse<T> & { success: true } {
  return typeof body === "object" && body !== null && (body as { success?: unknown }).success === true;
}

function normalizeError(status: number, body: unknown, fallbackTraceId: string) {
  const failure = (typeof body === "object" && body !== null ? body : {}) as Partial<ApiFailure>;
  const error = new Error(failure.message ?? `平台 API 请求失败：HTTP ${status}`);
  Object.assign(error, {
    status,
    code: failure.code ?? `HTTP_${status}`,
    traceId: failure.traceId ?? fallbackTraceId,
    retryable: failure.retryable ?? status >= 500,
    details: failure.details ?? {}
  });
  return error;
}

function readViteEnv(key: string): string | undefined {
  return (import.meta as unknown as { env?: Record<string, string | undefined> }).env?.[key];
}

function defaultTraceId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return `trace_${crypto.randomUUID().replaceAll("-", "")}`;
  }
  return `trace_${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`;
}
