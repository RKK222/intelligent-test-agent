import type {
  FileTreeEntry,
  WorkspaceViewEntry,
  WorkspaceViewLocator,
  WorkspaceViewWarning
} from "@test-agent/shared-types";

export type WorkspaceViewLoadTarget = Pick<WorkspaceViewEntry, "id" | "locator"> & {
  /** 工作区侧真实相对路径，用于条目移动后跨稳定 ID 重新认领目录。 */
  workspacePath?: string;
};

export type WorkspaceViewWarningSnapshot = {
  warnings: WorkspaceViewWarning[];
  truncated: boolean;
};

type WorkspaceFileLoadingTab = {
  path: string;
  loadState?: "loading" | "loaded" | "error";
  hasLoadedSnapshot?: boolean;
};

export type WorkspaceFileRefreshSettlement = {
  path: string;
  patch: {
    loadState: "loaded" | "error";
    loadError: string | undefined;
    hasLoadedSnapshot: boolean;
  };
};

export const ROOT_WORKSPACE_VIEW_TARGET: WorkspaceViewLoadTarget = {
  id: "",
  locator: { kind: "COMPOSITE", path: "" }
};

/** 文件树刷新会废弃当前代次的读取；这里同步结束 loading，确保空白 tab 可见错误并可重试。 */
export function workspaceFileRefreshSettlements(
  tabs: readonly WorkspaceFileLoadingTab[],
  excludedPath: (path: string) => boolean
): WorkspaceFileRefreshSettlement[] {
  return tabs
    .filter((tab) => tab.loadState === "loading" && !excludedPath(tab.path))
    .map((tab) => ({
      path: tab.path,
      patch: tab.hasLoadedSnapshot
        ? {
            loadState: "loaded" as const,
            loadError: undefined,
            hasLoadedSnapshot: true
          }
        : {
            loadState: "error" as const,
            loadError: "文件视图已刷新，请重试",
            hasLoadedSnapshot: false
          }
    }));
}

/** 保存引用配置后仅重放仍能由稳定 id 找回 locator 的展开目录。 */
export function workspaceViewRefreshTargets(
  expandedIds: Set<string>,
  directoryById: Map<string, WorkspaceViewEntry>
): WorkspaceViewLoadTarget[] {
  return [
    ROOT_WORKSPACE_VIEW_TARGET,
    ...[...expandedIds]
      .map((id) => directoryById.get(id))
      .filter((entry): entry is WorkspaceViewEntry => Boolean(entry?.locator && entry.type === "directory"))
      .sort((left, right) => pathDepth(left.locator.path) - pathDepth(right.locator.path))
  ];
}

/**
 * 目录移动会改变工作区稳定 ID；刷新前先迁移工作区侧路径，并补齐新位置的祖先目录，
 * 这样即使目标父目录原先未展开，也能从根开始逐层加载并恢复展开状态。
 */
export function migrateWorkspaceViewRefreshTargets(
  targets: readonly WorkspaceViewLoadTarget[],
  sourcePath: string,
  targetPath: string
): WorkspaceViewLoadTarget[] {
  const root = targets.find((target) => target.id === "") ?? ROOT_WORKSPACE_VIEW_TARGET;
  const workspaceTargets = new Map<string, WorkspaceViewLoadTarget>();
  const otherTargets: WorkspaceViewLoadTarget[] = [];

  for (const target of targets) {
    if (target.id === "") continue;
    if (!target.workspacePath) {
      otherTargets.push(target);
      continue;
    }
    const workspacePath = renameWorkspaceRelativePath(target.workspacePath, sourcePath, targetPath);
    const pathChanged = workspacePath !== normalizeWorkspacePath(target.workspacePath);
    const migrated = {
      ...target,
      workspacePath,
      locator: pathChanged
        ? { ...target.locator, path: renameWorkspaceRelativePath(target.locator.path, sourcePath, targetPath) }
        : target.locator
    };
    const segments = workspacePath.split("/").filter(Boolean);
    for (let depth = 1; depth < segments.length; depth += 1) {
      const ancestor = segments.slice(0, depth).join("/");
      if (!workspaceTargets.has(ancestor)) {
        workspaceTargets.set(ancestor, {
          id: `workspace-refresh:${ancestor}`,
          locator: { kind: "WORKSPACE", path: ancestor },
          workspacePath: ancestor
        });
      }
    }
    workspaceTargets.set(workspacePath, migrated);
  }

  return [
    root,
    ...[...otherTargets, ...workspaceTargets.values()]
      .sort((left, right) => pathDepth(left.workspacePath ?? left.locator.path)
        - pathDepth(right.workspacePath ?? right.locator.path))
  ];
}

/** 根目录刷新后必须使用新父节点返回的 locator，避免继续用刷新前的 WORKSPACE 视图漏掉刚合并的引用。 */
export function revalidatedWorkspaceViewRefreshTarget(
  previous: WorkspaceViewLoadTarget,
  directoryById: Map<string, WorkspaceViewEntry>
): WorkspaceViewLoadTarget | undefined {
  const current = directoryById.get(previous.id);
  if (current?.type === "directory"
    && (!previous.workspacePath || current.workspacePath === previous.workspacePath)) {
    return current;
  }
  if (previous.workspacePath) {
    return [...directoryById.values()].find((entry) =>
      entry.type === "directory" && entry.workspacePath === previous.workspacePath
    );
  }
  return undefined;
}

/** 汇总所有已加载目录的引用告警，并对重复告警和截断提示去重。 */
export function collectWorkspaceViewWarnings(
  snapshots: ReadonlyMap<string, WorkspaceViewWarningSnapshot>
): WorkspaceViewWarning[] {
  const warnings: WorkspaceViewWarning[] = [];
  const seen = new Set<string>();
  let truncated = false;
  for (const snapshot of snapshots.values()) {
    truncated ||= snapshot.truncated;
    for (const warning of snapshot.warnings) {
      const key = `${warning.alias ?? ""}\u0000${warning.code}\u0000${warning.message}`;
      if (seen.has(key)) continue;
      seen.add(key);
      warnings.push(warning);
    }
  }
  if (truncated) {
    warnings.push({
      code: "WORKSPACE_VIEW_TRUNCATED",
      message: "文件树条目过多，当前结果已截断"
    });
  }
  return warnings;
}

function pathDepth(path: string): number {
  return path.split(/[\\/]+/).filter(Boolean).length;
}

function normalizeWorkspacePath(path: string): string {
  return path.split(/[\\/]+/).filter(Boolean).join("/");
}

function renameWorkspaceRelativePath(path: string, sourcePath: string, targetPath: string): string {
  const normalizedPath = normalizeWorkspacePath(path);
  const normalizedSource = normalizeWorkspacePath(sourcePath);
  const normalizedTarget = normalizeWorkspacePath(targetPath);
  if (normalizedPath === normalizedSource) return normalizedTarget;
  return normalizedPath.startsWith(`${normalizedSource}/`)
    ? `${normalizedTarget}${normalizedPath.slice(normalizedSource.length)}`
    : normalizedPath;
}

export function referenceChatPath(alias: string, relativePath: string): string {
  return `references/${alias}/${relativePath.replace(/^[/\\]+/, "")}`;
}

export function referenceLocator(alias: string, path: string): WorkspaceViewLocator {
  return { kind: "REFERENCE", referenceAlias: alias, path };
}

export function workspaceViewEntries(entries: WorkspaceViewEntry[]): WorkspaceViewEntry[] {
  return entries;
}

/** 物理写操作先匹配 workspacePath；legacy path 只允许命中 WORKSPACE locator。 */
export function resolveWorkspaceViewLoadTarget(
  requested: string,
  entryById: Map<string, WorkspaceViewEntry>
): WorkspaceViewLoadTarget | undefined {
  return entryById.get(requested)
    ?? [...entryById.values()].find((entry) => entry.type === "directory" && entry.workspacePath === requested)
    ?? [...entryById.values()].find((entry) =>
      entry.type === "directory" && entry.locator.kind === "WORKSPACE" && entry.locator.path === requested
    );
}

/** 复制入口传物理目录路径，但视图缓存按稳定 id 分桶；先解析缓存桶再计算不覆盖的副本名。 */
export function copiedWorkspaceFileTargetPath(
  sourcePath: string,
  targetDirectory: string,
  entriesByDirectory: Record<string, Array<Pick<FileTreeEntry, "name">>>,
  entryById: Map<string, WorkspaceViewEntry>
): string {
  const cacheKey = resolveWorkspaceViewLoadTarget(targetDirectory, entryById)?.id ?? targetDirectory;
  const existingNames = new Set((entriesByDirectory[cacheKey] ?? []).map((entry) => entry.name));
  const originalName = sourcePath.split(/[\\/]+/).filter(Boolean).at(-1) ?? sourcePath;
  const childPath = (name: string) => targetDirectory
    ? `${targetDirectory}${targetDirectory.includes("\\") ? "\\" : "/"}${name}`
    : name;
  if (!existingNames.has(originalName)) return childPath(originalName);

  const extensionIndex = originalName.lastIndexOf(".");
  const hasExtension = extensionIndex > 0;
  const stem = hasExtension ? originalName.slice(0, extensionIndex) : originalName;
  const extension = hasExtension ? originalName.slice(extensionIndex) : "";
  let sequence = 1;
  let candidate = `${stem} copy${extension}`;
  while (existingNames.has(candidate)) {
    sequence += 1;
    candidate = `${stem} copy ${sequence}${extension}`;
  }
  return childPath(candidate);
}

export function workspaceViewContextIsCurrent(
  expectedWorkspaceId: string,
  expectedGeneration: number,
  currentWorkspaceId: string | undefined,
  currentGeneration: number
): boolean {
  return expectedWorkspaceId === currentWorkspaceId && expectedGeneration === currentGeneration;
}
