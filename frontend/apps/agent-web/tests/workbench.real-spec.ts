import { expect, test, type Page } from "@playwright/test";
import { access, rm } from "node:fs/promises";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import path from "node:path";

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

test.describe("phase 11 real service integration", () => {
  test.skip(!runRealE2e, "Set TEST_AGENT_RUN_REAL_E2E=1 to run the real frontend/backend/opencode integration suite.");

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
      await page.goto("/login");
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

type CapturedRunEvent = { seq: number; type: string; payload: Record<string, unknown> };

/** 真实 E2E 只通过平台 RunEvent SSE 收集旁路可见事件，不接触生产浏览器外的 OpenCode 事件流。 */
function captureRunEventsUntilTerminal(runId: string): { finished: Promise<CapturedRunEvent[]> } {
  const controller = new AbortController();
  const finished = new Promise<CapturedRunEvent[]>(async (resolve, reject) => {
    const timeout = setTimeout(() => {
      controller.abort();
      reject(new Error(`side-question Run ${runId} did not reach a terminal event within 120 seconds`));
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
  const { stdout } = await promisify(execFile)("sqlite3", ["-readonly", databasePath, "select id from session;"]);
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
  const url = new URL(`/session/${encodeURIComponent(remoteSessionId)}`, `${stripTrailingSlash(baseUrl)}/`);
  url.searchParams.set("directory", workspaceRoot);
  const deleted = await fetch(url, { method: "DELETE" });
  if (!deleted.ok) {
    throw new Error(`Native OpenCode session delete failed with HTTP ${deleted.status}`);
  }
  const probe = await fetch(url, { method: "GET" });
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
