const SERVER_WORKSPACE_PICKER_TAB_STATE_KEY = "test-agent.server-workspace-picker-tab-state";

export type ServerWorkspacePickerTabState = {
  serverId?: string;
  path?: string;
};

/**
 * 新标签页会复制 opener 的 sessionStorage；用它交接服务器和目录，
 * 避免把本机路径放进地址栏和浏览历史。
 */
export function writeServerWorkspacePickerTabState(state: ServerWorkspacePickerTabState): void {
  try {
    sessionStorage.setItem(SERVER_WORKSPACE_PICKER_TAB_STATE_KEY, JSON.stringify(state));
  } catch {
    // 企业策略禁用会话存储时仍可打开标签页，新页面会回退到默认服务器目录。
  }
}

export function readServerWorkspacePickerTabState(): ServerWorkspacePickerTabState {
  try {
    const raw = sessionStorage.getItem(SERVER_WORKSPACE_PICKER_TAB_STATE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    return {
      serverId: typeof parsed.serverId === "string" ? parsed.serverId : undefined,
      path: typeof parsed.path === "string" ? parsed.path : undefined
    };
  } catch {
    return {};
  }
}
