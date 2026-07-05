import type { AgentMessage, MessagePart } from "@test-agent/shared-types";
import type { OpencodeLikeConversationState } from "./types";
import { readPartText } from "./part-text";
import { canonicalMessageId } from "./part-utils";

export type ConversationLocatorTurn = {
  id: string;
  index: number;
  title: string;
  summary: string;
  files: Array<{ path: string; label: string }>;
  extraFileCount: number;
};

const MAX_TITLE_LENGTH = 34;
const MAX_SUMMARY_LENGTH = 96;
const MAX_FILE_CHIPS = 2;

export function createConversationLocatorTurns(state: OpencodeLikeConversationState): ConversationLocatorTurn[] {
  return state.userMessages.map((message, index) => {
    const userMessageId = canonicalMessageId(message);
    const assistantMessages = state.assistantMessagesByParent[userMessageId] ?? [];
    const files = collectTurnFiles(assistantMessages);
    const visibleFiles = files.slice(0, MAX_FILE_CHIPS);

    return {
      id: userMessageId,
      index: index + 1,
      title: compactText(message.text, MAX_TITLE_LENGTH) || `第 ${index + 1} 轮对话`,
      summary: summarizeTurn(assistantMessages, state.streamingTextByPartId, state.running, index === state.userMessages.length - 1),
      files: visibleFiles,
      extraFileCount: Math.max(0, files.length - visibleFiles.length)
    };
  });
}

// 定位器摘要只做前端展示投影：优先展示最终文本；没有文本时再退到思考、工具或运行态。
function summarizeTurn(
  assistantMessages: Array<Extract<AgentMessage, { role: "assistant" }>>,
  streamingTextByPartId: Record<string, string>,
  running: boolean,
  isLatestTurn: boolean
): string {
  const textParts: string[] = [];
  let reasoningPreview = "";
  let toolCount = 0;

  for (const message of assistantMessages) {
    for (const part of message.parts ?? []) {
      if (part.type === "text") {
        const text = readPartText(part, streamingTextByPartId).trim();
        if (text) {
          textParts.push(text);
        }
        continue;
      }
      if (part.type === "reasoning" && !reasoningPreview) {
        reasoningPreview = compactText(readPartText(part, streamingTextByPartId), 56);
      }
      if (part.type === "tool") {
        toolCount += 1;
      }
    }
  }

  const answer = compactText(textParts.join(" "), MAX_SUMMARY_LENGTH);
  if (answer) {
    return answer;
  }
  if (reasoningPreview) {
    return `思考中：${reasoningPreview}`;
  }
  if (toolCount > 0) {
    return `已记录 ${toolCount} 次工具调用`;
  }
  if (running && isLatestTurn) {
    return "正在生成回答";
  }
  return "暂无摘要";
}

// 文件 chips 需要跨 file part 与工具 input 去重，避免同一文件被 read/edit 多次时重复占满弹层。
function collectTurnFiles(assistantMessages: Array<Extract<AgentMessage, { role: "assistant" }>>): Array<{ path: string; label: string }> {
  const seen = new Set<string>();
  const files: Array<{ path: string; label: string }> = [];

  function addPath(path: string | undefined) {
    const normalized = normalizePath(path);
    if (!normalized || seen.has(normalized)) {
      return;
    }
    seen.add(normalized);
    files.push({ path: normalized, label: fileName(normalized) });
  }

  for (const message of assistantMessages) {
    for (const part of message.parts ?? []) {
      if (part.type === "file") {
        addPath(part.path ?? part.name);
        continue;
      }
      if (part.type === "tool") {
        addPath(pathFromToolPart(part));
      }
    }
  }

  return files;
}

function pathFromToolPart(part: Extract<MessagePart, { type: "tool" }>): string | undefined {
  const input = part.input ?? {};
  return firstText(input.filePath, input.path, input.file_path, input.file, input.directory, input.target, input.uri);
}

function firstText(...values: unknown[]): string | undefined {
  return values.find((value): value is string => typeof value === "string" && value.trim().length > 0);
}

function normalizePath(path: string | undefined): string {
  return (path ?? "").replace(/\\/g, "/").trim();
}

function fileName(path: string): string {
  return path.split("/").filter(Boolean).pop() ?? path;
}

function compactText(text: string, maxLength: number): string {
  const compacted = text.replace(/\s+/g, " ").trim();
  if (!compacted) {
    return "";
  }
  if (compacted.length <= maxLength) {
    return compacted;
  }
  return `${compacted.slice(0, maxLength - 1)}…`;
}
