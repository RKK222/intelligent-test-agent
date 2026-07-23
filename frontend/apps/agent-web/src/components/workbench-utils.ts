import { BackendApiError } from "@test-agent/backend-api";
import type {
  AgentMessage,
  FileSearchResult,
  FileTreeEntry,
  MessageScope,
  MessagePart,
  ModelInfo,
  PromptPart,
  Run,
  RunDiffFile,
  RunEvent,
  RuntimeResourceInfo,
  RuntimeStatus,
  RuntimeToolInfo,
  Session,
  SessionMessage,
  SessionRuntimeState,
  SessionTreeMessagesResponse,
  SubagentSession,
  TodoItem,
  UserOpencodeProcess,
  UserOpencodeProcessHealth,
  UserOpencodeProcessHealthRequest,
  Workspace
} from "@test-agent/shared-types";
import type { AgentChatRuntimeAction, AgentChatRuntimeState, OpencodeLikeRuntimeStatus } from "@test-agent/agent-chat";
import type { EditorSelectionContext } from "@test-agent/editor";
import type { Feedback } from "@test-agent/ui-kit";
import {
  buildComposerPromptParts,
  createInitialAgentChatRuntimeState,
  normalizeMessagePart,
  reduceAgentChatRuntime,
  snapshotEventsFromRunReset,
  todoSnapshotsFromMessagesByUserMessageId,
  type ComposerAttachment
} from "@test-agent/agent-chat";
import { buildEditorFilePromptPart } from "./prompt-context";

export type WorkspaceRequirementReference = {
  id: string;
  requirementName: string;
  subitemName: string;
  filePaths: string[];
};

/**
 * `.opencode` 已由下方 Agent 配置树专门管理，普通工作空间根目录不重复展示。
 */
export function isWorkspaceAgentConfigPath(path: string): boolean {
  const normalized = path
    .replaceAll("\\", "/")
    .trim()
    .replace(/^"|"$/g, "");
  return normalized.split("/").filter(Boolean).includes(".opencode");
}

export function filterWorkspaceRootEntries(path: string, entries: FileTreeEntry[]): FileTreeEntry[] {
  if (path !== "") {
    return entries;
  }
  return entries.filter((entry) => !isWorkspaceAgentConfigPath(entry.name));
}

/** 关闭夜间任务后，只离开由该任务创建且仍无真实消息的空白会话。 */
export function shouldResetAfterNightTaskClosure(
  session: Session | null,
  taskId: string,
  persistedMessageCount: number
): boolean {
  return persistedMessageCount === 0
    && session?.sourceType === "SCHEDULED_TASK"
    && session.sourceRefId === taskId;
}

export const workspaceRequirementStageDirectories = ["01-需求", "02-设计", "03-编码", "04-测试"] as const;
const workspaceRequirementStages = new Set<string>(workspaceRequirementStageDirectories);

/**
 * 将当前个人 worktree 的文件搜索结果按“spec/需求项/阶段/子条目”聚合。
 * 同名子条目可分布在需求、设计、编码、测试阶段；其下所有文件最终均作为同一条子条目上下文。
 * spec 是唯一合法上级目录，旧的根目录需求结构不再兼容。
 */
export function workspaceRequirementReferences(results: FileSearchResult[]): WorkspaceRequirementReference[] {
  const references = new Map<string, WorkspaceRequirementReference>();
  for (const result of results) {
    const parts = result.path.replace(/\\/g, "/").split("/").filter(Boolean);
    const stageName = parts[2];
    if (parts[0] !== "spec" || !stageName || !workspaceRequirementStages.has(stageName) || parts.length < 5) {
      continue;
    }
    const requirementName = parts[1];
    const subitemName = parts[3];
    const id = `spec/${requirementName}/01-需求/${subitemName}`;
    const current = references.get(id) ?? { id, requirementName, subitemName, filePaths: [] };
    if (!current.filePaths.includes(result.path)) {
      current.filePaths.push(result.path);
    }
    references.set(id, current);
  }
  return Array.from(references.values())
    .map((reference) => ({ ...reference, filePaths: reference.filePaths.sort() }))
    .sort((left, right) => left.id.localeCompare(right.id, "zh-CN"));
}

/**
 * 异步文件树请求只能写回发起时的 Workspace 代际，避免旧重试覆盖新 Workspace。
 */
export function workspaceLoadIsCurrent(
  requestWorkspaceId: string,
  requestGeneration: number,
  selectedWorkspaceId: string | undefined,
  currentGeneration: number
): boolean {
  return requestWorkspaceId === selectedWorkspaceId && requestGeneration === currentGeneration;
}

/**
 * RunEvent 只能更新当前订阅且仍是当前页面活动的 Run，防止旧 SSE 关闭窗口内的晚到终态污染新一轮。
 */
export function runEventMatchesRun(
  event: Pick<RunEvent, "runId">,
  subscribedRunId: string | undefined,
  currentRun: Pick<Run, "runId"> | null | undefined
): boolean {
  return Boolean(event.runId && subscribedRunId && currentRun?.runId && event.runId === subscribedRunId && event.runId === currentRun.runId);
}

/**
 * RunEvent 订阅只暴露稳定标量身份；Run 对象内的 status 等投影变化不能重建同一条 SSE。
 */
export function runEventSubscriptionRunId(
  currentRun: Pick<Run, "runId" | "status"> | null | undefined,
  pendingTitleRunId: string | null | undefined,
  terminalSettleRunId: string | null | undefined
): string | undefined {
  if (!currentRun) {
    return undefined;
  }
  const busy = currentRun.status === "PENDING"
    || currentRun.status === "QUEUED"
    || currentRun.status === "RUNNING"
    || currentRun.status === "CANCELLING";
  return busy || pendingTitleRunId === currentRun.runId || terminalSettleRunId === currentRun.runId
    ? currentRun.runId
    : undefined;
}

/** 平台会话切换时先撤销旧 Run 身份，避免旧连接晚到事件投影到新会话。 */
export function runEventSubscriptionSessionId(
  subscribedRunId: string | undefined,
  currentRun: Pick<Run, "runId" | "sessionId"> | null | undefined,
  currentSessionId: string | undefined
): string | undefined {
  return subscribedRunId
    && currentRun?.runId === subscribedRunId
    && currentRun.sessionId === currentSessionId
    ? currentRun.sessionId
    : undefined;
}

export type RunEventProjectionMode = "conversation" | "title-only" | "ignore";

/**
 * 新 Run 请求发出后，旧 Run 可能仍为标题同步保持 SSE 订阅。旧 Run 只允许 session.updated
 * 继续更新标题，其余对话、Todo、快照与终态全部在进入 reducer 前截断。
 */
export function runEventProjectionMode(
  event: Pick<RunEvent, "runId" | "type">,
  subscribedRunId: string | undefined,
  currentRun: Pick<Run, "runId"> | null | undefined,
  supersededRunId: string | null | undefined
): RunEventProjectionMode {
  if (!runEventMatchesRun(event, subscribedRunId, currentRun)) {
    return "ignore";
  }
  if (event.runId !== supersededRunId) {
    return "conversation";
  }
  return event.type === "session.updated" ? "title-only" : "ignore";
}

/**
 * 历史切换后旧 SSE 的关闭回调可能晚到；标题只能写回仍指向该订阅平台会话的当前页面。
 */
export function sessionTitleEventMatchesCurrentSession(
  subscribedSessionId: string | undefined,
  currentSessionId: string | undefined
): boolean {
  return Boolean(subscribedSessionId && currentSessionId && subscribedSessionId === currentSessionId);
}

/**
 * Workbench 将 reset wrapper 与需要重建独立副作用状态的快照事件拆开。
 * reducer 会原子重放对话状态；这里的 events 仅供 Diff、Run 终态等页面投影按原顺序同步。
 */
export function runEventProjection(event: RunEvent): { reset: boolean; events: RunEvent[] } {
  if (event.type !== "run.snapshot.reset") {
    return { reset: false, events: [event] };
  }
  return { reset: true, events: snapshotEventsFromRunReset(event) };
}

/**
 * 根 Run 的交互事件携带 OpenCode remote sessionId，而对话面板按平台 Session ID 分组。
 * 已知当前订阅对应的平台 Session 时，在进入 reducer 前完成映射；子 Agent 事件保留 remote ID，
 * 使子时间线仍只展示属于自己的 permission/question。
 */
export function projectRootInteractionSession(event: RunEvent, platformSessionId?: string): RunEvent {
  if (event.type === "run.snapshot.reset" && platformSessionId) {
    const snapshot = record(event.payload.snapshot);
    if (!snapshot || !Array.isArray(snapshot.events)) return event;
    const snapshotEvents = snapshotEventsFromRunReset(event);
    const projectedEvents = snapshotEvents.map((snapshotEvent) =>
      projectRootInteractionSession(snapshotEvent, platformSessionId)
    );
    if (projectedEvents.every((snapshotEvent, index) => snapshotEvent === snapshotEvents[index])) {
      return event;
    }
    return {
      ...event,
      payload: {
        ...event.payload,
        snapshot: {
          ...snapshot,
          events: projectedEvents
        }
      }
    };
  }
  if (
    (event.type !== "permission.asked" && event.type !== "question.asked")
    || !platformSessionId
    || event.payload.isChildSession === true
  ) {
    return event;
  }
  const remoteSessionId = typeof event.payload.sessionId === "string"
    ? event.payload.sessionId
    : typeof event.payload.sessionID === "string"
      ? event.payload.sessionID
      : undefined;
  if (!remoteSessionId || remoteSessionId === platformSessionId) {
    return event;
  }
  return {
    ...event,
    payload: {
      ...event.payload,
      sessionId: platformSessionId,
      remoteSessionId
    }
  };
}

/**
 * 历史切换已从 OpenCode 读取到 pending 交互快照时，忽略早于该快照且不在快照中的旧 ask 回放。
 * 这样不会吞掉快照之后刚产生的真实提问或权限请求。
 */
export function isSupersededInteractionAsk(
  event: RunEvent,
  synchronizedAtMs: number | undefined,
  pendingRequestIds: ReadonlySet<string> | undefined
): boolean {
  if (
    (event.type !== "permission.asked" && event.type !== "question.asked")
    || synchronizedAtMs === undefined
    || !pendingRequestIds
  ) {
    return false;
  }
  const requestId = text(event.payload.requestId) ?? text(event.payload.requestID) ?? text(event.payload.id);
  const occurredAtMs = Date.parse(event.occurredAt);
  return Boolean(
    requestId
    && Number.isFinite(occurredAtMs)
    && occurredAtMs <= synchronizedAtMs
    && !pendingRequestIds.has(requestId)
  );
}

/** 新模式在 run.created 中下发稳定反馈 ID；只接受平台 msg UUID 形状，避免误用远端 message id。 */
export function assistantSummaryMessageId(payload: Record<string, unknown>): string | undefined {
  const candidate = payload.assistantSummaryMessageId;
  return typeof candidate === "string" && /^msg_[0-9a-f]{32}$/i.test(candidate)
    ? candidate
    : undefined;
}

export const OPENCODE_HEALTH_REFETCH_INTERVAL_MS = 10_000;
export const PUBLIC_CONFIG_GATE_REFETCH_INTERVAL_MS = 5_000;

/** 轻量消息闸门查询的固定轮询间隔。 */
export function publicConfigGateRefetchInterval(
  _process: Partial<UserOpencodeProcess> | null | undefined
): number | false {
  return PUBLIC_CONFIG_GATE_REFETCH_INTERVAL_MS;
}
export const OPENCODE_RUNTIME_CAPABILITY_REFETCH_INTERVAL_MS = 300_000;
export const OPENCODE_VCS_STATUS_REFETCH_INTERVAL_MS = 30_000;

export type OpencodeAvailabilitySource = "process" | "health";
export type OpencodeAvailabilityState = {
  ready: boolean;
  source: OpencodeAvailabilitySource;
};

/**
 * 弱健康检查必须基于 /processes/me 返回的分配归属，缺任一字段时不发请求。
 */
export function opencodeHealthRequestFromProcess(
  process: Partial<UserOpencodeProcess> | null | undefined
): UserOpencodeProcessHealthRequest | null {
  if (!process?.linuxServerId || !process.containerId || !Number.isInteger(process.port) || (process.port as number) <= 0) {
    return null;
  }
  return {
    linuxServerId: process.linuxServerId,
    containerId: process.containerId,
    port: process.port as number
  };
}

/**
 * /processes/me 是强状态查询；它一旦返回，就覆盖当前前端 readiness。
 */
export function opencodeAvailabilityFromProcess(
  process: Partial<UserOpencodeProcess> | null | undefined
): OpencodeAvailabilityState {
  return { ready: process?.status === "READY", source: "process" };
}

/**
 * 弱健康检查是常态 readiness 来源；只更新健康态，不改变进程归属。
 */
export function opencodeAvailabilityFromHealth(
  health: Partial<UserOpencodeProcessHealth> | null | undefined
): OpencodeAvailabilityState {
  return { ready: Boolean(health?.healthy), source: "health" };
}

export type RetryDeadlineMap = Record<string, number>;
export type RetryExpirationDecision = "wait" | "retry" | "fail";
export type AutoRetryRunDraft = {
  prompt: string;
  parts: PromptPart[];
  userMessageId: string;
  title?: string;
  command?: { command: string; arguments: string };
};
export type AutoRetryRunPreparation =
  | { type: "missing-draft" }
  | { type: "start"; input: AutoRetryRunDraft; cancelRunId?: string; localRun?: Run };

const DEFAULT_RETRY_WAIT_SECONDS = 60;
const DEFAULT_RETRY_MAX_ATTEMPTS = 3;

/**
 * retry 事件可能来自 SSE 重放，不能用 occurredAt 推导倒计时。
 * 同一个 retryKey 首次进入前端时固定记录本地 deadline，后续 tick 只读取该 deadline。
 */
export function resolveRetryDeadline(
  deadlines: RetryDeadlineMap,
  retryRuntimeStatus: OpencodeLikeRuntimeStatus | undefined,
  nowMs = Date.now()
): { deadlines: RetryDeadlineMap; deadlineMs?: number } {
  if (!retryRuntimeStatus || retryRuntimeStatus.type !== "retry") {
    return { deadlines };
  }
  const key = retryDeadlineKey(retryRuntimeStatus);
  const existing = key ? deadlines[key] : undefined;
  if (Number.isFinite(existing)) {
    return { deadlines, deadlineMs: existing };
  }
  const waitSeconds = Math.max(0, retryRuntimeStatus.retryAfterSeconds ?? DEFAULT_RETRY_WAIT_SECONDS);
  const deadlineMs = nowMs + waitSeconds * 1000;
  return key
    ? { deadlines: { ...deadlines, [key]: deadlineMs }, deadlineMs }
    : { deadlines, deadlineMs };
}

export function retryCountdownSeconds(
  retryRuntimeStatus: OpencodeLikeRuntimeStatus | undefined,
  nowMs = Date.now(),
  deadlines: RetryDeadlineMap = {}
): number {
  const { deadlineMs } = resolveRetryDeadline(deadlines, retryRuntimeStatus, nowMs);
  if (!Number.isFinite(deadlineMs)) {
    return 0;
  }
  return Math.max(0, Math.ceil(((deadlineMs as number) - nowMs) / 1000));
}

export function retryExpirationDecision(
  retryRuntimeStatus: OpencodeLikeRuntimeStatus | undefined,
  nowMs = Date.now(),
  deadlines: RetryDeadlineMap = {}
): RetryExpirationDecision {
  if (!retryRuntimeStatus || retryRuntimeStatus.type !== "retry" || retryCountdownSeconds(retryRuntimeStatus, nowMs, deadlines) > 0) {
    return "wait";
  }
  const attempt = retryRuntimeStatus.attempt ?? 0;
  const maxAttempts = retryRuntimeStatus.maxAttempts ?? DEFAULT_RETRY_MAX_ATTEMPTS;
  return attempt >= maxAttempts ? "fail" : "retry";
}

export function shouldFailExhaustedRetry(
  retryRuntimeStatus: OpencodeLikeRuntimeStatus | undefined,
  nowMs = Date.now(),
  deadlines: RetryDeadlineMap = {}
): boolean {
  return retryExpirationDecision(retryRuntimeStatus, nowMs, deadlines) === "fail";
}

export function prepareAutoRetryRun(
  currentRun: Run | null | undefined,
  draft: AutoRetryRunDraft | null | undefined,
  updatedAt = new Date().toISOString()
): AutoRetryRunPreparation {
  if (!draft || !draft.prompt.trim()) {
    return { type: "missing-draft" };
  }
  if (currentRun && autoRetryRunIsBusyStatus(currentRun.status)) {
    return {
      type: "start",
      input: draft,
      cancelRunId: currentRun.runId,
      localRun: { ...currentRun, status: "CANCELLED", updatedAt }
    };
  }
  return { type: "start", input: draft };
}

function retryDeadlineKey(retryRuntimeStatus: OpencodeLikeRuntimeStatus): string {
  return retryRuntimeStatus.retryKey ?? `${retryRuntimeStatus.attempt ?? 0}:${retryRuntimeStatus.message ?? ""}`;
}

function autoRetryRunIsBusyStatus(status: Run["status"] | string | undefined): boolean {
  return status === "PENDING" || status === "QUEUED" || status === "RUNNING" || status === "CANCELLING";
}

type WorkbenchCenterMode = "editor" | "diff" | "system";
type WorkbenchDiffSource = "run" | "session" | "vcs" | "agent";

/**
 * VCS 文件被全部回退后，中心区不能继续停留在空 Diff 面板，否则会显示旧工具栏和“暂无 Diff”。
 */
export function nextCenterModeAfterVcsRefresh(
  currentMode: WorkbenchCenterMode,
  diffSource: WorkbenchDiffSource,
  files: RunDiffFile[]
): WorkbenchCenterMode {
  if (currentMode === "diff" && diffSource === "vcs" && files.length === 0) {
    return "editor";
  }
  return currentMode;
}

/**
 * 运行中新产出的 Run Diff 只更新右侧文件修改摘要；如果用户正在看 VCS/Agent Diff，
 * 不自动劫持中间区为旧 Run Diff 面板。
 */
export function nextCenterModeAfterRunDiff(
  currentMode: WorkbenchCenterMode,
  previousDiffSource: WorkbenchDiffSource
): WorkbenchCenterMode {
  if (currentMode === "diff" && previousDiffSource !== "run") {
    return "editor";
  }
  return currentMode;
}

export function diffFilesFromPayload(payload: Record<string, unknown>): RunDiffFile[] {
  const raw = Array.isArray(payload.diff) ? payload.diff : Array.isArray(payload.files) ? payload.files : [];
  return raw
    .map((item) => {
      // opencode 的 session.diff 事件 payload.files 是 path 字符串数组，
      // 而后端自生成的 diff.proposed 事件 payload.files 是包含 path/additions/deletions 的对象数组。
      if (typeof item === "string") {
        return item.length > 0 ? { path: item, patch: "", additions: 0, deletions: 0, status: "modified" } : null;
      }
      if (typeof item === "object" && item !== null) {
        const record = item as Record<string, unknown>;
        const path = String(record.path ?? record.file ?? "");
        if (!path) return null;
        return {
          path,
          patch: String(record.patch ?? ""),
          additions: Number(record.additions ?? 0),
          deletions: Number(record.deletions ?? 0),
          status: String(record.status ?? "modified")
        };
      }
      return null;
    })
    .filter((item): item is RunDiffFile => item !== null);
}

// 把新到达的 diff 文件列表按 path 合并到已有列表中：
// - 同 path 用新对象整体覆盖（additions/deletions/patch 用最新值），保留 entries 顺序。
// - 新 path 追加到尾部。
// 用于解决后端 `edit` 工具的 `diff.proposed` 事件只携带"本工具刚编辑的单个文件"、
// 多次事件直接替换会导致前面改过的文件被丢失的问题。
//
// 去重 key 用 normalizePathKey 统一：把 Windows 反斜杠、git a/ b/ 前缀、
// 末尾斜杠等都折叠成同一种形态，避免 "D:\\workspace\\vue\\src\\App.vue" 和
// "src/App.vue" 被误当成两个文件。
export function mergeDiffFiles(current: RunDiffFile[], incoming: RunDiffFile[]): RunDiffFile[] {
  if (incoming.length === 0) {
    return current;
  }
  const map = new Map<string, RunDiffFile>();
  for (const file of current) {
    const key = normalizePathKey(file.path);
    if (key) map.set(key, file);
  }
  for (const file of incoming) {
    const key = normalizePathKey(file.path);
    if (key) map.set(key, file);
  }
  return Array.from(map.values());
}

// 把任意形态的文件路径归一化为 mergeDiffFiles / FigmaChatPanel 抽屉的去重 key：
// - 去除 git diff 前缀 "a/" / "b/"
// - 统一反斜杠为正斜杠
// - 去除前导 "./"、尾部斜杠
// - 折叠重复斜杠
// 该函数无 workspace 上下文依赖（不在此处剥离 rootPath），仅负责形态归一化；
// workspace 相对化交给 AgentWorkbench 的 normalizeWorkspacePath。
export function normalizePathKey(raw: string | undefined): string {
  if (!raw) return "";
  // 必须先把反斜杠折叠成正斜杠，a/ b/ 前缀匹配才能命中 "b\\src\\App.vue" 这类路径。
  let p = raw.replace(/\\/g, "/").replace(/^([ab])\//, "").trim();
  while (p.startsWith("./")) p = p.slice(2);
  p = p.replace(/\/+$/, "");
  p = p.replace(/\/+/g, "/");
  return p.toLowerCase();
}

// 已知会落盘的工具名集合，与 AgentWorkbench 中的 LIVE_WRITE_TOOLS 保持一致。
const DIFF_AWARE_TOOL_NAMES = new Set([
  "write",
  "edit",
  "apply_patch",
  "str_replace",
  "multi_edit",
  "create_file"
]);

// 从 tool part 的 input/metadata 推断出本工具的 RunDiffFile。
// opencode 1.18.4 的 `session.diff` 事件在普通 summarize 流程中只发空 diff 数组，
// 导致前端"文件变更"卡片 +N 永远显示 0。这里基于工具的入参估算新增/删除行数，
// 让卡片在写文件工具完成时也能即时反映本次改动，并补一个合成的 unified diff
// 文本让"文件变更"抽屉的右侧 diff 视图能立刻渲染内容，而不是显示"暂无 diff 内容"。
//
// 返回 undefined 表示非写文件工具或参数不足，前端应保持原 diffFiles 不变。
export function inferDiffFromToolPart(part: Extract<MessagePart, { type: "tool" }>): RunDiffFile | undefined {
  const toolName = (part.toolName ?? "").toLowerCase();
  if (!DIFF_AWARE_TOOL_NAMES.has(toolName)) {
    return undefined;
  }
  const input = (part.input ?? {}) as Record<string, unknown>;
  const metadata = (part.metadata ?? {}) as Record<string, unknown>;
  const filePath =
    text(input.filePath) ??
    text(input.path) ??
    text(input.file) ??
    text(metadata.filepath) ??
    text(metadata.filePath) ??
    text(metadata.path) ??
    text(metadata.file);
  if (!filePath) {
    return undefined;
  }

  if (toolName === "edit" || toolName === "str_replace" || toolName === "multi_edit") {
    const oldText = text(input.oldString) ?? text(input.old_string) ?? text(input.oldText) ?? "";
    const newText = text(input.newString) ?? text(input.new_string) ?? text(input.newText) ?? "";
    const oldLines = splitLines(oldText);
    const newLines = splitLines(newText);
    const additions = newLines.length;
    const deletions = oldLines.length;
    const patch = buildEditPatch(filePath, oldText, newText);
    return {
      path: filePath,
      patch,
      additions,
      deletions,
      status: "modified"
    };
  }

  if (toolName === "apply_patch") {
    const patchText =
      text(input.input) ?? text(input.patch) ?? text(input.patchText) ?? text(input.patch_text) ?? "";
    if (!patchText) {
      return undefined;
    }
    const { additions, deletions } = countPatchStats(patchText);
    return {
      path: filePath,
      patch: patchText,
      additions,
      deletions,
      status: "modified"
    };
  }

  // write / create_file：纯新增场景，把新增内容按行数累加成 additions，
  // deletions 留 0（写入时是否覆盖旧内容前端无法可靠判断，留 0 与平台"新增"语义一致）。
  // 合成 unified diff 文本让"文件变更抽屉"右侧能渲染为全 +N 行的视图。
  const content = text(input.content) ?? text(input.text) ?? "";
  return {
    path: filePath,
    patch: buildWritePatch(filePath, content),
    additions: countAddedLines(content),
    deletions: 0,
    status: "added"
  };
}

// 按 \n / \r\n 拆分并保留尾部空行外的所有行；与 opencode 内部行数统计保持一致。
function splitLines(text: string): string[] {
  if (!text) return [];
  return text.split(/\r?\n/);
}

// 为 write / create_file 工具合成 unified diff：--- /dev/null → +++ b/<path>，
// @@ -0,0 +1,N @@，N 为内容总行数；body 每行加 + 前缀。
// 抽屉的 parseDiffLines 会按 + 行累加，additions 计数与 FileChangeStat.additions 一致。
function buildWritePatch(filePath: string, content: string): string {
  const lines = splitLines(content);
  // 文件头里只暴露 basename，避免与"+ b/<path>"的 git 风格重复
  const displayPath = filePath.split(/[\\/]/).filter(Boolean).pop() ?? filePath;
  const header = `--- /dev/null\n+++ b/${displayPath}\n@@ -0,0 +1,${lines.length} @@`;
  if (lines.length === 0) return header;
  const body = lines.map((line) => `+${line}`).join("\n");
  return `${header}\n${body}`;
}

// 为 edit / str_replace / multi_edit 工具合成 unified diff：oldString 作为 - 行，
// newString 作为 + 行；行号用 1 起估算，与 git apply 兼容。
// 真实编辑点位置无法在客户端还原（只看到 oldString/newString 字符串），
// 但作为可视化 diff 已经足够覆盖"新增/删除行数"和"内容预览"两个用途。
function buildEditPatch(filePath: string, oldText: string, newText: string): string {
  const oldLines = splitLines(oldText);
  const newLines = splitLines(newText);
  const displayPath = filePath.split(/[\\/]/).filter(Boolean).pop() ?? filePath;
  const header = `--- a/${displayPath}\n+++ b/${displayPath}\n@@ -1,${oldLines.length} +1,${newLines.length} @@`;
  if (oldLines.length === 0 && newLines.length === 0) return header;
  const oldBlock = oldLines.map((line) => `-${line}`).join("\n");
  const newBlock = newLines.map((line) => `+${line}`).join("\n");
  if (oldLines.length === 0) return `${header}\n${newBlock}`;
  if (newLines.length === 0) return `${header}\n${oldBlock}`;
  return `${header}\n${oldBlock}\n${newBlock}`;
}

// 统计非空行数（与 opencode 内部 addLines 语义一致，忽略末尾空行）。
// 空字符串视作 0 行；保留原 trim 行为以便工具入参为空时也返回 0。
function countAddedLines(text: string): number {
  if (!text) return 0;
  return text.split("\n").filter((line) => line.length > 0).length;
}

// 解析 unified diff 文本，按行前缀统计 +/- 行数。
// 兼容 git apply 格式：@@、---、+++ 等元行不计入；新增行以 + 开头（首个字符不是 +++）。
function countPatchStats(patch: string): { additions: number; deletions: number } {
  let additions = 0;
  let deletions = 0;
  for (const raw of patch.split("\n")) {
    if (raw.startsWith("+++") || raw.startsWith("---") || raw.startsWith("@@") || raw.startsWith("\\")) {
      continue;
    }
    if (raw.startsWith("+")) {
      additions += 1;
    } else if (raw.startsWith("-")) {
      deletions += 1;
    }
  }
  return { additions, deletions };
}

export function errorFeedback(title: string, error: unknown, fallbackContext: Record<string, unknown> = {}): Feedback {
  if (error instanceof BackendApiError) {
    const loadingContext = formatLoadingContext(error.details, fallbackContext);
    return {
      kind: "error",
      title,
      description: [`${error.code}: ${error.message}`, loadingContext].filter(Boolean).join("；"),
      traceId: error.traceId
    };
  }
  return { kind: "error", title, description: error instanceof Error ? error.message : "未知错误" };
}

function formatLoadingContext(details: Record<string, unknown>, fallbackContext: Record<string, unknown>): string {
  const merged = { ...fallbackContext, ...details };
  const hasContext = ["appId", "appName", "version", "versionId", "workspaceKind", "workspaceName", "workspaceId", "personalWorkspaceId"]
    .some((key) => displayValue(merged[key]) !== undefined);
  if (!hasContext) {
    return "";
  }
  const appId = displayValue(merged.appId) ?? "未确定";
  const appName = displayValue(merged.appName) ?? "未确定";
  const version = displayValue(merged.version) ?? displayValue(merged.versionId) ?? "未确定";
  const workspaceKind = displayValue(merged.workspaceKind) ?? "未确定";
  const workspaceName = displayValue(merged.workspaceName) ?? displayValue(merged.workspaceId) ?? "未确定";
  const extraEntries: Array<[string, unknown]> = [
    ["versionId", merged.versionId],
    ["applicationWorkspaceId", merged.applicationWorkspaceId],
    ["workspaceId", merged.workspaceId],
    ["personalWorkspaceId", merged.personalWorkspaceId]
  ];
  const extras = extraEntries
    .map(([label, value]) => {
      const display = displayValue(value);
      return display ? `${label}: ${display}` : "";
    })
    .filter((value): value is string => Boolean(value));
  return [`应用 ${appName}(${appId})`, `版本 ${version}`, `工作区 ${workspaceKind}:${workspaceName}`, ...extras].join("；");
}

function displayValue(value: unknown): string | undefined {
  if (typeof value === "string" && value.trim()) return value.trim();
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return undefined;
}

export function historyItems(run: Run | null, sessions: Session[], runtimeStatesBySessionId: Record<string, SessionRuntimeState> = {}) {
  void run;
  return sessions.map((item) => {
    const runtimeState = runtimeStatesBySessionId[item.sessionId];
    return {
      id: item.sessionId,
      title: item.title,
      preview: `${item.agent ?? "agent"} ${item.model?.id ?? ""}`.trim() || "Session",
      status: item.status,
      createdAt: item.createdAt,
      updatedAt: item.updatedAt,
      pinned: item.pinned,
      appName: item.workspaceContext?.appName ?? undefined,
      workspaceName: item.workspaceContext?.workspaceName ?? undefined,
      version: item.workspaceContext?.version ?? undefined,
      runtimeState: (runtimeState ? "running" : "completed") as "running" | "completed",
      runId: runtimeState?.runId,
      runStatus: runtimeState?.runStatus,
      pendingQuestion: runtimeState?.attention === "QUESTION",
      pendingAttention: Boolean(runtimeState?.attention),
      attentionEventId: runtimeState?.attentionEventId ?? undefined,
      attentionAt: runtimeState?.attentionAt ?? undefined,
      ...(item.sourceType ? { sourceType: item.sourceType } : {})
    };
  });
}

/**
 * 历史按钮 badge 只表达历史第一页内的后台未完成数量；加载更多后的运行态仍由 historyItems 单独展示。
 */
export function historyRuntimeBadgeCounts(
  sessions: Session[],
  runtimeStatesBySessionId: Record<string, SessionRuntimeState> = {},
  limit: number
) {
  const limitedSessions = sessions.slice(0, Math.max(0, limit));
  let runningCount = 0;
  let questionCount = 0;
  let permissionCount = 0;
  for (const session of limitedSessions) {
    const runtimeState = runtimeStatesBySessionId[session.sessionId];
    if (!runtimeState) {
      continue;
    }
    runningCount += 1;
    if (runtimeState.attention === "QUESTION") {
      questionCount += 1;
    }
    if (runtimeState.attention === "PERMISSION") {
      permissionCount += 1;
    }
  }
  return { runningCount, questionCount, permissionCount };
}

/** 根会话实时快照只替换根 scope，child 的 asked/replied 收敛结果继续以 session tree 为准。 */
export function replaceRootSessionInteractions<T extends { sessionId: string }>(
  restored: T[],
  liveRoot: T[] | null,
  rootSessionId: string
): T[] {
  if (liveRoot === null) return restored;
  return [
    ...restored.filter((item) => item.sessionId !== rootSessionId),
    ...liveRoot.filter((item) => item.sessionId === rootSessionId)
  ];
}

export function dedupeSessionMessages(messages: SessionMessage[]): SessionMessage[] {
  const seen = new Set<string>();
  const deduped: SessionMessage[] = [];
  for (const message of messages) {
    const key = sessionMessageDedupeKey(message);
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    deduped.push(message);
  }
  return deduped;
}

function sessionMessageDedupeKey(message: SessionMessage): string {
  if (message.remoteMessageId) {
    return `remote:${message.sessionId}:${message.role}:${message.remoteMessageId}`;
  }
  const partIds = (message.parts ?? [])
    .map((part, index) => {
      const raw = record(part);
      return text(raw?.partId) ?? text(raw?.partID) ?? text(raw?.id) ?? `part-${index}`;
    })
    .join("|");
  if (partIds) {
    return `parts:${message.sessionId}:${message.role}:${partIds}:${stableStringify(message.parts)}`;
  }
  if (message.role !== "ASSISTANT") {
    return `platform:${message.sessionId}:${message.messageId}`;
  }
  // 重复快照通常由多次刷新插入，createdAt 会不同；缺少远端 ID 时只能按可见内容做只读展示去重。
  return `assistant-content:${message.sessionId}:${message.content}:${stableStringify(message.parts ?? [])}`;
}

export function messagesFromSessionMessages(messages: SessionMessage[]): AgentMessage[] {
  return dedupeSessionMessages(messages).map((message) => {
    const role = message.role === "USER" ? "user" : "assistant";
    if (role === "user") {
      return {
        id: message.messageId,
        messageId: message.messageId,
        platformMessageId: message.messageId,
        remoteMessageId: message.remoteMessageId,
        runId: message.runId,
        ...(message.sourceType ? { sourceType: message.sourceType } : {}),
        ...(message.sourceRefId ? { sourceRefId: message.sourceRefId } : {}),
        role: "user",
        text: message.content,
        parts: normalizeSessionPromptParts(message),
        createdAt: message.createdAt
      };
    }
    return {
      id: message.messageId,
      messageId: message.messageId,
      platformMessageId: message.messageId,
      remoteMessageId: message.remoteMessageId,
      runId: message.runId,
      role: "assistant",
      text: message.content,
      parts: normalizeSessionMessageParts(message),
      tokens: message.tokens,
      createdAt: message.createdAt
    };
  });
}

export function chatStateFromSessionTreeSnapshot(
  snapshot: SessionTreeMessagesResponse,
  persistedMessages: SessionMessage[] = []
): AgentChatRuntimeState {
  let state = createInitialAgentChatRuntimeState();
  const visibleUserTextBySessionId = sessionTreeVisibleUserTextBySessionId(snapshot);
  dedupeSessionTreeEvents(snapshot.events ?? []).forEach((event, index) => {
    const normalizedEvent = sessionTreeEventWithVisibleUserText(event, visibleUserTextBySessionId);
    state = reduceAgentChatRuntime(state, {
      type: "event",
      event: runEventFromSessionTreeEvent(snapshot, normalizedEvent, index)
    });
  });
  state = replaySessionTreeMessagesBySessionId(state, snapshot, visibleUserTextBySessionId);
  state = hydrateSubagentIndexesFromSessionTreeSnapshot(state, snapshot);
  const persistedAgentMessages = messagesFromSessionMessages(persistedMessages);
  if (persistedAgentMessages.length > 0) {
    state = {
      ...state,
      messages: state.messages.length > 0 ? mergeSessionTreeMessages(persistedAgentMessages, state.messages) : persistedAgentMessages
    };
  }
  const hydrated = hydrateSubagentOutputsFromTaskParts(state);
  const rootMessages = hydrated.messages.filter((message) => !isChildScopedMessage(hydrated, message));
  // 持久化 message part 只是历史 fallback；RunEvent reducer 恢复的显式快照优先级更高。
  // 两组分别迁移 alias 后再合并，避免旧平台键抢先命中并删除较新的 remote 键。
  const messageTodoSnapshots = canonicalizeTodoSnapshotUserMessageIds(
    hydrated.messages,
    todoSnapshotsFromMessagesByUserMessageId(rootMessages)
  );
  const eventTodoSnapshots = canonicalizeTodoSnapshotUserMessageIds(
    hydrated.messages,
    hydrated.todoSnapshotsByUserMessageId
  );
  const todoSnapshotsByUserMessageId = { ...messageTodoSnapshots, ...eventTodoSnapshots };
  const latestUserMessageId = latestRootUserMessageId(hydrated);
  // 历史恢复必须按用户轮次读取。全会话“最后一个 Todo”可能仍属于上一轮，不能覆盖最新轮。
  const todos = latestUserMessageId && Object.prototype.hasOwnProperty.call(todoSnapshotsByUserMessageId, latestUserMessageId)
    ? [...todoSnapshotsByUserMessageId[latestUserMessageId]]
    : [];
  return {
    ...hydrated,
    todoSnapshotsByUserMessageId,
    todos
  };
}

/**
 * 平台历史会把 OpenCode user message 合并到稳定 messageId；同步迁移 todo.updated 仍使用的远端键。
 * 对同一用户消息的全部 alias 只保留平台 canonical key，避免时间线读到两份互相矛盾的快照。
 */
function canonicalizeTodoSnapshotUserMessageIds(
  messages: AgentMessage[],
  snapshots: Record<string, TodoItem[]>
): Record<string, TodoItem[]> {
  const canonicalized = { ...snapshots };
  for (const message of messages) {
    if (message.role !== "user") {
      continue;
    }
    const canonicalId = message.messageId ?? message.id;
    const aliases = [...new Set([message.id, message.messageId, message.remoteMessageId].filter(Boolean))] as string[];
    const aliasedSnapshot = aliases.find((alias) => Object.prototype.hasOwnProperty.call(canonicalized, alias));
    if (!Object.prototype.hasOwnProperty.call(canonicalized, canonicalId) && aliasedSnapshot) {
      canonicalized[canonicalId] = canonicalized[aliasedSnapshot];
    }
    for (const alias of aliases) {
      if (alias !== canonicalId) {
        delete canonicalized[alias];
      }
    }
  }
  return canonicalized;
}

/**
 * session Todo HTTP 只有会话维度，没有 Run/用户轮次信息。非空结果仅在最新轮已有显式 Todo
 * 所有权证据时用于校准；空结果只清空当前轮，绝不删除更早轮次快照。
 */
export function reconcileCurrentTurnTodos(
  state: AgentChatRuntimeState,
  liveTodos: AgentChatRuntimeState["todos"]
): AgentChatRuntimeState {
  const latestUserMessageId = latestRootUserMessageId(state);
  if (!latestUserMessageId) {
    return liveTodos.length === 0 ? { ...state, todos: [] } : state;
  }
  const hasOwnershipEvidence = Object.prototype.hasOwnProperty.call(
    state.todoSnapshotsByUserMessageId,
    latestUserMessageId
  ) || Object.values(state.todoUserMessageIdByRunId).includes(latestUserMessageId);
  if (liveTodos.length > 0 && !hasOwnershipEvidence) {
    return state;
  }
  return {
    ...state,
    todos: [...liveTodos],
    todoSnapshotsByUserMessageId: hasOwnershipEvidence
      ? { ...state.todoSnapshotsByUserMessageId, [latestUserMessageId]: [...liveTodos] }
      : state.todoSnapshotsByUserMessageId
  };
}

function latestRootUserMessageId(state: AgentChatRuntimeState): string | undefined {
  for (let index = state.messages.length - 1; index >= 0; index -= 1) {
    const message = state.messages[index];
    if (message.role === "user" && !isChildScopedMessage(state, message)) {
      return message.messageId ?? message.id;
    }
  }
  return undefined;
}

function isChildScopedMessage(state: AgentChatRuntimeState, message: AgentMessage): boolean {
  const messageId = message.role === "card" ? message.id : message.messageId ?? message.id;
  const scope = state.messageScopesById[messageId] ?? state.messageScopesById[message.id];
  return scope?.isChildSession === true;
}

export function messagesFromSessionTreeSnapshot(snapshot: SessionTreeMessagesResponse): AgentMessage[] {
  return chatStateFromSessionTreeSnapshot(snapshot).messages;
}

function replaySessionTreeMessagesBySessionId(
  state: AgentChatRuntimeState,
  snapshot: SessionTreeMessagesResponse,
  visibleUserTextBySessionId: Map<string, Map<string, string>>
): AgentChatRuntimeState {
  let next = state;
  let index = 0;
  for (const [sessionId, payloads] of Object.entries(snapshot.messagesBySessionId ?? {})) {
    if (!Array.isArray(payloads)) {
      continue;
    }
    const visibleUserTextByMessageId = visibleUserTextBySessionId.get(sessionId) ?? new Map<string, string>();
    for (const rawPayload of payloads) {
      const payload = record(rawPayload);
      const message = record(payload?.message) ?? record(payload?.info);
      const part = record(payload?.part);
      if (!payload) {
        continue;
      }
      if (message) {
        const normalized = sessionTreeMessageWithVisibleUserText(payload, message, visibleUserTextByMessageId);
        next = reduceAgentChatRuntime(next, {
          type: "event",
          event: runEventFromSessionTreeMessage(snapshot, sessionId, normalized.payload, normalized.message, index)
        });
      } else if (part) {
        // OpenCode tree 会把 child 输出拆成只含 part 的条目；跳过它会导致子 Agent 卡片可点却没有正文。
        next = reduceAgentChatRuntime(next, {
          type: "event",
          event: runEventFromSessionTreePart(snapshot, sessionId, payload, part, index)
        });
      } else {
        continue;
      }
      index += 1;
    }
  }
  return next;
}

/**
 * events 与 messagesBySessionId 会重复携带同一批历史消息；先按 Session 建索引，保证两次回放使用同一套 user 正文归一化规则。
 */
function sessionTreeVisibleUserTextBySessionId(
  snapshot: SessionTreeMessagesResponse
): Map<string, Map<string, string>> {
  const visibleTextBySessionId = new Map<string, Map<string, string>>();
  for (const [sessionId, payloads] of Object.entries(snapshot.messagesBySessionId ?? {})) {
    if (!Array.isArray(payloads)) {
      continue;
    }
    visibleTextBySessionId.set(sessionId, sessionTreeVisibleUserTextByMessageId(payloads));
  }
  return visibleTextBySessionId;
}

function sessionTreeEventWithVisibleUserText(
  event: SessionTreeMessagesResponse["events"][number],
  visibleUserTextBySessionId: Map<string, Map<string, string>>
): SessionTreeMessagesResponse["events"][number] {
  if (event.type !== "message.updated") {
    return event;
  }
  const payload = record(event.payload);
  const message = record(payload?.message) ?? record(payload?.info);
  const sessionId = text(event.sessionId) ?? text(payload?.sessionId) ?? text(payload?.sessionID);
  const visibleUserTextByMessageId = sessionId ? visibleUserTextBySessionId.get(sessionId) : undefined;
  if (!payload || !message || !visibleUserTextByMessageId) {
    return event;
  }
  const normalized = sessionTreeMessageWithVisibleUserText(payload, message, visibleUserTextByMessageId);
  return normalized.payload === payload ? event : { ...event, payload: normalized.payload };
}

/**
 * OpenCode 的 user message envelope 本身通常没有 content，正文由后续 text part 提供。
 * 历史恢复时先按 messageId 找到首个非 synthetic 文本，避免 envelope 被延迟后 file part 误归到 assistant。
 */
function sessionTreeVisibleUserTextByMessageId(payloads: Record<string, unknown>[]): Map<string, string> {
  const visibleTextByMessageId = new Map<string, string>();
  for (const rawPayload of payloads) {
    const payload = record(rawPayload);
    const part = record(payload?.part);
    if (!payload || !part || text(part.type) !== "text" || part.synthetic === true) {
      continue;
    }
    const messageId =
      text(payload.messageId) ??
      text(payload.messageID) ??
      text(part.messageId) ??
      text(part.messageID);
    const value = text(part.text) ?? text(part.content);
    if (messageId && value && !visibleTextByMessageId.has(messageId)) {
      visibleTextByMessageId.set(messageId, value);
    }
  }
  return visibleTextByMessageId;
}

function sessionTreeMessageWithVisibleUserText(
  payload: Record<string, unknown>,
  message: Record<string, unknown>,
  visibleUserTextByMessageId: Map<string, string>
): { payload: Record<string, unknown>; message: Record<string, unknown> } {
  if (text(message.role) !== "user" || text(message.text) || text(message.content)) {
    return { payload, message };
  }
  const messageId = text(message.messageId) ?? text(message.messageID) ?? text(message.id);
  const visibleText = messageId ? visibleUserTextByMessageId.get(messageId) : undefined;
  if (!visibleText) {
    return { payload, message };
  }
  const normalizedMessage = { ...message, content: visibleText };
  return {
    payload: record(payload.message)
      ? { ...payload, message: normalizedMessage }
      : { ...payload, info: normalizedMessage },
    message: normalizedMessage
  };
}

// 后端 snapshot 会把 message payload 同时按 session 分组；即使 events 被裁剪，也要按原 RunEvent reducer 口径恢复 scope。
function runEventFromSessionTreeMessage(
  snapshot: SessionTreeMessagesResponse,
  sessionId: string,
  payload: Record<string, unknown>,
  message: Record<string, unknown>,
  index: number
): RunEvent {
  const scopedPayload = {
    ...payload,
    sessionId: text(payload.sessionId) ?? sessionId,
    rootSessionId: text(payload.rootSessionId) ?? snapshot.sessionId
  };
  const messageId = text(message.messageId) ?? text(message.messageID) ?? text(message.id) ?? `message-${index + 1}`;
  const occurredAt = text(payload.occurredAt) ?? text(message.createdAt) ?? new Date(0).toISOString();
  return {
    eventId: `session-tree-message:${snapshot.sessionId}:${sessionId}:${messageId}:${index}`,
    runId: text(payload.runId) ?? `session-tree:${snapshot.sessionId}`,
    seq: 10_000 + index,
    type: "message.updated",
    traceId: text(payload.traceId) ?? "trace_session_tree",
    occurredAt,
    payload: scopedPayload
  };
}

/**
 * 把 session-tree 中未携带 message wrapper 的原生 part 恢复为同一条实时更新事件。
 * child session 常见此形态，必须保留其 root/task scope 才能在点击子 Agent 卡片后筛选出内容。
 */
function runEventFromSessionTreePart(
  snapshot: SessionTreeMessagesResponse,
  sessionId: string,
  payload: Record<string, unknown>,
  part: Record<string, unknown>,
  index: number
): RunEvent {
  const messageId =
    text(payload.messageId) ??
    text(payload.messageID) ??
    text(part.messageId) ??
    text(part.messageID) ??
    `message-part-${index + 1}`;
  const partId = text(part.partId) ?? text(part.partID) ?? text(part.id) ?? `part-${index + 1}`;
  const scopedPayload = {
    ...payload,
    sessionId: text(payload.sessionId) ?? text(payload.sessionID) ?? sessionId,
    rootSessionId: text(payload.rootSessionId) ?? snapshot.sessionId,
    messageId,
    messageID: messageId,
    part: {
      ...part,
      messageId: text(part.messageId) ?? text(part.messageID) ?? messageId,
      messageID: text(part.messageID) ?? text(part.messageId) ?? messageId
    }
  };
  return {
    eventId: `session-tree-part:${snapshot.sessionId}:${sessionId}:${messageId}:${partId}:${index}`,
    runId: text(payload.runId) ?? `session-tree:${snapshot.sessionId}`,
    seq: 20_000 + index,
    type: "message.part.updated",
    traceId: text(payload.traceId) ?? "trace_session_tree",
    occurredAt: text(payload.occurredAt) ?? new Date(0).toISOString(),
    payload: scopedPayload
  };
}

// 旧历史可能只持久化 root task part，child session 远端恢复为空；task_result 是可展示的只读子 Agent 正文。
function hydrateSubagentOutputsFromTaskParts(state: AgentChatRuntimeState): AgentChatRuntimeState {
  let changed = false;
  let messages = state.messages;
  const messageScopesById: Record<string, MessageScope> = { ...state.messageScopesById };
  const subagentsBySessionId: Record<string, SubagentSession> = { ...state.subagentsBySessionId };
  const subagentByTaskPartId: Record<string, string> = { ...state.subagentByTaskPartId };
  for (const message of state.messages) {
    if (message.role !== "assistant") {
      continue;
    }
    for (const part of message.parts ?? []) {
      if (part.type !== "tool" || part.toolName !== "task") {
        continue;
      }
      const sessionId = taskChildSessionId(part);
      const outputText = taskOutputText(part.output);
      if (!sessionId || !outputText || childMessageAlreadyRestored(messages, messageScopesById, sessionId, part)) {
        continue;
      }
      const subagent = subagentsBySessionId[sessionId] ?? subagentFromTaskOutput(message, part, sessionId, messageScopesById);
      if (!subagentsBySessionId[sessionId]) {
        subagentsBySessionId[sessionId] = subagent;
        changed = true;
      }
      for (const alias of [part.partId, part.callId, subagent.taskPartId, subagent.taskCallId]) {
        if (alias && !subagentByTaskPartId[alias]) {
          subagentByTaskPartId[alias] = sessionId;
          changed = true;
        }
      }
      const synthetic = subagentOutputMessage(message, part, sessionId, outputText);
      messages = [...messages, synthetic];
      messageScopesById[synthetic.id] = {
        sessionId,
        rootSessionId: subagent.parentSessionId ?? messageScopesById[message.messageId ?? message.id]?.rootSessionId,
        parentSessionId: subagent.parentSessionId,
        isChildSession: true,
        taskMessageId: subagent.taskMessageId,
        taskPartId: subagent.taskPartId,
        taskCallId: subagent.taskCallId
      };
      changed = true;
    }
  }
  return changed ? { ...state, messages, messageScopesById, subagentsBySessionId, subagentByTaskPartId } : state;
}

function subagentOutputMessage(
  parentMessage: Extract<AgentMessage, { role: "assistant" }>,
  part: Extract<MessagePart, { type: "tool" }>,
  sessionId: string,
  outputText: string
): Extract<AgentMessage, { role: "assistant" }> {
  const messageId = `task-output:${sessionId}:${part.partId}`;
  return {
    id: messageId,
    messageId,
    role: "assistant",
    text: outputText,
    parts: [{ partId: `${messageId}:text`, type: "text", text: outputText, status: "completed" }],
    createdAt: part.endedAt ?? part.startedAt ?? parentMessage.createdAt
  };
}

function subagentFromTaskOutput(
  message: Extract<AgentMessage, { role: "assistant" }>,
  part: Extract<MessagePart, { type: "tool" }>,
  sessionId: string,
  scopesById: Record<string, MessageScope>
): SubagentSession {
  const metadata = part.metadata ?? {};
  const output = record(part.output);
  const parentScope = scopesById[message.messageId ?? message.id] ?? scopesById[message.id];
  return {
    sessionId,
    parentSessionId: text(metadata.parentSessionId) ?? text(output?.parentSessionId) ?? parentScope?.sessionId ?? parentScope?.rootSessionId,
    taskMessageId: message.messageId ?? message.id,
    taskPartId: part.partId,
    taskCallId: part.callId,
    agentName: displayAgentName(text(metadata.agentName) ?? text(metadata.agent) ?? text(part.input?.subagent_type) ?? "Task"),
    title: text(metadata.title) ?? text(part.input?.description) ?? firstNonEmptyLine(text(part.input?.prompt)) ?? "Subagent task",
    status: part.status ?? "completed",
    updatedAt: part.endedAt ?? part.startedAt ?? message.createdAt
  };
}

function childMessageAlreadyRestored(
  messages: AgentMessage[],
  scopesById: Record<string, MessageScope>,
  sessionId: string,
  part: Extract<MessagePart, { type: "tool" }>
): boolean {
  const syntheticId = `task-output:${sessionId}:${part.partId}`;
  return messages.some((message) => {
    if (message.role === "card") {
      return false;
    }
    if (message.id === syntheticId || message.messageId === syntheticId) {
      return true;
    }
    const scope = scopesById[message.messageId ?? message.id] ?? scopesById[message.id];
    if (scope?.sessionId === sessionId) {
      return true;
    }
    if (scope?.isChildSession && part.partId && scope.taskPartId === part.partId) {
      return true;
    }
    return Boolean(scope?.isChildSession && part.callId && scope.taskCallId === part.callId);
  });
}

function taskChildSessionId(part: Extract<MessagePart, { type: "tool" }>): string | undefined {
  const metadata = part.metadata ?? {};
  const output = record(part.output);
  return (
    text(metadata.sessionId) ??
    text(metadata.sessionID) ??
    text(metadata.childSessionId) ??
    text(metadata.childSessionID) ??
    text(output?.sessionId) ??
    text(output?.sessionID) ??
    taskOutputSessionId(part.output)
  );
}

function taskOutputSessionId(output: unknown): string | undefined {
  if (typeof output !== "string") {
    return undefined;
  }
  return text(output.match(/<task\s+[^>]*id=["']([^"']+)["']/i)?.[1]);
}

function taskOutputText(output: unknown): string | undefined {
  if (typeof output === "string") {
    const result = output.match(/<task_result>\s*([\s\S]*?)\s*<\/task_result>/i)?.[1] ?? output;
    return text(result);
  }
  const value = record(output);
  return (
    text(value?.taskResult) ??
    text(value?.task_result) ??
    text(value?.result) ??
    text(value?.content) ??
    text(value?.text)
  );
}

function hydrateSubagentIndexesFromSessionTreeSnapshot(
  state: AgentChatRuntimeState,
  snapshot: SessionTreeMessagesResponse
): AgentChatRuntimeState {
  let changed = false;
  const subagentsBySessionId: Record<string, SubagentSession> = { ...state.subagentsBySessionId };
  const subagentByTaskPartId: Record<string, string> = { ...state.subagentByTaskPartId };
  for (const session of snapshot.sessions ?? []) {
    if (!session.childSession || !session.sessionId || !session.taskPartId) {
      continue;
    }
    const sessionId = snapshot.childSessionIdByTaskPartId?.[session.taskPartId] ?? session.sessionId;
    // 历史 snapshot 可能缺少 durable discovery 事件；此处仅用顶层 session 索引补齐导航所需的最小子 Agent 状态。
    const subagent = subagentsBySessionId[sessionId] ?? subagentFromSnapshotSession(state.messages, session, sessionId);
    if (!subagentsBySessionId[sessionId]) {
      subagentsBySessionId[sessionId] = subagent;
      changed = true;
    }
    for (const alias of [session.taskPartId, session.taskCallId, subagent.taskPartId, subagent.taskCallId]) {
      if (alias && !subagentByTaskPartId[alias]) {
        subagentByTaskPartId[alias] = sessionId;
        changed = true;
      }
    }
  }
  return changed ? { ...state, subagentsBySessionId, subagentByTaskPartId } : state;
}

function subagentFromSnapshotSession(
  messages: AgentMessage[],
  session: SessionTreeMessagesResponse["sessions"][number],
  sessionId: string
): SubagentSession {
  const task = findTaskPartForSnapshotSession(
    messages,
    session.taskMessageId ?? undefined,
    session.taskPartId ?? undefined,
    session.taskCallId ?? undefined
  );
  const taskInput = record(task?.part.input);
  const taskMetadata = record(task?.part.metadata);
  const title =
    text(taskMetadata?.title) ??
    text(taskInput?.description) ??
    firstNonEmptyLine(text(taskInput?.prompt)) ??
    "Subagent task";
  const agentName = displayAgentName(
    text(taskInput?.subagent_type) ??
    text(taskMetadata?.agentName) ??
    text(taskMetadata?.agent) ??
    "Task"
  );
  return {
    sessionId,
    parentSessionId: session.parentSessionId ?? undefined,
    taskMessageId: session.taskMessageId ?? undefined,
    taskPartId: task?.part.partId ?? session.taskPartId ?? undefined,
    taskCallId: session.taskCallId ?? task?.part.callId,
    agentName,
    title,
    status: task?.part.status ?? "running",
    updatedAt: task?.message.createdAt ?? new Date(0).toISOString()
  };
}

function findTaskPartForSnapshotSession(
  messages: AgentMessage[],
  taskMessageId: string | undefined,
  taskPartId: string | undefined,
  taskCallId: string | undefined
): { message: Extract<AgentMessage, { role: "assistant" }>; part: Extract<MessagePart, { type: "tool" }> } | undefined {
  for (const message of messages) {
    if (message.role !== "assistant") {
      continue;
    }
    const messageMatches = !taskMessageId || message.messageId === taskMessageId || message.id === taskMessageId;
    for (const part of message.parts ?? []) {
      if (part.type !== "tool" || part.toolName !== "task") {
        continue;
      }
      const partMatches = taskPartId ? part.partId === taskPartId : false;
      const callMatches = taskCallId ? part.callId === taskCallId : false;
      if ((taskPartId || taskCallId) && !partMatches && !callMatches) {
        continue;
      }
      if (!messageMatches && taskPartId === undefined && taskCallId === undefined) {
        continue;
      }
      return { message, part };
    }
  }
  return undefined;
}

function firstNonEmptyLine(value: string | undefined): string | undefined {
  return value?.split(/\r?\n/).map((item) => item.trim()).find(Boolean);
}

function displayAgentName(value: string): string {
  const trimmed = value.trim();
  return trimmed ? `${trimmed.charAt(0).toUpperCase()}${trimmed.slice(1)}` : "Task";
}

function mergeSessionTreeMessages(persistedMessages: AgentMessage[], treeMessages: AgentMessage[]): AgentMessage[] {
  const usedTreeIndexes = new Set<number>();
  const merged = persistedMessages.map((message) => {
    if (message.role === "user") {
      const treeIndex = treeMessages.findIndex((candidate, index) =>
        !usedTreeIndexes.has(index) && candidate.role === "user" && agentMessagesMatch(message, candidate)
      );
      if (treeIndex < 0) {
        return message;
      }
      usedTreeIndexes.add(treeIndex);
      // 历史 DB 保留平台 messageId；session tree 可能才有原生 file parts，用于恢复关联文件 chip。
      return mergeUserMessageWithTreeParts(message, treeMessages[treeIndex] as Extract<AgentMessage, { role: "user" }>);
    }
    if (message.role !== "assistant") {
      return message;
    }
    const treeIndex = treeMessages.findIndex((candidate, index) =>
      !usedTreeIndexes.has(index) && candidate.role === "assistant" && agentMessagesMatch(message, candidate)
    );
    if (treeIndex < 0) {
      return message;
    }
    usedTreeIndexes.add(treeIndex);
    // 平台 DB 保留 user 顺序和反馈身份；session tree assistant 保留更完整的工具/file parts。
    return treeMessages[treeIndex];
  });
  treeMessages.forEach((message, index) => {
    if (!usedTreeIndexes.has(index)) {
      merged.push(message);
    }
  });
  return merged;
}

function mergeUserMessageWithTreeParts(
  persisted: Extract<AgentMessage, { role: "user" }>,
  tree: Extract<AgentMessage, { role: "user" }>
): Extract<AgentMessage, { role: "user" }> {
  const parts = mergePromptParts(persisted.parts, tree.parts);
  return {
    ...persisted,
    remoteMessageId: persisted.remoteMessageId ?? tree.remoteMessageId ?? tree.messageId ?? tree.id,
    parts: parts.length > 0 ? parts : persisted.parts
  };
}

function mergePromptParts(left: PromptPart[] | undefined, right: PromptPart[] | undefined): PromptPart[] {
  const merged: PromptPart[] = [];
  const seen = new Set<string>();
  for (const part of [...(left ?? []), ...(right ?? [])]) {
    const key = promptPartStableKey(part);
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    merged.push(part);
  }
  return merged;
}

function promptPartStableKey(part: PromptPart): string {
  if (part.type === "file") {
    return [
      "file",
      part.path ?? "",
      part.name ?? "",
      part.url ?? "",
      part.source?.contextType ?? "",
      part.source?.startLine ?? "",
      part.source?.endLine ?? ""
    ].join(":");
  }
  if (part.type === "text") {
    return `text:${part.text}`;
  }
  return stableStringify(part);
}

function agentMessagesMatch(left: AgentMessage, right: AgentMessage): boolean {
  if (left.role === "card" || right.role === "card") {
    return false;
  }
  const leftIds = [left.messageId, left.remoteMessageId, left.id].filter(Boolean);
  const rightIds = [right.messageId, right.remoteMessageId, right.id].filter(Boolean);
  if (leftIds.some((id) => rightIds.includes(id))) {
    return true;
  }
  return left.role === right.role && Boolean(left.text) && left.text === right.text;
}

function dedupeSessionTreeEvents(events: SessionTreeMessagesResponse["events"]): SessionTreeMessagesResponse["events"] {
  const seen = new Set<string>();
  const deduped: SessionTreeMessagesResponse["events"] = [];
  for (const event of events) {
    const key = sessionTreeEventDedupeKey(event);
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    deduped.push(event);
  }
  return deduped;
}

function runEventFromSessionTreeEvent(
  snapshot: SessionTreeMessagesResponse,
  event: SessionTreeMessagesResponse["events"][number],
  index: number
): RunEvent {
  const payload = {
    ...(event.payload ?? {}),
    ...(event.rootSessionId ? { rootSessionId: event.rootSessionId } : {}),
    ...(event.sessionId ? { sessionId: event.sessionId } : {}),
    ...(event.parentSessionId ? { parentSessionId: event.parentSessionId } : {}),
    ...(event.childSession !== undefined && event.childSession !== null ? { isChildSession: event.childSession } : {})
  };
  const payloadRecord = record(payload) ?? {};
  const message = record(payloadRecord.message) ?? record(payloadRecord.info);
  const occurredAt = text(payloadRecord.occurredAt) ?? text(message?.createdAt) ?? new Date(0).toISOString();
  return {
    eventId: `session-tree:${snapshot.sessionId}:${sessionTreeEventDedupeKey(event)}`,
    runId: text(payloadRecord.runId) ?? `session-tree:${snapshot.sessionId}`,
    seq: index + 1,
    type: event.type,
    traceId: text(payloadRecord.traceId) ?? "trace_session_tree",
    occurredAt,
    payload
  };
}

function sessionTreeEventDedupeKey(event: SessionTreeMessagesResponse["events"][number]): string {
  const payload = event.payload ?? {};
  const payloadRecord = record(payload) ?? {};
  const raw = record(payloadRecord.part) ?? record(payloadRecord.message) ?? payloadRecord;
  const messageId =
    text(payloadRecord.messageId) ??
    text(payloadRecord.messageID) ??
    text(raw.messageId) ??
    text(raw.messageID) ??
    text(raw.id);
  const partId = text(raw.partId) ?? text(raw.partID) ?? text(raw.id);
  return [
    event.type,
    event.rootSessionId ?? "",
    event.sessionId ?? "",
    messageId ?? "",
    event.type.includes("part") ? partId ?? "" : "",
    stableStringify(payload)
  ].join(":");
}

function stableStringify(value: unknown): string {
  return JSON.stringify(stableValue(value));
}

function stableValue(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(stableValue);
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .filter(([, entry]) => entry !== undefined)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, entry]) => [key, stableValue(entry)])
    );
  }
  return value;
}

/**
 * 历史接口返回的 partsJson 保留 opencode 原始字段（id/tool/state），
 * 这里复用实时 reducer 的归一化逻辑，避免历史工具和文档因字段形态不同而丢失。
 */
function normalizeSessionMessageParts(message: SessionMessage): MessagePart[] {
  return (message.parts ?? [])
    .map((part, index) => {
      if (!part || typeof part !== "object") {
        return null;
      }
      return normalizeMessagePart(
        part as unknown as Record<string, unknown>,
        `${message.messageId}-part-${index}`
      );
    })
    .filter((part): part is MessagePart => part !== null);
}

function normalizeSessionPromptParts(message: SessionMessage): PromptPart[] {
  return (message.parts ?? [])
    .map((part): PromptPart | null => {
      if (!part || typeof part !== "object") {
        return null;
      }
      const raw = part as unknown as Record<string, unknown>;
      const partType = text(raw.type);
      if (partType === "text" && raw.synthetic !== true) {
        const value = text(raw.text) ?? text(raw.content);
        return value ? { type: "text", text: value } satisfies PromptPart : null;
      }
      if (partType !== "file") {
        return null;
      }
      const source = record(raw.source);
      const nestedText = record(source?.text);
      return {
        type: "file",
        path: text(raw.path) ?? text(source?.path),
        name: text(raw.name) ?? text(raw.filename),
        mimeType: text(raw.mimeType) ?? text(raw.mime),
        content: text(raw.content),
        url: text(raw.url),
        source: source
          ? {
              start: number(source.start) ?? number(nestedText?.start),
              end: number(source.end) ?? number(nestedText?.end),
              text: text(source.text) ?? text(nestedText?.value),
              startLine: number(source.startLine),
              endLine: number(source.endLine),
              contextType: text(source.contextType)
            }
          : undefined
      } satisfies PromptPart;
    })
    .filter((part): part is PromptPart => part !== null);
}

/**
 * 从历史 assistant 工具 part 恢复生成文件列表，作为 Run Diff 快照缺失时的展示兜底。
 */
export function diffFilesFromSessionMessages(messages: SessionMessage[]): RunDiffFile[] {
  let restored: RunDiffFile[] = [];
  for (const message of messagesFromSessionMessages(messages)) {
    if (message.role !== "assistant") {
      continue;
    }
    for (const part of message.parts ?? []) {
      if (part.type !== "tool") {
        continue;
      }
      const inferred = inferDiffFromToolPart(part);
      if (inferred) {
        restored = mergeDiffFiles(restored, [inferred]);
      }
    }
  }
  return restored;
}

/**
 * 新会话标题取第一次发送内容的首个非空行，并限制长度，避免长 prompt 超过后端 title 字段上限。
 */
export function sessionTitleFromFirstMessage(message: string): string {
  const line = message
    .split(/\r?\n/)
    .map((item) => item.trim())
    .find(Boolean);
  if (!line) {
    return "新对话";
  }
  return line.length > 72 ? `${line.slice(0, 69)}...` : line;
}

/**
 * 只消费后端已成功持久化的平台标题，禁止以 OpenCode 原始 payload 乐观覆盖页面状态。
 */
export function platformSessionTitleFromSynchronizedEventPayload(payload: Record<string, unknown>): string {
  if (payload.platformSessionTitleSynchronized !== true || typeof payload.platformSessionTitle !== "string") {
    return "";
  }
  return payload.platformSessionTitle.trim();
}

export function modelValue(model: ModelInfo) {
  return model.providerId ? `${model.providerId}/${model.id}` : model.id;
}

export function modelIdOnly(value: string) {
  return value.includes("/") ? value.split("/").slice(1).join("/") : value;
}

export function buildPromptParts(
  prompt: string,
  activeTab: { path: string; content: string } | undefined,
  attachments: ComposerAttachment[] = [],
  extraParts: PromptPart[] = [],
  editorSelection?: EditorSelectionContext
): PromptPart[] {
  const parts = buildComposerPromptParts(prompt, attachments);
  parts.push(...extraParts);
  const editorPart = buildEditorFilePromptPart(activeTab, editorSelection);
  if (editorPart) {
    parts.push(editorPart);
  }
  return parts;
}

export function promptFromParts(parts: PromptPart[]) {
  const fileNames = parts
    .filter((part): part is Extract<PromptPart, { type: "file" }> => part.type === "file")
    .map((part) => part.name ?? part.path)
    .filter((value): value is string => Boolean(value));
  return fileNames.length ? `附件：${fileNames.join(", ")}` : "";
}

export function parseCommand(prompt: string, mode: string) {
  if (mode.startsWith("command:")) {
    return { command: mode.slice("command:".length), arguments: prompt };
  }
  const match = /^\/([^\s]+)(?:\s+([\s\S]*))?$/.exec(prompt.trim());
  return match ? { command: match[1] ?? "", arguments: match[2] ?? "" } : null;
}

export function runtimeResources(
  resources: RuntimeResourceInfo[] | undefined,
  activeTab: { path: string } | undefined
): RuntimeResourceInfo[] {
  const currentFile = activeTab
    ? [{ id: `editor:${activeTab.path}`, name: activeTab.path, uri: `file://${activeTab.path}`, type: "editor" }]
    : [];
  return [...currentFile, ...(resources ?? [])];
}

export function runtimeStatus(
  session: Session | null,
  run: Run | null,
  selectedAgent: string,
  selectedModel: string,
  vcsStatus: unknown,
  lspStatus: unknown,
  mcpStatus: unknown,
  tools: RuntimeToolInfo[] | undefined,
  resources: RuntimeResourceInfo[] | undefined
): RuntimeStatus | undefined {
  if (!session && !run) {
    return undefined;
  }
  const branch = text(record(vcsStatus)?.branch) ?? text(record(record(vcsStatus)?.data)?.branch);
  const lspData = record(record(lspStatus)?.data) ?? record(lspStatus);
  const mcpData = record(mcpStatus);
  const mcpEntries = mcpData ? Object.values(mcpData).filter((item) => typeof item === "object" && item !== null) : [];
  const failedMcp = mcpEntries.some((item) => text(record(item)?.status) === "failed");
  const connectedMcp = mcpEntries.filter((item) => text(record(item)?.status) === "connected").length;
  return {
    sessionId: session?.sessionId ?? run?.sessionId ?? "local",
    runId: run?.runId,
    status: run?.status ?? session?.status ?? "IDLE",
    agent: selectedAgent ? { agentId: selectedAgent, name: selectedAgent } : undefined,
    model: selectedModel ? { id: modelIdOnly(selectedModel), providerId: selectedModel.includes("/") ? selectedModel.split("/")[0] : undefined } : undefined,
    tokens: session?.tokens,
    branch,
    lsp: lspData ? { status: text(lspData.status) ?? "ready", diagnostics: number(lspData.diagnostics) } : undefined,
    mcp: {
      status: failedMcp ? "failed" : connectedMcp > 0 ? "connected" : mcpEntries.length > 0 ? "available" : "unknown",
      tools: tools?.length,
      resources: resources?.length
    }
  };
}

export function dispatchRuntimeResult(result: unknown, dispatch: (action: AgentChatRuntimeAction) => void) {
  const raw = record(result);
  if (!raw) {
    return;
  }
  const info = record(raw.info) ?? record(raw.message) ?? raw;
  const messageId = text(info.id) ?? text(info.messageId) ?? text(info.messageID) ?? `command-${Date.now()}`;
  const occurredAt = new Date().toISOString();
  dispatch({
    type: "event",
    event: syntheticEvent("message.updated", {
      message: {
        id: messageId,
        role: "assistant",
        text: text(info.text) ?? text(info.content) ?? "",
        createdAt: text(record(info.time)?.created) ?? occurredAt
      }
    }, occurredAt)
  });
  const parts = Array.isArray(raw.parts) ? raw.parts : [];
  parts
    .filter((part): part is Record<string, unknown> => typeof part === "object" && part !== null)
    .forEach((part, index) => {
      dispatch({
        type: "event",
        event: syntheticEvent("message.part.updated", {
          messageId,
          part: { id: text(part.id) ?? `part-${index}`, ...part }
        }, occurredAt)
      });
    });
}

export function syntheticEvent(type: string, payload: Record<string, unknown>, occurredAt: string): RunEvent {
  return {
    eventId: `local-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    runId: "local",
    seq: Date.now(),
    type,
    traceId: "trace_local",
    occurredAt,
    payload
  };
}

export function notifyOnAttention(event: RunEvent, workspace: Workspace | undefined, session: Session | null) {
  if (typeof window === "undefined" || !("Notification" in window) || Notification.permission !== "granted") {
    return;
  }
  if (event.type === "permission.asked" || event.type === "question.asked") {
    const title = event.type === "permission.asked" ? "权限请求" : "Agent 提问";
    const body = `${workspace?.name ?? "Workspace"} / ${session?.title ?? "Session"}`;
    new Notification(title, { body });
  }
  if (event.type === "run.succeeded" || event.type === "run.failed") {
    new Notification(event.type === "run.succeeded" ? "Run 完成" : "Run 失败", { body: session?.title ?? event.runId });
  }
}

export function record(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : undefined;
}

export function text(value: unknown): string | undefined {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

export function number(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

export const initialMessages: AgentMessage[] = [
  {
    id: "welcome",
    role: "assistant",
    text: "已连接平台后端后，可以注册 Workspace、打开测试文件、发送 Agent 任务并查看 Diff。",
    createdAt: new Date().toISOString()
  }
];
