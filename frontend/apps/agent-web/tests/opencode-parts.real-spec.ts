import { expect, test, type Page } from "@playwright/test";
import { access, mkdtemp, mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import path from "node:path";

import {
  PART_KINDS,
  PART_SPECS,
  assertPartProjection,
  detectSafeRetryProvider,
  interactionExpectation,
  runNaturalAttempt,
  sanitizeEvidence,
  sanitizeTraceText,
  startOwnedTrace,
  waitForCapturedPart,
  writePartEvidence,
  type PartKind
} from "./opencode-parts-real-e2e";
import {
  apiDelete,
  apiGet,
  apiPost,
  assertNativeSessionAbsentInSqlite,
  authHeaders,
  createCleanupScope,
  resolveOwnedOpenCodeDatabase,
  resolveOwnedCleanupPath,
  resolveRemoteSessionIdFromSources,
  runCleanupStages,
  waitForWorkspaceOperation,
  type WorkspaceCreateOperation
} from "./real-e2e-api";

const enabled = process.env.TEST_AGENT_RUN_PART_E2E === "1" && process.env.TEST_AGENT_PART_PHASE === "natural";
const backendBaseUrl = stripTrailingSlash(process.env.TEST_AGENT_BASE_URL ?? "http://127.0.0.1:8080");
const evidenceRoot = path.resolve(process.cwd(), "../.tmp/e2e/opencode-parts");

// 仅本 spec 关闭 runner trace，避免与逐 kind 手动、脱敏后的 trace.zip 双重 ownership。
test.use({ trace: "off" });

test.describe("OpenCode 12 Part natural real E2E", () => {
  test.skip(!enabled, "Set TEST_AGENT_RUN_PART_E2E=1 and TEST_AGENT_PART_PHASE=natural to run natural Part E2E.");

  for (const kind of PART_KINDS) {
    test(`${kind}: exactly one natural trigger`, async ({ page, context }) => {
      test.setTimeout(180_000);
      const fixture = await createWorkspace(kind);
      const title = `e2e-part-${kind}-${fixture.marker}`;
      let sessionId: string | undefined;
      let runId: string | undefined;
      let remoteSessionId: string | undefined;
      let opencodeBaseUrl: string | undefined;
      let openCodeDatabasePath: string | undefined;
      let sse: SseCapture | undefined;
      let primaryError: unknown;
      let evidenceSummary: Record<string, unknown> | undefined;
      let evidenceRunId: string | undefined;
      let cleanupCompleted = false;
      let tracingStarted = false;
      try {
        await startOwnedTrace(
          async () => {
            await context.tracing.start({ screenshots: true, snapshots: true, sources: true });
            tracingStarted = true;
          },
          async () => {
            await cleanupFixture({ fixture, title });
            cleanupCompleted = true;
          }
        );
        const session = await apiPost<{ sessionId: string }>("/api/internal/platform/opencode-runtime/sessions", {
          workspaceId: fixture.workspaceId,
          title
        });
        sessionId = session.sessionId;
        const processInfo = await apiGet<{ port?: number; baseUrl?: string }>("/api/internal/agent/opencode/processes/me");
        if (!processInfo.baseUrl) throw new Error("OpenCode process response has no baseUrl");
        opencodeBaseUrl = stripTrailingSlash(processInfo.baseUrl);
        openCodeDatabasePath = (
          await resolveOwnedOpenCodeDatabase(processInfo, { projectRoot: path.resolve(process.cwd(), "..") })
        ).databasePath;
        const prompt = promptFor(kind, fixture.marker);
        const retryCapability = kind === "retry"
          ? detectSafeRetryProvider(await apiGet(`/api/internal/platform/opencode-runtime/providers?workspaceId=${encodeURIComponent(fixture.workspaceId)}`))
          : undefined;
        const result = await runNaturalAttempt({
          kind,
          timeoutMs: 45_000,
          pollIntervalMs: 500,
          skipReason: kind === "retry" && !retryCapability
            ? "unsafe-provider-injection"
            : kind === "compaction"
              ? "invalid-natural-strategy-consumed-requires-fixture-compact"
              : undefined,
          trigger: async () => {
            const run = await apiPost<{ runId: string }>(
              "/api/internal/agent/opencode/runs",
              runPayload(kind, session.sessionId, prompt, retryCapability)
            );
            runId = run.runId;
            sse = captureRunSse(run.runId, kind);
            return run;
          },
          observe: async () => {
            remoteSessionId ??= await tryResolveRemoteSessionId(session.sessionId);
            if (!remoteSessionId) return { observed: false };
            const raw = await loadNativeMessages(opencodeBaseUrl!, remoteSessionId, fixture.workspaceRootPath);
            return { observed: Boolean(findPartByKind(raw, kind)), rawSnapshot: raw };
          }
        });
        runId ??= result.runId;
        evidenceRunId = runId ?? `skip_${fixture.marker}`;
        await writePartEvidence({ root: evidenceRoot, runId: evidenceRunId, kind, name: "opencode-raw.json", value: result.rawSnapshot ?? [] });
        evidenceSummary = { ...result, projection: "unverified", ui: "unverified", cleanup: "pending" };
        await writePartEvidence({ root: evidenceRoot, runId: evidenceRunId, kind, name: "natural-result.json", value: evidenceSummary });
        if (sse) {
          if (result.classification === "natural-pass") {
            const observedPart = findPartByKind(result.rawSnapshot, kind);
            await sse.waitForPart(kind, String(observedPart?.id ?? ""), 5_000);
          }
          await sse.stop();
          await writeSseEvidence(evidenceRunId, kind, sse.events);
        } else {
          await writeSseEvidence(evidenceRunId, kind, [{ status: "not-started", reason: result.reason }]);
        }

        if (result.classification === "natural-pass") {
          if (!remoteSessionId) throw new Error(`${kind} natural-pass has no remote session id`);
          const rawPart = findPartByKind(result.rawSnapshot, kind);
          if (!rawPart) throw new Error(`${kind} target Part disappeared from raw snapshot`);
          const platformMessages = await apiGet(
            `/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(session.sessionId)}/messages?page=1&size=100&refresh=true`
          );
          const tree = await apiGet(`/api/internal/agent/opencode/sessions/${encodeURIComponent(session.sessionId)}/session-tree/messages`);
          await writePartEvidence({ root: evidenceRoot, runId: evidenceRunId, kind, name: "platform-messages.json", value: platformMessages });
          await writePartEvidence({ root: evidenceRoot, runId: evidenceRunId, kind, name: "platform-tree.json", value: tree });
          assertPartProjection(kind, rawPart, platformMessages, tree);
          evidenceSummary.projection = "pass";
          if (!sse?.containsPart(kind, String((rawPart as { id?: unknown }).id ?? ""))) {
            throw new Error(`${kind} was observed in raw HTTP but not in natural RunEvent SSE`);
          }
          await verifyUi(page, title, kind, String((rawPart as { id?: unknown }).id ?? ""), evidenceRunId, "current-ui.png");
          await page.getByRole("button", { name: /新建对话/ }).first().click();
          await verifyUi(page, title, kind, String((rawPart as { id?: unknown }).id ?? ""), evidenceRunId, "history-ui.png");
          evidenceSummary.ui = "pass";
          await writePartEvidence({ root: evidenceRoot, runId: evidenceRunId, kind, name: "natural-result.json", value: evidenceSummary });
        }
      } catch (error) {
        primaryError = error;
      } finally {
        await sse?.stop().catch(() => undefined);
        try {
          if (!cleanupCompleted) {
            await cleanupFixture({ fixture, sessionId, runId, remoteSessionId, opencodeBaseUrl, openCodeDatabasePath, title });
            cleanupCompleted = true;
          }
          if (evidenceSummary && evidenceRunId) {
            evidenceSummary.cleanup = "pass";
            await writePartEvidence({ root: evidenceRoot, runId: evidenceRunId, kind, name: "natural-result.json", value: evidenceSummary });
          }
        } catch (cleanupError) {
          if (evidenceSummary && evidenceRunId) {
            evidenceSummary.cleanup = "failed";
            await writePartEvidence({ root: evidenceRoot, runId: evidenceRunId, kind, name: "natural-result.json", value: evidenceSummary }).catch(() => undefined);
          }
          primaryError = primaryError
            ? new AggregateError([primaryError, cleanupError], `${kind} verification and cleanup both failed`)
            : cleanupError;
        }
        const traceRunId = runId ?? `skip_${fixture.marker}`;
        const tracePath = path.join(evidenceRoot, traceRunId, kind, "trace.zip");
        await mkdir(path.dirname(tracePath), { recursive: true });
        if (tracingStarted) {
          try {
            await context.tracing.stop({ path: tracePath });
            await sanitizeTraceArchive(tracePath, process.env.TEST_AGENT_API_TOKEN);
          } catch (traceError) {
            primaryError ??= traceError;
          }
        }
      }
      if (primaryError) throw primaryError;
    });
  }
});

function promptFor(kind: PartKind, marker: string): string {
  const prompts: Record<PartKind, string> = {
    text: `Reply with exactly NATURAL_TEXT_${marker}. Do not use tools.`,
    reasoning: `Analyze whether 104729 is prime step by step, then answer NATURAL_REASONING_${marker}.`,
    file: `Read the attached text and reply with its marker NATURAL_FILE_${marker}.`,
    tool: `Use the read tool exactly once to read README.md, then reply NATURAL_TOOL_${marker}.`,
    subtask: `Use the task/subagent tool exactly once for a read-only inspection of README.md, then reply NATURAL_SUBTASK_${marker}.`,
    "step-start": `Use the read tool once on README.md, then reply NATURAL_STEP_START_${marker}.`,
    "step-finish": `Use the read tool once on README.md, then reply NATURAL_STEP_FINISH_${marker}.`,
    snapshot: `Edit e2e-${marker}.txt exactly once to contain NATURAL_SNAPSHOT_${marker}, then stop.`,
    patch: `Edit e2e-${marker}.txt exactly once to contain NATURAL_PATCH_${marker}, then stop.`,
    agent: `Reply with NATURAL_AGENT_${marker}. Do not use tools.`,
    retry: "",
    compaction: `Summarize the current test context and reply NATURAL_COMPACTION_${marker}.`
  };
  return prompts[kind];
}

function runPayload(
  kind: PartKind,
  sessionId: string,
  prompt: string,
  retryCapability?: { providerId: string; statusCode: 429 | 503 }
): Record<string, unknown> {
  const parts: Array<Record<string, unknown>> = [{ type: "text", text: prompt }];
  if (kind === "file") {
    parts.push({ type: "file", mime: "text/plain", filename: "natural-marker.txt", url: `data:text/plain,${encodeURIComponent(prompt)}` });
  }
  return {
    sessionId,
    prompt,
    parts,
    ...(kind === "agent" ? { agent: "build" } : {}),
    ...(retryCapability ? { provider: retryCapability.providerId, e2eFailureStatus: retryCapability.statusCode } : {})
  };
}

async function tryResolveRemoteSessionId(sessionId: string): Promise<string | undefined> {
  try {
    return await resolveRemoteSessionIdFromSources({
      loadTree: () => apiGet(`/api/internal/agent/opencode/sessions/${encodeURIComponent(sessionId)}/session-tree/messages`),
      loadPlatformMessages: () => apiGet(`/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(sessionId)}/messages?page=1&size=100&refresh=true`)
    });
  } catch {
    return undefined;
  }
}

async function loadNativeMessages(baseUrl: string, remoteSessionId: string, directory: string): Promise<unknown> {
  const response = await fetch(`${baseUrl}/session/${encodeURIComponent(remoteSessionId)}/message?directory=${encodeURIComponent(directory)}`);
  if (!response.ok) throw new Error(`OpenCode raw messages failed: ${response.status}`);
  return response.json();
}

function findPartByKind(raw: unknown, kind: PartKind): Record<string, unknown> | undefined {
  if (!Array.isArray(raw)) return undefined;
  for (const message of raw) {
    if (!message || typeof message !== "object") continue;
    const parts = (message as { parts?: unknown }).parts;
    if (!Array.isArray(parts)) continue;
    const found = parts.find((part) => part && typeof part === "object" && (part as { type?: unknown }).type === kind);
    if (found) return found as Record<string, unknown>;
  }
  return undefined;
}

type SseCapture = {
  events: unknown[];
  stop: () => Promise<void>;
  containsPart: (kind: PartKind, partId: string) => boolean;
  waitForPart: (kind: PartKind, partId: string, timeoutMs: number) => Promise<void>;
};
function captureRunSse(runId: string, targetKind: PartKind): SseCapture {
  const controller = new AbortController();
  const events: unknown[] = [];
  const task = (async () => {
    const response = await fetch(`${backendBaseUrl}/api/internal/agent/opencode/runs/${encodeURIComponent(runId)}/events`, {
      headers: { ...authHeaders(), Accept: "text/event-stream" },
      signal: controller.signal
    });
    if (!response.ok || !response.body) throw new Error(`RunEvent SSE failed: ${response.status}`);
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (true) {
      const next = await reader.read();
      if (next.done) break;
      buffer += decoder.decode(next.value, { stream: true });
      const frames = buffer.split("\n\n");
      buffer = frames.pop() ?? "";
      for (const frame of frames) {
        const data = frame.split("\n").filter((line) => line.startsWith("data:")).map((line) => line.slice(5).trim()).join("\n");
        if (!data) continue;
        try { events.push(JSON.parse(data)); } catch { events.push({ raw: data }); }
      }
    }
  })().catch((error) => {
    if (!controller.signal.aborted) events.push({ sseError: error instanceof Error ? error.message : String(error) });
  });
  return {
    events,
    stop: async () => {
      controller.abort();
      // Node fetch 在个别 SSE 半关闭状态下不会及时兑现 abort；证据收集不能因此阻塞 finally 清理。
      await Promise.race([task, new Promise<void>((resolve) => setTimeout(resolve, 1_000))]);
    },
    containsPart: (kind, partId) => {
      const serialized = JSON.stringify(events);
      return serialized.includes(`\"type\":\"${kind}\"`) && (!partId || serialized.includes(partId));
    },
    waitForPart: async (kind, partId, timeoutMs) => {
      await waitForCapturedPart(events, kind, partId, { timeoutMs });
    }
  };
}

async function writeSseEvidence(runId: string, kind: PartKind, events: unknown[]): Promise<void> {
  const target = path.join(evidenceRoot, runId, kind, "run-events.ndjson");
  await mkdir(path.dirname(target), { recursive: true });
  await writeFile(target, events.map((event) => JSON.stringify(sanitizeEvidence(event))).join("\n") + "\n", "utf8");
}

async function verifyUi(page: Page, title: string, kind: PartKind, partId: string, runId: string, screenshot: string): Promise<void> {
  await page.addInitScript((token) => token && sessionStorage.setItem("test-agent.auth.token", token), process.env.TEST_AGENT_API_TOKEN ?? "");
  await page.goto("/", { timeout: 15_000, waitUntil: "domcontentloaded" });
  await page.getByRole("button", { name: /查看消息列表/ }).click({ timeout: 15_000 });
  await page.getByText(title, { exact: true }).click({ timeout: 15_000 });
  const part = page.locator(`[data-part-id='${partId}'][data-part-type='${kind}']`);
  await expect(part).toBeVisible({ timeout: 15_000 });
  await expect(part.locator(".oc-unknown-part")).toHaveCount(0);
  const contract = PART_SPECS.find((item) => item.kind === kind)!.ui.current;
  const childMapping = await part.locator("[data-child-session-id]").count() > 0;
  const diffAvailable = await part.locator("button").count() > 0;
  const interaction = interactionExpectation(contract, {
    targetPartId: partId,
    childMappingPartId: childMapping ? partId : undefined,
    diffAvailable
  });
  if (interaction === "required" && contract.interactionLocator) {
    const target = part.locator(contract.interactionLocator.replaceAll(":scope ", "")).first();
    await expect(target).toBeVisible();
    await target.click();
  }
  const target = path.join(evidenceRoot, runId, kind, screenshot);
  await mkdir(path.dirname(target), { recursive: true });
  await page.screenshot({ path: target, fullPage: true });
}

type Fixture = { marker: string; appId: string; applicationWorkspaceId: string; workspaceId: string; workspaceRootPath: string };
async function createWorkspace(kind: PartKind): Promise<Fixture> {
  const processInfo = await apiGet<{ status?: string; initializable?: boolean }>("/api/internal/agent/opencode/processes/me");
  if (processInfo.status !== "READY" && processInfo.initializable) {
    await apiPost("/api/internal/agent/opencode/processes/me/initialize", {});
  }
  const deadline = Date.now() + 30_000;
  while ((await apiGet<{ status?: string }>("/api/internal/agent/opencode/processes/me")).status !== "READY") {
    if (Date.now() >= deadline) throw new Error("OpenCode process did not become READY within 30 seconds");
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  const marker = `e2e_part_${kind.replace(/-/g, "_")}_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
  const applications = await apiGet<Array<{ appId: string }>>("/api/internal/platform/configuration-management/applications?enabled=true");
  for (const application of applications) {
    const repositories = await apiGet<Array<{ repositoryId: string; englishName?: string; standard: boolean }>>(
      `/api/internal/platform/configuration-management/applications/${encodeURIComponent(application.appId)}/repositories`
    );
    const repository = repositories.find((item) => item.englishName && !item.standard);
    if (!repository) continue;
    const branches = await apiGet<string[]>(`/api/internal/platform/configuration-management/repositories/${encodeURIComponent(repository.repositoryId)}/branches`);
    const configuredWorkspaces = await apiGet<Array<{ repositoryId: string; branch: string }>>(
      `/api/internal/platform/configuration-management/applications/${encodeURIComponent(application.appId)}/workspaces`
    );
    // 配置管理按日期复用 repository checkout，必须沿用该 checkout 已绑定的分支。
    const branch = configuredWorkspaces.find(
      (item) => item.repositoryId === repository.repositoryId
        && branches.includes(item.branch)
        && !(item as { workspaceName?: string }).workspaceName?.startsWith("e2e_part_")
    )?.branch ?? branches[0];
    if (!branch) continue;
    const operationId = `wco_${marker}`;
    const creationScope = createCleanupScope();
    try {
      await apiPost(`/api/internal/platform/configuration-management/applications/${encodeURIComponent(application.appId)}/workspaces`, {
        repositoryId: repository.repositoryId, branch, directoryPath: marker, workspaceName: marker,
        directoryNew: true, version: dateStamp(), operationId
      });
      // POST 接受后 operation 即拥有清理责任，即便后续轮询、version 或 Session 创建失败也能回收记录。
      creationScope.defer(async () => {
        const owned = await apiGet<{ workspaceId?: string | null }>(
          `/api/internal/platform/configuration-management/workspace-create-operations/${encodeURIComponent(operationId)}`
        );
        if (owned.workspaceId) {
          await apiDelete(`/api/internal/platform/configuration-management/applications/${encodeURIComponent(application.appId)}/workspaces/${encodeURIComponent(owned.workspaceId)}`);
        }
      });
      const operation: WorkspaceCreateOperation = await waitForWorkspaceOperation(operationId, {
        getOperation: (id) => apiGet(`/api/internal/platform/configuration-management/workspace-create-operations/${encodeURIComponent(id)}`)
      });
      if (!operation.workspaceId || !operation.versionId) throw new Error(`Workspace ${operationId} has no ids`);
      const versions = await apiGet<Array<{ versionId: string; workspaceRootPath: string; runtimeWorkspace?: { workspaceId?: string } }>>(
        `/api/internal/platform/workspace-management/applications/${encodeURIComponent(application.appId)}/workspace-templates/${encodeURIComponent(operation.workspaceId)}/versions`
      );
      const version = versions.find((item) => item.versionId === operation.versionId);
      if (!version?.runtimeWorkspace?.workspaceId || !version.workspaceRootPath) throw new Error(`Workspace ${operationId} has no runtime workspace`);
      creationScope.defer(async () => {
        const ownedRoot = path.resolve(process.cwd(), "../.testagent/agent-opencode/workspace");
        const safe = await resolveOwnedCleanupPath(version.workspaceRootPath, ownedRoot, marker);
        await rm(safe, { recursive: true, force: true });
      });
      if (kind === "snapshot" || kind === "patch") await writeFile(path.join(version.workspaceRootPath, `e2e-${marker}.txt`), "before\n", "utf8");
      creationScope.release();
      return { marker, appId: application.appId, applicationWorkspaceId: operation.workspaceId, workspaceId: version.runtimeWorkspace.workspaceId, workspaceRootPath: version.workspaceRootPath };
    } catch (error) {
      try {
        await creationScope.cleanup();
      } catch (cleanupError) {
        throw new AggregateError([error, cleanupError], `Workspace fixture ${operationId} failed and cleanup also failed`);
      }
      throw error;
    }
  }
  throw new Error("No enabled application repository is available for natural Part E2E");
}

async function cleanupFixture(input: {
  fixture: Fixture;
  sessionId?: string;
  runId?: string;
  remoteSessionId?: string;
  opencodeBaseUrl?: string;
  openCodeDatabasePath?: string;
  title: string;
}): Promise<void> {
  await runCleanupStages([
    async () => { if (input.runId) await apiPost(`/api/internal/agent/opencode/runs/${encodeURIComponent(input.runId)}/cancel`, {}).catch(() => undefined); },
    async () => {
      if (!input.sessionId || input.remoteSessionId) return;
      try {
        input.remoteSessionId = await resolveRemoteSessionIdFromSources({
          loadTree: () => apiGet(`/api/internal/agent/opencode/sessions/${encodeURIComponent(input.sessionId!)}/session-tree/messages`),
          loadPlatformMessages: () => apiGet(`/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(input.sessionId!)}/messages?page=1&size=100&refresh=true`)
        });
      } catch {
        if (!input.openCodeDatabasePath) throw new Error("cannot prove native Session absence without owned SQLite path");
        await assertNoNativeSessionForDirectory(input.openCodeDatabasePath, input.fixture.workspaceRootPath);
      }
    },
    async () => {
      if (!input.remoteSessionId || !input.opencodeBaseUrl) return;
      const nativeUrl = `${input.opencodeBaseUrl}/session/${encodeURIComponent(input.remoteSessionId)}?directory=${encodeURIComponent(input.fixture.workspaceRootPath)}`;
      const response = await fetch(nativeUrl, { method: "DELETE" });
      if (!response.ok && response.status !== 404) throw new Error(`OpenCode session delete failed: ${response.status}`);
      const probe = await fetch(nativeUrl);
      if (probe.status !== 404) throw new Error(`OpenCode session still accessible after delete: ${probe.status}`);
      if (!input.openCodeDatabasePath) throw new Error("owned OpenCode SQLite path was not resolved before cleanup");
      await assertNativeSessionAbsentInSqlite(input.openCodeDatabasePath, input.remoteSessionId);
    },
    async () => {
      if (input.sessionId) {
        await apiDelete(`/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(input.sessionId)}`);
        await expect(apiGet(`/api/internal/platform/opencode-runtime/sessions/${encodeURIComponent(input.sessionId)}`)).rejects.toThrow();
      }
      const history = await apiGet<{ items?: Array<{ title?: string }> }>(`/api/internal/platform/opencode-runtime/workspaces/${encodeURIComponent(input.fixture.workspaceId)}/sessions?page=1&size=100`);
      if (history.items?.some((item) => item.title === input.title)) throw new Error(`platform history still contains ${input.title}`);
    },
    async () => {
      await apiDelete(`/api/internal/platform/configuration-management/applications/${encodeURIComponent(input.fixture.appId)}/workspaces/${encodeURIComponent(input.fixture.applicationWorkspaceId)}`);
      const workspaces = await apiGet<Array<{ workspaceId?: string }>>(
        `/api/internal/platform/configuration-management/applications/${encodeURIComponent(input.fixture.appId)}/workspaces`
      );
      if (workspaces.some((item) => item.workspaceId === input.fixture.applicationWorkspaceId)) {
        throw new Error(`configuration workspace still exists: ${input.fixture.applicationWorkspaceId}`);
      }
    },
    async () => {
      const ownedRoot = path.resolve(process.cwd(), "../.testagent/agent-opencode/workspace");
      const safe = await resolveOwnedCleanupPath(input.fixture.workspaceRootPath, ownedRoot, input.fixture.marker);
      await rm(safe, { recursive: true, force: true });
      await expect(access(safe)).rejects.toThrow();
    }
  ]);
}

async function assertNoNativeSessionForDirectory(databasePath: string, directory: string): Promise<void> {
  const exec = promisify(execFile);
  const escaped = directory.replaceAll("'", "''");
  const { stdout } = await exec("sqlite3", ["-readonly", databasePath, `SELECT count(*) FROM session WHERE directory='${escaped}';`]);
  const count = Number(stdout.trim());
  if (!Number.isInteger(count) || count !== 0) throw new Error(`native SQLite still contains ${count} Session(s) for owned directory`);
}

function dateStamp(): string { const d = new Date(); return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}`; }
function stripTrailingSlash(value: string): string { return value.replace(/\/+$/, ""); }

/** Playwright trace 会记录请求头；解包后逐文件替换本轮 token，再重建归档。 */
async function sanitizeTraceArchive(tracePath: string, token?: string): Promise<void> {
  const exec = promisify(execFile);
  const directory = await mkdtemp(path.join(path.dirname(tracePath), ".trace-sanitize-"));
  try {
    await exec("unzip", ["-q", tracePath, "-d", directory]);
    const files = await listFiles(directory);
    for (const file of files) {
      const content = await readFile(file);
      const text = content.toString("utf8");
      const isText = Buffer.from(text, "utf8").equals(content);
      if (isText) {
        await writeFile(file, sanitizeTraceText(text, token), "utf8");
      } else if (containsTraceSecret(content, token)) {
        throw new Error(`binary trace attachment contains unverifiable sensitive data: ${path.basename(file)}`);
      }
    }
    await rm(tracePath, { force: true });
    await exec("zip", ["-q", "-r", tracePath, "."], { cwd: directory });
    for (const file of await listFiles(directory)) {
      const content = await readFile(file);
      if (containsTraceSecret(content, token)) throw new Error(`sanitized trace still contains sensitive data: ${path.basename(file)}`);
    }
  } catch (error) {
    await rm(tracePath, { force: true });
    throw error;
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
}

function containsTraceSecret(content: Buffer, token?: string): boolean {
  const text = content.toString("latin1");
  if (token && text.includes(token)) return true;
  return /(authorization|proxy-authorization|cookie|set-cookie|token|key|secret)\s*[:=]/i.test(text);
}

async function listFiles(directory: string): Promise<string[]> {
  const result: string[] = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const candidate = path.join(directory, entry.name);
    if (entry.isDirectory()) result.push(...await listFiles(candidate));
    else if (entry.isFile()) result.push(candidate);
  }
  return result;
}
