import type { PromptPart, Run } from "@test-agent/shared-types";

export type FollowUpDraft = {
  id: string;
  prompt: string;
  parts: PromptPart[];
  createdAt: string;
  command?: { command: string; arguments: string };
};

export function createFollowUpDraft(
  prompt: string,
  parts: PromptPart[],
  createdAt = new Date().toISOString(),
  command?: { command: string; arguments: string }
): FollowUpDraft {
  return {
    id: `${createdAt}:${prompt}:${parts.length}`,
    prompt,
    parts,
    createdAt,
    command
  };
}

export function enqueueFollowUp(queue: FollowUpDraft[], draft: FollowUpDraft): FollowUpDraft[] {
  return [...queue, draft];
}

export function dequeueFollowUp(queue: FollowUpDraft[]): { next?: FollowUpDraft; queue: FollowUpDraft[] } {
  const [next, ...rest] = queue;
  return { next, queue: rest };
}

export function canStartFollowUp(run: Pick<Run, "status"> | null | undefined, mutationPending: boolean): boolean {
  return !mutationPending && !isRunBusyStatus(run?.status);
}

export function isRunBusyStatus(status: Run["status"] | string | undefined): boolean {
  return status === "PENDING" || status === "QUEUED" || status === "RUNNING" || status === "CANCELLING";
}

/**
 * 合并平台 Run 与聊天 reducer 的运行态。
 * 新启动请求优先于上一次 Run 的终态；进入当前 Run 后，任一来源的明确终态都应立即停止动画，
 * 避免另一个来源的延迟状态把已完成任务重新判为运行中。
 */
export function isRuntimeBusy(
  runStatus: Run["status"] | string | undefined,
  chatStatus: string | undefined,
  mutationPending: boolean
): boolean {
  if (mutationPending) {
    return true;
  }
  if (isTerminalStatus(runStatus) || isTerminalStatus(chatStatus)) {
    return false;
  }
  return isRunBusyStatus(runStatus) || isRunBusyStatus(chatStatus);
}

function isTerminalStatus(status: string | undefined): boolean {
  if (!status) {
    return false;
  }
  return ["SUCCEEDED", "FAILED", "CANCELLED", "COMPLETED", "ERROR", "STOPPED"].includes(status.toUpperCase());
}
