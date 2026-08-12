export type ExperienceApplicationsStatus = "pending" | "success" | "error";

const EXPERIENCE_WORKSPACE_ACKNOWLEDGEMENT_VERSION = "v1";

type ExperienceWorkspaceStorage = Pick<Storage, "getItem" | "setItem">;

function experienceWorkspaceAcknowledgementKey(userId: string) {
  return `test-agent.experience-workspace.${EXPERIENCE_WORKSPACE_ACKNOWLEDGEMENT_VERSION}:${userId}`;
}

/** 体验说明按用户记忆；本地存储不可用时退化为本次会话继续展示，不阻断进入。 */
export function hasAcknowledgedExperienceWorkspace(
  storage: Pick<ExperienceWorkspaceStorage, "getItem"> | undefined,
  userId: string | undefined | null
): boolean {
  const normalizedUserId = userId?.trim();
  if (!storage || !normalizedUserId) return false;
  try {
    return storage.getItem(experienceWorkspaceAcknowledgementKey(normalizedUserId)) === "acknowledged";
  } catch {
    return false;
  }
}

/** 仅在服务端工作区已成功打开后落记忆，避免一次失败尝试永久跳过共享目录说明。 */
export function markExperienceWorkspaceAcknowledged(
  storage: Pick<ExperienceWorkspaceStorage, "setItem"> | undefined,
  userId: string | undefined | null
): void {
  const normalizedUserId = userId?.trim();
  if (!storage || !normalizedUserId) return;
  try {
    storage.setItem(experienceWorkspaceAcknowledgementKey(normalizedUserId), "acknowledged");
  } catch {
    // 浏览器禁用本地存储时仅影响后续是否重复提示。
  }
}

export type ExperienceOfferInput = {
  userId?: string | null;
  applicationsStatus: ExperienceApplicationsStatus;
  applicationCount: number;
  offeredThisMount: boolean;
  acknowledgedPreviously: boolean;
};

/** 无应用用户仍可收到一次自动邀请；存量用户通过常驻菜单主动进入，不被弹窗打断。 */
export function experienceOfferDecision(input: ExperienceOfferInput): "WAIT" | "OFFER" | "SKIP" {
  if (!input.userId?.trim() || input.applicationsStatus !== "success") return "WAIT";
  if (input.offeredThisMount || input.acknowledgedPreviously || input.applicationCount > 0) return "SKIP";
  return "OFFER";
}

export type ExperienceContinuationPhase = "IDLE" | "WAITING_FOR_READY" | "OPENING" | "ACTIVE";

export type ExperienceContinuationState = {
  generation: number;
  phase: ExperienceContinuationPhase;
};

export function initialExperienceContinuation(): ExperienceContinuationState {
  return { generation: 0, phase: "IDLE" };
}

/** 每次接受体验都创建新代次，旧健康检查、初始化回包和 open 响应均自动失效。 */
export function requestExperienceContinuation(
  current: ExperienceContinuationState
): ExperienceContinuationState {
  return { generation: current.generation + 1, phase: "WAITING_FOR_READY" };
}

/** 取消会主动推进代次，保证迟到 READY 不能重新进入体验区。 */
export function cancelExperienceContinuation(
  current: ExperienceContinuationState
): ExperienceContinuationState {
  return { generation: current.generation + 1, phase: "IDLE" };
}

export function beginExperienceWorkspaceOpen(
  current: ExperienceContinuationState,
  observedGeneration: number
): { state: ExperienceContinuationState; shouldOpen: boolean } {
  if (current.generation !== observedGeneration || current.phase !== "WAITING_FOR_READY") {
    return { state: current, shouldOpen: false };
  }
  return {
    state: { ...current, phase: "OPENING" },
    shouldOpen: true
  };
}

export function activateExperienceContinuation(
  current: ExperienceContinuationState,
  observedGeneration: number
): ExperienceContinuationState {
  if (current.generation !== observedGeneration || current.phase !== "OPENING") return current;
  return { ...current, phase: "ACTIVE" };
}

/** 确认框返回时再次校验代次，迟到确认不得启动已取消的初始化。 */
export function experienceInitializationConfirmationIsCurrent(
  current: ExperienceContinuationState,
  observedGeneration: number
): boolean {
  return current.generation === observedGeneration
    && current.phase === "WAITING_FOR_READY";
}
