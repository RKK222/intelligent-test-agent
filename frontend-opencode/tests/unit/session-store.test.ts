import { createPinia, setActivePinia } from "pinia";
import { usePlatformStore } from "@/stores/platform";
import { useRunEventStore } from "@/stores/runEvents";
import { useSessionStore } from "@/stores/session";

describe("session store actions", () => {
  it("proxies dock decisions through backend-api and updates local queues", async () => {
    setActivePinia(createPinia());
    const platform = usePlatformStore();
    const runEvents = useRunEventStore();
    const session = useSessionStore();
    const calls: Array<[string, ...unknown[]]> = [];
    platform.baseUrl = "http://127.0.0.1:8080";
    runEvents.subscribe = vi.fn();

    Object.defineProperty(platform, "api", {
      value: {
        replySessionPermission: async (...args: unknown[]) => calls.push(["permission", ...args]),
        replySessionQuestion: async (...args: unknown[]) => calls.push(["question", ...args]),
        unrevertSession: async (...args: unknown[]) => calls.push(["unrevert", ...args]),
        startRun: async (...args: unknown[]) => {
          calls.push(["startRun", ...args]);
          return { runId: "run_1", sessionId: "ses_1", workspaceId: "wrk_1", status: "RUNNING", createdAt: "", updatedAt: "" };
        }
      }
    });

    session.activeSession = {
      sessionId: "ses_1",
      workspaceId: "wrk_1",
      title: "Demo",
      status: "RUNNING",
      createdAt: "",
      updatedAt: ""
    };
    session.permissions = [{ requestId: "perm_1", sessionId: "ses_1", type: "edit", createdAt: "" }];
    session.questions = [{ requestId: "q_1", sessionId: "ses_1", questions: [], createdAt: "" }];
    session.followups = [{ id: "follow_1", text: "Run tests" }];
    session.revertItems = [{ id: "msg_1", text: "Restore msg_1" }];

    await session.replyPermission("perm_1", "always");
    await session.replyQuestion("q_1", [["yes"]]);
    await session.sendFollowup("follow_1");
    await session.restoreRevert("msg_1");

    expect(calls).toEqual([
      ["permission", "ses_1", "perm_1", { decision: "always" }],
      ["question", "ses_1", "q_1", { answers: [["yes"]] }],
      ["startRun", { sessionId: "ses_1", parts: [{ type: "text", text: "Run tests" }], prompt: "Run tests" }],
      ["unrevert", "ses_1", { messageId: "msg_1" }]
    ]);
    expect(session.permissions).toHaveLength(0);
    expect(session.questions).toHaveLength(0);
    expect(session.followups).toHaveLength(0);
    expect(session.revertItems).toHaveLength(0);
  });
});
