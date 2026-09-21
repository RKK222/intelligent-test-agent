import {
  isCustomMenuId,
  type CustomMenuIconKey,
  type CustomMenuItem
} from "./custom-menus";
import { hasAppAdminCapability, hasSuperAdminCapability, hasSystemAdminCapability } from "../auth/roleCapabilities";

export type SystemMenuKey =
  | "team"
  | "scheduler"
  | "runtime"
  | "users"
  | "localClientVersions"
  | "params"
  | "apiKeys"
  | "internalModels"
  | "internalModelObservability"
  | "memory"
  | "config"
  | "analytics"
  | "traces"
  | "support";

export type CustomWorkspacePageId = `custom:${string}`;
export type WorkspacePageId = "toolbox" | "memories" | "hub" | `system:${SystemMenuKey}` | CustomWorkspacePageId;

export type WorkspacePageTabsState = {
  openIds: WorkspacePageId[];
  activeId: WorkspacePageId | null;
  lastSystemId: `system:${SystemMenuKey}` | null;
};

export type WorkspacePageCloseMode = "current" | "others" | "left" | "right" | "all";

export type WorkspacePageTab = {
  id: WorkspacePageId;
  title: string;
  icon?: CustomMenuIconKey;
  systemMenuKey?: SystemMenuKey;
  persistent: boolean;
};

const SYSTEM_MENU_KEYS: readonly SystemMenuKey[] = [
  "team",
  "scheduler",
  "runtime",
  "users",
  "localClientVersions",
  "params",
  "apiKeys",
  "internalModels",
  "internalModelObservability",
  "memory",
  "config",
  "analytics",
  "traces",
  "support"
];

const SYSTEM_SECTION_BY_KEY: Record<SystemMenuKey, string> = {
  team: "team",
  scheduler: "scheduler",
  runtime: "runtime",
  users: "users",
  localClientVersions: "local-client-versions",
  params: "params",
  apiKeys: "api-keys",
  internalModels: "internal-models",
  internalModelObservability: "internal-model-observability",
  memory: "memory",
  config: "config",
  analytics: "analytics",
  traces: "traces",
  support: "support"
};

const SYSTEM_KEY_BY_SECTION = Object.fromEntries(
  Object.entries(SYSTEM_SECTION_BY_KEY).map(([key, section]) => [section, key])
) as Record<string, SystemMenuKey>;

const PAGE_TITLES: Record<Exclude<WorkspacePageId, CustomWorkspacePageId>, string> = {
  toolbox: "工具箱",
  memories: "记忆",
  hub: "能力库",
  "system:team": "团队管理",
  "system:scheduler": "定时任务管理",
  "system:runtime": "运行管理",
  "system:users": "用户管理",
  "system:localClientVersions": "本地客户端版本",
  "system:params": "通用参数管理",
  "system:apiKeys": "API Key 管理",
  "system:internalModels": "内部模型供应商",
  "system:internalModelObservability": "内部模型可观测",
  "system:memory": "记忆能力",
  "system:config": "配置管理",
  "system:analytics": "运营分析",
  "system:traces": "Trace 可观测",
  "system:support": "问题排查只读访问"
};

/** 页面注册表只描述稳定身份与标题，组件和图标仍由应用壳层按需装配。 */
export function workspacePageTab(id: WorkspacePageId, customMenus: readonly CustomMenuItem[] = []): WorkspacePageTab {
  if (isCustomWorkspacePageId(id)) {
    const menuId = customMenuIdFromPageId(id);
    const menu = customMenus.find((item) => item.id === menuId);
    return {
      id,
      title: menu?.name ?? "已删除的自定义菜单",
      icon: menu?.icon ?? "globe",
      persistent: Boolean(menu)
    };
  }
  return {
    id,
    title: PAGE_TITLES[id],
    ...(id.startsWith("system:") ? { systemMenuKey: id.slice("system:".length) as SystemMenuKey } : {}),
    persistent: id !== "system:support"
  };
}

export function isWorkspacePageId(value: unknown): value is WorkspacePageId {
  if (value === "toolbox" || value === "memories" || value === "hub") return true;
  if (typeof value === "string" && value.startsWith("custom:")) {
    return isCustomMenuId(value.slice("custom:".length));
  }
  if (typeof value !== "string" || !value.startsWith("system:")) return false;
  return SYSTEM_MENU_KEYS.includes(value.slice("system:".length) as SystemMenuKey);
}

export function customMenuPageId(menuId: string): CustomWorkspacePageId {
  return `custom:${menuId}`;
}

export function customMenuIdFromPageId(id: CustomWorkspacePageId): string {
  return id.slice("custom:".length);
}

export function isCustomWorkspacePageId(id: WorkspacePageId): id is CustomWorkspacePageId {
  return id.startsWith("custom:");
}

export function isSystemWorkspacePageId(id: WorkspacePageId): id is `system:${SystemMenuKey}` {
  return id.startsWith("system:");
}

export function systemMenuKeyFromPageId(id: WorkspacePageId): SystemMenuKey | null {
  return isSystemWorkspacePageId(id) ? id.slice("system:".length) as SystemMenuKey : null;
}

export function canOpenSystemMenu(key: SystemMenuKey, roles: string[], supportRevealed = false): boolean {
  if (hasSuperAdminCapability(roles)) return key !== "support" || supportRevealed;
  if (hasSystemAdminCapability(roles)) return key === "team" || key === "config";
  return hasAppAdminCapability(roles) && key === "config";
}

export function canOpenWorkspacePage(
  id: WorkspacePageId,
  roles: string[],
  supportRevealed = false,
  customMenuIds: readonly string[] = []
): boolean {
  if (isCustomWorkspacePageId(id)) return customMenuIds.includes(customMenuIdFromPageId(id));
  const systemKey = systemMenuKeyFromPageId(id);
  return systemKey === null || canOpenSystemMenu(systemKey, roles, supportRevealed);
}

export function openWorkspacePageTab(state: WorkspacePageTabsState, id: WorkspacePageId): WorkspacePageTabsState {
  const nextOpenIds = state.openIds.includes(id) ? state.openIds : [...state.openIds, id];
  return {
    openIds: nextOpenIds,
    activeId: id,
    lastSystemId: isSystemWorkspacePageId(id) ? id : state.lastSystemId
  };
}

export function moveWorkspacePageTab(
  state: WorkspacePageTabsState,
  id: WorkspacePageId,
  targetIndex: number
): WorkspacePageTabsState {
  const sourceIndex = state.openIds.indexOf(id);
  if (sourceIndex < 0) return state;
  const boundedTarget = Math.max(0, Math.min(targetIndex, state.openIds.length));
  const destination = sourceIndex < boundedTarget ? boundedTarget - 1 : boundedTarget;
  if (destination === sourceIndex) return state;
  const nextOpenIds = [...state.openIds];
  nextOpenIds.splice(sourceIndex, 1);
  nextOpenIds.splice(destination, 0, id);
  return { ...state, openIds: nextOpenIds };
}

export function closeWorkspacePageTabs(
  state: WorkspacePageTabsState,
  id: WorkspacePageId,
  mode: WorkspacePageCloseMode
): { state: WorkspacePageTabsState; navigateTo: WorkspacePageId | "workbench" | null } {
  const targetIndex = state.openIds.indexOf(id);
  if (targetIndex < 0 && mode !== "all") return { state, navigateTo: null };

  let nextOpenIds: WorkspacePageId[];
  if (mode === "all") {
    nextOpenIds = [];
  } else if (mode === "others") {
    nextOpenIds = [id];
  } else if (mode === "left") {
    nextOpenIds = state.openIds.slice(targetIndex);
  } else if (mode === "right") {
    nextOpenIds = state.openIds.slice(0, targetIndex + 1);
  } else {
    nextOpenIds = state.openIds.filter((item) => item !== id);
  }

  if (nextOpenIds.length === 0) {
    return {
      state: { openIds: [], activeId: null, lastSystemId: null },
      navigateTo: "workbench"
    };
  }

  let nextActiveId = state.activeId && nextOpenIds.includes(state.activeId) ? state.activeId : null;
  if (mode === "others") {
    nextActiveId = id;
  } else if (!nextActiveId) {
    nextActiveId = mode === "current"
      ? nextOpenIds[Math.min(targetIndex, nextOpenIds.length - 1)] ?? null
      : id;
  }
  const navigateTo = nextActiveId !== state.activeId ? nextActiveId : null;
  return {
    state: { ...state, openIds: nextOpenIds, activeId: nextActiveId },
    navigateTo
  };
}

export function serializeWorkspacePageTabs(state: WorkspacePageTabsState): string {
  // 自定义页面能否恢复由当前菜单目录决定；这里只排除明确的一次性问题排查页。
  const openIds = state.openIds.filter((id) => id !== "system:support");
  return JSON.stringify({
    version: 1,
    openIds,
    activeId: state.activeId && openIds.some((id) => id === state.activeId) ? state.activeId : null,
    lastSystemId: state.lastSystemId && state.lastSystemId !== "system:support"
      ? state.lastSystemId
      : null
  });
}

export function restoreWorkspacePageTabs(
  raw: string | null,
  roles: string[],
  customMenuIds: readonly string[] = []
): WorkspacePageTabsState {
  if (!raw) return { openIds: [], activeId: null, lastSystemId: null };
  try {
    const stored = JSON.parse(raw) as {
      version?: unknown;
      openIds?: unknown;
      activeId?: unknown;
      lastSystemId?: unknown;
    };
    if (stored.version !== 1 || !Array.isArray(stored.openIds)) {
      return { openIds: [], activeId: null, lastSystemId: null };
    }
    const openIds = Array.from(new Set(stored.openIds))
      .filter(isWorkspacePageId)
      .filter((id) => id !== "system:support" && canOpenWorkspacePage(id, roles, false, customMenuIds));
    const activeId = isWorkspacePageId(stored.activeId) && openIds.includes(stored.activeId)
      ? stored.activeId
      : openIds[0] ?? null;
    const storedLastSystemId = isWorkspacePageId(stored.lastSystemId)
      && isSystemWorkspacePageId(stored.lastSystemId)
      && stored.lastSystemId !== "system:support"
      && canOpenWorkspacePage(stored.lastSystemId, roles)
      ? stored.lastSystemId
      : null;
    // Chromium 108 / ES2022 不支持 Array.prototype.findLast，显式倒序保证恢复逻辑兼容既有基线。
    let fallbackSystemId: `system:${SystemMenuKey}` | null = null;
    for (let index = openIds.length - 1; index >= 0; index -= 1) {
      const candidate = openIds[index];
      if (candidate && isSystemWorkspacePageId(candidate)) {
        fallbackSystemId = candidate;
        break;
      }
    }
    return {
      openIds,
      activeId,
      lastSystemId: storedLastSystemId ?? fallbackSystemId
    };
  } catch {
    return { openIds: [], activeId: null, lastSystemId: null };
  }
}

export function defaultSystemMenuKey(roles: string[]): SystemMenuKey {
  return hasSuperAdminCapability(roles) ? "scheduler"
    : hasSystemAdminCapability(roles) ? "team"
      : "config";
}

export function parseWorkspacePageRoute(
  routeName: unknown,
  rawSection: unknown,
  roles: string[],
  supportRevealed = false,
  rawCustomMenuId?: unknown,
  customMenuIds: readonly string[] = []
): { id: WorkspacePageId; canonicalize: boolean } | null {
  if (routeName === "toolbox" || routeName === "memories" || routeName === "hub") {
    return { id: routeName, canonicalize: false };
  }
  if (routeName === "custom-menu") {
    if (!isCustomMenuId(rawCustomMenuId) || !customMenuIds.includes(rawCustomMenuId)) return null;
    return { id: customMenuPageId(rawCustomMenuId), canonicalize: false };
  }
  if (routeName !== "system") return null;
  if (!hasAppAdminCapability(roles)) return null;
  const fallbackKey = defaultSystemMenuKey(roles);
  if (typeof rawSection !== "string" || rawSection.trim() === "") {
    return { id: `system:${fallbackKey}`, canonicalize: false };
  }
  const requestedKey = SYSTEM_KEY_BY_SECTION[rawSection.trim()];
  if (!requestedKey || !canOpenSystemMenu(requestedKey, roles, supportRevealed)) {
    return { id: `system:${fallbackKey}`, canonicalize: true };
  }
  return { id: `system:${requestedKey}`, canonicalize: false };
}

export function workspacePageRoute(
  id: WorkspacePageId,
  roles: string[]
): { name: string; query?: { section: string }; params?: { menuId: string } } {
  if (isCustomWorkspacePageId(id)) {
    return { name: "custom-menu", params: { menuId: customMenuIdFromPageId(id) } };
  }
  if (!isSystemWorkspacePageId(id)) return { name: id };
  const key = systemMenuKeyFromPageId(id)!;
  if (key === defaultSystemMenuKey(roles)) return { name: "system" };
  return { name: "system", query: { section: SYSTEM_SECTION_BY_KEY[key] } };
}
