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
