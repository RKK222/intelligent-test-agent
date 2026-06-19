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
