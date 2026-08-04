import type { AppSourceOpenResult, AppSourcePurpose, PersonalWorkspace } from "@test-agent/shared-types";
import { BackendApiError } from "@test-agent/backend-api";

export type SelectedWorkspaceKind = "MANAGED" | "APP_SOURCE";

export type PersonalWorkspaceRuntimeContext = {
  personalWorkspaceId: string;
  personalWorkspaceBranch: string;
};

/**
 * 个人 worktree 身份只能按服务端返回的运行态 Workspace ID 精确恢复，不能用名称或物理路径推断写权限。
 */
export function personalWorkspaceRuntimeContext(
  workspaceId: string | undefined,
  personalWorkspaces: PersonalWorkspace[]
): PersonalWorkspaceRuntimeContext | undefined {
  if (!workspaceId) return undefined;
  const matched = personalWorkspaces.find((workspace) =>
    workspace.runtimeWorkspace?.workspaceId === workspaceId
  );
  if (!matched) return undefined;
  return {
    personalWorkspaceId: matched.personalWorkspaceId,
    personalWorkspaceBranch: matched.branch
  };
}

/**
 * 源码工作区只保存服务端返回的逻辑身份；物理路径仍由 Workspace 与文件 WebSocket 路由解析。
 */
export type AppSourceWorkspaceContext = {
  appId: string;
  repositoryId: string;
  generation: number;
  purpose: AppSourcePurpose;
  workspaceId: string;
  linuxServerId: string;
  expiresAt: string;
};

export function sourceContextFromOpen(result: AppSourceOpenResult): AppSourceWorkspaceContext {
  return {
    appId: result.appId,
    repositoryId: result.repositoryId,
    generation: result.generation,
    purpose: result.purpose,
    workspaceId: result.workspaceId,
    linuxServerId: result.linuxServerId,
    expiresAt: result.expiresAt
  };
}

/** 源码快照仍是普通可写 Workspace，但不具备任何 Git 发布或应用 Agent 发布能力。 */
export function appSourceWorkspaceCapabilities(kind: SelectedWorkspaceKind) {
  const sourceMode = kind === "APP_SOURCE";
  return {
    canWriteWorkspaceFiles: true,
    canUseSessionsAndRuns: true,
    canUseTerminal: true,
    canUseGitPublication: !sourceMode,
    canPublishApplicationAgentConfig: !sourceMode,
    canSelectApplicationVersion: !sourceMode
  };
}

/**
 * 普通 Workspace 文件写能力只依赖显式工作区语义：托管应用必须是个人 worktree，源码快照自身可写。
 */
export function ordinaryWorkspaceCanWrite(
  kind: SelectedWorkspaceKind,
  personalWorkspaceId?: string,
  workspaceId?: string
) {
  return Boolean(workspaceId && (kind === "APP_SOURCE" || personalWorkspaceId));
}

export type DiffSaveCapability = {
  workspaceKind: SelectedWorkspaceKind;
  ordinaryWorkspaceWritable: boolean;
  agentScope: "PUBLIC" | "WORKSPACE" | null;
  isSuperAdmin: boolean;
  isAppAdmin: boolean;
};

/**
 * Diff 保存权限不信任子组件的 emit：源码快照只允许普通文件，Agent 始终回到托管工作区角色门禁。
 */
export function diffFileCanWrite(capability: DiffSaveCapability) {
  if (capability.agentScope === null) return capability.ordinaryWorkspaceWritable;
  if (capability.workspaceKind === "APP_SOURCE") return false;
  return capability.agentScope === "PUBLIC"
    ? capability.isSuperAdmin
    : capability.isAppAdmin;
}

/** 已存在的团队副本只允许保持 TEAM；个人副本仍可提升为团队共享。 */
export function appSourcePurposeUpdateAllowed(
  repository: { generation?: number | null; purpose?: AppSourcePurpose | null },
  nextPurpose: AppSourcePurpose
) {
  return !(repository.generation && repository.purpose === "TEAM" && nextPurpose === "PERSONAL");
}

export type AppSourceIntentAuthority = {
  token: number;
  appId?: string;
  repositoryId?: string;
  generation?: number | null;
  workspaceKind: SelectedWorkspaceKind;
};

/** 每段异步 selection/recovery 都必须命中完整逻辑身份，不能仅靠最后一次请求序号。 */
export function appSourceIntentAuthorityMatches(
  request: AppSourceIntentAuthority,
  current: AppSourceIntentAuthority | null
) {
  return Boolean(current
    && request.token === current.token
    && request.appId === current.appId
    && request.repositoryId === current.repositoryId
    && request.generation === current.generation
    && request.workspaceKind === current.workspaceKind);
}

/** 只按 backend-api 的结构化错误码判断权威失效，禁止从 message 猜测。 */
export function appSourceRecoveryFailureInvalidatesRecent(error: unknown) {
  return error instanceof BackendApiError
    && ["FORBIDDEN", "NOT_FOUND", "CONFLICT"].includes(error.code);
}

export type AppSourceTreeAuthority = {
  token: number;
  appId: string;
  repositoryId: string;
  branch: string;
};

export function appSourceTreeAuthorityMatches(
  request: AppSourceTreeAuthority,
  current: AppSourceTreeAuthority | null
) {
  return Boolean(current
    && request.token === current.token
    && request.appId === current.appId
    && request.repositoryId === current.repositoryId
    && request.branch === current.branch);
}

export type AppSourceProgressAuthority = {
  token: number;
  operationId: string;
  appId: string;
  repositoryId: string;
  repositoryGeneration: number | null;
  targetGeneration: number;
};

export type AppSourceProgressContext = {
  dialogOpen: boolean;
  appId?: string;
  repositoryId?: string;
  repositoryGeneration: number | null;
};

export type AppSourceProgressIdentity = {
  operationId?: string | null;
  appId?: string;
  repositoryId?: string;
  targetGeneration?: number;
};

/** WS 事件除 operationId 外，还必须命中当前弹窗的仓库与 generation 语境。 */
export function appSourceProgressBelongsToObservation(
  authority: AppSourceProgressAuthority,
  current: AppSourceProgressContext,
  event: AppSourceProgressIdentity
) {
  return current.dialogOpen
    && event.operationId === authority.operationId
    && current.appId === authority.appId
    && current.repositoryId === authority.repositoryId
    && current.repositoryGeneration === authority.repositoryGeneration
    && (event.appId === undefined || event.appId === authority.appId)
    && (event.repositoryId === undefined || event.repositoryId === authority.repositoryId)
    && (event.targetGeneration === undefined || event.targetGeneration === authority.targetGeneration);
}

export function claimAppSourceTerminalOperation(handled: Set<string>, operationId: string) {
  if (handled.has(operationId)) return false;
  handled.add(operationId);
  return true;
}
