export type ApiSuccess<T> = {
  success: true;
  traceId: string;
  data: T;
};

export type ApiFailure = {
  success: false;
  traceId: string;
  code: string;
  message: string;
  retryable?: boolean;
  details?: Record<string, unknown>;
};

export type ApiResponse<T> = ApiSuccess<T> | ApiFailure;

export type PageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  total: number;
};

export type Workspace = {
  workspaceId: string;
  name: string;
  rootPath: string;
  status: string;
  linuxServerId?: string | null;
  createdAt: string;
  updatedAt: string;
  /**
   * 该工作区所属的托管应用 id。
   * 仅在「最近工作区」相关接口（`/recent-workspace`、`/applications/{appId}/recent-workspace`）中填充，
   * 用于重新登录或换电脑登录时还原上次所在的应用上下文；其他接口依旧返回 `null`。
   */
  appId?: string | null;
  /**
   * 该工作区所属的应用版本 id（`ApplicationWorkspaceVersion.versionId`）。
   * 仅在「最近工作区」相关接口中填充，便于前端在重新登录时直接把左下角"切换工作空间"按钮的 `selectedVersionId`
   * 设回上次的版本，从而立即显示当前所在的工作区名称而无需等待 `versionsByTemplateId` 异步加载完成。
   * 工作区不属于任何应用版本（例如历史手动注册或超级管理员服务器工作空间）时为 `null`。
   */
  versionId?: string | null;
  /**
   * 该工作区所属的应用工作空间模板 id（`ApplicationWorkspaceVersion.applicationWorkspaceId`）。
   * 仅在「最近工作区」相关接口中填充，用于在重新登录时按需触发该模板 `versions` 的预加载，
   * 避免 `WorkbenchFooter.selectedTemplate` 因为模板未展开而找不到匹配、按钮降级为「切换工作空间」。
   * 工作区不属于任何应用版本时为 `null`。
   */
  applicationWorkspaceId?: string | null;
};

export type WorkspaceDirectoryEntry = {
  name: string;
  path: string;
};

export type WorkspaceDirectoryList = {
  path: string;
  parentPath: string | null;
  entries: WorkspaceDirectoryEntry[];
};

export type FileTreeEntry = {
  path: string;
  name: string;
  type: "file" | "directory";
  size?: number;
  modifiedAt?: string;
};

export type WorkspaceViewLocator = {
  kind: "COMPOSITE" | "WORKSPACE" | "REFERENCE";
  path: string;
  referenceAlias?: string;
};

export type WorkspaceViewSource = "WORKSPACE" | "REFERENCE" | "MIXED";

/** 合并工作区树节点；`id` 是缓存、展开状态和 Vue 渲染唯一允许使用的节点身份。 */
export type WorkspaceViewEntry = FileTreeEntry & {
  id: string;
  locator: WorkspaceViewLocator;
  source: WorkspaceViewSource;
  merged: boolean;
  collision: boolean;
  readonly: boolean;
  workspacePath?: string;
  referenceAliases: string[];
};

export type WorkspaceViewWarning = {
  alias?: string;
  code: string;
  message: string;
};

export type WorkspaceViewList = {
  entries: WorkspaceViewEntry[];
  warnings: WorkspaceViewWarning[];
  truncated: boolean;
};

export type WorkspaceViewFileContent = FileContent & {
  readonly: boolean;
  source: WorkspaceViewSource;
  referenceAlias?: string;
  locator: WorkspaceViewLocator;
};

export type FileSearchResult = {
  path: string;
  name: string;
  directory: string;
  size: number;
  modifiedAt?: string;
};

export type FileContent = {
  path: string;
  content: string;
  encoding?: string;
  size?: number;
  readonly?: boolean;
};

export type FileStatus = {
  path: string;
  exists?: boolean;
  directory?: boolean;
  size?: number;
  lastModifiedAt?: string;
  status: "added" | "modified" | "deleted" | "unchanged" | string;
};

export type WorkspaceFileRoute = {
  workspaceId: string;
  linuxServerId: string;
  baseUrl: string;
  webSocketPath: string;
  sameServer: boolean;
  message?: string | null;
};

export type WorkspaceBackendServer = {
  linuxServerId: string;
  name: string;
  baseUrl: string;
  webSocketPath: string;
  defaultDirectory?: string | null;
  sameAsAgent: boolean;
};

export type WorkspaceFileSocketTicketRequest = {
  workspaceId?: string;
  linuxServerId?: string;
  mode?: "workspace" | "directory-picker" | "agent-config" | string;
  scope?: AgentConfigScope;
  worktreeId?: string | null;
};

export type WorkspaceFileSocketTicketResponse = {
  ticket: string;
  expiresAt: string;
  webSocketUrl: string;
};

export type AgentConfigScope = "PUBLIC" | "WORKSPACE" | string;

export type AgentConfigFileRoute = {
  scope: AgentConfigScope;
  workspaceId?: string | null;
  worktreeId?: string | null;
  linuxServerId: string;
  baseUrl: string;
  webSocketPath: string;
  sameServer: boolean;
  message?: string | null;
};

export type AgentConfigStatus = {
  scope: AgentConfigScope;
  enabled: boolean;
  writable: boolean;
  gitUrl?: string | null;
  gitRootPath?: string | null;
  agentDirectory: string;
  currentBranch?: string | null;
  commitHash?: string | null;
};

export type PublicAgentRepositoryStatus = {
  linuxServerId: string;
  serverName: string;
  gitRootPath?: string | null;
  configDirPath?: string | null;
  worktreeRootPath?: string | null;
  status: string;
  initialized: boolean;
  initializationAllowed: boolean;
  currentBranch?: string | null;
  commitHash?: string | null;
  message?: string | null;
};

export type AgentConfigWorktree = {
  worktreeId: string;
  scope: AgentConfigScope;
  workspaceId?: string | null;
  linuxServerId?: string | null;
  worktreeName: string;
  branch: string;
  rootPath: string;
  agentDirectory: string;
  status: "ACTIVE" | "PUBLISHED" | "REMOVED" | string;
  createdAt: string;
  updatedAt: string;
};

export type AgentConfigWorktreeOption = AgentConfigWorktree & {
  createdByUserId: string;
  createdByUsername?: string | null;
};

export type AgentConfigDiffFile = {
  path: string;
  status: string;
  staged: boolean;
  patch: string;
};

export type AgentConfigDiff = {
  files: AgentConfigDiffFile[];
};

export type AgentConfigOperation = {
  operationId: string;
  scope: AgentConfigScope;
  workspaceId?: string | null;
  action: string;
  status: "RUNNING" | "SUCCEEDED" | "FAILED" | string;
  currentStep: string;
  errorCode?: string | null;
  errorMessage?: string | null;
  branch?: string | null;
  commitHash?: string | null;
  traceId: string;
  createdAt: string;
  updatedAt: string;
};

export type AgentConfigProgressEvent = {
  type: "snapshot" | "step" | "completed" | "failed" | string;
  operationId?: string;
  operation?: AgentConfigOperation | null;
  status?: string;
  currentStep?: string;
  errorCode?: string | null;
  errorMessage?: string | null;
  commitHash?: string | null;
  command?: string | null;
  traceId?: string;
  occurredAt?: string;
};

export type AgentConfigOperationTicketResponse = {
  ticket: string;
  expiresAt: string;
  webSocketUrl: string;
};

export type AgentConfigWorktreePayload = {
  baseName: string;
  branch: string;
  operationId?: string;
  linuxServerId?: string;
};

export type AgentConfigCommitPayload = {
  message: string;
  worktreeId?: string | null;
  operationId?: string;
};

export type Session = {
  sessionId: string;
  workspaceId: string;
  title: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  parentId?: string;
  pinned?: boolean;
  agent?: string;
  model?: ModelRef;
  costUsd?: number;
  tokens?: TokenUsage;
  workspaceContext?: SessionWorkspaceContext | null;
};

export type SessionWorkspaceContext = {
  appId?: string | null;
  appName?: string | null;
  applicationWorkspaceId?: string | null;
  workspaceName?: string | null;
  versionId?: string | null;
  version?: string | null;
};

/**
 * 服务端签发的会话运行上下文。token 只允许保存在页面内存中，不能进入持久化前端状态或调试原始报文。
 */
export type ConversationRunContext = {
  contextToken: string;
  contextVersion: number;
  expiresAt: string;
};

export type SessionRuntimeAttention = "QUESTION" | string;

export type SessionRuntimeState = {
  sessionId: string;
  runId: string;
  runStatus: "PENDING" | "RUNNING" | "CANCELLING" | string;
  attention?: SessionRuntimeAttention | null;
  attentionEventId?: string | null;
  attentionAt?: string | null;
  updatedAt: string;
};

export type SessionRuntimeStateSummary = {
  runningCount: number;
  questionCount: number;
  sessions: SessionRuntimeState[];
  generatedAt: string;
};

export type SessionMessageContentKind = "RAW_LEGACY" | "SUMMARY" | string;
export type SessionMessageSummaryStatus = "COMPLETE" | "PARTIAL" | "FALLBACK" | string;

export type SessionMessage = {
  messageId: string;
  sessionId: string;
  role: "USER" | "ASSISTANT" | "SYSTEM" | string;
  content: string;
  createdAt: string;
  updatedAt?: string;
  runId?: string;
  remoteMessageId?: string;
  parts?: MessagePart[];
  costUsd?: number;
  tokens?: TokenUsage;
  /** 旧后端或旧数据可缺失；RAW_LEGACY 表示历史原文，SUMMARY 表示终态摘要。 */
  contentKind?: SessionMessageContentKind | null;
  summaryStatus?: SessionMessageSummaryStatus | null;
  summaryVersion?: number | null;
};

export type SideQuestionRequest = {
  question: string;
  messageId?: string;
  agent?: string;
  model?: string;
};

export type SideQuestionResponse = {
  answer: string;
  compacted: boolean;
};

/** 流式旁路问答由服务端固定只读 agent，浏览器只提交问题和上下文边界。 */
export type SideQuestionRunRequest = {
  question: string;
  messageId?: string;
  model?: string;
};

/** 无主对话的手册问答只需要当前工作区，问题正文由前端使用内置手册章节约束。 */
export type ManualQuestionRunRequest = {
  workspaceId: string;
  question: string;
  model?: string;
};

export type SideQuestionRunResponse = {
  runId: string;
};

export type RunSessionTreeSessionResponse = {
  rootSessionId?: string | null;
  sessionId: string;
  parentSessionId?: string | null;
  childSession: boolean;
  taskMessageId?: string | null;
  taskPartId?: string | null;
  taskCallId?: string | null;
};

export type RunSessionTreeEventResponse = {
  type: string;
  rootSessionId?: string | null;
  sessionId?: string | null;
  parentSessionId?: string | null;
  childSession?: boolean | null;
  payload: Record<string, unknown>;
};

export type SessionTreeMessagesResponse = {
  sessionId: string;
  sessions: RunSessionTreeSessionResponse[];
  messagesBySessionId: Record<string, Record<string, unknown>[]>;
  childSessionIdByTaskPartId: Record<string, string>;
  events: RunSessionTreeEventResponse[];
  /** 完整历史来自 Redis/OpenCode，摘要历史来自 PostgreSQL 终态投影。 */
  historyRepresentation?: "FULL" | "SUMMARY" | string | null;
  replayAvailable?: boolean | null;
  detailsAvailableUntil?: string | null;
};

export type AiFeedbackRating = "POSITIVE" | "NEGATIVE";

export type AiFeedbackReasonCode =
  | "WRONG_ANSWER"
  | "NOT_HELPFUL"
  | "DID_NOT_FOLLOW_INSTRUCTION"
  | "CODE_QUALITY_LOW"
  | "TEST_RESULT_BAD"
  | "TOO_SLOW"
  | "TOO_VERBOSE"
  | "TOO_SHORT"
  | "OTHER";

export type AiMessageFeedback = {
  feedbackId: string;
  messageId: string;
  sessionId: string;
  runId?: string | null;
  rating: AiFeedbackRating;
  reasonCode?: AiFeedbackReasonCode | null;
  comment?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type AiMessageFeedbackPayload = {
  rating: AiFeedbackRating;
  reasonCode?: AiFeedbackReasonCode | null;
  comment?: string | null;
};

/** 用户对一次主智能体 Run 整体回复的评价。 */
export type AiRunFeedback = {
  feedbackId: string;
  runId: string;
  sessionId: string;
  rating: AiFeedbackRating;
  reasonCode?: AiFeedbackReasonCode | null;
  comment?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type AiRunFeedbackPayload = AiMessageFeedbackPayload;

export type RunFeedbackState = {
  runId: string;
  sessionId: string;
  runStatus: string;
  feedback?: AiRunFeedback | null;
};

export type RunFeedbackQuery = {
  runIds: string[];
};

export type AnalyticsGranularity = "hour" | "day" | "week" | "month";

export type AnalyticsQueryParams = {
  startTime?: string;
  endTime?: string;
  granularity?: AnalyticsGranularity;
  organization?: string;
  rdDepartment?: string;
  department?: string;
  userId?: string;
  agentId?: string;
  model?: string;
  workspaceId?: string;
  topN?: number;
  page?: number;
  pageSize?: number;
  sort?: string;
};

export type AnalyticsFreshness = {
  generatedAt?: string | null;
  status: "FRESH" | "STALE" | "FAILED" | string;
  message?: string | null;
};

export type AnalyticsOverview = {
  registeredUsers: number;
  enabledUsers: number;
  loginUsers: number;
  activeUsers: number;
  validUsers: number;
  deepUsers: number;
  activeRate?: number | null;
  loginToActiveRate?: number | null;
  activeToValidRate?: number | null;
  validToDeepRate?: number | null;
  sessionCount: number;
  activeSessionCount: number;
  emptySessionCount: number;
  continuousSessionCount: number;
  userMessageCount: number;
  assistantMessageCount: number;
  runCount: number;
  runsPerUser?: number | null;
  messagesPerUser?: number | null;
  messagesPerSession?: number | null;
  continuousConversationRate?: number | null;
  validInteractionCount: number;
  sustainedUsers: number;
  succeededRuns: number;
  failedRuns: number;
  cancelledRuns: number;
  activeTerminations: number;
  successRate?: number | null;
  failureRate?: number | null;
  cancellationRate?: number | null;
  averageDurationMs?: number | null;
  p95DurationMs?: number | null;
  positiveFeedbackCount: number;
  negativeFeedbackCount: number;
  satisfactionRate?: number | null;
  feedbackCoverageRate?: number | null;
  diffProposedCount: number;
  diffAcceptedCount: number;
  diffRejectedCount: number;
  diffAcceptanceRate?: number | null;
  diffRejectionRate?: number | null;
  inputTokens: number;
  outputTokens: number;
  reasoningTokens: number;
  totalTokens: number;
  tokensPerUser?: number | null;
  tokensPerRun?: number | null;
  freshness: AnalyticsFreshness;
};

export type AnalyticsTimeSeriesPoint = {
  bucketStart: string;
  loginUsers: number;
  activeUsers: number;
  sessionCount: number;
  activeSessionCount: number;
  userMessageCount: number;
  assistantMessageCount: number;
  runCount: number;
  succeededRuns: number;
  failedRuns: number;
  cancelledRuns: number;
  positiveFeedbackCount: number;
  negativeFeedbackCount: number;
  diffAcceptedCount: number;
  diffRejectedCount: number;
  totalTokens: number;
  satisfactionRate?: number | null;
  diffAcceptanceRate?: number | null;
  cancellationRate?: number | null;
};

export type AnalyticsPeakPoint = {
  bucketStart: string;
  activeUsers: number;
  runCount: number;
  userMessageCount: number;
  satisfactionRate?: number | null;
  cancellationRate?: number | null;
  totalTokens: number;
};

export type AnalyticsHeatmapPoint = {
  dayOfWeek: number;
  hourOfDay: number;
  activeUsers: number;
  runCount: number;
  userMessageCount: number;
};

export type AnalyticsPeaks = {
  peakPeriods: AnalyticsPeakPoint[];
  heatmap: AnalyticsHeatmapPoint[];
  freshness: AnalyticsFreshness;
};

export type AnalyticsUserUsageRow = {
  userId: string;
  username?: string | null;
  organization?: string | null;
  rdDepartment?: string | null;
  department?: string | null;
  loginCount: number;
  sessionCount: number;
  activeSessionCount: number;
  userMessageCount: number;
  runCount: number;
  succeededRuns: number;
  failedRuns: number;
  cancelledRuns: number;
  positiveFeedbackCount: number;
  negativeFeedbackCount: number;
  diffAcceptedCount: number;
  diffRejectedCount: number;
  totalTokens: number;
  successRate?: number | null;
  satisfactionRate?: number | null;
  diffAcceptanceRate?: number | null;
  lastActivityAt?: string | null;
};

export type AnalyticsOrganizationUsageRow = {
  dimension: string;
  name: string;
  registeredUsers: number;
  enabledUsers: number;
  loginUsers: number;
  activeUsers: number;
  deepUsers: number;
  activeRate?: number | null;
  deepRate?: number | null;
  runCount: number;
  succeededRuns: number;
  failedRuns: number;
  cancelledRuns: number;
  positiveFeedbackCount: number;
  negativeFeedbackCount: number;
  diffAcceptedCount: number;
  diffRejectedCount: number;
  totalTokens: number;
  successRate?: number | null;
  satisfactionRate?: number | null;
  diffAcceptanceRate?: number | null;
};

export type AnalyticsFeedbackDetail = {
  feedbackId: string;
  userId: string;
  username?: string | null;
  organization?: string | null;
  rdDepartment?: string | null;
  department?: string | null;
  sessionId: string;
  runId?: string | null;
  messageId?: string | null;
  rating: AiFeedbackRating;
  reasonCode?: AiFeedbackReasonCode | null;
  comment?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type AnalyticsExceptionDetail = {
  runId: string;
  userId: string;
  username?: string | null;
  organization?: string | null;
  rdDepartment?: string | null;
  department?: string | null;
  workspaceId: string;
  agentId?: string | null;
  modelId?: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type AnalyticsSatisfaction = {
  positiveFeedbackCount: number;
  negativeFeedbackCount: number;
  satisfactionRate?: number | null;
  feedbackCoverageRate?: number | null;
  negativeReasonCounts: Record<string, number>;
  feedbackDetails: PageResponse<AnalyticsFeedbackDetail>;
  freshness: AnalyticsFreshness;
};

export type Run = {
  runId: string;
  sessionId: string;
  workspaceId: string;
  storageMode?: "LEGACY_FULL" | "REDIS_SUMMARY" | string | null;
  clientRequestId?: string | null;
  detailsAvailableUntil?: string | null;
  status: "PENDING" | "RUNNING" | "CANCELLING" | "SUCCEEDED" | "FAILED" | "CANCELLED" | string;
  createdAt: string;
  updatedAt: string;
  costUsd?: number;
  tokens?: TokenUsage;
};

export type UserOpencodeProcessStatus = "READY" | "NEEDS_INITIALIZATION" | "UNAVAILABLE" | string;
export type UserOpencodeServiceStatus = "UNASSIGNED" | "RUNNING" | "NOT_RUNNING" | string;

export type UserOpencodeProcess = {
  status: UserOpencodeProcessStatus;
  /** 头像菜单展示状态：未分配、运行中、未运行。旧后端缺失时前端从 status/baseUrl 推断。 */
  serviceStatus?: UserOpencodeServiceStatus;
  /** 头像菜单展示地址，格式为当前可访问 host:内部opencode端口；无法解析时为空。 */
  serviceAddress?: string | null;
  initializable: boolean;
  /** 用户绑定指向的 Linux 服务器已无可用容器，但本地仍有可路由节点时为 true。 */
  bindingClearable?: boolean;
  /** 当前响应已经回退到固定 execution_node（例如本地 opencode）时为 true。 */
  localFallback?: boolean;
  message: string;
  processId?: string;
  /** 稳定服务器身份，不保证是可连接 IP，不能直接拼接为 host:port。 */
  linuxServerId?: string;
  containerId?: string;
  port?: number;
  baseUrl?: string;
  checkedAt: string;
  /** 后端 Java 服务器 IP 地址 */
  backendJavaServerIp?: string;
  /** 公共 Agent/Skill 配置发布排空期间为 false；后端 Run 入口仍会再次强制校验。 */
  messageSendAllowed?: boolean;
  messageSendBlockedReason?: string | null;
  publicConfigRolloutId?: string | null;
};

export type UserOpencodeMessageGate = {
  messageSendAllowed: boolean;
  messageSendBlockedReason?: string | null;
  publicConfigRolloutId?: string | null;
};

export type UserOpencodeProcessHealthStatus =
  | "HEALTHY"
  | "UNHEALTHY"
  | "PROCESS_NOT_FOUND"
  | "MANAGER_UNAVAILABLE"
  | "BACKEND_UNAVAILABLE"
  | string;

export type UserOpencodeProcessHealthRequest = {
  linuxServerId: string;
  containerId: string;
  port: number;
};

export type UserOpencodeProcessHealth = {
  healthy: boolean;
  status: UserOpencodeProcessHealthStatus;
  serviceStatus: "RUNNING" | "NOT_RUNNING" | string;
  linuxServerId: string;
  containerId: string;
  port: number;
  baseUrl?: string | null;
  checkedAt: string;
  message: string;
};

export type OpencodeProcessStartOperationStatus = "RUNNING" | "SUCCEEDED" | "FAILED" | string;
export type OpencodeProcessStartOperationStepStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | string;

export type OpencodeProcessStartOperationStep = {
  /** 后端字段名；部分单测夹具兼容历史 code 写法。 */
  step?: string;
  code?: string;
  name: string;
  status: OpencodeProcessStartOperationStepStatus;
};

export type OpencodeProcessStartOperation = {
  operationId: string;
  status: OpencodeProcessStartOperationStatus;
  currentStep: string;
  steps: OpencodeProcessStartOperationStep[];
  errorCode?: string | null;
  errorMessage?: string | null;
  processId?: string | null;
  serviceAddress?: string | null;
  traceId: string;
  createdAt: string;
  updatedAt: string;
};

// ---- opencode 运行管理类型 ----

export type OpencodeRuntimeManagementOverviewParams = {
  status?: string;
  linuxServerId?: string;
  containerId?: string;
  username?: string;
  userId?: string;
  page?: number;
  size?: number;
};

export type OpencodeRuntimeManagementUserProcessParams = {
  keyword: string;
  page?: number;
  size?: number;
};

export type OpencodeRuntimeManagementSummary = {
  linuxServers: number;
  readyLinuxServers: number;
  backendProcesses: number;
  readyBackendProcesses: number;
  containers: number;
  readyContainers: number;
  managers: number;
  connectedManagers: number;
  managerBackendConnections: number;
  opencodeProcesses: number;
  runningOpencodeProcesses: number;
  userBindings: number;
};

export type OpencodeRuntimeLinuxServer = {
  linuxServerId: string;
  name: string;
  status: string;
  capacitySummary: Record<string, unknown>;
  lastHeartbeatAt?: string | null;
  createdAt: string;
  updatedAt: string;
  traceId: string;
};

export type OpencodeRuntimeBackendProcess = {
  backendProcessId: string;
  buildVersion?: string | null;
  linuxServerId: string;
  listenUrl: string;
  status: string;
  startedAt?: string | null;
  lastHeartbeatAt?: string | null;
  cpuUsagePercent?: number | null;
  cpuCoreCount?: number | null;
  loadAverage1m?: number | null;
  loadAverage5m?: number | null;
  loadAverage15m?: number | null;
  memoryMaxBytes?: number | null;
  memoryTotalBytes?: number | null;
  memoryAvailableBytes?: number | null;
  memoryFreeBytes?: number | null;
  memoryUsedBytes?: number | null;
  memoryUsagePercent?: number | null;
  memoryBuffersBytes?: number | null;
  memoryCachedBytes?: number | null;
  swapTotalBytes?: number | null;
  swapFreeBytes?: number | null;
  swapUsedBytes?: number | null;
  swapUsagePercent?: number | null;
  diskMaxBytes?: number | null;
  diskAvailableBytes?: number | null;
  diskUsedBytes?: number | null;
  diskUsagePercent?: number | null;
  jvmProcessCpuUsagePercent?: number | null;
  jvmProcessCpuCoreUsage?: number | null;
  jvmProcessCpuTimeNanos?: number | null;
  jvmProcessResidentMemoryBytes?: number | null;
  jvmProcessPeakResidentMemoryBytes?: number | null;
  jvmProcessVirtualMemoryBytes?: number | null;
  jvmProcessSwapBytes?: number | null;
  jvmOpenFileDescriptorCount?: number | null;
  jvmMaxFileDescriptorCount?: number | null;
  jvmMemoryUsedBytes?: number | null;
  jvmMemoryCommittedBytes?: number | null;
  jvmMemoryMaxBytes?: number | null;
  jvmHeapUsedBytes?: number | null;
  jvmHeapCommittedBytes?: number | null;
  jvmHeapMaxBytes?: number | null;
  jvmNonHeapUsedBytes?: number | null;
  jvmNonHeapCommittedBytes?: number | null;
  jvmNonHeapMaxBytes?: number | null;
  jvmDirectBufferCount?: number | null;
  jvmDirectBufferUsedBytes?: number | null;
  jvmDirectBufferCapacityBytes?: number | null;
  jvmMappedBufferCount?: number | null;
  jvmMappedBufferUsedBytes?: number | null;
  jvmMappedBufferCapacityBytes?: number | null;
  jvmGcPauseMillis?: number | null;
  jvmGcCollectionTimeDeltaMillis?: number | null;
  jvmGcCollectionCountDelta?: number | null;
  jvmGcTimePercent?: number | null;
  jvmThreadsLive?: number | null;
  jvmThreadsDaemon?: number | null;
  jvmThreadsPeak?: number | null;
  jvmThreadsTotalStarted?: number | null;
  createdAt: string;
  updatedAt: string;
  traceId: string;
};

export type OpencodeRuntimeContainer = {
  containerId: string;
  linuxServerId: string;
  containerName: string;
  portStart: number;
  portEnd: number;
  maxProcesses: number;
  currentProcesses: number;
  availableCapacity: number;
  metricsSource?: string | null;
  cpuUsagePercent?: number | null;
  memoryMaxBytes?: number | null;
  memoryUsedBytes?: number | null;
  memoryUsagePercent?: number | null;
  diskReadBytesPerSecond?: number | null;
  diskWriteBytesPerSecond?: number | null;
  status: string;
  lastHeartbeatAt?: string | null;
  createdAt: string;
  updatedAt: string;
  traceId: string;
};

export type OpencodeRuntimeManagedProcess = {
  port: number;
  pid?: number | null;
  baseUrl?: string | null;
  sessionPath?: string | null;
  configPath?: string | null;
  startedAt?: string | null;
  startCommand?: string | null;
  traceId?: string | null;
  ownership?: "BOUND" | "UNBOUND" | string | null;
  processId?: string | null;
  processStatus?: string | null;
  healthMessage?: string | null;
  userId?: string | null;
  username?: string | null;
  bindingAgentId?: string | null;
  bindingStatus?: string | null;
  bindingUpdatedAt?: string | null;
};

export type OpencodeRuntimeManagedProcessCommandResult = {
  command: string;
  status: string;
  port: number;
  pid?: number | null;
  baseUrl?: string | null;
  sessionPath?: string | null;
  configPath?: string | null;
  healthy?: boolean | null;
  message?: string | null;
  traceId?: string | null;
};

export type OpencodeRuntimeManager = {
  managerId: string;
  buildVersion?: string | null;
  containerId: string;
  linuxServerId: string;
  protocolVersion: string;
  connectionStatus: string;
  capabilities: Record<string, unknown>;
  lastHeartbeatAt?: string | null;
  createdAt: string;
  updatedAt: string;
  traceId: string;
  managedProcesses?: OpencodeRuntimeManagedProcess[];
};

export type OpencodeRuntimeManagerBackendConnection = {
  managerId: string;
  backendProcessId: string;
  status: string;
  connectedAt?: string | null;
  lastHeartbeatAt?: string | null;
  updatedAt: string;
  traceId: string;
};

export type OpencodeRuntimeProcess = {
  processId: string;
  userId: string;
  username?: string | null;
  linuxServerId: string;
  containerId: string;
  port: number;
  pid?: number | null;
  baseUrl: string;
  status: string;
  managerStatus?: string | null;
  healthStatus?: string | null;
  restartable?: boolean;
  sessionPath: string;
  configPath: string;
  startedAt?: string | null;
  lastHealthCheckAt?: string | null;
  healthMessage?: string | null;
  createdAt: string;
  updatedAt: string;
  traceId: string;
  bindingAgentId?: string | null;
  bindingStatus?: string | null;
  bindingUpdatedAt?: string | null;
};

export type OpencodeRuntimeManagementOverview = {
  generatedAt: string;
  summary: OpencodeRuntimeManagementSummary;
  linuxServers: OpencodeRuntimeLinuxServer[];
  backendProcesses: OpencodeRuntimeBackendProcess[];
  containers: OpencodeRuntimeContainer[];
  managers: OpencodeRuntimeManager[];
  managerBackendConnections: OpencodeRuntimeManagerBackendConnection[];
  opencodeProcesses: PageResponse<OpencodeRuntimeProcess>;
};

export type OpencodeRuntimeMetricHistoryParams = {
  windowMinutes?: number;
  hours?: number;
  maxPoints?: number;
};

export type OpencodeRuntimeContainerMetricSample = {
  sampledAt: string;
  maxProcesses: number;
  currentProcesses: number;
  metricsSource?: string | null;
  cpuUsagePercent?: number | null;
  memoryMaxBytes?: number | null;
  memoryUsedBytes?: number | null;
  memoryUsagePercent?: number | null;
  diskReadBytesPerSecond?: number | null;
  diskWriteBytesPerSecond?: number | null;
};

export type OpencodeRuntimeContainerMetricHistory = {
  generatedAt: string;
  containerId: string;
  from: string;
  to: string;
  samples: OpencodeRuntimeContainerMetricSample[];
};

export type OpencodeRuntimeBackendMetricSample = {
  sampledAt: string;
  cpuUsagePercent?: number | null;
  cpuCoreCount?: number | null;
  loadAverage1m?: number | null;
  loadAverage5m?: number | null;
  loadAverage15m?: number | null;
  memoryMaxBytes?: number | null;
  memoryTotalBytes?: number | null;
  memoryAvailableBytes?: number | null;
  memoryFreeBytes?: number | null;
  memoryUsedBytes?: number | null;
  memoryUsagePercent?: number | null;
  memoryBuffersBytes?: number | null;
  memoryCachedBytes?: number | null;
  swapTotalBytes?: number | null;
  swapFreeBytes?: number | null;
  swapUsedBytes?: number | null;
  swapUsagePercent?: number | null;
  diskMaxBytes?: number | null;
  diskAvailableBytes?: number | null;
  diskUsedBytes?: number | null;
  diskUsagePercent?: number | null;
  jvmProcessCpuUsagePercent?: number | null;
  jvmProcessCpuCoreUsage?: number | null;
  jvmProcessCpuTimeNanos?: number | null;
  jvmProcessResidentMemoryBytes?: number | null;
  jvmProcessPeakResidentMemoryBytes?: number | null;
  jvmProcessVirtualMemoryBytes?: number | null;
  jvmProcessSwapBytes?: number | null;
  jvmOpenFileDescriptorCount?: number | null;
  jvmMaxFileDescriptorCount?: number | null;
  jvmMemoryUsedBytes?: number | null;
  jvmMemoryCommittedBytes?: number | null;
  jvmMemoryMaxBytes?: number | null;
  jvmHeapUsedBytes?: number | null;
  jvmHeapCommittedBytes?: number | null;
  jvmHeapMaxBytes?: number | null;
  jvmNonHeapUsedBytes?: number | null;
  jvmNonHeapCommittedBytes?: number | null;
  jvmNonHeapMaxBytes?: number | null;
  jvmDirectBufferCount?: number | null;
  jvmDirectBufferUsedBytes?: number | null;
  jvmDirectBufferCapacityBytes?: number | null;
  jvmMappedBufferCount?: number | null;
  jvmMappedBufferUsedBytes?: number | null;
  jvmMappedBufferCapacityBytes?: number | null;
  jvmGcPauseMillis?: number | null;
  jvmGcCollectionTimeDeltaMillis?: number | null;
  jvmGcCollectionCountDelta?: number | null;
  jvmGcTimePercent?: number | null;
  jvmThreadsLive?: number | null;
  jvmThreadsDaemon?: number | null;
  jvmThreadsPeak?: number | null;
  jvmThreadsTotalStarted?: number | null;
};

export type OpencodeRuntimeBackendMetricHistory = {
  generatedAt: string;
  linuxServerId?: string | null;
  backendProcessId?: string | null;
  from: string;
  to: string;
  samples: OpencodeRuntimeBackendMetricSample[];
};

// ---- 定时任务管理类型 ----

export type SchedulerRunStatus =
  | "PENDING"
  | "RUNNING"
  | "STOPPING"
  | "SUCCEEDED"
  | "FAILED"
  | "SKIPPED"
  | "MANUALLY_STOPPED"
  | string;

export type SchedulerTriggerType = "CRON" | "MANUAL" | "USER_PLAN" | string;

export type SchedulerTaskRegistrationStatus = "REGISTERED" | "MISSING_HANDLER" | string;

export type ScheduledTaskRunSummary = {
  taskRunId: string;
  status: SchedulerRunStatus;
  statusLabel?: string;
  triggerType: SchedulerTriggerType;
  triggerTypeLabel?: string;
  requestedByUserId?: string | null;
  scheduledFireAt: string;
  startedAt?: string | null;
  endedAt?: string | null;
  ownerInstanceId?: string | null;
};

export type ScheduledTaskManagementTask = {
  taskKey: string;
  name: string;
  cronExpression: string;
  enabled: boolean;
  lockTtlSeconds: number;
  nextFireAt?: string | null;
  registrationStatus: SchedulerTaskRegistrationStatus;
  registrationStatusLabel?: string;
  currentRun?: ScheduledTaskRunSummary | null;
  latestRun?: ScheduledTaskRunSummary | null;
  traceId: string;
  createdAt: string;
  updatedAt: string;
};

export type ScheduledTaskManagementRun = {
  taskRunId: string;
  taskKey: string;
  planId?: string | null;
  triggerType: SchedulerTriggerType;
  triggerTypeLabel?: string;
  status: SchedulerRunStatus;
  statusLabel?: string;
  requestedByUserId?: string | null;
  scheduledFireAt: string;
  startedAt?: string | null;
  endedAt?: string | null;
  ownerInstanceId?: string | null;
  stopRequestedAt?: string | null;
  stopRequestedByUserId?: string | null;
  stopReason?: string | null;
  skipReason?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  result?: Record<string, unknown> | null;
  traceId: string;
  createdAt: string;
  updatedAt: string;
};

export type SchedulerRuntimeDiagnostics = {
  enabled: boolean;
  runnerRunning: boolean;
  instanceId: string;
  scanIntervalSeconds: number;
  dueTaskLimit: number;
  manualRunLimit: number;
  lastScanStartedAt?: string | null;
  lastScanFinishedAt?: string | null;
  lastScanErrorMessage?: string | null;
};

export type ScheduledTaskLockDiagnostics = {
  checkable: boolean;
  lockKey: string;
  locked: boolean;
  ttlMillis?: number | null;
  errorMessage?: string | null;
};

export type ScheduledTaskRuntimeDiagnostics = {
  taskKey: string;
  enabled: boolean;
  registrationStatus: SchedulerTaskRegistrationStatus;
  registrationStatusLabel?: string;
  nextFireAt?: string | null;
  lockTtlSeconds: number;
  currentRun?: ScheduledTaskRunSummary | null;
  latestRun?: ScheduledTaskRunSummary | null;
  pendingManualRunCount: number;
};

export type SchedulerDiagnosticBlocker = {
  code:
    | "SCHEDULER_DISABLED"
    | "RUNNER_NOT_RUNNING"
    | "HANDLER_MISSING"
    | "TASK_DISABLED_FOR_CRON"
    | "ACTIVE_RUN"
    | "LOCK_HELD"
    | string;
  message: string;
};

export type ScheduledTaskDiagnosis = {
  manualTriggerReady: boolean;
  cronReady: boolean;
  blockers: SchedulerDiagnosticBlocker[];
};

export type SchedulerDiagnostics = {
  scheduler: SchedulerRuntimeDiagnostics;
  redisLock: ScheduledTaskLockDiagnostics;
  task: ScheduledTaskRuntimeDiagnostics;
  diagnosis: ScheduledTaskDiagnosis;
};

export type ScheduledTaskUpdatePayload = {
  enabled?: boolean;
  cronExpression?: string;
  lockTtlSeconds?: number;
};

export type ScheduledTaskListParams = {
  page?: number;
  size?: number;
};

export type ScheduledTaskRunListParams = {
  taskKey?: string;
  status?: string;
  triggerType?: string;
  requestedByUserId?: string;
  page?: number;
  size?: number;
};

export type GeneralParameter = {
  parameterId: string;
  englishName: string;
  chineseName: string;
  parameterValue: string;
  platform: string;
  editable: boolean;
  createdAt: string;
  updatedAt: string;
};

export type GeneralParameterListParams = {
  platform?: string;
  page?: number;
  size?: number;
};

export type GeneralParameterUpdatePayload = {
  value: string;
};

export type CommonParameterChangeLog = {
  logId: string;
  parameterId: string;
  oldValue: string | null;
  newValue: string;
  changedByUserId: string | null;
  changedByUsername: string | null;
  traceId: string | null;
  createdAt: string;
};

export type InternalModelProviderConfig = {
  providerId: string;
  name: string;
  baseUrl: string;
  enabled: boolean;
  sortOrder: number;
  createdAt?: string;
  updatedAt?: string;
};

export type InternalModelProviderManagementResponse = {
  providers: InternalModelProviderConfig[];
  tokenConfigured: boolean;
};

export type InternalModelProviderUpdatePayload = {
  providers: Array<Pick<InternalModelProviderConfig, "providerId" | "name" | "baseUrl" | "enabled" | "sortOrder">>;
  authToken?: string | null;
};

export type InternalModelProviderRefreshStatus = {
  providers: InternalModelProviderConfig[];
  tokenConfigured: boolean;
  loadedAt?: string;
  traceId?: string;
};

export type RunEventType =
  | "run.created"
  | "run.started"
  | "run.cancelling"
  | "run.succeeded"
  | "run.failed"
  | "run.cancelled"
  | "run.snapshot.reset"
  | "side_question.started"
  | "side_question.progress"
  | "side_question.delta"
  | "assistant.message.delta"
  | "message.updated"
  | "message.removed"
  | "message.part.updated"
  | "message.part.removed"
  | "message.part.delta"
  | "session.diff"
  | "session.status"
  | "session.child.discovered"
  | "session.scope.updated"
  | "todo.updated"
  | "tool.started"
  | "tool.finished"
  | "diff.proposed"
  | "diff.accepted"
  | "diff.rejected"
  | "test.finished"
  | "permission.asked"
  | "permission.replied"
  | "question.asked"
  | "question.replied"
  | "question.rejected"
  | "vcs.branch.updated"
  | "lsp.updated"
  | "mcp.tools.changed"
  | "opencode.event.unknown"
  | string;

export type RunEvent = {
  eventId: string;
  runId: string;
  seq: number;
  type: RunEventType;
  traceId: string;
  occurredAt: string;
  payload: Record<string, unknown>;
};

/** Redis 详情游标失效后由后端下发的 Run 物化快照；字段保持可选以兼容旧响应。 */
export type RunRuntimeSnapshot = {
  barrierSeq?: number;
  runtimeVersion?: number;
  events?: RunEvent[];
};

/** `run.snapshot.reset` 的 payload；该 transient 事件不参与 durable Last-Event-ID 推进。 */
export type RunSnapshotResetPayload = {
  reason?: string;
  resetGeneration?: number;
  earliestSeq?: number;
  detailsAvailableUntil?: string;
  snapshot?: RunRuntimeSnapshot;
};

export type MessageScope = {
  sessionId?: string;
  rootSessionId?: string;
  parentSessionId?: string;
  isChildSession?: boolean;
  taskMessageId?: string;
  taskPartId?: string;
  taskCallId?: string;
};

export type SubagentSession = {
  sessionId: string;
  parentSessionId?: string;
  taskMessageId?: string;
  taskPartId?: string;
  taskCallId?: string;
  agentName: string;
  title: string;
  status: string;
  modelLabel?: string;
  updatedAt: string;
};

export type RunDiffFile = {
  path: string;
  patch: string;
  additions: number;
  deletions: number;
  status: string;
};

export type RunDiff = {
  runId: string;
  files: RunDiffFile[];
};

export type SessionDiff = {
  sessionId: string;
  messageId?: string;
  files: RunDiffFile[];
};

export type RunDiffAction = {
  runId: string;
  action: "accept" | "reject" | string;
  status: "accepted" | "rejected" | string;
  fileCount: number;
};

export type PromptPart =
  | { type: "text"; text: string }
  | {
      type: "file";
      path?: string;
      name?: string;
      mimeType?: string;
      content?: string;
      url?: string;
      source?: {
        start?: number;
        end?: number;
        text?: string;
        startLine?: number;
        endLine?: number;
        contextType?: "selection" | "file" | string;
      };
    }
  | { type: "agent"; agentId: string; name?: string }
  | { type: "reference"; id: string; label: string; uri?: string; metadata?: Record<string, unknown> };

export type MessagePartStatus = "pending" | "running" | "completed" | "error" | string;

export type TextPart = {
  partId: string;
  type: "text";
  text: string;
  status?: MessagePartStatus;
};

export type ReasoningPart = {
  partId: string;
  type: "reasoning";
  text: string;
  status?: MessagePartStatus;
  title?: string;
  durationMs?: number;
};

export type ToolPart = {
  partId: string;
  type: "tool";
  toolName: string;
  callId?: string;
  status: MessagePartStatus;
  input?: Record<string, unknown>;
  output?: unknown;
  metadata?: Record<string, unknown>;
  startedAt?: string;
  endedAt?: string;
};

export type FilePart = {
  partId: string;
  type: "file";
  path?: string;
  name?: string;
  mimeType?: string;
  url?: string;
  source?: { start?: number; end?: number; text?: string };
};

// 子任务：主 Agent 派发给子 Agent 的一次独立执行
export type SubtaskPart = {
  partId: string;
  type: "subtask";
  prompt: string;
  description: string;
  agent: string;
  model?: string;
  command?: string;
  status?: MessagePartStatus;
};

// 步骤开始：标记一次模型回合的起点
export type StepStartPart = {
  partId: string;
  type: "step-start";
  snapshot?: string;
};

// 步骤结束：一次模型回合的终点，携带原因与成本
export type StepFinishPart = {
  partId: string;
  type: "step-finish";
  reason: string;
  snapshot?: string;
  cost?: number;
  tokens?: { total?: number; input?: number; output?: number; reasoning?: number };
};

// 消息快照：用于回放/回退的完整消息内容
export type SnapshotPart = {
  partId: string;
  type: "snapshot";
  snapshot: string;
};

// 补丁：Agent 提出的一组文件改动
export type PatchPart = {
  partId: string;
  type: "patch";
  hash: string;
  files: string[];
  // 可选 metadata：filesMap 是 path → unified diff 文本；fileStats 是 path → { additions, deletions }。
  // 后端把 apply_patch / edit 工具的产物挂在 metadata 上，前端 PatchBlock 据此展示每文件的 diff 与 +/– 行数。
  metadata?: {
    filesMap?: Record<string, string>;
    fileStats?: Record<string, { additions?: number; deletions?: number }>;
  };
};

// Agent 声明：当前活跃的 Agent 标识
export type AgentPart = {
  partId: string;
  type: "agent";
  name: string;
  source?: { value: string; start?: number; end?: number };
};

// 重试：一次失败后的重试事件
export type RetryPart = {
  partId: string;
  type: "retry";
  attempt: number;
  error: { name?: string; message?: string };
  time?: { created?: number };
};

// 上下文压缩：会话上下文被压缩的事件
export type CompactionPart = {
  partId: string;
  type: "compaction";
  auto: boolean;
  overflow?: boolean;
  tailStartId?: string;
};

export type MessagePart =
  | TextPart
  | ReasoningPart
  | ToolPart
  | FilePart
  | SubtaskPart
  | StepStartPart
  | StepFinishPart
  | SnapshotPart
  | PatchPart
  | AgentPart
  | RetryPart
  | CompactionPart
  | { partId: string; type: "event"; eventType: string; payload: Record<string, unknown>; status?: MessagePartStatus };

export type PermissionRequest = {
  requestId: string;
  sessionId: string;
  type: string;
  title?: string;
  description?: string;
  pattern?: string;
  diff?: SessionDiff;
  createdAt: string;
};

export type QuestionRequest = {
  requestId: string;
  sessionId: string;
  /** OpenCode question 工具来源，用于回复成功后精确回填原工具卡片。 */
  tool?: {
    messageId?: string;
    callId?: string;
  };
  questions: Array<{
    questionId: string;
    header?: string;
    text: string;
    kind: "single" | "multiple" | "text" | string;
    options?: Array<{ id: string; label: string; description?: string }>;
    custom?: boolean;
    required?: boolean;
  }>;
  createdAt: string;
};

export type AgentInfo = {
  agentId: string;
  name: string;
  mode?: string;
  description?: string;
  color?: string;
  hidden?: boolean;
};

export type ModelRef = {
  id: string;
  providerId?: string;
  variant?: string;
};

export type ModelInfo = ModelRef & {
  name: string;
  contextLimit?: number;
  outputLimit?: number;
  free?: boolean;
  defaultModel?: boolean;
  variants?: string[];
};

export type ProviderInfo = {
  providerId: string;
  name: string;
  status?: string;
  models?: ModelInfo[];
  metadata?: Record<string, unknown>;
};

export type CommandInfo = {
  commandId: string;
  name: string;
  aliases?: string[];
  description?: string;
  arguments?: string;
  source?: "command" | "mcp" | "skill" | string;
  hints?: string[];
};

export type RuntimeResourceInfo = {
  id: string;
  name: string;
  uri?: string;
  type?: string;
  metadata?: Record<string, unknown>;
};

export type RuntimeToolInfo = {
  toolId: string;
  name: string;
  description?: string;
  parameters?: unknown;
  source?: "builtin" | "mcp" | "command" | string;
};

export type TodoItem = {
  id: string;
  text: string;
  status: "pending" | "in_progress" | "completed" | "cancelled" | string;
  priority?: "low" | "medium" | "high" | string;
  title?: string;
  description?: string;
  summary?: string;
  result?: string;
  error?: string;
  steps?: string[];
  updatedAt?: string;
};

export type TokenUsage = {
  input?: number;
  output?: number;
  reasoning?: number;
  cacheRead?: number;
  cacheWrite?: number;
  contextWindow?: number;
};

export type RuntimeStatus = {
  sessionId: string;
  runId?: string;
  status: string;
  agent?: AgentInfo;
  model?: ModelRef;
  tokens?: TokenUsage;
  costUsd?: number;
  branch?: string;
  lsp?: { status: string; diagnostics?: number };
  mcp?: { status: string; tools?: number; resources?: number };
};

export type TerminalTicketRequest = {
  workspaceId?: string;
  cwd?: string;
  shell?: string;
  cols?: number;
  rows?: number;
};

export type ServerTerminalTicketRequest = {
  confirmationText: string;
  cols?: number;
  rows?: number;
};

export type TerminalTicketResponse = {
  ticket: string;
  expiresAt: string;
  webSocketUrl: string;
};

export type AgentMessage =
  | {
      id: string;
      role: "user";
      text: string;
      parts?: PromptPart[];
      createdAt: string;
      messageId?: string;
      platformMessageId?: string;
      remoteMessageId?: string;
      runId?: string;
    }
  | {
      id: string;
      role: "assistant";
      text: string;
      parts?: MessagePart[];
      createdAt: string;
      messageId?: string;
      platformMessageId?: string;
      remoteMessageId?: string;
      runId?: string;
    }
  | {
      id: string;
      role: "card";
      cardType: "plan" | "tool" | "test" | "diff" | "event";
      title: string;
      payload: Record<string, unknown>;
      createdAt: string;
    };

// ---- 认证相关类型 ----

/**
 * 登录请求体。
 */
export type LoginRequest = {
  username: string;
  password: string;
};

/**
 * 登录成功响应体。
 */
export type LoginResponse = {
  token: string;
  userId: string;
  username: string;
  unifiedAuthId: string;
  roles?: string[];
};

/**
 * 当前登录用户信息。
 *
 * roleLabels 是 roles 对应的中文展示名（来自后端 `dictionaries.dict_label`），
 * 供右上角用户菜单直接展示；缺失时按空数组兼容。
 */
export type CurrentUser = {
  userId: string;
  username: string;
  unifiedAuthId: string;
  organization?: string | null;
  rdDepartment?: string | null;
  department?: string | null;
  roles?: string[];
  roleLabels?: string[];
};

// ---- 应用配置管理类型 ----

export type ApplicationDefinition = {
  appId: string;
  appName: string;
  enabled: boolean;
};

export type CreateApplicationPayload = {
  appId: string;
  appName: string;
};

export type PlatformUserSummary = {
  userId: string;
  username: string;
  unifiedAuthId: string;
  organization?: string | null;
  rdDepartment?: string | null;
  department?: string | null;
};

export type ApplicationMember = PlatformUserSummary;

export type UserManagementUser = PlatformUserSummary & {
  status: string;
  roles: string[];
  roleLabels?: string[];
  createdAt: string;
  updatedAt?: string;
};

export type CreateUserPayload = {
  unifiedAuthId: string;
  username: string;
  role: string;
  organization?: string | null;
  rdDepartment?: string | null;
  department?: string | null;
};

export type UpdateUserRolePayload = {
  role: string;
};

export type RoleOption = {
  roleCode: string;
  roleLabel: string;
};

/** 数据库 identity 运维：单张表的状态快照。 */
export type IdentityStatus = {
  table: string;
  tableName: string;
  currentValue: number | null;
  maxId: number | null;
  conflict: boolean;
  lastUpdatedAt: string;
};

export type CodeRepositoryConfig = {
  repositoryId: string;
  gitUrl: string;
  name: string;
  englishName?: string | null;
  deploymentMode?: "EXTERNAL" | "INTERNAL" | string | null;
  repositoryType?: string | null;
  repositoryTypeLabel?: string | null;
  standard: boolean;
  createdAt: string;
  updatedAt: string;
};

export type RepositoryTypeOption = {
  typeCode: string;
  typeLabel: string;
};

export type RepositoryDeploymentOption = {
  mode: "EXTERNAL" | "INTERNAL" | string;
  label: string;
};

export type RepositoryDeploymentOptions = {
  defaultDeploymentMode: "EXTERNAL" | "INTERNAL" | string;
  internalSshPrefix: string;
  options: RepositoryDeploymentOption[];
};

export type ApplicationWorkspaceConfig = {
  workspaceId: string;
  appId: string;
  repositoryId: string;
  branch: string;
  directoryPath: string;
  workspaceName: string;
  initialVersion?: ApplicationWorkspaceVersion | null;
  createdAt: string;
  updatedAt: string;
};

export type WorkspaceCreateOperationStep = {
  code: string;
  name: string;
  status: "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | string;
};

export type WorkspaceCreateOperation = {
  operationId: string;
  status: "RUNNING" | "SUCCEEDED" | "FAILED" | string;
  currentStep: string;
  errorCode?: string | null;
  errorMessage?: string | null;
  workspaceId?: string | null;
  versionId?: string | null;
  steps: WorkspaceCreateOperationStep[];
  createdAt?: string;
  updatedAt?: string;
};

export type RepositoryTreeNode = {
  name: string;
  path: string;
  type: "directory" | "file";
  children?: RepositoryTreeNode[];
};

export type RepositoryTreeResponse = {
  nodes: RepositoryTreeNode[];
};

/** 工作空间创建已接受的响应（异步模式） */
export type CreateWorkspaceAcceptedResponse = {
  operationId: string;
  status: "ACCEPTED";
  createdAt: string;
};

export type ManagedApplication = ApplicationDefinition;

export type ManagedWorkspaceRuntime = Workspace;

export type ApplicationWorkspaceTemplate = ApplicationWorkspaceConfig & {
  standard: boolean;
};

export type ApplicationWorkspaceVersion = {
  versionId: string;
  applicationWorkspaceId: string;
  appId: string;
  repositoryId: string;
  version: string;
  branch: string;
  repoRootPath: string;
  workspaceRootPath: string;
  runtimeWorkspace: ManagedWorkspaceRuntime;
  status: string;
  targetCommitHash?: string | null;
  replicaCommitHash?: string | null;
  replicaLinuxServerId?: string | null;
  replicaStatus?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PersonalWorkspace = {
  personalWorkspaceId: string;
  versionId: string;
  appId: string;
  applicationWorkspaceId: string;
  workspaceName: string;
  branch: string;
  repoRootPath: string;
  workspaceRootPath: string;
  runtimeWorkspace: ManagedWorkspaceRuntime;
  baseCommit: string;
  status: string;
  createdAt: string;
  updatedAt: string;
};

// 用户在 (appId, workspaceId) 维度下最近一次手动选择的 VCS 分支，
// 用于工作区下分支选择按钮的"下次进入默认切换"持久化。
export type WorkspaceBranchPreference = {
  appId: string;
  workspaceId: string;
  branch: string;
  updatedAt: string;
};

export type WorkspaceDiffFile = {
  path: string;
  status: string;
  conflict: boolean;
};

export type WorkspaceDiff = {
  files: WorkspaceDiffFile[];
};

export type WorkspaceSyncResult = {
  syncRecordId: string;
  status: string;
  files: string[];
  force: boolean;
};

export type SshKeyMetadata = {
  sshKeyId: string;
  name: string;
  fingerprint: string;
  createdAt: string;
};

/** 服务端 RSA 公钥响应，前端用于混合加密 SSH 私钥。 */
export type SshKeyPublicKeyResponse = {
  publicKey: string;
};

export type CreateRepositoryPayload = {
  gitUrl: string;
  name: string;
  englishName: string;
  deploymentMode?: "EXTERNAL" | "INTERNAL" | string;
  repositoryType?: string;
  standard?: boolean;
};

export type UpdateRepositoryPayload = {
  name: string;
  englishName: string;
  standard?: boolean;
};

export type CreateApplicationWorkspacePayload = {
  repositoryId: string;
  branch: string;
  directoryPath: string;
  workspaceName?: string;
  directoryNew?: boolean;
  version?: string;
  operationId?: string;
};

export type RenameApplicationWorkspacePayload = {
  workspaceName: string;
};

export type AddSshKeyPayload = {
  name: string;
  encryptedPrivateKey: string;
  encryptedAesKey: string;
  encryptionNonce: string;
  fingerprint: string;
};

export type CreateWorkspaceVersionPayload = {
  version: string;
  branch?: string;
};

export type CreatePersonalWorkspacePayload = {
  workspaceName: string;
};

export type SyncWorkspacePayload = {
  files: string[];
  force?: boolean;
};

export type PublishPersonalWorkspacePayload = {
  commitMessage: string;
  files: string[];
  expectedApplicationHead?: string;
  operationId?: string;
};

/** 个人工作区发布结果 */
export type PublishPersonalWorkspaceResult = {
  status: "LOCAL_COMMITTED" | "PUBLISHED" | "MERGED" | "CONFLICT";
  personalWorkspaceId: string;
  versionId: string;
  conflictFiles: string[];
  message: string;
  remotePushed?: boolean;
  headCommit?: string | null;
  executedCommands?: string[];
  currentStep?: "PREPARE_REMOTE" | "PROJECT_HEAD" | "COMMIT_FEATURE" | "PUSH_REMOTE" | "COMPLETED"
    | "COMMIT_LOCAL" | "MERGE_PERSONAL" | "MERGE_APPLICATION" | string | null;
};

export type PublishPersonalWorkspacePreview = {
  applicationHead: string;
  personalHead: string;
  incomingCommitCount: number;
  changedFileCount: number;
  addedCount: number;
  modifiedCount: number;
  deletedCount: number;
  renamedCount: number;
  samplePaths: string[];
};

export type WorkspaceGitConflict = {
  path: string;
  rawStatus: string;
  baseContent?: string | null;
  currentContent?: string | null;
  incomingContent?: string | null;
  resultContent?: string | null;
};

export type WorkspaceGitConflictResolution = "CURRENT" | "INCOMING" | "BOTH" | "MANUAL" | "DELETE";

export type ResolveWorkspaceGitConflictPayload = {
  path: string;
  resolution: WorkspaceGitConflictResolution;
  content?: string | null;
};

export type ResolveAllWorkspaceGitConflictsPayload = {
  resolution: "CURRENT" | "INCOMING";
};

/** 确保默认个人工作区响应 */
export type DefaultPersonalWorkspaceResponse = {
  personalWorkspaceId: string;
  personalWorkspaceName: string;
  personalWorkspaceBranch: string;
  runtimeWorkspace: ManagedWorkspaceRuntime;
};

/** 本地 Git diff 文件（不依赖 opencode） */
export type WorkspaceGitDiffFile = {
  path: string;
  rawStatus?: string;
  status: string;
  staged: boolean;
  patch: string;
  additions: number;
  deletions: number;
};

export type WorkspaceGitDiff = {
  files: WorkspaceGitDiffFile[];
};
