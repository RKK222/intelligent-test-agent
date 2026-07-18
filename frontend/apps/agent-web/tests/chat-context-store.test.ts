import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import {
  CHAT_CONTEXT_LIMITS,
  chatContextItemsToPromptParts,
  serializeChatContexts,
  summarizeChatContextItems,
  useChatContextStore,
  validateChatSend,
  type ChatContextItem
} from "../src/stores/chatContextStore";

function selection(overrides: Partial<Extract<ChatContextItem, { type: "selection" }>> = {}): Extract<ChatContextItem, { type: "selection" }> {
  const text = overrides.text ?? "const a = 1;";
  return {
    id: overrides.id ?? "sel_1",
    type: "selection",
    source: overrides.source ?? "workspace",
    path: overrides.path ?? "src/App.ts",
    fileName: overrides.fileName ?? "App.ts",
    language: "typescript",
    startLine: overrides.startLine ?? 3,
    endLine: overrides.endLine ?? 3,
    text,
    charCount: overrides.charCount ?? text.length,
    createdAt: overrides.createdAt ?? 1
  };
}

function file(overrides: Partial<Extract<ChatContextItem, { type: "file" }>> = {}): Extract<ChatContextItem, { type: "file" }> {
  const content = overrides.content ?? "hello\nworld";
  return {
    id: overrides.id ?? "file_1",
    type: "file",
    source: "workspace",
    path: overrides.path ?? "README.md",
    fileName: overrides.fileName ?? "README.md",
    language: "markdown",
    content,
    charCount: overrides.charCount ?? content.length,
    lineCount: overrides.lineCount ?? content.split("\n").length,
    sizeBytes: overrides.sizeBytes ?? content.length,
    openTarget: overrides.openTarget,
    createdAt: overrides.createdAt ?? 2
  };
}

describe("chat context store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it("adds, removes, clears and counts workspace contexts", () => {
    const store = useChatContextStore();

    expect(store.addSelectionContext(selection()).ok).toBe(true);
    expect(store.addFileContext(file()).ok).toBe(true);
    expect(store.items).toHaveLength(2);
    expect(store.getTotalCharCount()).toBe("const a = 1;".length + "hello\nworld".length);

    store.removeContext("sel_1");
    expect(store.items.map((item) => item.id)).toEqual(["file_1"]);

    store.clearContexts();
    expect(store.items).toEqual([]);
  });

  it("blocks oversized and duplicate contexts", () => {
    const store = useChatContextStore();
    const oversized = selection({ text: "x".repeat(CHAT_CONTEXT_LIMITS.MAX_SELECTION_CHARS + 1) });

    expect(store.addSelectionContext(oversized)).toEqual({ ok: false, reason: "选区过长，请缩小选择范围" });
    expect(store.items).toHaveLength(0);

    expect(store.addFileContext(file()).ok).toBe(true);
    expect(store.addFileContext(file({ id: "file_2" }))).toEqual({ ok: false, reason: "该文件已添加" });
  });

  it("validates send input and converts only whole-file contexts to native file prompt parts", () => {
    const items = [selection(), file({ id: "file_2", path: "docs/api.md", fileName: "api.md" })];
    const debug = vi.spyOn(console, "debug").mockImplementation(() => undefined);

    expect(validateChatSend("x".repeat(CHAT_CONTEXT_LIMITS.MAX_USER_INPUT_CHARS + 1), items)).toEqual({
      ok: false,
      reason: "输入内容过长，请精简后再发送"
    });

    expect(chatContextItemsToPromptParts(items)).toEqual([
      {
        type: "file",
        path: "docs/api.md",
        name: "api.md",
        mimeType: "text/plain",
        content: "hello\nworld",
        source: {
          text: "hello\nworld",
          start: 0,
          end: "hello\nworld".length,
          contextType: "file"
        }
      }
    ]);
    expect(debug).toHaveBeenCalledWith("workspace_context_parts_built", {
      component: "chatContextStore",
      action: "build_prompt_parts",
      count: 2,
      partsCount: 1,
      items: [
        { type: "selection", path: "src/App.ts", fileName: "App.ts", charCount: "const a = 1;".length, startLine: 3, endLine: 3 },
        { type: "file", path: "docs/api.md", fileName: "api.md", charCount: "hello\nworld".length }
      ]
    });
    expect(JSON.stringify(debug.mock.calls)).not.toContain("const a = 1;");
    expect(JSON.stringify(debug.mock.calls)).not.toContain("hello\nworld");
    debug.mockRestore();
  });

  it("summarizes workspace contexts without logging content", () => {
    expect(summarizeChatContextItems([selection(), file()])).toEqual([
      { type: "selection", path: "src/App.ts", fileName: "App.ts", charCount: "const a = 1;".length, startLine: 3, endLine: 3 },
      { type: "file", path: "README.md", fileName: "README.md", charCount: "hello\nworld".length }
    ]);
  });

  it("keeps legacy serialized context format available for history display compatibility", () => {
    const items = [selection(), file({ id: "file_2", path: "docs/api.md", fileName: "api.md" })];

    const prompt = serializeChatContexts("怎么改？", items);
    expect(prompt).toContain("用户问题：\n怎么改？");
    expect(prompt).toContain('<context type="selection" path="src/App.ts" lines="3-3">');
    expect(prompt).toContain('<context type="file" path="docs/api.md">');
    expect(prompt.indexOf('type="selection"')).toBeLessThan(prompt.indexOf('type="file"'));
  });

  it("keeps reference aliases independent and preserves locator metadata for prompt and preview", () => {
    const first = file({
      id: "reference:req:guide",
      source: "reference",
      path: "references/requirements/docs/guide.md",
      fileName: "guide.md",
      openTarget: {
        workspaceId: "wrk_1",
        locator: { kind: "REFERENCE", path: "docs/guide.md", referenceAlias: "requirements" }
      }
    });
    const second = file({
      id: "reference:legacy:guide",
      source: "reference",
      path: "references/legacy/docs/guide.md",
      fileName: "guide.md",
      openTarget: {
        workspaceId: "wrk_1",
        locator: { kind: "REFERENCE", path: "docs/guide.md", referenceAlias: "legacy" }
      }
    });
    const store = useChatContextStore();

    expect(store.addFileContext(first).ok).toBe(true);
    expect(store.addFileContext(second).ok).toBe(true);
    expect(chatContextItemsToPromptParts(store.items)).toEqual([
      expect.objectContaining({
        path: "references/requirements/docs/guide.md",
        source: expect.not.objectContaining({ workspaceViewLocator: expect.anything() })
      }),
      expect.objectContaining({
        path: "references/legacy/docs/guide.md",
        source: expect.not.objectContaining({ workspaceViewLocator: expect.anything() })
      })
    ]);
    expect(store.items.map((item) => item.openTarget?.locator)).toEqual([
      first.openTarget?.locator,
      second.openTarget?.locator
    ]);
    expect(serializeChatContexts("比较", store.items)).toContain('path="references/requirements/docs/guide.md"');
    expect(serializeChatContexts("比较", store.items)).toContain('path="references/legacy/docs/guide.md"');
  });
});
