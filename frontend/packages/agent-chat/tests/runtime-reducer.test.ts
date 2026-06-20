import { describe, expect, it } from "vitest";
import { createInitialAgentChatRuntimeState, reduceAgentChatRuntime } from "../src/runtime-reducer";
import type { RunEvent } from "@test-agent/shared-types";

describe("agent-chat runtime reducer", () => {
  it("keeps assistant.message.delta backward compatible", () => {
    const state = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("assistant.message.delta", { text: "hello" })
    });

    expect(state.messages).toMatchObject([{ role: "assistant", text: "hello" }]);
  });

  it("merges message.part.delta into a stable assistant part", () => {
    const first = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("message.part.delta", { messageId: "msg_1", partId: "part_1", delta: "hel", partType: "text" })
    });
    const second = reduceAgentChatRuntime(first, {
      type: "event",
      event: event("message.part.delta", { messageId: "msg_1", partId: "part_1", delta: "lo", partType: "text" })
    });

    expect(second.messages).toMatchObject([
      {
        role: "assistant",
        messageId: "msg_1",
        text: "hello",
        parts: [{ partId: "part_1", type: "text", text: "hello" }]
      }
    ]);
  });

  it("upserts opencode snapshot message and part updates", () => {
    const withMessage = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("message.updated", { message: { id: "msg_1", role: "assistant" } })
    });
    const withPart = reduceAgentChatRuntime(withMessage, {
      type: "event",
      event: event("message.part.updated", {
        messageID: "msg_1",
        part: { id: "part_1", messageID: "msg_1", type: "text", text: "restored" }
      })
    });

    expect(withPart.messages).toMatchObject([
      {
        role: "assistant",
        messageId: "msg_1",
        text: "restored",
        parts: [{ partId: "part_1", type: "text", text: "restored" }]
      }
    ]);
  });

  it("keeps reasoning part type when streaming delta omits partType", () => {
    const withReasoning = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("message.part.updated", {
        messageID: "msg_1",
        part: { id: "part_reasoning", messageID: "msg_1", type: "reasoning", text: "I should" }
      })
    });
    const withDelta = reduceAgentChatRuntime(withReasoning, {
      type: "event",
      event: event("message.part.delta", {
        messageID: "msg_1",
        partID: "part_reasoning",
        field: "text",
        delta: " inspect the files"
      })
    });

    expect(withDelta.messages).toMatchObject([
      {
        role: "assistant",
        messageId: "msg_1",
        text: "",
        parts: [{ partId: "part_reasoning", type: "reasoning", text: "I should inspect the files" }]
      }
    ]);
  });

  it("tracks permission and question dock state from events", () => {
    const asked = ["permission.asked", "question.asked"].reduce(
      (state, type) =>
        reduceAgentChatRuntime(state, {
          type: "event",
          event: event(type, {
            requestId: `${type}-1`,
            sessionId: "ses_1",
            permission: "bash",
            questions: [{ id: "q1", text: "Run tests?", kind: "single", options: [{ id: "yes", label: "Yes" }] }]
          })
        }),
      createInitialAgentChatRuntimeState()
    );

    expect(asked.permissions).toHaveLength(1);
    expect(asked.questions).toHaveLength(1);

    const replied = reduceAgentChatRuntime(asked, {
      type: "event",
      event: event("permission.replied", { requestId: "permission.asked-1" })
    });

    expect(replied.permissions).toHaveLength(0);
    expect(replied.questions).toHaveLength(1);
  });

  it("merges tool started and finished events for the same call", () => {
    const started = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("tool.started", {
        tool: "read",
        callId: "call_1",
        partId: "part_tool",
        title: "读取报告",
        status: "running"
      })
    });
    const finished = reduceAgentChatRuntime(started, {
      type: "event",
      event: event("tool.finished", {
        tool: "read",
        callId: "call_1",
        partId: "part_tool",
        status: "success",
        summary: "报告读取完成"
      })
    });

    expect(finished.messages.filter((message) => message.role === "card" && message.cardType === "tool")).toHaveLength(1);
    expect(finished.messages).toMatchObject([
      {
        role: "card",
        cardType: "tool",
        title: "工具调用完成",
        payload: { callId: "call_1", status: "success", summary: "报告读取完成" }
      }
    ]);
  });

  it("keeps expanded todo metadata for task decomposition display", () => {
    const state = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("todo.updated", {
        items: [
          {
            id: "task_1",
            title: "整理失败报告",
            description: "读取 run summary",
            status: "in_progress",
            summary: "已定位 3 个失败用例",
            result: "准备生成建议",
            error: "selector timeout",
            steps: ["读取报告", "聚合失败"],
            updatedAt: "2026-06-20T10:00:00Z"
          }
        ]
      })
    });

    expect(state.todos[0]).toMatchObject({
      id: "task_1",
      text: "整理失败报告",
      title: "整理失败报告",
      description: "读取 run summary",
      summary: "已定位 3 个失败用例",
      result: "准备生成建议",
      error: "selector timeout",
      steps: ["读取报告", "聚合失败"],
      updatedAt: "2026-06-20T10:00:00Z"
    });
  });

  it("records terminal run status from run events", () => {
    const failed = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("run.failed", { status: "FAILED", errorCode: "TOOL_ERROR" })
    });
    const cancelled = reduceAgentChatRuntime(failed, {
      type: "event",
      event: event("run.cancelled", { status: "CANCELLED" })
    });

    expect(failed.status).toBe("FAILED");
    expect(cancelled.status).toBe("CANCELLED");
  });
});

function event(type: string, payload: Record<string, unknown>): RunEvent {
  return {
    eventId: `evt_${type}`,
    runId: "run_1",
    seq: 1,
    type,
    traceId: "trace_1",
    occurredAt: "2026-06-19T00:00:00Z",
    payload
  };
}
