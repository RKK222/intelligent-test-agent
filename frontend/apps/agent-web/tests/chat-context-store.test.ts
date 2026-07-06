import { beforeEach, describe, expect, it } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import {
  CHAT_CONTEXT_LIMITS,
  serializeChatContexts,
  useChatContextStore,
  validateChatSend,
  type ChatContextItem
} from "../src/stores/chatContextStore";

function selection(overrides: Partial<Extract<ChatContextItem, { type: "selection" }>> = {}): Extract<ChatContextItem, { type: "selection" }> {
  const text = overrides.text ?? "const a = 1;";
  return {
    id: overrides.id ?? "sel_1",
    type: "selection",
    source: "workspace",
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

  it("validates send input and serializes contexts in stable order", () => {
    const items = [selection(), file({ id: "file_2", path: "docs/api.md", fileName: "api.md" })];

    expect(validateChatSend("x".repeat(CHAT_CONTEXT_LIMITS.MAX_USER_INPUT_CHARS + 1), items)).toEqual({
      ok: false,
      reason: "输入内容过长，请精简后再发送"
    });

    const prompt = serializeChatContexts("怎么改？", items);
    expect(prompt).toContain("用户问题：\n怎么改？");
    expect(prompt).toContain('<context type="selection" path="src/App.ts" lines="3-3">');
    expect(prompt).toContain('<context type="file" path="docs/api.md">');
    expect(prompt.indexOf('type="selection"')).toBeLessThan(prompt.indexOf('type="file"'));
  });
});
