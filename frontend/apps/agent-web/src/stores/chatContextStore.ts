import { computed, ref } from "vue";
import { defineStore } from "pinia";
import type { PromptPart } from "@test-agent/shared-types";

export const CHAT_CONTEXT_LIMITS = {
  MAX_SELECTION_CHARS: 20_000,
  MAX_FILE_CHARS: 80_000,
  MAX_TOTAL_CONTEXT_CHARS: 120_000,
  MAX_USER_INPUT_CHARS: 20_000
} as const;

export type ChatSelectionContextItem = {
  id: string;
  type: "selection";
  source: "workspace";
  path: string;
  fileName: string;
  language?: string;
  startLine: number;
  endLine: number;
  text: string;
  charCount: number;
  createdAt: number;
};

export type ChatFileContextItem = {
  id: string;
  type: "file";
  source: "workspace";
  path: string;
  fileName: string;
  language?: string;
  content: string;
  charCount: number;
  lineCount: number;
  sizeBytes?: number;
  createdAt: number;
};

export type ChatContextItem = ChatSelectionContextItem | ChatFileContextItem;

export type ChatContextValidationResult = { ok: true } | { ok: false; reason: string };
export type ChatContextLogSummary = {
  type: ChatContextItem["type"];
  path: string;
  fileName: string;
  charCount: number;
  startLine?: number;
  endLine?: number;
};

export function createContextId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return `ctx_${crypto.randomUUID()}`;
  }
  return `ctx_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
}

export function getContextItemText(item: ChatContextItem): string {
  return item.type === "selection" ? item.text : item.content;
}

export function getContextItemLineText(item: ChatContextItem): string {
  return item.type === "selection" ? `L${item.startLine}-L${item.endLine}` : `${item.lineCount} 行`;
}

export function validateChatContextItem(
  item: ChatContextItem,
  existingItems: ChatContextItem[] = []
): ChatContextValidationResult {
  if (item.type === "selection" && item.charCount > CHAT_CONTEXT_LIMITS.MAX_SELECTION_CHARS) {
    return { ok: false, reason: "选区过长，请缩小选择范围" };
  }
  if (item.type === "file" && item.charCount > CHAT_CONTEXT_LIMITS.MAX_FILE_CHARS) {
    return { ok: false, reason: "文件过大，请选择部分内容添加" };
  }
  const duplicateFile = item.type === "file" && existingItems.some((existing) => existing.type === "file" && existing.path === item.path);
  if (duplicateFile) {
    return { ok: false, reason: "该文件已添加" };
  }
  const total = existingItems.reduce((sum, existing) => sum + existing.charCount, 0) + item.charCount;
  if (total > CHAT_CONTEXT_LIMITS.MAX_TOTAL_CONTEXT_CHARS) {
    return { ok: false, reason: "上下文总量过大，请删除部分附件" };
  }
  return { ok: true };
}

export function validateChatSend(input: string, items: ChatContextItem[]): ChatContextValidationResult {
  const total = items.reduce((sum, item) => sum + item.charCount, 0);
  if (total > CHAT_CONTEXT_LIMITS.MAX_TOTAL_CONTEXT_CHARS) {
    return { ok: false, reason: "上下文内容过长，请删除部分附件后再发送" };
  }
  if (input.length > CHAT_CONTEXT_LIMITS.MAX_USER_INPUT_CHARS) {
    return { ok: false, reason: "输入内容过长，请精简后再发送" };
  }
  return { ok: true };
}

export function serializeChatContexts(userInput: string, items: ChatContextItem[]): string {
  const input = userInput.trim();
  if (items.length === 0) {
    return input;
  }
  const blocks = items.map((item) => {
    if (item.type === "selection") {
      return [
        `<context type="selection" path="${escapeContextAttribute(item.path)}" lines="${item.startLine}-${item.endLine}">`,
        item.text,
        "</context>"
      ].join("\n");
    }
    return [
      `<context type="file" path="${escapeContextAttribute(item.path)}">`,
      item.content,
      "</context>"
    ].join("\n");
  });
  return [`用户问题：`, input, "", "以下是用户添加的工作区上下文：", "", blocks.join("\n\n")].join("\n");
}

function escapeContextAttribute(value: string): string {
  return value.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
}

export function chatContextItemsToPromptParts(items: ChatContextItem[]): Extract<PromptPart, { type: "file" }>[] {
  const parts: Extract<PromptPart, { type: "file" }>[] = items
    .filter((item): item is ChatFileContextItem => item.type === "file")
    .map((item) => ({
      type: "file",
      path: item.path,
      name: item.fileName,
      mimeType: "text/plain",
      content: item.content,
      source: {
        text: item.content,
        start: 0,
        end: item.content.length,
        contextType: "file"
      }
    }));
  if (items.length > 0) {
    console.debug("workspace_context_parts_built", {
      component: "chatContextStore",
      action: "build_prompt_parts",
      count: items.length,
      partsCount: parts.length,
      items: summarizeChatContextItems(items)
    });
  }
  return parts;
}

export function summarizeChatContextItems(items: ChatContextItem[]): ChatContextLogSummary[] {
  return items.map((item) => ({
    type: item.type,
    path: item.path,
    fileName: item.fileName,
    charCount: item.charCount,
    ...(item.type === "selection" ? { startLine: item.startLine, endLine: item.endLine } : {})
  }));
}

export const useChatContextStore = defineStore("chat-context", () => {
  const items = ref<ChatContextItem[]>([]);
  const lastError = ref<string | null>(null);

  const totalCharCount = computed(() => items.value.reduce((sum, item) => sum + item.charCount, 0));
  const isOverLimit = computed(() => totalCharCount.value > CHAT_CONTEXT_LIMITS.MAX_TOTAL_CONTEXT_CHARS);

  function addContext(item: ChatContextItem): ChatContextValidationResult {
    const validation = validateChatContextItem(item, items.value);
    if (!validation.ok) {
      lastError.value = validation.reason;
      return validation;
    }
    items.value = [...items.value, item];
    lastError.value = null;
    return validation;
  }

  function addSelectionContext(item: ChatSelectionContextItem): ChatContextValidationResult {
    return addContext(item);
  }

  function addFileContext(item: ChatFileContextItem): ChatContextValidationResult {
    return addContext(item);
  }

  function removeContext(id: string) {
    items.value = items.value.filter((item) => item.id !== id);
    lastError.value = null;
  }

  function clearContexts() {
    items.value = [];
    lastError.value = null;
  }

  function getTotalCharCount() {
    return totalCharCount.value;
  }

  return {
    items,
    lastError,
    totalCharCount,
    isOverLimit,
    addSelectionContext,
    addFileContext,
    removeContext,
    clearContexts,
    getTotalCharCount
  };
});
