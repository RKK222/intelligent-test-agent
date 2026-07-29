import type { PromptPart } from "@test-agent/shared-types";

const CONTEXT_PROMPT_PREFIX = "用户问题：";
const CONTEXT_PROMPT_MARKER = "以下是用户添加的工作区上下文：";

export type UserPromptWorkspaceContextAttachment = {
  type: "selection" | "file";
  path: string;
  fileName: string;
  lines?: string;
};

/**
 * 将模型提交用 PromptPart 转换为用户消息展示用元数据。
 * 文件正文、内联 URL 和 source.text 只供模型读取，不进入前端 transcript；路径、文件名和选区行号继续用于附件 chip。
 */
export function promptPartsForUserDisplay(parts: PromptPart[] | undefined): PromptPart[] {
  const displayParts: PromptPart[] = [];
  const seen = new Set<string>();
  for (const part of parts ?? []) {
    if (part.type === "text") {
      const text = displayTextFromUserPrompt(part.text);
      appendUniqueDisplayPart(displayParts, seen, { type: "text", text });
      for (const attachment of workspaceContextAttachmentsFromUserPrompt(part.text)) {
        appendUniqueDisplayPart(displayParts, seen, workspaceContextAttachmentPromptPart(attachment));
      }
      continue;
    }
    if (part.type === "file") {
      appendUniqueDisplayPart(displayParts, seen, {
        type: "file",
        path: part.path,
        name: part.name,
        mimeType: part.mimeType,
        source: part.source
          ? {
              start: part.source.start,
              end: part.source.end,
              startLine: part.source.startLine,
              endLine: part.source.endLine,
              contextType: part.source.contextType
            }
          : undefined
      });
      continue;
    }
    appendUniqueDisplayPart(displayParts, seen, part);
  }
  return displayParts;
}

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
  const attachments: UserPromptWorkspaceContextAttachment[] = [];
  for (const part of parts ?? []) {
    if (part.type === "text") {
      // 选区上下文第一版按 prompt-only 发送，历史与实时展示都需要从 text part 恢复关联 chip。
      attachments.push(...workspaceContextAttachmentsFromUserPrompt(part.text));
      continue;
    }
    if (part.type !== "file") {
      continue;
    }
    const path = part.path ?? part.name ?? "attachment";
    const contextType = part.source?.contextType === "selection" ? "selection" : "file";
    const startLine = part.source?.startLine;
    const endLine = part.source?.endLine;
    attachments.push({
      type: contextType,
      path,
      fileName: part.name ?? fileNameFromPath(path),
      lines:
        contextType === "selection" && Number.isFinite(startLine) && Number.isFinite(endLine)
          ? `${startLine}-${endLine}`
          : undefined
    });
  }
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

function workspaceContextAttachmentPromptPart(
  attachment: UserPromptWorkspaceContextAttachment
): Extract<PromptPart, { type: "file" }> {
  const [startLine, endLine] = (attachment.lines ?? "")
    .split("-")
    .map((value) => Number.parseInt(value, 10));
  return {
    type: "file",
    path: attachment.path,
    name: attachment.fileName,
    source: {
      contextType: attachment.type,
      ...(Number.isFinite(startLine) ? { startLine } : {}),
      ...(Number.isFinite(endLine) ? { endLine } : {})
    }
  };
}

function appendUniqueDisplayPart(parts: PromptPart[], seen: Set<string>, part: PromptPart) {
  const key = displayPromptPartKey(part);
  if (seen.has(key)) {
    return;
  }
  seen.add(key);
  parts.push(part);
}

function displayPromptPartKey(part: PromptPart): string {
  if (part.type === "file") {
    return `file:${part.path ?? ""}:${part.name ?? ""}:${part.source?.contextType ?? ""}:${part.source?.startLine ?? ""}:${part.source?.endLine ?? ""}`;
  }
  if (part.type === "text") {
    return `text:${part.text}`;
  }
  return JSON.stringify(part);
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
