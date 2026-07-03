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

  it("filters child scoped messages out of the root view and shows them in the child view", () => {
    const messages: AgentMessage[] = [
      userMessage("msg_user_root", "分析前端结构"),
      assistantMessage("msg_root", [
        toolPart("prt_task_frontend", "task", {
          description: "Explore frontend structure",
          subagent_type: "explore"
        })
      ]),
      userMessage("msg_child_user", "Explore frontend structure"),
      assistantMessage("msg_child_answer", [textPart("prt_child_answer", "子 Agent 已读取前端目录。")])
    ];
    const messageScopesById = {
      msg_user_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false },
      msg_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false },
      msg_child_user: {
        sessionId: "ses_child",
        rootSessionId: "ses_root",
        parentSessionId: "ses_root",
        isChildSession: true,
        taskPartId: "prt_task_frontend"
      },
      msg_child_answer: {
        sessionId: "ses_child",
        rootSessionId: "ses_root",
        parentSessionId: "ses_root",
        isChildSession: true,
        taskPartId: "prt_task_frontend"
      }
    };
    const subagentsBySessionId = {
      ses_child: {
        sessionId: "ses_child",
        parentSessionId: "ses_root",
        taskMessageId: "msg_root",
        taskPartId: "prt_task_frontend",
        taskCallId: "call_task",
        agentName: "Explore",
        title: "Explore frontend structure",
        status: "running",
        updatedAt: "2026-07-03T00:00:00Z"
      }
    };

    const rootState = createOpencodeLikeState({
      messages,
      messageScopesById,
      subagentsBySessionId,
      subagentByTaskPartId: { prt_task_frontend: "ses_child" }
    } as any);
    const childState = createOpencodeLikeState({
      messages,
      messageScopesById,
      subagentsBySessionId,
      subagentByTaskPartId: { prt_task_frontend: "ses_child" },
      activeSubagentSessionId: "ses_child"
    } as any);

    expect(rootState.userMessages.map((message) => message.messageId)).toEqual(["msg_user_root"]);
    expect(rootState.partsByMessageId.msg_child_answer).toBeUndefined();
    expect(createTimelineRows(rootState).some((row) => row.type === "assistant-part" && row.messageId === "msg_child_answer")).toBe(false);
    expect(rootState.partsByMessageId.msg_root.map((part) => part.partId)).toEqual(["prt_task_frontend"]);

    expect(childState.userMessages.map((message) => message.messageId)).toEqual(["msg_child_user"]);
    expect(childState.partsByMessageId.msg_child_answer.map((part) => part.partId)).toEqual(["prt_child_answer"]);
    expect(createTimelineRows(childState).map((row) => row.type)).toEqual(["user-message", "assistant-part"]);
  });

  it("does not project empty running text placeholders while preserving root and child scoped timelines", () => {
    const rootReadParts = Array.from({ length: 88 }, (_, index) =>
      toolPart(`prt_read_${index}`, "read", { filePath: `src/file-${index}.ts` })
    );
    const messages: AgentMessage[] = [
      userMessage("msg_user_root", "分析前端结构"),
      assistantMessage("msg_root", [
        ...rootReadParts,
        textPart("prt_empty_root_1", "", "running"),
        textPart("prt_empty_root_2", "   ", "running"),
        toolPart("prt_task_frontend", "task", {
          description: "Explore frontend structure",
          subagent_type: "explore"
        })
      ]),
      userMessage("msg_child_user", "Explore frontend structure"),
      assistantMessage("msg_child_answer", [
        toolPart("prt_child_read", "read", { filePath: "frontend/README.md" }),
        textPart("prt_empty_child", "", "running"),
        textPart("prt_child_answer", "子 Agent 已读取前端目录。")
      ])
    ];
    const messageScopesById = {
      msg_user_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false },
      msg_root: { sessionId: "ses_root", rootSessionId: "ses_root", isChildSession: false },
      msg_child_user: {
        sessionId: "ses_child",
        rootSessionId: "ses_root",
        parentSessionId: "ses_root",
        isChildSession: true,
        taskPartId: "prt_task_frontend"
      },
      msg_child_answer: {
        sessionId: "ses_child",
        rootSessionId: "ses_root",
        parentSessionId: "ses_root",
        isChildSession: true,
        taskPartId: "prt_task_frontend"
      }
    };
    const subagentsBySessionId = {
      ses_child: {
        sessionId: "ses_child",
        parentSessionId: "ses_root",
        taskMessageId: "msg_root",
        taskPartId: "prt_task_frontend",
        taskCallId: "call_task",
        agentName: "Explore",
        title: "Explore frontend structure",
        status: "running",
        updatedAt: "2026-07-03T00:00:00Z"
      }
    };

    const rootState = createOpencodeLikeState({
      messages,
      messageScopesById,
      subagentsBySessionId,
      subagentByTaskPartId: { prt_task_frontend: "ses_child" }
    } as any);
    const childState = createOpencodeLikeState({
      messages,
      messageScopesById,
      subagentsBySessionId,
      subagentByTaskPartId: { prt_task_frontend: "ses_child" },
      activeSubagentSessionId: "ses_child"
    } as any);
    const rootRows = createTimelineRows(rootState);
    const childRows = createTimelineRows(childState);
    const rootAssistantPartIds = rootRows
      .filter((row) => row.type === "assistant-part")
      .map((row) => (row as { partId: string }).partId);
    const childAssistantPartIds = childRows
      .filter((row) => row.type === "assistant-part")
      .map((row) => (row as { partId: string }).partId);
    const rootContextRow = rootRows.find((row) => row.type === "context-tool-group") as { refs: unknown[] } | undefined;

    expect(rootContextRow?.refs).toHaveLength(88);
    expect(rootAssistantPartIds).toContain("prt_task_frontend");
    expect(rootAssistantPartIds).not.toContain("prt_empty_root_1");
    expect(rootAssistantPartIds).not.toContain("prt_empty_root_2");
    expect(rootState.partsByMessageId.msg_child_answer).toBeUndefined();

    expect(childState.userMessages.map((message) => message.messageId)).toEqual(["msg_child_user"]);
    expect(childAssistantPartIds).toContain("prt_child_answer");
    expect(childAssistantPartIds).not.toContain("prt_empty_child");
    expect(childRows.some((row) => row.type === "context-tool-group")).toBe(true);
  });

  it("projects task tool parts as independent rows instead of a folded tool group", () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "分析前后端结构"),
        assistantMessage("msg_assistant_1", [
          toolPart("prt_task_backend", "task", { description: "Explore backend structure", subagent_type: "explore" }),
          toolPart("prt_task_frontend", "task", { description: "Explore frontend structure", subagent_type: "explore" })
        ])
      ],
      subagentsBySessionId: {
        ses_backend: {
          sessionId: "ses_backend",
          taskMessageId: "msg_assistant_1",
          taskPartId: "prt_task_backend",
          agentName: "Explore",
          title: "Explore backend structure",
          status: "running",
          updatedAt: "2026-07-03T00:00:00Z"
        },
        ses_frontend: {
          sessionId: "ses_frontend",
          taskMessageId: "msg_assistant_1",
          taskPartId: "prt_task_frontend",
          agentName: "Explore",
          title: "Explore frontend structure",
          status: "running",
          updatedAt: "2026-07-03T00:00:00Z"
        }
      },
      subagentByTaskPartId: {
        prt_task_backend: "ses_backend",
        prt_task_frontend: "ses_frontend"
      }
    } as any);

    const rows = createTimelineRows(state);

    expect(rows.some((row) => row.type === "tool-group")).toBe(false);
    expect(rows.filter((row) => row.type === "assistant-part").map((row) => (row as any).partId)).toEqual([
      "prt_task_backend",
      "prt_task_frontend"
    ]);
  });

  it("keeps raw-properties task part visible in the root projection after child output arrives", () => {
    const submitted = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "user.submitted",
      prompt: "分析前端结构",
      createdAt: "2026-07-03T00:00:00Z"
    });
    const withTask = reduceAgentChatRuntime(submitted, {
      type: "event",
      event: event("message.part.updated", {
        properties: {
          sessionID: "ses_root",
          rootSessionId: "ses_root",
          part: {
            type: "tool",
            tool: "task",
            callID: "call_task",
            state: {
              title: "Explore frontend structure",
              status: "running",
              input: {
                description: "Explore frontend structure",
                subagent_type: "explore"
              },
              metadata: {
                parentSessionId: "ses_root",
                sessionId: "ses_child"
              }
            },
            id: "prt_task",
            messageID: "msg_root",
            sessionID: "ses_root"
          },
          messageID: "msg_root"
        }
      })
    });
    const withChildText = reduceAgentChatRuntime(withTask, {
      type: "event",
      event: event("message.part.delta", {
        sessionID: "ses_child",
        sessionId: "ses_child",
        rootSessionId: "ses_root",
        parentSessionId: "ses_root",
        isChildSession: true,
        taskMessageId: "msg_root",
        taskPartId: "prt_task",
        taskCallId: "call_task",
        messageID: "msg_child",
        partID: "prt_child_text",
        partType: "text",
        delta: "子 Agent 输出"
      })
    });

    const rootState = createOpencodeLikeState(withChildText as any);
    const rows = createTimelineRows(rootState);

    expect(rootState.partsByMessageId.msg_root.map((part) => part.partId)).toEqual(["prt_task"]);
    expect(rootState.partsByMessageId.msg_child).toBeUndefined();
    expect(rows.some((row) => row.type === "assistant-part" && row.messageId === "msg_root" && row.partId === "prt_task")).toBe(true);
    expect(rows.some((row) => row.type === "assistant-part" && row.messageId === "msg_child")).toBe(false);
  });

  it("adds a stable synthetic subagent entry when the original task part is missing", () => {
    const messages: AgentMessage[] = [
      userMessage("msg_user_1", "分析项目结构"),
      assistantMessage("msg_root", [])
    ];

    const state = createOpencodeLikeState({
      messages,
      subagentsBySessionId: {
        ses_child: {
          sessionId: "ses_child",
          parentSessionId: "ses_root",
          taskMessageId: "msg_root",
          taskPartId: "prt_task",
          taskCallId: "call_task",
          agentName: "Explore",
          title: "Explore project structure",
          status: "running",
          updatedAt: "2026-07-03T00:00:00Z"
        }
      },
      subagentByTaskPartId: { prt_task: "ses_child" }
    } as any);
    const childState = createOpencodeLikeState({
      messages,
      subagentsBySessionId: state.subagentsBySessionId,
      subagentByTaskPartId: state.subagentByTaskPartId,
      activeSubagentSessionId: "ses_child"
    } as any);

    expect(state.partsByMessageId.msg_root.map((part) => part.partId)).toEqual(["prt_task"]);
    expect(createTimelineRows(state)).toContainEqual(expect.objectContaining({
      type: "assistant-part",
      messageId: "msg_root",
      partId: "prt_task"
    }));
    expect(childState.partsByMessageId.msg_root).toBeUndefined();
  });
});

function userMessage(id: string, text: string): Extract<AgentMessage, { role: "user" }> {
  return { id, messageId: id, role: "user", text, createdAt: "2026-07-03T00:00:00Z" };
}

function assistantMessage(id: string, parts: MessagePart[]): Extract<AgentMessage, { role: "assistant" }> {
  return { id, messageId: id, role: "assistant", text: "", parts, createdAt: "2026-07-03T00:00:01Z" };
}

function textPart(partId: string, text: string, status = "completed"): Extract<MessagePart, { type: "text" }> {
  return { partId, type: "text", text, status };
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
