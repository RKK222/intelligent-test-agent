import type {
  WorkflowAgUiEvent,
  WorkflowMessage,
  WorkflowStructuredInput,
} from "@test-agent/workflow-api-client";

export type ToolActivity = {
  id: string;
  name: string;
  status: "RUNNING" | "FINISHED" | "SUCCEEDED" | "FAILED";
};

export type WorkflowViewState = {
  messages: WorkflowMessage[];
  runId?: string;
  taskId?: string;
  runStatus?: string;
  workspaceStatus?: string;
  requiredInput: string[];
  scopeInput: Array<Record<string, unknown>>;
  baselineInput: Array<{ repositoryId: string; availableBranches: string[] }>;
  currentInput?: WorkflowStructuredInput;
  tools: ToolActivity[];
  reportPublished: boolean;
};

export function initialWorkflowViewState(): WorkflowViewState {
  return {
    messages: [],
    requiredInput: [],
    scopeInput: [],
    baselineInput: [],
    tools: [],
    reportPublished: false,
  };
}

export function reduceAgUiEvent(state: WorkflowViewState, event: WorkflowAgUiEvent): void {
  switch (event.type) {
    case "STATE_SNAPSHOT": {
      const snapshot = (event.snapshot ?? {}) as Record<string, unknown>;
      if (snapshot.runId) state.runId = String(snapshot.runId);
      if (snapshot.taskId) state.taskId = String(snapshot.taskId);
      if (snapshot.runStatus) state.runStatus = String(snapshot.runStatus);
      if (snapshot.workspaceStatus) state.workspaceStatus = String(snapshot.workspaceStatus);
      state.requiredInput = Array.isArray(snapshot.requiredInput)
        ? snapshot.requiredInput.map(String)
        : [];
      state.scopeInput = Array.isArray(snapshot.scopeInput)
        ? snapshot.scopeInput as Array<Record<string, unknown>>
        : [];
      state.baselineInput = Array.isArray(snapshot.baselineInput)
        ? snapshot.baselineInput as Array<{ repositoryId: string; availableBranches: string[] }>
        : [];
      state.currentInput = snapshot.currentInput as WorkflowStructuredInput | undefined;
      state.tools = Array.isArray(snapshot.tools)
        ? snapshot.tools.map((tool) => {
            const value = tool as Record<string, unknown>;
            return {
              id: String(value.id),
              name: String(value.name ?? "分析工具"),
              status: String(value.status ?? "FINISHED") as ToolActivity["status"],
            };
          })
        : [];
      state.reportPublished = Boolean(snapshot.reportPublished);
      return;
    }
    case "MESSAGES_SNAPSHOT":
      state.messages = ((event.messages as Array<Record<string, unknown>> | undefined) ?? []).map((value) => ({
        id: String(value.id),
        role: String(value.role),
        content: String(value.content ?? ""),
        createdAt: String(value.createdAt ?? ""),
      }));
      return;
    case "TEXT_MESSAGE_START":
      if (!state.messages.some((message) => message.id === event.messageId)) {
        state.messages.push({
          id: String(event.messageId),
          role: String(event.role ?? "assistant"),
          content: "",
          createdAt: new Date().toISOString(),
        });
      }
      return;
    case "TEXT_MESSAGE_CONTENT": {
      const message = state.messages.find((value) => value.id === event.messageId);
      if (message) message.content += String(event.delta ?? "");
      return;
    }
    case "RUN_STARTED":
      state.runId = String(event.runId);
      state.taskId = String(event.taskId);
      state.runStatus = String(event.status ?? "RUNNING");
      state.reportPublished = false;
      state.baselineInput = [];
      state.currentInput = undefined;
      return;
    case "RUN_FINISHED":
      state.runId = String(event.runId);
      state.taskId = String(event.taskId);
      state.runStatus = String(event.status);
      return;
    case "RUN_ERROR":
      state.runStatus = "FAILED";
      return;
    case "TOOL_CALL_START":
      {
        const id = String(event.toolCallId);
        const existing = state.tools.find((value) => value.id === id);
        if (existing) {
          existing.name = String(event.toolCallName ?? existing.name);
          existing.status = "RUNNING";
        } else {
          state.tools.push({
            id,
            name: String(event.toolCallName ?? "分析工具"),
            status: "RUNNING",
          });
        }
      }
      return;
    case "TOOL_CALL_END": {
      const tool = state.tools.find((value) => value.id === event.toolCallId);
      if (tool) {
        const status = String(event.status ?? "FINISHED");
        tool.status = ["SUCCEEDED", "FAILED"].includes(status)
          ? status as ToolActivity["status"]
          : "FINISHED";
      }
      return;
    }
    case "CUSTOM":
      reduceCustomEvent(state, event);
      return;
    default:
      return;
  }
}

function reduceCustomEvent(state: WorkflowViewState, event: WorkflowAgUiEvent): void {
  const name = String(event.name ?? "");
  const value = (event.value ?? {}) as Record<string, unknown>;
  if (name === "workflow.input_required") {
    state.requiredInput = Array.isArray(value.requiredInput)
      ? value.requiredInput.map(String)
      : [];
    state.scopeInput = Array.isArray(value.scope)
      ? value.scope as Array<Record<string, unknown>>
      : [];
    state.baselineInput = Array.isArray(value.baselines)
      ? value.baselines.map((item) => {
          const baseline = item as Record<string, unknown>;
          return {
            repositoryId: String(baseline.repositoryId),
            availableBranches: Array.isArray(baseline.availableBranches)
              ? baseline.availableBranches.map(String)
              : [],
          };
        })
      : [];
    state.currentInput = value.currentInput as WorkflowStructuredInput | undefined;
    if (["SCOPE_DISAMBIGUATION", "BASELINE_SELECTION"].includes(String(value.kind))) {
      state.runStatus = "WAITING_INPUT";
    }
  } else if (name === "workflow.report_published") {
    state.taskId = String(value.taskId);
    state.reportPublished = true;
  } else if (name === "workflow.workspace_state") {
    state.workspaceStatus = String(value.status);
  }
}
