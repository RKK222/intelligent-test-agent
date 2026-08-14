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
      const recovery = publishRecoveryText(error.details);
      return `${fallback}：${hint}${recovery ? ` ${recovery}` : ""}（traceId: ${error.traceId}）`;
    }
    return `${fallback}：${error.message}`;
  }
  if (error instanceof Error) return `${fallback}：${error.message}`;
  return fallback;
}

function publishRecoveryText(details: Record<string, unknown>): string | null {
  if (details.localCommitRetained !== true) return null;
  const remoteState = text(details.remoteCommitState);
  const action = text(details.publishRecoveryAction);
  if (remoteState === "NOT_REACHED") {
    return "本地提交已保留，且已确认远端未包含本次提交；可直接点击“重新推送”，不会重复建立本地提交。";
  }
  if (remoteState === "NOT_ATTEMPTED") {
    return "本地提交已保留，远端推送尚未开始；修复认证、权限、分支或网络问题后可直接点击“重新推送”。";
  }
  if (remoteState === "UNKNOWN" && action === "WAIT_FOR_RECOVERY") {
    return "本地提交已保留，但远端是否收到仍无法确认；应用 Agent 正由后台补偿核验，请等待 5 分钟后刷新并重试，仍失败时将 traceId 提供给管理员。";
  }
  if (remoteState === "UNKNOWN") {
    return "本地提交已保留，但远端是否收到仍无法确认；可点击“重新推送”执行幂等核验，仍失败时将 traceId 提供给管理员。";
  }
  return "本地提交已保留，可使用“重新推送”继续处理。";
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
