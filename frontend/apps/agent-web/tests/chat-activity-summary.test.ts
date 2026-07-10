import { describe, expect, it } from "vitest";
import { buildChatActivitySummary } from "../src/components/chat-activity-summary";

describe("buildChatActivitySummary", () => {
  it("在没有本轮活动时不创建外层入口", () => {
    expect(
      buildChatActivitySummary({
        sessionId: "ses-root",
        run: null,
        todos: [],
        subagentsBySessionId: {},
        permissions: [],
        questions: []
      })
    ).toBeNull();
  });

  it("在 Run 结束后仍保留当前会话未处理的 Ask 摘要", () => {
    const summary = buildChatActivitySummary({
      sessionId: "ses-root",
      run: { runId: "run-ended", status: "SUCCEEDED" },
      todos: [],
      subagentsBySessionId: {},
      permissions: [],
      questions: [
        {
          requestId: "ask-current",
          sessionId: "ses-root",
          createdAt: "2026-07-10T00:00:00Z",
          questions: [{ questionId: "q-1", text: "继续吗？", kind: "text" }]
        },
        {
          requestId: "ask-other",
          sessionId: "ses-other",
          createdAt: "2026-07-10T00:00:00Z",
          questions: [{ questionId: "q-2", text: "不应出现", kind: "text" }]
        }
      ]
    });

    expect(summary?.pendingConfirmationCount).toBe(1);
    expect(summary?.items).toMatchObject([
      { kind: "confirmation", confirmationKind: "question", requestId: "ask-current" }
    ]);
  });

  it("按确认、运行中子 Agent、进行中任务和当前失败 Run 的优先级汇总", () => {
    const summary = buildChatActivitySummary({
      sessionId: "ses-root",
      run: { runId: "run-1", status: "FAILED" },
      todos: [
        { id: "todo-active", text: "检查日志", status: "in_progress" },
        { id: "todo-finished", text: "已完成任务", status: "completed" }
      ],
      subagentsBySessionId: {
        childActive: {
          sessionId: "ses-child-active",
          parentSessionId: "ses-root",
          agentName: "log-agent",
          title: "日志分析",
          status: "RUNNING",
          updatedAt: "2026-07-10T00:00:00Z"
        },
        childFinished: {
          sessionId: "ses-child-finished",
          parentSessionId: "ses-root",
          agentName: "done-agent",
          title: "已结束子任务",
          status: "SUCCEEDED",
          updatedAt: "2026-07-10T00:00:00Z"
        },
        childOther: {
          sessionId: "ses-child-other",
          parentSessionId: "ses-other",
          agentName: "other-agent",
          title: "其他会话",
          status: "RUNNING",
          updatedAt: "2026-07-10T00:00:00Z"
        }
      },
      permissions: [
        {
          requestId: "permission-current",
          sessionId: "ses-root",
          type: "edit",
          title: "允许修改文件",
          createdAt: "2026-07-10T00:00:00Z"
        },
        {
          requestId: "permission-other",
          sessionId: "ses-other",
          type: "edit",
          title: "不应出现",
          createdAt: "2026-07-10T00:00:00Z"
        }
      ],
      questions: [
        {
          requestId: "question-current",
          sessionId: "ses-root",
          createdAt: "2026-07-10T00:00:00Z",
          questions: [{ questionId: "q-1", text: "继续吗？", kind: "text" }]
        }
      ]
    });

    expect(summary?.pendingConfirmationCount).toBe(2);
    expect(summary?.items.map((item) => item.kind)).toEqual(["confirmation", "confirmation", "subagent", "todo", "run-failed"]);
    expect(summary?.items).toMatchObject([
      { confirmationKind: "permission", requestId: "permission-current" },
      { confirmationKind: "question", requestId: "question-current" },
      { sessionId: "ses-child-active", title: "日志分析" },
      { todoId: "todo-active", title: "检查日志" },
      { runId: "run-1", status: "FAILED" }
    ]);
  });

  it("将 ERROR 作为失败 Run 汇总", () => {
    const summary = buildChatActivitySummary({
      sessionId: "ses-root",
      run: { runId: "run-error", status: "ERROR" },
      todos: [],
      subagentsBySessionId: {},
      permissions: [],
      questions: []
    });

    expect(summary?.items).toEqual([{ kind: "run-failed", runId: "run-error", status: "ERROR" }]);
  });
});
