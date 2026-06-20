import type { MessagePart, TodoItem } from "@test-agent/shared-types";

export type ProcessStatusKind = "thinking" | "task" | "tool" | "skill";

export type NormalizedProcessStatus =
  | "not_started"
  | "waiting"
  | "running"
  | "completed"
  | "partial"
  | "failed"
  | "skipped"
  | "cancelled"
  | "paused"
  | "error";

export function normalizeProcessStatus(value: unknown): NormalizedProcessStatus {
  const status = typeof value === "string" ? value.toLowerCase() : "";
  if (["active", "running", "in_progress", "calling", "streaming", "started"].includes(status)) {
    return "running";
  }
  if (["pending", "queued", "waiting", "wait"].includes(status)) {
    return "waiting";
  }
  if (["done", "success", "succeeded", "completed", "complete", "finished"].includes(status)) {
    return "completed";
  }
  if (["partial", "partial_completed", "partially_completed"].includes(status)) {
    return "partial";
  }
  if (["failed", "failure"].includes(status)) {
    return "failed";
  }
  if (["error", "errored"].includes(status)) {
    return "error";
  }
  if (["skipped", "skip"].includes(status)) {
    return "skipped";
  }
  if (["cancelled", "canceled", "aborted"].includes(status)) {
    return "cancelled";
  }
  if (["paused", "pause"].includes(status)) {
    return "paused";
  }
  if (["not_started", "idle", "none", ""].includes(status)) {
    return "not_started";
  }
  return "waiting";
}

export function statusLabel(status: NormalizedProcessStatus, kind: ProcessStatusKind) {
  if (kind === "thinking") {
    return {
      not_started: "未开始",
      waiting: "正在准备",
      running: "思考中",
      completed: "已完成",
      partial: "部分完成",
      failed: "出错",
      skipped: "已跳过",
      cancelled: "已取消",
      paused: "已暂停",
      error: "出错"
    }[status];
  }
  if (kind === "skill") {
    return {
      not_started: "准备调用",
      waiting: "等待结果",
      running: "调用中",
      completed: "已完成",
      partial: "部分完成",
      failed: "调用失败",
      skipped: "已跳过",
      cancelled: "已取消",
      paused: "已暂停",
      error: "调用失败"
    }[status];
  }
  if (kind === "task") {
    return {
      not_started: "等待中",
      waiting: "等待中",
      running: "运行中",
      completed: "已完成",
      partial: "部分完成",
      failed: "失败",
      skipped: "已跳过",
      cancelled: "已取消",
      paused: "已暂停",
      error: "失败"
    }[status];
  }
  return {
    not_started: "准备调用",
    waiting: "等待结果",
    running: "调用中",
    completed: "已完成",
    partial: "部分完成",
    failed: "调用失败",
    skipped: "已跳过",
    cancelled: "已取消",
    paused: "已暂停",
    error: "调用失败"
  }[status];
}

export function statusToneClass(status: NormalizedProcessStatus) {
  if (status === "running") {
    return "border-[var(--ta-chat-status-running)] text-[var(--ta-chat-status-running)]";
  }
  if (status === "completed") {
    return "border-[var(--ta-chat-status-done)] text-[var(--ta-chat-status-done)]";
  }
  if (status === "failed" || status === "error") {
    return "border-[var(--ta-chat-status-error)] text-[var(--ta-chat-status-error)]";
  }
  if (status === "cancelled" || status === "skipped" || status === "paused") {
    return "border-[var(--ta-chat-muted)] text-[var(--ta-chat-muted)]";
  }
  return "border-[var(--ta-chat-border-strong)] text-[var(--ta-chat-subtle)]";
}

export function isActiveStatus(status: NormalizedProcessStatus) {
  return status === "running" || status === "waiting";
}

export function isSkillToolName(value: unknown) {
  return typeof value === "string" && value.toLowerCase() === "skill";
}

export function toolPartIsSkill(part: Extract<MessagePart, { type: "tool" }>) {
  return isSkillToolName(part.toolName) || isSkillToolName(part.metadata?.tool) || isSkillToolName(part.metadata?.type);
}

export function todoTitle(item: TodoItem) {
  return item.title ?? item.text;
}

export function textValue(value: unknown) {
  return typeof value === "string" && value.trim().length > 0 ? value : undefined;
}
