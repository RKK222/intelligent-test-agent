import { BackendApiError } from "@test-agent/backend-api";

/**
 * Agent 配置操作的错误展示只使用后端给出的安全提示；Git stderr/command 等诊断细节不得进入 UI。
 */
export function formatAgentConfigError(error: unknown, fallback: string): string {
  if (error instanceof BackendApiError) {
    const conflictFiles = stringArray(error.details.conflictFiles);
    if (error.code === "CONFLICT" && conflictFiles.length > 0) {
      return `${fallback}：合并冲突，请先处理 ${conflictFiles.join("、")} 后重试。`;
    }
    const hint = text(error.details.gitFailureHint);
    if (isGitError(error.code) && hint) {
      return `${fallback}：${hint}（traceId: ${error.traceId}）`;
    }
    return `${fallback}：${error.message}`;
  }
  if (error instanceof Error) return `${fallback}：${error.message}`;
  return fallback;
}

function isGitError(code: string): boolean {
  return code === "GIT_UNAVAILABLE" || code === "GIT_TIMEOUT";
}

function text(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function stringArray(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
}
