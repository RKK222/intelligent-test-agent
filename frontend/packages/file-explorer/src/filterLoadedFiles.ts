import type { FileTreeEntry, WorkspaceViewEntry } from "@test-agent/shared-types";

/**
 * 工作区搜索面板只按文件名匹配；父目录命中不能把无关文件带入结果。
 */
export function fileNameIncludesKeyword(fileName: string, keyword: string): boolean {
  const normalized = keyword.trim().toLowerCase();
  return normalized.length > 0 && fileName.toLowerCase().includes(normalized);
}

export function filterLoadedFiles(entriesByDirectory: Record<string, FileTreeEntry[]>, keyword: string) {
  if (!keyword.trim()) {
    return [];
  }
  return Object.values(entriesByDirectory)
    .flat()
    .filter((entry) => entry.type === "file")
    // 工作区 view 会把引用节点一并加载到树中，本地搜索仍只能覆盖物理 workspace。
    .filter((entry) => (entry as Partial<WorkspaceViewEntry>).source !== "REFERENCE")
    .filter((entry) => fileNameIncludesKeyword(entry.name, keyword));
}
