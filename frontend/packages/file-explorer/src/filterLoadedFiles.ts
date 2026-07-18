import type { FileTreeEntry, WorkspaceViewEntry } from "@test-agent/shared-types";

export function filterLoadedFiles(entriesByDirectory: Record<string, FileTreeEntry[]>, keyword: string) {
  const normalized = keyword.trim().toLowerCase();
  if (!normalized) {
    return [];
  }
  return Object.values(entriesByDirectory)
    .flat()
    .filter((entry) => entry.type === "file")
    // 工作区 view 会把引用节点一并加载到树中，本地搜索仍只能覆盖物理 workspace。
    .filter((entry) => (entry as Partial<WorkspaceViewEntry>).source !== "REFERENCE")
    .filter((entry) => entry.name.toLowerCase().includes(normalized) || entry.path.toLowerCase().includes(normalized));
}
