import { describe, expect, it } from "vitest";
import { createInitialAgentChatRuntimeState, reduceAgentChatRuntime } from "../src/runtime-reducer";
import type { RunEvent } from "@test-agent/shared-types";

describe("agent-chat runtime reducer", () => {
  it("starts a new run without clearing the existing conversation but clears stale todos", () => {
    const previous = {
      ...createInitialAgentChatRuntimeState([
        {
          id: "assistant-old",
          role: "assistant" as const,
          text: "上一轮完成",
          createdAt: "2026-07-02T09:00:00Z"
        }
      ]),
      status: "SUCCEEDED",
      todos: [{ id: "todo_old", text: "上一轮任务", status: "completed" }]
    };

    const next = reduceAgentChatRuntime(previous, { type: "run.requested" });

    expect(next.status).toBe("PENDING");
    expect(next.messages).toEqual(previous.messages);
    expect(next.todos).toEqual([]);

    const updated = reduceAgentChatRuntime(next, {
      type: "event",
      event: event("todo.updated", {
        todos: [{ id: "todo_new", content: "本轮任务", status: "in_progress" }]
      })
    });

    expect(updated.todos).toHaveLength(1);
    expect(updated.todos[0]).toMatchObject({ id: "todo_new", text: "本轮任务", status: "in_progress" });
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

  it("adds a visible diagnostic card when the SSE stream reports an error", () => {
    const pending = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), { type: "run.requested" });

    const next = reduceAgentChatRuntime(pending, {
      type: "run.stream.error",
      runId: "run_123",
      message: "浏览器事件流连接异常",
      occurredAt: "2026-07-05T10:00:00Z"
    });

    expect(next.status).toBe("PENDING");
    expect(next.messages).toMatchObject([
      {
        role: "card",
        cardType: "event",
        title: "RunEvent SSE 连接异常",
        payload: {
          type: "run.stream.error",
          error: { name: "EventSourceError", message: "浏览器事件流连接异常" }
        }
      }
    ]);
  });

  it("keeps opencode session retry status visible with the upstream action", () => {
    const pending = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), { type: "run.requested" });

    const next = reduceAgentChatRuntime(pending, {
      type: "event",
      event: event("session.status", {
        status: {
          type: "retry",
          attempt: 1,
          message: "Free usage exceeded, subscribe to Go",
          action: {
            reason: "free_tier_limit",
            provider: "opencode",
            title: "Free limit reached",
            message: "Subscribe to OpenCode Go for reliable access to the best open-source models, starting at $5/month.",
            label: "subscribe",
            link: "https://opencode.ai/go"
          },
          next: 1783296000351
        }
      })
    });

    expect(next.status).toBe("RETRY");
    expect(next.runtimeStatus).toMatchObject({
      type: "retry",
      retryKey: "evt_session.status",
      attempt: 1,
      maxAttempts: 3,
      retryAfterSeconds: 60,
      message: "Free usage exceeded, subscribe to Go",
      action: {
        reason: "free_tier_limit",
        provider: "opencode",
        label: "subscribe",
        link: "https://opencode.ai/go"
      }
    });
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

  it("marks live opencode message ids as remote ids instead of platform feedback ids", () => {
    const remoteMessageId = "msg_f2d478d96001861rLCyXjYqf75";
    const state = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("message.updated", { message: { id: remoteMessageId, role: "assistant", content: "实时输出" } })
    });

    expect(state.messages[0]).toMatchObject({
      role: "assistant",
      messageId: remoteMessageId,
      remoteMessageId,
      text: "实时输出"
    });
    expect(state.messages[0]).not.toHaveProperty("platformMessageId");
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

  it("keeps root task parts in root scope when live payload also carries child session scope", () => {
    const next = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("message.part.updated", {
        discoveredDuringRun: true,
        taskMessageId: "msg_root",
        parentSessionId: "ses_root",
        rawType: "message.part.updated",
        sessionId: "ses_child",
        sessionID: "ses_root",
        isChildSession: true,
        taskPartId: "prt_task",
        taskCallId: "call_task",
        rootSessionId: "ses_root",
        part: {
          type: "tool",
          tool: "task",
          callID: "call_task",
          id: "prt_task",
          sessionID: "ses_root",
          messageID: "msg_root",
          state: {
            title: "Explore backend structure",
            metadata: {
              parentSessionId: "ses_root",
              sessionId: "ses_child"
            },
            status: "running",
            input: {
              description: "Explore backend structure",
              subagent_type: "explore"
            }
          }
        }
      })
    });

    const rootAssistant = next.messages.find(
      (message) => message.role === "assistant" && message.messageId === "msg_root"
    );

    expect(rootAssistant).toMatchObject({
      role: "assistant",
      messageId: "msg_root",
      parts: [{ partId: "prt_task", type: "tool", toolName: "task", status: "running" }]
    });
    expect((next as any).messageScopesById.msg_root).toMatchObject({
      sessionId: "ses_root",
      rootSessionId: "ses_root",
      isChildSession: false,
      taskPartId: "prt_task"
    });
    expect((next as any).subagentByTaskPartId.prt_task).toBe("ses_child");
    expect((next as any).subagentsBySessionId.ses_child).toMatchObject({
      sessionId: "ses_child",
      taskMessageId: "msg_root",
      taskPartId: "prt_task",
      agentName: "Explore",
      title: "Explore backend structure"
    });
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

  it("keeps native opencode user file parts on the user message", () => {
    const submitted = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "user.submitted",
      prompt: "what in this",
      createdAt: "2026-07-06T00:00:00Z"
    });
    const withRemoteUser = reduceAgentChatRuntime(submitted, {
      type: "event",
      event: event("message.updated", {
        message: { id: "msg_user_file", role: "user" }
      })
    });
    const withSyntheticReadText = reduceAgentChatRuntime(withRemoteUser, {
      type: "event",
      event: event("message.part.updated", {
        messageID: "msg_user_file",
        part: {
          id: "part_synthetic_read",
          messageID: "msg_user_file",
          type: "text",
          synthetic: true,
          text: "Called the Read tool with the following input"
        }
      })
    });
    const withFilePart = reduceAgentChatRuntime(withSyntheticReadText, {
      type: "event",
      event: event("message.part.updated", {
        messageID: "msg_user_file",
        part: {
          id: "part_file",
          messageID: "msg_user_file",
          type: "file",
          filename: "CLAUDE.md",
          mime: "text/plain",
          url: "data:text/plain;base64,IyBDbGF1ZGU="
        }
      })
    });

    expect(withFilePart.messages).toMatchObject([
      {
        role: "user",
        messageId: "msg_user_file",
        text: "what in this",
        parts: [
          {
            type: "file",
            name: "CLAUDE.md",
            mimeType: "text/plain",
            url: "data:text/plain;base64,IyBDbGF1ZGU="
          }
        ]
      }
    ]);
    expect(withFilePart.messages).toHaveLength(1);
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

  it("binds delayed serialized workspace context user parts without rendering the internal prompt", () => {
    const serializedPrompt = [
      "用户问题：",
      "能看到什么内容",
      "",
      "以下是用户添加的工作区上下文：",
      "",
      '<context type="selection" path="99-测试数据/Git冲突处理/冲突文件.md" lines="5-5">',
      "应用分支上的修改，用于制造合并冲突。",
      "</context>"
    ].join("\n");
    const submitted = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "user.submitted",
      prompt: "能看到什么内容",
      parts: [{ type: "text", text: serializedPrompt }],
      createdAt: "2026-07-06T00:00:00Z"
    });
    const completed = reduceAgentChatRuntime(submitted, {
      type: "event",
      event: event("message.part.updated", {
        sessionID: "ses_workspace_context",
        rawType: "message.part.updated",
        part: {
          type: "text",
          text: serializedPrompt,
          messageID: "msg_user_workspace_context",
          sessionID: "ses_workspace_context",
          id: "prt_user_workspace_context"
        }
      })
    });

    expect(completed.messages).toMatchObject([
      {
        role: "user",
        messageId: "msg_user_workspace_context",
        text: "能看到什么内容",
        parts: [{ type: "text", text: serializedPrompt }]
      }
    ]);
    expect(completed.messages).toHaveLength(1);
    expect(
      completed.messages.some(
        (message) => message.role === "assistant" && message.text.includes("以下是用户添加的工作区上下文")
      )
    ).toBe(false);
  });

  it("binds delayed serialized workspace context user deltas without rendering the internal prompt", () => {
    const serializedPrompt = [
      "用户问题：",
      "能看到什么内容",
      "",
      "以下是用户添加的工作区上下文：",
      "",
      '<context type="selection" path="99-测试数据/Git冲突处理/冲突文件.md" lines="5-5">',
      "应用分支上的修改，用于制造合并冲突。",
      "</context>"
    ].join("\n");
    const submitted = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "user.submitted",
      prompt: "能看到什么内容",
      parts: [{ type: "text", text: serializedPrompt }],
      createdAt: "2026-07-06T00:00:00Z"
    });
    const completed = reduceAgentChatRuntime(submitted, {
      type: "event",
      event: event("message.part.delta", {
        messageID: "msg_user_workspace_context",
        partID: "prt_user_workspace_context",
        partType: "text",
        delta: serializedPrompt
      })
    });

    expect(completed.messages).toMatchObject([
      {
        role: "user",
        messageId: "msg_user_workspace_context",
        text: "能看到什么内容",
        parts: [{ type: "text", text: serializedPrompt }]
      }
    ]);
    expect(completed.messages).toHaveLength(1);
    expect(
      completed.messages.some(
        (message) => message.role === "assistant" && message.text.includes("以下是用户添加的工作区上下文")
      )
    ).toBe(false);
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

  it("normalizes opencode question.asked options into single-choice questions", () => {
    const state = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("question.asked", {
        id: "que_f31142aac001QiO13RfFtetggq",
        sessionID: "ses_1",
        questions: [
          {
            question: "领导说需求不满足客户要求，请告诉我具体需要修改哪方面？",
            header: "修改范围",
            options: [
              { label: "需求文档", description: "修改 需求.md / requirements.md 的内容" },
              { label: "UI 界面", description: "修改 index.html / styles.css 的视觉或布局" }
            ],
            multiple: false
          }
        ]
      })
    });

    expect(state.questions).toEqual([
      {
        requestId: "que_f31142aac001QiO13RfFtetggq",
        sessionId: "ses_1",
        createdAt: "2026-06-19T00:00:00Z",
        questions: [
          {
            questionId: "que_f31142aac001QiO13RfFtetggq:0",
            text: "领导说需求不满足客户要求，请告诉我具体需要修改哪方面？",
            header: "修改范围",
            kind: "single",
            options: [
              { id: "需求文档", label: "需求文档", description: "修改 需求.md / requirements.md 的内容" },
              { id: "UI 界面", label: "UI 界面", description: "修改 index.html / styles.css 的视觉或布局" }
            ],
            custom: undefined,
            required: undefined
          }
        ]
      }
    ]);
  });

  it("normalizes multiple and text question variants", () => {
    const state = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("question.asked", {
        requestId: "ques_1",
        sessionId: "ses_1",
        questions: [
          {
            id: "q_multi",
            question: "需要哪些调整？",
            type: "single",
            options: [{ label: "样式", description: "调整 CSS" }],
            multiple: true,
            custom: true
          },
          { id: "q_text", question: "还有什么补充？" }
        ]
      })
    });

    expect(state.questions[0]?.questions).toEqual([
      {
        questionId: "q_multi",
        text: "需要哪些调整？",
        header: undefined,
        kind: "multiple",
        options: [{ id: "样式", label: "样式", description: "调整 CSS" }],
        custom: true,
        required: undefined
      },
      {
        questionId: "q_text",
        text: "还有什么补充？",
        header: undefined,
        kind: "text",
        options: undefined,
        custom: undefined,
        required: undefined
      }
    ]);
  });

  it("does not infer questions from numbered assistant text parts", () => {
    const state = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("message.part.updated", {
        messageID: "msg_1",
        part: {
          id: "part_1",
          messageID: "msg_1",
          type: "text",
          text: "是。`index.html` 是入口页面，加载顺序为：\n\n1. `styles.css` — 样式\n2. `mock.js` — 模拟数据（必须在前）\n3. `app.js` — 主逻辑"
        }
      })
    });

    expect(state.questions).toEqual([]);
    expect(state.messages).toMatchObject([
      {
        role: "assistant",
        parts: [{ partId: "part_1", type: "text" }]
      }
    ]);
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

  it("projects OpenCode todowrite tool updates into the todo panel when no todo.updated event is emitted", () => {
    const state = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("message.part.updated", {
        messageID: "msg_todowrite_1",
        part: {
          id: "part_todowrite_1",
          messageID: "msg_todowrite_1",
          type: "tool",
          tool: "todowrite",
          state: {
            status: "completed",
            // OpenCode 1.17.7 实际将 todo 快照放在工具输入，而非 todo.updated SSE。
            input: {
              todos: [
                { content: "识别测试对象", status: "completed", priority: "high" },
                { content: "编写案例", status: "in_progress", priority: "medium" }
              ]
            }
          }
        }
      })
    });

    expect(state.todos).toEqual([
      expect.objectContaining({ text: "识别测试对象", status: "completed", priority: "high" }),
      expect.objectContaining({ text: "编写案例", status: "in_progress", priority: "medium" })
    ]);
    expect(new Set(state.todos.map((item) => item.id)).size).toBe(2);
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

  it("normalizes object session.status retry payloads and keeps the run active", () => {
    const state = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("session.status", {
        status: {
          type: "retry",
          attempt: 1,
          message: "Free usage exceeded, subscribe to Go",
          action: {
            label: "subscribe",
            link: "https://opencode.ai/go"
          },
          next: 1783296000963
        }
      })
    });

    expect(state.status).toBe("RETRY");
    expect(state.runtimeStatus).toMatchObject({
      type: "retry",
      retryKey: "evt_session.status",
      attempt: 1,
      maxAttempts: 3,
      message: "Free usage exceeded, subscribe to Go",
      retryAfterSeconds: 60,
      action: {
        label: "subscribe",
        link: "https://opencode.ai/go"
      }
    });
  });

  it("clears retry runtime status when assistant output resumes", () => {
    const retrying = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("session.status", {
        status: {
          type: "retry",
          attempt: 1,
          message: "Free usage exceeded, subscribe to Go"
        }
      })
    });

    const resumed = reduceAgentChatRuntime(retrying, {
      type: "event",
      event: { ...event("message.part.delta", { messageID: "msg_1", partID: "part_1", partType: "text", delta: "继续" }), eventId: "evt_message_part_delta_after_retry" }
    });

    expect(resumed.status).toBe("RUNNING");
    expect(resumed.runtimeStatus).toEqual({ type: "busy" });
  });

  it("uses the later success terminal event and removes stale failed cards for the same run", () => {
    const failed = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("run.failed", {
        status: "FAILED",
        error: { name: "ConnectionError", message: "Streaming response failed" }
      })
    });

    const succeeded = reduceAgentChatRuntime(failed, {
      type: "event",
      event: { ...event("run.succeeded", { status: "SUCCEEDED" }), eventId: "evt_run_succeeded_later", seq: 2 }
    });

    expect(failed.status).toBe("FAILED");
    expect(failed.messages).toHaveLength(1);
    expect(succeeded.status).toBe("SUCCEEDED");
    expect(succeeded.messages).toHaveLength(0);
  });

  it("clears stale run failed cards when a new run is requested", () => {
    const failed = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("run.failed", {
        status: "FAILED",
        error: { name: "Error", message: "Model not found" }
      })
    });

    const pending = reduceAgentChatRuntime(failed, { type: "run.requested" });

    expect(pending.status).toBe("PENDING");
    expect(pending.messages).toHaveLength(0);
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

  it("clears stale run projection before replaying snapshot reset events in order", () => {
    const staleEvents = [
      event("assistant.message.delta", { delta: "旧回答" }),
      event("permission.asked", { id: "perm_old", sessionId: "ses_1", type: "edit" }),
      event("question.asked", { id: "question_old", sessionId: "ses_1", questions: [{ question: "旧问题" }] }),
      event("todo.updated", { todos: [{ content: "旧任务", status: "pending" }] }),
      event("diff.proposed", { sessionId: "ses_1", files: [{ path: "old.ts" }] })
    ];
    const staleState = staleEvents.reduce(
      (state, item) => reduceAgentChatRuntime(state, { type: "event", event: item }),
      createInitialAgentChatRuntimeState()
    );
    const reset = {
      ...event("run.snapshot.reset", {
        snapshot: {
          barrierSeq: 9,
          events: [
            { ...event("assistant.message.delta", { delta: "新" }), eventId: "evt_snapshot_1", seq: 0 },
            { ...event("assistant.message.delta", { delta: "回答" }), eventId: "evt_snapshot_2", seq: 0 },
            {
              ...event("todo.updated", { todos: [{ content: "新任务", status: "in_progress" }] }),
              eventId: "evt_snapshot_3",
              seq: 0
            },
            {
              ...event("question.asked", {
                id: "question_new",
                sessionId: "ses_1",
                questions: [{ question: "新问题" }]
              }),
              eventId: "evt_snapshot_4",
              seq: 0
            }
          ]
        }
      }),
      eventId: "evt_snapshot_reset_run_1_2",
      seq: 0
    } satisfies RunEvent;

    const restored = reduceAgentChatRuntime(staleState, { type: "event", event: reset });

    expect(restored.messages).toHaveLength(1);
    expect(restored.messages[0]).toMatchObject({ role: "assistant", text: "新回答" });
    expect(restored.permissions).toEqual([]);
    expect(restored.questions).toHaveLength(1);
    expect(restored.questions[0]?.requestId).toBe("question_new");
    expect(restored.todos).toEqual([expect.objectContaining({ text: "新任务", status: "in_progress" })]);
    expect(restored.diff).toBeUndefined();
    expect(restored.seenEventIds).toEqual(expect.arrayContaining([
      "evt_snapshot_1",
      "evt_snapshot_2",
      "evt_snapshot_3",
      "evt_snapshot_4",
      "evt_snapshot_reset_run_1_2"
    ]));
  });

  it("clears run projection when snapshot reset payload is absent or empty", () => {
    const stale = reduceAgentChatRuntime(createInitialAgentChatRuntimeState(), {
      type: "event",
      event: event("assistant.message.delta", { delta: "旧回答" })
    });

    const missing = reduceAgentChatRuntime(stale, {
      type: "event",
      event: { ...event("run.snapshot.reset", {}), eventId: "evt_reset_missing", seq: 0 }
    });
    const empty = reduceAgentChatRuntime(stale, {
      type: "event",
      event: {
        ...event("run.snapshot.reset", { snapshot: { events: [] } }),
        eventId: "evt_reset_empty",
        seq: 0
      }
    });

    expect(missing.messages).toEqual([]);
    expect(empty.messages).toEqual([]);
  });

  it("keeps persisted transcript messages while clearing transient run messages", () => {
    const persisted = {
      id: "msg_1234567890abcdef1234567890abcdef",
      messageId: "msg_1234567890abcdef1234567890abcdef",
      platformMessageId: "msg_1234567890abcdef1234567890abcdef",
      role: "user" as const,
      text: "历史问题",
      createdAt: "2026-07-09T00:00:00Z"
    };
    const withTransient = reduceAgentChatRuntime(createInitialAgentChatRuntimeState([persisted]), {
      type: "event",
      event: event("assistant.message.delta", { delta: "未完成回答" })
    });

    const restored = reduceAgentChatRuntime(withTransient, {
      type: "event",
      event: {
        ...event("run.snapshot.reset", { snapshot: { events: [] } }),
        eventId: "evt_reset_keep_history",
        seq: 0
      }
    });

    expect(restored.messages).toEqual([persisted]);
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
