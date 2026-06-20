import type { AgentMessage, MessagePart, RunDiffFile, TodoItem } from "@test-agent/shared-types";
import type { ComposerAttachment } from "./prompt-parts";

/** 斜杠命令查询：返回光标处 `/xxx` 的查询串，无则 null */
export function commandQuery(text: string): string | null {
  const match = /(?:^|\n)\/([^\s/]*)$/.exec(text);
  return match?.[1] ?? null;
}

/** @上下文查询：返回光标处 `@xxx` 的查询串，无则 null */
export function contextQuery(text: string): string | null {
  const match = /@([^\s@]*)$/.exec(text);
  return match?.[1] ?? null;
}

export function replaceCommandQuery(text: string, command: string) {
  return text.replace(/(^|\n)\/[^\s/]*$/, `$1/${command} `);
}

export function replaceContextQuery(text: string, label: string) {
  return text.replace(/@[^\s@]*$/, `@${label} `);
}

/** 流式 part 指纹，用于判断消息流是否变化以触发自动滚动 */
export function partSignature(part: MessagePart) {
  if (part.type === "text" || part.type === "reasoning") {
    return `${part.partId}:${part.type}:${part.status ?? ""}:${part.text.length}`;
  }
  if (part.type === "tool") {
    return `${part.partId}:${part.type}:${part.status}:${String(part.output ?? "").length}`;
  }
  return `${part.partId}:${part.type}`;
}

export function viewportIsAtBottom(viewport: HTMLElement) {
  return viewport.scrollHeight - viewport.scrollTop - viewport.clientHeight <= 24;
}

export function scrollViewportToBottom(viewport: HTMLElement, behavior: ScrollBehavior) {
  if (typeof viewport.scrollTo === "function") {
    viewport.scrollTo({ top: viewport.scrollHeight, behavior });
  } else {
    viewport.scrollTop = viewport.scrollHeight;
  }
}

export function mergeAttachments(current: ComposerAttachment[], next: ComposerAttachment[]) {
  const seen = new Set(current.map((item) => item.id));
  return [...current, ...next.filter((item) => !seen.has(item.id))];
}

export function formatBytes(size: number) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

export function modelOptionValue(model: { providerId?: string; id: string }) {
  return model.providerId ? `${model.providerId}/${model.id}` : model.id;
}

/** 卡片是否默认展开：运行态或最新 tool/diff 卡片 */
export function shouldOpenCardByDefault(
  message: Extract<AgentMessage, { role: "card" }>,
  defaultOpenCardIds: { latestToolId?: string; latestDiffId?: string }
) {
  const status = typeof message.payload.status === "string" ? message.payload.status.toLowerCase() : "";
  if (["running", "active", "pending", "queued"].includes(status)) {
    return true;
  }
  return message.id === defaultOpenCardIds.latestToolId || message.id === defaultOpenCardIds.latestDiffId;
}

export function arrayOfRecords(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value)
    ? value.filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
    : [];
}

export function text(value: unknown): string | undefined {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

export function lineChange(file: RunDiffFile) {
  return `+${file.additions} -${file.deletions}`;
}

export function record(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : undefined;
}

export function toolPurpose(part: Extract<MessagePart, { type: "tool" }>) {
  return (
    text(part.metadata?.purpose) ??
    text(part.metadata?.summary) ??
    text(part.metadata?.description) ??
    text(part.metadata?.title)
  );
}

export function skillNameFromPart(part: Extract<MessagePart, { type: "tool" }>) {
  return (
    text(part.input?.name) ??
    text(part.metadata?.name) ??
    skillNameFromOutput(part.output) ??
    (part.toolName.toLowerCase() === "skill" ? undefined : part.toolName)
  );
}

export function skillNameFromPayload(payload: Record<string, unknown>) {
  const input = record(payload.input);
  const metadata = record(payload.metadata);
  return (
    text(input?.name) ??
    text(metadata?.name) ??
    skillNameFromOutput(payload.output) ??
    skillNameFromOutput(payload.rawOutput) ??
    text(payload.toolName)
  );
}

export function skillNameFromOutput(value: unknown) {
  const match = typeof value === "string" ? /Loaded skill:\s*([^\n<]+)/i.exec(value) : null;
  return match?.[1]?.trim();
}

export function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

/** 运行态上下文窗口占用百分比，无则 null */
export function contextPercent(runtimeStatus?: { tokens?: { contextWindow?: number; input?: number; output?: number; reasoning?: number; cacheRead?: number; cacheWrite?: number } }) {
  const tokens = runtimeStatus?.tokens;
  if (!tokens?.contextWindow) {
    return null;
  }
  const used = (tokens.input ?? 0) + (tokens.output ?? 0) + (tokens.reasoning ?? 0) + (tokens.cacheRead ?? 0) + (tokens.cacheWrite ?? 0);
  return Math.min(100, Math.max(0, Math.round((used / tokens.contextWindow) * 100)));
}

export function todoTitleFallback(item: TodoItem) {
  return item.title ?? item.summary ?? item.description ?? item.id;
}
