type LocalClientEndpointSnapshot = {
  endpointId: string;
  runtimeKind: string;
  online: boolean;
};

type LocalWorkspaceSnapshot = {
  runtimeKind?: string | null;
  online?: boolean | null;
  gitAccessStatus?: string | null;
  gitAccessReason?: string | null;
};

/**
 * 客户端上线后短时刷新工作区投影，等待服务端完成历史目录逐项验真与接管。
 * 同一批连接限制尝试次数，已恢复或无法恢复的目录不会造成永久高频轮询。
 */
export function createLocalWorkspaceReconnectRefreshGate(maxAttempts = 24) {
  let onlineEndpointFingerprint = "";
  let attempts = 0;

  return {
    shouldRefresh(
      endpoints: LocalClientEndpointSnapshot[],
      workspaces: LocalWorkspaceSnapshot[]
    ): boolean {
      const nextFingerprint = endpoints
        .filter((endpoint) => endpoint.runtimeKind === "LOCAL_CLIENT" && endpoint.online)
        .map((endpoint) => endpoint.endpointId)
        .sort()
        .join("|");
      if (nextFingerprint !== onlineEndpointFingerprint) {
        onlineEndpointFingerprint = nextFingerprint;
        attempts = 0;
      }
      if (!nextFingerprint) return false;

      const hasPendingProjection = workspaces.some((workspace) =>
        workspace.runtimeKind === "LOCAL_CLIENT"
        && (workspace.online === false
          || (workspace.gitAccessStatus === "INACCESSIBLE"
            && workspace.gitAccessReason === "NOT_GIT_REPOSITORY"))
      );
      if (!hasPendingProjection) {
        attempts = 0;
        return false;
      }
      if (attempts >= maxAttempts) return false;
      attempts += 1;
      return true;
    }
  };
}
