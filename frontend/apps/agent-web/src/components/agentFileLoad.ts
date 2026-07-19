/** Agent 配置文件加载请求只携带路由与交互语义，正文统一由工作台父层读取。 */
export type AgentFileLoadRequest = {
  scope: "PUBLIC" | "WORKSPACE";
  path: string;
  /** 后端返回的真实 Agent 根目录与相对路径合成的磁盘绝对路径，仅用于展示和复制。 */
  absolutePath?: string;
  workspaceId?: string;
  worktreeId?: string | null;
  linuxServerId?: string | null;
  readonly: boolean;
  activate: boolean;
  closeOnNotFound: boolean;
  /** 用户明确执行 Git 回退后允许替换此前 dirty 快照；读取期间的新编辑仍受修订代次保护。 */
  replaceExistingDirty?: boolean;
};

export type AgentFileTabInfo = {
  scope: "PUBLIC" | "WORKSPACE";
  path: string;
  workspaceId?: string;
  worktreeId?: string;
  linuxServerId?: string;
};

const AGENT_PUBLIC_FILE_PREFIX = "agent-public:";
const AGENT_WORKSPACE_FILE_PREFIX = "agent-workspace:";

export function isAgentFilePath(path: string): boolean {
  return path.startsWith(AGENT_PUBLIC_FILE_PREFIX) || path.startsWith(AGENT_WORKSPACE_FILE_PREFIX);
}

/** 公共与应用个人 worktree 的运行目录定义保存后都只热加载当前用户实例。 */
export function shouldReloadPersonalRuntimeCatalog(
  scope: "PUBLIC" | "WORKSPACE",
  path: string
): boolean {
  const normalized = path.replaceAll("\\", "/");
  const personalPreviewScope = scope === "PUBLIC" || scope === "WORKSPACE";
  return personalPreviewScope && (/(^|\/)opencode\.jsonc?$/i.test(normalized)
    || /(^|\/)agents\/.*\.md$/i.test(normalized)
    || /(^|\/)skills\/.+\/SKILL\.md$/i.test(normalized));
}

/**
 * Agent tab 的 path 同时承担唯一身份与后续写入路由，应用级文件必须把 feature workspace 固化在其中。
 * 路径本身先编码，因此分隔符只会出现在路由元数据之间。
 */
export function agentTabPath(
  scope: "PUBLIC" | "WORKSPACE",
  path: string,
  workspaceId?: string | null,
  worktreeId?: string | null,
  linuxServerId?: string | null
): string {
  const encodedPath = encodeURIComponent(path);
  const encodedWorktree = encodeURIComponent(worktreeId ?? "");
  const encodedLinuxServer = encodeURIComponent(linuxServerId ?? "");
  if (scope === "PUBLIC") {
    return `${AGENT_PUBLIC_FILE_PREFIX}${encodedWorktree}:${encodedLinuxServer}:${encodedPath}`;
  }
  return `${AGENT_WORKSPACE_FILE_PREFIX}${encodeURIComponent(workspaceId ?? "")}:${encodedWorktree}:${encodedLinuxServer}:${encodedPath}`;
}

function decodeRouteValue(value: string): string | undefined {
  return value ? decodeURIComponent(value) : undefined;
}

export function agentFileInfo(tabPath: string): AgentFileTabInfo {
  const scope: "PUBLIC" | "WORKSPACE" = tabPath.startsWith(AGENT_PUBLIC_FILE_PREFIX) ? "PUBLIC" : "WORKSPACE";
  const prefix = scope === "PUBLIC" ? AGENT_PUBLIC_FILE_PREFIX : AGENT_WORKSPACE_FILE_PREFIX;
  const parts = tabPath.slice(prefix.length).split(":");

  if (scope === "PUBLIC") {
    const [rawWorktree = "", rawLinuxServer = "", rawPath = ""] = parts;
    return {
      scope,
      path: decodeURIComponent(rawPath),
      workspaceId: undefined,
      worktreeId: decodeRouteValue(rawWorktree),
      linuxServerId: decodeRouteValue(rawLinuxServer)
    };
  }

  // 新格式固定四段；三段旧 tab 不携带 workspaceId，只允许读取信息，不得回退到当前个人工作区写入。
  const isLegacyPath = parts.length < 4;
  const [rawWorkspace = "", rawWorktree = "", rawLinuxServer = "", rawPath = ""] = isLegacyPath
    ? ["", parts[0] ?? "", parts[1] ?? "", parts[2] ?? ""]
    : parts;
  return {
    scope,
    path: decodeURIComponent(rawPath),
    workspaceId: decodeRouteValue(rawWorkspace),
    worktreeId: decodeRouteValue(rawWorktree),
    linuxServerId: decodeRouteValue(rawLinuxServer)
  };
}
