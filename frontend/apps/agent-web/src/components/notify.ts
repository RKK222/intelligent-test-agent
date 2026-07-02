import { ElMessage, ElNotification } from "element-plus";
import type { Feedback } from "@test-agent/ui-kit";

type MessageKind = "success" | "info" | "warning" | "error";

const COMMON_DURATION = 3500;
const NOTIFICATION_DURATION = 4500;

function asString(value: unknown): string | undefined {
  if (typeof value === "string" && value.length > 0) return value;
  if (typeof value === "number") return String(value);
  return undefined;
}

function describeError(error: unknown): { message: string; traceId?: string } {
  if (error && typeof error === "object") {
    const errObj = error as Record<string, unknown>;
    const code = asString(errObj.code);
    const message = asString(errObj.message) ?? asString(errObj.error);
    if (code && message) {
      return { message: `${code}：${message}`, traceId: asString(errObj.traceId) };
    }
    if (message) return { message, traceId: asString(errObj.traceId) };
  }
  if (error instanceof Error) return { message: error.message };
  return { message: "未知错误" };
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function renderHtml(title: string, description?: string, traceId?: string): string {
  const safeTitle = escapeHtml(title);
  if (!description && !traceId) return safeTitle;
  const detail = description ? `<div class="ta-notify-detail">${escapeHtml(description)}</div>` : "";
  const meta = traceId ? `<div class="ta-notify-meta">traceId: ${escapeHtml(traceId)}</div>` : "";
  return `<div class="ta-notify-title">${safeTitle}</div>${detail}${meta}`;
}

export function showMessage(kind: MessageKind, title: string, description?: string, traceId?: string) {
  ElMessage({
    type: kind,
    showClose: true,
    offset: 24,
    duration: COMMON_DURATION,
    grouping: true,
    customClass: "ta-top-message",
    message: renderHtml(title, description, traceId),
    dangerouslyUseHTMLString: true
  });
}

export function showNotification(kind: MessageKind, title: string, message: string) {
  ElNotification({
    type: kind,
    showClose: true,
    offset: 24,
    duration: NOTIFICATION_DURATION,
    position: "top-right",
    title,
    message
  });
}

export function notifyError(title: string, error?: unknown) {
  if (error === undefined) {
    showMessage("error", title);
    return;
  }
  const { message, traceId } = describeError(error);
  showMessage("error", title, message, traceId);
}

export function notifyWarning(title: string, description?: string) {
  showMessage("warning", title, description);
}

export function notifySuccess(title: string, description?: string) {
  showMessage("success", title, description);
}

export function notifyInfo(title: string, description?: string) {
  showMessage("info", title, description);
}

export function notifyPermission(title: string, body?: string) {
  showNotification("warning", title, body ?? "");
}

export function notifyRunFinished(success: boolean, body?: string) {
  showNotification(success ? "success" : "error", success ? "Run 完成" : "Run 失败", body ?? "");
}

export function notifyFeedback(feedback: Feedback) {
  const { kind, title, description, traceId } = feedback;
  if (kind === "error") {
    showMessage("error", title, description, traceId);
    return;
  }
  if (kind === "success") {
    notifySuccess(title, description);
    return;
  }
  if (kind === "info") {
    notifyInfo(title, description);
    return;
  }
  if (kind === "warning") {
    notifyWarning(title, description);
    return;
  }
  // 未识别类型降级为 info
  notifyInfo(title, description);
}
