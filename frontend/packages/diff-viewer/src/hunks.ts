import type { PromptPart, RunDiffFile } from "@test-agent/shared-types";

export type DiffHunk = {
  index: number;
  filePath: string;
  oldStart: number;
  oldLines: number;
  newStart: number;
  newLines: number;
  heading: string;
  patch: string;
};

const HUNK_HEADER = /^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@\s?(.*)$/;

export function parseDiffHunks(file: Pick<RunDiffFile, "path" | "patch">): DiffHunk[] {
  const hunks: DiffHunk[] = [];
  let current: Omit<DiffHunk, "patch"> | undefined;
  let patchLines: string[] = [];

  for (const line of file.patch.split("\n")) {
    const match = HUNK_HEADER.exec(line);
    if (match) {
      if (current) {
        hunks.push({ ...current, patch: patchLines.join("\n") });
      }
      current = {
        index: hunks.length,
        filePath: file.path,
        oldStart: Number(match[1]),
        oldLines: Number(match[2] ?? 1),
        newStart: Number(match[3]),
        newLines: Number(match[4] ?? 1),
        heading: match[5] ?? ""
      };
      patchLines = [];
      continue;
    }
    if (!current || line.startsWith("---") || line.startsWith("+++")) {
      continue;
    }
    patchLines.push(line);
  }

  if (current) {
    hunks.push({ ...current, patch: patchLines.join("\n") });
  }

  return hunks;
}

export function selectAdjacentHunk(hunks: DiffHunk[], currentIndex: number, direction: "previous" | "next"): DiffHunk | undefined {
  if (hunks.length === 0) {
    return undefined;
  }
  const delta = direction === "previous" ? -1 : 1;
  const nextIndex = (currentIndex + delta + hunks.length) % hunks.length;
  return hunks[nextIndex];
}

export function hunkToPromptPart(file: Pick<RunDiffFile, "path">, hunk: DiffHunk): Extract<PromptPart, { type: "file" }> {
  return {
    type: "file",
    path: file.path,
    name: file.path.split("/").at(-1) ?? file.path,
    source: {
      start: hunk.newStart,
      end: Math.max(hunk.newStart, hunk.newStart + hunk.newLines - 1),
      text: hunk.patch.slice(0, 12000)
    }
  };
}
