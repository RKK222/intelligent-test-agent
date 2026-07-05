import { describe, expect, it } from "vitest";
import type { MessagePart, RunDiffFile } from "@test-agent/shared-types";
import {
  diffFilesFromPayload,
  diffFilesFromSessionMessages,
  filterWorkspaceRootEntries,
  inferDiffFromToolPart,
  runEventMatchesRun,
  mergeDiffFiles,
  messagesFromSessionMessages,
  normalizePathKey,
  sessionTitleFromFirstMessage,
  workspaceLoadIsCurrent
} from "../src/components/workbench-utils";
import type { FileTreeEntry } from "@test-agent/shared-types";

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

describe("filterWorkspaceRootEntries", () => {
  it("hides .opencode only from the workspace root", () => {
    const entries: FileTreeEntry[] = [
      { path: ".opencode", name: ".opencode", type: "directory" },
      { path: "src", name: "src", type: "directory" }
    ];

    expect(filterWorkspaceRootEntries("", entries)).toEqual([
      { path: "src", name: "src", type: "directory" }
    ]);
    expect(filterWorkspaceRootEntries("config", entries)).toEqual(entries);
  });
});

describe("workspaceLoadIsCurrent", () => {
  it("accepts a result only for the selected workspace and current generation", () => {
    expect(workspaceLoadIsCurrent("wrk_a", 3, "wrk_a", 3)).toBe(true);
    expect(workspaceLoadIsCurrent("wrk_a", 3, "wrk_b", 3)).toBe(false);
    expect(workspaceLoadIsCurrent("wrk_a", 2, "wrk_a", 3)).toBe(false);
  });
});

describe("runEventMatchesRun", () => {
  it("accepts events only for the subscribed and current run", () => {
    const current = {
      runId: "run_current",
      sessionId: "ses_1",
      workspaceId: "wrk_1",
      status: "RUNNING",
      createdAt: "2026-07-04T09:00:00Z",
      updatedAt: "2026-07-04T09:00:00Z"
    };

    expect(runEventMatchesRun({ runId: "run_current" }, "run_current", current)).toBe(true);
    expect(runEventMatchesRun({ runId: "run_old" }, "run_current", current)).toBe(false);
    expect(runEventMatchesRun({ runId: "run_current" }, "run_old", current)).toBe(false);
    expect(runEventMatchesRun({ runId: "run_current" }, "run_current", { ...current, runId: "run_next" })).toBe(false);
  });
});

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

  it("deduplicates already-normalized paths in different forms", () => {
    // 调用方（AgentWorkbench.applyToolChangeToDiff / diff.proposed 事件处理）已用
    // normalizeWorkspacePath 把 Windows 绝对路径、a/ b/ 前缀等归一化为同一种相对路径形态，
    // mergeDiffFiles 的职责是把这些"已归一化"路径再按形态 key 折叠，避免大小写、反斜杠残留造成重复。
    // 这里模拟两条来源的同一文件（已被调用方归一化），验证它们合并为一条。
    const current = [{ ...file("src/App.vue", 1, 0), patch: "old patch" }];
    const incoming = [file("src/app.vue", 2, 1)];
    const merged = mergeDiffFiles(current, incoming);
    expect(merged).toHaveLength(1);
    expect(merged[0]).toEqual({ path: "src/app.vue", patch: "", additions: 2, deletions: 1, status: "modified" });
  });

  it("collapses mixed backslash / forward-slash forms into a single key", () => {
    // 模拟调用方未做 separator 折叠时的边缘场景：mergeDiffFiles 自身也要兜底
    // 让 "src\\App.vue" 与 "src/App.vue" 落到同一个 key。
    const merged = mergeDiffFiles([file("src\\App.vue", 1, 0)], [file("src/App.vue", 2, 1)]);
    expect(merged).toHaveLength(1);
    expect(merged[0]).toEqual(file("src/App.vue", 2, 1));
  });

  it("deduplicates git a/ b/ prefixes as the same path", () => {
    const current = [file("a/src/App.vue", 1, 0)];
    const incoming = [file("b/src/App.vue", 3, 1)];
    expect(mergeDiffFiles(current, incoming)).toHaveLength(1);
    expect(mergeDiffFiles(current, incoming)[0]).toEqual(file("b/src/App.vue", 3, 1));
  });

  it("is case-insensitive on the path key for Windows file systems", () => {
    expect(mergeDiffFiles([file("SRC/App.vue", 1, 0)], [file("src/app.vue", 2, 1)])).toHaveLength(1);
  });
});

describe("normalizePathKey", () => {
  it("strips git a/ b/ prefixes and folds separators", () => {
    expect(normalizePathKey("a/src/App.vue")).toBe("src/app.vue");
    expect(normalizePathKey("b\\src\\App.vue")).toBe("src/app.vue");
  });
  it("removes trailing slashes and collapses repeated slashes", () => {
    expect(normalizePathKey("./src//App.vue/")).toBe("src/app.vue");
  });
  it("returns empty string for falsy input", () => {
    expect(normalizePathKey("")).toBe("");
    expect(normalizePathKey(undefined)).toBe("");
  });
});

describe("historical session restoration", () => {
  it("normalizes raw opencode parts and restores generated documents", () => {
    const messages = [
      {
        messageId: "msg_1",
        sessionId: "ses_1",
        role: "ASSISTANT",
        content: "测试文档已生成",
        createdAt: "2026-06-28T08:00:00Z",
        parts: [
          {
            id: "part_write",
            messageID: "msg_1",
            type: "tool",
            tool: "write",
            state: {
              status: "completed",
              input: {
                filePath: "/workspace/docs/登录测试报告.md",
                content: "# 登录测试报告"
              }
            }
          }
        ] as never
      }
    ];

    const mapped = messagesFromSessionMessages(messages);
    expect(mapped[0]).toMatchObject({
      role: "assistant",
      parts: [
        {
          partId: "part_write",
          type: "tool",
          toolName: "write",
          status: "completed",
          input: {
            filePath: "/workspace/docs/登录测试报告.md",
            content: "# 登录测试报告"
          }
        }
      ]
    });
    expect(diffFilesFromSessionMessages(messages)).toMatchObject([
      {
        path: "/workspace/docs/登录测试报告.md",
        additions: 1,
        status: "added"
      }
    ]);
  });

  it("keeps platform and remote message ids when restoring assistant messages", () => {
    const mapped = messagesFromSessionMessages([
      {
        messageId: "msg_11111111111111111111111111111111",
        remoteMessageId: "msg_f2d478d96001861rLCyXjYqf75",
        sessionId: "ses_1",
        role: "ASSISTANT",
        content: "已完成",
        createdAt: "2026-06-28T08:00:00Z"
      }
    ]);

    expect(mapped[0]).toMatchObject({
      id: "msg_11111111111111111111111111111111",
      messageId: "msg_11111111111111111111111111111111",
      platformMessageId: "msg_11111111111111111111111111111111",
      remoteMessageId: "msg_f2d478d96001861rLCyXjYqf75",
      role: "assistant"
    });
  });

  it("uses the first non-empty line as the session title", () => {
    expect(sessionTitleFromFirstMessage("  请生成登录测试案例  ")).toBe("请生成登录测试案例");
    expect(sessionTitleFromFirstMessage(" \n\n  第一行标题  \n第二行不进入标题")).toBe("第一行标题");
  });

  it("truncates long session titles", () => {
    const longTitle = "a".repeat(80);

    expect(sessionTitleFromFirstMessage(longTitle)).toBe(`${"a".repeat(69)}...`);
  });
});

describe("inferDiffFromToolPart", () => {
  it("counts new file lines for write tool and synthesizes a unified diff", () => {
    // write 工具完成时，前端应能基于 input.content 估算 additions，让"文件变更"卡片显示 +N。
    // 同时合成 unified diff 文本，让"文件变更抽屉"右侧能渲染为全 +N 行的视图，
    // 避免点击后展示"暂无 diff 内容"的空态。
    const result = inferDiffFromToolPart(
      toolPart({ filePath: "/tmp/demo/src/new.ts", content: "export const a = 1\nexport const b = 2\nexport const c = 3" })
    );
    expect(result).toMatchObject({
      path: "/tmp/demo/src/new.ts",
      additions: 3,
      deletions: 0,
      status: "added"
    });
    // patch 应包含文件头 + + 前缀的全部内容行
    expect(result?.patch).toContain("--- /dev/null");
    expect(result?.patch).toContain("+++ b/new.ts");
    expect(result?.patch).toContain("@@ -0,0 +1,3 @@");
    expect(result?.patch).toContain("+export const a = 1");
    expect(result?.patch).toContain("+export const b = 2");
    expect(result?.patch).toContain("+export const c = 3");
  });

  it("synthesizes unified diff for edit tool from oldString/newString", () => {
    const result = inferDiffFromToolPart(
      toolPart(
        { filePath: "/tmp/demo/src/app.ts", oldString: "const a = 1\nconst b = 2", newString: "const a = 1\nconst b = 2\nconst c = 3" },
        { toolName: "edit" }
      )
    );
    expect(result).toMatchObject({
      path: "/tmp/demo/src/app.ts",
      additions: 3,
      deletions: 2,
      status: "modified"
    });
    // patch 应包含 - 行（被替换的旧内容）和 + 行（新增内容）
    expect(result?.patch).toContain("--- a/app.ts");
    expect(result?.patch).toContain("+++ b/app.ts");
    expect(result?.patch).toContain("-const a = 1");
    expect(result?.patch).toContain("-const b = 2");
    expect(result?.patch).toContain("+const a = 1");
    expect(result?.patch).toContain("+const b = 2");
    expect(result?.patch).toContain("+const c = 3");
  });

  it("preserves the original patch text for apply_patch tool", () => {
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
    // apply_patch 把原始 patch 文本透传，不再丢成空字符串，让抽屉能完整渲染。
    expect(result).toMatchObject({
      path: "/tmp/demo/src/x.ts",
      additions: 2,
      deletions: 1,
      status: "modified"
    });
    expect(result?.patch).toBe(patch);
  });

  it("treats empty write content as zero additions with header-only patch", () => {
    const result = inferDiffFromToolPart(toolPart({ filePath: "/tmp/demo/empty.ts", content: "" }));
    expect(result).toMatchObject({ path: "/tmp/demo/empty.ts", additions: 0, deletions: 0, status: "added" });
    expect(result?.patch).toBe("--- /dev/null\n+++ b/empty.ts\n@@ -0,0 +1,0 @@");
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
