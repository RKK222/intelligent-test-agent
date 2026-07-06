import type { PromptPart } from "@test-agent/shared-types";

const CONTEXT_PROMPT_PREFIX = "用户问题：";
const CONTEXT_PROMPT_MARKER = "以下是用户添加的工作区上下文：";

export type UserPromptWorkspaceContextAttachment = {
  type: "selection" | "file";
  path: string;
  fileName: string;
  lines?: string;
};

// 后端持久化的 user prompt 可能包含前端拼接的工作区上下文；消息气泡只展示用户原始提问。
export function displayTextFromUserPrompt(text: string): string {
  const normalized = text.replace(/\r\n/g, "\n");
  const prefixedPattern = new RegExp(
    `^${escapeRegExp(CONTEXT_PROMPT_PREFIX)}\\s*([\\s\\S]*?)\\s*${escapeRegExp(CONTEXT_PROMPT_MARKER)}\\s*[\\s\\S]*$`
  );
  const prefixedMatch = normalized.match(prefixedPattern);
  if (prefixedMatch) {
    return prefixedMatch[1]?.trim() || "附件上下文";
  }

  const markerIndex = normalized.indexOf(`\n${CONTEXT_PROMPT_MARKER}`);
  if (markerIndex >= 0) {
    const question = normalized.slice(0, markerIndex).trim();
    return question || "附件上下文";
  }

  return text;
}

export function workspaceContextAttachmentsFromUserPrompt(text: string): UserPromptWorkspaceContextAttachment[] {
  const normalized = text.replace(/\r\n/g, "\n");
  if (!normalized.includes(CONTEXT_PROMPT_MARKER)) {
    return [];
  }
  const attachments: UserPromptWorkspaceContextAttachment[] = [];
  const contextTagPattern = /<context\s+([^>]*)>/g;
  for (const match of normalized.matchAll(contextTagPattern)) {
    const attrs = contextAttributes(match[1] ?? "");
    const type = attrs.type === "selection" || attrs.type === "file" ? attrs.type : undefined;
    const path = attrs.path ? decodeContextAttribute(attrs.path) : "";
    if (!type || !path) {
      continue;
    }
    attachments.push({
      type,
      path,
      fileName: fileNameFromPath(path),
      lines: type === "selection" ? attrs.lines : undefined
    });
  }
  return uniqueWorkspaceContextAttachments(attachments);
}

export function workspaceContextAttachmentsFromPromptParts(
  parts: PromptPart[] | undefined
): UserPromptWorkspaceContextAttachment[] {
  const attachments: UserPromptWorkspaceContextAttachment[] = (parts ?? [])
    .filter((part): part is Extract<PromptPart, { type: "file" }> => part.type === "file")
    .map((part) => {
      const path = part.path ?? part.name ?? "attachment";
      const contextType = part.source?.contextType === "selection" ? "selection" : "file";
      const startLine = part.source?.startLine;
      const endLine = part.source?.endLine;
      return {
        type: contextType,
        path,
        fileName: part.name ?? fileNameFromPath(path),
        lines:
          contextType === "selection" && Number.isFinite(startLine) && Number.isFinite(endLine)
            ? `${startLine}-${endLine}`
            : undefined
      };
    });
  return uniqueWorkspaceContextAttachments(attachments);
}

// opencode 实时 message.updated 与本地 optimistic user message 可能携带同一 file part，展示层按来源去重。
function uniqueWorkspaceContextAttachments(
  attachments: UserPromptWorkspaceContextAttachment[]
): UserPromptWorkspaceContextAttachment[] {
  const seen = new Set<string>();
  return attachments.filter((attachment) => {
    const key = `${attachment.type}:${attachment.path}:${attachment.lines ?? ""}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function contextAttributes(source: string): Record<string, string> {
  const attrs: Record<string, string> = {};
  const attrPattern = /([\w-]+)="([^"]*)"/g;
  for (const match of source.matchAll(attrPattern)) {
    const key = match[1];
    const value = match[2];
    if (key && value !== undefined) {
      attrs[key] = value;
    }
  }
  return attrs;
}

function decodeContextAttribute(value: string): string {
  return value.replace(/&quot;/g, '"').replace(/&lt;/g, "<").replace(/&amp;/g, "&");
}

function fileNameFromPath(path: string): string {
  return path.split(/[\\/]/).filter(Boolean).at(-1) ?? path;
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
