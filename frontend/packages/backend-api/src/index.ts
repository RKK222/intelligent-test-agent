import type {
  ApiFailure,
  ApiResponse,
  FileContent,
  FileStatus,
  FileTreeEntry,
  PageResponse,
  Run,
  RunDiff,
  RunDiffAction,
  Session,
  Workspace
} from "@test-agent/shared-types";

export type BackendApiClientOptions = {
  baseUrl?: string;
  apiToken?: string;
  fetcher?: typeof fetch;
  traceIdFactory?: () => string;
};

export class BackendApiError extends Error {
  readonly code: string;
  readonly traceId: string;
  readonly details: Record<string, unknown>;
  readonly retryable: boolean;
  readonly status: number;

  constructor(status: number, failure: ApiFailure) {
    super(failure.message);
    this.name = "BackendApiError";
    this.status = status;
    this.code = failure.code;
    this.traceId = failure.traceId;
    this.details = failure.details ?? {};
    this.retryable = failure.retryable ?? (status >= 500 || status === 408 || status === 429);
  }
}

export type BackendApiClient = ReturnType<typeof createBackendApiClient>;

export function createBackendApiClient(options: BackendApiClientOptions = {}) {
  const env = (globalThis as unknown as { process?: { env?: Record<string, string | undefined> } }).process?.env;
  const baseUrl = (options.baseUrl ?? env?.NEXT_PUBLIC_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080").replace(
    /\/$/,
    ""
  );
  const fetcher = options.fetcher ?? fetch;
  const traceIdFactory = options.traceIdFactory ?? defaultTraceId;

  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
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
    const response = await fetcher(`${baseUrl}${path}`, { ...init, headers });
    const body = await readJson(response);
    if (!response.ok || !isSuccessResponse<T>(body)) {
      throw new BackendApiError(response.status, normalizeFailure(body, traceId, response.status));
    }
    return body.data;
  }

  return {
    listWorkspaces: (page = 1, size = 20) =>
      request<PageResponse<Workspace>>(`/api/workspaces?page=${page}&size=${size}`),
    createWorkspace: (payload: { name: string; rootPath: string }) =>
      request<Workspace>("/api/workspaces", { method: "POST", body: JSON.stringify(payload) }),
    listFiles: async (workspaceId: string, path = "") => {
      const entries = await request<BackendFileTreeEntry[]>(`/api/workspaces/${workspaceId}/files${query({ path })}`);
      return entries.map((entry) => ({
        path: entry.path,
        name: entry.name,
        type: entry.directory ? "directory" : "file",
        size: entry.size,
        modifiedAt: entry.lastModifiedAt
      })) satisfies FileTreeEntry[];
    },
    readFile: async (workspaceId: string, path: string) => {
      const file = await request<BackendFileContent>(`/api/workspaces/${workspaceId}/files/content${query({ path })}`);
      return { ...file, encoding: "utf-8", readonly: false } satisfies FileContent;
    },
    writeFile: (workspaceId: string, path: string, content: string) =>
      request<void>(`/api/workspaces/${workspaceId}/files/content`, {
        method: "PUT",
        body: JSON.stringify({ path, content })
      }),
    fileStatus: async (workspaceId: string, path: string) => {
      const status = await request<BackendFileStatus>(`/api/workspaces/${workspaceId}/files/status${query({ path })}`);
      return {
        ...status,
        status: status.exists ? "unchanged" : "deleted"
      } satisfies FileStatus;
    },
    listSessions: (workspaceId: string, page = 1, size = 20) =>
      request<PageResponse<Session>>(`/api/workspaces/${workspaceId}/sessions?page=${page}&size=${size}`),
    createSession: (workspaceId: string, title: string) =>
      request<Session>("/api/sessions", { method: "POST", body: JSON.stringify({ workspaceId, title }) }),
    startRun: (sessionId: string, prompt: string) =>
      request<Run>("/api/runs", { method: "POST", body: JSON.stringify({ sessionId, prompt }) }),
    getRun: (runId: string) => request<Run>(`/api/runs/${runId}`),
    cancelRun: (runId: string) => request<Run>(`/api/runs/${runId}/cancel`, { method: "POST" }),
    getRunDiff: (runId: string) => request<RunDiff>(`/api/runs/${runId}/diff`),
    acceptRunDiff: (runId: string) => request<RunDiffAction>(`/api/runs/${runId}/diff/accept`, { method: "POST" }),
    rejectRunDiff: (runId: string) => request<RunDiffAction>(`/api/runs/${runId}/diff/reject`, { method: "POST" })
  };
}

type BackendFileTreeEntry = {
  path: string;
  name: string;
  directory: boolean;
  size: number;
  lastModifiedAt?: string;
};

type BackendFileContent = {
  path: string;
  content: string;
  size: number;
};

type BackendFileStatus = {
  path: string;
  exists: boolean;
  directory: boolean;
  size: number;
  lastModifiedAt?: string;
};

function isSuccessResponse<T>(body: unknown): body is ApiResponse<T> & { success: true } {
  return typeof body === "object" && body !== null && (body as { success?: unknown }).success === true;
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    return { success: true, data: undefined, traceId: response.headers.get("X-Trace-Id") ?? "trace_unknown" };
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return { success: false, code: "BAD_RESPONSE", message: text, traceId: response.headers.get("X-Trace-Id") ?? "trace_unknown" };
  }
}

function normalizeFailure(body: unknown, fallbackTraceId: string, status: number): ApiFailure {
  if (typeof body === "object" && body !== null) {
    const value = body as Record<string, unknown>;
    const nested = value.error as Record<string, unknown> | undefined;
    return {
      success: false,
      code: text(value.code) ?? text(nested?.code) ?? `HTTP_${status}`,
      message: text(value.message) ?? text(nested?.message) ?? "请求失败",
      traceId: text(value.traceId) ?? text(nested?.traceId) ?? fallbackTraceId,
      retryable: Boolean(value.retryable ?? nested?.retryable ?? (status >= 500 || status === 408 || status === 429)),
      details: record(value.details) ?? record(nested?.details) ?? {}
    };
  }
  return {
    success: false,
    code: `HTTP_${status}`,
    message: "请求失败",
    traceId: fallbackTraceId,
    retryable: status >= 500 || status === 408 || status === 429,
    details: {}
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

function text(value: unknown) {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function record(value: unknown) {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : undefined;
}

function defaultTraceId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return `trace_${crypto.randomUUID().replaceAll("-", "")}`;
  }
  return `trace_${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`;
}
