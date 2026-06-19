"use client";

import * as React from "react";
import { useExternalStoreRuntime, type AppendMessage, type ThreadMessageLike } from "@assistant-ui/react";
import type { AgentMessage, MessagePart } from "@test-agent/shared-types";
import type { ComposerAttachment } from "./prompt-parts";

/**
 * card 消息在 ThreadMessageLike.metadata.custom 上的承载结构。
 * assistant-ui 的 external-store runtime 会保留 metadata，渲染时可据此识别
 * 这是一条结构化卡片消息（而非普通文本气泡），交给 AgentCard 渲染。
 */
export type AgentCardMeta = {
  card: true;
  cardType: "plan" | "tool" | "test" | "diff" | "event";
  title: string;
  payload: Record<string, unknown>;
};

/**
 * 把内部 AgentMessage 转换为 assistant-ui 的 ThreadMessageLike。
 * - user/assistant：文本与 parts 映射为标准 part；
 * - card：映射为一条 assistant 消息，payload 放进 metadata.custom，content 留空，
 *   渲染层据此改用 AgentCard，保持与文本消息的交错顺序。
 */
export function convertAgentMessage(message: AgentMessage): ThreadMessageLike {
  if (message.role === "card") {
    const meta: AgentCardMeta = {
      card: true,
      cardType: message.cardType,
      title: message.title,
      payload: message.payload
    };
    return {
      role: "assistant",
      id: message.id,
      createdAt: new Date(message.createdAt),
      content: [],
      metadata: { custom: meta }
    };
  }

  if (message.role === "user") {
    return {
      role: "user",
      id: message.id,
      createdAt: new Date(message.createdAt),
      content: message.text
    };
  }

  // assistant：parts 的结构化渲染由 AssistantThread 从原始 AgentMessage 完成，
  // 这里只把文本汇总喂给 assistant-ui（用于空态判定、复制、可访问性等）。
  const parts = message.parts ?? [];
  const text = parts.length
    ? parts
        .filter((part) => part.type === "text")
        .map((part) => part.text)
        .join("\n")
    : message.text;
  return {
    role: "assistant",
    id: message.id,
    createdAt: new Date(message.createdAt),
    content: text
  };
}

/** 把内部 MessagePart 映射为 assistant-ui 的 assistant 消息 part（保留以备后续按 part 渲染）。 */
export function convertAssistantPart(part: MessagePart) {
  switch (part.type) {
    case "text":
      return { type: "text" as const, text: part.text };
    case "reasoning":
      return { type: "reasoning" as const, text: part.text };
    case "file":
      return {
        type: "file" as const,
        filename: part.name,
        data: part.url ?? "",
        mimeType: part.mimeType ?? "application/octet-stream"
      };
    default:
      // tool / event 等结构化 part，由 AssistantThread 直接从原消息渲染，此处不转。
      return { type: "text" as const, text: "" };
  }
}

/** 从 AppendMessage 中提取纯文本 prompt 与附件，用于转发给现有 onSend。 */
export function extractPromptFromAppendMessage(
  message: AppendMessage,
  attachments: ComposerAttachment[] = []
): { prompt: string; attachments: ComposerAttachment[] } {
  const content = message.content;
  if (typeof content === "string") {
    return { prompt: content, attachments };
  }
  const text = content
    .filter((part): part is { type: "text"; text: string } => part.type === "text")
    .map((part) => part.text)
    .join("\n");
  return { prompt: text, attachments };
}

export type UseAgentExternalRuntimeOptions = {
  messages: AgentMessage[];
  running?: boolean;
  onSend: (prompt: string, attachments?: ComposerAttachment[]) => void;
  onCancel: () => void;
  /**
   * 待发送附件的 ref。composer 的 onNew 只能拿到文本，附件由输入区维护；
   * onNew 读取此 ref 取附件随 prompt 一起转发给 onSend，随后由输入区清空。
   */
  attachmentsRef: React.RefObject<ComposerAttachment[]>;
};

/**
 * 基于 assistant-ui external-store runtime 构建运行时。
 * messages 仍由上游 reducer 单一维护，runtime 只读；
 * onNew 转发给现有 onSend，不引入第二个消息源。
 */
export function useAgentExternalRuntime({ messages, running, onSend, onCancel, attachmentsRef }: UseAgentExternalRuntimeOptions) {
  const onSendRef = React.useRef(onSend);
  const onCancelRef = React.useRef(onCancel);
  onSendRef.current = onSend;
  onCancelRef.current = onCancel;

  return useExternalStoreRuntime<AgentMessage>({
    messages,
    isRunning: running,
    convertMessage: convertAgentMessage,
    onNew: async (message) => {
      const attachments = attachmentsRef.current ?? [];
      const { prompt } = extractPromptFromAppendMessage(message);
      if (!prompt && attachments.length === 0) {
        return;
      }
      onSendRef.current(prompt, attachments);
    },
    onCancel: async () => {
      onCancelRef.current();
    }
  });
}
