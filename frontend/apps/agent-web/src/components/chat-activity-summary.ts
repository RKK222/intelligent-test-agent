import type { PermissionRequest, QuestionRequest, Run, SubagentSession, TodoItem } from "@test-agent/shared-types";

export type ChatActivityInput = {
  sessionId: string;
  run?: Pick<Run, "runId" | "status"> | null;
  /** Todo 已由对话宿主按当前会话归一化，此处不得伪造跨会话或跨 Run 的过滤规则。 */
  todos: TodoItem[];
  subagentsBySessionId: Record<string, SubagentSession>;
  permissions: PermissionRequest[];
  questions: QuestionRequest[];
};

export type ChatActivityItem =
  | {
      kind: "confirmation";
      confirmationKind: "permission" | "question";
      requestId: string;
      title: string;
    }
  | {
      kind: "subagent";
      sessionId: string;
      title: string;
      agentName: string;
      status: string;
    }
  | {
      kind: "todo";
      todoId: string;
      title: string;
      status: string;
    }
  | {
      kind: "run-failed";
      runId: string;
      status: string;
    };

export type ChatActivitySummary = {
  pendingConfirmationCount: number;
  items: ChatActivityItem[];
};

/**
 * 仅从当前会话已归一化的运行态构建外层只读活动摘要。
 * 不读取或投影 OpencodeTimeline 的消息、part、工具和原生操作状态。
 */
export function buildChatActivitySummary(input: ChatActivityInput): ChatActivitySummary | null {
  const confirmations = [
    ...input.permissions.filter((item) => item.sessionId === input.sessionId).map(permissionItem),
    ...input.questions.filter((item) => item.sessionId === input.sessionId).map(questionItem)
  ];
  const runningSubagents = Object.values(input.subagentsBySessionId)
    .filter((item) => item.parentSessionId === input.sessionId && hasStatus(item.status, "running"))
    .map(subagentItem);
  const inProgressTodos = input.todos.filter((item) => hasStatus(item.status, "in_progress")).map(todoItem);
  const failedRun = input.run && hasFailureStatus(input.run.status)
    ? [{ kind: "run-failed" as const, runId: input.run.runId, status: input.run.status }]
    : [];
  const items = [...confirmations, ...runningSubagents, ...inProgressTodos, ...failedRun];

  return items.length > 0
    ? { pendingConfirmationCount: confirmations.length, items }
    : null;
}

function permissionItem(item: PermissionRequest): Extract<ChatActivityItem, { kind: "confirmation" }> {
  return {
    kind: "confirmation",
    confirmationKind: "permission",
    requestId: item.requestId,
    title: item.title ?? item.description ?? item.type
  };
}

function questionItem(item: QuestionRequest): Extract<ChatActivityItem, { kind: "confirmation" }> {
  const question = item.questions[0];
  return {
    kind: "confirmation",
    confirmationKind: "question",
    requestId: item.requestId,
    title: question?.header ?? question?.text ?? "待回答问题"
  };
}

function subagentItem(item: SubagentSession): Extract<ChatActivityItem, { kind: "subagent" }> {
  return {
    kind: "subagent",
    sessionId: item.sessionId,
    title: item.title,
    agentName: item.agentName,
    status: item.status
  };
}

function todoItem(item: TodoItem): Extract<ChatActivityItem, { kind: "todo" }> {
  return {
    kind: "todo",
    todoId: item.id,
    title: item.title ?? item.text,
    status: item.status
  };
}

function hasStatus(status: string | undefined, expected: string): boolean {
  return status?.trim().toLowerCase() === expected;
}

/** Run 运行态兼容后端可能返回的 FAILED 与 ERROR 两种失败终态。 */
function hasFailureStatus(status: string | undefined): boolean {
  const normalized = status?.trim().toLowerCase();
  return normalized === "failed" || normalized === "error";
}
