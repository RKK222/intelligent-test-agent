import { describe, expect, it } from "vitest";
import { createInitialAgentChatRuntimeState, reduceAgentChatRuntime } from "../src/runtime-reducer";
import type { RunEvent } from "@test-agent/shared-types";

describe("agent-chat runtime reducer", () => {
  it("starts a new run without clearing the existing conversation", () => {
    const previous = {
      ...createInitialAgentChatRuntimeState([
        {
          id: "assistant-old",
          role: "assistant" as const,
          text: "上一轮完成",
          createdAt: "2026-07-02T09:00:00Z"
        }
      ]),
      status: "SUCCEEDED"
    };

    const next = reduceAgentChatRuntime(previous, { type: "run.requested" });

    expect(next.status).toBe("PENDING");
    expect(next.messages).toEqual(previous.messages);
  });

  it("stops a pending local run when the startup request fails", () => {
    const submitted = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "user.submitted",
      prompt: "这次创建会话会失败",
      createdAt: "2026-07-03T11:24:00Z"
    });
    const pending = reduceAgentChatRuntime(submitted, { type: "run.requested" });

    const failed = reduceAgentChatRuntime(pending, { type: "run.request.failed", message: "数据冲突" });

    expect(failed.status).toBe("FAILED");
    expect(failed.messages).toEqual(submitted.messages);
  });

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

  it("tracks subagent scopes and keeps child output out of the root assistant message", () => {
    const submitted = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "user.submitted",
      prompt: "分析前端结构",
      createdAt: "2026-07-03T00:00:00Z"
    });
    const withTask = reduceAgentChatRuntime(submitted, {
      type: "event",
      event: event("message.part.updated", {
        sessionID: "ses_root",
        rootSessionId: "ses_root",
        messageID: "msg_root",
        part: {
          id: "prt_task",
          messageID: "msg_root",
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
              sessionId: "ses_child",
              model: { providerID: "opencode", modelID: "deepseek-v4" }
            }
          }
        }
      })
    });

    expect((withTask as any).subagentsBySessionId.ses_child).toMatchObject({
      sessionId: "ses_child",
      parentSessionId: "ses_root",
      taskMessageId: "msg_root",
      taskPartId: "prt_task",
      taskCallId: "call_task",
      agentName: "Explore",
      title: "Explore frontend structure",
      status: "running",
      modelLabel: "opencode / deepseek-v4"
    });
    expect((withTask as any).subagentByTaskPartId.prt_task).toBe("ses_child");

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

    const rootAssistant = withChildText.messages.find(
      (message) => message.role === "assistant" && message.messageId === "msg_root"
    );
    const childAssistant = withChildText.messages.find(
      (message) => message.role === "assistant" && message.messageId === "msg_child"
    );

    expect(rootAssistant).toMatchObject({ role: "assistant", messageId: "msg_root", text: "" });
    expect(childAssistant).toMatchObject({
      role: "assistant",
      messageId: "msg_child",
      text: "子 Agent 输出",
      parts: [{ partId: "prt_child_text", type: "text", text: "子 Agent 输出" }]
    });
    expect((withChildText as any).messageScopesById.msg_child).toMatchObject({
      sessionId: "ses_child",
      rootSessionId: "ses_root",
      parentSessionId: "ses_root",
      isChildSession: true,
      taskMessageId: "msg_root",
      taskPartId: "prt_task",
      taskCallId: "call_task"
    });
  });

  it("keeps subagent task cards when message.part.updated uses opencode raw properties", () => {
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
          part: {
            type: "tool",
            tool: "task",
            callID: "call_task",
            state: {
              title: "Explore frontend structure",
              metadata: {
                parentSessionId: "ses_root",
                sessionId: "ses_child",
                model: { modelID: "deepseek-v4", providerID: "opencode" }
              },
              status: "running",
              input: {
                description: "Explore frontend structure",
                subagent_type: "explore"
              },
              time: { start: 1783075022016 }
            },
            id: "prt_task",
            sessionID: "ses_root",
            messageID: "msg_root"
          },
          id: "prt_task",
          messageID: "msg_root"
        }
      })
    });

    const rootAssistant = withTask.messages.find(
      (message) => message.role === "assistant" && message.messageId === "msg_root"
    );

    expect(rootAssistant).toMatchObject({
      role: "assistant",
      messageId: "msg_root",
      parts: [
        {
          partId: "prt_task",
          type: "tool",
          toolName: "task",
          callId: "call_task",
          status: "running"
        }
      ]
    });
    expect((withTask as any).subagentByTaskPartId.prt_task).toBe("ses_child");
    expect((withTask as any).subagentsBySessionId.ses_child).toMatchObject({
      sessionId: "ses_child",
      parentSessionId: "ses_root",
      taskMessageId: "msg_root",
      taskPartId: "prt_task",
      taskCallId: "call_task",
      agentName: "Explore",
      title: "Explore frontend structure",
      status: "running"
    });
    expect((withTask as any).messageScopesById.msg_root).toMatchObject({
      sessionId: "ses_root"
    });
    expect((withTask as any).messageScopesById.msg_root?.isChildSession).not.toBe(true);
  });

  it("updates subagent indexes from session child discovery events", () => {
    const next = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("session.child.discovered", {
        sessionId: "ses_child_2",
        parentSessionId: "ses_root",
        isChildSession: true,
        taskMessageId: "msg_root",
        taskPartId: "prt_task_2",
        taskCallId: "call_task_2",
        metadata: {
          title: "Explore backend structure",
          agentName: "Explore"
        }
      })
    });

    expect((next as any).subagentByTaskPartId.prt_task_2).toBe("ses_child_2");
    expect((next as any).subagentsBySessionId.ses_child_2).toMatchObject({
      sessionId: "ses_child_2",
      parentSessionId: "ses_root",
      taskMessageId: "msg_root",
      taskPartId: "prt_task_2",
      taskCallId: "call_task_2",
      agentName: "Explore",
      title: "Explore backend structure"
    });
  });

  it("turns a pending native task part into a bound subagent after child discovery", () => {
    const submitted = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "user.submitted",
      prompt: "分析项目结构",
      createdAt: "2026-07-03T00:00:00Z"
    });
    const withPendingTask = reduceAgentChatRuntime(submitted, {
      type: "event",
      event: event("message.part.updated", {
        sessionID: "ses_root",
        rootSessionId: "ses_root",
        messageID: "msg_root",
        part: {
          id: "prt_task",
          messageID: "msg_root",
          sessionID: "ses_root",
          type: "tool",
          tool: "task",
          callID: "call_task",
          state: { status: "pending", input: {}, raw: "" }
        }
      })
    });

    expect((withPendingTask as any).subagentByTaskPartId.prt_task).toBeUndefined();
    expect((withPendingTask.messages.at(-1) as any).parts[0]).toMatchObject({
      partId: "prt_task",
      type: "tool",
      toolName: "task",
      status: "pending"
    });

    const discovered = reduceAgentChatRuntime(withPendingTask, {
      type: "event",
      event: event("session.child.discovered", {
        sessionId: "ses_child",
        parentSessionId: "ses_root",
        isChildSession: true,
        taskMessageId: "msg_root",
        taskPartId: "prt_task",
        taskCallId: "call_task",
        metadata: {
          agent: "explore",
          title: "Explore project structure"
        }
      })
    });

    expect((discovered as any).subagentByTaskPartId.prt_task).toBe("ses_child");
    expect((discovered as any).subagentsBySessionId.ses_child).toMatchObject({
      sessionId: "ses_child",
      parentSessionId: "ses_root",
      taskMessageId: "msg_root",
      taskPartId: "prt_task",
      taskCallId: "call_task",
      agentName: "Explore",
      title: "Explore project structure",
      status: "running"
    });
  });

  it("keeps subagent indexes after task part removal", () => {
    const withSubagent = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("session.child.discovered", {
        sessionId: "ses_child",
        parentSessionId: "ses_root",
        taskMessageId: "msg_root",
        taskPartId: "prt_task",
        taskCallId: "call_task",
        metadata: { agent: "explore", title: "Explore project structure" }
      })
    });
    const withTask = reduceAgentChatRuntime(withSubagent, {
      type: "event",
      event: event("message.part.updated", {
        sessionID: "ses_root",
        messageID: "msg_root",
        part: { id: "prt_task", messageID: "msg_root", type: "tool", tool: "task", callID: "call_task", state: { status: "running" } }
      })
    });
    const removed = reduceAgentChatRuntime(withTask, {
      type: "event",
      event: event("message.part.removed", {
        messageID: "msg_root",
        partID: "prt_task"
      })
    });

    expect((removed.messages.at(-1) as any).parts).toEqual([]);
    expect((removed as any).subagentByTaskPartId.prt_task).toBe("ses_child");
    expect((removed as any).subagentsBySessionId.ses_child).toMatchObject({
      taskPartId: "prt_task",
      title: "Explore project structure"
    });
  });

  it("keeps live user message parts out of the assistant response", () => {
    const submitted = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "user.submitted",
      prompt: "请只回复：空行验证通过",
      createdAt: "2026-06-19T00:00:00Z"
    });
    const withRemoteUser = reduceAgentChatRuntime(submitted, {
      type: "event",
      event: event("message.updated", {
        message: { id: "msg_user_1", role: "user" }
      })
    });
    const withRemoteUserPart = reduceAgentChatRuntime(withRemoteUser, {
      type: "event",
      event: event("message.part.updated", {
        messageID: "msg_user_1",
        part: {
          id: "part_user_1",
          messageID: "msg_user_1",
          type: "text",
          text: "请只回复：空行验证通过"
        }
      })
    });
    const withAssistant = reduceAgentChatRuntime(withRemoteUserPart, {
      type: "event",
      event: event("message.updated", {
        message: { id: "msg_assistant_1", role: "assistant" }
      })
    });
    const completed = reduceAgentChatRuntime(withAssistant, {
      type: "event",
      event: event("message.part.updated", {
        messageID: "msg_assistant_1",
        part: {
          id: "part_assistant_1",
          messageID: "msg_assistant_1",
          type: "text",
          text: "空行验证通过"
        }
      })
    });

    expect(completed.messages).toMatchObject([
      { role: "user", messageId: "msg_user_1", text: "请只回复：空行验证通过" },
      { role: "assistant", messageId: "msg_assistant_1", text: "空行验证通过" }
    ]);
    expect(completed.messages).toHaveLength(2);
  });

  it("keeps expanded slash command user parts out of the assistant response", () => {
    const submitted = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "user.submitted",
      prompt: "/generate-cases-path 对车贷的开发文档，生成路径图",
      createdAt: "2026-07-02T02:16:00Z"
    });
    const withRemoteUser = reduceAgentChatRuntime(submitted, {
      type: "event",
      event: event("message.updated", {
        message: { id: "msg_user_command", role: "user" }
      })
    });
    const withExpandedDelta = reduceAgentChatRuntime(withRemoteUser, {
      type: "event",
      event: event("message.part.delta", {
        messageID: "msg_user_command",
        partID: "part_user_command",
        partType: "text",
        delta: "# 路径法案例生成\n\n## 步骤"
      })
    });
    const completed = reduceAgentChatRuntime(withExpandedDelta, {
      type: "event",
      event: event("message.part.updated", {
        messageID: "msg_user_command",
        part: {
          id: "part_user_command",
          messageID: "msg_user_command",
          type: "text",
          text: "# 路径法案例生成\n\n## 步骤\n\n输出路径图。"
        }
      })
    });

    expect(completed.messages).toMatchObject([
      {
        role: "user",
        messageId: "msg_user_command",
        text: "/generate-cases-path 对车贷的开发文档，生成路径图"
      }
    ]);
    expect(completed.messages).toHaveLength(1);
  });

  it("binds expanded slash command user parts when the user message snapshot is delayed", () => {
    const submitted = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "user.submitted",
      prompt: "/generate-cases-path 对车贷的开发文档，生成路径图",
      createdAt: "2026-07-02T05:44:30Z"
    });
    const completed = reduceAgentChatRuntime(submitted, {
      type: "event",
      event: event("message.part.updated", {
        sessionID: "ses_0dea4821cffepwpXaTFMUHFwsH",
        rawType: "message.part.updated",
        part: {
          type: "text",
          text: "# 路径法案例生成\n\n## 适用\n\n页面流、状态流、审批流、跨接口链路。\n\n对车贷的开发文档，生成路径图",
          messageID: "msg_f215b8020001M9BKwEZC6zAH3e",
          sessionID: "ses_0dea4821cffepwpXaTFMUHFwsH",
          id: "prt_f215b8027001sJNXB8Knzu5XJx"
        }
      })
    });

    expect(completed.messages).toMatchObject([
      {
        role: "user",
        messageId: "msg_f215b8020001M9BKwEZC6zAH3e",
        text: "/generate-cases-path 对车贷的开发文档，生成路径图"
      }
    ]);
    expect(completed.messages).toHaveLength(1);
    expect(completed.messages.some((message) => message.role === "assistant" && message.text.includes("# 路径法案例生成"))).toBe(false);
  });

  it("merges a delayed remote user snapshot back into the optimistic user message", () => {
    const submitted = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "user.submitted",
      prompt: "你是谁",
      createdAt: "2026-06-29T04:57:00Z"
    });
    const withAssistant = reduceAgentChatRuntime(submitted, {
      type: "event",
      event: event("message.updated", {
        message: { id: "msg_assistant_1", role: "assistant" }
      })
    });
    const withAssistantPart = reduceAgentChatRuntime(withAssistant, {
      type: "event",
      event: event("message.part.updated", {
        messageID: "msg_assistant_1",
        part: {
          id: "part_assistant_1",
          messageID: "msg_assistant_1",
          type: "text",
          text: "我是 opencode"
        }
      })
    });
    const withDelayedRemoteUser = reduceAgentChatRuntime(withAssistantPart, {
      type: "event",
      event: event("message.updated", {
        message: { id: "msg_user_1", role: "user" }
      })
    });
    const completed = reduceAgentChatRuntime(withDelayedRemoteUser, {
      type: "event",
      event: event("message.part.updated", {
        messageID: "msg_user_1",
        part: {
          id: "part_user_1",
          messageID: "msg_user_1",
          type: "text",
          text: "你是谁"
        }
      })
    });

    expect(completed.messages).toMatchObject([
      { role: "user", messageId: "msg_user_1", text: "你是谁" },
      { role: "assistant", messageId: "msg_assistant_1", text: "我是 opencode" }
    ]);
    expect(completed.messages).toHaveLength(2);
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

  it("maps native todo.updated payload.todos and creates unique ids when opencode omits ids", () => {
    const state = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("todo.updated", {
        sessionID: "ses_123",
        todos: [
          { content: "分析需求", status: "pending", priority: "high" },
          { content: "编写实现", status: "in_progress", priority: "medium" },
          { content: "补充测试", status: "completed", priority: "low" },
          { content: "废弃旧路径", status: "cancelled", priority: "low" }
        ]
      })
    });

    expect(state.todos.map((item) => item.text)).toEqual(["分析需求", "编写实现", "补充测试", "废弃旧路径"]);
    expect(state.todos.map((item) => item.status)).toEqual(["pending", "in_progress", "completed", "cancelled"]);
    expect(new Set(state.todos.map((item) => item.id)).size).toBe(4);
    expect(state.todos.every((item) => item.id !== "todo" && item.id !== "unknown")).toBe(true);
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

  it("collects patch filesMap/fileStats into metadata for the part block", () => {
    const state = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("message.part.updated", {
        messageID: "msg_1",
        part: {
          id: "p_patch",
          messageID: "msg_1",
          type: "patch",
          hash: "abcdef1234",
          files: ["a.ts", "b.ts"],
          filesMap: { "a.ts": "--- a.ts\n+++ a.ts\n@@\n-foo\n+bar" },
          fileStats: { "a.ts": { additions: 1, deletions: 1 } }
        }
      })
    });

    const message = state.messages[state.messages.length - 1];
    const part = (message as { parts?: { type: string; metadata?: { filesMap?: Record<string, string>; fileStats?: Record<string, { additions?: number; deletions?: number }> } }[] }).parts?.[0];
    expect(part?.metadata?.filesMap?.["a.ts"]).toContain("+bar");
    expect(part?.metadata?.fileStats?.["a.ts"]).toEqual({ additions: 1, deletions: 1 });
  });

  it("falls back to a top-level metadata object when patch lacks inline maps", () => {
    const state = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("message.part.updated", {
        messageID: "msg_1",
        part: {
          id: "p_patch2",
          messageID: "msg_1",
          type: "patch",
          hash: "1234",
          files: ["a.ts"],
          metadata: { filesMap: { "a.ts": "@@\n-x\n+y" } }
        }
      })
    });

    const message = state.messages[state.messages.length - 1];
    const part = (message as { parts?: { type: string; metadata?: { filesMap?: Record<string, string> } }[] }).parts?.[0];
    expect(part?.metadata?.filesMap?.["a.ts"]).toContain("+y");
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
