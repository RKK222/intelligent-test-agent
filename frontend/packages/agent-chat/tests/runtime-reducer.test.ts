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

  it("normalizes opencode tool state from nested message part updates", () => {
    const state = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("message.part.updated", {
        messageID: "msg_1",
        part: {
          id: "part_tool",
          messageID: "msg_1",
          type: "tool",
          tool: "write",
          callID: "call_1",
          state: {
            status: "completed",
            input: { filePath: "/tmp/demo/src/App.ts" },
            output: "updated",
            metadata: { filepath: "/tmp/demo/src/App.ts" },
            time: { start: "2026-06-19T00:00:01Z", end: "2026-06-19T00:00:02Z" }
          }
        }
      })
    });

    expect(state.messages).toMatchObject([
      {
        role: "assistant",
        messageId: "msg_1",
        parts: [
          {
            partId: "part_tool",
            type: "tool",
            toolName: "write",
            callId: "call_1",
            status: "completed",
            input: { filePath: "/tmp/demo/src/App.ts" },
            output: "updated",
            metadata: { filepath: "/tmp/demo/src/App.ts" },
            startedAt: "2026-06-19T00:00:01Z",
            endedAt: "2026-06-19T00:00:02Z"
          }
        ]
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

  it("normalizes the eight extended part types via message.part.updated", () => {
    const cases = [
      { type: "subtask", part: { id: "p_sub", type: "subtask", prompt: "do x", description: "子任务", agent: "coder", model: "gpt", command: "build" }, expect: { type: "subtask", prompt: "do x", agent: "coder", model: "gpt", command: "build" } },
      { type: "step-start", part: { id: "p_ss", type: "step-start", snapshot: "s" }, expect: { type: "step-start", snapshot: "s" } },
      { type: "step-finish", part: { id: "p_sf", type: "step-finish", reason: "done", cost: 0.0012, tokens: { total: 100, input: 60, output: 40 } }, expect: { type: "step-finish", reason: "done", cost: 0.0012, tokens: { total: 100, input: 60, output: 40 } } },
      { type: "snapshot", part: { id: "p_snap", type: "snapshot", snapshot: "full" }, expect: { type: "snapshot", snapshot: "full" } },
      { type: "patch", part: { id: "p_patch", type: "patch", hash: "abcdef1234", files: ["a.ts", "b.ts"] }, expect: { type: "patch", hash: "abcdef1234", files: ["a.ts", "b.ts"] } },
      { type: "agent", part: { id: "p_agent", type: "agent", name: "build", source: { value: "user", start: 0, end: 1 } }, expect: { type: "agent", name: "build", source: { value: "user", start: 0, end: 1 } } },
      { type: "retry", part: { id: "p_retry", type: "retry", attempt: 2, error: { name: "rate_limit", message: "slow down" }, time: { created: 123 } }, expect: { type: "retry", attempt: 2, error: { name: "rate_limit", message: "slow down" }, time: { created: 123 } } },
      { type: "compaction", part: { id: "p_comp", type: "compaction", auto: true, overflow: false, tail_start_id: "t1" }, expect: { type: "compaction", auto: true, overflow: false, tailStartId: "t1" } }
    ] as const;

    for (const item of cases) {
      const state = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
        type: "event",
        event: event("message.part.updated", { messageID: "msg_1", part: { ...item.part, messageID: "msg_1" } })
      });
      const message = state.messages[state.messages.length - 1];
      expect(message).toMatchObject({ role: "assistant", messageId: "msg_1" });
      const part = (message as { parts?: { type: string }[] }).parts?.[0];
      expect(part).toMatchObject({ partId: item.part.id, ...item.expect });
    }
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
