export type RuntimeStateFallbackLease = {
  outageGeneration: number;
  revision: number;
};

export type RuntimeStateOutageTracker = {
  onOpen: () => void;
  onError: () => void;
  onSnapshot: () => void;
  takeFallback: (sessionId: string) => RuntimeStateFallbackLease | null;
  isCurrent: (lease: RuntimeStateFallbackLease) => boolean;
  reset: () => void;
};

/**
 * 将短暂重连视为同一次故障，只有连接稳定保持后才允许同一会话在下一次故障中再次兜底。
 */
export function createRuntimeStateOutageTracker(recoveryStableMs: number): RuntimeStateOutageTracker {
  let unavailable = false;
  let generation = 0;
  let revision = 0;
  let recoveryTimer: ReturnType<typeof setTimeout> | null = null;
  const fallbackSessions = new Set<string>();

  const cancelRecovery = () => {
    if (recoveryTimer) {
      clearTimeout(recoveryTimer);
      recoveryTimer = null;
    }
  };

  return {
    onOpen() {
      cancelRecovery();
      if (!unavailable) {
        return;
      }
      recoveryTimer = setTimeout(() => {
        recoveryTimer = null;
        unavailable = false;
        revision += 1;
        fallbackSessions.clear();
      }, recoveryStableMs);
    },
    onError() {
      cancelRecovery();
      if (unavailable) {
        return;
      }
      unavailable = true;
      generation += 1;
      revision += 1;
      fallbackSessions.clear();
    },
    onSnapshot() {
      // 新摘要比尚未返回的 HTTP fallback 更新，但不把短连接误判为已经稳定恢复。
      revision += 1;
    },
    takeFallback(sessionId) {
      if (!unavailable || fallbackSessions.has(sessionId)) {
        return null;
      }
      fallbackSessions.add(sessionId);
      return { outageGeneration: generation, revision };
    },
    isCurrent(lease) {
      return unavailable
        && lease.outageGeneration === generation
        && lease.revision === revision;
    },
    reset() {
      cancelRecovery();
      unavailable = false;
      generation = 0;
      revision += 1;
      fallbackSessions.clear();
    }
  };
}
