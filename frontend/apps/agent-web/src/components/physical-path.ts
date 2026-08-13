import type { Workspace } from "@test-agent/shared-types";

const WINDOWS_DRIVE_ABSOLUTE = /^[A-Za-z]:\//;
const URI_LIKE_PATH = /^[A-Za-z][A-Za-z0-9+.-]*:/;

/**
 * 只接受可直接交给本机文件能力的绝对路径，并统一 Windows 分隔符。
 * `personalworktree:`、`agent-workspace:` 等逻辑定位符不会被误判为物理路径。
 */
export function normalizePhysicalAbsolutePath(value?: string | null): string | undefined {
  if (typeof value !== "string") return undefined;
  const normalized = value.trim().replace(/\\/g, "/");
  if (!normalized) return undefined;
  if (normalized.startsWith("/") || WINDOWS_DRIVE_ABSOLUTE.test(normalized)) {
    if (normalized.split("/").some((segment) => segment === "..")) return undefined;
    return normalized;
  }
  return undefined;
}

/** 普通工作区只接受专用字段；rootPath 已是逻辑定位符，禁止再作为绝对路径兜底。 */
export function workspacePhysicalRootPath(
  workspace?: Pick<Workspace, "rootPath" | "physicalRootPath"> | null
): string | undefined {
  return normalizePhysicalAbsolutePath(workspace?.physicalRootPath);
}

/**
 * 将可信工作区工具路径归一化为相对路径。没有物理根时绝对路径必须失败关闭，不能把宿主机路径
 * 误发给文件 WebSocket；后端生成的 diff.proposed 会继续提供可信的工作区相对路径。
 */
export function normalizeWorkspaceRelativePath(
  raw: string,
  physicalRootPath?: string
): string | undefined {
  let path = raw.trim().replace(/^([ab])\//, "").replace(/\\/g, "/");
  const root = normalizePhysicalAbsolutePath(physicalRootPath)?.replace(/\/+$/, "");
  if (root) {
    if (path === root) path = "";
    else if (path.startsWith(`${root}/`)) path = path.slice(root.length + 1);
  }
  while (path.startsWith("./")) path = path.slice(2);
  path = path.replace(/\/+$/, "").replace(/\/+/g, "/");
  if (!path || path.startsWith("/") || WINDOWS_DRIVE_ABSOLUTE.test(path) || URI_LIKE_PATH.test(path)) {
    return undefined;
  }
  if (path.split("/").some((segment) => segment === "..")) return undefined;
  return path;
}

/** 批量过滤并归一化 Diff/历史文件；非法绝对路径不得回退到原值。 */
export function normalizeWorkspaceRelativeEntries<T extends { path: string }>(
  entries: readonly T[],
  physicalRootPath?: string
): T[] {
  return entries.flatMap((entry) => {
    const path = normalizeWorkspaceRelativePath(entry.path, physicalRootPath);
    return path ? [{ ...entry, path }] : [];
  });
}
