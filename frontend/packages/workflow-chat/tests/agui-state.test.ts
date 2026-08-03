import { describe, expect, it } from "vitest";
import { initialWorkflowViewState, reduceAgUiEvent } from "../src/agui-state";

describe("workflow AG-UI state", () => {
  it("clears optional run projection fields when a conversation view is reset", () => {
    const state = initialWorkflowViewState();
    state.runId = "run_failed";
    state.taskId = "task_failed";
    state.runStatus = "FAILED";
    state.workspaceStatus = "CLEANUP_FAILED";
    state.currentInput = { mode: "SINGLE" };

    Object.assign(state, initialWorkflowViewState());

    expect(state.runId).toBeUndefined();
    expect(state.taskId).toBeUndefined();
    expect(state.runStatus).toBeUndefined();
    expect(state.workspaceStatus).toBeUndefined();
    expect(state.currentInput).toBeUndefined();
  });

  it("restores the latest run and workspace from STATE_SNAPSHOT", () => {
    const state = initialWorkflowViewState();

    reduceAgUiEvent(state, {
      type: "STATE_SNAPSHOT",
      snapshot: {
        runId: "run_12345678",
        taskId: "task_12345678",
        runStatus: "SUCCEEDED",
        workspaceStatus: "STOPPED_RETAINED",
        reportPublished: true,
        tools: [{ id: "tool-1", name: "Codex分析", status: "SUCCEEDED" }],
      },
    });

    expect(state.runId).toBe("run_12345678");
    expect(state.taskId).toBe("task_12345678");
    expect(state.runStatus).toBe("SUCCEEDED");
    expect(state.workspaceStatus).toBe("STOPPED_RETAINED");
    expect(state.reportPublished).toBe(true);
    expect(state.tools).toEqual([{ id: "tool-1", name: "Codex分析", status: "SUCCEEDED" }]);
  });

  it("deduplicates retried tool calls and preserves failed status", () => {
    const state = initialWorkflowViewState();

    reduceAgUiEvent(state, {
      type: "TOOL_CALL_START",
      toolCallId: "run-1:analyze:codex",
      toolCallName: "Codex分析",
    });
    reduceAgUiEvent(state, {
      type: "TOOL_CALL_START",
      toolCallId: "run-1:analyze:codex",
      toolCallName: "Codex重试",
    });
    reduceAgUiEvent(state, {
      type: "TOOL_CALL_END",
      toolCallId: "run-1:analyze:codex",
      status: "FAILED",
    });

    expect(state.tools).toEqual([
      { id: "run-1:analyze:codex", name: "Codex重试", status: "FAILED" },
    ]);
  });

  it("keeps baseline candidates and original input for a resumable card", () => {
    const state = initialWorkflowViewState();

    reduceAgUiEvent(state, {
      type: "CUSTOM",
      name: "workflow.input_required",
      value: {
        kind: "BASELINE_SELECTION",
        baselines: [
          { repositoryId: "repo_12345678", availableBranches: ["develop", "release"] },
        ],
        currentInput: {
          repositories: [
            { repositoryId: "repo_12345678", targetBranch: "feature/a" },
          ],
          mode: "SINGLE",
          analyzerIds: ["codex"],
        },
      },
    });

    expect(state.runStatus).toBe("WAITING_INPUT");
    expect(state.baselineInput[0]?.availableBranches).toEqual(["develop", "release"]);
    expect(state.currentInput?.analyzerIds).toEqual(["codex"]);
  });

  it("projects an expired retained workspace without losing the report state", () => {
    const state = initialWorkflowViewState();

    reduceAgUiEvent(state, {
      type: "STATE_SNAPSHOT",
      snapshot: {
        runStatus: "SUCCEEDED",
        workspaceStatus: "EXPIRED",
        reportPublished: true,
      },
    });

    expect(state.workspaceStatus).toBe("EXPIRED");
    expect(state.reportPublished).toBe(true);
  });
});
