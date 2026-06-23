import { BackendApiError } from "@test-agent/backend-api";
import type {
  AgentMessage,
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
import { buildComposerPromptParts, type ComposerAttachment } from "@test-agent/agent-chat";
import { buildEditorFilePromptPart } from "./prompt-context";

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
export function mergeDiffFiles(current: RunDiffFile[], incoming: RunDiffFile[]): RunDiffFile[] {
  if (incoming.length === 0) {
    return current;
  }
  const map = new Map<string, RunDiffFile>();
  for (const file of current) {
    if (file.path) map.set(file.path, file);
  }
  for (const file of incoming) {
    if (file.path) map.set(file.path, file);
  }
  return Array.from(map.values());
}

export function errorFeedback(title: string, error: unknown): Feedback {
  if (error instanceof BackendApiError) {
    return { kind: "error", title, description: `${error.code}: ${error.message}`, traceId: error.traceId };
  }
  return { kind: "error", title, description: error instanceof Error ? error.message : "未知错误" };
}

export function historyItems(run: Run | null, sessions: Session[]) {
  return [
    ...sessions.map((item) => ({
      id: item.sessionId,
      title: item.title,
      preview: `${item.agent ?? "agent"} ${item.model?.id ?? ""}`.trim() || "Session",
      status: item.status,
      updatedAt: new Date(item.updatedAt).toLocaleTimeString("zh-CN", { hour12: false }),
      pinned: item.pinned
    })),
    {
      id: run?.runId ?? "local",
      title: run ? `Run ${run.runId}` : "本地会话",
      preview: run ? `状态 ${run.status}` : "等待发起智能体任务",
      status: run?.status ?? "IDLE",
      updatedAt: new Date().toLocaleTimeString("zh-CN", { hour12: false })
    }
  ];
}

export function messagesFromSessionMessages(messages: SessionMessage[]): AgentMessage[] {
  return messages.map((message) => ({
    id: message.messageId,
    messageId: message.messageId,
    role: message.role === "USER" ? "user" : "assistant",
    text: message.content,
    createdAt: message.createdAt
  }));
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
