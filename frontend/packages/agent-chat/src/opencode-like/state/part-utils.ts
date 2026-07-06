import type { AgentMessage, MessagePart } from "@test-agent/shared-types";
import type { RenderablePartGroup } from "./types";
import { isContextTool } from "./tool-registry";

export function canonicalMessageId(message: Extract<AgentMessage, { role: "user" | "assistant" }>): string {
  return message.messageId ?? message.id;
}

export function isTextualPart(part: MessagePart): part is Extract<MessagePart, { type: "text" | "reasoning" }> {
  return part.type === "text" || part.type === "reasoning";
}

export function isRenderablePart(part: MessagePart, options: { showReasoningSummaries: boolean }): boolean {
  // step-start 只是模型回合边界标记，本身没有可见内容；避免它抢占助手头像行。
  if (part.type === "step-start") {
    return false;
  }
  if (part.type === "text") {
    return part.text.trim().length > 0;
  }
  if (part.type === "reasoning") {
    return options.showReasoningSummaries && (part.text.trim().length > 0 || part.status === "running");
  }
  if (part.type === "file") {
    return false;
  }
  return true;
}

export function groupRenderableParts(
  parts: MessagePart[],
  options: { showReasoningSummaries: boolean }
): RenderablePartGroup[] {
  const groups: RenderablePartGroup[] = [];
  let contextRefs: Array<{ partId: string }> = [];

  function flushContextGroup() {
    if (contextRefs.length === 0) {
      return;
    }
    groups.push({
      type: "context-tool-group",
      key: `context:${contextRefs.map((ref) => ref.partId).join(":")}`,
      refs: contextRefs
    });
    contextRefs = [];
  }

  for (const part of parts) {
    if (!isRenderablePart(part, options)) {
      continue;
    }
    if (part.type === "tool" && isContextTool(part)) {
      contextRefs.push({ partId: part.partId });
      continue;
    }
    flushContextGroup();
    groups.push({ type: "part", partId: part.partId });
  }

  flushContextGroup();
  return groups;
}
