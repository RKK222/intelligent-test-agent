import { describe, expect, it } from "vitest";
import type { AgentMessage, MessagePart, RunEvent } from "@test-agent/shared-types";
import { createOpencodeLikeState } from "../src/opencode-like/state/adapter";
import { createTimelineRows } from "../src/opencode-like/state/projection";
import { readPartText } from "../src/opencode-like/state/part-text";
import { formatModelLabel } from "../src/opencode-like/state/model-catalog";
import { createInitialAgentChatRuntimeState, reduceAgentChatRuntime } from "../src/runtime-reducer";

describe("opencode-like conversation state", () => {
  it("projects user messages, context tool groups, assistant parts, thinking and diff rows", () => {
    const messages: AgentMessage[] = [
      userMessage("msg_user_1", "分析 checkout 失败"),
      assistantMessage("msg_assistant_1", [
        toolPart("part_read", "read", { filePath: "README.md" }),
        toolPart("part_list", "list", { path: "src" }),
        textPart("part_answer", "定位到 checkout 表单校验失败。")
      ])
    ];

    const state = createOpencodeLikeState({
      messages,
      running: true,
      diffFiles: [{ path: "src/checkout.ts", patch: "@@\n-old\n+new", additions: 1, deletions: 1, status: "modified" }]
    });
    const rows = createTimelineRows(state);

    expect(state.userMessages).toHaveLength(1);
    expect(state.assistantMessagesByParent.msg_user_1).toHaveLength(1);
    expect(state.partsByMessageId.msg_assistant_1.map((part) => part.partId)).toEqual([
      "part_read",
      "part_list",
      "part_answer"
    ]);
    expect(rows.map((row) => row.type)).toEqual([
      "user-message",
      "context-tool-group",
      "assistant-part",
      "thinking",
      "diff-summary"
    ]);
    expect(rows[1]).toMatchObject({
      type: "context-tool-group",
      refs: [
        { messageId: "msg_assistant_1", partId: "part_read" },
        { messageId: "msg_assistant_1", partId: "part_list" }
      ],
      busy: true
    });
    expect(rows[2]).toMatchObject({
      type: "assistant-part",
      messageId: "msg_assistant_1",
      partId: "part_answer",
      previousAssistantPart: true
    });
  });

  it("keeps runtime failures as timeline error rows instead of card messages", () => {
    const state = createOpencodeLikeState({
      messages: [userMessage("msg_user_1", "运行测试")],
      runtimeStatus: { type: "failed", message: "工具调用失败" }
    });

    expect(createTimelineRows(state).at(-1)).toEqual({
      type: "error",
      key: "runtime:error",
      message: "工具调用失败"
    });
  });

  it("formats provider and model labels from the catalog", () => {
    const state = createOpencodeLikeState({
      messages: [],
      providers: [{ providerId: "openai", name: "OpenAI", models: [{ id: "gpt-5", name: "GPT-5" }] }],
      models: [{ id: "claude-sonnet", providerId: "anthropic", name: "Claude Sonnet" }]
    });

    expect(formatModelLabel(state.modelCatalog, { providerId: "openai", id: "gpt-5" })).toBe("OpenAI / GPT-5");
    expect(formatModelLabel(state.modelCatalog, { providerId: "anthropic", id: "claude-sonnet" })).toBe(
      "anthropic / Claude Sonnet"
    );
    expect(formatModelLabel(state.modelCatalog, { id: "missing" })).toBe("missing");
  });

  it("uses streaming overlays without duplicating text and clears them after part updates", () => {
    const withDelta = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("message.part.delta", {
        messageID: "msg_assistant_1",
        partID: "part_text",
        partType: "text",
        delta: "hel"
      })
    });
    const withSecondDelta = reduceAgentChatRuntime(withDelta, {
      type: "event",
      event: event("message.part.delta", {
        messageID: "msg_assistant_1",
        partID: "part_text",
        partType: "text",
        delta: "lo"
      })
    });
    const part = (withSecondDelta.messages[0] as Extract<AgentMessage, { role: "assistant" }>).parts?.[0];

    expect(withSecondDelta.streamingTextByPartId).toEqual({ part_text: "hello" });
    expect(part?.type).toBe("text");
    expect(readPartText(part as Extract<MessagePart, { type: "text" }>, withSecondDelta.streamingTextByPartId)).toBe(
      "hello"
    );

    const completed = reduceAgentChatRuntime(withSecondDelta, {
      type: "event",
      event: event("message.part.updated", {
        messageID: "msg_assistant_1",
        part: { id: "part_text", messageID: "msg_assistant_1", type: "text", text: "hello" }
      })
    });

    expect(completed.streamingTextByPartId).toEqual({});
  });
});

function userMessage(id: string, text: string): Extract<AgentMessage, { role: "user" }> {
  return { id, messageId: id, role: "user", text, createdAt: "2026-07-03T00:00:00Z" };
}

function assistantMessage(id: string, parts: MessagePart[]): Extract<AgentMessage, { role: "assistant" }> {
  return { id, messageId: id, role: "assistant", text: "", parts, createdAt: "2026-07-03T00:00:01Z" };
}

function textPart(partId: string, text: string): Extract<MessagePart, { type: "text" }> {
  return { partId, type: "text", text, status: "completed" };
}

function toolPart(
  partId: string,
  toolName: string,
  input: Record<string, unknown>
): Extract<MessagePart, { type: "tool" }> {
  return { partId, type: "tool", toolName, status: "completed", input };
}

function event(type: string, payload: Record<string, unknown>): RunEvent {
  return {
    eventId: `${type}:${JSON.stringify(payload)}`,
    runId: "run_1",
    seq: 1,
    type,
    traceId: "trace_1",
    occurredAt: "2026-07-03T00:00:00Z",
    payload
  };
}
