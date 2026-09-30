import { expect, test, type Page } from "@playwright/test";
import { access, readFile, rm } from "node:fs/promises";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import path from "node:path";
import { assistantMessagesAfter, getNativeV2Session, listNativeV2Messages, nativeV2AuthHeaders, nativeV2SessionDirectory } from "./opencode-v2-native";

import {
  apiDelete,
  apiGet,
  apiPost,
  authHeaders,
  assertNativeSessionAbsentInSqlite,
  createCleanupScope,
  resolveOwnedOpenCodeDatabase,
  runCleanupStages,
  resolveRemoteSessionIdFromSources,
  resolveOwnedCleanupPath,
  waitForWorkspaceOperation,
  type WorkspaceCreateOperation
} from "./real-e2e-api";

const runRealE2e = process.env.TEST_AGENT_RUN_REAL_E2E === "1";
const backendBaseUrl = stripTrailingSlash(process.env.TEST_AGENT_BASE_URL ?? "http://127.0.0.1:8080");
const existingWorkspaceId = process.env.TEST_AGENT_REAL_E2E_WORKSPACE_ID;
const existingWorkspaceRoot = process.env.TEST_AGENT_REAL_E2E_WORKSPACE_ROOT;

test.describe("phase 11 real service integration", () => {
  test.skip(!runRealE2e, "Set TEST_AGENT_RUN_REAL_E2E=1 to run the real frontend/backend/opencode integration suite.");

  test("runs a real V2 conversation and PTY in an existing owned workspace", async ({ page }) => {
    test.skip(!existingWorkspaceId || !existingWorkspaceRoot,
      "Set TEST_AGENT_REAL_E2E_WORKSPACE_ID and TEST_AGENT_REAL_E2E_WORKSPACE_ROOT for the existing-workspace E2E.");
    test.skip(!process.env.TEST_AGENT_OPENCODE_SERVER_PASSWORD && !process.env.OPENCODE_PASSWORD,
      "Set the managed OpenCode V2 Basic Auth secret before the native session and cleanup checks.");
    const workspaceId = existingWorkspaceId!;
    const workspaceRoot = existingWorkspaceRoot!;
    const marker = `v2_real_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    let sessionId: string | undefined;
    let remoteSessionId: string | undefined;
    let opencodeBaseUrl: string | undefined;
    let primaryFailure: unknown;
    try {
      await expect.poll(
        async () => (await apiGet<{ status?: string }>("/api/internal/agent/opencode/processes/me")).status,
        { timeout: 30_000, intervals: [500, 1_000, 2_000], message: "V2 manager should restore the user process after restart" }
      ).toBe("READY");
      const processInfo = await apiGet<{ status?: string; baseUrl?: string; linuxServerId?: string }>(
        "/api/internal/agent/opencode/processes/me"
      );
      const workspace = await apiGet<{ linuxServerId?: string; capabilities?: { chat?: boolean; terminal?: boolean } }>(
        `/api/internal/platform/workspace-management/workspaces/${encodeURIComponent(workspaceId)}`
      );
      expect(processInfo.status).toBe("READY");
      expect(workspace.linuxServerId).toBe(processInfo.linuxServerId);
      expect(workspace.capabilities).toMatchObject({ chat: true, terminal: true });
      opencodeBaseUrl = processInfo.baseUrl;
      expect(opencodeBaseUrl).toBeTruthy();

      const session = await apiPost<{ sessionId: string }>("/api/internal/platform/opencode-runtime/sessions", {
        workspaceId,
        title: marker
      });
      sessionId = session.sessionId;
      const prompt = `Reply with ${marker}. Do not modify files.`;
      const run = await apiPost<{ runId: string }>("/api/internal/agent/opencode/runs", {
        sessionId,
        prompt,
        parts: [{ type: "text", text: prompt }]
      });
      const events = await captureRunEventsUntilTerminal(run.runId).finished;
      remoteSessionId = await resolveRemoteSessionId(sessionId, (observedId) => {
        remoteSessionId ??= observedId;
      });
      const uniqueEvents = new Map<string, CapturedRunEvent>();
      for (const event of events) {
        const previous = uniqueEvents.get(event.eventId);
        if (previous) {
          // legacy SSE 的 live/replay 可重复投递同一 durable ID；内容必须保持一致。
          expect({ seq: event.seq, type: event.type }).toEqual({ seq: previous.seq, type: previous.type });
        } else {
          uniqueEvents.set(event.eventId, event);
        }
      }
      const delivered = [...uniqueEvents.values()];
      expect(delivered.map((event) => event.type)).toContain("run.succeeded");
      expect(delivered.filter((event) => event.type === "run.succeeded")).toHaveLength(1);
      const durable = delivered.filter((event) => event.seq > 0);
      expect(new Set(durable.map((event) => event.seq)).size).toBe(durable.length);

      const nativeSession = await getNativeV2Session(opencodeBaseUrl!, remoteSessionId);
      expect(nativeV2SessionDirectory(nativeSession)).toBe(workspaceRoot);
      const nativeMessages = await listNativeV2Messages(opencodeBaseUrl!, remoteSessionId);
      expect(nativeMessages.some((message) => message.type === "user")).toBe(true);
      expect(nativeMessages.some((message) => message.type === "assistant")).toBe(true);
      const history = await apiGet<{ items?: Array<{ role?: string; runId?: string }> }>(
        `/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(sessionId)}/messages?page=1&size=100&refresh=true`
      );
      expect(history.items?.some((message) =>
        message.role?.toLowerCase() === "user" && message.runId === run.runId
      )).toBe(true);
      const tree = await apiGet<{
        messagesBySessionId?: Record<string, Array<{ message?: { role?: string } }>>;
      }>(`/api/internal/agent/opencode/sessions/${encodeURIComponent(sessionId)}/session-tree/messages`);
      const treeMessages = Object.values(tree.messagesBySessionId ?? {}).flat();
      expect(treeMessages.some((entry) => entry.message?.role?.toLowerCase() === "assistant")).toBe(true);

      const ticket = await apiPost<{ webSocketUrl: string }>(
        `/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(sessionId)}/terminal/tickets`,
        { workspaceId, cols: 120, rows: 32 }
      );
      await page.goto("/985211");
      await page.waitForLoadState("networkidle");
      const terminal = await connectTerminalAndEcho(page, ticket.webSocketUrl, marker);
      expect(terminal.error).toBeUndefined();
      expect(terminal.output).toContain(marker);
    } catch (error) {
      primaryFailure = error;
      throw error;
    } finally {
      const ownedSessionId = sessionId;
      const ownedRemoteSessionId = remoteSessionId;
      const ownedOpencodeBaseUrl = opencodeBaseUrl;
      try {
        await runCleanupStages([
          async () => {
            if (!ownedSessionId) return;
            const activeRun = await apiGet<{ runId: string } | null>(
              `/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(ownedSessionId)}/active-run`
            );
            if (activeRun?.runId) {
              await apiPost(`/api/internal/agent/opencode/runs/${encodeURIComponent(activeRun.runId)}/cancel`, {});
            }
          },
          async () => {
            if (!ownedOpencodeBaseUrl) return;
            const nativeId = ownedRemoteSessionId ?? (ownedSessionId
              ? await resolveRemoteSessionId(ownedSessionId, (observedId) => { remoteSessionId ??= observedId; }).catch(() => undefined)
              : undefined);
            if (nativeId) await deleteNativeSession(ownedOpencodeBaseUrl, nativeId, workspaceRoot);
          },
          async () => {
            if (ownedSessionId) await apiDelete(`/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(ownedSessionId)}`);
          }
        ]);
      } catch (cleanupError) {
        if (primaryFailure !== undefined) {
          throw new AggregateError([primaryFailure, cleanupError], "Real V2 E2E failed and cleanup also failed");
        }
        throw cleanupError;
      }
    }
  });

  test("creates a real opencode-backed session and opens a PTY terminal websocket", async ({ page }) => {
    const workspace = await createManagedWorkspaceFixture();
    let sessionId: string | undefined;
    let remoteSessionId: string | undefined;
    let opencodeBaseUrl: string | undefined;
    let openCodeDatabasePath: string | undefined;
    try {
      const processInfo = await apiGet<{ port?: number; baseUrl?: string }>("/api/internal/agent/opencode/processes/me");
      opencodeBaseUrl = processInfo.baseUrl;
      openCodeDatabasePath = (
        await resolveOwnedOpenCodeDatabase(processInfo, { projectRoot: path.resolve(process.cwd(), "..") })
      ).databasePath;
      const session = await apiPost<{ sessionId: string }>("/api/internal/platform/opencode-runtime/sessions", {
        workspaceId: workspace.workspaceId,
        title: "Phase 11 real E2E"
      });
      sessionId = session.sessionId;

      const mappingTicket = await establishOpencodeMapping(session.sessionId);
      remoteSessionId = await resolveRemoteSessionId(session.sessionId, (observedRemoteSessionId) => {
        // cleanup-owned ref 必须在任意后续 tree/投影异常之前取得远端 ID。
        remoteSessionId ??= observedRemoteSessionId;
      });
      const ticket =
        mappingTicket ??
        (await apiPost<{ webSocketUrl: string }>(`/api/internal/platform/opencode-runtime/sessions/${session.sessionId}/terminal/tickets`, {
          workspaceId: workspace.workspaceId,
          cols: 120,
          rows: 32
        }));

      // PTY probe 只需要稳定的同源浏览器上下文；未注入登录态时根路由会异步跳转并销毁 evaluate。
      await page.goto("/985211");
      await page.waitForLoadState("networkidle");
      const terminalResult = await connectTerminalAndEcho(page, ticket.webSocketUrl, "phase11-real-e2e");

      expect(terminalResult.output).toContain("phase11-real-e2e");
      expect(terminalResult.error).toBeUndefined();

      const reusedTicketResult = await connectTerminalAndEcho(page, ticket.webSocketUrl, "phase11-ticket-reuse");
      expect(reusedTicketResult.error?.code).toBeTruthy();
    } finally {
      const ownedSessionId = sessionId;
      const ownedRemoteSessionId = remoteSessionId;
      const ownedOpencodeBaseUrl = opencodeBaseUrl;
      const ownedOpenCodeDatabasePath = openCodeDatabasePath;
      await runCleanupStages([
        async () => {
          if (!ownedSessionId) return;
          const activeRun = await apiGet<{ runId: string } | null>(
            `/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(ownedSessionId)}/active-run`
          );
          if (activeRun?.runId) {
            await apiPost(`/api/internal/agent/opencode/runs/${encodeURIComponent(activeRun.runId)}/cancel`, {});
          }
        },
        async () => {
          if (!ownedRemoteSessionId || !ownedOpencodeBaseUrl) return;
          await deleteNativeSession(ownedOpencodeBaseUrl, ownedRemoteSessionId, workspace.workspaceRootPath);
          if (!ownedOpenCodeDatabasePath) throw new Error("owned OpenCode SQLite path was not resolved before cleanup");
          await assertNativeSessionAbsentInSqlite(ownedOpenCodeDatabasePath, ownedRemoteSessionId);
        },
        async () => {
          if (ownedSessionId) {
            await apiDelete(`/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(ownedSessionId)}`);
            const history = await apiGet<{ items?: Array<{ sessionId?: string }> }>(
              `/api/internal/platform/opencode-runtime/workspaces/${encodeURIComponent(workspace.workspaceId)}/sessions?page=1&size=100`
            );
            expect(history.items?.some((item) => item.sessionId === ownedSessionId) ?? false).toBe(false);
          }
        },
        async () => {
          await apiDelete(
            `/api/internal/platform/configuration-management/applications/${encodeURIComponent(workspace.appId)}/workspaces/${encodeURIComponent(workspace.applicationWorkspaceId)}`
          );
          const configuredWorkspaces = await apiGet<Array<{ workspaceId?: string }>>(
            `/api/internal/platform/configuration-management/applications/${encodeURIComponent(workspace.appId)}/workspaces`
          );
          expect(configuredWorkspaces.some((item) => item.workspaceId === workspace.applicationWorkspaceId)).toBe(false);
        },
        async () => {
          const ownedRoot = path.resolve(process.cwd(), "../.testagent/agent-opencode/workspace");
          const safePath = await resolveOwnedCleanupPath(workspace.workspaceRootPath, ownedRoot, workspace.marker);
          await rm(safePath, { recursive: true, force: true });
          await expectPathAbsent(safePath);
        }
      ]);
    }
  });

  test("pet side-question streams an isolated answer and removes its temporary fork", async () => {
    const workspace = await createManagedWorkspaceFixture();
    let sessionId: string | undefined;
    let mainRemoteSessionId: string | undefined;
    let opencodeBaseUrl: string | undefined;
    let openCodeDatabasePath: string | undefined;
    let observedForkIds: string[] = [];
    try {
      const processInfo = await apiGet<{ port?: number; baseUrl?: string }>("/api/internal/agent/opencode/processes/me");
      opencodeBaseUrl = processInfo.baseUrl;
      openCodeDatabasePath = (
        await resolveOwnedOpenCodeDatabase(processInfo, { projectRoot: path.resolve(process.cwd(), "..") })
      ).databasePath;
      const session = await apiPost<{ sessionId: string }>("/api/internal/platform/opencode-runtime/sessions", {
        workspaceId: workspace.workspaceId,
        title: "Pet side-question real E2E"
      });
      sessionId = session.sessionId;
      const mainPrompt = "请只读检查当前工作区，并用简短自然语言说明可见的测试文件。不要修改文件。";
      await apiPost("/api/internal/agent/opencode/runs", {
        sessionId,
        prompt: mainPrompt,
        parts: [{ type: "text", text: mainPrompt }]
      });
      mainRemoteSessionId = await resolveRemoteSessionId(sessionId, () => undefined);
      const beforeMessages = await apiGet<{ items?: unknown[] }>(
        `/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(sessionId)}/messages?page=1&size=100&refresh=false`
      );
      const baselineNativeSessions = await listNativeSessionIds(openCodeDatabasePath);
      const sideQuestion = "当前主对话正在做什么？只给简短结论。";
      const started = await apiPost<{ runId: string }>(
        `/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(sessionId)}/side-question/runs`,
        { question: sideQuestion }
      );
      const capture = captureRunEventsUntilTerminal(started.runId);
      observedForkIds = await observeNewNativeSessionIds(
        openCodeDatabasePath,
        baselineNativeSessions,
        capture.finished
      );
      const events = await capture.finished;
      const nativeSessionsAfterTerminal = await listNativeSessionIds(openCodeDatabasePath);
      observedForkIds = observedForkIds.filter((id) => !nativeSessionsAfterTerminal.includes(id));
      // live bus 与历史回放可并发抵达；durable 顺序以服务端分配的 seq 为唯一事实源。
      const durable = events
        .filter((event) => typeof event.seq === "number" && event.seq > 0)
        .sort((left, right) => left.seq - right.seq);
      expect(durable.map((event) => event.type).slice(0, 3)).toEqual([
        "run.created",
        "run.started",
        "side_question.started"
      ]);
      expect(events.some((event) => event.type === "side_question.progress")).toBe(true);
      expect(events.some((event) => event.type === "side_question.delta" && String(event.payload?.delta ?? "").length > 0)).toBe(true);
      const terminal = events.find((event) => event.type === "run.succeeded");
      expect(typeof terminal?.payload?.answer).toBe("string");
      expect(String(terminal?.payload?.answer)).not.toContain("<tool_calls");
      expect(events.every((event) => event.type.startsWith("run.") || event.type.startsWith("side_question."))).toBe(true);
      const afterMessages = await apiGet<{ items?: unknown[] }>(
        `/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(sessionId)}/messages?page=1&size=100&refresh=false`
      );
      expect(JSON.stringify(afterMessages.items ?? [])).not.toContain(sideQuestion);
      expect((afterMessages.items?.length ?? 0)).toBeGreaterThanOrEqual(beforeMessages.items?.length ?? 0);
      expect(observedForkIds).not.toHaveLength(0);
      await expect
        .poll(() => listNativeSessionIds(openCodeDatabasePath!), { timeout: 20_000, intervals: [200, 500, 1_000] })
        .not.toEqual(expect.arrayContaining(observedForkIds));
    } finally {
      const ownedSessionId = sessionId;
      const ownedRemoteSessionId = mainRemoteSessionId;
      const ownedOpencodeBaseUrl = opencodeBaseUrl;
      const ownedDatabasePath = openCodeDatabasePath;
      const ownedForkIds = observedForkIds;
      await runCleanupStages([
        async () => {
          if (!ownedSessionId) return;
          const activeRun = await apiGet<{ runId: string } | null>(
            `/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(ownedSessionId)}/active-run`
          );
          if (activeRun?.runId) await apiPost(`/api/internal/agent/opencode/runs/${encodeURIComponent(activeRun.runId)}/cancel`, {});
        },
        async () => {
          if (!ownedDatabasePath) return;
          for (const forkId of ownedForkIds) await assertNativeSessionAbsentInSqlite(ownedDatabasePath, forkId);
        },
        async () => {
          if (!ownedRemoteSessionId || !ownedOpencodeBaseUrl) return;
          await deleteNativeSession(ownedOpencodeBaseUrl, ownedRemoteSessionId, workspace.workspaceRootPath);
        },
        async () => {
          if (ownedSessionId) await apiDelete(`/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(ownedSessionId)}`);
        },
        async () => {
          await apiDelete(
            `/api/internal/platform/configuration-management/applications/${encodeURIComponent(workspace.appId)}/workspaces/${encodeURIComponent(workspace.applicationWorkspaceId)}`
          );
        },
        async () => {
          const ownedRoot = path.resolve(process.cwd(), "../.testagent/agent-opencode/workspace");
          const safePath = await resolveOwnedCleanupPath(workspace.workspaceRootPath, ownedRoot, workspace.marker);
          await rm(safePath, { recursive: true, force: true });
        }
      ]);
    }
  });

  test("native resend reverts the last user turn and replaces its visible response", async () => {
    test.setTimeout(180_000);
    const sessions = await apiGet<{ items?: Array<{ workspaceId?: string; status?: string }> }>(
      "/api/internal/platform/opencode-runtime/sessions?page=1&size=100"
    );
    const reusable = sessions.items?.find((item) => item.status === "ACTIVE" && item.workspaceId);
    if (!reusable?.workspaceId) throw new Error("No existing active workspace is available for resend real E2E");
    const workspace = await apiGet<{ physicalRootPath?: string }>(
      `/api/internal/platform/workspace-management/workspaces/${encodeURIComponent(reusable.workspaceId)}`
    );
    if (!workspace.physicalRootPath) throw new Error("Resend real E2E workspace has no physical root");

    const marker = `resend_real_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    const markerName = `.testagent-${marker}.txt`;
    const markerPath = path.join(workspace.physicalRootPath, markerName);
    let sessionId: string | undefined;
    let remoteSessionId: string | undefined;
    let opencodeBaseUrl: string | undefined;
    try {
      const processInfo = await apiGet<{ baseUrl?: string }>("/api/internal/agent/opencode/processes/me");
      opencodeBaseUrl = processInfo.baseUrl;
      if (!opencodeBaseUrl) throw new Error("Resend real E2E OpenCode process has no baseUrl");
      const session = await apiPost<{ sessionId: string }>("/api/internal/platform/opencode-runtime/sessions", {
        workspaceId: reusable.workspaceId,
        title: `Resend real E2E ${marker}`
      });
      sessionId = session.sessionId;
      const context = await apiPost<{ contextToken: string }>(
        `/api/internal/agent/opencode/sessions/${encodeURIComponent(sessionId)}/run-context`,
        {}
      );
      const prompt = [
        `Check whether ${markerName} exists in the current workspace.`,
        `If absent, create it containing exactly ${marker} and reply exactly CREATED_FROM_ABSENT_${marker}.`,
        `If it exists, do not modify it and reply exactly FOUND_EXISTING_${marker}.`
      ].join(" ");
      const source = await apiPost<{ runId: string }>("/api/internal/agent/opencode/runs", {
        sessionId,
        contextToken: context.contextToken,
        clientRequestId: `real-source-${marker}`,
        prompt,
        parts: [{ type: "text", text: prompt }]
      });
      const sourceEvents = await captureRunEventsUntilTerminal(source.runId).finished;
      expect(sourceEvents.some((event) => event.type === "run.succeeded")).toBe(true);
      expect(await readFile(markerPath, "utf8")).toBe(marker);
      remoteSessionId = await resolveRemoteSessionId(sessionId, () => undefined);

      const beforeMessages = await apiGet<{ items?: PlatformRealMessage[] }>(
        `/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(sessionId)}/messages?page=1&size=100&refresh=true`
      );
      const sourceUser = beforeMessages.items?.find(
        (message) => message.runId === source.runId && message.role?.toUpperCase() === "USER"
      );
      if (!sourceUser?.remoteMessageId) throw new Error("Source Run has no recoverable remote user boundary");
      const beforeNative = await listNativeV2Messages(opencodeBaseUrl, remoteSessionId);
      const oldAssistantIds = assistantMessagesAfter(beforeNative, sourceUser.remoteMessageId).map((message) => message.id);
      expect(oldAssistantIds.length).toBeGreaterThan(0);
      const resendContext = await apiPost<{ contextToken: string }>(
        `/api/internal/agent/opencode/sessions/${encodeURIComponent(sessionId)}/run-context`,
        {}
      );

      const created = await apiPost<{
        replacementRun: { runId: string };
        resend: { replacementRunId: string; status: string };
      }>(`/api/internal/agent/opencode/sessions/${encodeURIComponent(sessionId)}/resends`, {
        expectedRemoteMessageId: sourceUser.remoteMessageId,
        expectedRunId: source.runId,
        contextToken: resendContext.contextToken,
        clientRequestId: `real-resend-${marker}`
      });
      expect(created.replacementRun.runId).toBe(created.resend.replacementRunId);
      expect(created.resend.status).toBe("WAITING");
      const replacementEvents = await captureRunEventsUntilTerminal(created.replacementRun.runId).finished;
      expect(replacementEvents.some((event) => event.type === "run.resend.scheduled")).toBe(true);
      expect(replacementEvents.some((event) => event.type === "run.resend.started")).toBe(true);
      expect(replacementEvents.some((event) => event.type === "run.succeeded")).toBe(true);

      const afterNative = await listNativeV2Messages(opencodeBaseUrl, remoteSessionId);
      const nativeIds = new Set(afterNative.map((message) => message.id));
      expect(nativeIds.has(sourceUser.remoteMessageId)).toBe(false);
      expect(oldAssistantIds.every((id) => !nativeIds.has(id))).toBe(true);
      const replacementUser = afterNative.filter((message) => message.type === "user").at(-1);
      if (!replacementUser?.id) throw new Error("Replacement native user message was not found");
      const replacementAnswer = assistantMessagesAfter(afterNative, replacementUser.id)
        .flatMap((message) => message.content ?? [])
        .filter((part) => part.type === "text")
        .map((part) => String(part.text ?? ""))
        .join("\n");
      expect(replacementAnswer).toContain(`CREATED_FROM_ABSENT_${marker}`);
      expect(replacementAnswer).not.toContain(`FOUND_EXISTING_${marker}`);
      expect(await readFile(markerPath, "utf8")).toBe(marker);

      const afterMessages = await apiGet<{ items?: PlatformRealMessage[] }>(
        `/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(sessionId)}/messages?page=1&size=100&refresh=true`
      );
      expect(afterMessages.items?.some((message) => message.runId === source.runId) ?? false).toBe(false);
      expect(afterMessages.items?.some((message) => message.runId === created.replacementRun.runId) ?? false).toBe(true);
    } finally {
      const ownedSessionId = sessionId;
      const ownedRemoteSessionId = remoteSessionId;
      const ownedOpencodeBaseUrl = opencodeBaseUrl;
      await runCleanupStages([
        async () => {
          if (!ownedSessionId) return;
          const activeRun = await apiGet<{ runId: string } | null>(
            `/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(ownedSessionId)}/active-run`
          );
          if (activeRun?.runId) await apiPost(`/api/internal/agent/opencode/runs/${encodeURIComponent(activeRun.runId)}/cancel`, {});
        },
        async () => {
          if (ownedRemoteSessionId && ownedOpencodeBaseUrl) {
            await deleteNativeSession(ownedOpencodeBaseUrl, ownedRemoteSessionId, workspace.physicalRootPath!);
          }
        },
        async () => {
          if (ownedSessionId) await apiDelete(`/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(ownedSessionId)}`);
        },
        async () => {
          await rm(markerPath, { force: true });
        }
      ]);
    }
  });
});

async function createManagedWorkspaceFixture(): Promise<ManagedWorkspaceFixture> {
  const initialProcess = await apiGet<{ status?: string; initializable?: boolean }>("/api/internal/agent/opencode/processes/me");
  if (initialProcess.status !== "READY" && initialProcess.initializable) {
    await apiPost("/api/internal/agent/opencode/processes/me/initialize", {});
  }
  await expect
    .poll(
      async () => {
        const process = await apiGet<{ status?: string }>("/api/internal/agent/opencode/processes/me");
        return process.status;
      },
      { timeout: 30_000, intervals: [500, 1_000, 2_000], message: "current user's OpenCode process should become READY" }
    )
    .toBe("READY");
  const marker = `phase11_real_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const applications = await apiGet<Application[]>("/api/internal/platform/configuration-management/applications?enabled=true");
  for (const application of applications) {
    const repositories = await apiGet<Repository[]>(
      `/api/internal/platform/configuration-management/applications/${encodeURIComponent(application.appId)}/repositories`
    );
    const repository = repositories.find((candidate) => Boolean(candidate.englishName) && !candidate.standard);
    if (!repository) {
      continue;
    }
    const branches = await apiGet<string[]>(
      `/api/internal/platform/configuration-management/repositories/${encodeURIComponent(repository.repositoryId)}/branches`
    );
    // 同一日期的 repoRoot 会被多个 Workspace 复用，优先沿用该代码库已有模板分支，
    // 避免异步准备阶段因强行切换共享 checkout 而报冲突。
    const configuredWorkspaces = await apiGet<Array<{ repositoryId: string; branch: string }>>(
      `/api/internal/platform/configuration-management/applications/${encodeURIComponent(application.appId)}/workspaces`
    );
    const configuredBranch = configuredWorkspaces.find(
      (candidate) => candidate.repositoryId === repository.repositoryId && branches.includes(candidate.branch)
    )?.branch;
    const branch = configuredBranch ?? branches[0];
    if (!branch) {
      continue;
    }

    const operationId = `wco_${marker}`;
    const creationScope = createCleanupScope();
    try {
      await apiPost(`/api/internal/platform/configuration-management/applications/${encodeURIComponent(application.appId)}/workspaces`, {
        repositoryId: repository.repositoryId,
        branch,
        directoryPath: marker,
        workspaceName: marker,
        directoryNew: true,
        version: formatDate(new Date()),
        operationId
      });
      creationScope.defer(async () => {
        const ownedOperation = await apiGet<{ workspaceId?: string | null }>(
          `/api/internal/platform/configuration-management/workspace-create-operations/${encodeURIComponent(operationId)}`
        );
        if (ownedOperation.workspaceId) {
          await apiDelete(
            `/api/internal/platform/configuration-management/applications/${encodeURIComponent(application.appId)}/workspaces/${encodeURIComponent(ownedOperation.workspaceId)}`
          );
        }
      });
      const operation: WorkspaceCreateOperation = await waitForWorkspaceOperation(operationId, {
        getOperation: (id) =>
          apiGet(`/api/internal/platform/configuration-management/workspace-create-operations/${encodeURIComponent(id)}`)
      });
      if (!operation.workspaceId || !operation.versionId) {
        throw new Error(`Workspace operation ${operationId} succeeded without workspaceId/versionId`);
      }
      const versions = await apiGet<WorkspaceVersion[]>(
        `/api/internal/platform/workspace-management/applications/${encodeURIComponent(application.appId)}/workspace-templates/${encodeURIComponent(operation.workspaceId)}/versions`
      );
      const version = versions.find((candidate) => candidate.versionId === operation.versionId);
      if (!version?.runtimeWorkspace?.workspaceId || !version.workspaceRootPath) {
        throw new Error(`Workspace operation ${operationId} version ${operation.versionId} has no runtime workspace`);
      }
      const fixture = {
        marker,
        appId: application.appId,
        applicationWorkspaceId: operation.workspaceId,
        workspaceId: version.runtimeWorkspace.workspaceId,
        workspaceRootPath: version.workspaceRootPath
      };
      creationScope.release();
      return fixture;
    } catch (error) {
      try {
        await creationScope.cleanup();
      } catch (cleanupError) {
        throw new AggregateError([error, cleanupError], `Workspace fixture ${operationId} failed and cleanup also failed`);
      }
      throw error;
    }
  }
  throw new Error("No enabled application has a linked non-standard repository with a usable branch");
}

function formatDate(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  return `${year}${month}${day}`;
}

type Application = { appId: string; appName: string };
type Repository = { repositoryId: string; englishName?: string | null; standard: boolean };
type WorkspaceVersion = { versionId: string; workspaceRootPath: string; runtimeWorkspace?: { workspaceId?: string } | null };
type ManagedWorkspaceFixture = {
  marker: string;
  appId: string;
  applicationWorkspaceId: string;
  workspaceId: string;
  workspaceRootPath: string;
};

type CapturedRunEvent = { eventId: string; seq: number; type: string; payload: Record<string, unknown> };
type PlatformRealMessage = { role?: string; runId?: string; remoteMessageId?: string };
/** 真实 E2E 只通过平台 RunEvent SSE 收集旁路可见事件，不接触生产浏览器外的 OpenCode 事件流。 */
function captureRunEventsUntilTerminal(runId: string): { finished: Promise<CapturedRunEvent[]> } {
  const controller = new AbortController();
  const finished = new Promise<CapturedRunEvent[]>(async (resolve, reject) => {
    const timeout = setTimeout(() => {
      controller.abort();
      reject(new Error(`Run ${runId} did not reach a terminal event within 120 seconds`));
    }, 120_000);
    try {
      const response = await fetch(`${backendBaseUrl}/api/internal/agent/opencode/runs/${encodeURIComponent(runId)}/events`, {
        headers: { ...authHeaders(), Accept: "text/event-stream" },
        signal: controller.signal
      });
      if (!response.ok || !response.body) throw new Error(`RunEvent SSE failed: ${response.status}`);
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      const events: CapturedRunEvent[] = [];
      let buffer = "";
      while (true) {
        const next = await reader.read();
        if (next.done) break;
        buffer += decoder.decode(next.value, { stream: true });
        const frames = buffer.split("\n\n");
        buffer = frames.pop() ?? "";
        for (const frame of frames) {
          const data = frame
            .split("\n")
            .filter((line) => line.startsWith("data:"))
            .map((line) => line.slice(5).trim())
            .join("\n");
          if (!data) continue;
          const parsed = JSON.parse(data) as CapturedRunEvent;
          events.push(parsed);
          if (parsed.type === "run.succeeded" || parsed.type === "run.failed" || parsed.type === "run.cancelled") {
            clearTimeout(timeout);
            controller.abort();
            resolve(events);
            return;
          }
        }
      }
      clearTimeout(timeout);
      reject(new Error(`RunEvent SSE ended before terminal event for ${runId}`));
    } catch (error) {
      clearTimeout(timeout);
      if (controller.signal.aborted) return;
      reject(error);
    }
  });
  return { finished };
}

/** 仅在真实 E2E 进程内读取测试用户自有 SQLite，记录 fork 曾出现的 ID 以验证清理。 */
async function observeNewNativeSessionIds(
  databasePath: string,
  baseline: string[],
  terminal: Promise<unknown>
): Promise<string[]> {
  const known = new Set(baseline);
  const discovered = new Set<string>();
  let terminalReached = false;
  void terminal.finally(() => {
    terminalReached = true;
  });
  while (!terminalReached) {
    for (const id of await listNativeSessionIds(databasePath)) {
      if (!known.has(id)) discovered.add(id);
    }
    await new Promise<void>((resolve) => setTimeout(resolve, 100));
  }
  return [...discovered];
}

async function listNativeSessionIds(databasePath: string): Promise<string[]> {
  const { stdout } = await promisify(execFile)("sqlite3", ["-readonly", databasePath, "select id from session_v2;"]);
  return stdout.split("\n").map((value) => value.trim()).filter(Boolean);
}

async function establishOpencodeMapping(sessionId: string): Promise<{ webSocketUrl: string } | null> {
  try {
    await apiPost("/api/internal/agent/opencode/runs", {
      sessionId,
      prompt: "Reply with phase11-real-e2e-ready. Do not modify files.",
      parts: [{ type: "text", text: "Reply with phase11-real-e2e-ready. Do not modify files." }]
    });
    return null;
  } catch (error) {
    const ticket = await tryCreateTerminalTicket(sessionId);
    if (ticket) {
      return ticket;
    }
    throw error;
  }
}

async function resolveRemoteSessionId(platformSessionId: string, onObserved: (remoteSessionId: string) => void): Promise<string> {
  let remoteSessionId: string | undefined;
  await expect
    .poll(
      async () => {
        try {
          remoteSessionId = await resolveRemoteSessionIdFromSources({
            loadTree: () =>
              apiGet(`/api/internal/agent/opencode/sessions/${encodeURIComponent(platformSessionId)}/session-tree/messages`),
            loadPlatformMessages: () =>
              apiGet(
                `/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(platformSessionId)}/messages?page=1&size=100&refresh=true`
              ),
            onObserved
          });
        } catch (error) {
          if (!(error instanceof Error) || !error.message.includes("was not found")) throw error;
          remoteSessionId = undefined;
        }
        return remoteSessionId;
      },
      { timeout: 15_000, intervals: [200, 500, 1_000], message: "remote OpenCode session id should be recoverable" }
    )
    .toBeTruthy();
  return remoteSessionId!;
}

async function expectPathAbsent(candidate: string): Promise<void> {
  try {
    await access(candidate);
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") return;
    throw error;
  }
  throw new Error(`owned workspace directory still exists: ${path.basename(candidate)}`);
}

async function deleteNativeSession(baseUrl: string, remoteSessionId: string, workspaceRoot: string): Promise<void> {
  const url = new URL(`/api/session/${encodeURIComponent(remoteSessionId)}`, `${stripTrailingSlash(baseUrl)}/`);
  const nativeSession = await getNativeV2Session(baseUrl, remoteSessionId);
  if (nativeV2SessionDirectory(nativeSession) !== workspaceRoot) {
    throw new Error("OpenCode V2 session location does not match owned workspace");
  }
  const deleted = await fetch(url, { method: "DELETE", headers: nativeV2AuthHeaders() });
  if (!deleted.ok) {
    throw new Error(`Native OpenCode session delete failed with HTTP ${deleted.status}`);
  }
  const probe = await fetch(url, { method: "GET", headers: nativeV2AuthHeaders() });
  if (probe.status !== 404) {
    throw new Error(`Native OpenCode session still exists after delete: HTTP ${probe.status}`);
  }
}

async function tryCreateTerminalTicket(sessionId: string) {
  try {
    return await apiPost<{ webSocketUrl: string }>(`/api/internal/platform/opencode-runtime/sessions/${sessionId}/terminal/tickets`, { cols: 80, rows: 24 });
  } catch {
    return null;
  }
}

function stripTrailingSlash(value: string) {
  return value.replace(/\/$/, "");
}

type TerminalProbeResult = {
  output: string;
  error?: { code: string; message: string };
};

async function connectTerminalAndEcho(page: Page, webSocketUrl: string, marker: string): Promise<TerminalProbeResult> {
  return await page.evaluate(
    async ({ baseUrl, webSocketUrl, marker }) => {
      const url = new URL(webSocketUrl, baseUrl);
      url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
      return await new Promise<TerminalProbeResult>((resolve) => {
        const socket = new WebSocket(url.toString());
        let output = "";
        let settled = false;
        let inputSent = false;
        let inputTimer: number | undefined;
        const timer = window.setTimeout(() => finish(), 10_000);

        function finish(error?: { code: string; message: string }) {
          if (settled) {
            return;
          }
          settled = true;
          window.clearTimeout(timer);
          if (inputTimer !== undefined) {
            window.clearTimeout(inputTimer);
          }
          try {
            socket.close();
          } catch {
            // Browser close can throw if the connection never opened.
          }
          resolve(error ? { output, error } : { output });
        }

        function sendInput() {
          if (inputSent || socket.readyState !== WebSocket.OPEN) {
            return;
          }
          inputSent = true;
          socket.send(JSON.stringify({ type: "input", data: `printf '${marker}\\n'\n` }));
        }

        socket.onopen = () => {
          // 后端先完成 PTY spawn 再开始消费浏览器输入；启动输出到达即表示消费链路就绪。
          // 对没有 shell 启动输出的环境保留短延迟兜底，且两条路径只发送一次。
          inputTimer = window.setTimeout(sendInput, 500);
        };
        socket.onerror = () => finish({ code: "PTY_SOCKET_ERROR", message: "terminal socket error" });
        socket.onclose = () => finish();
        socket.onmessage = (event) => {
          const message = JSON.parse(String(event.data)) as Record<string, unknown>;
          if (message.type === "output") {
            sendInput();
            output += typeof message.data === "string" ? message.data : "";
            if (output.includes(marker)) {
              socket.send(JSON.stringify({ type: "close", reason: "e2e" }));
              finish();
            }
          }
          if (message.type === "error") {
            finish({
              code: typeof message.code === "string" ? message.code : "PTY_ERROR",
              message: typeof message.message === "string" ? message.message : "terminal error"
            });
          }
        };
      });
    },
    { baseUrl: backendBaseUrl, webSocketUrl, marker }
  );
}
