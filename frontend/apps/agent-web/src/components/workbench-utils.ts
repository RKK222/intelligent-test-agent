import { BackendApiError } from "@test-agent/backend-api";
import type {
  AgentMessage,
  FileTreeEntry,
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
  Workspace
} from "@test-agent/shared-types";
import type { AgentChatRuntimeAction } from "@test-agent/agent-chat";
import type { EditorSelectionContext } from "@test-agent/editor";
import type { Feedback } from "@test-agent/ui-kit";
import { buildComposerPromptParts, normalizeMessagePart, type ComposerAttachment } from "@test-agent/agent-chat";
import { buildEditorFilePromptPart } from "./prompt-context";

/**
 * `.opencode` 已由下方 Agent 配置树专门管理，普通工作空间根目录不重复展示。
 */
export function filterWorkspaceRootEntries(path: string, entries: FileTreeEntry[]): FileTreeEntry[] {
  if (path !== "") {
    return entries;
  }
  return entries.filter((entry) => entry.name !== ".opencode");
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
// opencode 1.17.8 的 `session.diff` 事件在普通 summarize 流程中只发空 diff 数组，
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

export function historyItems(run: Run | null, sessions: Session[]) {
  return [
    ...sessions.map((item) => ({
      id: item.sessionId,
      title: item.title,
      preview: `${item.agent ?? "agent"} ${item.model?.id ?? ""}`.trim() || "Session",
      status: item.status,
      createdAt: item.createdAt,
      updatedAt: item.updatedAt,
      pinned: item.pinned
    })),
    {
      id: run?.runId ?? "local",
      title: run ? `Run ${run.runId}` : "本地会话",
      preview: run ? `状态 ${run.status}` : "等待发起智能体任务",
      status: run?.status ?? "IDLE",
      createdAt: run?.createdAt,
      updatedAt: run?.updatedAt ?? new Date().toISOString()
    }
  ];
}

export function messagesFromSessionMessages(messages: SessionMessage[]): AgentMessage[] {
  return messages.map((message) => {
    const role = message.role === "USER" ? "user" : "assistant";
    if (role === "user") {
      return {
        id: message.messageId,
        messageId: message.messageId,
        platformMessageId: message.messageId,
        remoteMessageId: message.remoteMessageId,
        role: "user",
        text: message.content,
        createdAt: message.createdAt
      };
    }
    return {
      id: message.messageId,
      messageId: message.messageId,
      platformMessageId: message.messageId,
      remoteMessageId: message.remoteMessageId,
      role: "assistant",
      text: message.content,
      parts: normalizeSessionMessageParts(message),
      createdAt: message.createdAt
    };
  });
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
