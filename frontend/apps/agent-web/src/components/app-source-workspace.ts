import type { AppSourceOpenResult, AppSourcePurpose } from "@test-agent/shared-types";

export type SelectedWorkspaceKind = "MANAGED" | "APP_SOURCE";

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
