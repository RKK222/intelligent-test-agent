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
  // 这些是 OpenCode 会话回放/输入引用的原生元数据，不是 assistant timeline 卡片。
  // 数据仍完整保留在消息 state，供历史恢复、审计和 task 子会话索引使用；其中子 Agent
  // 只通过已建立映射的 tool=task 卡片进入，不能把裸 SubtaskPart 渲染成未知 JSON。
  if (
    part.type === "subtask" ||
    part.type === "step-start" ||
    part.type === "step-finish" ||
    part.type === "snapshot" ||
    part.type === "patch" ||
    part.type === "agent"
  ) {
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
