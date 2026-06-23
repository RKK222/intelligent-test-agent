import { describe, expect, it } from "vitest";
import type { MessagePart, RunDiffFile } from "@test-agent/shared-types";
import { diffFilesFromPayload, inferDiffFromToolPart, mergeDiffFiles } from "../src/components/workbench-utils";

function file(path: string, additions: number, deletions: number, status = "modified"): RunDiffFile {
  return { path, patch: "", additions, deletions, status };
}

function toolPart(input: Record<string, unknown>, overrides: Partial<Extract<MessagePart, { type: "tool" }>> = {}): Extract<MessagePart, { type: "tool" }> {
  return {
    partId: "part_1",
    type: "tool",
    toolName: "write",
    status: "completed",
    input,
    ...overrides
  };
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

describe("inferDiffFromToolPart", () => {
  it("counts new file lines for write tool", () => {
    // write 工具完成时，前端应能基于 input.content 估算 additions，让"文件变更"卡片显示 +N。
    const result = inferDiffFromToolPart(
      toolPart({ filePath: "/tmp/demo/src/new.ts", content: "export const a = 1\nexport const b = 2\nexport const c = 3" })
    );
    expect(result).toEqual({ path: "/tmp/demo/src/new.ts", patch: "", additions: 3, deletions: 0, status: "added" });
  });

  it("counts new and old lines for edit tool", () => {
    const result = inferDiffFromToolPart(
      toolPart(
        { filePath: "/tmp/demo/src/app.ts", oldString: "const a = 1\nconst b = 2", newString: "const a = 1\nconst b = 2\nconst c = 3" },
        { toolName: "edit" }
      )
    );
    expect(result).toEqual({
      path: "/tmp/demo/src/app.ts",
      patch: "",
      additions: 3,
      deletions: 2,
      status: "modified"
    });
  });

  it("parses unified diff for apply_patch tool", () => {
    const patch = [
      "*** Begin Patch",
      "*** Update File: src/x.ts",
      "@@",
      "-old line",
      "+new line one",
      "+new line two",
      "*** End Patch"
    ].join("\n");
    const result = inferDiffFromToolPart(
      toolPart({ patchText: patch, filePath: "/tmp/demo/src/x.ts" }, { toolName: "apply_patch" })
    );
    // patchText 解析以 +/- 行为准：+2, -1。
    expect(result).toMatchObject({ path: "/tmp/demo/src/x.ts", additions: 2, deletions: 1, status: "modified" });
  });

  it("treats empty write content as zero additions", () => {
    const result = inferDiffFromToolPart(toolPart({ filePath: "/tmp/demo/empty.ts", content: "" }));
    expect(result).toEqual({ path: "/tmp/demo/empty.ts", patch: "", additions: 0, deletions: 0, status: "added" });
  });

  it("ignores tools outside the write-tool allowlist", () => {
    const result = inferDiffFromToolPart(
      toolPart({ filePath: "/tmp/demo/src/app.ts", content: "x" }, { toolName: "read" })
    );
    expect(result).toBeUndefined();
  });

  it("returns undefined when filePath is missing", () => {
    const result = inferDiffFromToolPart(toolPart({ content: "abc" }));
    expect(result).toBeUndefined();
  });
});
