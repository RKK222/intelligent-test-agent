import type { RunEvent } from "@test-agent/shared-types";
import { createRunEventState, reduceRunEvent } from "@/stores/runEvents";

function event(type: string, payload: Record<string, unknown>, eventId = `${type}:1`): RunEvent {
  return {
    eventId,
    runId: "run_1",
    seq: 1,
    type,
    traceId: "trace_1",
    occurredAt: "2026-06-20T00:00:00.000Z",
    payload
  };
}

describe("run event reducer", () => {
  it("dedupes eventId while merging message part deltas", () => {
    const state = createRunEventState();

    reduceRunEvent(state, event("message.part.delta", { messageId: "msg_1", partId: "part_1", text: "hel" }, "evt_1"));
    reduceRunEvent(state, event("message.part.delta", { messageId: "msg_1", partId: "part_1", text: "lo" }, "evt_2"));
    reduceRunEvent(state, event("message.part.delta", { messageId: "msg_1", partId: "part_1", text: "!" }, "evt_2"));

    expect(state.messages.msg_1.parts.part_1.text).toBe("hello");
    expect(state.seenEventIds).toEqual(["evt_1", "evt_2"]);
  });

  it("updates todos, permissions and questions from platform RunEvent payloads", () => {
    const state = createRunEventState();

    reduceRunEvent(state, event("todo.updated", { sessionId: "ses_1", todos: [{ id: "todo_1", text: "Run tests", status: "pending" }] }));
    reduceRunEvent(state, event("permission.asked", { sessionId: "ses_1", requestId: "perm_1", type: "edit", title: "Edit file" }));
    reduceRunEvent(state, event("question.asked", { sessionId: "ses_1", requestId: "q_1", questions: [{ text: "Continue?", kind: "single" }] }));

    expect(state.todos.ses_1[0].text).toBe("Run tests");
    expect(state.permissions.ses_1[0].requestId).toBe("perm_1");
    expect(state.questions.ses_1[0].questions[0].text).toBe("Continue?");
  });
});
