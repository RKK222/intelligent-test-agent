import { describe, expect, it } from "vitest";
import type { RunDiffFile } from "@test-agent/shared-types";
import { diffFilesFromPayload, mergeDiffFiles } from "../src/components/workbench-utils";

function file(path: string, additions: number, deletions: number, status = "modified"): RunDiffFile {
  return { path, patch: "", additions, deletions, status };
}

describe("diffFilesFromPayload", () => {
  it("reads file objects from payload.files", () => {
    expect(diffFilesFromPayload({ files: [{ path: "a.ts", additions: 1, deletions: 0 }] })).toEqual([
      { path: "a.ts", patch: "", additions: 1, deletions: 0, status: "modified" }
    ]);
  });

  it("falls back to payload.diff when files missing", () => {
    expect(diffFilesFromPayload({ diff: [{ path: "a.ts" }] })).toEqual([
      { path: "a.ts", patch: "", additions: 0, deletions: 0, status: "modified" }
    ]);
  });

  it("filters out items with empty path", () => {
    expect(diffFilesFromPayload({ files: [{ path: "" }, { path: "b.ts" }] })).toEqual([
      { path: "b.ts", patch: "", additions: 0, deletions: 0, status: "modified" }
    ]);
  });

  it("normalizes opencode session.diff string path arrays", () => {
    // opencode session.diff 事件的 payload.files 是 path 字符串数组，
    // 需要包装成 RunDiffFile 对象才能在 mergeDiffFiles 中按 path 去重累加。
    expect(diffFilesFromPayload({ files: ["a.ts", "b.ts", ""] })).toEqual([
      { path: "a.ts", patch: "", additions: 0, deletions: 0, status: "modified" },
      { path: "b.ts", patch: "", additions: 0, deletions: 0, status: "modified" }
    ]);
  });

  it("mixes string paths and file objects in the same array", () => {
    expect(
      diffFilesFromPayload({ files: ["c.ts", { path: "a.ts", additions: 2, deletions: 1, status: "modified" }] })
    ).toEqual([
      { path: "c.ts", patch: "", additions: 0, deletions: 0, status: "modified" },
      { path: "a.ts", patch: "", additions: 2, deletions: 1, status: "modified" }
    ]);
  });
});

describe("mergeDiffFiles", () => {
  it("appends new paths to the tail of the list", () => {
    const current = [file("a.ts", 1, 0)];
    const incoming = [file("b.ts", 2, 1)];
    expect(mergeDiffFiles(current, incoming)).toEqual([
      file("a.ts", 1, 0),
      file("b.ts", 2, 1)
    ]);
  });

  it("overwrites an existing path with the newest file object", () => {
    const current = [file("a.ts", 1, 0), file("b.ts", 2, 1)];
    const incoming = [file("a.ts", 9, 4)];
    expect(mergeDiffFiles(current, incoming)).toEqual([
      file("a.ts", 9, 4),
      file("b.ts", 2, 1)
    ]);
  });

  it("returns the original list when incoming is empty", () => {
    const current = [file("a.ts", 1, 0)];
    expect(mergeDiffFiles(current, [])).toBe(current);
  });

  it("preserves original entry order on overwrite", () => {
    const current = [file("a.ts", 1, 0), file("b.ts", 2, 0), file("c.ts", 3, 0)];
    const incoming = [file("b.ts", 5, 1), file("d.ts", 0, 0)];
    expect(mergeDiffFiles(current, incoming)).toEqual([
      file("a.ts", 1, 0),
      file("b.ts", 5, 1),
      file("c.ts", 3, 0),
      file("d.ts", 0, 0)
    ]);
  });
});
