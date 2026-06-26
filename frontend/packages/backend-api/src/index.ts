import type {
  AgentInfo,
  AddSshKeyPayload,
  ApplicationWorkspaceTemplate,
  ApplicationWorkspaceVersion,
  ApplicationDefinition,
  ApplicationMember,
  ApplicationWorkspaceConfig,
  ApiFailure,
  ApiResponse,
  CodeRepositoryConfig,
  CommandInfo,
  CreateApplicationWorkspacePayload,
  CreatePersonalWorkspacePayload,
  CreateRepositoryPayload,
  CreateWorkspaceVersionPayload,
  CurrentUser,
  FileContent,
  FileStatus,
  FileTreeEntry,
  LoginRequest,
  LoginResponse,
  ManagedApplication,
  ManagedWorkspaceRuntime,
  ModelInfo,
  OpencodeRuntimeManagementOverview,
  OpencodeRuntimeManagementOverviewParams,
  PageResponse,
  PlatformUserSummary,
  PersonalWorkspace,
  PermissionRequest,
  PromptPart,
  ProviderInfo,
  QuestionRequest,
  Run,
  RunDiff,
  RunDiffAction,
  RuntimeResourceInfo,
  RuntimeToolInfo,
  ScheduledTaskListParams,
  ScheduledTaskManagementRun,
  ScheduledTaskManagementTask,
  ScheduledTaskRunListParams,
  ScheduledTaskUpdatePayload,
  SessionDiff,
  Session,
  SessionMessage,
  SshKeyMetadata,
  SyncWorkspacePayload,
  TerminalTicketRequest,
  TerminalTicketResponse,
  TodoItem,
  UpdateRepositoryPayload,
  UserOpencodeProcess,
  Workspace,
  WorkspaceBackendServer,
  WorkspaceDiff,
  WorkspaceSyncResult,
  WorkspaceBranchPreference,
  WorkspaceDirectoryList,
  WorkspaceFileRoute,
  WorkspaceFileSocketTicketRequest,
  WorkspaceFileSocketTicketResponse
} from "@test-agent/shared-types";

type WorkspaceWebSocketLike = {
  onopen: ((event: any) => void) | null;
  onmessage: ((event: any) => void) | null;
  onerror: ((event: any) => void) | null;
  onclose: ((event: any) => void) | null;
  send: (payload: string) => void;
  close: () => void;
  readyState?: number;
};

export type WorkspaceWebSocketFactory = (url: string) => WorkspaceWebSocketLike;

export type BackendApiClientOptions = {
  baseUrl?: string;
  agentId?: string;
  apiToken?: string;
  fetcher?: typeof fetch;
  webSocketFactory?: WorkspaceWebSocketFactory;
  traceIdFactory?: () => string;
  requestTimeoutMs?: number;
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

// 统一读取环境变量：Vite 运行时（import.meta.env）优先，Node 运行时（process.env）兜底
function readEnv(key: string): string | undefined {
  const viteEnv = (import.meta as unknown as { env?: Record<string, string | undefined> }).env;
  if (viteEnv?.[key]) {
    return viteEnv[key];
  }
  const proc = (globalThis as unknown as { process?: { env?: Record<string, string | undefined> } }).process;
  return proc?.env?.[key];
}

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
  const baseUrl = (options.baseUrl ?? readEnv("VITE_TEST_AGENT_API_BASE_URL") ?? "http://127.0.0.1:8080").replace(
    /\/$/,
    ""
  );
  const agentId = normalizeAgentId(options.agentId ?? readEnv("VITE_TEST_AGENT_AGENT_ID") ?? "opencode");
  const agentBase = `/api/internal/agent/${encodeURIComponent(agentId)}`;
  const configurationBase = "/api/internal/platform/configuration-management";
  const workspaceManagementBase = "/api/internal/platform/workspace-management";
  const opencodeRuntimeManagementBase = "/api/internal/platform/opencode-runtime/management";
  const schedulerManagementBase = "/api/internal/platform/scheduler-management";
  const fetcher = options.fetcher ?? fetch;
  const webSocketFactory: WorkspaceWebSocketFactory =
    options.webSocketFactory ??
    ((url: string) => {
      if (typeof WebSocket === "undefined") {
        throw new Error("WebSocket is not available in this runtime");
      }
      return new WebSocket(url);
    });
  const traceIdFactory = options.traceIdFactory ?? defaultTraceId;
  const requestTimeoutMs = options.requestTimeoutMs ?? 30000;

  async function requestFrom<T>(requestBaseUrl: string, path: string, init: RequestInit = {}): Promise<T> {
    const traceId = traceIdFactory();
    const headers = new Headers(init.headers);
    headers.set("Accept", "application/json");
    headers.set("X-Trace-Id", traceId);
    if (init.body != null && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    // 自动附加用户 Token：优先使用 options 中的 apiToken，其次从 localStorage 读取
    const userToken = options.apiToken ?? (typeof localStorage !== "undefined" ? localStorage.getItem("test-agent.auth.token") : null);
    if (userToken && !headers.has("Authorization")) {
      headers.set("Authorization", `Bearer ${userToken}`);
    }
    // 所有后端请求统一设置超时，避免目录选择等界面在连接悬挂时一直停留在加载态。
    const controller = new AbortController();
    let timedOut = false;
    const timeoutId =
      requestTimeoutMs > 0
        ? setTimeout(() => {
            timedOut = true;
            controller.abort();
          }, requestTimeoutMs)
        : undefined;
    const abortFromCaller = () => controller.abort();
    init.signal?.addEventListener("abort", abortFromCaller, { once: true });
    if (init.signal?.aborted) {
      controller.abort();
    }
    try {
      const response = await fetcher(`${requestBaseUrl}${path}`, { ...init, headers, signal: controller.signal });
      const body = await readJson(response);
      if (!response.ok || !isSuccessResponse<T>(body)) {
        const error = new BackendApiError(response.status, normalizeFailure(body, traceId, response.status));
        // 401 未认证：触发全局跳转到登录页
        if (response.status === 401 && typeof window !== "undefined") {
          const handler = (window as unknown as Record<string, unknown>).__handleUnauthorized;
          if (typeof handler === "function") {
            handler();
          }
        }
        throw error;
      }
      return body.data;
    } catch (error) {
      if (timedOut) {
        throw new BackendApiError(408, {
          success: false,
          code: "REQUEST_TIMEOUT",
          message: "请求超时",
          traceId,
          retryable: true,
          details: { path, baseUrl: requestBaseUrl }
        });
      }
      throw error;
    } finally {
      if (timeoutId !== undefined) {
        clearTimeout(timeoutId);
      }
      init.signal?.removeEventListener("abort", abortFromCaller);
    }
  }

  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    return requestFrom<T>(baseUrl, path, init);
  }

  const agentPath = (path: string) => `${agentBase}${path}`;
  const workspaceFileSockets = new Map<string, WorkspaceFileSocketClient>();

  async function workspaceFileRpc<T>(workspaceId: string, op: string, params: Record<string, unknown>): Promise<T> {
    const client = await ensureWorkspaceFileClient(workspaceId);
    return client.request<T>(op, { workspaceId, ...params });
  }

  async function ensureWorkspaceFileClient(workspaceId: string): Promise<WorkspaceFileSocketClient> {
    const existing = workspaceFileSockets.get(workspaceId);
    if (existing?.open) {
      return existing;
    }
    existing?.close();
    const route = await request<WorkspaceFileRoute>(`/api/workspaces/${encodeURIComponent(workspaceId)}/file-ws-route`, {
      method: "POST"
    });
    const ticket = await requestFrom<WorkspaceFileSocketTicketResponse>(
      route.baseUrl.replace(/\/$/, ""),
      "/api/internal/platform/workspace-management/file-ws/tickets",
      {
        method: "POST",
        body: JSON.stringify({
          workspaceId,
          linuxServerId: route.linuxServerId,
          mode: "workspace"
        } satisfies WorkspaceFileSocketTicketRequest)
      }
    );
    const client = new WorkspaceFileSocketClient(
      toWebSocketUrl(route.baseUrl, ticket.webSocketUrl),
      webSocketFactory,
      () => workspaceFileSockets.delete(workspaceId)
    );
    workspaceFileSockets.set(workspaceId, client);
    await client.ready();
    return client;
  }

  async function createDirectoryPickerClient(server: WorkspaceBackendServer): Promise<WorkspaceFileSocketClient> {
    const ticket = await requestFrom<WorkspaceFileSocketTicketResponse>(
      server.baseUrl.replace(/\/$/, ""),
      "/api/internal/platform/workspace-management/file-ws/tickets",
      {
        method: "POST",
        body: JSON.stringify({
          linuxServerId: server.linuxServerId,
          mode: "directory-picker"
        } satisfies WorkspaceFileSocketTicketRequest)
      }
    );
    const client = new WorkspaceFileSocketClient(
      toWebSocketUrl(server.baseUrl, ticket.webSocketUrl),
      webSocketFactory,
      () => {}
    );
    await client.ready();
    return client;
  }

  return {
    listWorkspaces: (page = 1, size = 20) =>
      request<PageResponse<Workspace>>(`/api/workspaces?page=${page}&size=${size}`),
    getWorkspace: (workspaceId: string) => request<Workspace>(`/api/workspaces/${encodeURIComponent(workspaceId)}`),
    createWorkspace: (payload: { name: string; rootPath: string; linuxServerId?: string }) =>
      request<Workspace>("/api/workspaces", { method: "POST", body: JSON.stringify(payload) }),
    listManagedApplications: () => request<ManagedApplication[]>(`${workspaceManagementBase}/applications`),
    listWorkspaceTemplates: (appId: string) =>
      request<ApplicationWorkspaceTemplate[]>(`${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/workspace-templates`),
    listWorkspaceVersions: (appId: string, templateId: string) =>
      request<ApplicationWorkspaceVersion[]>(
        `${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/workspace-templates/${encodeURIComponent(templateId)}/versions`
      ),
    createWorkspaceVersion: (appId: string, templateId: string, payload: CreateWorkspaceVersionPayload) =>
      request<ApplicationWorkspaceVersion>(
        `${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/workspace-templates/${encodeURIComponent(templateId)}/versions`,
        { method: "POST", body: JSON.stringify(payload) }
      ),
    listPersonalWorkspaces: (versionId: string) =>
      request<PersonalWorkspace[]>(`${workspaceManagementBase}/workspace-versions/${encodeURIComponent(versionId)}/personal-workspaces`),
    createPersonalWorkspace: (versionId: string, payload: CreatePersonalWorkspacePayload) =>
      request<PersonalWorkspace>(`${workspaceManagementBase}/workspace-versions/${encodeURIComponent(versionId)}/personal-workspaces`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    getRecentManagedWorkspace: () => request<ManagedWorkspaceRuntime | null>(`${workspaceManagementBase}/recent-workspace`),
    getRecentManagedWorkspaceForApplication: (appId: string) =>
      request<ManagedWorkspaceRuntime | null>(`${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/recent-workspace`),
    markRecentManagedWorkspace: (workspaceId: string) =>
      request<ManagedWorkspaceRuntime>(`${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/recent`, { method: "POST" }),
    markRecentBranch: (appId: string, workspaceId: string, branch: string) =>
      request<WorkspaceBranchPreference>(
        `${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/workspaces/${encodeURIComponent(workspaceId)}/branch-preference`,
        { method: "POST", body: JSON.stringify({ branch }) }
      ),
    getRecentBranch: (appId: string, workspaceId: string) =>
      request<WorkspaceBranchPreference | null>(
        `${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/workspaces/${encodeURIComponent(workspaceId)}/branch-preference`
      ),
    diffPersonalWorkspace: (personalWorkspaceId: string) =>
      request<WorkspaceDiff>(`${workspaceManagementBase}/personal-workspaces/${encodeURIComponent(personalWorkspaceId)}/diff`),
    syncPersonalToApplication: (personalWorkspaceId: string, payload: SyncWorkspacePayload) =>
      request<WorkspaceSyncResult>(`${workspaceManagementBase}/personal-workspaces/${encodeURIComponent(personalWorkspaceId)}/sync-to-application`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    syncApplicationToPersonal: (personalWorkspaceId: string, payload: SyncWorkspacePayload) =>
      request<WorkspaceSyncResult>(`${workspaceManagementBase}/personal-workspaces/${encodeURIComponent(personalWorkspaceId)}/sync-from-application`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    listWorkspaceDirectories: (path?: string) =>
      request<WorkspaceDirectoryList>(`/api/workspace-directories${query({ path })}`),
    listFiles: async (workspaceId: string, path = "") => {
      const entries = await workspaceFileRpc<BackendFileTreeEntry[]>(workspaceId, "workspace.list", { path });
      return entries.map((entry) => ({
        path: entry.path,
        name: entry.name,
        type: entry.directory ? "directory" : "file",
        size: entry.size,
        modifiedAt: entry.lastModifiedAt
      })) satisfies FileTreeEntry[];
    },
    readFile: async (workspaceId: string, path: string) => {
      const file = await workspaceFileRpc<BackendFileContent>(workspaceId, "workspace.read", { path });
      return { ...file, encoding: "utf-8", readonly: false } satisfies FileContent;
    },
    writeFile: (workspaceId: string, path: string, content: string) =>
      workspaceFileRpc<void>(workspaceId, "workspace.write", { path, content }),
    fileStatus: async (workspaceId: string, path: string) => {
      const status = await workspaceFileRpc<BackendFileStatus>(workspaceId, "workspace.status", { path });
      return {
        ...status,
        status: status.exists ? "unchanged" : "deleted"
      } satisfies FileStatus;
    },
    deleteWorkspaceFile: (workspaceId: string, path: string) =>
      workspaceFileRpc<void>(workspaceId, "workspace.delete", { path }),
    listWorkspaceBackendServers: () =>
      request<WorkspaceBackendServer[]>(`${workspaceManagementBase}/backend-servers`),
    createWorkspaceFileSocketTicket: (targetBaseUrl: string, payload: WorkspaceFileSocketTicketRequest) =>
      requestFrom<WorkspaceFileSocketTicketResponse>(
        targetBaseUrl.replace(/\/$/, ""),
        `${workspaceManagementBase}/file-ws/tickets`,
        { method: "POST", body: JSON.stringify(payload) }
      ),
    listServerWorkspaceDirectories: async (server: WorkspaceBackendServer, path?: string) => {
      const client = await createDirectoryPickerClient(server);
      try {
        return await client.request<WorkspaceDirectoryList>("directory.list", { path });
      } finally {
        client.close();
      }
    },
    createServerWorkspace: async (server: WorkspaceBackendServer, payload: { name: string; rootPath: string }) => {
      const client = await createDirectoryPickerClient(server);
      try {
        return await client.request<Workspace>("workspace.create", payload);
      } finally {
        client.close();
      }
    },
    // 公共目录（后端 application.yml 中 test-agent.public-directory.path 配置的固定根目录）：
    // 列表/读取对所有登录用户开放，写入仅 SUPER_ADMIN 可调用。
    listPublicFiles: async (path = "") => {
      const entries = await request<BackendFileTreeEntry[]>(`/api/public/files${query({ path })}`);
      return entries.map((entry) => ({
        path: entry.path,
        name: entry.name,
        type: entry.directory ? "directory" : "file",
        size: entry.size,
        modifiedAt: entry.lastModifiedAt
      })) satisfies FileTreeEntry[];
    },
    readPublicFile: async (path: string) => {
      const file = await request<BackendFileContent>(`/api/public/files/content${query({ path })}`);
      return { ...file, encoding: "utf-8", readonly: false } satisfies FileContent;
    },
    writePublicFile: (path: string, content: string) =>
      request<void>("/api/public/files/content", {
        method: "PUT",
        body: JSON.stringify({ path, content })
      }),
    listAllSessions: (page = 1, size = 20, q?: string) => request<PageResponse<Session>>(`/api/sessions${query({ page, size, q })}`),
    listSessions: (workspaceId: string, page = 1, size = 20) =>
      request<PageResponse<Session>>(`/api/workspaces/${workspaceId}/sessions?page=${page}&size=${size}`),
    getSession: (sessionId: string) => request<Session>(`/api/sessions/${encodeURIComponent(sessionId)}`),
    updateSession: (sessionId: string, payload: { title?: string; pinned?: boolean }) =>
      request<Session>(`/api/sessions/${encodeURIComponent(sessionId)}`, { method: "PATCH", body: JSON.stringify(payload) }),
    deleteSession: (sessionId: string) => request<Session>(`/api/sessions/${encodeURIComponent(sessionId)}`, { method: "DELETE" }),
    listSessionMessages: (sessionId: string, page = 1, size = 100) =>
      request<PageResponse<SessionMessage>>(`/api/sessions/${encodeURIComponent(sessionId)}/messages?page=${page}&size=${size}`),
    getActiveRun: (sessionId: string) => request<Run | null>(`/api/sessions/${encodeURIComponent(sessionId)}/active-run`),
    createSession: (workspaceId: string, title: string) =>
      request<Session>("/api/sessions", { method: "POST", body: JSON.stringify({ workspaceId, title }) }),
    startRun: (sessionIdOrPayload: string | StartRunPayload, prompt?: string) =>
      request<Run>(agentPath("/runs"), {
        method: "POST",
        body: JSON.stringify(normalizeStartRunPayload(sessionIdOrPayload, prompt))
      }),
    getMyOpencodeProcess: () => request<UserOpencodeProcess>(agentPath("/processes/me")),
    initializeMyOpencodeProcess: () =>
      request<UserOpencodeProcess>(agentPath("/processes/me/initialize"), { method: "POST" }),
    getOpencodeRuntimeManagementOverview: (params: OpencodeRuntimeManagementOverviewParams = {}) =>
      request<OpencodeRuntimeManagementOverview>(`${opencodeRuntimeManagementBase}/overview${query({ ...params })}`),
    listScheduledTasks: (params: ScheduledTaskListParams = {}) =>
      request<PageResponse<ScheduledTaskManagementTask>>(
        `${schedulerManagementBase}/tasks${query({ page: params.page, size: params.size })}`
      ),
    getScheduledTask: (taskKey: string) =>
      request<ScheduledTaskManagementTask>(`${schedulerManagementBase}/tasks/${encodeURIComponent(taskKey)}`),
    updateScheduledTask: (taskKey: string, payload: ScheduledTaskUpdatePayload) =>
      request<ScheduledTaskManagementTask>(`${schedulerManagementBase}/tasks/${encodeURIComponent(taskKey)}`, {
        method: "PATCH",
        body: JSON.stringify(compactObject(payload))
      }),
    triggerScheduledTask: (taskKey: string) =>
      request<ScheduledTaskManagementRun>(`${schedulerManagementBase}/tasks/${encodeURIComponent(taskKey)}/trigger`, { method: "POST" }),
    listScheduledTaskRuns: (params: ScheduledTaskRunListParams = {}) =>
      request<PageResponse<ScheduledTaskManagementRun>>(
        `${schedulerManagementBase}/runs${query({
          taskKey: params.taskKey,
          status: params.status,
          triggerType: params.triggerType,
          requestedByUserId: params.requestedByUserId,
          page: params.page,
          size: params.size
        })}`
      ),
    getScheduledTaskRun: (taskRunId: string) =>
      request<ScheduledTaskManagementRun>(`${schedulerManagementBase}/runs/${encodeURIComponent(taskRunId)}`),
    stopScheduledTaskRun: (taskRunId: string) =>
      request<ScheduledTaskManagementRun>(`${schedulerManagementBase}/runs/${encodeURIComponent(taskRunId)}/stop`, { method: "POST" }),
    getRun: (runId: string) => request<Run>(agentPath(`/runs/${encodeURIComponent(runId)}`)),
    cancelRun: (runId: string) => request<Run>(agentPath(`/runs/${encodeURIComponent(runId)}/cancel`), { method: "POST" }),
    getRunDiff: (runId: string) => request<RunDiff>(agentPath(`/runs/${encodeURIComponent(runId)}/diff`)),
    acceptRunDiff: (runId: string) => request<RunDiffAction>(agentPath(`/runs/${encodeURIComponent(runId)}/diff/accept`), { method: "POST" }),
    rejectRunDiff: (runId: string) => request<RunDiffAction>(agentPath(`/runs/${encodeURIComponent(runId)}/diff/reject`), { method: "POST" }),
    listAgents: async (workspaceId?: string) => (await runtimeList(agentPath(`/api/agent${query({ workspaceId })}`), request)).map(toAgentInfo),
    listModels: async (workspaceId?: string) => (await runtimeList(agentPath(`/api/model${query({ workspaceId })}`), request)).map(toModelInfo),
    listProviders: async (workspaceId?: string) =>
      (await runtimeList(agentPath(`/api/provider${query({ workspaceId })}`), request)).map(toProviderInfo),
    getConfig: (workspaceId?: string) => request<unknown>(agentPath(`/global/config${query({ workspaceId })}`)),
    updateConfig: (payload: Record<string, unknown>, workspaceId?: string) =>
      request<unknown>(agentPath(`/global/config${query({ workspaceId })}`), { method: "PATCH", body: JSON.stringify(payload) }),
    disposeGlobal: () => request<unknown>(agentPath("/global/dispose"), { method: "POST" }),
    listProviderAuth: (workspaceId?: string) => request<unknown>(agentPath(`/provider/auth${query({ workspaceId })}`)),
    authorizeProviderOAuth: (providerId: string, payload?: Record<string, unknown>) =>
      postRuntime(agentPath(`/provider/${encodeURIComponent(providerId)}/oauth/authorize`), payload, request),
    completeProviderOAuth: (providerId: string, payload?: Record<string, unknown>) =>
      postRuntime(agentPath(`/provider/${encodeURIComponent(providerId)}/oauth/callback`), payload, request),
    setProviderAuth: (providerId: string, payload: Record<string, unknown>) =>
      request<unknown>(agentPath(`/auth/${encodeURIComponent(providerId)}`), { method: "PUT", body: JSON.stringify(payload) }),
    removeProviderAuth: (providerId: string) =>
      request<unknown>(agentPath(`/auth/${encodeURIComponent(providerId)}`), { method: "DELETE" }),
    listCommands: async (workspaceId?: string) =>
      (await runtimeList(agentPath(`/api/command${query({ workspaceId })}`), request)).map(toCommandInfo),
    listReferences: (workspaceId?: string) => request<unknown>(agentPath(`/api/reference${query({ workspaceId })}`)),
    listRuntimeFiles: (workspaceId?: string, path = ".") => request<unknown>(agentPath(`/file${query({ workspaceId, path })}`)),
    findRuntimeFiles: (workspaceId?: string, search = "") => request<unknown>(agentPath(`/find/file${query({ workspaceId, query: search })}`)),
    readRuntimeFile: (workspaceId: string | undefined, path: string) =>
      request<unknown>(agentPath(`/file/content${query({ workspaceId, path })}`)),
    getVcsStatus: (workspaceId?: string) => request<unknown>(agentPath(`/vcs/status${query({ workspaceId })}`)),
    getVcsDiff: (workspaceId?: string, mode = "git", context?: number) =>
      request<unknown>(agentPath(`/vcs/diff${query({ workspaceId, mode, context })}`)),
    getVcsDiffFiles: async (workspaceId?: string, mode = "working", context?: number) => ({
      files: listFromRuntimeEnvelope(await request<unknown>(agentPath(`/vcs/diff${query({ workspaceId, mode, context })}`))).map(toRunDiffFile)
    }),
    getLspStatus: (workspaceId?: string) => request<unknown>(agentPath(`/lsp${query({ workspaceId })}`)),
    getMcpStatus: (workspaceId?: string) => request<unknown>(agentPath(`/mcp${query({ workspaceId })}`)),
    getMcpResources: async (workspaceId?: string) =>
      listFromRuntimeEnvelope(await request<unknown>(agentPath(`/experimental/resource${query({ workspaceId })}`))).map(toRuntimeResourceInfo),
    getMcpTools: async (workspaceId?: string, provider?: string, model?: string) =>
      listValuesFromRuntimeEnvelope(await request<unknown>(agentPath(`${provider && model ? "/experimental/tool" : "/experimental/tool/ids"}${query({ workspaceId, provider, model })}`))).map((item) =>
        typeof item === "string" ? toRuntimeToolInfo({ id: item, name: item }) : toRuntimeToolInfo(item)
      ),
    startMcpAuth: (name: string, payload?: Record<string, unknown>) =>
      postRuntime(agentPath(`/mcp/${encodeURIComponent(name)}/auth`), payload, request),
    completeMcpAuth: (name: string, payload?: Record<string, unknown>) =>
      postRuntime(agentPath(`/mcp/${encodeURIComponent(name)}/auth/callback`), payload, request),
    authenticateMcp: (name: string, payload?: Record<string, unknown>) =>
      postRuntime(agentPath(`/mcp/${encodeURIComponent(name)}/auth/authenticate`), payload, request),
    removeMcpAuth: (name: string) => request<unknown>(agentPath(`/mcp/${encodeURIComponent(name)}/auth`), { method: "DELETE" }),
    listWorktrees: (workspaceId?: string) => request<unknown>(agentPath(`/experimental/worktree${query({ workspaceId })}`)),
    createWorktree: (payload?: Record<string, unknown>) => postRuntime(agentPath("/experimental/worktree"), payload, request),
    removeWorktree: (payload?: Record<string, unknown>) =>
      request<unknown>(agentPath("/experimental/worktree"), { method: "DELETE", body: payload == null ? undefined : JSON.stringify(payload) }),
    resetWorktree: (payload?: Record<string, unknown>) => postRuntime(agentPath("/experimental/worktree/reset"), payload, request),
    getSessionChildren: (sessionId: string) => request<unknown>(agentPath(`/session/${encodeURIComponent(sessionId)}/children`)),
    getSessionTodo: async (sessionId: string) =>
      listFromRuntimeEnvelope(await request<unknown>(agentPath(`/session/${encodeURIComponent(sessionId)}/todo`))).map(toTodoItem),
    getSessionDiff: async (sessionId: string, messageId?: string) => ({
      sessionId,
      messageId,
      files: listFromRuntimeEnvelope(
        await request<unknown>(agentPath(`/session/${encodeURIComponent(sessionId)}/diff${query({ messageId })}`))
      ).map(toRunDiffFile)
    }) satisfies SessionDiff,
    abortSession: (sessionId: string) => request<unknown>(agentPath(`/session/${encodeURIComponent(sessionId)}/abort`), { method: "POST" }),
    forkSession: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(agentPath(`/session/${encodeURIComponent(sessionId)}/fork`), payload, request),
    compactSession: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(agentPath(`/session/${encodeURIComponent(sessionId)}/summarize`), payload, request),
    revertSession: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(agentPath(`/session/${encodeURIComponent(sessionId)}/revert`), payload, request),
    unrevertSession: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(agentPath(`/session/${encodeURIComponent(sessionId)}/unrevert`), payload, request),
    runSessionCommand: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(agentPath(`/session/${encodeURIComponent(sessionId)}/command`), payload, request),
    runSessionShell: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(agentPath(`/session/${encodeURIComponent(sessionId)}/shell`), payload, request),
    shareSession: (sessionId: string) =>
      postRuntime(agentPath(`/session/${encodeURIComponent(sessionId)}/share`), undefined, request),
    unshareSession: (sessionId: string) =>
      request<unknown>(agentPath(`/session/${encodeURIComponent(sessionId)}/share`), { method: "DELETE" }),
    listSessionPermissions: async (sessionId: string) =>
      listFromRuntimeEnvelope(await request<unknown>(agentPath(`/permission${query({ sessionId })}`))).map((item) =>
        toPermissionRequest(item, sessionId)
      ),
    replySessionPermission: (sessionId: string, requestId: string, payload: { decision?: "once" | "always" | "reject"; reply?: string; message?: string }) =>
      request<unknown>(agentPath(`/permission/${encodeURIComponent(requestId)}/reply${query({ sessionId })}`), {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    listSessionQuestions: async (sessionId: string) =>
      listFromRuntimeEnvelope(await request<unknown>(agentPath(`/question${query({ sessionId })}`))).map((item) =>
        toQuestionRequest(item, sessionId)
      ),
    replySessionQuestion: (sessionId: string, requestId: string, payload: { answers: unknown[] }) =>
      request<unknown>(agentPath(`/question/${encodeURIComponent(requestId)}/reply${query({ sessionId })}`), {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    rejectSessionQuestion: (sessionId: string, requestId: string) =>
      request<unknown>(agentPath(`/question/${encodeURIComponent(requestId)}/reject${query({ sessionId })}`), {
        method: "POST"
      }),
    createTerminalTicket: (sessionId: string, payload: TerminalTicketRequest = {}) =>
      request<TerminalTicketResponse>(`/api/sessions/${encodeURIComponent(sessionId)}/terminal/tickets`, {
        method: "POST",
        body: JSON.stringify(compactObject(payload))
      }),

    // ---- 认证相关 API ----

    /**
     * 用户登录。
     */
    login: (payload: LoginRequest) =>
      request<LoginResponse>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify(payload)
      }),

    /**
     * 用户登出。
     */
    logout: () =>
      request<void>("/api/auth/logout", { method: "POST" }),

    /**
     * 获取当前登录用户信息。
     */
    getCurrentUser: () =>
      request<CurrentUser>("/api/auth/me"),

    /**
     * 刷新当前 Token。
     */
    refreshToken: () =>
      request<LoginResponse>("/api/auth/refresh", { method: "POST" }),

    // ---- 应用配置管理 API ----

    listApplications: (enabled = true) =>
      request<ApplicationDefinition[]>(`${configurationBase}/applications${query({ enabled: String(enabled) })}`),
    listApplicationMembers: (appId: string) =>
      request<ApplicationMember[]>(`${configurationBase}/applications/${encodeURIComponent(appId)}/members`),
    addApplicationMember: (appId: string, userId: string) =>
      request<ApplicationMember>(`${configurationBase}/applications/${encodeURIComponent(appId)}/members`, {
        method: "POST",
        body: JSON.stringify({ userId })
      }),
    removeApplicationMember: (appId: string, userId: string) =>
      request<void>(`${configurationBase}/applications/${encodeURIComponent(appId)}/members/${encodeURIComponent(userId)}`, {
        method: "DELETE"
      }),
    searchUsers: (keyword?: string, page = 1, size = 20) =>
      request<PageResponse<PlatformUserSummary>>(`${configurationBase}/users${query({ keyword, page, size })}`),
    listRepositories: (page = 1, size = 50) =>
      request<PageResponse<CodeRepositoryConfig>>(`${configurationBase}/repositories${query({ page, size })}`),
    createRepository: (payload: CreateRepositoryPayload) =>
      request<CodeRepositoryConfig>(`${configurationBase}/repositories`, { method: "POST", body: JSON.stringify(payload) }),
    updateRepository: (repositoryId: string, payload: UpdateRepositoryPayload) =>
      request<CodeRepositoryConfig>(`${configurationBase}/repositories/${encodeURIComponent(repositoryId)}`, {
        method: "PATCH",
        body: JSON.stringify(payload)
      }),
    listApplicationRepositories: (appId: string) =>
      request<CodeRepositoryConfig[]>(`${configurationBase}/applications/${encodeURIComponent(appId)}/repositories`),
    linkApplicationRepository: (appId: string, repositoryId: string) =>
      request<CodeRepositoryConfig>(`${configurationBase}/applications/${encodeURIComponent(appId)}/repositories`, {
        method: "POST",
        body: JSON.stringify({ repositoryId })
      }),
    unlinkApplicationRepository: (appId: string, repositoryId: string) =>
      request<void>(`${configurationBase}/applications/${encodeURIComponent(appId)}/repositories/${encodeURIComponent(repositoryId)}`, {
        method: "DELETE"
      }),
    listRepositoryApplications: (repositoryId: string) =>
      request<ApplicationDefinition[]>(`${configurationBase}/repositories/${encodeURIComponent(repositoryId)}/applications`),
    linkRepositoryApplication: (repositoryId: string, appId: string) =>
      request<ApplicationDefinition>(`${configurationBase}/repositories/${encodeURIComponent(repositoryId)}/applications`, {
        method: "POST",
        body: JSON.stringify({ appId })
      }),
    unlinkRepositoryApplication: (repositoryId: string, appId: string) =>
      request<void>(`${configurationBase}/repositories/${encodeURIComponent(repositoryId)}/applications/${encodeURIComponent(appId)}`, {
        method: "DELETE"
      }),
    listRepositoryBranches: (repositoryId: string) =>
      request<string[]>(`${configurationBase}/repositories/${encodeURIComponent(repositoryId)}/branches`),
    listRepositoryDirectories: (repositoryId: string, branch: string) =>
      request<string[]>(`${configurationBase}/repositories/${encodeURIComponent(repositoryId)}/directories${query({ branch })}`),
    listApplicationWorkspaces: (appId: string) =>
      request<ApplicationWorkspaceConfig[]>(`${configurationBase}/applications/${encodeURIComponent(appId)}/workspaces`),
    createApplicationWorkspace: (appId: string, payload: CreateApplicationWorkspacePayload) =>
      request<ApplicationWorkspaceConfig>(`${configurationBase}/applications/${encodeURIComponent(appId)}/workspaces`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    renameApplicationWorkspace: (appId: string, workspaceId: string, payload: { workspaceName: string }) =>
      request<ApplicationWorkspaceConfig>(
        `${configurationBase}/applications/${encodeURIComponent(appId)}/workspaces/${encodeURIComponent(workspaceId)}`,
        { method: "PATCH", body: JSON.stringify(payload) }
      ),
    deleteApplicationWorkspace: (appId: string, workspaceId: string) =>
      request<void>(`${configurationBase}/applications/${encodeURIComponent(appId)}/workspaces/${encodeURIComponent(workspaceId)}`, {
        method: "DELETE"
      }),
    listPersonalSshKeys: () => request<SshKeyMetadata[]>(`${configurationBase}/personal/ssh-keys`),
    addPersonalSshKey: (payload: AddSshKeyPayload) =>
      request<SshKeyMetadata>(`${configurationBase}/personal/ssh-keys`, { method: "POST", body: JSON.stringify(payload) }),
    deletePersonalSshKey: (sshKeyId: string) =>
      request<void>(`${configurationBase}/personal/ssh-keys/${encodeURIComponent(sshKeyId)}`, { method: "DELETE" })
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

class WorkspaceFileSocketClient {
  private readonly socket: WorkspaceWebSocketLike;
  private readonly pending = new Map<string, { resolve: (value: unknown) => void; reject: (error: unknown) => void; timeoutId: ReturnType<typeof setTimeout> }>();
  private readonly opened: Promise<void>;
  private sequence = 0;
  open = false;

  constructor(url: string, factory: WorkspaceWebSocketFactory, private readonly onClose: () => void) {
    this.socket = factory(url);
    this.opened = new Promise((resolve, reject) => {
      this.socket.onopen = () => {
        this.open = true;
        resolve();
      };
      this.socket.onerror = () => {
        reject(new Error("工作空间文件 WebSocket 连接失败"));
      };
      this.socket.onclose = () => {
        this.open = false;
        this.rejectAll(new Error("工作空间文件 WebSocket 已关闭"));
        this.onClose();
      };
      this.socket.onmessage = (event) => this.handleMessage(event.data);
    });
  }

  ready() {
    return this.opened;
  }

  request<T>(op: string, params: Record<string, unknown>): Promise<T> {
    if (!this.open) {
      return Promise.reject(new Error("工作空间文件 WebSocket 尚未连接"));
    }
    const id = `wfr_${Date.now()}_${++this.sequence}`;
    return new Promise<T>((resolve, reject) => {
      const timeoutId = setTimeout(() => {
        this.pending.delete(id);
        reject(new BackendApiError(408, {
          success: false,
          code: "REQUEST_TIMEOUT",
          message: "工作空间文件 WebSocket 请求超时",
          traceId: "",
          retryable: true,
          details: { op }
        }));
      }, 30000);
      this.pending.set(id, { resolve: resolve as (value: unknown) => void, reject, timeoutId });
      this.socket.send(JSON.stringify({ id, op, params }));
    });
  }

  close() {
    this.socket.close();
  }

  private handleMessage(payload: string) {
    const message = JSON.parse(payload) as {
      id?: string;
      type?: "result" | "error";
      data?: unknown;
      code?: string;
      message?: string;
      traceId?: string;
      details?: Record<string, unknown>;
    };
    const id = message.id;
    if (!id) return;
    const pending = this.pending.get(id);
    if (!pending) return;
    this.pending.delete(id);
    clearTimeout(pending.timeoutId);
    if (message.type === "error") {
      pending.reject(new BackendApiError(500, {
        success: false,
        code: message.code ?? "INTERNAL_ERROR",
        message: message.message ?? "工作空间文件操作失败",
        traceId: message.traceId ?? "",
        details: message.details ?? {}
      }));
      return;
    }
    pending.resolve(message.data);
  }

  private rejectAll(error: unknown) {
    for (const pending of this.pending.values()) {
      clearTimeout(pending.timeoutId);
      pending.reject(error);
    }
    this.pending.clear();
  }
}

function toWebSocketUrl(baseUrl: string, webSocketUrl: string): string {
  const absolute = webSocketUrl.startsWith("ws://") || webSocketUrl.startsWith("wss://")
    ? webSocketUrl
    : webSocketUrl.startsWith("http://") || webSocketUrl.startsWith("https://")
      ? webSocketUrl
      : `${baseUrl.replace(/\/$/, "")}${webSocketUrl.startsWith("/") ? "" : "/"}${webSocketUrl}`;
  if (absolute.startsWith("https://")) {
    return `wss://${absolute.slice("https://".length)}`;
  }
  if (absolute.startsWith("http://")) {
    return `ws://${absolute.slice("http://".length)}`;
  }
  return absolute;
}

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
    defaultModel: typeof value.defaultModel === "boolean" ? value.defaultModel : undefined,
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
  const hints = Array.isArray(value.hints) ? value.hints.filter((item): item is string => typeof item === "string") : undefined;
  return compactObject({
    commandId,
    name: text(value.name) ?? commandId,
    aliases: Array.isArray(value.aliases) ? value.aliases.filter((item): item is string => typeof item === "string") : undefined,
    description: text(value.description),
    arguments: text(value.arguments),
    source: text(value.source),
    hints
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

function normalizeAgentId(agentId: string) {
  const normalized = agentId.trim().toLowerCase();
  return normalized.length > 0 ? normalized : "opencode";
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
