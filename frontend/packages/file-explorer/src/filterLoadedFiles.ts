import type { FileTreeEntry } from "@test-agent/shared-types";

export function filterLoadedFiles(entriesByDirectory: Record<string, FileTreeEntry[]>, keyword: string) {
  const normalized = keyword.trim().toLowerCase();
  if (!normalized) {
    return [];
  }
  return Object.values(entriesByDirectory)
    .flat()
    .filter((entry) => entry.type === "file")
    .filter((entry) => entry.name.toLowerCase().includes(normalized) || entry.path.toLowerCase().includes(normalized));
}
