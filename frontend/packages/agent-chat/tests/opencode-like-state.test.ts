import { describe, expect, it } from "vitest";
import type { AgentMessage, MessagePart, RunEvent } from "@test-agent/shared-types";
import { createOpencodeLikeState } from "../src/opencode-like/state/adapter";
import { createTimelineRows } from "../src/opencode-like/state/projection";
import { readPartText } from "../src/opencode-like/state/part-text";
import { formatModelLabel } from "../src/opencode-like/state/model-catalog";
import { createInitialAgentChatRuntimeState, reduceAgentChatRuntime } from "../src/runtime-reducer";

describe("opencode-like conversation state", () => {
  it("projects user messages, context tool groups, assistant parts and diff rows", () => {
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

  it("keeps completed process groups idle when the next turn starts running", () => {
    const messages: AgentMessage[] = [
      userMessage("msg_user_1", "分析第一轮问题"),
      assistantMessage("msg_assistant_1", [
        toolPart("part_read", "read", { filePath: "README.md" }),
        { partId: "part_reasoning", type: "reasoning", text: "第一轮分析完成", status: "completed" },
        toolPart("part_bash_1", "bash", { command: "pwd" }),
        toolPart("part_bash_2", "bash", { command: "git status --short" })
      ]),
      userMessage("msg_user_2", "继续下一轮")
    ];

    const rows = createTimelineRows(createOpencodeLikeState({ messages, running: true }));
    const previousTurnGroups = rows.filter((row) =>
      (row.type === "context-tool-group" || row.type === "reasoning-group" || row.type === "tool-group") &&
      row.userMessageId === "msg_user_1"
    );

    expect(previousTurnGroups).toHaveLength(3);
    expect(previousTurnGroups).toEqual([
      expect.objectContaining({ type: "context-tool-group", busy: false }),
      expect.objectContaining({ type: "reasoning-group", busy: false }),
      expect.objectContaining({ type: "tool-group", busy: false })
    ]);
    expect(rows.at(-1)).toMatchObject({ type: "user-message", userMessageId: "msg_user_2" });
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

  it("projects runtime retry status for a running turn", () => {
    const state = createOpencodeLikeState({
      messages: [userMessage("msg_user_1", "完成车贷的接口案例设计")],
      running: true,
      runtimeStatus: {
        type: "retry",
        retryKey: "evt_retry_1",
        attempt: 1,
        maxAttempts: 3,
        message: "Free usage exceeded, subscribe to Go",
        retryAfterSeconds: 60,
        action: { label: "subscribe", link: "https://opencode.ai/go" }
      }
    });

    const rows = createTimelineRows(state);

    expect(rows.map((row) => row.type)).toEqual(["user-message", "retry"]);
    expect(rows.at(-1)).toMatchObject({
      type: "retry",
      attempt: 1,
      maxAttempts: 3,
      message: "Free usage exceeded, subscribe to Go",
      retryAfterSeconds: 60,
      action: { label: "subscribe", link: "https://opencode.ai/go" }
    });
  });

  it("keeps retry as the last row after tool activity", () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "继续执行"),
        assistantMessage("msg_assistant_1", [toolPart("part_read", "read", { filePath: "README.md" })])
      ],
      running: true,
      runtimeStatus: {
        type: "retry",
        retryKey: "evt_retry_1",
        attempt: 1,
        maxAttempts: 3,
        retryAfterSeconds: 60
      }
    });
    const rows = createTimelineRows(state);

    expect(rows.map((row) => row.type)).toEqual(["user-message", "context-tool-group", "retry"]);
    expect(rows.at(-1)).toMatchObject({ type: "retry", retryAfterSeconds: 60 });
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

  it("does not add a synthetic working row when process parts have no text output", () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "读取项目结构"),
        assistantMessage("msg_assistant_1", [
          toolPart("part_read_1", "read", { filePath: "README.md" }),
          toolPart("part_read_2", "read", { filePath: "frontend/README.md" })
        ])
      ],
      running: true
    });

    const rows = createTimelineRows(state);

    expect(rows.map((row) => row.type)).toEqual(["user-message", "context-tool-group"]);
  });

  it("keeps historical process rows and running text without a synthetic working row", () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "读取项目结构"),
        assistantMessage("msg_assistant_1", [toolPart("part_read_1", "read", { filePath: "README.md" })]),
        userMessage("msg_user_2", "总结结果"),
        assistantMessage("msg_assistant_2", [textPart("part_running_text", "正在整理总结", "running")])
      ],
      running: true
    });

    const rows = createTimelineRows(state);

    expect(rows.map((row) => row.type)).toEqual([
      "user-message",
      "context-tool-group",
      "turn-gap",
      "user-message",
      "assistant-part"
    ]);
    expect(rows.some((row) => row.type === "assistant-part" && row.messageId === "msg_assistant_2")).toBe(true);
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

  it("projects repeated question tool calls as independent timeline rows", () => {
    const state = createOpencodeLikeState({
      messages: [
        userMessage("msg_user_1", "确认发布参数"),
        assistantMessage("msg_question_1", [
          toolPart("prt_question_env", "question", { questions: [{ question: "选择环境" }] })
        ]),
        assistantMessage("msg_question_2", [
          toolPart("prt_question_scope", "question", { questions: [{ question: "选择范围" }] })
        ])
      ]
    });

    const rows = createTimelineRows(state);

    expect(rows.some((row) => row.type === "tool-group")).toBe(false);
    expect(rows.filter((row) => row.type === "assistant-part").map((row) => (row as any).partId)).toEqual([
      "prt_question_env",
      "prt_question_scope"
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

  it("does not attach a stale synthetic subagent entry to later turns when the original task message is gone", () => {
    const messages: AgentMessage[] = [
      userMessage("msg_user_1", "分析项目结构"),
      userMessage("msg_user_2", "继续生成接口文档"),
      assistantMessage("msg_assistant_2", [toolPart("prt_read", "read", { filePath: "docs/api/http-api.md" })])
    ];

    const state = createOpencodeLikeState({
      messages,
      subagentsBySessionId: {
        ses_child: {
          sessionId: "ses_child",
          parentSessionId: "ses_root",
          taskMessageId: "msg_missing_task",
          taskPartId: "prt_task_old",
          taskCallId: "call_task",
          agentName: "Explore",
          title: "Explore backend structure",
          status: "completed",
          updatedAt: "2026-07-03T00:00:00Z"
        }
      },
      subagentByTaskPartId: { prt_task_old: "ses_child" }
    } as any);
    const rows = createTimelineRows(state);

    expect(state.partsByMessageId.msg_assistant_2.map((part) => part.partId)).toEqual(["prt_read"]);
    expect(rows.some((row) => row.type === "assistant-part" && row.partId === "prt_task_old")).toBe(false);
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
