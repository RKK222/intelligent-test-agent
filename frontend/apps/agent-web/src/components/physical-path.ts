import type { Workspace } from "@test-agent/shared-types";

const WINDOWS_DRIVE_ABSOLUTE = /^[A-Za-z]:\//;

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
