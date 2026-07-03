import type { MessagePart } from "@test-agent/shared-types";

export function readPartText(
  part: Extract<MessagePart, { type: "text" | "reasoning" }>,
  streamingTextByPartId: Record<string, string> = {}
): string {
  const overlay = streamingTextByPartId[part.partId] ?? "";
  if (!overlay) {
    return part.text;
  }
  // reducer 兼容旧展示路径时会把 delta 同步写进 part.text；
  // 若 part.text 已包含 overlay，就不能再拼一次，否则流式内容会重复。
  if (part.text.endsWith(overlay)) {
    return part.text;
  }
  return `${part.text}${overlay}`;
}

export function compactPartPreview(text: string, maxLength = 42): string {
  const compacted = text.replace(/\s+/g, " ").trim();
  if (!compacted) {
    return "";
  }
  return compacted.length > maxLength ? `...${compacted.slice(-maxLength)}` : compacted;
}
