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

/** 新后端显式字段优先；兼容旧后端时只接受确实为绝对路径的 rootPath。 */
export function workspacePhysicalRootPath(
  workspace?: Pick<Workspace, "rootPath" | "physicalRootPath"> | null
): string | undefined {
  return normalizePhysicalAbsolutePath(workspace?.physicalRootPath)
    ?? normalizePhysicalAbsolutePath(workspace?.rootPath);
}

/**
 * 生成复制入口使用的文件绝对路径。显式 copyPath 非绝对路径时直接失败关闭，
 * 普通相对文件只有在工作区物理根存在且不含内部 URI scheme 时才允许拼接。
 */
export function resolvePhysicalFilePath(input: {
  explicitPath?: string;
  workspaceRootPath?: string;
  filePath?: string;
}): string | undefined {
  if (input.explicitPath !== undefined) {
    return normalizePhysicalAbsolutePath(input.explicitPath);
  }
  const filePath = input.filePath?.trim().replace(/\\/g, "/");
  if (!filePath) return undefined;
  const absoluteFile = normalizePhysicalAbsolutePath(filePath);
  if (absoluteFile) return absoluteFile;
  if (URI_LIKE_PATH.test(filePath)) {
    return undefined;
  }
  const root = normalizePhysicalAbsolutePath(input.workspaceRootPath);
  if (!root) return undefined;
  const rootWithoutTrailingSlash = root.replace(/\/+$/, "");
  const relativePath = filePath.replace(/^\.\//, "").replace(/^\/+/, "");
  if (!relativePath || relativePath.split("/").some((segment) => segment === "..")) {
    return undefined;
  }
  return rootWithoutTrailingSlash
    ? `${rootWithoutTrailingSlash}/${relativePath}`
    : `/${relativePath}`;
}
