import type {
  AgentInfo,
  AgentConfigCommitPayload,
  AgentConfigDiff,
  AgentConfigFileRoute,
  AgentConfigOperation,
  AgentConfigOperationTicketResponse,
  AgentConfigProgressEvent,
  AgentConfigStatus,
  AppSourceMaterializationPayload,
  AppSourceOpenResult,
  AppSourceOperation,
  AppSourceOperationTicketResponse,
  AppSourceProgressEvent,
  AppSourceRemoteTreeNode,
  AppSourceTreeSnapshot,
  AppSourceReplicaRetryPayload,
  AppSourceRepositorySummary,
  AppSourceRetentionUpdatePayload,
  AppSourceRetentionUpdateResult,
  AgentSkillHubAsset,
  AgentSkillHubAssetDetail,
  AgentSkillHubAssetType,
  AgentSkillHubSourceKind,
  AgentSkillHubClassification,
  AgentSkillHubFileContent,
  AgentSkillHubReference,
  AgentSkillHubUpdate,
  AgentSkillHubUpdateOperation,
  AgentSkillHubSkillCategory,
  AgentSkillHubSkillSubcategory,
  AgentConfigWorktree,
  AgentConfigWorktreeOption,
  AgentConfigWorktreePayload,
  AiMessageFeedback,
  AiMessageFeedbackPayload,
  AiRunFeedback,
  AiRunFeedbackPayload,
  RunFeedbackQuery,
  RunFeedbackState,
  RepositoryTreeNode,
  AddSshKeyPayload,
  AnalyticsExceptionDetail,
  AnalyticsCapabilities,
  AnalyticsFilterOptions,
  AnalyticsFunnel,
  AnalyticsHeatmapMetric,
  AnalyticsHourlyHeatmap,
  AnalyticsOrganizationUsageRow,
  AnalyticsOverview,
  AnalyticsPeaks,
  AnalyticsQueryParams,
  AnalyticsSatisfaction,
  AnalyticsTimeSeriesPoint,
  AnalyticsTokenOperations,
  AnalyticsUserUsageRow,
  TraceCatalog,
  TraceQueryParams,
  TraceRawEventPage,
  TraceSpanPage,
  ApplicationWorkspaceTemplate,
  BatchContext,
  ApplicationWorkspaceVersion,
  ApplicationDefinition,
  ApplicationGitRefreshScope,
  ApplicationGitRefreshGroupSelector,
  ApplicationGitRefreshResult,
  CreateApplicationPayload,
  ApplicationMember,
  ApplicationWorkspaceConfig,
  ApiFailure,
  ApiResponse,
  CodeRepositoryConfig,
  CommandInfo,
  CommonParameterChangeLog,
  CommonParameterMemoryCluster,
  CommonParameterMemoryProcess,
  ConversationRunContext,
  CreateRunResendPayload,
  CreateApplicationWorkspacePayload,
  CreateWorkspaceAcceptedResponse,
  CreatePersonalWorkspacePayload,
  CreateRepositoryPayload,
  CreateWorkspaceVersionPayload,
  CreateUserPayload,
  CurrentUser,
  DefaultPersonalWorkspaceResponse,
  FileContent,
  FilePreviewChunk,
  FilePreviewChunkRequest,
  FileBinaryChunk,
  FileBinaryChunkRequest,
  FileSearchResult,
  FileStatus,
  FileTreeEntry,
  ExternalApiCredential,
  ExternalApiCredentialCreatePayload,
  ExternalApiCredentialCreated,
  ExternalApiCredentialListParams,
  ExternalApiCredentialRevealed,
  ExternalApiCredentialUpdatePayload,
  ExternalApiScopeOption,
  GeneralParameter,
  GeneralParameterListParams,
  GeneralParameterUpdatePayload,
  GitRepositoryAccess,
  InternalModelCallOutcome,
  InternalModelCallOutcomeGroup,
  InternalModelCallSource,
  InternalModelCallRecord,
  InternalModelCallHourlyStat,
  InternalModelLatencyDistribution,
  InternalModelTtftDistribution,
  InternalModelThroughputDistribution,
  InternalModelProbeStatus,
  InternalModelProbeRunResult,
  InternalModelProviderManagementResponse,
  InternalModelProviderModel,
  InternalModelProviderModelUpdatePayload,
  InternalModelCapability,
  InternalModelCapabilityProbeResult,
  InternalModelProviderRefreshStatus,
  InternalModelProviderUpdatePayload,
  InternalModelTokenCreatePayload,
  InternalModelTokenDefinition,
  InternalModelTokenDeleteResponse,
  UpdateApplicationWorkspacePayload,
  InternalModelTokenUpdatePayload,
  LoginRequest,
  LoginResponse,
  LobehubSsoTicket,
  LocalClientCommandResult,
  LocalClientCredential,
  LocalClientDirectoryEntry,
  LocalClientDownloadAccess,
  LocalClientGlobalPolicy,
  LocalClientInstance,
  LocalClientPublicCapabilityUpdateRequest,
  LocalClientPlaintextKey,
  LocalClientRelease,
  LocalClientReleaseSyncResult,
  LocalClientRollout,
  LocalClientRolloutRequest,
  LocalClientRolloutUser,
  LocalClientUpdateAttempt,
  LocalClientUserPolicy,
  LocalClientUserUpdateRequest,
  LocalWorkspace,
  ManagedApplication,
  ManagedWorkspaceRuntime,
  MemoryAdminHealth,
  MemoryEvidenceView,
  MemoryScope,
  MemorySettingsView,
  MemorySkillProposalView,
  MemoryStatus,
  MemoryUsageView,
  MemoryView,
  MemoryWhitelistView,
  ModelInfo,
  NightExecutionScheduleMode,
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
  OpencodeEndpoint,
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
  PublicAgentConfigRolloutStatus,
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
  RunResendResponse,
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
  SessionCollaborationShare,
  SessionMessage,
  SessionShareAccess,
  SessionShareCandidate,
  SharedSessionListItem,
  PutSessionCollaborationSharePayload,
  UserNotificationPage,
  SideQuestionRequest,
  SideQuestionResponse,
  SideQuestionRunRequest,
  SideQuestionRunResponse,
  ManualQuestionRunRequest,
  SessionRuntimeStateSummary,
  SessionTreeMessagesResponse,
  SupportAccessAuditEvent,
  SupportAccessAuditQuery,
  SupportAccessGrant,
  SupportAccessGrantRequest,
  SupportAccessIncidentSuggestion,
  SupportAccessTarget,
  SshKeyMetadata,
  SshKeyPublicKeyResponse,
  SyncWorkspacePayload,
  TerminalTicketRequest,
  ServerTerminalTicketRequest,
  TerminalTicketResponse,
  ToolboxCatalog,
  ToolboxClickResult,
  TcdsTestCaseMaintenancePayload,
  TcdsTaskTypeOption,
  TodoItem,
  DeleteUsersResult,
  SyncUsersFromTcdsResult,
  UpdateUsernamePayload,
  UpdateUserRolePayload,
  UpdateUserRolesPayload,
  UpdateUserRolesResult,
  UserIdsPayload,
  UserManagementQuery,
  UpdateRepositoryPayload,
  UserManagementUser,
  UserOpencodeProcess,
  Workspace,
  WorkspaceBackendServer,
  WorkspaceCreateOperation,
  WorkspaceDiff,
  WorkspaceGitCommitResult,
  WorkspaceGitDiff,
  WorkspaceGitMergeCompletion,
  WorkspaceGitConflict,
  PersonalWorkspaceGitPullResult,
  PublishPersonalWorkspacePreview,
  WorkspaceSyncResult,
  WorkspaceBranchPreference,
  WorkspaceDirectoryList,
  RequirementImportApplication,
  RequirementImportItem,
  RequirementImportCommand,
  RequirementImportResult,
  WorkspaceFileRoute,
  WorkspaceFileSocketTicketRequest,
  WorkspaceFileSocketTicketResponse,
  XxlJobSsoTicket
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
export type AppSourceProgressHandler = (event: AppSourceProgressEvent) => void;
export type AppSourceProgressConnection = {
  close: () => void;
};
export type FileUploadProgress = {
  uploadedBytes: number;
  totalBytes: number;
};
export type FileUploadProgressHandler = (progress: FileUploadProgress) => void;

const WEBSOCKET_OPEN_STATE = 1;
const AGENT_CONFIG_PROGRESS_OPEN_TIMEOUT_MS = 3000;
const APP_SOURCE_PROGRESS_OPEN_TIMEOUT_MS = 3000;

export type BackendApiClientOptions = {
  baseUrl?: string;
  agentId?: string;
  apiToken?: string;
  /**
   * 返回当前页面内存中的用户绑定服务器；未绑定时不产生 Nginx 路由提示头。
   */
  routeLinuxServerId?: () => string | null | undefined;
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
export type SessionShareApiClient = ReturnType<typeof createSessionShareApiClient>;

export const LINUX_SERVER_ROUTE_HEADER = "X-Test-Agent-Linux-Server-Id";
export const SUPPORT_ACCESS_GRANT_HEADER = "X-Support-Access-Grant";
export const SESSION_SHARE_HEADER = "X-Test-Agent-Session-Share";

// 应用源码分支读取最多执行一次 60 秒 Git 命令，目录快照还会串行解析提交并读取远端树；
// 这里仅放宽这两类慢 Git 读取，避免全局 30 秒超时先于后端的权威 Git 结果返回。
const APP_SOURCE_BRANCH_REQUEST_TIMEOUT_MS = 70_000;
const APP_SOURCE_TREE_REQUEST_TIMEOUT_MS = 130_000;
// OpenCode summarize 会同步等待模型生成摘要，需与企业 Nginx 的一小时长请求窗口保持一致。
const NATIVE_SESSION_COMPACT_TIMEOUT_MS = 3_600_000;

// 统一读取环境变量：Vite 运行时（import.meta.env）优先，Node 运行时（process.env）兜底
function readEnv(key: string): string | undefined {
  const viteEnv = (import.meta as unknown as { env?: Record<string, string | undefined> }).env;
  // 显式空字符串表示同源部署，必须区别于变量未定义，避免回退到本机开发地址。
  if (viteEnv && Object.prototype.hasOwnProperty.call(viteEnv, key)) {
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
  scheduleMode?: NightExecutionScheduleMode;
  slotStart: string;
  batchContext?: BatchContext;
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

export type AutomationReferenceConfiguration = {
  generation: number;
  branch: string;
  directoryPath: string;
  description: string;
  merge: false;
  targetCommitHash: string;
  alias: string;
  logicalPath: string;
  directoryName: string;
  activatedAt?: string | null;
  status: string;
};

/** `(appId, repositoryId)` 唯一自动化引用及当前共享副本状态。 */
export type AutomationReferenceRepositoryStatus = {
  appId: string;
  repositoryId: string;
  name: string;
  englishName: string;
  gitUrl: string;
  status: string;
  operation: "CONFIGURE" | "SYNCHRONIZE" | "VERIFY_POINTERS" | string;
  lockVersion: number;
  activeGeneration?: number | null;
  pendingGeneration?: number | null;
  currentConfiguration?: AutomationReferenceConfiguration | null;
  pendingConfiguration?: AutomationReferenceConfiguration | null;
  targetServerCount: number;
  readyServerCount: number;
  servers: ReferenceRepositoryServerStatus[];
  traceId?: string | null;
  message?: string | null;
};

/** 后端单一 JSONC 对账器的工作树结果；警告只描述本次未就绪的局部引用。 */
export type AutomationReferenceWorkspaceReconciliation = {
  changed: boolean;
  warnings: string[];
};

export type ExtraRequestInit = RequestInit & { timeoutMs?: number };

type RequestFn = <T>(path: string, init?: ExtraRequestInit) => Promise<T>;

type BackendApiClientInternalOptions = BackendApiClientOptions & {
  /** 仅由 createSessionShareApiClient 设置，避免普通客户端误带分享凭据。 */
  sessionShareId?: string;
};

export type SessionShareApiClientOptions = BackendApiClientOptions & { shareId: string };

export function createBackendApiClient(options: BackendApiClientOptions = {}) {
  return createBackendApiClientInternal(options);
}

/**
 * 创建分享工作台专用客户端。shareId 只进入请求头，并由原始报文观察器自动排除。
 */
export function createSessionShareApiClient(options: SessionShareApiClientOptions) {
  const shareId = options.shareId.trim();
  if (!shareId) {
    throw new Error("shareId is required");
  }
  const { shareId: _, ...baseOptions } = options;
  return createBackendApiClientInternal({ ...baseOptions, sessionShareId: shareId });
}

function createBackendApiClientInternal(options: BackendApiClientInternalOptions = {}) {
  const baseUrl = (options.baseUrl ?? readEnv("VITE_TEST_AGENT_API_BASE_URL") ?? "http://127.0.0.1:8080").replace(
    /\/$/,
    ""
  );
  const agentId = normalizeAgentId(options.agentId ?? readEnv("VITE_TEST_AGENT_AGENT_ID") ?? "opencode");
  const agentBase = `/api/internal/agent/${encodeURIComponent(agentId)}`;
  const configurationBase = "/api/internal/platform/configuration-management";
  const workspaceManagementBase = "/api/internal/platform/workspace-management";
  const localClientBase = "/api/internal/platform/local-opencode-client";
  const requirementImportBase = "/api/v1/requirement-import";
  const localClientVersionManagementBase = `${localClientBase}/version-management`;
  const agentConfigBase = `${workspaceManagementBase}/agent-config`;
  const agentSkillHubBase = `${workspaceManagementBase}/agent-skill-hub`;
  const opencodeRuntimeBase = "/api/internal/platform/opencode-runtime";
  const internalModelObservabilityBase = `${opencodeRuntimeBase}/internal-model-observability`;
  const opencodeRuntimeManagementBase = "/api/internal/platform/opencode-runtime/management";
  const schedulerManagementBase = "/api/internal/platform/scheduler-management";
  const xxlJobBase = "/api/internal/platform/xxl-job";
  const lobehubSsoBase = "/api/internal/platform/lobehub-sso";
  const systemManagementBase = "/api/internal/platform/system-management";
  const externalApiCredentialBase = `${systemManagementBase}/api-keys`;
  const toolboxBase = "/api/internal/platform/toolbox";
  const tcdsIntegrationBase = "/api/internal/platform/integration/tcds";
  const memoryBase = "/api/internal/platform/memory/v1";
  const memoryAdminBase = `${memoryBase}/admin`;
  const analyticsBase = "/api/internal/platform/analytics";
  const traceBase = "/api/internal/platform/traces";
  const notificationCenterBase = "/api/internal/platform/notification-center/notifications";
  const commonParameterBase = `${configurationBase}/common-parameters`;
  const referenceRepositoryBase = (appId: string) =>
    `${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/reference-repositories`;
  const automationReferenceRepositoryBase = (appId: string) =>
    `${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/automation-reference-repositories`;
  const appSourceRepositoryBase = (appId: string) =>
    `${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/app-source-repositories`;
  const appSourceOperationBase = `${workspaceManagementBase}/app-source-operations`;
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
    if (options.sessionShareId && !headers.has(SESSION_SHARE_HEADER)) {
      headers.set(SESSION_SHARE_HEADER, options.sessionShareId);
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
        if (response.status === 401
          && !headers.has(SUPPORT_ACCESS_GRANT_HEADER)
          && typeof window !== "undefined") {
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

  /**
   * 仅为需要用户绑定 Java 的请求增加 Nginx 首跳提示。该值不持久化，也不替代后端权威路由校验。
   */
  async function routedRequest<T>(path: string, init: ExtraRequestInit = {}): Promise<T> {
    const linuxServerId = options.routeLinuxServerId?.()?.trim();
    if (!linuxServerId) {
      return request<T>(path, init);
    }
    const headers = new Headers(init.headers);
    headers.set(LINUX_SERVER_ROUTE_HEADER, linuxServerId);
    return requestFrom<T>(baseUrl, path, { ...init, headers });
  }

  async function requestBlob(path: string, init: RequestInit = {}, accept = "application/octet-stream"): Promise<Blob> {
    const traceId = traceIdFactory();
    const headers = new Headers(init.headers);
    headers.set("Accept", accept);
    headers.set("X-Trace-Id", traceId);
    const userToken = options.apiToken ?? (typeof sessionStorage !== "undefined" ? sessionStorage.getItem("test-agent.auth.token") : null);
    if (userToken && !headers.has("Authorization")) {
      headers.set("Authorization", `Bearer ${userToken}`);
    }
    if (options.sessionShareId && !headers.has(SESSION_SHARE_HEADER)) {
      headers.set(SESSION_SHARE_HEADER, options.sessionShareId);
    }
    const response = await fetcher(`${baseUrl}${path}`, { ...init, headers });
    if (!response.ok) {
      const body = await readJson(response);
      throw new BackendApiError(response.status, normalizeFailure(body, traceId, response.status));
    }
    return response.blob();
  }

  async function requestCsv(path: string, init: RequestInit = {}): Promise<Blob> {
    return requestBlob(path, init, "text/csv");
  }

  const agentPath = (path: string) => `${agentBase}${path}`;
  const workspaceFileSockets = new Map<string, WorkspaceFileSocketClient>();
  const workspaceFileConnections = new Map<string, Promise<WorkspaceFileSocketClient>>();
  const supportFileSockets = new Map<string, WorkspaceFileSocketClient>();
  const supportFileConnections = new Map<string, Promise<WorkspaceFileSocketClient>>();
  const agentConfigFileSockets = new Map<string, WorkspaceFileSocketClient>();
  const agentConfigFileConnections = new Map<string, Promise<WorkspaceFileSocketClient>>();
  let agentSkillHubFileSocket: WorkspaceFileSocketClient | null = null;
  let agentSkillHubFileConnection: Promise<WorkspaceFileSocketClient> | null = null;
  const runtimeProviderAllowlistRequests = new Map<string, Promise<Set<string> | undefined>>();

  /**
   * 模型和 Provider 目录并发加载时复用同一轮 config 请求；请求结束即清理，配置热加载后可及时生效。
   * config 暂时不可用时保持原生目录兼容，不能让辅助过滤阻断整个模型选择器。
   */
  function runtimeProviderAllowlist(workspaceId?: string): Promise<Set<string> | undefined> {
    const routeLinuxServerId = options.routeLinuxServerId?.()?.trim() ?? "";
    const key = `${routeLinuxServerId}\u0000${workspaceId ?? ""}`;
    const existing = runtimeProviderAllowlistRequests.get(key);
    if (existing) return existing;

    const pending = routedRequest<unknown>(`${opencodeRuntimeBase}/config${query({ workspaceId })}`).then(
      providerAllowlistFromConfig,
      () => undefined
    );
    runtimeProviderAllowlistRequests.set(key, pending);
    void pending.then(() => {
      if (runtimeProviderAllowlistRequests.get(key) === pending) {
        runtimeProviderAllowlistRequests.delete(key);
      }
    });
    return pending;
  }

  async function runtimeCatalogList(path: string, workspaceId?: string): Promise<Record<string, unknown>[]> {
    const [value, allowlist] = await Promise.all([
      routedRequest<unknown>(path),
      runtimeProviderAllowlist(workspaceId)
    ]);
    // OpenCode V2 的 Provider 目录包在 `{ all: [...] }` 中；统一解包后只应用平台白名单，保留原生顺序。
    const payload = record(value)?.data ?? value;
    const all = record(payload)?.all;
    const items = listFromRuntimeEnvelope(Array.isArray(all) ? all : payload);
    if (!allowlist) return items;
    return items.filter((item) => {
      const providerId = runtimeCatalogProviderId(item);
      return providerId !== undefined && allowlist.has(providerId);
    });
  }

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

  function supportHeaders(grantToken: string): Headers {
    const headers = new Headers();
    headers.set(SUPPORT_ACCESS_GRANT_HEADER, grantToken);
    return headers;
  }

  function supportSocketKey(grantId: string, targetUserId: string, workspaceId: string): string {
    return [grantId, targetUserId, workspaceId].map(encodeURIComponent).join(":");
  }

  function closeSupportFileSockets(grantId?: string) {
    const encodedGrant = grantId ? `${encodeURIComponent(grantId)}:` : undefined;
    for (const [key, client] of supportFileSockets) {
      if (!encodedGrant || key.startsWith(encodedGrant)) {
        client.close();
        supportFileSockets.delete(key);
      }
    }
    // CONNECTING socket 会在 ready 失败后自清理；先删除 single-flight，禁止后续调用复用旧授权。
    for (const key of supportFileConnections.keys()) {
      if (!encodedGrant || key.startsWith(encodedGrant)) supportFileConnections.delete(key);
    }
  }

  async function ensureSupportWorkspaceFileClient(
    grant: Pick<SupportAccessGrant, "grantId" | "grantToken">,
    targetUserId: string,
    workspaceId: string
  ): Promise<WorkspaceFileSocketClient> {
    const key = supportSocketKey(grant.grantId, targetUserId, workspaceId);
    const existing = supportFileSockets.get(key);
    if (existing?.open) return existing;
    const connecting = supportFileConnections.get(key);
    if (connecting) return connecting;
    existing?.close();
    const basePath = `${systemManagementBase}/support-access/targets/${encodeURIComponent(targetUserId)}`
      + `/workspaces/${encodeURIComponent(workspaceId)}`;
    const connection = (async () => {
      // 排查控制面请求不携带动态用户路由头，目标后端完全由权威 workspace 归属决定。
      const route = await requestFrom<WorkspaceFileRoute>(baseUrl, `${basePath}/file-ws-route`, {
        method: "POST",
        headers: supportHeaders(grant.grantToken)
      });
      const ticket = await requestFrom<WorkspaceFileSocketTicketResponse>(
        route.baseUrl.replace(/\/$/, ""),
        `${basePath}/file-ws-tickets`,
        {
          method: "POST",
          headers: supportHeaders(grant.grantToken),
          body: JSON.stringify({ linuxServerId: route.linuxServerId })
        }
      );
      let client!: WorkspaceFileSocketClient;
      client = new WorkspaceFileSocketClient(
        toWebSocketUrl(route.baseUrl, ticket.webSocketUrl),
        webSocketFactory,
        () => {
          if (supportFileSockets.get(key) === client) supportFileSockets.delete(key);
        }
      );
      supportFileSockets.set(key, client);
      await client.ready();
      return client;
    })();
    supportFileConnections.set(key, connection);
    try {
      return await connection;
    } finally {
      if (supportFileConnections.get(key) === connection) supportFileConnections.delete(key);
    }
  }

  async function supportWorkspaceFileRpc<T>(
    grant: Pick<SupportAccessGrant, "grantId" | "grantToken">,
    targetUserId: string,
    workspaceId: string,
    op: "workspace.list" | "workspace.search" | "workspace.read" | "workspace.read.chunk" | "workspace.read.binary.chunk",
    params: Record<string, unknown>,
    retryTransportOnce = false
  ): Promise<T> {
    for (let attempt = 0; ; attempt += 1) {
      try {
        const client = await ensureSupportWorkspaceFileClient(grant, targetUserId, workspaceId);
        return await client.request<T>(op, { workspaceId, ...params });
      } catch (error) {
        if (!retryTransportOnce || attempt > 0 || !(error instanceof WorkspaceFileTransportError)) throw error;
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
      const route = await routedRequest<WorkspaceFileRoute>(
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
            linuxServerId: route.linuxServerId ?? undefined,
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

  /** 分享失效或权限变化时主动关闭已有文件连接，后续操作必须重新签发并鉴权。 */
  function closeWorkspaceFileConnections(workspaceId?: string) {
    for (const [key, client] of workspaceFileSockets) {
      if (!workspaceId || key === workspaceId) {
        client.close();
        workspaceFileSockets.delete(key);
        workspaceFileConnections.delete(key);
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

  /** 本地目录选择器先解析精确连接持有 Java，再在该 Java 上签发 generation 绑定 ticket。 */
  async function createLocalDirectoryPickerClient(clientInstanceId: string): Promise<WorkspaceFileSocketClient> {
    const normalizedClientInstanceId = clientInstanceId.trim();
    if (!normalizedClientInstanceId) throw new Error("clientInstanceId is required");
    const route = await request<WorkspaceFileRoute>(
      `${workspaceManagementBase}/local-clients/${encodeURIComponent(normalizedClientInstanceId)}/directory-picker/file-ws-route`,
      { method: "POST" }
    );
    if (route.runtimeKind !== "LOCAL_CLIENT"
      || route.localClientInstanceId !== normalizedClientInstanceId
      || !route.connectionGeneration) {
      throw new BackendApiError(409, {
        success: false,
        code: "LOCAL_CLIENT_ROUTE_CHANGED",
        message: "本地客户端连接路由已变化，请重试",
        traceId: "",
        retryable: true,
        details: {}
      });
    }
    const ticket = await requestFrom<WorkspaceFileSocketTicketResponse>(
      route.baseUrl.replace(/\/$/, ""),
      `${workspaceManagementBase}/file-ws/tickets`,
      {
        method: "POST",
        body: JSON.stringify({
          mode: "directory-picker",
          localClientInstanceId: normalizedClientInstanceId,
          connectionGeneration: route.connectionGeneration
        } satisfies WorkspaceFileSocketTicketRequest)
      }
    );
    const client = new WorkspaceFileSocketClient(
      toWebSocketUrl(route.baseUrl, ticket.webSocketUrl),
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

  async function ensureAgentSkillHubFileClient(): Promise<WorkspaceFileSocketClient> {
    if (agentSkillHubFileSocket?.open) return agentSkillHubFileSocket;
    if (agentSkillHubFileConnection) return agentSkillHubFileConnection;
    agentSkillHubFileSocket?.close();
    const connection = (async () => {
      const ticket = await request<WorkspaceFileSocketTicketResponse>(
        `${workspaceManagementBase}/file-ws/tickets`,
        { method: "POST", body: JSON.stringify({ mode: "agent-skill-hub", scope: "HUB" }) }
      );
      let client!: WorkspaceFileSocketClient;
      client = new WorkspaceFileSocketClient(
        toWebSocketUrl(baseUrl, ticket.webSocketUrl),
        webSocketFactory,
        () => {
          if (agentSkillHubFileSocket === client) agentSkillHubFileSocket = null;
        }
      );
      agentSkillHubFileSocket = client;
      await client.ready();
      return client;
    })();
    agentSkillHubFileConnection = connection;
    try {
      return await connection;
    } finally {
      if (agentSkillHubFileConnection === connection) agentSkillHubFileConnection = null;
    }
  }

  async function hubReadRpc<T>(op: string, params: Record<string, unknown>): Promise<T> {
    for (let attempt = 0; ; attempt += 1) {
      try {
        return await (await ensureAgentSkillHubFileClient()).request<T>(op, params);
      } catch (error) {
        if (attempt > 0 || !(error instanceof WorkspaceFileTransportError)) throw error;
      }
    }
  }

  return {
    listAgentSkillHubAssets: (params: {
      type?: AgentSkillHubAssetType;
      category?: AgentSkillHubSkillCategory;
      subcategory?: AgentSkillHubSkillSubcategory;
      source?: AgentSkillHubSourceKind | "ALL";
      keyword?: string;
      referencedOnly?: boolean;
      targetWorkspaceId?: string;
      page?: number;
      size?: number;
    } = {}) => request<PageResponse<AgentSkillHubAsset>>(`${agentSkillHubBase}/assets${query(params)}`),
    getAgentSkillHubAsset: (assetId: string, revisionId?: string, targetWorkspaceId?: string) =>
      request<AgentSkillHubAssetDetail>(
        `${agentSkillHubBase}/assets/${encodeURIComponent(assetId)}${query({ revisionId, targetWorkspaceId })}`
      ),
    materializeAgentSkillHubAsset: (assetId: string, targetWorkspaceId?: string) =>
      request<AgentSkillHubAssetDetail>(
        `${agentSkillHubBase}/assets/${encodeURIComponent(assetId)}/materialize${query({ targetWorkspaceId })}`,
        { method: "POST" }
      ),
    readAgentSkillHubFile: (revisionId: string, path: string) =>
      hubReadRpc<AgentSkillHubFileContent>("hub.asset.read", { revisionId, path }),
    publishAgentSkillHubAsset: (assetId: string, dependencyAssetIds: string[] = []) =>
      request<{ assetId: string; revisionId: string; publishedAt: string; dependencyCount: number }>(
        `${agentSkillHubBase}/assets/${encodeURIComponent(assetId)}/publish`,
        { method: "POST", body: JSON.stringify({ dependencyAssetIds }) }
      ),
    updateAgentSkillHubClassification: (
      assetId: string,
      category: AgentSkillHubSkillCategory,
      subcategory?: AgentSkillHubSkillSubcategory | null
    ) => request<AgentSkillHubClassification>(
      `${agentSkillHubBase}/assets/${encodeURIComponent(assetId)}/classification`,
      { method: "PUT", body: JSON.stringify({ category, subcategory: subcategory ?? null }) }
    ),
    getAgentSkillHubUpdateCount: (targetWorkspaceId?: string) =>
      request<{ count: number }>(`${agentSkillHubBase}/updates/count${query({ targetWorkspaceId })}`),
    listAgentSkillHubUpdates: (page = 1, size = 30, targetWorkspaceId?: string) =>
      request<PageResponse<AgentSkillHubUpdate>>(
        `${agentSkillHubBase}/references/updates${query({ page, size, targetWorkspaceId })}`
      ),
    createAgentSkillHubReference: (
      workspaceId: string,
      assetId: string,
      aliasTechnicalId?: string
    ) => agentConfigFileRpc<AgentSkillHubReference>(
      "WORKSPACE",
      "hub.reference.create",
      { assetId, aliasTechnicalId },
      { workspaceId }
    ),
    removeAgentSkillHubReference: (workspaceId: string, assetId: string) =>
      agentConfigFileRpc<AgentSkillHubReference>(
        "WORKSPACE", "hub.reference.remove", { assetId }, { workspaceId }
      ),
    startAgentSkillHubReferenceUpdate: (workspaceId: string, referenceId: string) =>
      agentConfigFileRpc<AgentSkillHubUpdateOperation>(
        "WORKSPACE", "hub.reference.update.start", { referenceId }, { workspaceId }
      ),
    getAgentSkillHubUpdateOperation: (workspaceId: string, operationId: string) =>
      agentConfigFileRpc<AgentSkillHubUpdateOperation>(
        "WORKSPACE", "hub.reference.update.read-conflict", { operationId }, { workspaceId }, true
      ),
    resolveAgentSkillHubUpdateConflict: (
      workspaceId: string,
      operationId: string,
      payload: { path: string; resolution: string; content?: string | null }
    ) => agentConfigFileRpc<AgentSkillHubUpdateOperation>(
      "WORKSPACE", "hub.reference.update.resolve", { operationId, ...payload }, { workspaceId }
    ),
    completeAgentSkillHubUpdate: (workspaceId: string, operationId: string) =>
      agentConfigFileRpc<AgentSkillHubReference>(
        "WORKSPACE", "hub.reference.update.complete", { operationId }, { workspaceId }
      ),
    abortAgentSkillHubUpdate: (workspaceId: string, operationId: string) =>
      agentConfigFileRpc<void>(
        "WORKSPACE", "hub.reference.update.abort", { operationId }, { workspaceId }
      ),
    listWorkspaces: (page = 1, size = 20) =>
      request<PageResponse<Workspace>>(`${workspaceManagementBase}/workspaces?page=${page}&size=${size}`),
    getMyLocalClientCredential: () =>
      request<LocalClientCredential>(`${localClientBase}/credentials/me`),
    createMyLocalClientCredential: () =>
      request<LocalClientCredential>(`${localClientBase}/credentials/me`, { method: "POST" }),
    copyMyLocalClientCredential: () =>
      request<LocalClientPlaintextKey>(`${localClientBase}/credentials/me/copy`, { method: "POST" }),
    rotateMyLocalClientCredential: () =>
      request<LocalClientCredential>(`${localClientBase}/credentials/me/rotate`, { method: "POST" }),
    revokeMyLocalClientCredential: () =>
      request<{ revoked: boolean }>(`${localClientBase}/credentials/me`, { method: "DELETE" }),
    listMyLocalClientInstances: () =>
      request<LocalClientInstance[]>(`${localClientBase}/instances/me`),
    requestLocalClientPublicCapabilityUpdate: (clientInstanceId: string, expectedBundleDigest: string) =>
      request<LocalClientPublicCapabilityUpdateRequest>(
        `${localClientBase}/instances/${encodeURIComponent(clientInstanceId)}/public-capabilities/updates`,
        { method: "POST", body: JSON.stringify({ expectedBundleDigest }) }
      ),
    listLocalClientRolloutUsers: (page = 1, size = 50) =>
      request<PageResponse<LocalClientRolloutUser>>(
        `${localClientBase}/admin/rollout-users${query({ page, size })}`
      ),
    enableLocalClientRolloutUser: (userId: string) =>
      request<LocalClientRolloutUser>(`${localClientBase}/admin/rollout-users`, {
        method: "POST",
        body: JSON.stringify({ userId })
      }),
    disableLocalClientRolloutUser: (userId: string) =>
      request<void>(
        `${localClientBase}/admin/rollout-users/${encodeURIComponent(userId)}`,
        { method: "DELETE" }
      ),
    getMyLocalClientDownloadAccess: () =>
      request<LocalClientDownloadAccess>(`${localClientBase}/download-access/me`),
    syncLocalClientReleases: () =>
      request<LocalClientReleaseSyncResult>(`${localClientVersionManagementBase}/releases/sync`, {
        method: "POST"
      }),
    listLocalClientReleases: () =>
      request<LocalClientRelease[]>(`${localClientVersionManagementBase}/releases`),
    getLocalClientGlobalPolicy: () =>
      request<LocalClientGlobalPolicy>(`${localClientVersionManagementBase}/global-policy`),
    setLocalClientGlobalPolicy: (targetVersion: string) =>
      request<LocalClientGlobalPolicy>(`${localClientVersionManagementBase}/global-policy`, {
        method: "PUT",
        body: JSON.stringify({ targetVersion })
      }),
    listLocalClientUserPolicies: () =>
      request<LocalClientUserPolicy[]>(`${localClientVersionManagementBase}/user-policies`),
    setLocalClientUserPolicy: (userId: string, targetVersion: string) =>
      request<LocalClientUserPolicy>(
        `${localClientVersionManagementBase}/user-policies/${encodeURIComponent(userId)}`,
        { method: "PUT", body: JSON.stringify({ targetVersion }) }
      ),
    clearLocalClientUserPolicy: (userId: string) =>
      request<LocalClientUserPolicy>(
        `${localClientVersionManagementBase}/user-policies/${encodeURIComponent(userId)}`,
        { method: "DELETE" }
      ),
    createLocalClientRollout: (payload: LocalClientRolloutRequest) =>
      request<LocalClientRollout>(`${localClientVersionManagementBase}/rollouts`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    listLocalClientRollouts: () =>
      request<LocalClientRollout[]>(`${localClientVersionManagementBase}/rollouts`),
    listLocalClientRolloutAttempts: (rolloutId: string) =>
      request<LocalClientUpdateAttempt[]>(
        `${localClientVersionManagementBase}/rollouts/${encodeURIComponent(rolloutId)}/attempts`
      ),
    requestLocalClientUpdate: (
      clientInstanceId: string,
      payload: LocalClientUserUpdateRequest
    ) => request<LocalClientRollout>(
      `${localClientBase}/instances/${encodeURIComponent(clientInstanceId)}/updates`,
      { method: "POST", body: JSON.stringify(payload) }
    ),
    getMyOpencodeEndpoints: () =>
      request<OpencodeEndpoint[]>(agentPath("/opencode-endpoints/me")),
    commandLocalClientOpencode: (
      clientInstanceId: string,
      action: "START" | "RESTART" | "STOP" | "STATUS"
    ) => request<LocalClientCommandResult>(
      `${localClientBase}/instances/${encodeURIComponent(clientInstanceId)}/opencode/commands`,
      { method: "POST", body: JSON.stringify({ action }) }
    ),
    listLocalClientDirectories: async (clientInstanceId: string, absolutePath: string) => {
      const client = await createLocalDirectoryPickerClient(clientInstanceId);
      try {
        return await client.request<LocalClientDirectoryEntry[]>("directory.list", {
          absolutePath,
          limit: 1000
        });
      } finally {
        client.close();
      }
    },
    createLocalWorkspace: (payload: { clientInstanceId: string; name: string; rootPath: string }) =>
      request<LocalWorkspace>(`${workspaceManagementBase}/local-workspaces`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    markRecentLocalWorkspace: (workspaceId: string) =>
      request<LocalWorkspace>(
        `${workspaceManagementBase}/local-workspaces/${encodeURIComponent(workspaceId)}/recent`,
        { method: "POST" }
      ),
    deleteLocalWorkspace: (workspaceId: string) =>
      request<{ workspaceId: string; localDirectoryDeleted: boolean }>(
        `${workspaceManagementBase}/local-workspaces/${encodeURIComponent(workspaceId)}`,
        { method: "DELETE" }
      ),
    getWorkspace: (workspaceId: string) => routedRequest<Workspace>(`${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}`),
    /** 体验目录、目标服务器与 Workspace ID 全部由后端依据当前用户进程分配，客户端不传选择参数。 */
    openExperienceWorkspace: () => routedRequest<Workspace>(
      `${workspaceManagementBase}/workspaces/experience/open`,
      { method: "POST" }
    ),
    /** 用户失去体验资格或切换工作区时立即关闭对应文件连接。 */
    closeWorkspaceFileSocket: (workspaceId: string) => {
      const client = workspaceFileSockets.get(workspaceId);
      workspaceFileSockets.delete(workspaceId);
      client?.close();
      const connecting = workspaceFileConnections.get(workspaceId);
      workspaceFileConnections.delete(workspaceId);
      void connecting?.then((pendingClient) => pendingClient.close()).catch(() => undefined);
    },
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
    /** 以页面观察到的 generation 终止活动操作，防止迟到交互误伤新代次。 */
    terminateReferenceRepositoryOperation: (appId: string, repositoryId: string, expectedGeneration: number) =>
      request<ReferenceRepositoryStatus>(
        `${referenceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/terminate`,
        { method: "POST", body: JSON.stringify({ expectedGeneration }) }
      ),
    getReferenceRepositoryStatus: (appId: string, repositoryId: string) =>
      request<ReferenceRepositoryStatus>(
        `${referenceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/status`
      ),
    listReferenceRepositoryTree: (appId: string, repositoryId: string, path = "") =>
      request<ReferenceRepositoryTreeNode[]>(
        `${referenceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/tree${query({ path })}`
      ),
    listAutomationReferenceRepositories: (appId: string) =>
      request<AutomationReferenceRepositoryStatus[]>(automationReferenceRepositoryBase(appId)),
    getAutomationReferenceRepositoryStatus: (appId: string, repositoryId: string) =>
      request<AutomationReferenceRepositoryStatus>(
        `${automationReferenceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/status`
      ),
    listAutomationReferenceRepositoryBranches: (appId: string, repositoryId: string) =>
      request<string[]>(
        `${automationReferenceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/branches`
      ),
    listAutomationReferenceRepositoryTree: (
      appId: string,
      repositoryId: string,
      branch: string,
      path = ""
    ) => request<RepositoryTreeNode[]>(
      `${automationReferenceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/tree${query({ branch, path })}`
    ),
    configureAutomationReferenceRepository: (appId: string, repositoryId: string, payload: {
      alias: string;
      branch: string;
      directoryPath: string;
      description?: string;
      merge: false;
      expectedGeneration: number;
      operationId: string;
    }) => request<AutomationReferenceRepositoryStatus>(
      `${automationReferenceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/configuration`,
      { method: "PUT", body: JSON.stringify(payload) }
    ),
    synchronizeAutomationReferenceRepository: (
      appId: string,
      repositoryId: string,
      expectedGeneration: number,
      operationId: string
    ) => request<AutomationReferenceRepositoryStatus>(
      `${automationReferenceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/synchronize`,
      { method: "POST", body: JSON.stringify({ expectedGeneration, operationId }) }
    ),
    verifyAutomationReferenceRepository: (
      appId: string,
      repositoryId: string,
      expectedGeneration: number
    ) => request<AutomationReferenceRepositoryStatus>(
      `${automationReferenceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/verify`,
      { method: "POST", body: JSON.stringify({ expectedGeneration }) }
    ),
    terminateAutomationReferenceRepository: (
      appId: string,
      repositoryId: string,
      expectedGeneration: number
    ) => request<AutomationReferenceRepositoryStatus>(
      `${automationReferenceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/terminate`,
      { method: "POST", body: JSON.stringify({ expectedGeneration }) }
    ),
    listWorkspaceTemplates: (appId: string) =>
      request<ApplicationWorkspaceTemplate[]>(`${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/workspace-templates`),
    listWorkspaceVersions: (appId: string, templateId: string) =>
      request<ApplicationWorkspaceVersion[]>(
        `${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/workspace-templates/${encodeURIComponent(templateId)}/versions`
      ),
    createWorkspaceVersion: (appId: string, templateId: string, payload: CreateWorkspaceVersionPayload) =>
      routedRequest<ApplicationWorkspaceVersion>(
        `${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/workspace-templates/${encodeURIComponent(templateId)}/versions`,
        { method: "POST", body: JSON.stringify(payload) }
      ),
    /** @deprecated 版本级全员拉取已停用；请使用 gitPullPersonalWorkspace。 */
    gitPullWorkspaceVersion: (versionId: string) =>
      routedRequest<ApplicationWorkspaceVersion>(
        `${workspaceManagementBase}/workspace-versions/${encodeURIComponent(versionId)}/git-pull`,
        { method: "POST" }
      ),
    /** 在版本选择产生任何本地 worktree 副作用前，以当前用户身份只读探测关联 Git 仓库。 */
    checkWorkspaceVersionGitAccess: (versionId: string) =>
      routedRequest<GitRepositoryAccess>(
        `${workspaceManagementBase}/workspace-versions/${encodeURIComponent(versionId)}/git-access`
      ),
    listPersonalWorkspaces: (versionId: string) =>
      routedRequest<PersonalWorkspace[]>(`${workspaceManagementBase}/workspace-versions/${encodeURIComponent(versionId)}/personal-workspaces`),
    createPersonalWorkspace: (versionId: string, payload: CreatePersonalWorkspacePayload) =>
      routedRequest<PersonalWorkspace>(`${workspaceManagementBase}/workspace-versions/${encodeURIComponent(versionId)}/personal-workspaces`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    /** 只拉取当前登录用户拥有的个人 worktree，不更新共享版本或其它成员。 */
    gitPullPersonalWorkspace: (personalWorkspaceId: string) =>
      routedRequest<PersonalWorkspaceGitPullResult>(
        `${workspaceManagementBase}/personal-workspaces/${encodeURIComponent(personalWorkspaceId)}/git-pull`,
        { method: "POST" }
      ),
    /** 超级管理员只读查询每个应用将刷新的工作空间、版本和 feature 分支。 */
    listApplicationGitRefreshScopes: () =>
      request<ApplicationGitRefreshScope[]>(`${workspaceManagementBase}/applications/git-refresh-scopes`),
    /** 超级管理员刷新应用全部 feature 仓库组，并触发相关个人 worktree 安全收敛。 */
    refreshApplicationGit: (appId: string) =>
      request<ApplicationGitRefreshResult>(
        `${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/git-refresh`,
        { method: "POST" }
      ),
    /** 超级管理员只刷新一个物理 feature 分支组及其关联 worktree。 */
    refreshApplicationGitGroup: (appId: string, payload: ApplicationGitRefreshGroupSelector) =>
      request<ApplicationGitRefreshResult>(
        `${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/git-refresh-groups`,
        { method: "POST", body: JSON.stringify(payload) }
      ),
    getRecentManagedWorkspace: () => request<ManagedWorkspaceRuntime | null>(`${workspaceManagementBase}/recent-workspace`),
    getRecentManagedWorkspaceForApplication: (appId: string) =>
      request<ManagedWorkspaceRuntime | null>(`${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/recent-workspace`),
    markRecentManagedWorkspace: (workspaceId: string) =>
      routedRequest<ManagedWorkspaceRuntime>(`${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/recent`, { method: "POST" }),
    markRecentBranch: (appId: string, workspaceId: string, branch: string) =>
      routedRequest<WorkspaceBranchPreference>(
        `${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/workspaces/${encodeURIComponent(workspaceId)}/branch-preference`,
        { method: "POST", body: JSON.stringify({ branch }) }
      ),
    getRecentBranch: (appId: string, workspaceId: string) =>
      routedRequest<WorkspaceBranchPreference | null>(
        `${workspaceManagementBase}/applications/${encodeURIComponent(appId)}/workspaces/${encodeURIComponent(workspaceId)}/branch-preference`
      ),
    diffPersonalWorkspace: (personalWorkspaceId: string) =>
      routedRequest<WorkspaceDiff>(`${workspaceManagementBase}/personal-workspaces/${encodeURIComponent(personalWorkspaceId)}/diff`),
    syncPersonalToApplication: (personalWorkspaceId: string, payload: SyncWorkspacePayload) =>
      routedRequest<WorkspaceSyncResult>(`${workspaceManagementBase}/personal-workspaces/${encodeURIComponent(personalWorkspaceId)}/sync-to-application`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    syncApplicationToPersonal: (personalWorkspaceId: string, payload: SyncWorkspacePayload) =>
      routedRequest<WorkspaceSyncResult>(`${workspaceManagementBase}/personal-workspaces/${encodeURIComponent(personalWorkspaceId)}/sync-from-application`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    /**
     * 确保默认个人工作区存在：先查 (versionId, userId, workspaceName=default)，
     * 存在则复用，不存在则后台创建。
     */
    ensureDefaultPersonalWorkspace: (versionId: string) =>
      routedRequest<DefaultPersonalWorkspaceResponse>(
        `${workspaceManagementBase}/workspace-versions/${encodeURIComponent(versionId)}/ensure-default-personal-workspace`,
        { method: "POST" }
      ),
    /**
     * 基于本地 Git 获取工作区变更文件列表（不依赖 opencode runtime /vcs/diff）。
     * @param workspaceId 运行时 workspace ID（personal workspace 的 runtimeWorkspace.workspaceId）
     */
    getWorkspaceGitDiff: (workspaceId: string) =>
      routedRequest<WorkspaceGitDiff>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-diff`
      ),
    discardWorkspaceGitFiles: (workspaceId: string, files: string[]) =>
      routedRequest<void>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-discard`,
        { method: "POST", body: JSON.stringify({ files }) }
      ),
    stageWorkspaceGitFiles: (workspaceId: string, files: string[]) =>
      routedRequest<void>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-stage`,
        { method: "POST", body: JSON.stringify({ files }) }
      ),
    unstageWorkspaceGitFiles: (workspaceId: string, files: string[]) =>
      routedRequest<void>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-unstage`,
        { method: "POST", body: JSON.stringify({ files }) }
      ),
    /** 体验工作区只建立本服务器 Git 提交，不进入任何发布或 push 程序。 */
    commitExperienceWorkspace: (workspaceId: string, commitMessage: string, files: string[]) =>
      routedRequest<WorkspaceGitCommitResult>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-commit`,
        { method: "POST", body: JSON.stringify({ commitMessage, files }) }
      ),
    getWorkspaceGitConflict: (workspaceId: string, path: string) =>
      routedRequest<WorkspaceGitConflict>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-conflict${query({ path })}`
      ),
    resolveWorkspaceGitConflict: (workspaceId: string, payload: ResolveWorkspaceGitConflictPayload) =>
      routedRequest<void>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-conflict/resolve`,
        { method: "POST", body: JSON.stringify(payload) }
      ),
    resolveAllWorkspaceGitConflicts: (
      workspaceId: string,
      payload: ResolveAllWorkspaceGitConflictsPayload
    ) =>
      routedRequest<void>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-conflict/resolve-all`,
        { method: "POST", body: JSON.stringify(payload) }
      ),
    abortWorkspaceGitConflict: (workspaceId: string) =>
      routedRequest<void>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-conflict/abort`,
        { method: "POST" }
      ),
    completeWorkspaceGitMerge: (workspaceId: string) =>
      routedRequest<WorkspaceGitMergeCompletion>(
        `${workspaceManagementBase}/workspaces/${encodeURIComponent(workspaceId)}/git-conflict/complete`,
        { method: "POST" }
      ),
    /** 仅提交个人 worktree，不推送远端。 */
    commitPersonalWorkspace: (personalWorkspaceId: string, payload: PublishPersonalWorkspacePayload) =>
      routedRequest<PublishPersonalWorkspaceResult>(
        `${workspaceManagementBase}/personal-workspaces/${encodeURIComponent(personalWorkspaceId)}/commit`,
        { method: "POST", body: JSON.stringify(payload) }
      ),
    /** 从个人 HEAD 按白名单投影到应用 feature worktree，提交并推送。 */
    publishPersonalWorkspace: (personalWorkspaceId: string, payload: PublishPersonalWorkspacePayload) =>
      routedRequest<PublishPersonalWorkspaceResult>(
        `${workspaceManagementBase}/personal-workspaces/${encodeURIComponent(personalWorkspaceId)}/publish`,
        { method: "POST", body: JSON.stringify(payload) }
      ),
    previewPersonalWorkspacePublish: (personalWorkspaceId: string) =>
      routedRequest<PublishPersonalWorkspacePreview>(
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
    readWorkspaceViewFilePreviewChunk: async (
      workspaceId: string,
      locator: WorkspaceViewLocator,
      preview: FilePreviewChunkRequest
    ): Promise<FilePreviewChunk> => mapFilePreviewChunk(await workspaceFileRpc<BackendFilePreviewChunk>(
      workspaceId,
      "workspace.view.read.chunk",
      { locator, ...preview },
      true
    )),
    readWorkspaceViewFileBinaryChunk: async (
      workspaceId: string,
      locator: WorkspaceViewLocator,
      request: FileBinaryChunkRequest
    ): Promise<FileBinaryChunk> => mapFileBinaryChunk(await workspaceFileRpc<BackendFileBinaryChunk>(
      workspaceId,
      "workspace.view.read.binary.chunk",
      { locator, ...request },
      true
    )),
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
    readFilePreviewChunk: async (
      workspaceId: string,
      path: string,
      preview: FilePreviewChunkRequest
    ): Promise<FilePreviewChunk> => mapFilePreviewChunk(await workspaceFileRpc<BackendFilePreviewChunk>(
      workspaceId,
      "workspace.read.chunk",
      { path, ...preview },
      true
    )),
    readFileBinaryChunk: async (
      workspaceId: string,
      path: string,
      request: FileBinaryChunkRequest
    ): Promise<FileBinaryChunk> => mapFileBinaryChunk(await workspaceFileRpc<BackendFileBinaryChunk>(
      workspaceId,
      "workspace.read.binary.chunk",
      { path, ...request },
      true
    )),
    writeFile: (workspaceId: string, path: string, content: string) =>
      workspaceFileRpc<void>(workspaceId, "workspace.write", { path, content }),
    uploadWorkspaceFile: async (
      workspaceId: string,
      path: string,
      file: Blob,
      onProgress?: FileUploadProgressHandler
    ) => {
      // 上传会话绑定创建它的同一条 WebSocket，避免中途重连后把分片发给另一条连接。
      const client = await ensureWorkspaceFileClient(workspaceId);
      await uploadBlobInChunks(
        file,
        path,
        (op, params) => client.request(op, { workspaceId, ...params }),
        "workspace.upload",
        onProgress
      );
    },
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
    resolveWorkspacePhysicalPath: (workspaceId: string, path: string) =>
      workspaceFileRpc<string>(workspaceId, "workspace.resolve-physical-path", { path }),
    listRequirementImportApplications: () =>
      request<RequirementImportApplication[]>(`${requirementImportBase}/applications`),
    listRequirementImportItems: (appShortName: string, editionId: string) =>
      request<RequirementImportItem[]>(`${requirementImportBase}/sub-items${query({ appShortName, editionId })}`),
    listWorkspaceRequirementImportItems: (workspaceId: string, appShortName: string, editionId: string) =>
      workspaceFileRpc<RequirementImportItem[]>(
        workspaceId,
        "workspace.requirement-import-items",
        { appShortName, editionId }
      ),
    importWorkspaceRequirements: (command: RequirementImportCommand) =>
      workspaceFileRpc<RequirementImportResult>(
        command.workspaceId,
        "workspace.requirement-import",
        command as unknown as Record<string, unknown>
      ),
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
    closeWorkspaceFileConnections,
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
      routedRequest<AgentConfigStatus>(`${agentConfigBase}/workspaces/${encodeURIComponent(workspaceId)}/status`),
    listPublicAgentBranches: () => request<string[]>(`${agentConfigBase}/public/branches`),
    listPublicAgentRepositories: () => request<PublicAgentRepositoryStatus[]>(`${agentConfigBase}/public/repositories`),
    getPublicAgentConfigRollout: () =>
      request<PublicAgentConfigRolloutStatus | null>(`${agentConfigBase}/public/rollout`),
    getApplicationAgentConfigRollouts: () =>
      request<PublicAgentConfigRolloutStatus[]>(`${agentConfigBase}/application/rollouts`),
    supersedePublicAgentConfigRollout: (payload: {
      activeRolloutId: string;
      branch: string;
      operationId?: string;
      discardLocalChanges?: boolean;
      reason: string;
    }) => request<AgentConfigOperation>(`${agentConfigBase}/public/rollout/supersede`, {
      method: "POST",
      body: JSON.stringify(payload)
    }),
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
    /**
     * 仅保留给旧客户端的按服务器 URL 兼容方法；当前前端没有调用方。
     * 后端实际委托公共全局 rollout，linuxServerId 不代表只更新一台服务器。
     */
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
    readPublicAgentFilePreviewChunk: async (
      path: string,
      preview: FilePreviewChunkRequest,
      worktreeId?: string | null,
      linuxServerId?: string | null
    ): Promise<FilePreviewChunk> => mapFilePreviewChunk(await agentConfigFileRpc<BackendFilePreviewChunk>(
      "PUBLIC",
      "agent-config.read.chunk",
      { path, ...preview },
      { worktreeId, linuxServerId },
      true
    )),
    writePublicAgentFile: (path: string, content: string, worktreeId?: string | null, linuxServerId?: string | null) =>
      agentConfigFileRpc<void>(
        "PUBLIC",
        "agent-config.write",
        { path, content },
        { worktreeId, linuxServerId }
      ),
    uploadPublicAgentFile: async (
      path: string,
      file: Blob,
      worktreeId?: string | null,
      linuxServerId?: string | null,
      onProgress?: FileUploadProgressHandler
    ) => {
      const context = { worktreeId, linuxServerId };
      const client = await ensureAgentConfigFileClient("PUBLIC", context);
      await uploadBlobInChunks(
        file,
        path,
        (op, params) => client.request(op, {
          scope: "PUBLIC",
          worktreeId: worktreeId ?? undefined,
          ...params
        }),
        "agent-config.upload",
        onProgress
      );
    },
    renamePublicAgentFile: (path: string, name: string, worktreeId?: string | null, linuxServerId?: string | null) =>
      agentConfigFileRpc<void>(
        "PUBLIC",
        "agent-config.rename",
        { path, name },
        { worktreeId, linuxServerId }
      ),
    copyPublicAgentFile: (
      sourcePath: string,
      targetPath: string,
      worktreeId?: string | null,
      linuxServerId?: string | null
    ) => agentConfigFileRpc<void>(
      "PUBLIC",
      "agent-config.copy",
      { sourcePath, targetPath },
      { worktreeId, linuxServerId }
    ),
    movePublicAgentFile: (
      sourcePath: string,
      targetPath: string,
      worktreeId?: string | null,
      linuxServerId?: string | null
    ) => agentConfigFileRpc<void>(
      "PUBLIC",
      "agent-config.move",
      { sourcePath, targetPath },
      { worktreeId, linuxServerId }
    ),
    deletePublicAgentFile: (path: string, worktreeId?: string | null, linuxServerId?: string | null) =>
      agentConfigFileRpc<void>(
        "PUBLIC",
        "agent-config.delete",
        { path },
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
    readWorkspaceAgentFilePreviewChunk: async (
      workspaceId: string,
      path: string,
      preview: FilePreviewChunkRequest,
      worktreeId?: string | null
    ): Promise<FilePreviewChunk> => mapFilePreviewChunk(await agentConfigFileRpc<BackendFilePreviewChunk>(
      "WORKSPACE",
      "agent-config.read.chunk",
      { path, ...preview },
      { workspaceId, worktreeId },
      true
    )),
    writeWorkspaceAgentFile: (workspaceId: string, path: string, content: string, worktreeId?: string | null) =>
      agentConfigFileRpc<void>(
        "WORKSPACE",
        "agent-config.write",
        { path, content },
        { workspaceId, worktreeId }
      ),
    reconcileWorkspaceAutomationReferences: (workspaceId: string) =>
      agentConfigFileRpc<AutomationReferenceWorkspaceReconciliation>(
        "WORKSPACE",
        "agent-config.automation-reference.reconcile",
        {},
        { workspaceId },
        true
      ),
    uploadWorkspaceAgentFile: async (
      workspaceId: string,
      path: string,
      file: Blob,
      worktreeId?: string | null,
      onProgress?: FileUploadProgressHandler
    ) => {
      const context = { workspaceId, worktreeId };
      const client = await ensureAgentConfigFileClient("WORKSPACE", context);
      await uploadBlobInChunks(
        file,
        path,
        (op, params) => client.request(op, {
          scope: "WORKSPACE",
          workspaceId,
          worktreeId: worktreeId ?? undefined,
          ...params
        }),
        "agent-config.upload",
        onProgress
      );
    },
    renameWorkspaceAgentFile: (workspaceId: string, path: string, name: string, worktreeId?: string | null) =>
      agentConfigFileRpc<void>(
        "WORKSPACE",
        "agent-config.rename",
        { path, name },
        { workspaceId, worktreeId }
      ),
    copyWorkspaceAgentFile: (
      workspaceId: string,
      sourcePath: string,
      targetPath: string,
      worktreeId?: string | null
    ) => agentConfigFileRpc<void>(
      "WORKSPACE",
      "agent-config.copy",
      { sourcePath, targetPath },
      { workspaceId, worktreeId }
    ),
    moveWorkspaceAgentFile: (
      workspaceId: string,
      sourcePath: string,
      targetPath: string,
      worktreeId?: string | null
    ) => agentConfigFileRpc<void>(
      "WORKSPACE",
      "agent-config.move",
      { sourcePath, targetPath },
      { workspaceId, worktreeId }
    ),
    deleteWorkspaceAgentFile: (workspaceId: string, path: string, worktreeId?: string | null) =>
      agentConfigFileRpc<void>(
        "WORKSPACE",
        "agent-config.delete",
        { path },
        { workspaceId, worktreeId }
      ),
    createWorkspaceAgentWorktree: (workspaceId: string, payload: AgentConfigWorktreePayload) =>
      routedRequest<AgentConfigWorktree>(`${agentConfigBase}/workspaces/${encodeURIComponent(workspaceId)}/worktrees`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    getWorkspaceAgentDiff: (workspaceId: string, worktreeId?: string | null) =>
      routedRequest<AgentConfigDiff>(`${agentConfigBase}/workspaces/${encodeURIComponent(workspaceId)}/diff${query({ worktreeId })}`),
    stageWorkspaceAgentFiles: (workspaceId: string, files: string[], worktreeId?: string | null) =>
      routedRequest<void>(`${agentConfigBase}/workspaces/${encodeURIComponent(workspaceId)}/stage`, {
        method: "POST",
        body: JSON.stringify({ files, worktreeId })
      }),
    unstageWorkspaceAgentFiles: (workspaceId: string, files: string[], worktreeId?: string | null) =>
      routedRequest<void>(`${agentConfigBase}/workspaces/${encodeURIComponent(workspaceId)}/unstage`, {
        method: "POST",
        body: JSON.stringify({ files, worktreeId })
      }),
    discardWorkspaceAgentFiles: (workspaceId: string, files: string[], worktreeId?: string | null) =>
      routedRequest<void>(`${agentConfigBase}/workspaces/${encodeURIComponent(workspaceId)}/discard`, {
        method: "POST",
        body: JSON.stringify({ files, worktreeId })
      }),
    commitWorkspaceAgentConfig: (workspaceId: string, payload: AgentConfigCommitPayload) =>
      routedRequest<AgentConfigOperation>(`${agentConfigBase}/workspaces/${encodeURIComponent(workspaceId)}/commit`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    publishWorkspaceAgentConfig: (workspaceId: string, worktreeId?: string | null, operationId?: string) =>
      routedRequest<AgentConfigOperation>(`${agentConfigBase}/workspaces/${encodeURIComponent(workspaceId)}/publish`, {
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
    listAppSourceRepositories: (appId: string) =>
      routedRequest<AppSourceRepositorySummary[]>(appSourceRepositoryBase(appId)),
    listAppSourceBranches: (appId: string, repositoryId: string) =>
      routedRequest<string[]>(
        `${appSourceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/branches`,
        { timeoutMs: APP_SOURCE_BRANCH_REQUEST_TIMEOUT_MS }
      ),
    listAppSourceTree: (
      appId: string,
      repositoryId: string,
      branch: string,
      path = "."
    ) =>
      routedRequest<AppSourceRemoteTreeNode[]>(
        `${appSourceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/tree${query({ branch, path })}`,
        { timeoutMs: APP_SOURCE_TREE_REQUEST_TIMEOUT_MS }
      ),
    getAppSourceTreeSnapshot: (
      appId: string,
      repositoryId: string,
      branch: string,
      path = "."
    ) =>
      routedRequest<AppSourceTreeSnapshot>(
        `${appSourceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/tree${query({
          branch,
          path,
          includeCommit: true
        })}`,
        { timeoutMs: APP_SOURCE_TREE_REQUEST_TIMEOUT_MS }
      ),
    materializeAppSource: (
      appId: string,
      repositoryId: string,
      payload: AppSourceMaterializationPayload
    ) =>
      routedRequest<AppSourceOperation>(
        `${appSourceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/materializations`,
        { method: "POST", body: JSON.stringify(payload) }
      ),
    retryAppSourceReplicas: (
      appId: string,
      repositoryId: string,
      payload: AppSourceReplicaRetryPayload
    ) =>
      routedRequest<AppSourceOperation>(
        `${appSourceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/replica-retries`,
        { method: "POST", body: JSON.stringify(payload) }
      ),
    updateAppSourceRetention: (
      appId: string,
      repositoryId: string,
      payload: AppSourceRetentionUpdatePayload
    ) =>
      routedRequest<AppSourceRetentionUpdateResult>(
        `${appSourceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/retention`,
        { method: "PATCH", body: JSON.stringify(payload) }
      ),
    openAppSource: (appId: string, repositoryId: string, generation: number) =>
      routedRequest<AppSourceOpenResult>(
        `${appSourceRepositoryBase(appId)}/${encodeURIComponent(repositoryId)}/open`,
        { method: "POST", body: JSON.stringify({ generation }) }
      ),
    getRecentAppSource: () =>
      routedRequest<AppSourceOpenResult | null>(`${workspaceManagementBase}/recent-app-source`),
    clearRecentAppSource: () =>
      routedRequest<void>(`${workspaceManagementBase}/recent-app-source`, { method: "DELETE" }),
    getAppSourceOperation: async (operationId: string) =>
      request<AppSourceOperation>(`${appSourceOperationBase}/${appSourceOperationPathSegment(operationId)}`),
    createAppSourceOperationTicket: async (operationId: string) =>
      request<AppSourceOperationTicketResponse>(
        `${appSourceOperationBase}/${appSourceOperationPathSegment(operationId)}/ticket`,
        { method: "POST" }
      ),
    /**
     * 单次调用只消费一个新 ticket 并建立一条连接；close 仅停止观察，重连策略与时机由调用方决定。
     */
    connectAppSourceProgress: async (
      operationId: string,
      onEvent: AppSourceProgressHandler,
      options: { signal?: AbortSignal } = {}
    ): Promise<AppSourceProgressConnection> => {
      const normalizedOperationId = normalizeAppSourceOperationId(operationId);
      const abortError = () => {
        const error = new Error("应用源码进度连接已取消");
        error.name = "AbortError";
        return error;
      };
      if (options.signal?.aborted) throw abortError();
      const ticket = await request<AppSourceOperationTicketResponse>(
        `${appSourceOperationBase}/${encodeURIComponent(normalizedOperationId)}/ticket`,
        { method: "POST", signal: options.signal }
      );
      if (options.signal?.aborted) throw abortError();
      const socket = webSocketFactory(toWebSocketUrl(baseUrl, ticket.webSocketUrl));
      let callerClosed = false;
      let connectionFailureReported = false;
      let rejectOpening: ((reason?: unknown) => void) | null = null;
      let openingTimeout: ReturnType<typeof setTimeout> | null = null;
      const abortConnection = () => {
        callerClosed = true;
        if (openingTimeout) clearTimeout(openingTimeout);
        openingTimeout = null;
        socket.close();
        rejectOpening?.(abortError());
        rejectOpening = null;
      };
      options.signal?.addEventListener("abort", abortConnection, { once: true });
      socket.onmessage = (event) => {
        let parsed: AppSourceProgressEvent;
        try {
          const candidate = JSON.parse(String(event.data)) as unknown;
          if (!isAppSourceProgressEvent(candidate)) {
            throw new Error("invalid app-source progress event");
          }
          parsed = candidate;
        } catch {
          onEvent(appSourceClientFailure(
            normalizedOperationId,
            "WEBSOCKET_MESSAGE_INVALID",
            "应用源码进度消息格式无效"
          ));
          return;
        }
        // 调用方异常属于业务回调失败，不能被误判为协议消息格式错误并二次回调。
        onEvent(parsed);
      };
      const reportConnectionFailure = (errorCode: string, errorMessage: string) => {
        if (callerClosed || connectionFailureReported) return;
        connectionFailureReported = true;
        onEvent(appSourceClientFailure(normalizedOperationId, errorCode, errorMessage));
      };
      const connectionFailure = () => reportConnectionFailure(
        "WEBSOCKET_ERROR",
        "应用源码进度连接失败"
      );
      socket.onerror = connectionFailure;
      socket.onclose = () => reportConnectionFailure(
        "WEBSOCKET_DISCONNECTED",
        "应用源码进度连接已断开"
      );
      if (socket.readyState !== WEBSOCKET_OPEN_STATE) {
        await new Promise<void>((resolve, reject) => {
          rejectOpening = reject;
          openingTimeout = setTimeout(() => {
            callerClosed = true;
            options.signal?.removeEventListener("abort", abortConnection);
            rejectOpening = null;
            socket.close();
            reject(new Error("应用源码进度连接超时"));
          }, APP_SOURCE_PROGRESS_OPEN_TIMEOUT_MS);
          socket.onopen = () => {
            if (openingTimeout) clearTimeout(openingTimeout);
            openingTimeout = null;
            rejectOpening = null;
            resolve();
          };
          socket.onerror = () => {
            if (openingTimeout) clearTimeout(openingTimeout);
            openingTimeout = null;
            rejectOpening = null;
            options.signal?.removeEventListener("abort", abortConnection);
            connectionFailure();
            reject(new Error("应用源码进度连接失败"));
          };
        });
        socket.onerror = connectionFailure;
      }
      if (options.signal?.aborted) {
        abortConnection();
        throw abortError();
      }
      return {
        close: () => {
          callerClosed = true;
          options.signal?.removeEventListener("abort", abortConnection);
          socket.close();
        }
      };
    },
    listAllSessions: (page = 1, size = 30, q?: string) =>
      routedRequest<PageResponse<Session>>(`${opencodeRuntimeBase}/sessions${query({ page, size, q })}`),
    listSessionShareCandidates: (q?: string, page = 1, size = 20) =>
      request<PageResponse<SessionShareCandidate>>(
        `${opencodeRuntimeBase}/session-share-candidates${query({ q, page, size })}`
      ),
    getSessionCollaborationShare: (sessionId: string) =>
      request<SessionCollaborationShare | null>(
        `${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/collaboration-share`
      ),
    putSessionCollaborationShare: (sessionId: string, payload: PutSessionCollaborationSharePayload) =>
      request<SessionCollaborationShare>(
        `${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/collaboration-share`,
        { method: "PUT", body: JSON.stringify(payload) }
      ),
    revokeSessionCollaborationShare: (sessionId: string, expectedVersion: number) =>
      request<SessionCollaborationShare>(
        `${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/collaboration-share${query({ expectedVersion })}`,
        { method: "DELETE" }
      ),
    listSharedSessions: (page = 1, size = 30) =>
      request<PageResponse<SharedSessionListItem>>(
        `${opencodeRuntimeBase}/session-shares${query({ page, size })}`
      ),
    /** 通知目标是受控 actionTargetId；客户端不得把返回字段当成外部 URL。 */
    listUserNotifications: (page = 1, size = 20, unreadOnly = false) =>
      request<UserNotificationPage>(
        `${notificationCenterBase}${query({ page, size, unreadOnly })}`
      ),
    markUserNotificationRead: (notificationId: string) =>
      request<{ notificationId: string; read: boolean }>(
        `${notificationCenterBase}/${encodeURIComponent(notificationId)}/read`,
        { method: "POST" }
      ),
    getSessionShareAccess: () =>
      request<SessionShareAccess>(`${opencodeRuntimeBase}/session-shares/access`),
    getSessionRuntimeState: async () =>
      normalizeSessionRuntimeStateSummary(
        await routedRequest<SessionRuntimeStateSummary>(`${opencodeRuntimeBase}/sessions/runtime-state`)
      ),
    listSessions: (workspaceId: string, page = 1, size = 20) =>
      routedRequest<PageResponse<Session>>(`${opencodeRuntimeBase}/workspaces/${workspaceId}/sessions?page=${page}&size=${size}`),
    getSession: (sessionId: string) => routedRequest<Session>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}`),
    updateSession: (sessionId: string, payload: { title?: string; pinned?: boolean }) =>
      routedRequest<Session>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}`, { method: "PATCH", body: JSON.stringify(payload) }),
    deleteSession: (sessionId: string) => routedRequest<Session>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}`, { method: "DELETE" }),
    listSessionMessages: (sessionId: string, page = 1, size = 100, options: { refresh?: boolean } = {}) =>
      routedRequest<PageResponse<SessionMessage>>(
        `${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/messages${query({ page, size, refresh: options.refresh })}`
      ),
    getSessionUserMessageForRun: (sessionId: string, runId: string) =>
      routedRequest<SessionMessage>(
        `${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/messages/runs/${encodeURIComponent(runId)}/user`
      ),
    listSessionMessagesForRun: (sessionId: string, runId: string) =>
      routedRequest<SessionMessage[]>(
        `${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/messages/runs/${encodeURIComponent(runId)}`
      ),
    getNightExecutionSlots: () =>
      routedRequest<NightExecutionSlots>(`${opencodeRuntimeBase}/night-execution/slots`),
    createNightExecutionTask: (payload: CreateNightExecutionTaskPayload) =>
      routedRequest<NightExecutionTask>(`${opencodeRuntimeBase}/night-execution/tasks`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    listNightExecutionTasks: (params: { sessionId?: string; page?: number; size?: number } = {}) =>
      routedRequest<NightExecutionTaskQueryResponse>(
        `${opencodeRuntimeBase}/night-execution/tasks${query({
          sessionId: params.sessionId,
          page: params.page,
          size: params.size
        })}`
      ),
    adjustNightExecutionTask: (taskId: string, slotStart: string) =>
      routedRequest<NightExecutionTask>(`${opencodeRuntimeBase}/night-execution/tasks/${encodeURIComponent(taskId)}`, {
        method: "PATCH",
        body: JSON.stringify({ slotStart })
      }),
    cancelNightExecutionTask: (taskId: string) =>
      routedRequest<NightExecutionTask>(`${opencodeRuntimeBase}/night-execution/tasks/${encodeURIComponent(taskId)}/cancel`, {
        method: "POST"
      }),
    dismissNightExecutionTask: (taskId: string) =>
      routedRequest<NightExecutionTask>(`${opencodeRuntimeBase}/night-execution/tasks/${encodeURIComponent(taskId)}/dismiss`, {
        method: "POST"
      }),
    getSessionTreeMessages: (sessionId: string) =>
      routedRequest<SessionTreeMessagesResponse>(agentPath(`/sessions/${encodeURIComponent(sessionId)}/session-tree/messages`)),
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
    getActiveRun: (sessionId: string) => routedRequest<Run | null>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/active-run`),
    askSideQuestion: (sessionId: string, payload: SideQuestionRequest) =>
      routedRequest<SideQuestionResponse>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/side-question`, {
        method: "POST",
        body: JSON.stringify(payload),
        timeoutMs: 120000
      }),
    getRunContext: (sessionId: string) =>
      routedRequest<ConversationRunContext>(agentPath(`/sessions/${encodeURIComponent(sessionId)}/run-context`), { method: "POST" }),
    startSideQuestionRun: (sessionId: string, payload: SideQuestionRunRequest) =>
      routedRequest<SideQuestionRunResponse>(
        `${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/side-question/runs`,
        {
          method: "POST",
          body: JSON.stringify(payload)
        }
      ),
    startManualQuestionRun: (payload: ManualQuestionRunRequest) =>
      routedRequest<SideQuestionRunResponse>(`${opencodeRuntimeBase}/manual-question/runs`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    createSession: (workspaceId: string, title: string) =>
      routedRequest<Session>(`${opencodeRuntimeBase}/sessions`, { method: "POST", body: JSON.stringify({ workspaceId, title }) }),
    createBatchItemSession: (workspaceId: string, title: string, batchContext: BatchContext) =>
      routedRequest<Session>(`${opencodeRuntimeBase}/sessions/batch-items`, {
        method: "POST",
        body: JSON.stringify({ workspaceId, title, batchContext })
      }),
    startRun: (sessionIdOrPayload: string | StartRunPayload, prompt?: string) =>
      routedRequest<Run>(agentPath("/runs"), {
        method: "POST",
        body: JSON.stringify(normalizeStartRunPayload(sessionIdOrPayload, prompt)),
        timeoutMs: 120000
      }),
    createRunResend: (sessionId: string, payload: CreateRunResendPayload) =>
      routedRequest<RunResendResponse>(agentPath(`/sessions/${encodeURIComponent(sessionId)}/resends`), {
        method: "POST",
        body: JSON.stringify(payload),
        timeoutMs: 120000
      }),
    getMyOpencodeProcess: () => routedRequest<UserOpencodeProcess>(agentPath("/processes/me")),
    getMyOpencodeMessageGate: () =>
      request<UserOpencodeMessageGate>(agentPath("/processes/me/message-gate")),
    getMyOpencodeProcessHealth: (params: UserOpencodeProcessHealthRequest) =>
      routedRequest<UserOpencodeProcessHealth>(agentPath(`/processes/me/health${query(params)}`)),
    initializeMyOpencodeProcess: (operationId?: string) =>
      routedRequest<UserOpencodeProcess>(agentPath("/processes/me/initialize"), {
        method: "POST",
        ...(operationId ? { body: JSON.stringify({ operationId }) } : {}),
        timeoutMs: 120000
      }),
    /** 重启始终由后端按当前用户 binding 路由；confirmRunning 只确认取消活动 Run，不参与目标选择。 */
    restartMyOpencodeProcess: (confirmRunning = false) =>
      routedRequest<UserOpencodeProcess>(agentPath("/processes/me/restart"), {
        method: "POST",
        body: JSON.stringify({ confirmRunning }),
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
    listExternalApiScopes: () =>
      request<ExternalApiScopeOption[]>(`${externalApiCredentialBase}/scopes`),
    listExternalApiCredentials: (params: ExternalApiCredentialListParams = {}) =>
      request<PageResponse<ExternalApiCredential>>(
        `${externalApiCredentialBase}${query({
          keyword: params.keyword,
          enabled: params.enabled,
          page: params.page,
          size: params.size
        })}`
      ),
    createExternalApiCredential: (payload: ExternalApiCredentialCreatePayload) =>
      request<ExternalApiCredentialCreated>(externalApiCredentialBase, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    updateExternalApiCredential: (credentialId: string, payload: ExternalApiCredentialUpdatePayload) =>
      request<ExternalApiCredential>(
        `${externalApiCredentialBase}/${encodeURIComponent(credentialId)}`,
        { method: "PATCH", body: JSON.stringify(payload) }
      ),
    revealExternalApiCredential: (credentialId: string) =>
      request<ExternalApiCredentialRevealed>(
        `${externalApiCredentialBase}/${encodeURIComponent(credentialId)}/reveal`,
        { method: "POST" }
      ),
    rotateExternalApiCredential: (credentialId: string) =>
      request<ExternalApiCredentialRevealed>(
        `${externalApiCredentialBase}/${encodeURIComponent(credentialId)}/rotate`,
        { method: "POST" }
      ),
    deleteExternalApiCredential: (credentialId: string) =>
      request<null>(`${externalApiCredentialBase}/${encodeURIComponent(credentialId)}`, { method: "DELETE" }),
    getAnalyticsOverview: (params: AnalyticsQueryParams = {}) =>
      request<AnalyticsOverview>(`${analyticsBase}/overview${query({ ...params })}`),
    getAnalyticsFilterOptions: (params: AnalyticsQueryParams = {}) =>
      request<AnalyticsFilterOptions>(`${analyticsBase}/filter-options${query({ ...params })}`),
    getAnalyticsFunnel: (params: AnalyticsQueryParams = {}) =>
      request<AnalyticsFunnel>(`${analyticsBase}/funnel${query({ ...params })}`),
    getAnalyticsHourlyHeatmap: (params: AnalyticsQueryParams = {}, metric: AnalyticsHeatmapMetric = "USER_MESSAGES") =>
      request<AnalyticsHourlyHeatmap>(`${analyticsBase}/hourly-heatmap${query({ ...params, metric })}`),
    getAnalyticsTokenOperations: (params: AnalyticsQueryParams = {}) =>
      request<AnalyticsTokenOperations>(`${analyticsBase}/token-operations${query({ ...params })}`),
    getAnalyticsCapabilities: (params: AnalyticsQueryParams = {}) =>
      request<AnalyticsCapabilities>(`${analyticsBase}/capabilities${query({ ...params })}`),
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
    exportAnalyticsCsv: (type: "overview" | "timeseries" | "users" | "organizations" | "feedback" | "exceptions" | "funnel" | "token-operations" | "capabilities", params: AnalyticsQueryParams = {}) =>
      requestCsv(`${analyticsBase}/export${query({ ...params, type })}`),
    listTraces: (params: TraceQueryParams = {}) =>
      request<PageResponse<TraceCatalog>>(`${traceBase}${query({ ...params })}`),
    getTrace: (traceId: string) =>
      request<TraceCatalog>(`${traceBase}/${encodeURIComponent(traceId)}`),
    getTraceEvents: (traceId: string, afterSequence = 0, limit = 500) =>
      request<TraceRawEventPage>(
        `${traceBase}/${encodeURIComponent(traceId)}/events${query({ afterSequence, limit })}`
      ),
    getTraceSpans: (traceId: string, afterSequence = 0, limit = 500) =>
      request<TraceSpanPage>(
        `${traceBase}/${encodeURIComponent(traceId)}/spans${query({ afterSequence, limit })}`
      ),
    getTraceRecord: (traceId: string, eventId: string, globalSequence: number) =>
      request<TraceRawEventPage>(
        `${traceBase}/${encodeURIComponent(traceId)}/records/${encodeURIComponent(eventId)}`
          + query({ globalSequence })
      ),
    downloadTrace: (traceId: string) =>
      requestBlob(`${traceBase}/${encodeURIComponent(traceId)}/download`, {}, "application/gzip"),
    createXxlJobSsoTicket: () =>
      request<XxlJobSsoTicket>(`${xxlJobBase}/sso-tickets`, { method: "POST" }),
    createLobehubSsoTicket: () =>
      request<LobehubSsoTicket>(`${lobehubSsoBase}/tickets`, { method: "POST" }),
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
        `${commonParameterBase}${query({
          platform: params.platform,
          englishName: params.englishName,
          page: params.page,
          size: params.size
        })}`
      ),
    updateGeneralParameter: (parameterId: string, payload: GeneralParameterUpdatePayload) =>
      request<GeneralParameter>(`${commonParameterBase}/${encodeURIComponent(parameterId)}`, {
        method: "PATCH",
        body: JSON.stringify(payload)
      }),
    listCommonParameterChangeLogs: (parameterId: string) =>
      request<CommonParameterChangeLog[]>(`${commonParameterBase}/${encodeURIComponent(parameterId)}/change-logs`),
    listCommonParameterMemoryValues: () =>
      request<CommonParameterMemoryCluster>(`${commonParameterBase}/memory-values`),
    getCommonParameterMemoryValues: (backendProcessId: string) =>
      request<CommonParameterMemoryProcess>(
        `${commonParameterBase}/memory-values/${encodeURIComponent(backendProcessId)}`
      ),
    refreshCommonParameterMemoryValues: () =>
      request<CommonParameterMemoryCluster>(`${commonParameterBase}/memory-values/refresh`, { method: "POST" }),
    refreshCommonParameterMemoryValuesForProcess: (backendProcessId: string) =>
      request<CommonParameterMemoryProcess>(
        `${commonParameterBase}/memory-values/${encodeURIComponent(backendProcessId)}/refresh`,
        { method: "POST" }
      ),
    getInternalModelProviders: () =>
      request<InternalModelProviderManagementResponse>(`${configurationBase}/internal-model-providers`),
    updateInternalModelProviders: (payload: InternalModelProviderUpdatePayload) =>
      request<InternalModelProviderManagementResponse>(`${configurationBase}/internal-model-providers`, {
        method: "PUT",
        body: JSON.stringify(payload)
      }),
    getInternalModelProviderModels: (providerId: string) =>
      request<InternalModelProviderModel[]>(
        `${configurationBase}/internal-model-providers/${encodeURIComponent(providerId)}/models`
      ),
    updateInternalModelProviderModels: (providerId: string, payload: InternalModelProviderModelUpdatePayload) =>
      request<InternalModelProviderModel[]>(
        `${configurationBase}/internal-model-providers/${encodeURIComponent(providerId)}/models`,
        { method: "PUT", body: JSON.stringify(payload) }
      ),
    probeInternalModelProviderModel: (
      providerId: string,
      modelId: string,
      capability: InternalModelCapability
    ) => request<InternalModelCapabilityProbeResult>(
      `${configurationBase}/internal-model-providers/${encodeURIComponent(providerId)}/models/${encodeURIComponent(modelId)}/probe`,
      { method: "POST", body: JSON.stringify({ capability }) }
    ),
    getInternalModelProviderRefreshStatus: () =>
      request<InternalModelProviderRefreshStatus>(`${configurationBase}/internal-model-providers/refresh-status`),
    refreshInternalModelProviders: () =>
      request<InternalModelProviderRefreshStatus>(`${configurationBase}/internal-model-providers/refresh`, { method: "POST" }),
    listInternalModelTokens: () =>
      request<InternalModelTokenDefinition[]>(`${configurationBase}/internal-model-tokens`),
    createInternalModelToken: (payload: InternalModelTokenCreatePayload) =>
      request<InternalModelTokenDefinition>(`${configurationBase}/internal-model-tokens`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    updateInternalModelToken: (tokenId: number, payload: InternalModelTokenUpdatePayload) =>
      request<InternalModelTokenDefinition>(
        `${configurationBase}/internal-model-tokens/${encodeURIComponent(String(tokenId))}`,
        { method: "PATCH", body: JSON.stringify(payload) }
      ),
    deleteInternalModelToken: (tokenId: number) =>
      request<InternalModelTokenDeleteResponse>(
        `${configurationBase}/internal-model-tokens/${encodeURIComponent(String(tokenId))}`,
        { method: "DELETE" }
      ),
    listInternalModelCallRecords: (params: {
      providerId?: string | null;
      outcome?: InternalModelCallOutcome | null;
      outcomeGroup?: InternalModelCallOutcomeGroup | null;
      source?: InternalModelCallSource | null;
      ucid?: string | null;
      from?: string | null;
      to?: string | null;
      page?: number;
      size?: number;
    } = {}) => request<PageResponse<InternalModelCallRecord>>(
      `${internalModelObservabilityBase}/call-records${query({
        providerId: params.providerId,
        outcome: params.outcome,
        outcomeGroup: params.outcomeGroup,
        source: params.source,
        ucid: params.ucid,
        from: params.from,
        to: params.to,
        page: params.page ?? 1,
        size: params.size ?? 20
      })}`
    ),
    getInternalModelCallStats: (params: {
      providerId?: string | null;
      source?: InternalModelCallSource | null;
      from?: string | null;
      to?: string | null;
    } = {}) => request<InternalModelCallHourlyStat[]>(
      `${internalModelObservabilityBase}/stats${query({
        providerId: params.providerId,
        source: params.source,
        from: params.from,
        to: params.to
      })}`
    ),
    getInternalModelTtftDistribution: (params: {
      providerId?: string | null;
      outcomeGroup?: InternalModelCallOutcomeGroup | null;
      source?: InternalModelCallSource | null;
      from?: string | null;
      to?: string | null;
    } = {}) => request<InternalModelTtftDistribution>(
      `${internalModelObservabilityBase}/ttft-distribution${query({
        providerId: params.providerId,
        outcomeGroup: params.outcomeGroup,
        source: params.source,
        from: params.from,
        to: params.to
      })}`
    ),
    getInternalModelItlDistribution: (params: {
      providerId?: string | null;
      outcomeGroup?: InternalModelCallOutcomeGroup | null;
      source?: InternalModelCallSource | null;
      from?: string | null;
      to?: string | null;
    } = {}) => request<InternalModelLatencyDistribution>(
      `${internalModelObservabilityBase}/itl-distribution${query({
        providerId: params.providerId,
        outcomeGroup: params.outcomeGroup,
        source: params.source,
        from: params.from,
        to: params.to
      })}`
    ),
    getInternalModelTpsDistribution: (params: {
      providerId?: string | null;
      outcomeGroup?: InternalModelCallOutcomeGroup | null;
      source?: InternalModelCallSource | null;
      from?: string | null;
      to?: string | null;
    } = {}) => request<InternalModelThroughputDistribution>(
      `${internalModelObservabilityBase}/tps-distribution${query({
        providerId: params.providerId,
        outcomeGroup: params.outcomeGroup,
        source: params.source,
        from: params.from,
        to: params.to
      })}`
    ),
    getInternalModelProbeStatus: () => request<InternalModelProbeStatus[]>(
      `${internalModelObservabilityBase}/probe-status`
    ),
    triggerInternalModelProbe: (providerId?: string | null) =>
      request<InternalModelProbeRunResult>(`${internalModelObservabilityBase}/probe`, {
        method: "POST",
        body: JSON.stringify(providerId ? { providerId } : {})
      }),
    getRun: (runId: string) => routedRequest<Run>(agentPath(`/runs/${encodeURIComponent(runId)}`)),
    cancelRun: (runId: string) => routedRequest<Run>(agentPath(`/runs/${encodeURIComponent(runId)}/cancel`), { method: "POST" }),
    getRunDiff: (runId: string) => routedRequest<RunDiff>(agentPath(`/runs/${encodeURIComponent(runId)}/diff`)),
    acceptRunDiff: (runId: string) => routedRequest<RunDiffAction>(agentPath(`/runs/${encodeURIComponent(runId)}/diff/accept`), { method: "POST" }),
    rejectRunDiff: (runId: string) => routedRequest<RunDiffAction>(agentPath(`/runs/${encodeURIComponent(runId)}/diff/reject`), { method: "POST" }),
    listAgents: async (workspaceId?: string, init?: ExtraRequestInit) =>
      (await runtimeList(`${opencodeRuntimeBase}/agents${query({ workspaceId })}`, routedRequest, init)).map(toAgentInfo),
    listModels: async (workspaceId?: string) =>
      (await runtimeCatalogList(`${opencodeRuntimeBase}/models${query({ workspaceId })}`, workspaceId)).map(toModelInfo),
    listProviders: async (workspaceId?: string) =>
      (await runtimeCatalogList(`${opencodeRuntimeBase}/providers${query({ workspaceId })}`, workspaceId)).map(toProviderInfo),
    getConfig: (workspaceId?: string) => routedRequest<unknown>(`${opencodeRuntimeBase}/config${query({ workspaceId })}`),
    updateConfig: (payload: Record<string, unknown>, workspaceId?: string) =>
      routedRequest<unknown>(`${opencodeRuntimeBase}/config${query({ workspaceId })}`, { method: "PATCH", body: JSON.stringify(payload) }),
    disposeGlobal: () => routedRequest<unknown>(`${opencodeRuntimeBase}/global/dispose`, { method: "POST" }),
    listProviderAuth: (workspaceId?: string) => routedRequest<unknown>(`${opencodeRuntimeBase}/provider/auth${query({ workspaceId })}`),
    authorizeProviderOAuth: (providerId: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/provider/${encodeURIComponent(providerId)}/oauth/authorize`, payload, routedRequest),
    completeProviderOAuth: (providerId: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/provider/${encodeURIComponent(providerId)}/oauth/callback`, payload, routedRequest),
    setProviderAuth: (providerId: string, payload: Record<string, unknown>) =>
      routedRequest<unknown>(`${opencodeRuntimeBase}/auth/${encodeURIComponent(providerId)}`, { method: "PUT", body: JSON.stringify(payload) }),
    removeProviderAuth: (providerId: string) =>
      routedRequest<unknown>(`${opencodeRuntimeBase}/auth/${encodeURIComponent(providerId)}`, { method: "DELETE" }),
    listCommands: async (workspaceId?: string) =>
      (await runtimeList(`${opencodeRuntimeBase}/commands${query({ workspaceId })}`, routedRequest)).map(toCommandInfo),
    listReferences: (workspaceId?: string) => routedRequest<unknown>(`${opencodeRuntimeBase}/references${query({ workspaceId })}`),
    listRuntimeFiles: (workspaceId?: string, path = ".") => routedRequest<unknown>(`${opencodeRuntimeBase}/fs/list${query({ workspaceId, path })}`),
    findRuntimeFiles: (workspaceId?: string, search = "") => routedRequest<unknown>(`${opencodeRuntimeBase}/fs/find${query({ workspaceId, query: search })}`),
    readRuntimeFile: (workspaceId: string | undefined, path: string) =>
      routedRequest<unknown>(`${opencodeRuntimeBase}/fs/read${query({ workspaceId, path })}`),
    getVcsStatus: (workspaceId?: string) => routedRequest<unknown>(`${opencodeRuntimeBase}/vcs/status${query({ workspaceId })}`),
    getVcsDiff: (workspaceId?: string, mode = "git", context?: number) =>
      routedRequest<unknown>(`${opencodeRuntimeBase}/vcs/diff${query({ workspaceId, mode, context })}`),
    getVcsDiffFiles: async (workspaceId?: string, mode = "working", context?: number) => ({
      files: listFromRuntimeEnvelope(await routedRequest<unknown>(`${opencodeRuntimeBase}/vcs/diff${query({ workspaceId, mode, context })}`)).map(toRunDiffFile)
    }),
    getLspStatus: (workspaceId?: string) => routedRequest<unknown>(`${opencodeRuntimeBase}/lsp/status${query({ workspaceId })}`),
    getMcpStatus: (workspaceId?: string) => routedRequest<unknown>(`${opencodeRuntimeBase}/mcp/status${query({ workspaceId })}`),
    getMcpResources: async (workspaceId?: string) =>
      listFromRuntimeEnvelope(await routedRequest<unknown>(`${opencodeRuntimeBase}/mcp/resources${query({ workspaceId })}`)).map(toRuntimeResourceInfo),
    getMcpTools: async (workspaceId?: string, provider?: string, model?: string) =>
      listValuesFromRuntimeEnvelope(await routedRequest<unknown>(`${opencodeRuntimeBase}/mcp/tools${query({ workspaceId, provider, model })}`)).map((item) =>
        typeof item === "string" ? toRuntimeToolInfo({ id: item, name: item }) : toRuntimeToolInfo(item)
      ),
    startMcpAuth: (name: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/mcp/${encodeURIComponent(name)}/auth`, payload, routedRequest),
    completeMcpAuth: (name: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/mcp/${encodeURIComponent(name)}/auth/callback`, payload, routedRequest),
    authenticateMcp: (name: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/mcp/${encodeURIComponent(name)}/auth/authenticate`, payload, routedRequest),
    removeMcpAuth: (name: string) => routedRequest<unknown>(`${opencodeRuntimeBase}/mcp/${encodeURIComponent(name)}/auth`, { method: "DELETE" }),
    listWorktrees: (workspaceId?: string) => routedRequest<unknown>(`${opencodeRuntimeBase}/worktrees${query({ workspaceId })}`),
    createWorktree: (payload?: Record<string, unknown>) => postRuntime(`${opencodeRuntimeBase}/worktrees`, payload, routedRequest),
    removeWorktree: (payload?: Record<string, unknown>) =>
      routedRequest<unknown>(`${opencodeRuntimeBase}/worktrees`, { method: "DELETE", body: payload == null ? undefined : JSON.stringify(payload) }),
    resetWorktree: (payload?: Record<string, unknown>) => postRuntime(`${opencodeRuntimeBase}/worktrees/reset`, payload, routedRequest),
    getSessionChildren: (sessionId: string) => routedRequest<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/children`),
    getSessionTodo: async (sessionId: string) =>
      listFromRuntimeEnvelope(await routedRequest<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/todo`)).map(toTodoItem),
    getSessionDiff: async (sessionId: string, messageId?: string) => ({
      sessionId,
      messageId,
      files: listFromRuntimeEnvelope(
        await routedRequest<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/diff${query({ messageId })}`)
      ).map(toRunDiffFile)
    }) satisfies SessionDiff,
    abortSession: (sessionId: string) => routedRequest<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/abort`, { method: "POST" }),
    forkSession: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/fork`, payload, routedRequest),
    compactSession: (sessionId: string, payload: { providerID: string; modelID: string }) =>
      postRuntime(
        `${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/compact`,
        payload,
        routedRequest,
        { timeoutMs: NATIVE_SESSION_COMPACT_TIMEOUT_MS }
      ),
    revertSession: (sessionId: string, payload: { messageID: string }) =>
      postRuntime(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/revert`, payload, routedRequest),
    unrevertSession: (sessionId: string) =>
      postRuntime(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/unrevert`, undefined, routedRequest),
    runSessionCommand: (sessionId: string, payload?: Record<string, unknown>) =>
      postRuntime(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/command`, payload, routedRequest, { timeoutMs: 120000 }),
    runSessionShell: (sessionId: string, payload: { command: string; agent: string; model?: { providerID: string; modelID: string } }) =>
      postRuntime(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/shell`, payload, routedRequest, { timeoutMs: 120000 }),
    shareSession: (sessionId: string) =>
      postRuntime(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/share`, undefined, routedRequest),
    unshareSession: (sessionId: string) =>
      routedRequest<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/share`, { method: "DELETE" }),
    listSessionPermissions: async (sessionId: string) =>
      listFromRuntimeEnvelope(await routedRequest<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/permissions`)).map((item) =>
        toPermissionRequest(item, sessionId)
      ),
    replySessionPermission: (sessionId: string, requestId: string, payload: { decision?: "once" | "always" | "reject"; reply?: string; message?: string }) =>
      routedRequest<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/permissions/${encodeURIComponent(requestId)}/reply`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    listSessionQuestions: async (sessionId: string) =>
      listFromRuntimeEnvelope(await routedRequest<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/questions`)).map((item) =>
        toQuestionRequest(item, sessionId)
      ),
    replySessionQuestion: (sessionId: string, requestId: string, payload: { answers: unknown[] }) =>
      routedRequest<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/questions/${encodeURIComponent(requestId)}/reply`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    rejectSessionQuestion: (sessionId: string, requestId: string) =>
      routedRequest<unknown>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/questions/${encodeURIComponent(requestId)}/reject`, {
        method: "POST"
      }),
    createTerminalTicket: (sessionId: string, payload: TerminalTicketRequest = {}) =>
      routedRequest<TerminalTicketResponse>(`${opencodeRuntimeBase}/sessions/${encodeURIComponent(sessionId)}/terminal/tickets`, {
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

    // ---- 通用长期记忆 API ----

    getQaMemoryAvailability: () =>
      request<{ enabled: boolean }>(`${memoryBase}/availability`),
    listPersonalMemories: (params: {
      applicationId?: string;
      status?: MemoryStatus;
      page?: number;
      size?: number;
    } = {}) => request<PageResponse<MemoryView>>(`${memoryBase}/personal${query(params)}`),
    createPersonalMemory: (payload: {
      scope: Extract<MemoryScope, "PERSONAL_GLOBAL" | "PERSONAL_APPLICATION">;
      applicationId?: string | null;
      content: string;
    }) => request<MemoryView>(`${memoryBase}/personal`, {
      method: "POST",
      body: JSON.stringify(payload)
    }),
    listTeamMemories: (params: {
      applicationId?: string;
      status?: MemoryStatus;
      page?: number;
      size?: number;
    } = {}) => request<PageResponse<MemoryView>>(`${memoryBase}/team${query(params)}`),
    createTeamMemoryProposal: (payload: {
      applicationId: string;
      content: string;
      sourceMemoryId?: string;
    }) => request<MemoryView>(`${memoryBase}/team/proposals`, {
      method: "POST",
      body: JSON.stringify(payload)
    }),
    reviewTeamMemory: (memoryId: string, payload: {
      decision: "APPROVE" | "REJECT";
      comment?: string;
      expectedVersion: number;
    }) => request<MemoryView>(`${memoryBase}/team/${encodeURIComponent(memoryId)}/reviews`, {
      method: "POST",
      body: JSON.stringify(payload)
    }),
    getQaMemory: (memoryId: string) =>
      request<MemoryView>(`${memoryBase}/memories/${encodeURIComponent(memoryId)}`),
    updateQaMemory: (memoryId: string, payload: {
      content?: string;
      expectedVersion: number;
    }) => request<MemoryView>(`${memoryBase}/memories/${encodeURIComponent(memoryId)}`, {
      method: "PATCH",
      body: JSON.stringify(payload)
    }),
    promotePersonalMemoryGlobal: (memoryId: string, expectedVersion: number) =>
      request<MemoryView>(`${memoryBase}/personal/${encodeURIComponent(memoryId)}/promote-global`, {
        method: "POST",
        body: JSON.stringify({ expectedVersion })
      }),
    pausePersonalMemory: (memoryId: string, expectedVersion: number) =>
      request<MemoryView>(`${memoryBase}/personal/${encodeURIComponent(memoryId)}/pause`, {
        method: "POST",
        body: JSON.stringify({ expectedVersion })
      }),
    archiveQaMemory: (memoryId: string, expectedVersion: number) =>
      request<MemoryView>(
        `${memoryBase}/memories/${encodeURIComponent(memoryId)}${query({ expectedVersion })}`,
        { method: "DELETE" }
      ),
    listQaMemoryEvidence: (memoryId: string) =>
      request<MemoryEvidenceView[]>(`${memoryBase}/memories/${encodeURIComponent(memoryId)}/evidence`),
    queryQaMemoryRunUsage: (runIds: string[]) =>
      request<MemoryUsageView[]>(`${memoryBase}/run-usage/query`, {
        method: "POST",
        body: JSON.stringify({ runIds })
      }),
    createMemorySkillProposal: (payload: {
      memoryId: string;
      applicationId: string;
      title?: string;
    }) => request<MemorySkillProposalView>(`${memoryBase}/skill-proposals`, {
      method: "POST",
      body: JSON.stringify(payload)
    }),
    listMemorySkillProposals: (params: { applicationId?: string; page?: number; size?: number } = {}) =>
      request<PageResponse<MemorySkillProposalView>>(`${memoryBase}/skill-proposals${query(params)}`),
    updateMemorySkillProposal: (proposalId: string, payload: {
      title?: string;
      skillMdDraft: string;
      expectedVersion: number;
    }) => request<MemorySkillProposalView>(
      `${memoryBase}/skill-proposals/${encodeURIComponent(proposalId)}`,
      { method: "PATCH", body: JSON.stringify(payload) }
    ),
    reviewMemorySkillProposal: (proposalId: string, decision: "APPROVE" | "REJECT", expectedVersion: number) =>
      request<MemorySkillProposalView>(
        `${memoryBase}/skill-proposals/${encodeURIComponent(proposalId)}/reviews`,
        { method: "POST", body: JSON.stringify({ decision, expectedVersion }) }
      ),
    linkPublishedMemorySkill: (proposalId: string, publishedAssetId: string, expectedVersion: number) =>
      request<MemorySkillProposalView>(
        `${memoryBase}/skill-proposals/${encodeURIComponent(proposalId)}/published-asset`,
        { method: "POST", body: JSON.stringify({ publishedAssetId, expectedVersion }) }
      ),
    archiveMemorySkillProposal: (proposalId: string, expectedVersion: number) =>
      request<MemorySkillProposalView>(
        `${memoryBase}/skill-proposals/${encodeURIComponent(proposalId)}${query({ expectedVersion })}`,
        { method: "DELETE" }
      ),

    getQaMemoryAdminHealth: () => request<MemoryAdminHealth>(`${memoryAdminBase}/health`),
    getQaMemorySettings: () => request<MemorySettingsView>(`${memoryAdminBase}/settings`),
    updateQaMemorySettings: (payload: {
      primaryChatModelId?: string | null;
      primaryEmbeddingModelId?: string | null;
      expectedVersion: number;
    }) => request<MemorySettingsView>(`${memoryAdminBase}/settings`, {
      method: "PATCH",
      body: JSON.stringify(payload)
    }),
    listQaMemoryWhitelist: (page = 1, size = 50) =>
      request<PageResponse<MemoryWhitelistView>>(`${memoryAdminBase}/whitelist${query({ page, size })}`),
    enableQaMemoryUser: (userId: string) =>
      request<MemoryWhitelistView>(`${memoryAdminBase}/whitelist`, {
        method: "POST",
        body: JSON.stringify({ userId })
      }),
    disableQaMemoryUser: (userId: string) =>
      request<void>(`${memoryAdminBase}/whitelist/${encodeURIComponent(userId)}`, { method: "DELETE" }),

    // ---- 工具盒子 API ----

    /** 获取固定版本、可完全离线运行的工具目录及热门排名。 */
    getToolboxCatalog: () => request<ToolboxCatalog>(`${toolboxBase}/tools`),

    /** 点击上报失败由调用界面静默处理，不参与工具链接的导航控制。 */
    recordToolboxClick: (toolId: string, eventId: string) =>
      request<ToolboxClickResult>(`${toolboxBase}/tools/${encodeURIComponent(toolId)}/clicks`, {
        method: "POST",
        body: JSON.stringify({ eventId })
      }),

    /** 通过平台后端受控调用生产 TCDS，浏览器不直连内网 HTTP 地址。 */
    getTcdsTaskTypes: () =>
      request<TcdsTaskTypeOption[]>(`${tcdsIntegrationBase}/task-types`),

    /** 通过平台后端受控调用生产 TCDS，浏览器不直连内网 HTTP 地址。 */
    maintainTcdsTestCases: (payload: TcdsTestCaseMaintenancePayload) =>
      request<void>(`${tcdsIntegrationBase}/test-cases`, {
        method: "POST",
        body: JSON.stringify(payload)
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

    /** 按关键字、角色、组织及部门组合分页查询用户；字符串入参保留旧调用兼容。 */
    listUsers: (paramsOrKeyword: UserManagementQuery | string = {}, page = 1, size = 50) => {
      const params: UserManagementQuery = typeof paramsOrKeyword === "string"
        ? { keyword: paramsOrKeyword, page, size }
        : paramsOrKeyword;
      return request<PageResponse<UserManagementUser>>(`${systemManagementBase}/users${query({
        keyword: params.keyword,
        role: params.role,
        organization: params.organization,
        rdDepartment: params.rdDepartment,
        department: params.department,
        page: params.page ?? 1,
        size: params.size ?? 50
      })}`);
    },
    /** 创建测试用户，密码由后端注入默认值 123456。 */
    createUser: (payload: CreateUserPayload) =>
      request<UserManagementUser>(`${systemManagementBase}/users`, { method: "POST", body: JSON.stringify(payload) }),
    /** 手工修正用户名，统一认证号、角色及业务关联保持不变（仅 SUPER_ADMIN）。 */
    updateUsername: (userId: string, payload: UpdateUsernamePayload) =>
      request<UserManagementUser>(`${systemManagementBase}/users/${encodeURIComponent(userId)}/username`, {
        method: "PUT",
        body: JSON.stringify(payload)
      }),
    /** 调整指定用户的全局角色（仅 SUPER_ADMIN）。 */
    updateUserRole: (userId: string, payload: UpdateUserRolePayload) =>
      request<UserManagementUser>(`${systemManagementBase}/users/${encodeURIComponent(userId)}/roles`, {
        method: "PUT",
        body: JSON.stringify(payload)
      }),
    /** 一次提交显式角色项，或按筛选快照更新全部匹配用户（仅 SUPER_ADMIN）。 */
    updateUserRoles: (payload: UpdateUserRolesPayload) =>
      request<UpdateUserRolesResult>(`${systemManagementBase}/users/batch-roles`, {
        method: "PUT",
        body: JSON.stringify(payload)
      }),
    /** 删除单个未承载业务资产的用户（仅 SUPER_ADMIN）。 */
    deleteUser: (userId: string) =>
      request<DeleteUsersResult>(`${systemManagementBase}/users/${encodeURIComponent(userId)}`, {
        method: "DELETE"
      }),
    /** 原子批量删除未承载业务资产的用户（仅 SUPER_ADMIN）。 */
    deleteUsers: (payload: UserIdsPayload) =>
      request<DeleteUsersResult>(`${systemManagementBase}/users/batch-delete`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    /** 从 TCDS 原位同步单个用户姓名和部门，保留既有业务关联。 */
    syncUserFromTcds: (userId: string) =>
      request<SyncUsersFromTcdsResult>(
        `${systemManagementBase}/users/${encodeURIComponent(userId)}/tcds-sync`,
        { method: "POST" }
      ),
    /** 从 TCDS 原位批量同步用户姓名和部门。 */
    syncUsersFromTcds: (payload: UserIdsPayload) =>
      request<SyncUsersFromTcdsResult>(`${systemManagementBase}/users/tcds-sync`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    /** 查询可选角色列表，供新增用户下拉选择。 */
    listRoles: () => request<RoleOption[]>(`${systemManagementBase}/roles`),

    // ---- 超级管理员问题排查只读 API ----

    issueSupportAccessGrant: (payload: SupportAccessGrantRequest) =>
      request<SupportAccessGrant>(`${systemManagementBase}/support-access/grants`, {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    getSupportAccessIncidentSuggestion: () =>
      request<SupportAccessIncidentSuggestion>(
        // 滚动发布期间继续调用兼容别名，使新前端也能与尚未升级的旧后端共同运行。
        `${systemManagementBase}/support-access/grants/recent-incident`,
        { cache: "no-store" }
      ),
    revokeSupportAccessGrant: async (grantId: string, grantToken: string) => {
      try {
        return await request<void>(
          `${systemManagementBase}/support-access/grants/${encodeURIComponent(grantId)}`,
          { method: "DELETE", headers: supportHeaders(grantToken) }
        );
      } finally {
        closeSupportFileSockets(grantId);
      }
    },
    closeSupportAccessConnections: (grantId?: string) => closeSupportFileSockets(grantId),
    selectSupportAccessTarget: (grantToken: string, targetUserId: string) =>
      request<SupportAccessTarget>(
        `${systemManagementBase}/support-access/targets/${encodeURIComponent(targetUserId)}/selections`,
        { method: "POST", headers: supportHeaders(grantToken) }
      ),
    listSupportAccessSessions: (
      grantToken: string,
      targetUserId: string,
      params: { q?: string; includeArchived?: boolean; page?: number; size?: number } = {}
    ) => request<PageResponse<Session>>(
      `${systemManagementBase}/support-access/targets/${encodeURIComponent(targetUserId)}/sessions${query({
        q: params.q,
        includeArchived: params.includeArchived || undefined,
        page: params.page ?? 1,
        size: params.size ?? 30
      })}`,
      { headers: supportHeaders(grantToken) }
    ),
    getSupportAccessSessionTreeMessages: (
      grantToken: string,
      targetUserId: string,
      sessionId: string,
      includeArchived = false
    ) =>
      request<SessionTreeMessagesResponse>(
        `${systemManagementBase}/support-access/targets/${encodeURIComponent(targetUserId)}`
          + `/sessions/${encodeURIComponent(sessionId)}/session-tree/messages${query({
            includeArchived: includeArchived || undefined
          })}`,
        { headers: supportHeaders(grantToken) }
      ),
    listSupportAccessWorkspaces: (
      grantToken: string,
      targetUserId: string,
      page = 1,
      size = 30
    ) => request<PageResponse<Workspace>>(
      `${systemManagementBase}/support-access/targets/${encodeURIComponent(targetUserId)}`
        + `/workspaces${query({ page, size })}`,
      { headers: supportHeaders(grantToken) }
    ),
    listSupportAccessAuditEvents: (params: SupportAccessAuditQuery = {}) =>
      request<PageResponse<SupportAccessAuditEvent>>(
        `${systemManagementBase}/support-access/audit-events${query({
          actorUserId: params.actorUserId,
          targetUserId: params.targetUserId,
          incidentId: params.incidentId,
          outcome: params.outcome,
          page: params.page ?? 1,
          size: params.size ?? 50
        })}`
      ),
    listSupportWorkspaceFiles: async (
      grant: Pick<SupportAccessGrant, "grantId" | "grantToken">,
      targetUserId: string,
      workspaceId: string,
      path = ""
    ) => {
      const entries = await supportWorkspaceFileRpc<BackendFileTreeEntry[]>(
        grant, targetUserId, workspaceId, "workspace.list", { path }
      );
      return entries.map((entry) => ({
        path: entry.path,
        name: entry.name,
        type: entry.directory ? "directory" : "file",
        size: entry.size,
        modifiedAt: entry.lastModifiedAt
      })) satisfies FileTreeEntry[];
    },
    searchSupportWorkspaceFiles: async (
      grant: Pick<SupportAccessGrant, "grantId" | "grantToken">,
      targetUserId: string,
      workspaceId: string,
      searchQuery: string
    ) => {
      const results = await supportWorkspaceFileRpc<BackendFileSearchResult[]>(
        grant, targetUserId, workspaceId, "workspace.search", { query: searchQuery }
      );
      return results.map((result) => ({
        path: result.path,
        name: result.name,
        directory: result.directory,
        size: result.size,
        modifiedAt: result.lastModifiedAt
      })) satisfies FileSearchResult[];
    },
    readSupportWorkspaceFile: async (
      grant: Pick<SupportAccessGrant, "grantId" | "grantToken">,
      targetUserId: string,
      workspaceId: string,
      path: string
    ) => {
      const data = await supportWorkspaceFileRpc<BackendFileContent>(
        grant, targetUserId, workspaceId, "workspace.read", { path }, true
      );
      return {
        path: data.path || path,
        content: typeof data.content === "string" ? data.content : "",
        encoding: "utf-8",
        size: data.size,
        readonly: true
      } satisfies FileContent;
    },
    readSupportWorkspaceFilePreviewChunk: async (
      grant: Pick<SupportAccessGrant, "grantId" | "grantToken">,
      targetUserId: string,
      workspaceId: string,
      path: string,
      preview: FilePreviewChunkRequest
    ) => mapFilePreviewChunk(await supportWorkspaceFileRpc<BackendFilePreviewChunk>(
      grant, targetUserId, workspaceId, "workspace.read.chunk", { path, ...preview }, true
    )),
    readSupportWorkspaceFileBinaryChunk: async (
      grant: Pick<SupportAccessGrant, "grantId" | "grantToken">,
      targetUserId: string,
      workspaceId: string,
      path: string,
      binaryRequest: FileBinaryChunkRequest
    ) => mapFileBinaryChunk(await supportWorkspaceFileRpc<BackendFileBinaryChunk>(
      grant, targetUserId, workspaceId, "workspace.read.binary.chunk", { path, ...binaryRequest }, true
    )),

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
      routedRequest<CreateWorkspaceAcceptedResponse>(`${configurationBase}/applications/${encodeURIComponent(appId)}/workspaces`, {
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
    updateApplicationWorkspace: (appId: string, workspaceId: string, payload: UpdateApplicationWorkspacePayload) =>
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
  displayName?: string;
  displayNameEn?: string;
  directory: boolean;
  size: number;
  lastModifiedAt?: string;
};

type BackendFileContent = {
  path: string;
  content: string;
  size: number;
};

type BackendFilePreviewChunk = {
  path: string;
  content: string;
  offset: number;
  nextOffset: number;
  size: number;
  eof: boolean;
  warningThresholdBytes: number;
  lastModifiedMillis: number;
};

type BackendFileBinaryChunk = {
  path: string;
  contentBase64: string;
  offset: number;
  nextOffset: number;
  size: number;
  eof: boolean;
  lastModifiedMillis: number;
};

type BackendWorkspaceViewEntry = {
  id: string;
  path: string;
  name: string;
  directory: boolean;
  size: number;
  lastModifiedAt?: string;
  locator: WorkspaceViewLocator;
  source: "WORKSPACE" | "REFERENCE" | "AUTOMATION_REFERENCE" | "MIXED";
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
  source: "WORKSPACE" | "REFERENCE" | "AUTOMATION_REFERENCE" | "MIXED";
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
    displayName: entry.displayName,
    displayNameEn: entry.displayNameEn,
    type: entry.directory ? "directory" : "file",
    size: entry.size,
    modifiedAt: entry.lastModifiedAt
  };
}

function mapFilePreviewChunk(chunk: BackendFilePreviewChunk): FilePreviewChunk {
  return {
    path: chunk.path,
    content: typeof chunk.content === "string" ? chunk.content : "",
    offset: chunk.offset,
    nextOffset: chunk.nextOffset,
    size: chunk.size,
    eof: chunk.eof === true,
    warningThresholdBytes: chunk.warningThresholdBytes,
    lastModifiedMillis: chunk.lastModifiedMillis
  };
}

function mapFileBinaryChunk(chunk: BackendFileBinaryChunk): FileBinaryChunk {
  return {
    path: chunk.path,
    contentBase64: typeof chunk.contentBase64 === "string" ? chunk.contentBase64 : "",
    offset: chunk.offset,
    nextOffset: chunk.nextOffset,
    size: chunk.size,
    eof: chunk.eof === true,
    lastModifiedMillis: chunk.lastModifiedMillis
  };
}

type FileUploadRpc = (op: string, params: Record<string, unknown>) => Promise<unknown>;
type FileUploadBeginResult = {
  uploadId: string;
  chunkBytes: number;
  totalBytes: number;
};
type FileUploadChunkResult = FileUploadProgress;
type FileUploadCompleteResult = { size: number };

const MAX_CLIENT_UPLOAD_CHUNK_BYTES = 4 * 1024 * 1024;

/**
 * 只把当前 Blob.slice 读进内存并编码，文件总大小不会决定单条消息或浏览器瞬时内存占用。
 */
async function uploadBlobInChunks(
  file: Blob,
  path: string,
  rpc: FileUploadRpc,
  operationPrefix: "workspace.upload" | "agent-config.upload",
  onProgress?: FileUploadProgressHandler
): Promise<void> {
  const begin = requireUploadBeginResult(
    await rpc(`${operationPrefix}.begin`, { path, size: file.size }),
    file.size
  );
  notifyUploadProgress(onProgress, { uploadedBytes: 0, totalBytes: file.size });
  try {
    let offset = 0;
    let index = 0;
    while (offset < file.size) {
      const end = Math.min(offset + begin.chunkBytes, file.size);
      const chunk = file.slice(offset, end);
      const contentBase64 = await blobToBase64(chunk);
      const progress = requireUploadChunkResult(
        await rpc(`${operationPrefix}.chunk`, {
          uploadId: begin.uploadId,
          index,
          contentBase64
        }),
        end,
        file.size
      );
      offset = end;
      index += 1;
      notifyUploadProgress(onProgress, progress);
    }
    requireUploadCompleteResult(
      await rpc(`${operationPrefix}.complete`, { uploadId: begin.uploadId }),
      file.size
    );
  } catch (error) {
    // 传输关闭时服务端会随连接清理临时文件；此时不要重连并向新连接发送无效 abort。
    if (!(error instanceof WorkspaceFileTransportError)) {
      try {
        await rpc(`${operationPrefix}.abort`, { uploadId: begin.uploadId });
      } catch {
        // 保留原始上传错误；服务端对失败分片和连接关闭都还有兜底清理。
      }
    }
    throw error;
  }
}

function requireUploadBeginResult(value: unknown, expectedBytes: number): FileUploadBeginResult {
  const result = value as Partial<FileUploadBeginResult> | null;
  if (!result
    || typeof result.uploadId !== "string"
    || !result.uploadId
    || !Number.isInteger(result.chunkBytes)
    || (result.chunkBytes ?? 0) < 1
    || (result.chunkBytes ?? 0) > MAX_CLIENT_UPLOAD_CHUNK_BYTES
    || result.totalBytes !== expectedBytes) {
    throw uploadProtocolError("服务端返回的上传会话无效");
  }
  return result as FileUploadBeginResult;
}

function requireUploadChunkResult(
  value: unknown,
  expectedUploadedBytes: number,
  expectedTotalBytes: number
): FileUploadChunkResult {
  const result = value as Partial<FileUploadChunkResult> | null;
  if (!result
    || result.uploadedBytes !== expectedUploadedBytes
    || result.totalBytes !== expectedTotalBytes) {
    throw uploadProtocolError("服务端返回的上传进度无效");
  }
  return result as FileUploadChunkResult;
}

function requireUploadCompleteResult(value: unknown, expectedBytes: number): FileUploadCompleteResult {
  const result = value as Partial<FileUploadCompleteResult> | null;
  if (!result || result.size !== expectedBytes) {
    throw uploadProtocolError("服务端返回的上传完成结果无效");
  }
  return result as FileUploadCompleteResult;
}

function uploadProtocolError(message: string): BackendApiError {
  return new BackendApiError(500, {
    success: false,
    code: "FILE_UPLOAD_PROTOCOL_ERROR",
    message,
    traceId: "",
    retryable: false,
    details: {}
  });
}

async function blobToBase64(blob: Blob): Promise<string> {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  let binary = "";
  const stringChunkBytes = 32 * 1024;
  for (let offset = 0; offset < bytes.length; offset += stringChunkBytes) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + stringChunkBytes));
  }
  return btoa(binary);
}

function notifyUploadProgress(
  handler: FileUploadProgressHandler | undefined,
  progress: FileUploadProgress
) {
  try {
    handler?.(progress);
  } catch {
    // 进度展示是旁路能力，不能因为 UI 回调异常破坏已经开始的文件传输。
  }
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

  request<T>(op: string, params: Record<string, unknown>, timeoutMs = 30000): Promise<T> {
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
      }, timeoutMs);
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
  const normalizedWebSocketUrl = webSocketUrl.trim();
  if (normalizedWebSocketUrl.startsWith("ws://") || normalizedWebSocketUrl.startsWith("wss://")) {
    return normalizedWebSocketUrl;
  }
  const normalizedBaseUrl = baseUrl.trim().replace(/\/$/, "");
  let absolute = normalizedWebSocketUrl;
  if (!normalizedWebSocketUrl.startsWith("http://") && !normalizedWebSocketUrl.startsWith("https://")) {
    if (normalizedBaseUrl) {
      absolute = `${normalizedBaseUrl}${normalizedWebSocketUrl.startsWith("/") ? "" : "/"}${normalizedWebSocketUrl}`;
    } else {
      // 企业同源包会把 HTTP API 基址编译为空；WebSocket 构造器要求绝对地址，因此必须按当前页面补全 origin。
      const pageUrl = typeof globalThis.location?.href === "string" ? globalThis.location.href.trim() : "";
      if (!pageUrl) {
        throw new Error("Cannot resolve a relative WebSocket URL without an API base URL or browser location");
      }
      absolute = new URL(normalizedWebSocketUrl, pageUrl).toString();
    }
  }
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

function providerAllowlistFromConfig(value: unknown): Set<string> | undefined {
  const config = record(value);
  const raw = config?.enabled_providers ?? config?.enabledProviders;
  if (!Array.isArray(raw)) return undefined;
  const providerIds = raw
    .filter((item): item is string => typeof item === "string")
    .map((item) => item.trim())
    .filter(Boolean);
  return providerIds.length > 0 ? new Set(providerIds) : undefined;
}

function runtimeCatalogProviderId(value: Record<string, unknown>): string | undefined {
  return text(value.providerId)
    ?? text(value.providerID)
    ?? text(record(value.provider)?.id)
    ?? text(value.id);
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
  const limit = record(value.limit);
  const capabilities = record(value.capabilities);
  const inputCapabilities = record(capabilities?.input);
  return compactObject({
    id,
    providerId: text(value.providerId) ?? text(value.providerID) ?? text(record(value.provider)?.id),
    name: text(value.name) ?? id,
    contextLimit: number(value.contextLimit) ?? number(value.context) ?? number(limit?.context),
    outputLimit: number(value.outputLimit) ?? number(limit?.output),
    free: typeof value.free === "boolean" ? value.free : undefined,
    defaultModel: typeof value.defaultModel === "boolean" ? value.defaultModel : undefined,
    variants,
    capabilities: capabilities
      ? compactObject({
          attachment: typeof capabilities.attachment === "boolean" ? capabilities.attachment : undefined,
          input: inputCapabilities
            ? compactObject({
                text: typeof inputCapabilities.text === "boolean" ? inputCapabilities.text : undefined,
                audio: typeof inputCapabilities.audio === "boolean" ? inputCapabilities.audio : undefined,
                image: typeof inputCapabilities.image === "boolean" ? inputCapabilities.image : undefined,
                video: typeof inputCapabilities.video === "boolean" ? inputCapabilities.video : undefined,
                pdf: typeof inputCapabilities.pdf === "boolean" ? inputCapabilities.pdf : undefined
              })
            : undefined
        })
      : undefined
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
      ? Object.entries(rawModels).map(([id, model]) => toModelInfo({ id, providerId, ...(record(model) ?? {}) }))
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
  const patterns = permissionPatterns(value.patterns, value.pattern);
  return compactObject({
    requestId,
    // permission 列表同样已经由平台 session 路由；远端 sessionID 只用于 OpenCode 内部，
    // 不能让历史会话的 dock 按它筛选，否则待授权项会消失。
    sessionId: fallbackSessionId,
    type: text(value.type) ?? text(value.permission) ?? text(value.action) ?? "permission",
    title: text(value.title),
    description: text(value.description),
    patterns: patterns.length > 0 ? patterns : undefined,
    pattern: text(value.pattern),
    createdAt: text(value.createdAt) ?? text(record(value.time)?.created) ?? new Date(0).toISOString()
  });
}

function permissionPatterns(value: unknown, fallback: unknown): string[] {
  const source = Array.isArray(value) ? value : [fallback];
  const seen = new Set<string>();
  return source.reduce<string[]>((patterns, item) => {
    const pattern = text(item)?.trim();
    if (!pattern || seen.has(pattern)) return patterns;
    seen.add(pattern);
    patterns.push(pattern);
    return patterns;
  }, []);
}

function normalizeSessionRuntimeStateSummary(summary: SessionRuntimeStateSummary): SessionRuntimeStateSummary {
  return {
    ...summary,
    permissionCount: typeof summary.permissionCount === "number"
      ? summary.permissionCount
      : summary.sessions.filter((item) => item.attention === "PERMISSION").length
  };
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
    return JSON.stringify(redactObservedSensitiveData(JSON.parse(raw)));
  } catch {
    return redactObservedSensitiveText(raw);
  }
}

const OBSERVED_SENSITIVE_KEYS = new Set([
  "apikey",
  "authorization",
  "accesstoken",
  "authtoken",
  "cookie",
  "contexttoken",
  "ciphertext",
  "clientkey",
  "encryptedapikey",
  "granttoken",
  "password",
  "refreshtoken",
  "secret",
  "sessiondigest",
  "supportaccessgrant",
  "setcookie",
  "ticket",
  "token",
  "tokenvalue"
]);

function redactObservedSensitiveText(raw: string): string {
  const keyPattern = /(["']?)\b(?:api[-_]?key|authorization|access[-_]?token|auth[-_]?token|ciphertext|client[-_]?key|cookie|context[-_]?token|encrypted[-_]?api[-_]?key|grant[-_]?token|password|refresh[-_]?token|secret|session[-_]?digest|set-cookie|support[-_]?access[-_]?grant|ticket|token[-_]?value|token)\b\1\s*[:=]\s*/gi;
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

function redactObservedSensitiveData(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(redactObservedSensitiveData);
  }
  if (!value || typeof value !== "object") {
    return value;
  }
  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>).map(([key, item]) => [
      key,
      OBSERVED_SENSITIVE_KEYS.has(key.toLowerCase().replace(/[-_]/g, ""))
        ? "[REDACTED]"
        : redactObservedSensitiveData(item)
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

function isAppSourceProgressEvent(value: unknown): value is AppSourceProgressEvent {
  const candidate = record(value);
  if (candidate === undefined || typeof candidate.type !== "string") {
    return false;
  }
  if (candidate.type === "failed") {
    if (candidate.status !== "FAILED"
      || nonBlankText(candidate.errorCode) === undefined
      || nonBlankText(candidate.errorMessage) === undefined) {
      return false;
    }
    if (candidate.operation === undefined || candidate.operation === null) {
      return isOptionalNonBlankText(candidate.operationId)
        && isOptionalNonBlankText(candidate.traceId);
    }
    const operation = isAppSourceOperation(candidate.operation) ? candidate.operation : undefined;
    return operation !== undefined
      && operation.status === "FAILED"
      && nonBlankText(candidate.operationId) !== undefined
      && nonBlankText(candidate.traceId) !== undefined
      && candidate.operationId === operation.operationId
      && candidate.traceId === operation.traceId;
  }
  if (!["snapshot", "step", "completed"].includes(candidate.type)) {
    return false;
  }
  const operation = isAppSourceOperation(candidate.operation) ? candidate.operation : undefined;
  if (nonBlankText(candidate.operationId) === undefined
    || nonBlankText(candidate.traceId) === undefined
    || operation === undefined
    || candidate.operationId !== operation.operationId
    || candidate.traceId !== operation.traceId) {
    return false;
  }
  if (candidate.type === "step") {
    return operation.status === "PENDING" || operation.status === "RUNNING";
  }
  if (candidate.type === "completed") {
    return operation.status === "SUCCEEDED" || operation.status === "PARTIAL_FAILED";
  }
  return true;
}

function isAppSourceOperation(value: unknown): value is AppSourceOperation {
  const candidate = record(value);
  return candidate !== undefined
    && nonBlankText(candidate.operationId) !== undefined
    && nonBlankText(candidate.appId) !== undefined
    && nonBlankText(candidate.repositoryId) !== undefined
    && (candidate.sourceGeneration === undefined
      || candidate.sourceGeneration === null
      || positiveInteger(candidate.sourceGeneration))
    && positiveInteger(candidate.targetGeneration)
    && nonBlankText(candidate.operationType) !== undefined
    && ["PENDING", "RUNNING", "SUCCEEDED", "PARTIAL_FAILED", "FAILED"].includes(String(candidate.status))
    && (candidate.purpose === undefined
      || candidate.purpose === null
      || ["TEAM", "PERSONAL"].includes(String(candidate.purpose)))
    && isOptionalNonBlankText(candidate.branch)
    && isOptionalNonBlankText(candidate.targetCommit)
    && isOptionalNonBlankText(candidate.expiresAt)
    && nonBlankText(candidate.traceId) !== undefined
    && nonBlankText(candidate.acceptedAt) !== undefined
    && isOptionalNonBlankText(candidate.completedAt)
    && isArrayOf(candidate.selectedPaths, isAppSourceSelectedPath)
    && isArrayOf(candidate.globalSteps, isAppSourceStepSummary)
    && isArrayOf(candidate.serverSummaries, isAppSourceServerSummary);
}

function isAppSourceSelectedPath(value: unknown) {
  const candidate = record(value);
  return candidate !== undefined
    && nonBlankText(candidate.path) !== undefined
    && ["FILE", "DIRECTORY"].includes(String(candidate.type));
}

function isAppSourceStepSummary(value: unknown) {
  const candidate = record(value);
  return candidate !== undefined
    && nonBlankText(candidate.stepCode) !== undefined
    && nonNegativeInteger(candidate.sequence)
    && nonBlankText(candidate.status) !== undefined
    && isOptionalNonBlankText(candidate.safeSummary)
    && isOptionalNonBlankText(candidate.startedAt)
    && isOptionalNonBlankText(candidate.completedAt)
    && (candidate.elapsedMillis === undefined
      || candidate.elapsedMillis === null
      || nonNegativeInteger(candidate.elapsedMillis))
    && nonBlankText(candidate.updatedAt) !== undefined;
}

function isAppSourceServerSummary(value: unknown) {
  const candidate = record(value);
  return candidate !== undefined
    && nonBlankText(candidate.linuxServerId) !== undefined
    && isOptionalNonBlankText(candidate.replicaStatus)
    && nonNegativeInteger(candidate.attemptCount)
    && isOptionalNonBlankText(candidate.safeErrorCode)
    && isOptionalNonBlankText(candidate.safeErrorMessage)
    && isOptionalNonBlankText(candidate.targetCommit)
    && isArrayOf(candidate.steps, isAppSourceStepSummary);
}

function isArrayOf(value: unknown, predicate: (item: unknown) => boolean) {
  return Array.isArray(value) && value.every(predicate);
}

function positiveInteger(value: unknown) {
  return typeof value === "number" && Number.isInteger(value) && value > 0;
}

function nonNegativeInteger(value: unknown) {
  return typeof value === "number" && Number.isInteger(value) && value >= 0;
}

function appSourceClientFailure(
  operationId: string,
  errorCode: string,
  errorMessage: string
): AppSourceProgressEvent {
  return {
    type: "failed",
    operationId,
    status: "FAILED",
    errorCode,
    errorMessage
  };
}

/** operationId 同时作为 REST/WS 路径段，必须与后端值对象保持同一套边界。 */
function normalizeAppSourceOperationId(operationId: string) {
  const normalized = operationId.trim();
  if (normalized.length === 0
    || normalized.length > 128
    || normalized === "."
    || normalized === ".."
    || /[\u0000-\u001f\u007f-\u009f/\\]/u.test(normalized)) {
    throw new Error("operationId 格式无效");
  }
  return normalized;
}

function appSourceOperationPathSegment(operationId: string) {
  return encodeURIComponent(normalizeAppSourceOperationId(operationId));
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

function nonBlankText(value: unknown) {
  return typeof value === "string" && value.trim().length > 0 ? value : undefined;
}

function isOptionalNonBlankText(value: unknown) {
  return value === undefined || value === null || nonBlankText(value) !== undefined;
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
