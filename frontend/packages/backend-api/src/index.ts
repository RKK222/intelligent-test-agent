import type {
  AgentInfo,
  ApiFailure,
  ApiResponse,
  CommandInfo,
  FileContent,
  FileStatus,
  FileTreeEntry,
  ModelInfo,
  PageResponse,
  PermissionRequest,
  PromptPart,
  ProviderInfo,
  QuestionRequest,
  Run,
  RunDiff,
  RunDiffAction,
  RuntimeResourceInfo,
  RuntimeToolInfo,
  SessionDiff,
  Session,
  SessionMessage,
  TerminalTicketRequest,
  TerminalTicketResponse,
  TodoItem,
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

export type StartRunPayload = {
  sessionId: string;
  prompt?: string;
  parts?: PromptPart[];
  messageId?: string;
  agent?: string;
  model?: string;
  variant?: string;
  mode?: string;
};

type RequestFn = <T>(path: string, init?: RequestInit) => Promise<T>;

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
    listAllSessions: (page = 1, size = 20, q?: string) => request<PageResponse<Session>>(`/api/sessions${query({ page, size, q })}`),
    listSessions: (workspaceId: string, page = 1, size = 20) =>
      request<PageResponse<Session>>(`/api/workspaces/${workspaceId}/sessions?page=${page}&size=${size}`),
    getSession: (sessionId: string) => request<Session>(`/api/sessions/${encodeURIComponent(sessionId)}`),
    updateSession: (sessionId: string, payload: { title?: string; pinned?: boolean }) =>
      request<Session>(`/api/sessions/${encodeURIComponent(sessionId)}`, { method: "PATCH", body: JSON.stringify(payload) }),
    deleteSession: (sessionId: string) => request<Session>(`/api/sessions/${encodeURIComponent(sessionId)}`, { method: "DELETE" }),
    listSessionMessages: (sessionId: string, page = 1, size = 100) =>
      request<PageResponse<SessionMessage>>(`/api/sessions/${encodeURIComponent(sessionId)}/messages?page=${page}&size=${size}`),
    createSession: (workspaceId: string, title: string) =>
      request<Session>("/api/sessions", { method: "POST", body: JSON.stringify({ workspaceId, title }) }),
    startRun: (sessionIdOrPayload: string | StartRunPayload, prompt?: string) =>
      request<Run>("/api/runs", {
        method: "POST",
        body: JSON.stringify(normalizeStartRunPayload(sessionIdOrPayload, prompt))
      }),
    getRun: (runId: string) => request<Run>(`/api/runs/${runId}`),
    cancelRun: (runId: string) => request<Run>(`/api/runs/${runId}/cancel`, { method: "POST" }),
    getRunDiff: (runId: string) => request<RunDiff>(`/api/runs/${runId}/diff`),
    acceptRunDiff: (runId: string) => request<RunDiffAction>(`/api/runs/${runId}/diff/accept`, { method: "POST" }),
    rejectRunDiff: (runId: string) => request<RunDiffAction>(`/api/runs/${runId}/diff/reject`, { method: "POST" }),
    listAgents: async (workspaceId?: string) => (await runtimeList(`/api/agents${query({ workspaceId })}`, request)).map(toAgentInfo),
    listModels: async (workspaceId?: string) => (await runtimeList(`/api/models${query({ workspaceId })}`, request)).map(toModelInfo),
    listProviders: async (workspaceId?: string) =>
      (await runtimeList(`/api/providers${query({ workspaceId })}`, request)).map(toProviderInfo),
    getConfig: (workspaceId?: string) => request<unknown>(`/api/config${query({ workspaceId })}`),
    updateConfig: (payload: Record<string, unknown>, workspaceId?: string) =>
      request<unknown>(`/api/config${query({ workspaceId })}`, { method: "PATCH", body: JSON.stringify(payload) }),
    disposeGlobal: () => request<unknown>("/api/global/dispose", { method: "POST" }),
    listProviderAuth: (workspaceId?: string) => request<unknown>(`/api/provider/auth${query({ workspaceId })}`),
    authorizeProviderOAuth: (providerId: string, payload?: Record<string, unknown>) =>
      postRuntime(`/api/provider/${encodeURIComponent(providerId)}/oauth/authorize`, payload, request),
    completeProviderOAuth: (providerId: string, payload?: Record<string, unknown>) =>
      postRuntime(`/api/provider/${encodeURIComponent(providerId)}/oauth/callback`, payload, request),
    setProviderAuth: (providerId: string, payload: Record<string, unknown>) =>
      request<unknown>(`/api/auth/${encodeURIComponent(providerId)}`, { method: "PUT", body: JSON.stringify(payload) }),
    removeProviderAuth: (providerId: string) =>
      request<unknown>(`/api/auth/${encodeURIComponent(providerId)}`, { method: "DELETE" }),
    listCommands: async (workspaceId?: string) =>
      (await runtimeList(`/api/commands${query({ workspaceId })}`, request)).map(toCommandInfo),
    listReferences: (workspaceId?: string) => request<unknown>(`/api/references${query({ workspaceId })}`),
    listRuntimeFiles: (workspaceId?: string, path = ".") => request<unknown>(`/api/fs/list${query({ workspaceId, path })}`),
    findRuntimeFiles: (workspaceId?: string, search = "") => request<unknown>(`/api/fs/find${query({ workspaceId, query: search })}`),
    readRuntimeFile: (workspaceId: string | undefined, path: string) =>
      request<unknown>(`/api/fs/read${query({ workspaceId, path })}`),
    getVcsStatus: (workspaceId?: string) => request<unknown>(`/api/vcs/status${query({ workspaceId })}`),
    getVcsDiff: (workspaceId?: string, mode = "git", context?: number) =>
      request<unknown>(`/api/vcs/diff${query({ workspaceId, mode, context })}`),
    getVcsDiffFiles: async (workspaceId?: string, mode = "working", context?: number) => ({
      files: listFromRuntimeEnvelope(await request<unknown>(`/api/vcs/diff${query({ workspaceId, mode, context })}`)).map(toRunDiffFile)
    }),
    getLspStatus: (workspaceId?: string) => request<unknown>(`/api/lsp/status${query({ workspaceId })}`),
    getMcpStatus: (workspaceId?: string) => request<unknown>(`/api/mcp/status${query({ workspaceId })}`),
    getMcpResources: async (workspaceId?: string) =>
      listFromRuntimeEnvelope(await request<unknown>(`/api/mcp/resources${query({ workspaceId })}`)).map(toRuntimeResourceInfo),
    getMcpTools: async (workspaceId?: string, provider?: string, model?: string) =>
      listValuesFromRuntimeEnvelope(await request<unknown>(`/api/mcp/tools${query({ workspaceId, provider, model })}`)).map((item) =>
        typeof item === "string" ? toRuntimeToolInfo({ id: item, name: item }) : toRuntimeToolInfo(item)
      ),
    startMcpAuth: (name: string, payload?: Record<string, unknown>) =>
      postRuntime(`/api/mcp/${encodeURIComponent(name)}/auth`, payload, request),
    completeMcpAuth: (name: string, payload?: Record<string, unknown>) =>
      postRuntime(`/api/mcp/${encodeURIComponent(name)}/auth/callback`, payload, request),
    authenticateMcp: (name: string, payload?: Record<string, unknown>) =>
      postRuntime(`/api/mcp/${encodeURIComponent(name)}/auth/authenticate`, payload, request),
    removeMcpAuth: (name: string) => request<unknown>(`/api/mcp/${encodeURIComponent(name)}/auth`, { method: "DELETE" }),
    listWorktrees: (workspaceId?: string) => request<unknown>(`/api/worktrees${query({ workspaceId })}`),
    createWorktree: (payload?: Record<string, unknown>) => postRuntime("/api/worktrees", payload, request),
    removeWorktree: (payload?: Record<string, unknown>) =>
      request<unknown>("/api/worktrees", { method: "DELETE", body: payload == null ? undefined : JSON.stringify(payload) }),
    resetWorktree: (payload?: Record<string, unknown>) => postRuntime("/api/worktrees/reset", payload, request),
    getSessionChildren: (sessionId: string) => request<unknown>(`/api/sessions/${encodeURIComponent(sessionId)}/children`),
    getSessionTodo: async (sessionId: string) =>
      listFromRuntimeEnvelope(await request<unknown>(`/api/sessions/${encodeURIComponent(sessionId)}/todo`)).map(toTodoItem),
    getSessionDiff: async (sessionId: string, messageId?: string) => ({
      sessionId,
      messageId,
      files: listFromRuntimeEnvelope(
        await request<unknown>(`/api/sessions/${encodeURIComponent(sessionId)}/diff${query({ messageId })}`)
      ).map(toRunDiffFile)
    }) satisfies SessionDiff,
    abortSession: (sessionId: string) => request<unknown>(`/api/sessions/${encodeURIComponent(sessionId)}/abort`, { method: "POST" }),
    forkSession: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(`/api/sessions/${encodeURIComponent(sessionId)}/fork`, payload, request),
    compactSession: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(`/api/sessions/${encodeURIComponent(sessionId)}/compact`, payload, request),
    revertSession: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(`/api/sessions/${encodeURIComponent(sessionId)}/revert`, payload, request),
    unrevertSession: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(`/api/sessions/${encodeURIComponent(sessionId)}/unrevert`, payload, request),
    runSessionCommand: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(`/api/sessions/${encodeURIComponent(sessionId)}/command`, payload, request),
    runSessionShell: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(`/api/sessions/${encodeURIComponent(sessionId)}/shell`, payload, request),
    shareSession: (sessionId: string) =>
      postRuntime(`/api/sessions/${encodeURIComponent(sessionId)}/share`, undefined, request),
    unshareSession: (sessionId: string) =>
      request<unknown>(`/api/sessions/${encodeURIComponent(sessionId)}/share`, { method: "DELETE" }),
    listSessionPermissions: async (sessionId: string) =>
      listFromRuntimeEnvelope(await request<unknown>(`/api/sessions/${encodeURIComponent(sessionId)}/permissions`)).map((item) =>
        toPermissionRequest(item, sessionId)
      ),
    replySessionPermission: (sessionId: string, requestId: string, payload: { decision?: "once" | "always" | "reject"; reply?: string; message?: string }) =>
      request<unknown>(`/api/sessions/${encodeURIComponent(sessionId)}/permissions/${encodeURIComponent(requestId)}/reply`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    listSessionQuestions: async (sessionId: string) =>
      listFromRuntimeEnvelope(await request<unknown>(`/api/sessions/${encodeURIComponent(sessionId)}/questions`)).map((item) =>
        toQuestionRequest(item, sessionId)
      ),
    replySessionQuestion: (sessionId: string, requestId: string, payload: { answers: unknown[] }) =>
      request<unknown>(`/api/sessions/${encodeURIComponent(sessionId)}/questions/${encodeURIComponent(requestId)}/reply`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    rejectSessionQuestion: (sessionId: string, requestId: string) =>
      request<unknown>(`/api/sessions/${encodeURIComponent(sessionId)}/questions/${encodeURIComponent(requestId)}/reject`, {
        method: "POST"
      }),
    createTerminalTicket: (sessionId: string, payload: TerminalTicketRequest = {}) =>
      request<TerminalTicketResponse>(`/api/sessions/${encodeURIComponent(sessionId)}/terminal/tickets`, {
        method: "POST",
        body: JSON.stringify(compactObject(payload))
      })
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

async function runtimeList(path: string, request: RequestFn) {
  return listFromRuntimeEnvelope(await request<unknown>(path));
}

function postRuntime(path: string, payload: Record<string, unknown> | undefined, request: RequestFn) {
  return request<unknown>(path, {
    method: "POST",
    body: payload == null ? undefined : JSON.stringify(payload)
  });
}

function listFromRuntimeEnvelope(value: unknown): Record<string, unknown>[] {
  return listValuesFromRuntimeEnvelope(value).filter(
    (item): item is Record<string, unknown> => typeof item === "object" && item !== null
  );
}

function listValuesFromRuntimeEnvelope(value: unknown): Array<Record<string, unknown> | string> {
  const data = record(value)?.data;
  const raw = Array.isArray(data) ? data : Array.isArray(value) ? value : [];
  return raw.filter((item): item is Record<string, unknown> | string => typeof item === "string" || (typeof item === "object" && item !== null));
}

function toAgentInfo(value: Record<string, unknown>): AgentInfo {
  const agentId = text(value.agentId) ?? text(value.agentID) ?? text(value.id) ?? text(value.name) ?? "unknown";
  return compactObject({
    agentId,
    name: text(value.name) ?? agentId,
    mode: text(value.mode),
    description: text(value.description),
    color: text(value.color),
    hidden: typeof value.hidden === "boolean" ? value.hidden : undefined
  });
}

function toModelInfo(value: Record<string, unknown>): ModelInfo {
  const id = text(value.id) ?? text(value.modelId) ?? text(value.modelID) ?? "unknown";
  const variants = Array.isArray(value.variants) ? value.variants.filter((item): item is string => typeof item === "string") : undefined;
  return compactObject({
    id,
    providerId: text(value.providerId) ?? text(value.providerID) ?? text(record(value.provider)?.id),
    name: text(value.name) ?? id,
    contextLimit: number(value.contextLimit) ?? number(value.context),
    outputLimit: number(value.outputLimit),
    free: typeof value.free === "boolean" ? value.free : undefined,
    variants
  });
}

function toProviderInfo(value: Record<string, unknown>): ProviderInfo {
  const providerId = text(value.providerId) ?? text(value.providerID) ?? text(value.id) ?? text(value.name) ?? "unknown";
  const rawModels = record(value.models);
  return compactObject({
    providerId,
    name: text(value.name) ?? providerId,
    status: text(value.status),
    models: rawModels
      ? Object.entries(rawModels).map(([id, model]) => toModelInfo({ id, ...(record(model) ?? {}) }))
      : undefined,
    metadata: value
  });
}

function toCommandInfo(value: Record<string, unknown>): CommandInfo {
  const commandId = text(value.commandId) ?? text(value.commandID) ?? text(value.id) ?? text(value.name) ?? "unknown";
  return compactObject({
    commandId,
    name: text(value.name) ?? commandId,
    aliases: Array.isArray(value.aliases) ? value.aliases.filter((item): item is string => typeof item === "string") : undefined,
    description: text(value.description),
    arguments: text(value.arguments)
  });
}

function toRuntimeResourceInfo(value: Record<string, unknown>): RuntimeResourceInfo {
  const uri = text(value.uri) ?? text(value.url);
  const id = text(value.id) ?? uri ?? text(value.name) ?? "unknown";
  return compactObject({
    id,
    name: text(value.name) ?? text(value.title) ?? uri ?? id,
    uri,
    type: text(value.type) ?? text(value.mime),
    metadata: value
  });
}

function toRuntimeToolInfo(value: Record<string, unknown>): RuntimeToolInfo {
  const toolId = text(value.toolId) ?? text(value.toolID) ?? text(value.id) ?? text(value.name) ?? "unknown";
  return compactObject({
    toolId,
    name: text(value.name) ?? toolId,
    description: text(value.description),
    parameters: value.parameters,
    source: text(value.source)
  });
}

function toTodoItem(value: Record<string, unknown>): TodoItem {
  const id = text(value.id) ?? text(value.todoId) ?? text(value.todoID) ?? "unknown";
  return compactObject({
    id,
    text: text(value.text) ?? text(value.content) ?? text(value.title) ?? id,
    status: text(value.status) ?? "pending",
    priority: text(value.priority)
  });
}

function toRunDiffFile(value: Record<string, unknown>) {
  return {
    path: text(value.path) ?? text(value.file) ?? "",
    patch: text(value.patch) ?? text(value.diff) ?? "",
    additions: number(value.additions) ?? 0,
    deletions: number(value.deletions) ?? 0,
    status: text(value.status) ?? "modified"
  };
}

function toPermissionRequest(value: Record<string, unknown>, fallbackSessionId: string): PermissionRequest {
  const requestId = text(value.requestId) ?? text(value.requestID) ?? text(value.id) ?? "unknown";
  return compactObject({
    requestId,
    sessionId: text(value.sessionId) ?? text(value.sessionID) ?? fallbackSessionId,
    type: text(value.type) ?? text(value.permission) ?? text(value.action) ?? "permission",
    title: text(value.title),
    description: text(value.description) ?? text(value.pattern),
    pattern: text(value.pattern),
    createdAt: text(value.createdAt) ?? text(record(value.time)?.created) ?? new Date(0).toISOString()
  });
}

function toQuestionRequest(value: Record<string, unknown>, fallbackSessionId: string): QuestionRequest {
  const requestId = text(value.requestId) ?? text(value.requestID) ?? text(value.id) ?? "unknown";
  const questions = Array.isArray(value.questions) ? value.questions : Array.isArray(value.items) ? value.items : [value];
  return {
    requestId,
    sessionId: text(value.sessionId) ?? text(value.sessionID) ?? fallbackSessionId,
    questions: questions
      .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
      .map((item, index) => ({
        questionId: text(item.questionId) ?? text(item.questionID) ?? text(item.id) ?? `${requestId}:${index}`,
        text: text(item.text) ?? text(item.prompt) ?? text(item.question) ?? "",
        kind: text(item.kind) ?? text(item.type) ?? "text",
        options: Array.isArray(item.options)
          ? item.options
              .filter((option): option is Record<string, unknown> => typeof option === "object" && option !== null)
              .map((option) => ({
                id: text(option.id) ?? text(option.value) ?? text(option.label) ?? "option",
                label: text(option.label) ?? text(option.value) ?? text(option.id) ?? "option",
                description: text(option.description)
              }))
          : undefined,
        required: typeof item.required === "boolean" ? item.required : undefined
      })),
    createdAt: text(value.createdAt) ?? text(record(value.time)?.created) ?? new Date(0).toISOString()
  };
}

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

function normalizeStartRunPayload(sessionIdOrPayload: string | StartRunPayload, prompt?: string): StartRunPayload {
  if (typeof sessionIdOrPayload === "string") {
    return { sessionId: sessionIdOrPayload, prompt: prompt ?? "" };
  }
  return sessionIdOrPayload;
}

function text(value: unknown) {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function record(value: unknown) {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : undefined;
}

function number(value: unknown) {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function compactObject<T extends Record<string, unknown>>(value: T): T {
  return Object.fromEntries(Object.entries(value).filter(([, item]) => item !== undefined)) as T;
}

function defaultTraceId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return `trace_${crypto.randomUUID().replaceAll("-", "")}`;
  }
  return `trace_${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`;
}
