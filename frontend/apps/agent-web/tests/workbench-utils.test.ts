import { describe, expect, it } from "vitest";
import type { AgentMessage, FileSearchResult, MessagePart, PromptPart, Run, RunDiffFile, RunEvent, Session, SessionMessage, SessionRuntimeState, SessionTreeMessagesResponse } from "@test-agent/shared-types";
import * as workbenchUtils from "../src/components/workbench-utils";
import {
  assistantSummaryMessageId,
  OPENCODE_HEALTH_REFETCH_INTERVAL_MS,
  PUBLIC_CONFIG_GATE_REFETCH_INTERVAL_MS,
  OPENCODE_RUNTIME_CAPABILITY_REFETCH_INTERVAL_MS,
  OPENCODE_VCS_STATUS_REFETCH_INTERVAL_MS,
  opencodeAvailabilityFromHealth,
  opencodeAvailabilityFromProcess,
  opencodeHealthRequestFromProcess,
  publicConfigGateRefetchInterval,
  chatStateFromSessionTreeSnapshot,
  dedupeSessionMessages,
  diffFilesFromPayload,
  diffFilesFromSessionMessages,
  filterWorkspaceRootEntries,
  isWorkspaceAgentConfigPath,
  inferDiffFromToolPart,
  historyItems,
  reconcileCurrentTurnTodos,
  runEventProjectionMode,
  runEventMatchesRun,
  mergeDiffFiles,
  messagesFromSessionMessages,
  messagesFromSessionTreeSnapshot,
  normalizePathKey,
  nextCenterModeAfterVcsRefresh,
  nextCenterModeAfterRunDiff,
  prepareAutoRetryRun,
  resolveRetryDeadline,
  retryCountdownSeconds,
  retryExpirationDecision,
  runEventSubscriptionRunId,
  runEventSubscriptionSessionId,
  sessionTitleEventMatchesCurrentSession,
  platformSessionTitleFromSynchronizedEventPayload,
  projectRootInteractionSession,
  isSupersededInteractionAsk,
  runEventProjection,
  sessionTitleFromFirstMessage,
  shouldFailExhaustedRetry,
  workspaceRequirementReferences,
  workspaceRequirementStageDirectories,
  workspaceLoadIsCurrent
} from "../src/components/workbench-utils";
import type { FileTreeEntry } from "@test-agent/shared-types";

function file(path: string, additions: number, deletions: number, status = "modified"): RunDiffFile {
  return { path, patch: "", additions, deletions, status };
}

function toolPart(input: Record<string, unknown>, overrides: Partial<Extract<MessagePart, { type: "tool" }>> = {}): Extract<MessagePart, { type: "tool" }> {
  return {
    partId: "part_1",
    type: "tool",
    toolName: "write",
    status: "completed",
    input,
    ...overrides
  };
}

describe("filterWorkspaceRootEntries", () => {
  it("hides .opencode only from the workspace root", () => {
    const entries: FileTreeEntry[] = [
      { path: ".opencode", name: ".opencode", type: "directory" },
      { path: "src", name: "src", type: "directory" }
    ];

    expect(filterWorkspaceRootEntries("", entries)).toEqual([
      { path: "src", name: "src", type: "directory" }
    ]);
    expect(filterWorkspaceRootEntries("config", entries)).toEqual(entries);
  });
});

describe("isWorkspaceAgentConfigPath", () => {
  it("识别普通 workspace 中需要交给 Agent 配置区管理的 .opencode 路径", () => {
    expect(isWorkspaceAgentConfigPath(".opencode/skills/demo/SKILL.md")).toBe(true);
    expect(isWorkspaceAgentConfigPath("workspace/.opencode/agents/demo.md")).toBe(true);
    expect(isWorkspaceAgentConfigPath('".opencode\\skills\\demo\\SKILL.md"')).toBe(true);
    expect(isWorkspaceAgentConfigPath("docs/spec.md")).toBe(false);
  });
});

describe("workspaceRequirementReferences", () => {
  it("groups spec files by the same requirement subitem and ignores the legacy root layout", () => {
    expect(workspaceRequirementStageDirectories).toEqual(["01-需求", "02-设计", "03-编码", "04-测试"]);
    const results: FileSearchResult[] = [
      {
        path: "spec/120260624-0318-新增助商组合贷信鸽取证状态/01-需求/S20260630-000038-子条目/需求文档/test.txt",
        name: "test.txt",
        directory: "spec/120260624-0318-新增助商组合贷信鸽取证状态/01-需求/S20260630-000038-子条目/需求文档",
        size: 1
      },
      {
        path: "spec/120260624-0318-新增助商组合贷信鸽取证状态/01-需求/S20260630-000038-子条目/子条目需求.md",
        name: "子条目需求.md",
        directory: "spec/120260624-0318-新增助商组合贷信鸽取证状态/01-需求/S20260630-000038-子条目",
        size: 10
      },
      {
        path: "spec/120260624-0318-新增助商组合贷信鸽取证状态/02-设计/S20260630-000038-子条目/详细设计.md",
        name: "详细设计.md",
        directory: "spec/120260624-0318-新增助商组合贷信鸽取证状态/02-设计/S20260630-000038-子条目",
        size: 10
      },
      {
        path: "spec/120260624-0318-新增助商组合贷信鸽取证状态/03-编码/S20260630-000038-子条目/031-业务代码/test2.txt",
        name: "test2.txt",
        directory: "spec/120260624-0318-新增助商组合贷信鸽取证状态/03-编码/S20260630-000038-子条目/031-业务代码",
        size: 1
      },
      {
        path: "spec/120260624-0318-新增助商组合贷信鸽取证状态/04-测试/S20260630-000038-子条目/测试案例.md",
        name: "测试案例.md",
        directory: "spec/120260624-0318-新增助商组合贷信鸽取证状态/04-测试/S20260630-000038-子条目",
        size: 10
      },
      {
        path: "spec/120260624-0318-新增助商组合贷信鸽取证状态/05-发布/S20260630-000038-子条目/发布说明.md",
        name: "发布说明.md",
        directory: "spec/120260624-0318-新增助商组合贷信鸽取证状态/05-发布/S20260630-000038-子条目",
        size: 10
      },
      {
        path: "120260624-0318-新增助商组合贷信鸽取证状态/01-需求/S20260630-000038-子条目/旧结构.md",
        name: "旧结构.md",
        directory: "120260624-0318-新增助商组合贷信鸽取证状态/01-需求/S20260630-000038-子条目",
        size: 10
      }
    ];

    expect(workspaceRequirementReferences(results)).toEqual([
      {
        id: "spec/120260624-0318-新增助商组合贷信鸽取证状态/01-需求/S20260630-000038-子条目",
        requirementName: "120260624-0318-新增助商组合贷信鸽取证状态",
        subitemName: "S20260630-000038-子条目",
        filePaths: [
          "spec/120260624-0318-新增助商组合贷信鸽取证状态/01-需求/S20260630-000038-子条目/子条目需求.md",
          "spec/120260624-0318-新增助商组合贷信鸽取证状态/01-需求/S20260630-000038-子条目/需求文档/test.txt",
          "spec/120260624-0318-新增助商组合贷信鸽取证状态/02-设计/S20260630-000038-子条目/详细设计.md",
          "spec/120260624-0318-新增助商组合贷信鸽取证状态/03-编码/S20260630-000038-子条目/031-业务代码/test2.txt",
          "spec/120260624-0318-新增助商组合贷信鸽取证状态/04-测试/S20260630-000038-子条目/测试案例.md"
        ]
      }
    ]);
  });
});

describe("workspaceLoadIsCurrent", () => {
  it("accepts a result only for the selected workspace and current generation", () => {
    expect(workspaceLoadIsCurrent("wrk_a", 3, "wrk_a", 3)).toBe(true);
    expect(workspaceLoadIsCurrent("wrk_a", 3, "wrk_b", 3)).toBe(false);
    expect(workspaceLoadIsCurrent("wrk_a", 2, "wrk_a", 3)).toBe(false);
  });
});

describe("runEventMatchesRun", () => {
  it("accepts events only for the subscribed and current run", () => {
    const current = {
      runId: "run_current",
      sessionId: "ses_1",
      workspaceId: "wrk_1",
      status: "RUNNING",
      createdAt: "2026-07-04T09:00:00Z",
      updatedAt: "2026-07-04T09:00:00Z"
    };

    expect(runEventMatchesRun({ runId: "run_current" }, "run_current", current)).toBe(true);
    expect(runEventMatchesRun({ runId: "run_old" }, "run_current", current)).toBe(false);
    expect(runEventMatchesRun({ runId: "run_current" }, "run_old", current)).toBe(false);
    expect(runEventMatchesRun({ runId: "run_current" }, "run_current", { ...current, runId: "run_next" })).toBe(false);
  });
});

describe("runEventProjectionMode", () => {
  const current = {
    runId: "run_old",
    sessionId: "ses_1",
    workspaceId: "wrk_1",
    status: "SUCCEEDED",
    createdAt: "2026-07-15T09:00:00Z",
    updatedAt: "2026-07-15T09:00:00Z"
  } as Run;

  it("allows only title synchronization from a superseded run", () => {
    expect(runEventProjectionMode({ runId: "run_old", type: "session.updated" }, "run_old", current, "run_old")).toBe("title-only");
    expect(runEventProjectionMode({ runId: "run_old", type: "todo.updated" }, "run_old", current, "run_old")).toBe("ignore");
    expect(runEventProjectionMode({ runId: "run_old", type: "run.snapshot.reset" }, "run_old", current, "run_old")).toBe("ignore");
  });

  it("restores normal projection after the new run becomes current", () => {
    const next = { ...current, runId: "run_next", status: "RUNNING" } as Run;
    expect(runEventProjectionMode({ runId: "run_next", type: "todo.updated" }, "run_next", next, "run_old")).toBe("conversation");
    expect(runEventProjectionMode({ runId: "run_old", type: "session.updated" }, "run_old", next, "run_old")).toBe("ignore");
  });
});

describe("RunEvent subscription identity", () => {
  const running = {
    runId: "run_current",
    sessionId: "ses_current",
    workspaceId: "wrk_1",
    status: "RUNNING",
    createdAt: "2026-07-17T08:00:00Z",
    updatedAt: "2026-07-17T08:00:00Z"
  } as Run;

  it("keeps the same scalar run identity while busy, settling a terminal, or waiting for title", () => {
    expect(runEventSubscriptionRunId(running, null, null)).toBe("run_current");
    expect(runEventSubscriptionRunId({ ...running, status: "FAILED" }, null, "run_current"))
      .toBe("run_current");
    expect(runEventSubscriptionRunId({ ...running, status: "SUCCEEDED" }, "run_current", null))
      .toBe("run_current");
    expect(runEventSubscriptionRunId({ ...running, status: "SUCCEEDED" }, null, null))
      .toBeUndefined();
  });

  it("switches once to a new run and rejects a run whose platform session is no longer current", () => {
    const next = { ...running, runId: "run_next", sessionId: "ses_next" } as Run;

    expect(runEventSubscriptionRunId(next, "run_current", "run_current")).toBe("run_next");
    expect(runEventSubscriptionSessionId("run_current", running, "ses_current")).toBe("ses_current");
    expect(runEventSubscriptionSessionId("run_current", running, "ses_next")).toBeUndefined();
    expect(runEventSubscriptionSessionId("run_next", next, "ses_next")).toBe("ses_next");
  });
});

describe("sessionTitleEventMatchesCurrentSession", () => {
  it("accepts a title event only when its subscribed platform session remains current", () => {
    expect(sessionTitleEventMatchesCurrentSession("ses_current", "ses_current")).toBe(true);
    expect(sessionTitleEventMatchesCurrentSession("ses_previous", "ses_current")).toBe(false);
    expect(sessionTitleEventMatchesCurrentSession("ses_current", undefined)).toBe(false);
  });
});

describe("projectRootInteractionSession", () => {
  it("maps root question and permission events from remote session to the subscribed platform session", () => {
    const event = {
      eventId: "evt_question",
      runId: "run_1",
      seq: 7,
      type: "question.asked",
      traceId: "trace_1",
      occurredAt: "2026-07-11T08:40:50Z",
      payload: {
        sessionId: "ses_remote_root",
        id: "que_1",
        questions: [{ question: "请选择 A 或 B" }]
      }
    } as RunEvent;

    expect(projectRootInteractionSession(event, "ses_platform_root")).toMatchObject({
      payload: {
        sessionId: "ses_platform_root",
        remoteSessionId: "ses_remote_root"
      }
    });
  });

  it("does not remap child session interaction events", () => {
    const event = {
      eventId: "evt_child_question",
      runId: "run_1",
      seq: 8,
      type: "question.asked",
      traceId: "trace_1",
      occurredAt: "2026-07-11T08:40:50Z",
      payload: { sessionId: "ses_remote_child", isChildSession: true }
    } as RunEvent;

    expect(projectRootInteractionSession(event, "ses_platform_root")).toBe(event);
  });
});

describe("isSupersededInteractionAsk", () => {
  const staleQuestion = {
    eventId: "evt_stale_question",
    runId: "run_1",
    seq: 7,
    type: "question.asked",
    traceId: "trace_1",
    occurredAt: "2026-07-11T08:40:50Z",
    payload: { requestId: "que_stale" }
  } as RunEvent;

  it("ignores only a replayed ask absent from the newer remote pending snapshot", () => {
    expect(isSupersededInteractionAsk(staleQuestion, Date.parse("2026-07-11T08:41:00Z"), new Set())).toBe(true);
    expect(isSupersededInteractionAsk(staleQuestion, Date.parse("2026-07-11T08:41:00Z"), new Set(["que_stale"]))).toBe(false);
    expect(isSupersededInteractionAsk({ ...staleQuestion, occurredAt: "2026-07-11T08:41:01Z" }, Date.parse("2026-07-11T08:41:00Z"), new Set())).toBe(false);
  });
});

describe("assistantSummaryMessageId", () => {
  it("accepts only the stable platform feedback id carried by run.created", () => {
    expect(assistantSummaryMessageId({
      assistantSummaryMessageId: "msg_0123456789abcdef0123456789abcdef"
    })).toBe("msg_0123456789abcdef0123456789abcdef");
    expect(assistantSummaryMessageId({ assistantSummaryMessageId: "msg_remote-opencode" })).toBeUndefined();
    expect(assistantSummaryMessageId({})).toBeUndefined();
  });
});

describe("runEventProjection", () => {
  it("marks snapshot reset and exposes its materialized events in source order", () => {
    const first = {
      eventId: "evt_snapshot_1",
      runId: "run_1",
      seq: 0,
      type: "message.updated",
      traceId: "trace_1",
      occurredAt: "2026-07-10T00:00:00Z",
      payload: { messageId: "msg_1" }
    };
    const second = { ...first, eventId: "evt_snapshot_2", type: "todo.updated" };
    const reset = {
      ...first,
      eventId: "evt_snapshot_reset",
      type: "run.snapshot.reset",
      payload: { snapshot: { barrierSeq: 8, events: [first, second] } }
    };

    expect(runEventProjection(reset)).toEqual({ reset: true, events: [first, second] });
    expect(runEventProjection({ ...reset, payload: {} })).toEqual({ reset: true, events: [] });
    expect(runEventProjection(first)).toEqual({ reset: false, events: [first] });
  });
});

describe("historyItems", () => {
  it("only returns backend sessions and maps workspace context fields", () => {
    const sessions: Session[] = [
      {
        sessionId: "ses_1234567890abcdef",
        workspaceId: "wrk_1234567890abcdef",
        title: "用户历史",
        status: "ACTIVE",
        createdAt: "2026-07-08T09:00:00Z",
        updatedAt: "2026-07-08T10:00:00Z",
        workspaceContext: {
          appId: "app_1234567890abcdef",
          appName: "智能测试平台",
          applicationWorkspaceId: "aw_1234567890abcdef",
          workspaceName: "主干工作区",
          versionId: "ver_1234567890abcdef",
          version: "20260708"
        }
      }
    ];

    expect(historyItems(null, sessions)).toEqual([
      expect.objectContaining({
        id: "ses_1234567890abcdef",
        title: "用户历史",
        appName: "智能测试平台",
        workspaceName: "主干工作区",
        version: "20260708"
      })
    ]);
    expect(historyItems(null, sessions)).not.toContainEqual(expect.objectContaining({ id: "local" }));
  });

  it("merges runtime state and question attention into history items", () => {
    const sessions: Session[] = [
      {
        sessionId: "ses_running",
        workspaceId: "wrk_1",
        title: "运行中历史",
        status: "ACTIVE",
        createdAt: "2026-07-08T09:00:00Z",
        updatedAt: "2026-07-08T10:00:00Z"
      },
      {
        sessionId: "ses_done",
        workspaceId: "wrk_1",
        title: "已完成历史",
        status: "ACTIVE",
        createdAt: "2026-07-08T09:00:00Z",
        updatedAt: "2026-07-08T10:00:00Z"
      }
    ];
    const runtimeState: SessionRuntimeState = {
      sessionId: "ses_running",
      runId: "run_1",
      runStatus: "RUNNING",
      attention: "QUESTION",
      attentionEventId: "evt_1",
      attentionAt: "2026-07-08T10:01:00Z",
      updatedAt: "2026-07-08T10:01:02Z"
    };

    expect(historyItems(null, sessions, { ses_running: runtimeState })).toEqual([
      expect.objectContaining({
        id: "ses_running",
        runtimeState: "running",
        runId: "run_1",
        runStatus: "RUNNING",
        pendingQuestion: true,
        attentionEventId: "evt_1"
      }),
      expect.objectContaining({
        id: "ses_done",
        runtimeState: "completed",
        pendingQuestion: false
      })
    ]);
  });
});

describe("historyRuntimeBadgeCounts", () => {
  it("counts unfinished sessions only from the first history page", () => {
    const sessions: Session[] = Array.from({ length: 31 }, (_, index) => ({
      sessionId: `ses_${index + 1}`,
      workspaceId: "wrk_1",
      title: `历史 ${index + 1}`,
      status: "ACTIVE",
      createdAt: "2026-07-08T09:00:00Z",
      updatedAt: "2026-07-08T10:00:00Z"
    }));
    const runtimeStates = Object.fromEntries(
      sessions.map((session, index) => [
        session.sessionId,
        {
          sessionId: session.sessionId,
          runId: `run_${index + 1}`,
          runStatus: "RUNNING",
          attention: index === 0 || index === 30 ? "QUESTION" : null,
          updatedAt: "2026-07-08T10:01:00Z"
        } satisfies SessionRuntimeState
      ])
    );

    expect(workbenchUtils.historyRuntimeBadgeCounts(sessions, runtimeStates, 30)).toEqual({
      runningCount: 30,
      questionCount: 1
    });
  });
});

describe("opencode readiness helpers", () => {
  it("keeps health, runtime catalog and VCS refresh intervals explicit", () => {
    expect(OPENCODE_HEALTH_REFETCH_INTERVAL_MS).toBe(10_000);
    expect(PUBLIC_CONFIG_GATE_REFETCH_INTERVAL_MS).toBe(5_000);
    expect(OPENCODE_RUNTIME_CAPABILITY_REFETCH_INTERVAL_MS).toBe(300_000);
    expect(OPENCODE_VCS_STATUS_REFETCH_INTERVAL_MS).toBe(30_000);
  });

  it("polls process state before and during rollout so an open page discovers a new gate", () => {
    expect(publicConfigGateRefetchInterval({ messageSendAllowed: false })).toBe(5_000);
    expect(publicConfigGateRefetchInterval({ messageSendAllowed: true })).toBe(5_000);
    expect(publicConfigGateRefetchInterval(null)).toBe(5_000);
  });

  it("builds weak health request only after process assignment is known", () => {
    expect(
      opencodeHealthRequestFromProcess({
        status: "READY",
        initializable: false,
        message: "ready",
        linuxServerId: "server-a",
        containerId: "ctr_01",
        port: 4096,
        checkedAt: "2026-07-06T00:00:00Z"
      })
    ).toEqual({ linuxServerId: "server-a", containerId: "ctr_01", port: 4096 });

    expect(
      opencodeHealthRequestFromProcess({
        status: "READY",
        initializable: false,
        message: "ready",
        linuxServerId: "server-a",
        containerId: "ctr_01",
        checkedAt: "2026-07-06T00:00:00Z"
      })
    ).toBeNull();

    expect(
      opencodeHealthRequestFromProcess({
        status: "READY",
        initializable: false,
        message: "ready",
        linuxServerId: "server-a",
        containerId: "ctr_01",
        port: 0,
        checkedAt: "2026-07-06T00:00:00Z"
      })
    ).toBeNull();
  });

  it("lets processes/me override readiness when it returns", () => {
    expect(
      opencodeAvailabilityFromProcess({
        status: "READY",
        initializable: false,
        message: "ready",
        checkedAt: "2026-07-06T00:00:00Z"
      })
    ).toEqual({ ready: true, source: "process" });

    expect(
      opencodeAvailabilityFromProcess({
        status: "UNAVAILABLE",
        initializable: true,
        message: "unhealthy",
        checkedAt: "2026-07-06T00:00:00Z"
      })
    ).toEqual({ ready: false, source: "process" });
  });

  it("uses weak health as the normal readiness source between processes/me refreshes", () => {
    expect(
      opencodeAvailabilityFromHealth({
        healthy: true,
        status: "HEALTHY",
        serviceStatus: "RUNNING",
        linuxServerId: "server-a",
        containerId: "ctr_01",
        port: 4096,
        checkedAt: "2026-07-06T00:00:00Z",
        message: "ok"
      })
    ).toEqual({ ready: true, source: "health" });

    expect(
      opencodeAvailabilityFromHealth({
        healthy: false,
        status: "UNHEALTHY",
        serviceStatus: "NOT_RUNNING",
        linuxServerId: "server-a",
        containerId: "ctr_01",
        port: 4096,
        checkedAt: "2026-07-06T00:00:10Z",
        message: "HTTP 503"
      })
    ).toEqual({ ready: false, source: "health" });
  });
});

describe("retry status helpers", () => {
  it("starts a replayed retry countdown from the client receipt time", () => {
    const retry = {
      type: "retry",
      retryKey: "evt_retry_1",
      attempt: 1,
      maxAttempts: 3,
      message: "Free usage exceeded, subscribe to Go",
      retryAfterSeconds: 60
    } as const;
    const receivedAt = Date.parse("2026-07-05T11:29:00.000Z");
    const resolved = resolveRetryDeadline({}, retry, receivedAt);

    expect(resolved.deadlineMs).toBe(Date.parse("2026-07-05T11:30:00.000Z"));
    expect(retryCountdownSeconds(retry, receivedAt, resolved.deadlines)).toBe(60);
    expect(retryCountdownSeconds(retry, Date.parse("2026-07-05T11:29:59.000Z"), resolved.deadlines)).toBe(1);
    expect(retryCountdownSeconds(retry, Date.parse("2026-07-05T11:30:00.000Z"), resolved.deadlines)).toBe(0);
  });

  it("reuses an existing retry deadline for the same retry key", () => {
    const retry = {
      type: "retry",
      retryKey: "evt_retry_1",
      attempt: 1,
      maxAttempts: 3,
      retryAfterSeconds: 60
    } as const;
    const deadlines = { evt_retry_1: Date.parse("2026-07-05T11:30:00.000Z") };
    const resolved = resolveRetryDeadline(deadlines, retry, Date.parse("2026-07-05T11:29:30.000Z"));

    expect(resolved.deadlines).toBe(deadlines);
    expect(resolved.deadlineMs).toBe(deadlines.evt_retry_1);
  });

  it("auto-retries the first two expired retry attempts and fails the third", () => {
    const deadlines = { evt_retry_1: Date.parse("2026-07-05T11:30:00.000Z") };
    const now = Date.parse("2026-07-05T11:30:00.000Z");
    const retry = {
      type: "retry",
      retryKey: "evt_retry_1",
      attempt: 1,
      maxAttempts: 3,
      retryAfterSeconds: 60
    } as const;

    expect(retryExpirationDecision(retry, now - 1, deadlines)).toBe("wait");
    expect(retryExpirationDecision(retry, now, deadlines)).toBe("retry");
    expect(retryExpirationDecision({ ...retry, attempt: 2 }, now, deadlines)).toBe("retry");
    expect(retryExpirationDecision({ ...retry, attempt: 3 }, now, deadlines)).toBe("fail");
    expect(shouldFailExhaustedRetry({ ...retry, attempt: 3 }, now, deadlines)).toBe(true);
  });
});

describe("auto retry run helpers", () => {
  it("prepares a new run from the last run draft and cancels the current busy run", () => {
    const parts: PromptPart[] = [{ type: "text", text: "继续执行" }];
    const currentRun: Run = {
      runId: "run_old",
      sessionId: "ses_1",
      workspaceId: "wrk_1",
      status: "RUNNING",
      createdAt: "2026-07-05T11:00:00Z",
      updatedAt: "2026-07-05T11:00:00Z"
    };
    const draft = {
      prompt: "继续执行",
      parts,
      userMessageId: "msg_user_retry",
      title: "继续执行",
      command: { command: "skill", arguments: "frontend" }
    };

    expect(prepareAutoRetryRun(currentRun, draft, "2026-07-05T11:01:00.000Z")).toEqual({
      type: "start",
      input: draft,
      cancelRunId: "run_old",
      localRun: { ...currentRun, status: "CANCELLED", updatedAt: "2026-07-05T11:01:00.000Z" }
    });
  });

  it("fails auto retry when the previous run draft is missing", () => {
    expect(prepareAutoRetryRun(null, null, "2026-07-05T11:01:00.000Z")).toEqual({
      type: "missing-draft"
    });
  });
});

describe("diffFilesFromPayload", () => {
  it("reads file objects from payload.files", () => {
    expect(diffFilesFromPayload({ files: [{ path: "a.ts", additions: 1, deletions: 0 }] })).toEqual([
      { path: "a.ts", patch: "", additions: 1, deletions: 0, status: "modified" }
    ]);
  });

  it("falls back to payload.diff when files missing", () => {
    expect(diffFilesFromPayload({ diff: [{ path: "a.ts" }] })).toEqual([
      { path: "a.ts", patch: "", additions: 0, deletions: 0, status: "modified" }
    ]);
  });

  it("filters out items with empty path", () => {
    expect(diffFilesFromPayload({ files: [{ path: "" }, { path: "b.ts" }] })).toEqual([
      { path: "b.ts", patch: "", additions: 0, deletions: 0, status: "modified" }
    ]);
  });

  it("normalizes opencode session.diff string path arrays", () => {
    // opencode session.diff 事件的 payload.files 是 path 字符串数组，
    // 需要包装成 RunDiffFile 对象才能在 mergeDiffFiles 中按 path 去重累加。
    expect(diffFilesFromPayload({ files: ["a.ts", "b.ts", ""] })).toEqual([
      { path: "a.ts", patch: "", additions: 0, deletions: 0, status: "modified" },
      { path: "b.ts", patch: "", additions: 0, deletions: 0, status: "modified" }
    ]);
  });

  it("mixes string paths and file objects in the same array", () => {
    expect(
      diffFilesFromPayload({ files: ["c.ts", { path: "a.ts", additions: 2, deletions: 1, status: "modified" }] })
    ).toEqual([
      { path: "c.ts", patch: "", additions: 0, deletions: 0, status: "modified" },
      { path: "a.ts", patch: "", additions: 2, deletions: 1, status: "modified" }
    ]);
  });
});

describe("mergeDiffFiles", () => {
  it("appends new paths to the tail of the list", () => {
    const current = [file("a.ts", 1, 0)];
    const incoming = [file("b.ts", 2, 1)];
    expect(mergeDiffFiles(current, incoming)).toEqual([
      file("a.ts", 1, 0),
      file("b.ts", 2, 1)
    ]);
  });

  it("overwrites an existing path with the newest file object", () => {
    const current = [file("a.ts", 1, 0), file("b.ts", 2, 1)];
    const incoming = [file("a.ts", 9, 4)];
    expect(mergeDiffFiles(current, incoming)).toEqual([
      file("a.ts", 9, 4),
      file("b.ts", 2, 1)
    ]);
  });

  it("returns the original list when incoming is empty", () => {
    const current = [file("a.ts", 1, 0)];
    expect(mergeDiffFiles(current, [])).toBe(current);
  });

  it("preserves original entry order on overwrite", () => {
    const current = [file("a.ts", 1, 0), file("b.ts", 2, 0), file("c.ts", 3, 0)];
    const incoming = [file("b.ts", 5, 1), file("d.ts", 0, 0)];
    expect(mergeDiffFiles(current, incoming)).toEqual([
      file("a.ts", 1, 0),
      file("b.ts", 5, 1),
      file("c.ts", 3, 0),
      file("d.ts", 0, 0)
    ]);
  });

  it("deduplicates already-normalized paths in different forms", () => {
    // 调用方（AgentWorkbench.applyToolChangeToDiff / diff.proposed 事件处理）已用
    // normalizeWorkspacePath 把 Windows 绝对路径、a/ b/ 前缀等归一化为同一种相对路径形态，
    // mergeDiffFiles 的职责是把这些"已归一化"路径再按形态 key 折叠，避免大小写、反斜杠残留造成重复。
    // 这里模拟两条来源的同一文件（已被调用方归一化），验证它们合并为一条。
    const current = [{ ...file("src/App.vue", 1, 0), patch: "old patch" }];
    const incoming = [file("src/app.vue", 2, 1)];
    const merged = mergeDiffFiles(current, incoming);
    expect(merged).toHaveLength(1);
    expect(merged[0]).toEqual({ path: "src/app.vue", patch: "", additions: 2, deletions: 1, status: "modified" });
  });

  it("collapses mixed backslash / forward-slash forms into a single key", () => {
    // 模拟调用方未做 separator 折叠时的边缘场景：mergeDiffFiles 自身也要兜底
    // 让 "src\\App.vue" 与 "src/App.vue" 落到同一个 key。
    const merged = mergeDiffFiles([file("src\\App.vue", 1, 0)], [file("src/App.vue", 2, 1)]);
    expect(merged).toHaveLength(1);
    expect(merged[0]).toEqual(file("src/App.vue", 2, 1));
  });

  it("deduplicates git a/ b/ prefixes as the same path", () => {
    const current = [file("a/src/App.vue", 1, 0)];
    const incoming = [file("b/src/App.vue", 3, 1)];
    expect(mergeDiffFiles(current, incoming)).toHaveLength(1);
    expect(mergeDiffFiles(current, incoming)[0]).toEqual(file("b/src/App.vue", 3, 1));
  });

  it("is case-insensitive on the path key for Windows file systems", () => {
    expect(mergeDiffFiles([file("SRC/App.vue", 1, 0)], [file("src/app.vue", 2, 1)])).toHaveLength(1);
  });
});

describe("normalizePathKey", () => {
  it("strips git a/ b/ prefixes and folds separators", () => {
    expect(normalizePathKey("a/src/App.vue")).toBe("src/app.vue");
    expect(normalizePathKey("b\\src\\App.vue")).toBe("src/app.vue");
  });
  it("removes trailing slashes and collapses repeated slashes", () => {
    expect(normalizePathKey("./src//App.vue/")).toBe("src/app.vue");
  });
  it("returns empty string for falsy input", () => {
    expect(normalizePathKey("")).toBe("");
    expect(normalizePathKey(undefined)).toBe("");
  });
});

describe("nextCenterModeAfterVcsRefresh", () => {
  it("leaves the VCS diff panel when the refreshed diff list becomes empty", () => {
    expect(nextCenterModeAfterVcsRefresh("diff", "vcs", [])).toBe("editor");
  });

  it("keeps the diff panel when VCS still has files or another source is active", () => {
    expect(nextCenterModeAfterVcsRefresh("diff", "vcs", [file("src/App.vue", 1, 0)])).toBe("diff");
    expect(nextCenterModeAfterVcsRefresh("diff", "run", [])).toBe("diff");
    expect(nextCenterModeAfterVcsRefresh("editor", "vcs", [])).toBe("editor");
  });
});

describe("nextCenterModeAfterRunDiff", () => {
  it("does not let live run diffs hijack an open VCS diff panel", () => {
    expect(nextCenterModeAfterRunDiff("diff", "vcs")).toBe("editor");
    expect(nextCenterModeAfterRunDiff("diff", "agent")).toBe("editor");
  });

  it("keeps current mode when already showing run diff or not showing a diff panel", () => {
    expect(nextCenterModeAfterRunDiff("diff", "run")).toBe("diff");
    expect(nextCenterModeAfterRunDiff("editor", "vcs")).toBe("editor");
    expect(nextCenterModeAfterRunDiff("system", "vcs")).toBe("system");
  });
});

describe("historical session restoration", () => {
  it("deduplicates persisted history rows before rendering", () => {
    const messages: SessionMessage[] = [
      {
        messageId: "msg_platform_1",
        remoteMessageId: "remote_assistant_1",
        sessionId: "ses_1",
        role: "ASSISTANT",
        content: "重复回答",
        createdAt: "2026-06-28T08:00:00Z"
      },
      {
        messageId: "msg_platform_2",
        remoteMessageId: "remote_assistant_1",
        sessionId: "ses_1",
        role: "ASSISTANT",
        content: "重复回答",
        createdAt: "2026-06-28T08:00:01Z"
      },
      {
        messageId: "msg_platform_3",
        sessionId: "ses_1",
        role: "ASSISTANT",
        content: "缺少远端 ID 的重复回答",
        createdAt: "2026-06-28T08:00:02Z"
      },
      {
        messageId: "msg_platform_4",
        sessionId: "ses_1",
        role: "ASSISTANT",
        content: "缺少远端 ID 的重复回答",
        createdAt: "2026-06-28T08:00:03Z"
      }
    ];

    expect(dedupeSessionMessages(messages).map((message) => message.messageId)).toEqual(["msg_platform_1", "msg_platform_3"]);
  });

  it("restores session tree snapshots through RunEvent reducer without duplicate messages", () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: [
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          parentSessionId: undefined,
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            message: { id: "remote_msg_1", role: "assistant", content: "主回答" }
          }
        },
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          parentSessionId: undefined,
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            message: { id: "remote_msg_1", role: "assistant", content: "主回答" }
          }
        },
        {
          type: "message.part.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          parentSessionId: undefined,
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            messageId: "remote_msg_1",
            part: { id: "part_task", type: "tool", tool: "task", state: { status: "completed", input: { description: "child" } } }
          }
        },
        {
          type: "message.part.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          parentSessionId: undefined,
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            messageId: "remote_msg_1",
            part: { id: "part_task", type: "tool", tool: "task", state: { status: "completed", input: { description: "child" } } }
          }
        }
      ]
    };

    const mapped = messagesFromSessionTreeSnapshot(snapshot);

    expect(mapped).toHaveLength(1);
    expect(mapped[0]).toMatchObject({
      role: "assistant",
      text: "主回答",
      parts: [{ partId: "part_task", type: "tool", toolName: "task" }]
    });
  });

  it("keeps OpenCode file source on the original user message instead of rendering it as a second message", () => {
    const userMessage = {
      rootSessionId: "ses_root",
      sessionId: "ses_root",
      message: { id: "msg_user", role: "user" }
    };
    const userText = {
      rootSessionId: "ses_root",
      sessionId: "ses_root",
      messageId: "msg_user",
      part: { id: "prt_text", messageID: "msg_user", type: "text", text: "这个子条目干了什么" }
    };
    const syntheticReadText = {
      rootSessionId: "ses_root",
      sessionId: "ses_root",
      messageId: "msg_user",
      part: {
        id: "prt_synthetic",
        messageID: "msg_user",
        type: "text",
        synthetic: true,
        text: 'Called the Read tool with the following input: {"filePath":"详细设计说明书.md"}'
      }
    };
    const userFile = {
      rootSessionId: "ses_root",
      sessionId: "ses_root",
      messageId: "msg_user",
      part: {
        id: "prt_file",
        messageID: "msg_user",
        type: "file",
        mime: "text/markdown",
        filename: "详细设计说明书.md",
        url: "file://详细设计说明书.md",
        source: {
          type: "file",
          path: "02-设计/子条目/开发文档/详细设计说明书.md",
          text: { value: "# 详细设计说明书\n这里是只供模型读取的完整原文。", start: 0, end: 26 }
        }
      }
    };
    const assistantMessage = {
      rootSessionId: "ses_root",
      sessionId: "ses_root",
      message: { id: "msg_assistant", role: "assistant" }
    };
    const assistantText = {
      rootSessionId: "ses_root",
      sessionId: "ses_root",
      messageId: "msg_assistant",
      part: { id: "prt_answer", messageID: "msg_assistant", type: "text", text: "该子条目完成了详细设计。" }
    };
    const payloads = [userMessage, userText, syntheticReadText, userFile, assistantMessage, assistantText];
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_platform",
      sessions: [{ rootSessionId: "ses_root", sessionId: "ses_root", childSession: false }],
      messagesBySessionId: { ses_root: payloads },
      childSessionIdByTaskPartId: {},
      events: payloads.map((payload) => ({
        type: "message" in payload ? "message.updated" : "message.part.updated",
        rootSessionId: "ses_root",
        sessionId: "ses_root",
        childSession: false,
        payload
      }))
    };

    const state = chatStateFromSessionTreeSnapshot(snapshot);

    expect(state.messages).toHaveLength(2);
    expect(state.messages[0]).toMatchObject({
      id: "msg_user",
      role: "user",
      text: "这个子条目干了什么"
    });
    expect(state.messages[0]?.role === "user" ? state.messages[0].parts : []).toEqual([
      { type: "text", text: "这个子条目干了什么" },
      expect.objectContaining({
        type: "file",
        name: "详细设计说明书.md",
        path: "02-设计/子条目/开发文档/详细设计说明书.md",
        source: expect.objectContaining({ text: "# 详细设计说明书\n这里是只供模型读取的完整原文。" })
      })
    ]);
    expect(state.messages[1]).toMatchObject({
      id: "msg_assistant",
      role: "assistant",
      text: "该子条目完成了详细设计。"
    });
    expect(state.messages.some((message) => message.role === "user" && message.text.includes("完整原文"))).toBe(false);
  });

  it("keeps multi-turn user text parts out of the previous assistant message", () => {
    const remoteSessionId = "ses_opencode_root";
    const turns = [
      {
        prompt: "第一次提问，不用调用任何技能，直接输入回答1",
        answer: "1",
        reasoning: "直接按要求回答 1。",
        platformUserId: "msg_11111111111111111111111111111111",
        platformAssistantId: "msg_22222222222222222222222222222222",
        remoteUserId: "msg_remote_user_1",
        remoteAssistantId: "msg_remote_assistant_1"
      },
      {
        prompt: "第二次提问，不用调用任何技能，直接输出：回答2",
        answer: "回答2",
        reasoning: "直接按要求回答 2。",
        platformUserId: "msg_33333333333333333333333333333333",
        platformAssistantId: "msg_44444444444444444444444444444444",
        remoteUserId: "msg_remote_user_2",
        remoteAssistantId: "msg_remote_assistant_2"
      },
      {
        prompt: "第三次提问，不用调用任何技能，直接输出：回答三",
        answer: "回答三",
        reasoning: "直接按要求回答三。",
        platformUserId: "msg_55555555555555555555555555555555",
        platformAssistantId: "msg_66666666666666666666666666666666",
        remoteUserId: "msg_remote_user_3",
        remoteAssistantId: "msg_remote_assistant_3"
      }
    ];
    // 夹具保持真实 OpenCode 顺序：无正文 message envelope 后紧跟独立 text/reasoning part。
    const payloads: Record<string, unknown>[] = turns.flatMap((turn, index) => [
      {
        rootSessionId: remoteSessionId,
        sessionId: remoteSessionId,
        message: {
          id: turn.remoteUserId,
          messageID: turn.remoteUserId,
          sessionID: remoteSessionId,
          role: "user"
        }
      },
      {
        rootSessionId: remoteSessionId,
        sessionId: remoteSessionId,
        messageId: turn.remoteUserId,
        messageID: turn.remoteUserId,
        part: {
          id: `prt_user_text_${index + 1}`,
          partID: `prt_user_text_${index + 1}`,
          messageId: turn.remoteUserId,
          messageID: turn.remoteUserId,
          sessionID: remoteSessionId,
          type: "text",
          text: turn.prompt
        }
      },
      {
        rootSessionId: remoteSessionId,
        sessionId: remoteSessionId,
        message: {
          id: turn.remoteAssistantId,
          messageID: turn.remoteAssistantId,
          sessionID: remoteSessionId,
          role: "assistant"
        }
      },
      {
        rootSessionId: remoteSessionId,
        sessionId: remoteSessionId,
        messageId: turn.remoteAssistantId,
        messageID: turn.remoteAssistantId,
        part: {
          id: `prt_reasoning_${index + 1}`,
          partID: `prt_reasoning_${index + 1}`,
          messageId: turn.remoteAssistantId,
          messageID: turn.remoteAssistantId,
          sessionID: remoteSessionId,
          type: "reasoning",
          text: turn.reasoning
        }
      },
      {
        rootSessionId: remoteSessionId,
        sessionId: remoteSessionId,
        messageId: turn.remoteAssistantId,
        messageID: turn.remoteAssistantId,
        part: {
          id: `prt_answer_${index + 1}`,
          partID: `prt_answer_${index + 1}`,
          messageId: turn.remoteAssistantId,
          messageID: turn.remoteAssistantId,
          sessionID: remoteSessionId,
          type: "text",
          text: turn.answer
        }
      }
    ]);
    const persistedMessages: SessionMessage[] = turns.flatMap((turn, index) => [
      {
        messageId: turn.platformUserId,
        sessionId: "ses_platform",
        role: "USER",
        content: turn.prompt,
        createdAt: `2026-07-15T00:0${index * 2}:00Z`
      },
      {
        messageId: turn.platformAssistantId,
        remoteMessageId: turn.remoteAssistantId,
        sessionId: "ses_platform",
        role: "ASSISTANT",
        content: turn.answer,
        createdAt: `2026-07-15T00:0${index * 2 + 1}:00Z`
      }
    ]);
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_platform",
      sessions: [{ rootSessionId: remoteSessionId, sessionId: remoteSessionId, childSession: false }],
      messagesBySessionId: { [remoteSessionId]: payloads },
      childSessionIdByTaskPartId: {},
      events: payloads.map((payload) => ({
        type: "message" in payload ? "message.updated" : "message.part.updated",
        rootSessionId: remoteSessionId,
        sessionId: remoteSessionId,
        childSession: false,
        payload
      }))
    };

    const state = chatStateFromSessionTreeSnapshot(snapshot, persistedMessages);

    expect(state.messages).toHaveLength(6);
    const conversationMessages = state.messages.filter(
      (message): message is Extract<AgentMessage, { role: "user" | "assistant" }> =>
        message.role === "user" || message.role === "assistant"
    );
    expect(conversationMessages.map((message) => [message.role, message.text])).toEqual(
      turns.flatMap((turn) => [["user", turn.prompt], ["assistant", turn.answer]])
    );
    const userMessages = conversationMessages.filter(
      (message): message is Extract<AgentMessage, { role: "user" }> => message.role === "user"
    );
    expect(userMessages.map((message) => message.messageId)).toEqual(turns.map((turn) => turn.platformUserId));
    expect(userMessages.map((message) => message.remoteMessageId)).toEqual(turns.map((turn) => turn.remoteUserId));

    const assistantMessages = conversationMessages.filter(
      (message): message is Extract<AgentMessage, { role: "assistant" }> => message.role === "assistant"
    );
    expect(assistantMessages.map((message) => message.remoteMessageId)).toEqual(turns.map((turn) => turn.remoteAssistantId));
    expect(
      assistantMessages.map((message) =>
        (message.parts ?? []).filter((part) => part.type === "reasoning").map((part) => part.text)
      )
    ).toEqual(turns.map((turn) => [turn.reasoning]));
    expect(
      assistantMessages.map((message) => (message.parts ?? []).map((part) => part.partId))
    ).toEqual(turns.map((_, index) => [`prt_reasoning_${index + 1}`, `prt_answer_${index + 1}`]));
    const assistantPartTexts = assistantMessages.flatMap((message) =>
      (message.parts ?? []).flatMap((part) => part.type === "text" || part.type === "reasoning" ? [part.text] : [])
    );
    turns.forEach((turn) => expect(assistantPartTexts).not.toContain(turn.prompt));
  });

  it("restores subagent indexes from snapshot sessions when discovery events are absent", () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [
        { rootSessionId: "ses_root", sessionId: "ses_root", childSession: false },
        {
          rootSessionId: "ses_root",
          sessionId: "ses_child",
          parentSessionId: "ses_root",
          childSession: true,
          taskMessageId: "msg_root",
          taskPartId: "prt_task",
          taskCallId: "call_task"
        }
      ],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: { prt_task: "ses_child" },
      events: [
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            message: { id: "msg_root", role: "assistant" }
          }
        },
        {
          type: "message.part.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            messageID: "msg_root",
            part: {
              id: "prt_task",
              messageID: "msg_root",
              type: "tool",
              tool: "task",
              callID: "call_task",
              state: {
                status: "completed",
                input: { description: "Explore project structure", subagent_type: "explore" }
              }
            }
          }
        },
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_child",
          parentSessionId: "ses_root",
          childSession: true,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_child",
            parentSessionId: "ses_root",
            isChildSession: true,
            taskMessageId: "msg_root",
            taskPartId: "prt_task",
            taskCallId: "call_task",
            message: { id: "msg_child", role: "assistant", content: "子 Agent 输出" }
          }
        }
      ]
    };

    const state = chatStateFromSessionTreeSnapshot(snapshot);

    expect(state.subagentByTaskPartId.prt_task).toBe("ses_child");
    expect(state.subagentsBySessionId.ses_child).toMatchObject({
      sessionId: "ses_child",
      parentSessionId: "ses_root",
      taskMessageId: "msg_root",
      taskPartId: "prt_task",
      taskCallId: "call_task",
      agentName: "Explore",
      title: "Explore project structure",
      status: "completed"
    });
  });

  it("restores historical subagent binding when snapshot taskPartId differs from rendered task part id", () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [
        { rootSessionId: "ses_root", sessionId: "ses_root", childSession: false },
        {
          rootSessionId: "ses_root",
          sessionId: "ses_child",
          parentSessionId: "ses_root",
          childSession: true,
          taskMessageId: "msg_root",
          taskPartId: "toolu_snapshot_task",
          taskCallId: "call_task"
        }
      ],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: { toolu_snapshot_task: "ses_child" },
      events: [
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            message: { id: "msg_root", role: "assistant" }
          }
        },
        {
          type: "message.part.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            messageID: "msg_root",
            part: {
              id: "prt_rendered_task",
              messageID: "msg_root",
              type: "tool",
              tool: "task",
              callID: "call_task",
              metadata: { agent: "build", title: "构建回归用例" },
              state: {
                status: "completed",
                input: { description: "构建回归用例" }
              }
            }
          }
        }
      ]
    };

    const state = chatStateFromSessionTreeSnapshot(snapshot);

    expect(state.subagentByTaskPartId.toolu_snapshot_task).toBe("ses_child");
    expect(state.subagentByTaskPartId.prt_rendered_task).toBe("ses_child");
    expect(state.subagentByTaskPartId.call_task).toBe("ses_child");
    expect(state.subagentsBySessionId.ses_child).toMatchObject({
      taskPartId: "prt_rendered_task",
      taskCallId: "call_task",
      agentName: "Build",
      title: "构建回归用例",
      status: "completed"
    });
  });

  it("restores subagent output from persisted task parts when the session tree is empty", () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: []
    };
    const persisted: SessionMessage[] = [
      {
        messageId: "msg_root",
        sessionId: "ses_platform",
        role: "ASSISTANT",
        content: "",
        createdAt: "2026-07-05T13:14:00Z",
        parts: [
          {
            partId: "prt_task",
            type: "tool",
            toolName: "task",
            callId: "call_task",
            status: "completed",
            input: { description: "分析 I2026000 测试设计对象", subagent_type: "test-design-analysis" },
            metadata: { sessionId: "ses_child" },
            output: "<task id=\"ses_child\" state=\"completed\"><task_result>\n## 子 Agent 工作内容\n识别完成。\n</task_result></task>"
          }
        ]
      }
    ];

    const state = chatStateFromSessionTreeSnapshot(snapshot, persisted);
    const childMessage = state.messages.find((message) => message.id === "task-output:ses_child:prt_task");

    expect(state.subagentsBySessionId.ses_child).toMatchObject({
      sessionId: "ses_child",
      taskMessageId: "msg_root",
      taskPartId: "prt_task",
      taskCallId: "call_task",
      agentName: "Test-design-analysis",
      title: "分析 I2026000 测试设计对象",
      status: "completed"
    });
    expect(state.subagentByTaskPartId.prt_task).toBe("ses_child");
    expect(state.subagentByTaskPartId.call_task).toBe("ses_child");
    expect(childMessage).toMatchObject({
      role: "assistant",
      text: "## 子 Agent 工作内容\n识别完成。"
    });
    expect(state.messageScopesById["task-output:ses_child:prt_task"]).toMatchObject({
      sessionId: "ses_child",
      isChildSession: true,
      taskMessageId: "msg_root",
      taskPartId: "prt_task",
      taskCallId: "call_task"
    });
  });

  it("replays child messages from session tree messagesBySessionId", () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [
        { rootSessionId: "ses_root", sessionId: "ses_root", childSession: false },
        {
          rootSessionId: "ses_root",
          sessionId: "ses_child",
          parentSessionId: "ses_root",
          childSession: true,
          taskMessageId: "msg_root",
          taskPartId: "prt_task",
          taskCallId: "call_task"
        }
      ],
      messagesBySessionId: {
        ses_child: [
          {
            rootSessionId: "ses_root",
            sessionId: "ses_child",
            parentSessionId: "ses_root",
            isChildSession: true,
            taskMessageId: "msg_root",
            taskPartId: "prt_task",
            taskCallId: "call_task",
            message: { id: "msg_child", role: "assistant", content: "子 Agent messagesBySessionId 输出" }
          }
        ]
      },
      childSessionIdByTaskPartId: { prt_task: "ses_child" },
      events: [
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            message: { id: "msg_root", role: "assistant" }
          }
        },
        {
          type: "message.part.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            messageID: "msg_root",
            part: {
              id: "prt_task",
              messageID: "msg_root",
              type: "tool",
              tool: "task",
              callID: "call_task",
              state: {
                status: "completed",
                input: { description: "分析 I2026000 测试设计对象", subagent_type: "test-design-analysis" }
              }
            }
          }
        }
      ]
    };

    const state = chatStateFromSessionTreeSnapshot(snapshot);

    expect(state.messages).toEqual(expect.arrayContaining([
      expect.objectContaining({
        id: "msg_child",
        role: "assistant",
        text: "子 Agent messagesBySessionId 输出"
      })
    ]));
    expect(state.messageScopesById.msg_child).toMatchObject({
      sessionId: "ses_child",
      isChildSession: true,
      taskPartId: "prt_task",
      taskCallId: "call_task"
    });
  });

  it("replays child-only part entries so a historical subagent card does not open an empty timeline", () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [
        { rootSessionId: "ses_root", sessionId: "ses_root", childSession: false },
        {
          rootSessionId: "ses_root",
          sessionId: "ses_child",
          parentSessionId: "ses_root",
          childSession: true,
          taskMessageId: "msg_root",
          taskPartId: "prt_task",
          taskCallId: "call_task"
        }
      ],
      messagesBySessionId: {
        ses_child: [
          {
            rootSessionId: "ses_root",
            sessionId: "ses_child",
            parentSessionId: "ses_root",
            isChildSession: true,
            taskMessageId: "msg_root",
            taskPartId: "prt_task",
            taskCallId: "call_task",
            part: {
              id: "prt_child_text",
              messageID: "msg_child",
              type: "text",
              text: "子 Agent 仅 part 回放的工作内容"
            }
          }
        ]
      },
      childSessionIdByTaskPartId: { prt_task: "ses_child" },
      events: []
    };

    const state = chatStateFromSessionTreeSnapshot(snapshot);
    const childMessage = state.messages.find((message) => message.id === "msg_child");

    expect(childMessage).toMatchObject({
      role: "assistant",
      text: "子 Agent 仅 part 回放的工作内容",
      parts: [{ partId: "prt_child_text", type: "text", text: "子 Agent 仅 part 回放的工作内容" }]
    });
    expect(state.messageScopesById.msg_child).toMatchObject({
      sessionId: "ses_child",
      isChildSession: true,
      taskPartId: "prt_task",
      taskCallId: "call_task"
    });
  });

  it("keeps persisted user turns when session tree supplies assistant snapshots", () => {
    const persisted: SessionMessage[] = [
      {
        messageId: "msg_user",
        sessionId: "ses_root",
        role: "USER",
        content: "请生成登录测试报告",
        createdAt: "2026-06-28T08:00:00Z"
      },
      {
        messageId: "msg_assistant",
        sessionId: "ses_root",
        role: "ASSISTANT",
        content: "测试报告已生成",
        createdAt: "2026-06-28T08:01:00Z"
      }
    ];
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: [
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            message: { id: "remote_assistant", role: "assistant", content: "测试报告已生成" }
          }
        },
        {
          type: "message.part.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            messageId: "remote_assistant",
            part: { id: "part_file", type: "file", name: "登录测试报告.md", path: "docs/登录测试报告.md" }
          }
        }
      ]
    };

    const state = chatStateFromSessionTreeSnapshot(snapshot, persisted);

    expect(state.messages).toHaveLength(2);
    expect(state.messages[0]).toMatchObject({ role: "user", text: "请生成登录测试报告" });
    expect(state.messages[1]).toMatchObject({
      role: "assistant",
      text: "测试报告已生成",
      parts: [{ partId: "part_file", type: "file", path: "docs/登录测试报告.md" }]
    });
  });

  it("keeps user file parts from session tree when persisted user message has no parts", () => {
    const persisted: SessionMessage[] = [
      {
        messageId: "msg_user_platform",
        sessionId: "ses_root",
        role: "USER",
        content: "这个文件里有什么内容",
        createdAt: "2026-07-06T08:00:00Z"
      },
      {
        messageId: "msg_assistant_platform",
        sessionId: "ses_root",
        role: "ASSISTANT",
        content: "文件内容如下",
        createdAt: "2026-07-06T08:01:00Z"
      }
    ];
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: [
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            message: { id: "remote_user", role: "user", content: "这个文件里有什么内容" }
          }
        },
        {
          type: "message.part.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            messageID: "remote_user",
            part: {
              id: "part_file",
              messageID: "remote_user",
              type: "file",
              filename: "冲突文件.md",
              url: "data:text/plain;base64,LS0t",
              source: {
                path: "99-测试数据/Git冲突处理/冲突文件.md",
                contextType: "file"
              }
            }
          }
        },
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            rootSessionId: "ses_root",
            sessionId: "ses_root",
            message: { id: "remote_assistant", role: "assistant", content: "文件内容如下" }
          }
        }
      ]
    };

    const state = chatStateFromSessionTreeSnapshot(snapshot, persisted);

    expect(state.messages).toHaveLength(2);
    expect(state.messages[0]).toMatchObject({
      role: "user",
      messageId: "msg_user_platform",
      text: "这个文件里有什么内容",
      parts: [
        {
          type: "file",
          path: "99-测试数据/Git冲突处理/冲突文件.md",
          name: "冲突文件.md",
          url: "data:text/plain;base64,LS0t",
          source: { contextType: "file", path: "99-测试数据/Git冲突处理/冲突文件.md" }
        }
      ]
    });
  });

  it("normalizes raw opencode parts and restores generated documents", () => {
    const messages = [
      {
        messageId: "msg_1",
        sessionId: "ses_1",
        role: "ASSISTANT",
        content: "测试文档已生成",
        createdAt: "2026-06-28T08:00:00Z",
        parts: [
          {
            id: "part_write",
            messageID: "msg_1",
            type: "tool",
            tool: "write",
            state: {
              status: "completed",
              input: {
                filePath: "/workspace/docs/登录测试报告.md",
                content: "# 登录测试报告"
              }
            }
          }
        ] as never
      }
    ];

    const mapped = messagesFromSessionMessages(messages);
    expect(mapped[0]).toMatchObject({
      role: "assistant",
      parts: [
        {
          partId: "part_write",
          type: "tool",
          toolName: "write",
          status: "completed",
          input: {
            filePath: "/workspace/docs/登录测试报告.md",
            content: "# 登录测试报告"
          }
        }
      ]
    });
    expect(diffFilesFromSessionMessages(messages)).toMatchObject([
      {
        path: "/workspace/docs/登录测试报告.md",
        additions: 1,
        status: "added"
      }
    ]);
  });

  it("keeps native opencode file parts when restoring user messages", () => {
    const mapped = messagesFromSessionMessages([
      {
        messageId: "msg_user",
        sessionId: "ses_1",
        role: "USER",
        content: "what in this",
        createdAt: "2026-07-06T08:00:00Z",
        parts: [
          {
            id: "part_text",
            type: "text",
            text: "what in this"
          },
          {
            id: "part_synthetic",
            type: "text",
            synthetic: true,
            text: "Called the Read tool"
          },
          {
            id: "part_file",
            type: "file",
            filename: "CLAUDE.md",
            mime: "text/plain",
            url: "data:text/plain;base64,IyBDbGF1ZGU="
          }
        ] as never
      }
    ]);

    expect(mapped[0]).toMatchObject({
      role: "user",
      text: "what in this",
      parts: [
        { type: "text", text: "what in this" },
        {
          type: "file",
          name: "CLAUDE.md",
          mimeType: "text/plain",
          url: "data:text/plain;base64,IyBDbGF1ZGU="
        }
      ]
    });
  });

  it("keeps run ids on restored user and assistant messages", () => {
    const mapped = messagesFromSessionMessages([
      {
        messageId: "msg_user_run",
        sessionId: "ses_run",
        runId: "run_restore",
        role: "USER",
        content: "整轮问题",
        createdAt: "2026-07-15T10:00:00Z"
      },
      {
        messageId: "msg_assistant_run",
        sessionId: "ses_run",
        runId: "run_restore",
        role: "ASSISTANT",
        content: "整轮回答",
        createdAt: "2026-07-15T10:01:00Z"
      }
    ]);

    expect(mapped).toEqual([
      expect.objectContaining({ role: "user", runId: "run_restore" }),
      expect.objectContaining({ role: "assistant", runId: "run_restore" })
    ]);
  });

  it("keeps platform and remote message ids when restoring assistant messages", () => {
    const mapped = messagesFromSessionMessages([
      {
        messageId: "msg_11111111111111111111111111111111",
        remoteMessageId: "msg_f2d478d96001861rLCyXjYqf75",
        sessionId: "ses_1",
        role: "ASSISTANT",
        content: "已完成",
        createdAt: "2026-06-28T08:00:00Z"
      }
    ]);

    expect(mapped[0]).toMatchObject({
      id: "msg_11111111111111111111111111111111",
      messageId: "msg_11111111111111111111111111111111",
      platformMessageId: "msg_11111111111111111111111111111111",
      remoteMessageId: "msg_f2d478d96001861rLCyXjYqf75",
      role: "assistant"
    });
  });

  it("uses the first non-empty line as the session title", () => {
    expect(sessionTitleFromFirstMessage("  请生成登录测试案例  ")).toBe("请生成登录测试案例");
    expect(sessionTitleFromFirstMessage(" \n\n  第一行标题  \n第二行不进入标题")).toBe("第一行标题");
  });

  it("truncates long session titles", () => {
    const longTitle = "a".repeat(80);

    expect(sessionTitleFromFirstMessage(longTitle)).toBe(`${"a".repeat(69)}...`);
  });
});

describe("platformSessionTitleFromSynchronizedEventPayload", () => {
  it("reads and trims a title only after the platform persistence marker", () => {
    expect(
      platformSessionTitleFromSynchronizedEventPayload({
        platformSessionTitleSynchronized: true,
        platformSessionTitle: "  已持久化的标题  "
      })
    ).toBe("已持久化的标题");
  });

  it("rejects missing or false persistence markers instead of falling back to OpenCode payloads", () => {
    expect(
      platformSessionTitleFromSynchronizedEventPayload({
        platformSessionTitle: "不应乐观更新",
        info: { title: "原始标题" },
        rawPayload: { properties: { info: { title: "原始包装标题" } } }
      })
    ).toBe("");
    expect(
      platformSessionTitleFromSynchronizedEventPayload({
        platformSessionTitleSynchronized: false,
        platformSessionTitle: "不应乐观更新"
      })
    ).toBe("");
  });

  it("returns an empty title when the synchronized title is blank or missing", () => {
    expect(
      platformSessionTitleFromSynchronizedEventPayload({
        platformSessionTitleSynchronized: true,
        platformSessionTitle: " \n "
      })
    ).toBe("");
    expect(platformSessionTitleFromSynchronizedEventPayload({ platformSessionTitleSynchronized: true })).toBe("");
  });
});

describe("inferDiffFromToolPart", () => {
  it("counts new file lines for write tool and synthesizes a unified diff", () => {
    // write 工具完成时，前端应能基于 input.content 估算 additions，让"文件变更"卡片显示 +N。
    // 同时合成 unified diff 文本，让"文件变更抽屉"右侧能渲染为全 +N 行的视图，
    // 避免点击后展示"暂无 diff 内容"的空态。
    const result = inferDiffFromToolPart(
      toolPart({ filePath: "/tmp/demo/src/new.ts", content: "export const a = 1\nexport const b = 2\nexport const c = 3" })
    );
    expect(result).toMatchObject({
      path: "/tmp/demo/src/new.ts",
      additions: 3,
      deletions: 0,
      status: "added"
    });
    // patch 应包含文件头 + + 前缀的全部内容行
    expect(result?.patch).toContain("--- /dev/null");
    expect(result?.patch).toContain("+++ b/new.ts");
    expect(result?.patch).toContain("@@ -0,0 +1,3 @@");
    expect(result?.patch).toContain("+export const a = 1");
    expect(result?.patch).toContain("+export const b = 2");
    expect(result?.patch).toContain("+export const c = 3");
  });

  it("synthesizes unified diff for edit tool from oldString/newString", () => {
    const result = inferDiffFromToolPart(
      toolPart(
        { filePath: "/tmp/demo/src/app.ts", oldString: "const a = 1\nconst b = 2", newString: "const a = 1\nconst b = 2\nconst c = 3" },
        { toolName: "edit" }
      )
    );
    expect(result).toMatchObject({
      path: "/tmp/demo/src/app.ts",
      additions: 3,
      deletions: 2,
      status: "modified"
    });
    // patch 应包含 - 行（被替换的旧内容）和 + 行（新增内容）
    expect(result?.patch).toContain("--- a/app.ts");
    expect(result?.patch).toContain("+++ b/app.ts");
    expect(result?.patch).toContain("-const a = 1");
    expect(result?.patch).toContain("-const b = 2");
    expect(result?.patch).toContain("+const a = 1");
    expect(result?.patch).toContain("+const b = 2");
    expect(result?.patch).toContain("+const c = 3");
  });

  it("preserves the original patch text for apply_patch tool", () => {
    const patch = [
      "*** Begin Patch",
      "*** Update File: src/x.ts",
      "@@",
      "-old line",
      "+new line one",
      "+new line two",
      "*** End Patch"
    ].join("\n");
    const result = inferDiffFromToolPart(
      toolPart({ patchText: patch, filePath: "/tmp/demo/src/x.ts" }, { toolName: "apply_patch" })
    );
    // apply_patch 把原始 patch 文本透传，不再丢成空字符串，让抽屉能完整渲染。
    expect(result).toMatchObject({
      path: "/tmp/demo/src/x.ts",
      additions: 2,
      deletions: 1,
      status: "modified"
    });
    expect(result?.patch).toBe(patch);
  });

  it("treats empty write content as zero additions with header-only patch", () => {
    const result = inferDiffFromToolPart(toolPart({ filePath: "/tmp/demo/empty.ts", content: "" }));
    expect(result).toMatchObject({ path: "/tmp/demo/empty.ts", additions: 0, deletions: 0, status: "added" });
    expect(result?.patch).toBe("--- /dev/null\n+++ b/empty.ts\n@@ -0,0 +1,0 @@");
  });

  it("ignores tools outside the write-tool allowlist", () => {
    const result = inferDiffFromToolPart(
      toolPart({ filePath: "/tmp/demo/src/app.ts", content: "x" }, { toolName: "read" })
    );
    expect(result).toBeUndefined();
  });

  it("returns undefined when filePath is missing", () => {
    const result = inferDiffFromToolPart(toolPart({ content: "abc" }));
    expect(result).toBeUndefined();
  });

  it("restores pending historical question and permission events from the session tree", () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [{ sessionId: "ses_root", rootSessionId: "ses_root", childSession: false }],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: [
        {
          type: "question.asked",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            id: "ques_history",
            sessionID: "ses_root",
            questions: [{ id: "q_history", question: "是否继续执行？", options: [{ label: "继续" }] }]
          }
        },
        {
          type: "permission.asked",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            id: "perm_history",
            sessionID: "ses_root",
            permission: "bash",
            title: "允许执行命令"
          }
        }
      ]
    };

    const state = chatStateFromSessionTreeSnapshot(snapshot);

    expect(state.questions).toMatchObject([
      { requestId: "ques_history", sessionId: "ses_root", questions: [{ text: "是否继续执行？" }] }
    ]);
    expect(state.permissions).toMatchObject([
      { requestId: "perm_history", sessionId: "ses_root", type: "bash", title: "允许执行命令" }
    ]);
  });

  it("restores historical todo panel data from an OpenCode todowrite part without todo.updated", () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [{ sessionId: "ses_root", rootSessionId: "ses_root", childSession: false }],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: []
    };
    const persisted: SessionMessage[] = [
      {
        messageId: "msg_user_todowrite_history",
        sessionId: "ses_root",
        role: "USER",
        content: "执行历史任务",
        createdAt: "2026-07-11T05:54:30.000Z"
      },
      {
        messageId: "msg_todowrite_history",
        sessionId: "ses_root",
        role: "ASSISTANT",
        content: "",
        createdAt: "2026-07-11T05:54:32.473Z",
        parts: [
          {
            partId: "part_todowrite_history",
            type: "tool",
            toolName: "todowrite",
            status: "completed",
            input: {
              todos: [
                { content: "历史任务一", status: "completed", priority: "high" },
                { content: "历史任务二", status: "in_progress", priority: "medium" }
              ]
            }
          }
        ]
      }
    ];

    const state = chatStateFromSessionTreeSnapshot(snapshot, persisted);

    expect(state.todos).toEqual([
      expect.objectContaining({ text: "历史任务一", status: "completed", priority: "high" }),
      expect.objectContaining({ text: "历史任务二", status: "in_progress", priority: "medium" })
    ]);
    expect(state.todoSnapshotsByUserMessageId.msg_user_todowrite_history).toEqual([
      expect.objectContaining({ text: "历史任务一", status: "completed", priority: "high" }),
      expect.objectContaining({ text: "历史任务二", status: "in_progress", priority: "medium" })
    ]);
  });

  it("does not project an earlier turn todowrite snapshot onto a newer turn", () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [{ sessionId: "ses_root", rootSessionId: "ses_root", childSession: false }],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: []
    };
    const persisted: SessionMessage[] = [
      { messageId: "msg_user_first", sessionId: "ses_root", role: "USER", content: "第一轮", createdAt: "2026-07-15T09:00:00Z" },
      {
        messageId: "msg_assistant_first",
        sessionId: "ses_root",
        role: "ASSISTANT",
        content: "",
        createdAt: "2026-07-15T09:00:01Z",
        parts: [{
          partId: "part_first_todo",
          type: "tool",
          toolName: "todowrite",
          status: "completed",
          input: { todos: [{ content: "仅第一轮拥有", status: "completed" }] }
        }]
      },
      { messageId: "msg_user_second", sessionId: "ses_root", role: "USER", content: "第二轮", createdAt: "2026-07-15T09:01:00Z" }
    ];

    const state = chatStateFromSessionTreeSnapshot(snapshot, persisted);

    expect(state.todos).toEqual([]);
    expect(state.todoSnapshotsByUserMessageId.msg_user_first).toEqual([
      expect.objectContaining({ text: "仅第一轮拥有" })
    ]);
    expect(state.todoSnapshotsByUserMessageId.msg_user_second).toBeUndefined();
  });

  it("does not recover a child-session todowrite as the root turn todo snapshot", () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [
        { sessionId: "ses_root", rootSessionId: "ses_root", childSession: false },
        { sessionId: "ses_child", rootSessionId: "ses_root", parentSessionId: "ses_root", childSession: true }
      ],
      messagesBySessionId: {
        ses_child: [
          {
            message: { id: "msg_child_assistant", role: "assistant", createdAt: "2026-07-15T09:00:01Z" },
            sessionId: "ses_child",
            rootSessionId: "ses_root",
            isChildSession: true
          },
          {
            messageId: "msg_child_assistant",
            sessionId: "ses_child",
            rootSessionId: "ses_root",
            isChildSession: true,
            part: {
              id: "part_child_todo",
              messageID: "msg_child_assistant",
              type: "tool",
              tool: "todowrite",
              state: {
                status: "completed",
                input: { todos: [{ content: "子 Agent 待办", status: "completed" }] }
              }
            }
          }
        ]
      },
      childSessionIdByTaskPartId: {},
      events: []
    };
    const state = chatStateFromSessionTreeSnapshot(snapshot, [
      { messageId: "msg_root_user", sessionId: "ses_root", role: "USER", content: "执行根任务", createdAt: "2026-07-15T09:00:00Z" }
    ]);

    expect(state.todos).toEqual([]);
    expect(state.todoSnapshotsByUserMessageId.msg_root_user).toBeUndefined();
  });

  it("migrates todo.updated history from a remote user id to the merged platform message id", () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [{ sessionId: "ses_root", rootSessionId: "ses_root", childSession: false }],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: [
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: {
            message: { id: "msg_remote_user", role: "user", content: "历史任务", createdAt: "2026-07-15T09:00:00Z" }
          }
        },
        {
          type: "todo.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: { todos: [{ content: "远端快照任务", status: "completed" }] }
        }
      ]
    };
    const state = chatStateFromSessionTreeSnapshot(snapshot, [{
      messageId: "msg_11111111111111111111111111111111",
      remoteMessageId: "msg_remote_user",
      sessionId: "ses_root",
      role: "USER",
      content: "历史任务",
      createdAt: "2026-07-15T09:00:00Z"
    }]);

    expect(state.todos).toEqual([expect.objectContaining({ text: "远端快照任务" })]);
    expect(state.todoSnapshotsByUserMessageId.msg_11111111111111111111111111111111).toEqual([
      expect.objectContaining({ text: "远端快照任务" })
    ]);
    expect(state.todoSnapshotsByUserMessageId.msg_remote_user).toBeUndefined();
  });

  it("keeps the latest root todo when a child user message is replayed later", () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [
        { sessionId: "ses_root", rootSessionId: "ses_root", childSession: false },
        { sessionId: "ses_child", rootSessionId: "ses_root", parentSessionId: "ses_root", childSession: true }
      ],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: [
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: { message: { id: "msg_root_user", role: "user", content: "根任务" } }
        },
        {
          type: "todo.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: { todos: [{ content: "根任务待办", status: "in_progress" }] }
        },
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_child",
          parentSessionId: "ses_root",
          childSession: true,
          payload: { message: { id: "msg_child_user", role: "user", content: "子 Agent 输入" } }
        }
      ]
    };

    const state = chatStateFromSessionTreeSnapshot(snapshot);
    const reconciled = reconcileCurrentTurnTodos(state, [{ id: "todo_calibrated", text: "根任务校准", status: "completed" }]);

    expect(state.todos).toEqual([expect.objectContaining({ text: "根任务待办" })]);
    expect(reconciled.todoSnapshotsByUserMessageId.msg_root_user).toEqual([
      expect.objectContaining({ text: "根任务校准" })
    ]);
    expect(reconciled.todoSnapshotsByUserMessageId.msg_child_user).toBeUndefined();
  });

  it("prefers a newer remote todo.updated over an older persisted todowrite fallback", () => {
    const snapshot: SessionTreeMessagesResponse = {
      sessionId: "ses_root",
      sessions: [{ sessionId: "ses_root", rootSessionId: "ses_root", childSession: false }],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: [
        {
          type: "message.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: { message: { id: "msg_remote_user_newer", role: "user", content: "历史任务" } }
        },
        {
          type: "todo.updated",
          rootSessionId: "ses_root",
          sessionId: "ses_root",
          childSession: false,
          payload: { todos: [{ content: "较新的事件快照", status: "in_progress" }] }
        }
      ]
    };
    const state = chatStateFromSessionTreeSnapshot(snapshot, [
      {
        messageId: "msg_22222222222222222222222222222222",
        remoteMessageId: "msg_remote_user_newer",
        sessionId: "ses_root",
        role: "USER",
        content: "历史任务",
        createdAt: "2026-07-15T09:00:00Z"
      },
      {
        messageId: "msg_old_todowrite",
        sessionId: "ses_root",
        role: "ASSISTANT",
        content: "",
        createdAt: "2026-07-15T09:00:01Z",
        parts: [{
          partId: "part_old_todo",
          type: "tool",
          toolName: "todowrite",
          status: "completed",
          input: { todos: [{ content: "较旧的持久化快照", status: "completed" }] }
        }]
      }
    ]);

    expect(state.todos).toEqual([expect.objectContaining({ text: "较新的事件快照" })]);
    expect(state.todoSnapshotsByUserMessageId.msg_22222222222222222222222222222222).toEqual([
      expect.objectContaining({ text: "较新的事件快照" })
    ]);
  });

  it("reconciles session todo results only when the latest turn has ownership evidence", () => {
    const withoutCurrentOwnership = chatStateFromSessionTreeSnapshot({
      sessionId: "ses_root",
      sessions: [{ sessionId: "ses_root", rootSessionId: "ses_root", childSession: false }],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: []
    }, [
      { messageId: "msg_user_first", sessionId: "ses_root", role: "USER", content: "第一轮", createdAt: "2026-07-15T09:00:00Z" },
      { messageId: "msg_user_second", sessionId: "ses_root", role: "USER", content: "第二轮", createdAt: "2026-07-15T09:01:00Z" }
    ]);
    const staleLiveTodos = [{ id: "todo_old", text: "旧会话快照", status: "completed" }];

    const ignored = reconcileCurrentTurnTodos(withoutCurrentOwnership, staleLiveTodos);
    expect(ignored.todos).toEqual([]);
    expect(ignored.todoSnapshotsByUserMessageId.msg_user_second).toBeUndefined();

    const withCurrentOwnership = {
      ...withoutCurrentOwnership,
      todoSnapshotsByUserMessageId: {
        ...withoutCurrentOwnership.todoSnapshotsByUserMessageId,
        msg_user_second: []
      }
    };
    const reconciled = reconcileCurrentTurnTodos(withCurrentOwnership, staleLiveTodos);
    expect(reconciled.todos).toEqual(staleLiveTodos);
    expect(reconciled.todoSnapshotsByUserMessageId.msg_user_second).toEqual(staleLiveTodos);

    const cleared = reconcileCurrentTurnTodos({
      ...reconciled,
      todoSnapshotsByUserMessageId: {
        msg_user_first: [{ id: "todo_first", text: "第一轮任务", status: "completed" }],
        msg_user_second: staleLiveTodos
      }
    }, []);
    expect(cleared.todos).toEqual([]);
    expect(cleared.todoSnapshotsByUserMessageId.msg_user_first).toEqual([
      { id: "todo_first", text: "第一轮任务", status: "completed" }
    ]);
  });
});
