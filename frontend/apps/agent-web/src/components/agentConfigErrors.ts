import { BackendApiError } from "@test-agent/backend-api";

/**
 * Agent 配置操作的错误展示只使用后端给出的安全提示；Git stderr/command 等诊断细节不得进入 UI。
 */
export function formatAgentConfigError(error: unknown, fallback: string): string {
  if (error instanceof BackendApiError) {
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
