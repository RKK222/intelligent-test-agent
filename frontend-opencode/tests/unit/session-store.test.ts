import { createPinia, setActivePinia } from "pinia";
import { usePlatformStore } from "@/stores/platform";
import { useRunEventStore } from "@/stores/runEvents";
import { useSessionStore } from "@/stores/session";

describe("session store actions", () => {
  it("keeps RunEvent message parts on the timeline projection", () => {
    setActivePinia(createPinia());
    const session = useSessionStore();
    const runEvents = useRunEventStore();
    session.activeSession = {
      sessionId: "ses_1",
      workspaceId: "wrk_1",
      title: "Demo",
      status: "RUNNING",
      createdAt: "2026-06-20T00:00:00Z",
      updatedAt: "2026-06-20T00:00:00Z"
    };

    runEvents.apply({
      eventId: "evt_tool",
      runId: "run_1",
      seq: 1,
      type: "message.part.updated",
      traceId: "trace_1",
      occurredAt: "2026-06-20T00:00:01Z",
      payload: {
        messageId: "msg_1",
        part: {
          partId: "tool_1",
          type: "tool",
          toolName: "grep",
          status: "completed",
          output: "vite.config.ts"
        }
      }
    });

    expect(session.timeline[0]).toMatchObject({
      messageId: "msg_1",
      parts: [{ partId: "tool_1", type: "tool", toolName: "grep", output: "vite.config.ts" }]
    });
  });

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

  it("passes selected runtime agent, model, and variant to startRun", async () => {
    setActivePinia(createPinia());
    const platform = usePlatformStore();
    const runEvents = useRunEventStore();
    const session = useSessionStore();
    const calls: Array<[string, ...unknown[]]> = [];
    platform.baseUrl = "http://127.0.0.1:8080";
    runEvents.subscribe = vi.fn();

    Object.defineProperty(platform, "api", {
      value: {
        startRun: async (...args: unknown[]) => {
          calls.push(["startRun", ...args]);
          return { runId: "run_2", sessionId: "ses_1", workspaceId: "wrk_1", status: "RUNNING", createdAt: "", updatedAt: "" };
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

    await session.sendPrompt({ text: "Use selected runtime", agent: "build", model: "anthropic/claude-sonnet-4", variant: "high" });

    expect(calls).toEqual([
      [
        "startRun",
        {
          sessionId: "ses_1",
          parts: [{ type: "text", text: "Use selected runtime" }],
          prompt: "Use selected runtime",
          agent: "build",
          model: "anthropic/claude-sonnet-4",
          variant: "high"
        }
      ]
    ]);
  });

  it("routes shell mode and slash commands through opencode runtime session APIs", async () => {
    setActivePinia(createPinia());
    const platform = usePlatformStore();
    const runEvents = useRunEventStore();
    const session = useSessionStore();
    const calls: Array<[string, ...unknown[]]> = [];
    runEvents.subscribe = vi.fn();

    Object.defineProperty(platform, "api", {
      value: {
        startRun: async (...args: unknown[]) => calls.push(["startRun", ...args]),
        runSessionShell: async (...args: unknown[]) => calls.push(["shell", ...args]),
        runSessionCommand: async (...args: unknown[]) => calls.push(["command", ...args])
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

    await session.sendPrompt({ text: "pnpm test", shellMode: true, agent: "build", model: "anthropic/claude-sonnet-4", variant: "low" });
    await session.sendPrompt({
      text: "/review staged changes",
      model: "anthropic/claude-sonnet-4",
      files: [{ path: "src/App.vue", name: "App.vue" }]
    });

    expect(calls).toEqual([
      [
        "shell",
        "ses_1",
        {
          command: "pnpm test",
          agent: "build",
          model: "anthropic/claude-sonnet-4",
          variant: "low"
        }
      ],
      [
        "command",
        "ses_1",
        {
          command: "review",
          arguments: "staged changes",
          model: "anthropic/claude-sonnet-4",
          parts: [{ type: "file", path: "src/App.vue", name: "App.vue" }]
        }
      ]
    ]);
    expect(runEvents.subscribe).not.toHaveBeenCalled();
  });

  it("routes toolbar fork, revert, and compact actions through backend-api", async () => {
    setActivePinia(createPinia());
    const platform = usePlatformStore();
    const session = useSessionStore();
    const calls: Array<[string, ...unknown[]]> = [];

    Object.defineProperty(platform, "api", {
      value: {
        forkSession: async (...args: unknown[]) => {
          calls.push(["fork", ...args]);
          return {
            id: "ses_child",
            title: "Forked from message",
            time: { created: "2026-06-20T00:03:00Z", updated: "2026-06-20T00:03:00Z" }
          };
        },
        revertSession: async (...args: unknown[]) => calls.push(["revert", ...args]),
        compactSession: async (...args: unknown[]) => calls.push(["compact", ...args])
      }
    });

    session.activeSession = {
      sessionId: "ses_1",
      workspaceId: "wrk_1",
      title: "Demo",
      status: "RUNNING",
      createdAt: "2026-06-20T00:00:00Z",
      updatedAt: "2026-06-20T00:00:00Z",
      model: { id: "claude-sonnet-4", providerId: "anthropic" }
    };
    session.messages = [
      {
        messageId: "msg_1",
        sessionId: "ses_1",
        role: "USER",
        content: "First prompt",
        createdAt: "2026-06-20T00:01:00Z"
      },
      {
        messageId: "msg_2",
        sessionId: "ses_1",
        role: "ASSISTANT",
        content: "First answer",
        createdAt: "2026-06-20T00:01:30Z"
      },
      {
        messageId: "msg_3",
        sessionId: "ses_1",
        role: "USER",
        content: "Second prompt",
        createdAt: "2026-06-20T00:02:00Z"
      }
    ];

    const forked = await session.forkFromMessage("msg_1");
    await session.revertLatestUserMessage();
    await session.compactSession();

    expect(forked?.sessionId).toBe("ses_child");
    expect(session.activeSession?.sessionId).toBe("ses_child");
    expect(session.revertItems).toEqual([{ id: "msg_3", text: "Restore Second prompt" }]);
    expect(calls).toEqual([
      ["fork", "ses_1", { messageID: "msg_1" }],
      ["revert", "ses_child", { messageID: "msg_3" }],
      ["compact", "ses_child", { providerID: "anthropic", modelID: "claude-sonnet-4" }]
    ]);
  });
});
