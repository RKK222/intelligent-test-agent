import type {
  AgentInfo,
  AgentConfigCommitPayload,
  AgentConfigDiff,
  AgentConfigFileRoute,
  AgentConfigOperation,
  AgentConfigOperationTicketResponse,
  AgentConfigProgressEvent,
  AgentConfigStatus,
  AgentConfigWorktree,
  AgentConfigWorktreeOption,
  AgentConfigWorktreePayload,
  AiMessageFeedback,
  AiMessageFeedbackPayload,
  AiRunFeedback,
  AiRunFeedbackPayload,
  RunFeedbackQuery,
  RunFeedbackState,
  AddSshKeyPayload,
  AnalyticsExceptionDetail,
  AnalyticsOrganizationUsageRow,
  AnalyticsOverview,
  AnalyticsPeaks,
  AnalyticsQueryParams,
  AnalyticsSatisfaction,
  AnalyticsTimeSeriesPoint,
  AnalyticsUserUsageRow,
  ApplicationWorkspaceTemplate,
  ApplicationWorkspaceVersion,
  ApplicationDefinition,
  CreateApplicationPayload,
  ApplicationMember,
  ApplicationWorkspaceConfig,
  ApiFailure,
  ApiResponse,
  CodeRepositoryConfig,
  CommandInfo,
  CommonParameterChangeLog,
  ConversationRunContext,
  CreateApplicationWorkspacePayload,
  CreateWorkspaceAcceptedResponse,
  CreatePersonalWorkspacePayload,
  CreateRepositoryPayload,
  CreateWorkspaceVersionPayload,
  CreateUserPayload,
  CurrentUser,
  DefaultPersonalWorkspaceResponse,
  FileContent,
  FileSearchResult,
  FileStatus,
  FileTreeEntry,
  GeneralParameter,
  GeneralParameterListParams,
  GeneralParameterUpdatePayload,
  InternalModelProviderManagementResponse,
  InternalModelProviderRefreshStatus,
  InternalModelProviderUpdatePayload,
  LoginRequest,
  LoginResponse,
  ManagedApplication,
  ManagedWorkspaceRuntime,
  ModelInfo,
  NightExecutionSlots,
  NightExecutionTask,
  NightExecutionTaskQueryResponse,
  OpencodeRuntimeManagementOverview,
  OpencodeRuntimeManagementOverviewParams,
  OpencodeRuntimeMetricHistoryParams,
  OpencodeRuntimeContainerMetricHistory,
  OpencodeRuntimeBackendMetricHistory,
  OpencodeRuntimeManagedProcessCommandResult,
  OpencodeRuntimeManagementUserProcessParams,
  OpencodeRuntimeProcess,
  OpencodeProcessStartOperation,
  UserOpencodeProcessHealth,
  UserOpencodeProcessHealthRequest,
  UserOpencodeMessageGate,
  PageResponse,
  PlatformUserSummary,
  PersonalWorkspace,
  PersonalAgentConfigRuntimeReloadResult,
  PermissionRequest,
  PromptPart,
  WorkspaceViewFileContent,
  WorkspaceViewList,
  WorkspaceViewLocator,
  PublicAgentRepositoryStatus,
  ProviderInfo,
  RepositoryDeploymentOptions,
  RepositoryTreeResponse,
  PublishPersonalWorkspacePayload,
  PublishPersonalWorkspaceResult,
  ResolveWorkspaceGitConflictPayload,
  ResolveAllWorkspaceGitConflictsPayload,
  QuestionRequest,
  RepositoryTypeOption,
  RoleOption,
  IdentityStatus,
  Run,
  RunDiff,
  RunDiffAction,
  RuntimeResourceInfo,
  RuntimeToolInfo,
  SchedulerDiagnostics,
  ScheduledTaskListParams,
  ScheduledTaskManagementRun,
  ScheduledTaskManagementTask,
  ScheduledTaskRunListParams,
  ScheduledTaskUpdatePayload,
  SessionDiff,
  Session,
  SessionMessage,
  SideQuestionRequest,
  SideQuestionResponse,
  SideQuestionRunRequest,
  SideQuestionRunResponse,
  ManualQuestionRunRequest,
  SessionRuntimeStateSummary,
  SessionTreeMessagesResponse,
  SshKeyMetadata,
  SshKeyPublicKeyResponse,
  SyncWorkspacePayload,
  TerminalTicketRequest,
  ServerTerminalTicketRequest,
  TerminalTicketResponse,
  TodoItem,
  UpdateUserRolePayload,
  UpdateRepositoryPayload,
  UserManagementUser,
  UserOpencodeProcess,
  Workspace,
  WorkspaceBackendServer,
  WorkspaceCreateOperation,
  WorkspaceDiff,
  WorkspaceGitDiff,
  WorkspaceGitMergeCompletion,
  WorkspaceGitConflict,
  PublishPersonalWorkspacePreview,
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
export type AgentConfigProgressHandler = (event: AgentConfigProgressEvent) => void;

const WEBSOCKET_OPEN_STATE = 1;
const AGENT_CONFIG_PROGRESS_OPEN_TIMEOUT_MS = 3000;

export type BackendApiClientOptions = {
  baseUrl?: string;
  agentId?: string;
  apiToken?: string;
  fetcher?: typeof fetch;
  webSocketFactory?: WorkspaceWebSocketFactory;
  traceIdFactory?: () => string;
  requestTimeoutMs?: number;
  rawExchangeObserver?: (exchange: RawHttpExchange) => void;
};

export type RawHttpExchangePhase = "response" | "error" | "timeout";

export type RawHttpExchange = {
  id: string;
  method: string;
  url: string;
  path: string;
  traceId: string;
  requestHeaders: Record<string, string>;
  requestBody?: string;
  responseStatus?: number;
  responseHeaders?: Record<string, string>;
  responseText?: string;
  errorMessage?: string;
  phase: RawHttpExchangePhase;
  startedAt: string;
  endedAt: string;
  durationMs: number;
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
  command?: string;
  arguments?: string;
  contextToken?: string;
  clientRequestId?: string;
};

export type CreateNightExecutionTaskPayload = Omit<StartRunPayload, "sessionId" | "contextToken"> & {
  /** 夜间任务本身的幂等键，与 Run 幂等键分离。 */
  clientRequestId: string;
  runClientRequestId?: string;
  sessionId?: string;
  workspaceId: string;
  sessionTitle?: string;
  slotStart: string;
};

/** 引用资产库在单台 Linux 服务器上的同步投影。 */
export type ReferenceRepositoryServerStatus = {
  linuxServerId: string;
  status: string;
  currentBranch?: string | null;
  currentCommitHash?: string | null;
  online?: boolean;
  matchesTarget?: boolean | null;
  verifiedAt?: string | null;
  syncedAt?: string | null;
  error?: string | null;
};

/** 当前应用关联的引用资产库及其总体/逐服务器状态。 */
export type ReferenceRepositoryStatus = {
  repositoryId: string;
  name: string;
  englishName: string;
  gitUrl: string;
  repositoryPath?: string | null;
  initialized: boolean;
  branch?: string | null;
  targetCommitHash?: string | null;
  generation: number;
  status: string;
  operation?: "INITIALIZE" | "SYNCHRONIZE" | "SWITCH_BRANCH" | "VERIFY_POINTERS" | string | null;
  targetServerCount: number;
  readyServerCount: number;
  servers: ReferenceRepositoryServerStatus[];
  traceId?: string | null;
  message?: string | null;
};

/** 引用资产库单层目录响应；只有后端标记的首层 SDD 目录允许配置。 */
export type ReferenceRepositoryTreeNode = {
  path: string;
  name: string;
  directory: boolean;
  size: number;
  highlighted: boolean;
  selectable: boolean;
};

export type ExtraRequestInit = RequestInit & { timeoutMs?: number };

type RequestFn = <T>(path: string, init?: ExtraRequestInit) => Promise<T>;

export function createBackendApiClient(options: BackendApiClientOptions = {}) {
  const baseUrl = (options.baseUrl ?? readEnv("VITE_TEST_AGENT_API_BASE_URL") ?? "http://127.0.0.1:8080").replace(
    /\/$/,
    ""
  );
  const agentId = normalizeAgentId(options.agentId ?? readEnv("VITE_TEST_AGENT_AGENT_ID") ?? "opencode");
  const agentBase = `/api/internal/agent/${encodeURIComponent(agentId)}`;
  const configurationBase = "/api/internal/platform/configuration-management";
  const workspaceManagementBase = "/api/internal/platform/workspace-management";
  const agentConfigBase = `${workspaceManagementBase}/agent-config`;
  const opencodeRuntimeBase = "/api/internal/platform/opencode-runtime";
  const opencodeRuntimeManagementBase = "/api/internal/platform/opencode-runtime/management";
  const schedulerManagementBase = "/api/internal/platform/scheduler-management";
  const systemManagementBase = "/api/internal/platform/system-management";
  const analyticsBase = "/api/internal/platform/analytics";
  const commonParameterBase = `${configurationBase}/common-parameters`;
  const referenceRepositoryBase = (appId: string) =>
    `${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/reference-repositories`;
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

  async function requestFrom<T>(requestBaseUrl: string, path: string, init: ExtraRequestInit = {}): Promise<T> {
    const traceId = traceIdFactory();
    const headers = new Headers(init.headers);
    headers.set("Accept", "application/json");
    headers.set("X-Trace-Id", traceId);
    if (init.body != null && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    // 自动附加用户 Token：优先使用 options 中的 apiToken，其次从 sessionStorage 读取
    const userToken = options.apiToken ?? (typeof sessionStorage !== "undefined" ? sessionStorage.getItem("test-agent.auth.token") : null);
    if (userToken && !headers.has("Authorization")) {
      headers.set("Authorization", `Bearer ${userToken}`);
    }
    // 所有后端请求统一设置超时，避免文件、运行和配置管理界面在连接悬挂时一直停留在加载态。
    const controller = new AbortController();
    let timedOut = false;
    const timeoutMs = init.timeoutMs ?? requestTimeoutMs;
    const timeoutId =
      timeoutMs > 0
        ? setTimeout(() => {
            timedOut = true;
            controller.abort();
          }, timeoutMs)
        : undefined;
    const abortFromCaller = () => controller.abort();
    init.signal?.addEventListener("abort", abortFromCaller, { once: true });
    if (init.signal?.aborted) {
      controller.abort();
    }
    const method = (init.method ?? "GET").toUpperCase();
    const url = `${requestBaseUrl}${path}`;
    const startedAtMs = Date.now();
    const startedAt = new Date(startedAtMs).toISOString();
    const rawBase = {
      id: defaultTraceId(),
      method,
      url,
      path: pathFromUrl(url),
      traceId,
      requestHeaders: safeRequestHeaders(headers),
      requestBody: bodyToObservedRawText(init.body),
      startedAt
    };
    let rawExchangeReported = false;
    try {
      const { timeoutMs: _, ...restInit } = init;
      const response = await fetcher(url, { ...restInit, headers, signal: controller.signal });
      const responseText = await response.text();
      rawExchangeReported = true;
      notifyRawExchange(options.rawExchangeObserver, {
        ...rawBase,
        responseStatus: response.status,
        responseHeaders: responseHeadersToRecord(response.headers),
        // 观察器可能将完整响应展示在调试面板，必须递归清除上下文 token；业务解析仍使用原始 responseText。
        responseText: redactObservedJsonText(responseText),
        phase: "response",
        ...rawTiming(startedAtMs)
      });
      const body = readJsonFromText(response, responseText);
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
      if (!rawExchangeReported) {
        notifyRawExchange(options.rawExchangeObserver, {
          ...rawBase,
          errorMessage: error instanceof Error ? error.message : String(error),
          phase: timedOut ? "timeout" : "error",
          ...rawTiming(startedAtMs)
        });
      }
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

  async function request<T>(path: string, init: ExtraRequestInit = {}): Promise<T> {
    return requestFrom<T>(baseUrl, path, init);
  }

  async function requestCsv(path: string, init: RequestInit = {}): Promise<Blob> {
    const traceId = traceIdFactory();
    const headers = new Headers(init.headers);
    headers.set("Accept", "text/csv");
    headers.set("X-Trace-Id", traceId);
    const userToken = options.apiToken ?? (typeof sessionStorage !== "undefined" ? sessionStorage.getItem("test-agent.auth.token") : null);
    if (userToken && !headers.has("Authorization")) {
      headers.set("Authorization", `Bearer ${userToken}`);
    }
    const response = await fetcher(`${baseUrl}${path}`, { ...init, headers });
    if (!response.ok) {
      const body = await readJson(response);
      throw new BackendApiError(response.status, normalizeFailure(body, traceId, response.status));
    }
    return response.blob();
  }

  const agentPath = (path: string) => `${agentBase}${path}`;
  const workspaceFileSockets = new Map<string, WorkspaceFileSocketClient>();
  const workspaceFileConnections = new Map<string, Promise<WorkspaceFileSocketClient>>();
  const agentConfigFileSockets = new Map<string, WorkspaceFileSocketClient>();
  const agentConfigFileConnections = new Map<string, Promise<WorkspaceFileSocketClient>>();

  async function workspaceFileRpc<T>(
    workspaceId: string,
    op: string,
    params: Record<string, unknown>,
    retryTransportOnce = false
  ): Promise<T> {
    for (let attempt = 0; ; attempt += 1) {
      try {
        const client = await ensureWorkspaceFileClient(workspaceId);
        return await client.request<T>(op, { workspaceId, ...params });
      } catch (error) {
        // 只有读操作显式开启一次传输重试；业务错误、超时与写操作原样返回。
        if (!retryTransportOnce || attempt > 0 || !(error instanceof WorkspaceFileTransportError)) {
          throw error;
        }
      }
    }
  }

  async function ensureWorkspaceFileClient(workspaceId: string): Promise<WorkspaceFileSocketClient> {
    const existing = workspaceFileSockets.get(workspaceId);
    if (existing?.open) {
      return existing;
    }
    const connecting = workspaceFileConnections.get(workspaceId);
    if (connecting) {
      return connecting;
    }
    existing?.close();
    // route、ticket 和 socket 创建必须作为一个整体复用，避免并发打开文件时重复建连。
    const connection = (async () => {
      const route = await request<WorkspaceFileRoute>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/file-ws-route`,
        { method: "POST" }
      );
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
      let client!: WorkspaceFileSocketClient;
      client = new WorkspaceFileSocketClient(
        toWebSocketUrl(route.baseUrl, ticket.webSocketUrl),
        webSocketFactory,
        () => {
          // 旧连接的迟到 close 只能清理自身，不能驱逐已经替换它的新连接。
          if (workspaceFileSockets.get(workspaceId) === client) {
            workspaceFileSockets.delete(workspaceId);
          }
        }
      );
      workspaceFileSockets.set(workspaceId, client);
      await client.ready();
      return client;
    })();
    workspaceFileConnections.set(workspaceId, connection);
    try {
      return await connection;
    } finally {
      if (workspaceFileConnections.get(workspaceId) === connection) {
        workspaceFileConnections.delete(workspaceId);
      }
    }
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

  async function agentConfigFileRpc<T>(
    scope: "PUBLIC" | "WORKSPACE",
    op: string,
    params: Record<string, unknown>,
    routeContext: { workspaceId?: string; worktreeId?: string | null; linuxServerId?: string | null } = {},
    retryTransportOnce = false
  ): Promise<T> {
    for (let attempt = 0; ; attempt += 1) {
      try {
        const client = await ensureAgentConfigFileClient(scope, routeContext);
        return await client.request<T>(op, {
          scope,
          workspaceId: routeContext.workspaceId,
          worktreeId: routeContext.worktreeId ?? undefined,
          ...params
        });
      } catch (error) {
        if (!retryTransportOnce || attempt > 0 || !(error instanceof WorkspaceFileTransportError)) {
          throw error;
        }
      }
    }
  }

  async function ensureAgentConfigFileClient(
    scope: "PUBLIC" | "WORKSPACE",
    context: { workspaceId?: string; worktreeId?: string | null; linuxServerId?: string | null }
  ): Promise<WorkspaceFileSocketClient> {
    const cacheKey = agentConfigSocketKey(scope, context);
    const existing = agentConfigFileSockets.get(cacheKey);
    if (existing?.open) {
      return existing;
    }
    const connecting = agentConfigFileConnections.get(cacheKey);
    if (connecting) {
      return connecting;
    }
    existing?.close();
    // Agent 配置按 scope 与路由上下文隔离，同一键的并发调用只允许创建一条连接。
    const connection = (async () => {
      const routePayload = {
        scope,
        workspaceId: context.workspaceId,
        worktreeId: context.worktreeId ?? undefined,
        linuxServerId: context.linuxServerId ?? undefined
      };
      const route = await request<AgentConfigFileRoute>(`${agentConfigBase}/file-ws-route`, {
        method: "POST",
        body: JSON.stringify(routePayload)
      });
      const ticket = await requestFrom<WorkspaceFileSocketTicketResponse>(
        route.baseUrl.replace(/\/$/, ""),
        "/api/internal/platform/workspace-management/file-ws/tickets",
        {
          method: "POST",
          body: JSON.stringify({
            workspaceId: scope === "WORKSPACE" ? context.workspaceId : undefined,
            linuxServerId: route.linuxServerId,
            mode: "agent-config",
            scope,
            worktreeId: context.worktreeId ?? undefined
          } satisfies WorkspaceFileSocketTicketRequest)
        }
      );
      let client!: WorkspaceFileSocketClient;
      client = new WorkspaceFileSocketClient(
        toWebSocketUrl(route.baseUrl, ticket.webSocketUrl),
        webSocketFactory,
        () => {
          if (agentConfigFileSockets.get(cacheKey) === client) {
            agentConfigFileSockets.delete(cacheKey);
          }
        }
      );
      agentConfigFileSockets.set(cacheKey, client);
      await client.ready();
      return client;
    })();
    agentConfigFileConnections.set(cacheKey, connection);
    try {
      return await connection;
    } finally {
      if (agentConfigFileConnections.get(cacheKey) === connection) {
        agentConfigFileConnections.delete(cacheKey);
      }
    }
  }

  function agentConfigSocketKey(
    scope: "PUBLIC" | "WORKSPACE",
    context: { workspaceId?: string; worktreeId?: string | null; linuxServerId?: string | null }
  ) {
    return [scope, context.workspaceId ?? "", context.worktreeId ?? "", context.linuxServerId ?? ""].join(":");
  }

  return {
    listWorkspaces: (page = 1, size = 20) =>
      request<PageResponse<Workspace>>(`${workspaceManagementBase}/workspaces?page=${page}&size=${size}`),
    getWorkspace: (workspaceId: string) => request<Workspace>(`${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}`),
    listManagedApplications: () => request<ManagedApplication[]>(`${workspaceManagementBase}/applications`),
    /** 仅返回当前应用关联的 APPLICATION_ASSET_REPOSITORY。 */
    listReferenceRepositories: (appId: string) =>
      request<ReferenceRepositoryStatus[]>(referenceRepositoryBase(appId)),
    initializeReferenceRepository: (appId: string, repositoryId: string, branch: string) =>
      request<ReferenceRepositoryStatus>(
        `${referenceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/initialize`,
        { method: "POST", body: JSON.stringify({ branch }) }
      ),
    synchronizeReferenceRepository: (appId: string, repositoryId: string) =>
      request<ReferenceRepositoryStatus>(
        `${referenceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/synchronize`,
        { method: "POST" }
      ),
    /** 将共享引用资产库切换到固定远端分支 HEAD，并由多节点协调器完成收敛。 */
    switchReferenceRepositoryBranch: (appId: string, repositoryId: string, branch: string) =>
      request<ReferenceRepositoryStatus>(
        `${referenceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/switch-branch`,
        { method: "POST", body: JSON.stringify({ branch }) }
      ),
    /** 只读核验各服务器本地 Git 指针，不触发 fetch 或 checkout。 */
    verifyReferenceRepositoryPointers: (appId: string, repositoryId: string) =>
      request<ReferenceRepositoryStatus>(
        `${referenceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/verify`,
        { method: "POST" }
      ),
    getReferenceRepositoryStatus: (appId: string, repositoryId: string) =>
      request<ReferenceRepositoryStatus>(
        `${referenceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/status`
      ),
    listReferenceRepositoryTree: (appId: string, repositoryId: string, path = "") =>
      request<ReferenceRepositoryTreeNode[]>(
        `${referenceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/tree${query({ path })}`
      ),
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
    gitPullWorkspaceVersion: (versionId: string) =>
      request<ApplicationWorkspaceVersion>(
        `${workspaceManagementBase}/workspace-versions/${encodeURIComponent(versionId)}/git-pull`,
        { method: "POST" }
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
    /**
     * 确保默认个人工作区存在：先查 (versionId, userId, workspaceName=default)，
     * 存在则复用，不存在则后台创建。
     */
    ensureDefaultPersonalWorkspace: (versionId: string) =>
      request<DefaultPersonalWorkspaceResponse>(
        `${workspaceManagementBase}/workspace-versions/${encodeURIComponent(versionId)}/ensure-default-personal-workspace`,
        { method: "POST" }
      ),
    /**
     * 基于本地 Git 获取工作区变更文件列表（不依赖 opencode runtime /vcs/diff）。
     * @param workspaceId 运行时 workspace ID（personal workspace 的 runtimeWorkspace.workspaceId）
     */
    getWorkspaceGitDiff: (workspaceId: string) =>
      request<WorkspaceGitDiff>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-diff`
      ),
    discardWorkspaceGitFiles: (workspaceId: string, files: string[]) =>
      request<void>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-discard`,
        { method: "POST", body: JSON.stringify({ files }) }
      ),
    stageWorkspaceGitFiles: (workspaceId: string, files: string[]) =>
      request<void>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-stage`,
        { method: "POST", body: JSON.stringify({ files }) }
      ),
    unstageWorkspaceGitFiles: (workspaceId: string, files: string[]) =>
      request<void>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-unstage`,
        { method: "POST", body: JSON.stringify({ files }) }
      ),
    getWorkspaceGitConflict: (workspaceId: string, path: string) =>
      request<WorkspaceGitConflict>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-conflict${query({ path })}`
      ),
    resolveWorkspaceGitConflict: (workspaceId: string, payload: ResolveWorkspaceGitConflictPayload) =>
      request<void>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-conflict/resolve`,
        { method: "POST", body: JSON.stringify(payload) }
      ),
    resolveAllWorkspaceGitConflicts: (
      workspaceId: string,
      payload: ResolveAllWorkspaceGitConflictsPayload
    ) =>
      request<void>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-conflict/resolve-all`,
        { method: "POST", body: JSON.stringify(payload) }
      ),
    abortWorkspaceGitConflict: (workspaceId: string) =>
      request<void>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-conflict/abort`,
        { method: "POST" }
      ),
    completeWorkspaceGitMerge: (workspaceId: string) =>
      request<WorkspaceGitMergeCompletion>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-conflict/complete`,
        { method: "POST" }
      ),
    /** 仅提交个人 worktree，不推送远端。 */
    commitPersonalWorkspace: (personalWorkspaceId: string, payload: PublishPersonalWorkspacePayload) =>
      request<PublishPersonalWorkspaceResult>(
        `${workspaceManagementBase}/personal-workspaces/${encodeURIComponent(personalWorkspaceId)}/commit`,
        { method: "POST", body: JSON.stringify(payload) }
      ),
    /** 从个人 HEAD 按白名单投影到应用 feature worktree，提交并推送。 */
    publishPersonalWorkspace: (personalWorkspaceId: string, payload: PublishPersonalWorkspacePayload) =>
      request<PublishPersonalWorkspaceResult>(
        `${workspaceManagementBase}/personal-workspaces/${encodeURIComponent(personalWorkspaceId)}/publish`,
        { method: "POST", body: JSON.stringify(payload) }
      ),
    previewPersonalWorkspacePublish: (personalWorkspaceId: string) =>
      request<PublishPersonalWorkspacePreview>(
        `${workspaceManagementBase}/personal-workspaces/${encodeURIComponent(personalWorkspaceId)}/publish-preview`,
        { method: "POST" }
      ),
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
    listWorkspaceView: async (workspaceId: string, locator: WorkspaceViewLocator): Promise<WorkspaceViewList> => {
      const result = await workspaceFileRpc<BackendWorkspaceViewList>(
        workspaceId,
        "workspace.view.list",
        { locator }
      );
      return {
        entries: result.entries.map((entry) => ({
          id: entry.id,
          path: entry.path,
          name: entry.name,
          type: entry.directory ? "directory" : "file",
          size: entry.size,
          modifiedAt: entry.lastModifiedAt,
          locator: entry.locator,
          source: entry.source,
          merged: entry.merged,
          collision: entry.collision,
          readonly: entry.readonly,
          workspacePath: entry.workspacePath,
          referenceAliases: entry.referenceAliases ?? []
        })),
        warnings: result.warnings ?? [],
        truncated: result.truncated === true
      };
    },
    readWorkspaceViewFile: async (
      workspaceId: string,
      locator: WorkspaceViewLocator
    ): Promise<WorkspaceViewFileContent> => {
      const data = await workspaceFileRpc<BackendWorkspaceViewFileContent>(
        workspaceId,
        "workspace.view.read",
        { locator },
        true
      );
      return {
        path: data.path || locator.path,
        content: typeof data.content === "string" ? data.content : "",
        encoding: "utf-8",
        size: data.size,
        readonly: data.readonly,
        source: data.source,
        referenceAlias: data.referenceAlias,
        locator: data.locator
      };
    },
    readFile: async (workspaceId: string, path: string, readonly = false) => {
      // 工作区文件读取与列表、写入保持同一条平台 WebSocket 路由，避免旧 OpenCode
      // HTTP 代理在跨服务器或响应格式变化时把真实 Markdown 内容丢在前端之外。
      const data = await workspaceFileRpc<BackendFileContent>(workspaceId, "workspace.read", { path }, true);
      return {
        path: data.path || path,
        content: typeof data.content === "string" ? data.content : "",
        encoding: "utf-8",
        size: data.size,
        readonly
      } satisfies FileContent;
    },
    writeFile: (workspaceId: string, path: string, content: string) =>
      workspaceFileRpc<void>(workspaceId, "workspace.write", { path, content }),
    uploadWorkspaceFile: (workspaceId: string, path: string, contentBase64: string) =>
      workspaceFileRpc<void>(workspaceId, "workspace.upload", { path, contentBase64 }),
    copyWorkspaceFile: (workspaceId: string, sourcePath: string, targetPath: string) =>
      workspaceFileRpc<void>(workspaceId, "workspace.copy", { sourcePath, targetPath }),
    moveWorkspaceFile: (workspaceId: string, sourcePath: string, targetPath: string) =>
      workspaceFileRpc<void>(workspaceId, "workspace.move", { sourcePath, targetPath }),
    renameWorkspaceFile: (workspaceId: string, path: string, name: string) =>
      workspaceFileRpc<void>(workspaceId, "workspace.rename", { path, name }),
    fileStatus: async (workspaceId: string, path: string) => {
      const status = await workspaceFileRpc<BackendFileStatus>(workspaceId, "workspace.status", { path });
      return {
        ...status,
        status: status.exists ? "unchanged" : "deleted"
      } satisfies FileStatus;
    },
    deleteWorkspaceFile: (workspaceId: string, path: string) =>
      workspaceFileRpc<void>(workspaceId, "workspace.delete", { path }),
    createDirectory: (workspaceId: string, path: string) =>
      workspaceFileRpc<void>(workspaceId, "workspace.mkdir", { path }),
    searchFiles: async (workspaceId: string, query: string) => {
      const results = await workspaceFileRpc<BackendFileSearchResult[]>(workspaceId, "workspace.search", { query });
      return results.map((result) => ({
        path: result.path,
        name: result.name,
        directory: result.directory,
        size: result.size,
        modifiedAt: result.lastModifiedAt
      })) satisfies FileSearchResult[];
    },
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
    getPublicAgentConfigStatus: () => request<AgentConfigStatus>(`${agentConfigBase}/public/status`),
    getWorkspaceAgentConfigStatus: (workspaceId: string) =>
      request<AgentConfigStatus>(`${agentConfigBase}/workspaces/${encodeURIComponent(workspaceId)}/status`),
    listPublicAgentBranches: () => request<string[]>(`${agentConfigBase}/public/branches`),
    listPublicAgentRepositories: () => request<PublicAgentRepositoryStatus[]>(`${agentConfigBase}/public/repositories`),
    listPublicAgentWorktrees: (linuxServerId: string) =>
      request<AgentConfigWorktreeOption[]>(`${agentConfigBase}/public/worktrees${query({ linuxServerId })}`),
    initializePublicAgentRepository: (linuxServerId: string, branch: string, operationId?: string) =>
      request<PublicAgentRepositoryStatus>(
        `${agentConfigBase}/public/repositories/${encodeURIComponent(linuxServerId)}/initialize`,
        {
          method: "POST",
          body: JSON.stringify({ branch, operationId })
        }
      ),
    pullPublicAgentRepository: (linuxServerId: string, branch: string, operationId?: string, discardLocalChanges = false) =>
      request<PublicAgentRepositoryStatus>(
        `${agentConfigBase}/public/repositories/${encodeURIComponent(linuxServerId)}/pull`,
        {
          method: "POST",
          body: JSON.stringify({ branch, operationId, discardLocalChanges })
        }
      ),
    updatePublicAgentConfig: (branch: string, operationId?: string, discardLocalChanges = false) =>
      request<AgentConfigOperation>(`${agentConfigBase}/public/update`, {
        method: "POST",
        body: JSON.stringify({ branch, operationId, discardLocalChanges })
      }),
    /**
     * 公共配置"提交并推送"复合接口：fetch 远端后提交本地变更、merge 远端分支并推送。
     */
    updatePublicAgentConfigAndPush: (payload: {
      branch: string;
      commitMessage: string;
      operationId?: string;
      discardLocalChanges?: boolean;
    }) =>
      request<AgentConfigOperation>(`${agentConfigBase}/public/update-and-push`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    getPublicAgentGitConflictFiles: (worktreeId?: string | null, linuxServerId?: string | null) =>
      request<{ files: string[] }>(
        `${agentConfigBase}/public/git-conflicts${query({ worktreeId, linuxServerId })}`
      ),
    getPublicAgentGitConflict: (path: string, worktreeId?: string | null, linuxServerId?: string | null) =>
      request<WorkspaceGitConflict>(
        `${agentConfigBase}/public/git-conflict${query({ path, worktreeId, linuxServerId })}`
      ),
    resolvePublicAgentGitConflict: (payload: ResolveWorkspaceGitConflictPayload & {
      worktreeId?: string | null;
      linuxServerId?: string | null;
    }) =>
      request<void>(`${agentConfigBase}/public/git-conflict/resolve`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    resolveAllPublicAgentGitConflicts: (payload: ResolveAllWorkspaceGitConflictsPayload & {
      worktreeId?: string | null;
      linuxServerId?: string | null;
    }) =>
      request<void>(`${agentConfigBase}/public/git-conflict/resolve-all`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    abortPublicAgentGitConflict: (worktreeId?: string | null, linuxServerId?: string | null) =>
      request<void>(`${agentConfigBase}/public/git-conflict/abort`, {
        method: "POST",
        body: JSON.stringify({ worktreeId, linuxServerId })
      }),
    listPublicAgentFiles: async (path = "", worktreeId?: string | null, linuxServerId?: string | null) => {
      const entries = await agentConfigFileRpc<BackendFileTreeEntry[]>(
        "PUBLIC",
        "agent-config.list",
        { path },
        { worktreeId, linuxServerId }
      );
      return entries.map(toFileTreeEntry);
    },
    readPublicAgentFile: async (path: string, worktreeId?: string | null, linuxServerId?: string | null) => {
      const file = await agentConfigFileRpc<BackendFileContent>(
        "PUBLIC",
        "agent-config.read",
        { path },
        { worktreeId, linuxServerId },
        true
      );
      return { ...file, encoding: "utf-8", readonly: false } satisfies FileContent;
    },
    writePublicAgentFile: (path: string, content: string, worktreeId?: string | null, linuxServerId?: string | null) =>
      agentConfigFileRpc<void>(
        "PUBLIC",
        "agent-config.write",
        { path, content },
        { worktreeId, linuxServerId }
      ),
    createPublicAgentWorktree: (payload: AgentConfigWorktreePayload) =>
      request<AgentConfigWorktree>(`${agentConfigBase}/public/worktrees`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    reloadPublicPersonalAgentRuntime: (worktreeId: string, linuxServerId?: string | null) =>
      request<PersonalAgentConfigRuntimeReloadResult>(`${agentConfigBase}/public/runtime-reload`, {
        method: "POST",
        body: JSON.stringify({ worktreeId, linuxServerId })
      }),
    getPublicAgentDiff: (worktreeId?: string | null) =>
      request<AgentConfigDiff>(`${agentConfigBase}/public/diff${query({ worktreeId })}`),
    stagePublicAgentFiles: (files: string[], worktreeId?: string | null) =>
      request<void>(`${agentConfigBase}/public/stage`, { method: "POST", body: JSON.stringify({ files, worktreeId }) }),
    unstagePublicAgentFiles: (files: string[], worktreeId?: string | null) =>
      request<void>(`${agentConfigBase}/public/unstage`, { method: "POST", body: JSON.stringify({ files, worktreeId }) }),
    discardPublicAgentFiles: (files: string[], worktreeId?: string | null) =>
      request<void>(`${agentConfigBase}/public/discard`, { method: "POST", body: JSON.stringify({ files, worktreeId }) }),
    commitPublicAgentConfig: (payload: AgentConfigCommitPayload) =>
      request<AgentConfigOperation>(`${agentConfigBase}/public/commit`, { method: "POST", body: JSON.stringify(payload) }),
    publishPublicAgentConfig: (worktreeId?: string | null, operationId?: string) =>
      request<AgentConfigOperation>(`${agentConfigBase}/public/publish`, {
        method: "POST",
        body: JSON.stringify({ worktreeId, operationId })
      }),
    listWorkspaceAgentFiles: async (workspaceId: string, path = "", worktreeId?: string | null) => {
      const entries = await agentConfigFileRpc<BackendFileTreeEntry[]>(
        "WORKSPACE",
        "agent-config.list",
        { path },
        { workspaceId, worktreeId }
      );
      return entries.map(toFileTreeEntry);
    },
    readWorkspaceAgentFile: async (workspaceId: string, path: string, worktreeId?: string | null) => {
      const file = await agentConfigFileRpc<BackendFileContent>(
        "WORKSPACE",
        "agent-config.read",
        { path },
        { workspaceId, worktreeId },
        true
      );
      return { ...file, encoding: "utf-8", readonly: false } satisfies FileContent;
    },
    writeWorkspaceAgentFile: (workspaceId: string, path: string, content: string, worktreeId?: string | null) =>
      agentConfigFileRpc<void>(
        "WORKSPACE",
        "agent-config.write",
        { path, content },
        { workspaceId, worktreeId }
      ),
    createWorkspaceAgentWorktree: (workspaceId: string, payload: AgentConfigWorktreePayload) =>
      request<AgentConfigWorktree>(`${agentConfigBase}/workspaces/${encodeURIComponent(workspaceId)}/worktrees`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    getWorkspaceAgentDiff: (workspaceId: string, worktreeId?: string | null) =>
      request<AgentConfigDiff>(`${agentConfigBase}/workspaces/${encodeURIComponent(workspaceId)}/diff${query({ worktreeId })}`),
    stageWorkspaceAgentFiles: (workspaceId: string, files: string[], worktreeId?: string | null) =>
      request<void>(`${agentConfigBase}/workspaces/${encodeURIComponent(workspaceId)}/stage`, {
        method: "POST",
        body: JSON.stringify({ files, worktreeId })
      }),
    unstageWorkspaceAgentFiles: (workspaceId: string, files: string[], worktreeId?: string | null) =>
      request<void>(`${agentConfigBase}/workspaces/${encodeURIComponent(workspaceId)}/unstage`, {
        method: "POST",
        body: JSON.stringify({ files, worktreeId })
      }),
    discardWorkspaceAgentFiles: (workspaceId: string, files: string[], worktreeId?: string | null) =>
      request<void>(`${agentConfigBase}/workspaces/${encodeURIComponent(workspaceId)}/discard`, {
        method: "POST",
        body: JSON.stringify({ files, worktreeId })
      }),
    commitWorkspaceAgentConfig: (workspaceId: string, payload: AgentConfigCommitPayload) =>
      request<AgentConfigOperation>(`${agentConfigBase}/workspaces/${encodeURIComponent(workspaceId)}/commit`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    publishWorkspaceAgentConfig: (workspaceId: string, worktreeId?: string | null, operationId?: string) =>
      request<AgentConfigOperation>(`${agentConfigBase}/workspaces/${encodeURIComponent(workspaceId)}/publish`, {
        method: "POST",
        body: JSON.stringify({ worktreeId, operationId })
      }),
    getAgentConfigOperation: (operationId: string) =>
      request<AgentConfigOperation | null>(`${agentConfigBase}/operations/${encodeURIComponent(operationId)}`),
    createAgentConfigOperationTicket: (operationId: string) =>
      request<AgentConfigOperationTicketResponse>(`${agentConfigBase}/operations/${encodeURIComponent(operationId)}/tickets`, { method: "POST" }),
    connectAgentConfigProgress: async (operationId: string, onEvent: AgentConfigProgressHandler) => {
      const ticket = await request<AgentConfigOperationTicketResponse>(
        `${agentConfigBase}/operations/${encodeURIComponent(operationId)}/tickets`,
        { method: "POST" }
      );
      const socket = webSocketFactory(toWebSocketUrl(baseUrl, ticket.webSocketUrl));
      socket.onmessage = (event) => onEvent(JSON.parse(event.data) as AgentConfigProgressEvent);
      let opened = socket.readyState === WEBSOCKET_OPEN_STATE;
      socket.onerror = () => {
        onEvent({
          type: "failed",
          operationId,
          status: "FAILED",
          errorCode: "WEBSOCKET_ERROR",
          errorMessage: "Agent 配置进度连接失败"
        });
      };
      if (!opened) {
        // Git 发布可能在几十毫秒内执行第一条命令；这里等待连接真正打开，避免丢失“当前正在执行的命令”事件。
        await new Promise<void>((resolve, reject) => {
          const timeout = setTimeout(() => {
            socket.close();
            reject(new Error("Agent 配置进度连接超时"));
          }, AGENT_CONFIG_PROGRESS_OPEN_TIMEOUT_MS);
          socket.onopen = () => {
            opened = true;
            clearTimeout(timeout);
            resolve();
          };
          socket.onerror = () => {
            clearTimeout(timeout);
            onEvent({
              type: "failed",
              operationId,
              status: "FAILED",
              errorCode: "WEBSOCKET_ERROR",
              errorMessage: "Agent 配置进度连接失败"
            });
            reject(new Error("Agent 配置进度连接失败"));
          };
        });
        socket.onerror = () =>
          onEvent({
            type: "failed",
            operationId,
            status: "FAILED",
            errorCode: "WEBSOCKET_ERROR",
            errorMessage: "Agent 配置进度连接失败"
          });
      }
      return socket;
    },
    listAllSessions: (page = 1, size = 30, q?: string) =>
      request<PageResponse<Session>>(`${opencodeRuntimeBase}/sessions${query({ page, size, q })}`),
    getSessionRuntimeState: () =>
      request<SessionRuntimeStateSummary>(`${opencodeRuntimeBase}/sessions/runtime-state`),
    listSessions: (workspaceId: string, page = 1, size = 20) =>
      request<PageResponse<Session>>(`${opencodeRuntimeBase}/workspaces/${workspaceId}/sessions?page=${page}&size=${size}`),
    getSession: (sessionId: string) => request<Session>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}`),
    updateSession: (sessionId: string, payload: { title?: string; pinned?: boolean }) =>
      request<Session>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}`, { method: "PATCH", body: JSON.stringify(payload) }),
    deleteSession: (sessionId: string) => request<Session>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}`, { method: "DELETE" }),
    listSessionMessages: (sessionId: string, page = 1, size = 100, options: { refresh?: boolean } = {}) =>
      request<PageResponse<SessionMessage>>(
        `${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/messages${query({ page, size, refresh: options.refresh })}`
      ),
    getNightExecutionSlots: () =>
      request<NightExecutionSlots>(`${opencodeRuntimeBase}/night-execution/slots`),
    createNightExecutionTask: (payload: CreateNightExecutionTaskPayload) =>
      request<NightExecutionTask>(`${opencodeRuntimeBase}/night-execution/tasks`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    listNightExecutionTasks: (params: { sessionId?: string; page?: number; size?: number } = {}) =>
      request<NightExecutionTaskQueryResponse>(
        `${opencodeRuntimeBase}/night-execution/tasks${query({
          sessionId: params.sessionId,
          page: params.page,
          size: params.size
        })}`
      ),
    adjustNightExecutionTask: (taskId: string, slotStart: string) =>
      request<NightExecutionTask>(`${opencodeRuntimeBase}/night-execution/tasks/${encodeURIComponent(taskId)}`, {
        method: "PATCH",
        body: JSON.stringify({ slotStart })
      }),
    cancelNightExecutionTask: (taskId: string) =>
      request<NightExecutionTask>(`${opencodeRuntimeBase}/night-execution/tasks/${encodeURIComponent(taskId)}/cancel`, {
        method: "POST"
      }),
    dismissNightExecutionTask: (taskId: string) =>
      request<NightExecutionTask>(`${opencodeRuntimeBase}/night-execution/tasks/${encodeURIComponent(taskId)}/dismiss`, {
        method: "POST"
      }),
    getSessionTreeMessages: (sessionId: string) =>
      request<SessionTreeMessagesResponse>(agentPath(`/sessions/${encodeURIComponent(sessionId)}/session-tree/messages`)),
    putMessageFeedback: (messageId: string, payload: AiMessageFeedbackPayload) =>
      request<AiMessageFeedback>(`/api/internal/platform/opencode-runtime/messages/${encodeURIComponent(messageId)}/feedback`, {
        method: "PUT",
        body: JSON.stringify(payload)
      }),
    getMyMessageFeedback: (messageId: string) =>
      request<AiMessageFeedback | null>(`/api/internal/platform/opencode-runtime/messages/${encodeURIComponent(messageId)}/feedback/me`),
    putRunFeedback: (runId: string, payload: AiRunFeedbackPayload) =>
      request<AiRunFeedback>(`${opencodeRuntimeBase}/runs/${encodeURIComponent(runId)}/feedback`, {
        method: "PUT",
        body: JSON.stringify(payload)
      }),
    getMyRunFeedback: (runId: string) =>
      request<AiRunFeedback | null>(`${opencodeRuntimeBase}/runs/${encodeURIComponent(runId)}/feedback/me`),
    queryMyRunFeedbacks: (payload: RunFeedbackQuery) =>
      request<RunFeedbackState[]>(`${opencodeRuntimeBase}/run-feedbacks/me/query`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    getActiveRun: (sessionId: string) => request<Run | null>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/active-run`),
    askSideQuestion: (sessionId: string, payload: SideQuestionRequest) =>
      request<SideQuestionResponse>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/side-question`, {
        method: "POST",
        body: JSON.stringify(payload),
        timeoutMs: 120000
      }),
    getRunContext: (sessionId: string) =>
      request<ConversationRunContext>(agentPath(`/sessions/${encodeURIComponent(sessionId)}/run-context`), { method: "POST" }),
    startSideQuestionRun: (sessionId: string, payload: SideQuestionRunRequest) =>
      request<SideQuestionRunResponse>(
        `${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/side-question/runs`,
        {
          method: "POST",
          body: JSON.stringify(payload)
        }
      ),
    startManualQuestionRun: (payload: ManualQuestionRunRequest) =>
      request<SideQuestionRunResponse>(`${opencodeRuntimeBase}/manual-question/runs`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    createSession: (workspaceId: string, title: string) =>
      request<Session>(`${opencodeRuntimeBase}/sessions`, { method: "POST", body: JSON.stringify({ workspaceId, title }) }),
    startRun: (sessionIdOrPayload: string | StartRunPayload, prompt?: string) =>
      request<Run>(agentPath("/runs"), {
        method: "POST",
        body: JSON.stringify(normalizeStartRunPayload(sessionIdOrPayload, prompt)),
        timeoutMs: 120000
      }),
    getMyOpencodeProcess: () => request<UserOpencodeProcess>(agentPath("/processes/me")),
    getMyOpencodeMessageGate: () =>
      request<UserOpencodeMessageGate>(agentPath("/processes/me/message-gate")),
    getMyOpencodeProcessHealth: (params: UserOpencodeProcessHealthRequest) =>
      request<UserOpencodeProcessHealth>(agentPath(`/processes/me/health${query(params)}`)),
    initializeMyOpencodeProcess: (operationId?: string) =>
      request<UserOpencodeProcess>(agentPath("/processes/me/initialize"), {
        method: "POST",
        ...(operationId ? { body: JSON.stringify({ operationId }) } : {}),
        timeoutMs: 120000
      }),
    getOpencodeProcessStartOperation: (operationId: string) =>
      request<OpencodeProcessStartOperation>(
        agentPath(`/processes/me/initialize-operations/${encodeURIComponent(operationId)}`)
      ),
    getOpencodeRuntimeManagementOverview: (params: OpencodeRuntimeManagementOverviewParams = {}) =>
      request<OpencodeRuntimeManagementOverview>(`${opencodeRuntimeManagementBase}/overview${query({ ...params })}`),
    getOpencodeRuntimeManagementUserProcesses: (params: OpencodeRuntimeManagementUserProcessParams) =>
      request<PageResponse<OpencodeRuntimeProcess>>(`${opencodeRuntimeManagementBase}/user-processes${query({ ...params })}`),
    getOpencodeRuntimeContainerMetrics: (containerId: string, params: OpencodeRuntimeMetricHistoryParams = {}) =>
      request<OpencodeRuntimeContainerMetricHistory>(
        `${opencodeRuntimeManagementBase}/containers/${encodeURIComponent(containerId)}/metrics${query({ ...params })}`
      ),
    getOpencodeRuntimeBackendServerMetrics: (linuxServerId: string, params: OpencodeRuntimeMetricHistoryParams = {}) =>
      request<OpencodeRuntimeBackendMetricHistory>(
        `${opencodeRuntimeManagementBase}/linux-servers/${encodeURIComponent(linuxServerId)}/backend-metrics${query({ ...params })}`
      ),
    restartOpencodeRuntimeManagedProcess: (containerId: string, port: number) =>
      request<OpencodeRuntimeManagedProcessCommandResult>(
        `${opencodeRuntimeManagementBase}/containers/${encodeURIComponent(containerId)}/processes/${encodeURIComponent(String(port))}/restart`,
        { method: "POST" }
      ),
    stopOpencodeRuntimeManagedProcess: (containerId: string, port: number) =>
      request<OpencodeRuntimeManagedProcessCommandResult>(
        `${opencodeRuntimeManagementBase}/containers/${encodeURIComponent(containerId)}/processes/${encodeURIComponent(String(port))}/stop`,
        { method: "POST" }
      ),
    getAnalyticsOverview: (params: AnalyticsQueryParams = {}) =>
      request<AnalyticsOverview>(`${analyticsBase}/overview${query({ ...params })}`),
    getAnalyticsTimeseries: (params: AnalyticsQueryParams = {}) =>
      request<AnalyticsTimeSeriesPoint[]>(`${analyticsBase}/timeseries${query({ ...params })}`),
    getAnalyticsPeaks: (params: AnalyticsQueryParams = {}) =>
      request<AnalyticsPeaks>(`${analyticsBase}/peaks${query({ ...params })}`),
    getAnalyticsUsers: (params: AnalyticsQueryParams = {}) =>
      request<PageResponse<AnalyticsUserUsageRow>>(`${analyticsBase}/users${query({ ...params })}`),
    getAnalyticsOrganizations: (params: AnalyticsQueryParams & { groupBy?: string } = {}) =>
      request<AnalyticsOrganizationUsageRow[]>(`${analyticsBase}/organizations${query({ ...params })}`),
    getAnalyticsSatisfaction: (params: AnalyticsQueryParams = {}) =>
      request<AnalyticsSatisfaction>(`${analyticsBase}/satisfaction${query({ ...params })}`),
    getAnalyticsExceptions: (params: AnalyticsQueryParams = {}) =>
      request<PageResponse<AnalyticsExceptionDetail>>(`${analyticsBase}/exceptions${query({ ...params })}`),
    exportAnalyticsCsv: (type: "overview" | "timeseries" | "users" | "organizations" | "feedback" | "exceptions", params: AnalyticsQueryParams = {}) =>
      requestCsv(`${analyticsBase}/export${query({ ...params, type })}`),
    listScheduledTasks: (params: ScheduledTaskListParams = {}) =>
      request<PageResponse<ScheduledTaskManagementTask>>(
        `${schedulerManagementBase}/tasks${query({ page: params.page, size: params.size })}`
      ),
    getScheduledTask: (taskKey: string) =>
      request<ScheduledTaskManagementTask>(`${schedulerManagementBase}/tasks/${encodeURIComponent(taskKey)}`),
    getSchedulerDiagnostics: (taskKey: string) =>
      request<SchedulerDiagnostics>(`${schedulerManagementBase}/diagnostics${query({ taskKey })}`),
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
    listGeneralParameters: (params: GeneralParameterListParams = {}) =>
      request<PageResponse<GeneralParameter>>(
        `${commonParameterBase}${query({ platform: params.platform, page: params.page, size: params.size })}`
      ),
    updateGeneralParameter: (parameterId: string, payload: GeneralParameterUpdatePayload) =>
      request<GeneralParameter>(`${commonParameterBase}/${encodeURIComponent(parameterId)}`, {
        method: "PATCH",
        body: JSON.stringify(payload)
      }),
    listCommonParameterChangeLogs: (parameterId: string) =>
      request<CommonParameterChangeLog[]>(`${commonParameterBase}/${encodeURIComponent(parameterId)}/change-logs`),
    getInternalModelProviders: () =>
      request<InternalModelProviderManagementResponse>(`${configurationBase}/internal-model-providers`),
    updateInternalModelProviders: (payload: InternalModelProviderUpdatePayload) =>
      request<InternalModelProviderManagementResponse>(`${configurationBase}/internal-model-providers`, {
        method: "PUT",
        body: JSON.stringify(payload)
      }),
    getInternalModelProviderRefreshStatus: () =>
      request<InternalModelProviderRefreshStatus>(`${configurationBase}/internal-model-providers/refresh-status`),
    refreshInternalModelProviders: () =>
      request<InternalModelProviderRefreshStatus>(`${configurationBase}/internal-model-providers/refresh`, { method: "POST" }),
    getRun: (runId: string) => request<Run>(agentPath(`/runs/${encodeURIComponent(runId)}`)),
    cancelRun: (runId: string) => request<Run>(agentPath(`/runs/${encodeURIComponent(runId)}/cancel`), { method: "POST" }),
    getRunDiff: (runId: string) => request<RunDiff>(agentPath(`/runs/${encodeURIComponent(runId)}/diff`)),
    acceptRunDiff: (runId: string) => request<RunDiffAction>(agentPath(`/runs/${encodeURIComponent(runId)}/diff/accept`), { method: "POST" }),
    rejectRunDiff: (runId: string) => request<RunDiffAction>(agentPath(`/runs/${encodeURIComponent(runId)}/diff/reject`), { method: "POST" }),
    listAgents: async (workspaceId?: string, init?: ExtraRequestInit) =>
      (await runtimeList(`${opencodeRuntimeBase}/agents${query({ workspaceId })}`, request, init)).map(toAgentInfo),
    listModels: async (workspaceId?: string) => (await runtimeList(`${opencodeRuntimeBase}/models${query({ workspaceId })}`, request)).map(toModelInfo),
    listProviders: async (workspaceId?: string) =>
      (await runtimeList(`${opencodeRuntimeBase}/providers${query({ workspaceId })}`, request)).map(toProviderInfo),
    getConfig: (workspaceId?: string) => request<unknown>(`${opencodeRuntimeBase}/config${query({ workspaceId })}`),
    updateConfig: (payload: Record<string, unknown>, workspaceId?: string) =>
      request<unknown>(`${opencodeRuntimeBase}/config${query({ workspaceId })}`, { method: "PATCH", body: JSON.stringify(payload) }),
    disposeGlobal: () => request<unknown>(`${opencodeRuntimeBase}/global/dispose`, { method: "POST" }),
    listProviderAuth: (workspaceId?: string) => request<unknown>(`${opencodeRuntimeBase}/provider/auth${query({ workspaceId })}`),
    authorizeProviderOAuth: (providerId: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/provider/${encodeURIComponent(providerId)}/oauth/authorize`, payload, request),
    completeProviderOAuth: (providerId: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/provider/${encodeURIComponent(providerId)}/oauth/callback`, payload, request),
    setProviderAuth: (providerId: string, payload: Record<string, unknown>) =>
      request<unknown>(`${opencodeRuntimeBase}/auth/${encodeURIComponent(providerId)}`, { method: "PUT", body: JSON.stringify(payload) }),
    removeProviderAuth: (providerId: string) =>
      request<unknown>(`${opencodeRuntimeBase}/auth/${encodeURIComponent(providerId)}`, { method: "DELETE" }),
    listCommands: async (workspaceId?: string) =>
      (await runtimeList(`${opencodeRuntimeBase}/commands${query({ workspaceId })}`, request)).map(toCommandInfo),
    listReferences: (workspaceId?: string) => request<unknown>(`${opencodeRuntimeBase}/references${query({ workspaceId })}`),
    listRuntimeFiles: (workspaceId?: string, path = ".") => request<unknown>(`${opencodeRuntimeBase}/fs/list${query({ workspaceId, path })}`),
    findRuntimeFiles: (workspaceId?: string, search = "") => request<unknown>(`${opencodeRuntimeBase}/fs/find${query({ workspaceId, query: search })}`),
    readRuntimeFile: (workspaceId: string | undefined, path: string) =>
      request<unknown>(`${opencodeRuntimeBase}/fs/read${query({ workspaceId, path })}`),
    getVcsStatus: (workspaceId?: string) => request<unknown>(`${opencodeRuntimeBase}/vcs/status${query({ workspaceId })}`),
    getVcsDiff: (workspaceId?: string, mode = "git", context?: number) =>
      request<unknown>(`${opencodeRuntimeBase}/vcs/diff${query({ workspaceId, mode, context })}`),
    getVcsDiffFiles: async (workspaceId?: string, mode = "working", context?: number) => ({
      files: listFromRuntimeEnvelope(await request<unknown>(`${opencodeRuntimeBase}/vcs/diff${query({ workspaceId, mode, context })}`)).map(toRunDiffFile)
    }),
    getLspStatus: (workspaceId?: string) => request<unknown>(`${opencodeRuntimeBase}/lsp/status${query({ workspaceId })}`),
    getMcpStatus: (workspaceId?: string) => request<unknown>(`${opencodeRuntimeBase}/mcp/status${query({ workspaceId })}`),
    getMcpResources: async (workspaceId?: string) =>
      listFromRuntimeEnvelope(await request<unknown>(`${opencodeRuntimeBase}/mcp/resources${query({ workspaceId })}`)).map(toRuntimeResourceInfo),
    getMcpTools: async (workspaceId?: string, provider?: string, model?: string) =>
      listValuesFromRuntimeEnvelope(await request<unknown>(`${opencodeRuntimeBase}/mcp/tools${query({ workspaceId, provider, model })}`)).map((item) =>
        typeof item === "string" ? toRuntimeToolInfo({ id: item, name: item }) : toRuntimeToolInfo(item)
      ),
    startMcpAuth: (name: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/mcp/${encodeURIComponent(name)}/auth`, payload, request),
    completeMcpAuth: (name: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/mcp/${encodeURIComponent(name)}/auth/callback`, payload, request),
    authenticateMcp: (name: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/mcp/${encodeURIComponent(name)}/auth/authenticate`, payload, request),
    removeMcpAuth: (name: string) => request<unknown>(`${opencodeRuntimeBase}/mcp/${encodeURIComponent(name)}/auth`, { method: "DELETE" }),
    listWorktrees: (workspaceId?: string) => request<unknown>(`${opencodeRuntimeBase}/worktrees${query({ workspaceId })}`),
    createWorktree: (payload?: Record<string, unknown>) => postRuntime(`${opencodeRuntimeBase}/worktrees`, payload, request),
    removeWorktree: (payload?: Record<string, unknown>) =>
      request<unknown>(`${opencodeRuntimeBase}/worktrees`, { method: "DELETE", body: payload == null ? undefined : JSON.stringify(payload) }),
    resetWorktree: (payload?: Record<string, unknown>) => postRuntime(`${opencodeRuntimeBase}/worktrees/reset`, payload, request),
    getSessionChildren: (sessionId: string) => request<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/children`),
    getSessionTodo: async (sessionId: string) =>
      listFromRuntimeEnvelope(await request<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/todo`)).map(toTodoItem),
    getSessionDiff: async (sessionId: string, messageId?: string) => ({
      sessionId,
      messageId,
      files: listFromRuntimeEnvelope(
        await request<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/diff${query({ messageId })}`)
      ).map(toRunDiffFile)
    }) satisfies SessionDiff,
    abortSession: (sessionId: string) => request<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/abort`, { method: "POST" }),
    forkSession: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/fork`, payload, request),
    compactSession: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/compact`, payload, request),
    revertSession: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/revert`, payload, request),
    unrevertSession: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/unrevert`, payload, request),
    runSessionCommand: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/command`, payload, request, { timeoutMs: 120000 }),
    runSessionShell: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/shell`, payload, request, { timeoutMs: 120000 }),
    shareSession: (sessionId: string) =>
      postRuntime(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/share`, undefined, request),
    unshareSession: (sessionId: string) =>
      request<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/share`, { method: "DELETE" }),
    listSessionPermissions: async (sessionId: string) =>
      listFromRuntimeEnvelope(await request<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/permissions`)).map((item) =>
        toPermissionRequest(item, sessionId)
      ),
    replySessionPermission: (sessionId: string, requestId: string, payload: { decision?: "once" | "always" | "reject"; reply?: string; message?: string }) =>
      request<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/permissions/${encodeURIComponent(requestId)}/reply`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    listSessionQuestions: async (sessionId: string) =>
      listFromRuntimeEnvelope(await request<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/questions`)).map((item) =>
        toQuestionRequest(item, sessionId)
      ),
    replySessionQuestion: (sessionId: string, requestId: string, payload: { answers: unknown[] }) =>
      request<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/questions/${encodeURIComponent(requestId)}/reply`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    rejectSessionQuestion: (sessionId: string, requestId: string) =>
      request<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/questions/${encodeURIComponent(requestId)}/reject`, {
        method: "POST"
      }),
    createTerminalTicket: (sessionId: string, payload: TerminalTicketRequest = {}) =>
      request<TerminalTicketResponse>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/terminal/tickets`, {
        method: "POST",
        body: JSON.stringify(compactObject(payload))
      }),
    createServerTerminalTicket: (linuxServerId: string, payload: ServerTerminalTicketRequest) =>
      request<TerminalTicketResponse>(
        `${opencodeRuntimeBase}/management/linux-servers/${encodeURIComponent(linuxServerId)}/terminal/tickets`,
        {
          method: "POST",
          body: JSON.stringify(payload)
        }
      ),

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
    createApplication: (payload: CreateApplicationPayload) =>
      request<ApplicationDefinition>(`${configurationBase}/applications`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
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

    // ---- 用户管理 API ----

    /** 分页查询用户列表（仅 SUPER_ADMIN）。 */
    listUsers: (keyword?: string, page = 1, size = 50) =>
      request<PageResponse<UserManagementUser>>(`${systemManagementBase}/users${query({ keyword, page, size })}`),
    /** 创建测试用户，密码由后端注入默认值 123456。 */
    createUser: (payload: CreateUserPayload) =>
      request<UserManagementUser>(`${systemManagementBase}/users`, { method: "POST", body: JSON.stringify(payload) }),
    /** 调整指定用户的全局角色（仅 SUPER_ADMIN）。 */
    updateUserRole: (userId: string, payload: UpdateUserRolePayload) =>
      request<UserManagementUser>(`${systemManagementBase}/users/${encodeURIComponent(userId)}/roles`, {
        method: "PUT",
        body: JSON.stringify(payload)
      }),
    /** 查询可选角色列表，供新增用户下拉选择。 */
    listRoles: () => request<RoleOption[]>(`${systemManagementBase}/roles`),

    // ---- 数据库 IDENTITY 运维 API ----

    /** 查询白名单表 identity 状态（仅 SUPER_ADMIN）。 */
    listIdentityStatuses: () => request<IdentityStatus[]>(`${systemManagementBase}/identity`),
    /** 把指定表 identity 对齐到 max(id)+1。 */
    alignIdentity: (table: string) =>
      request<IdentityStatus>(`${systemManagementBase}/identity/align`, { method: "POST", body: JSON.stringify({ table }) }),
    /** 手动把指定表 identity 重启到目标值。 */
    restartIdentity: (table: string, targetValue: number) =>
      request<IdentityStatus>(`${systemManagementBase}/identity/restart`, { method: "POST", body: JSON.stringify({ table, targetValue }) }),

    listRepositories: (page = 1, size = 50) =>
      request<PageResponse<CodeRepositoryConfig>>(`${configurationBase}/repositories${query({ page, size })}`),
    listRepositoryTypes: () =>
      request<RepositoryTypeOption[]>(`${configurationBase}/repository-types`),
    getRepositoryDeploymentOptions: () =>
      request<RepositoryDeploymentOptions>(`${configurationBase}/repository-deployment-options`),
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
    getRepositoryTree: (appId: string, repositoryId: string, branch: string) =>
      request<RepositoryTreeResponse>(
        `${configurationBase}/applications/${encodeURIComponent(appId)}/repositories/${encodeURIComponent(repositoryId)}/tree${query({ branch })}`
      ),
    listApplicationWorkspaces: (appId: string) =>
      request<ApplicationWorkspaceConfig[]>(`${configurationBase}/applications/${encodeURIComponent(appId)}/workspaces`),
    createApplicationWorkspace: (appId: string, payload: CreateApplicationWorkspacePayload) =>
      request<CreateWorkspaceAcceptedResponse>(`${configurationBase}/applications/${encodeURIComponent(appId)}/workspaces`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    getWorkspaceCreateOperation: (operationId: string) =>
      request<WorkspaceCreateOperation>(`${configurationBase}/workspace-create-operations/${encodeURIComponent(operationId)}`),
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
    /** 获取服务端 RSA 公钥（SPKI Base64），供前端混合加密 SSH 私钥。 */
    getSshKeyPublicKey: () => request<SshKeyPublicKeyResponse>(`${configurationBase}/ssh-key/public-key`),
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

type BackendWorkspaceViewEntry = {
  id: string;
  path: string;
  name: string;
  directory: boolean;
  size: number;
  lastModifiedAt?: string;
  locator: WorkspaceViewLocator;
  source: "WORKSPACE" | "REFERENCE" | "MIXED";
  merged: boolean;
  collision: boolean;
  readonly: boolean;
  workspacePath?: string;
  referenceAliases?: string[];
};

type BackendWorkspaceViewList = {
  entries: BackendWorkspaceViewEntry[];
  warnings?: WorkspaceViewList["warnings"];
  truncated?: boolean;
};

type BackendWorkspaceViewFileContent = {
  path: string;
  content: string;
  size: number;
  readonly: boolean;
  source: "WORKSPACE" | "REFERENCE" | "MIXED";
  referenceAlias?: string;
  locator: WorkspaceViewLocator;
};

type BackendFileStatus = {
  path: string;
  exists: boolean;
  directory: boolean;
  size: number;
  lastModifiedAt?: string;
};

type BackendFileSearchResult = {
  path: string;
  name: string;
  directory: string;
  size: number;
  lastModifiedAt?: string;
};

function toFileTreeEntry(entry: BackendFileTreeEntry): FileTreeEntry {
  return {
    path: entry.path,
    name: entry.name,
    type: entry.directory ? "directory" : "file",
    size: entry.size,
    modifiedAt: entry.lastModifiedAt
  };
}

class WorkspaceFileTransportError extends Error {
  constructor(message: string, readonly cause?: unknown) {
    super(message);
    this.name = "WorkspaceFileTransportError";
  }
}

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
      this.socket.onerror = (event) => {
        const error = new WorkspaceFileTransportError("工作空间文件 WebSocket 连接失败", event);
        this.open = false;
        reject(error);
        this.rejectAll(error);
        this.onClose();
      };
      this.socket.onclose = () => {
        const error = new WorkspaceFileTransportError("工作空间文件 WebSocket 已关闭");
        this.open = false;
        // open 前关闭也必须结算 ready()，否则所有复用此 single-flight 的调用都会永久等待。
        reject(error);
        this.rejectAll(error);
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
      return Promise.reject(new WorkspaceFileTransportError("工作空间文件 WebSocket 尚未连接"));
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
      try {
        this.socket.send(JSON.stringify({ id, op, params }));
      } catch (cause) {
        const detail = cause instanceof Error ? `: ${cause.message}` : "";
        const error = new WorkspaceFileTransportError(`工作空间文件 WebSocket 发送失败${detail}`, cause);
        // send() 可能同步抛错；必须先移除当前 pending 与定时器，避免随后再次拒绝或泄漏。
        this.pending.delete(id);
        clearTimeout(timeoutId);
        this.open = false;
        reject(error);
        this.rejectAll(error);
        this.onClose();
        try {
          // 先完成原错误与缓存清理再关闭；同步 onclose 可幂等执行，close 异常不得覆盖 send 错误。
          this.socket.close();
        } catch {
          // 连接已从缓存移除且所有 pending 已拒绝，无需用关闭异常替换原始发送失败。
        }
      }
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

async function runtimeList(path: string, request: RequestFn, init?: ExtraRequestInit) {
  return listFromRuntimeEnvelope(await request<unknown>(path, init));
}

function postRuntime(path: string, payload: Record<string, unknown> | undefined, request: RequestFn, init?: ExtraRequestInit) {
  return request<unknown>(path, {
    method: "POST",
    body: payload == null ? undefined : JSON.stringify(payload),
    ...init
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
    // permission 列表同样已经由平台 session 路由；远端 sessionID 只用于 OpenCode 内部，
    // 不能让历史会话的 dock 按它筛选，否则待授权项会消失。
    sessionId: fallbackSessionId,
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
    // /sessions/{platformSessionId}/questions 已按平台会话路由；原生 payload 的 sessionID 是
    // OpenCode 远端会话 ID，不能用于前端历史会话筛选，否则真实待答问题会被错误丢弃。
    sessionId: fallbackSessionId,
    questions: questions
      .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
      .map((item, index) => {
        const options = Array.isArray(item.options)
          ? item.options
              .filter((option): option is Record<string, unknown> => typeof option === "object" && option !== null)
              .map((option) => ({
                id: text(option.id) ?? text(option.value) ?? text(option.label) ?? "option",
                label: text(option.label) ?? text(option.value) ?? text(option.id) ?? "option",
                description: text(option.description)
              }))
          : undefined;
        // /question 原生对象不提供 kind，必须由 multiple/options 恢复可交互的题型。
        const kind = typeof item.multiple === "boolean"
          ? item.multiple ? "multiple" : options?.length ? "single" : "text"
          : text(item.kind) ?? text(item.type) ?? (options?.length ? "single" : "text");
        return {
          questionId: text(item.questionId) ?? text(item.questionID) ?? text(item.id) ?? `${requestId}:${index}`,
          header: text(item.header),
          text: text(item.text) ?? text(item.prompt) ?? text(item.question) ?? "",
          kind,
          options,
          custom: typeof item.custom === "boolean" ? item.custom : undefined,
          required: typeof item.required === "boolean" ? item.required : undefined
        };
      }),
    createdAt: text(value.createdAt) ?? text(record(value.time)?.created) ?? new Date(0).toISOString()
  };
}

function isSuccessResponse<T>(body: unknown): body is ApiResponse<T> & { success: true } {
  return typeof body === "object" && body !== null && (body as { success?: unknown }).success === true;
}

function readJsonFromText(response: Response, text: string): unknown {
  if (!text) {
    return { success: true, data: undefined, traceId: response.headers.get("X-Trace-Id") ?? "trace_unknown" };
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return { success: false, code: "BAD_RESPONSE", message: text, traceId: response.headers.get("X-Trace-Id") ?? "trace_unknown" };
  }
}

async function readJson(response: Response): Promise<unknown> {
  return readJsonFromText(response, await response.text());
}

function safeRequestHeaders(headers: Headers): Record<string, string> {
  const allowList = new Set(["accept", "content-type", "x-trace-id"]);
  const result: Record<string, string> = {};
  headers.forEach((value, key) => {
    const normalized = key.toLowerCase();
    if (allowList.has(normalized)) {
      result[normalized] = value;
    }
  });
  return result;
}

function responseHeadersToRecord(headers: Headers): Record<string, string> {
  const result: Record<string, string> = {};
  headers.forEach((value, key) => {
    result[key.toLowerCase()] = value;
  });
  return result;
}

function bodyToRawText(body: BodyInit | null | undefined): string | undefined {
  if (typeof body === "string") {
    return body;
  }
  if (body instanceof URLSearchParams) {
    return body.toString();
  }
  return undefined;
}

/**
 * 原始报文观察器面向页面调试，不能暴露服务端签发的会话上下文 token。
 * 这里只改观察副本，实际 fetch body 仍保持原值发送给后端。
 */
function bodyToObservedRawText(body: BodyInit | null | undefined): string | undefined {
  const raw = bodyToRawText(body);
  if (!raw || typeof body !== "string") {
    return raw;
  }
  return redactObservedJsonText(raw);
}

/** 优先递归脱敏 JSON；解析失败时继续按字段名处理 SSE/截断文本，调试副本禁止泄露 token。 */
function redactObservedJsonText(raw: string): string {
  try {
    return JSON.stringify(redactConversationContextToken(JSON.parse(raw)));
  } catch {
    return redactConversationContextTokenText(raw);
  }
}

function redactConversationContextTokenText(raw: string): string {
  const keyPattern = /(["']?)\bcontexttoken\b\1\s*[:=]\s*/gi;
  let redacted = "";
  let cursor = 0;
  let match: RegExpExecArray | null;
  while ((match = keyPattern.exec(raw)) !== null) {
    redacted += raw.slice(cursor, match.index) + match[0];
    const valueStart = keyPattern.lastIndex;
    const quote = raw[valueStart];
    let valueEnd = valueStart;
    let closedQuote = false;
    if (quote === '"' || quote === "'") {
      valueEnd += 1;
      let escaped = false;
      while (valueEnd < raw.length) {
        const current = raw[valueEnd];
        if (current === "\n" || current === "\r") break;
        if (escaped) {
          escaped = false;
        } else if (current === "\\") {
          escaped = true;
        } else if (current === quote) {
          closedQuote = true;
          valueEnd += 1;
          break;
        }
        valueEnd += 1;
      }
      redacted += `${quote}[REDACTED]${closedQuote ? quote : ""}`;
    } else {
      while (valueEnd < raw.length && !/[\s,;&}\]]/.test(raw[valueEnd])) valueEnd += 1;
      redacted += "[REDACTED]";
    }
    cursor = valueEnd;
    keyPattern.lastIndex = valueEnd;
  }
  return redacted + raw.slice(cursor);
}

function redactConversationContextToken(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(redactConversationContextToken);
  }
  if (!value || typeof value !== "object") {
    return value;
  }
  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>).map(([key, item]) => [
      key,
      key.toLowerCase() === "contexttoken" ? "[REDACTED]" : redactConversationContextToken(item)
    ])
  );
}

function pathFromUrl(url: string): string {
  try {
    const parsed = new URL(url);
    return `${parsed.pathname}${parsed.search}`;
  } catch {
    return url;
  }
}

function rawTiming(startedAtMs: number): Pick<RawHttpExchange, "endedAt" | "durationMs"> {
  const endedAtMs = Date.now();
  return {
    endedAt: new Date(endedAtMs).toISOString(),
    durationMs: Math.max(0, endedAtMs - startedAtMs)
  };
}

function notifyRawExchange(observer: BackendApiClientOptions["rawExchangeObserver"], exchange: RawHttpExchange) {
  try {
    observer?.(exchange);
  } catch {
    // 调试观察器不应影响业务请求。
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

function query(values: Record<string, string | number | boolean | null | undefined>) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
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
