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
  createdAt: string;
  updatedAt: string;
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
};

export type SessionMessage = {
  messageId: string;
  sessionId: string;
  role: "USER" | "ASSISTANT" | "SYSTEM" | string;
  content: string;
  createdAt: string;
};

export type Run = {
  runId: string;
  sessionId: string;
  workspaceId: string;
  status: "PENDING" | "RUNNING" | "CANCELLING" | "SUCCEEDED" | "FAILED" | "CANCELLED" | string;
  createdAt: string;
  updatedAt: string;
};

export type RunEventType =
  | "run.created"
  | "run.started"
  | "run.cancelling"
  | "run.succeeded"
  | "run.failed"
  | "run.cancelled"
  | "assistant.message.delta"
  | "message.updated"
  | "message.removed"
  | "message.part.updated"
  | "message.part.removed"
  | "message.part.delta"
  | "session.diff"
  | "session.status"
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
      source?: { start?: number; end?: number; text?: string };
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
  questions: Array<{
    questionId: string;
    text: string;
    kind: "single" | "multiple" | "text" | string;
    options?: Array<{ id: string; label: string; description?: string }>;
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
  status: "pending" | "in_progress" | "completed" | string;
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

export type TerminalTicketResponse = {
  ticket: string;
  expiresAt: string;
  webSocketUrl: string;
};

export type AgentMessage =
  | { id: string; role: "user"; text: string; parts?: PromptPart[]; createdAt: string; messageId?: string }
  | { id: string; role: "assistant"; text: string; parts?: MessagePart[]; createdAt: string; messageId?: string }
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
 */
export type CurrentUser = {
  userId: string;
  username: string;
  unifiedAuthId: string;
  organization?: string | null;
  rdDepartment?: string | null;
  department?: string | null;
  roles?: string[];
};

// ---- 应用配置管理类型 ----

export type ApplicationDefinition = {
  appId: string;
  appName: string;
  enabled: boolean;
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

export type CodeRepositoryConfig = {
  repositoryId: string;
  gitUrl: string;
  name: string;
  standard: boolean;
  createdAt: string;
  updatedAt: string;
};

export type ApplicationWorkspaceConfig = {
  workspaceId: string;
  appId: string;
  repositoryId: string;
  branch: string;
  directoryPath: string;
  workspaceName: string;
  createdAt: string;
  updatedAt: string;
};

export type ManagedApplication = ApplicationDefinition;

export type ManagedWorkspaceRuntime = Workspace;

export type ApplicationWorkspaceTemplate = ApplicationWorkspaceConfig;

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

export type CreateRepositoryPayload = {
  gitUrl: string;
  name: string;
  standard?: boolean;
};

export type UpdateRepositoryPayload = {
  name: string;
  standard?: boolean;
};

export type CreateApplicationWorkspacePayload = {
  repositoryId: string;
  branch: string;
  directoryPath: string;
  workspaceName?: string;
};

export type RenameApplicationWorkspacePayload = {
  workspaceName: string;
};

export type AddSshKeyPayload = {
  name: string;
  privateKey: string;
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
