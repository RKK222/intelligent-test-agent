import { describe, expect, it, vi } from "vitest";
import {
  PART_KINDS,
  PART_SPECS,
  assertPartProjection,
  evidencePath,
  findPlatformMessagePart,
  findRawPart,
  findTreeMessagePart,
  sanitizeEvidence,
  selectPartFields,
  interactionExpectation,
  runNaturalAttempt,
  writePartEvidence,
  detectSafeRetryProvider,
  buildCompactionPreparation,
  startOwnedTrace,
  sanitizeTraceText,
  waitForCapturedPart,
  buildNativePartFixture,
  buildNativeFixtureSql,
  createNativeFixtureController,
  executeNativeFixtureSql,
  buildNativeFixtureCleanupSql,
  executeNativeFixtureCleanupSql
} from "./opencode-parts-real-e2e";
import { mkdtemp, mkdir, readFile, rm } from "node:fs/promises";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import os from "node:os";
import path from "node:path";

const base = { id: "part_1", partID: "part_1", partId: "part_1", sessionID: "ses_1", messageID: "msg_1" };
const ownedNativeSessionId = "ses_00000000000100000000000000";
const ids = ["id", "sessionID", "messageID", "type"];
const expectedProjectionFields = {
  text: [...ids, "text", "synthetic", "ignored", "time.start", "time.end", "metadata"],
  subtask: [...ids, "prompt", "description", "agent", "model.providerID", "model.modelID", "command"],
  reasoning: [...ids, "text", "metadata", "time.start", "time.end"],
  file: [...ids, "mime", "filename", "url", "source.type", "source.text.value", "source.text.start", "source.text.end", "source.path", "source.range.start.line", "source.range.start.character", "source.range.end.line", "source.range.end.character", "source.name", "source.kind", "source.clientName", "source.uri"],
  tool: [...ids, "callID", "tool", "state.status", "state.input", "state.raw", "state.title", "state.output", "state.error", "state.metadata", "state.time.start", "state.time.end", "state.time.compacted", "state.attachments", "metadata"],
  "step-start": [...ids, "snapshot"],
  "step-finish": [...ids, "reason", "snapshot", "cost", "tokens.total", "tokens.input", "tokens.output", "tokens.reasoning", "tokens.cache.read", "tokens.cache.write"],
  snapshot: [...ids, "snapshot"],
  patch: [...ids, "hash", "files"],
  agent: [...ids, "name", "source.value", "source.start", "source.end"],
  retry: [...ids, "attempt", "error.name", "error.data.message", "error.data.statusCode", "error.data.isRetryable", "error.data.responseHeaders", "error.data.responseBody", "error.data.metadata", "time.created"],
  compaction: [...ids, "auto", "overflow", "tail_start_id"]
} as const;

const samples = {
  text: { ...base, type: "text", text: "e2e text", synthetic: true, ignored: false, time: { start: 1, end: 2 }, metadata: { marker: "text" } },
  subtask: { ...base, type: "subtask", prompt: "inspect", description: "review", agent: "explore", model: { providerID: "p", modelID: "m" }, command: "check" },
  reasoning: { ...base, type: "reasoning", text: "reason", metadata: { marker: "reasoning" }, time: { start: 1, end: 2 } },
  file: { ...base, type: "file", mime: "text/plain", filename: "marker.txt", url: "data:text/plain,marker", source: { type: "file", path: "marker.txt", text: { value: "@marker.txt", start: 0, end: 11 } } },
  tool: { ...base, type: "tool", callID: "call_1", tool: "read", metadata: { marker: "tool" }, state: { status: "completed", input: { filePath: "marker.txt" }, output: "marker", title: "Read", metadata: { child: "value" }, time: { start: 1, end: 2, compacted: 3 }, attachments: [{ ...base, id: "attachment_1", type: "file", mime: "text/plain", filename: "attached.txt", url: "data:text/plain,attached" }] } },
  "step-start": { ...base, type: "step-start", snapshot: "snap-start" },
  "step-finish": { ...base, type: "step-finish", reason: "stop", snapshot: "snap-end", cost: 0.1, tokens: { total: 6, input: 1, output: 2, reasoning: 3, cache: { read: 4, write: 5 } } },
  snapshot: { ...base, type: "snapshot", snapshot: "snapshot-marker" },
  patch: { ...base, type: "patch", hash: "hash-marker", files: ["marker.txt"] },
  agent: { ...base, type: "agent", name: "build", source: { value: "@build", start: 0, end: 6 } },
  retry: { ...base, type: "retry", attempt: 1, error: { name: "APIError", data: { message: "retry-marker", statusCode: 429, isRetryable: true, responseHeaders: { retryAfter: "1" }, responseBody: "busy", metadata: { provider: "test" } } }, time: { created: 1 } },
  compaction: { ...base, type: "compaction", auto: true, overflow: false, tail_start_id: "part_tail" }
} as const;

describe("OpenCode 1.17.7 Part 契约", () => {
  it("自然触发每类恰好发送一次请求，观察到目标后立即通过", async () => {
    let polls = 0;
    const trigger = vi.fn().mockResolvedValue({ runId: "run_once" });
    const result = await runNaturalAttempt({
      kind: "tool",
      timeoutMs: 45_000,
      pollIntervalMs: 1,
      trigger,
      observe: async () => ({ observed: ++polls === 2, rawSnapshot: { polls } }),
      sleep: async () => undefined,
      now: (() => { let value = 0; return () => value += 10; })()
    });
    expect(trigger).toHaveBeenCalledOnce();
    expect(result).toMatchObject({ classification: "natural-pass", runId: "run_once", rawSnapshot: { polls: 2 } });
  });

  it("45秒内未观察到目标时不重试模型并保留最后原始快照", async () => {
    const trigger = vi.fn().mockResolvedValue({ runId: "run_timeout" });
    const result = await runNaturalAttempt({
      kind: "subtask",
      timeoutMs: 45,
      pollIntervalMs: 1,
      trigger,
      observe: async () => ({ observed: false, rawSnapshot: { marker: "last" } }),
      sleep: async () => undefined,
      now: (() => { let value = 0; return () => value += 20; })()
    });
    expect(trigger).toHaveBeenCalledOnce();
    expect(result).toEqual({
      classification: "native-fixture-required",
      kind: "subtask",
      runId: "run_timeout",
      reason: "not-observed-within-45ms",
      rawSnapshot: { marker: "last" }
    });
  });

  it("不安全的 retry provider 注入直接分类且不发送模型请求", async () => {
    const trigger = vi.fn();
    const result = await runNaturalAttempt({
      kind: "retry",
      timeoutMs: 45_000,
      trigger,
      skipReason: "unsafe-provider-injection",
      observe: async () => ({ observed: false }),
      sleep: async () => undefined
    });
    expect(trigger).not.toHaveBeenCalled();
    expect(result).toMatchObject({ classification: "native-fixture-required", reason: "unsafe-provider-injection" });
  });

  it("只把显式声明测试隔离且可控429/503的provider视为安全retry能力", () => {
    expect(detectSafeRetryProvider([{ id: "normal" }, { id: "e2e", metadata: { testIsolated: true, controllableStatusCodes: [429, 503] } }])).toEqual({ providerId: "e2e", statusCode: 429 });
    expect(detectSafeRetryProvider([{ id: "normal", metadata: { controllableStatusCodes: [429] } }])).toBeUndefined();
  });

  it("compaction准备最多50条且总字符不超过48000", () => {
    const prepared = buildCompactionPreparation(Array.from({ length: 60 }, (_, index) => `message-${index}-${"x".repeat(1_000)}`));
    expect(prepared.length).toBeLessThanOrEqual(50);
    expect(prepared.reduce((sum, item) => sum + item.length, 0)).toBeLessThanOrEqual(48_000);
    expect(prepared.at(-1)).toContain("message-59");
  });

  it("tracing启动失败时仍立即清理已移交的Workspace", async () => {
    const cleanup = vi.fn().mockResolvedValue(undefined);
    await expect(startOwnedTrace(async () => { throw new Error("trace start failed"); }, cleanup)).rejects.toThrow("trace start failed");
    expect(cleanup).toHaveBeenCalledOnce();
  });

  it("trace文本按结构和header模式删除全部敏感值", () => {
    const source = [
      JSON.stringify({ headers: { Authorization: "Bearer abc", Cookie: "sid=x", safe: "ok" }, nested: { token: "abc" } }),
      "Set-Cookie: sid=x; HttpOnly",
      "Proxy-Authorization=Basic xyz",
      "secret: hidden"
    ].join("\n");
    const sanitized = sanitizeTraceText(source, "abc");
    expect(sanitized).toContain("ok");
    expect(sanitized).not.toMatch(/abc|sid=x|Basic xyz|hidden/);
    expect(sanitized).not.toMatch(/Authorization|Cookie|token|secret/i);
  });

  it("raw命中后等待SSE目标part而不是立即abort", async () => {
    const events: unknown[] = [];
    const sleep = vi.fn().mockImplementation(async () => { events.push({ payload: { part: { id: "prt_1", type: "tool" } } }); });
    await expect(waitForCapturedPart(events, "tool", "prt_1", { timeoutMs: 10, sleep, now: (() => { let n = 0; return () => ++n; })() })).resolves.toBeUndefined();
    expect(sleep).toHaveBeenCalled();
  });

  it("按 run/kind 隔离写入已脱敏且非空的证据", async () => {
    const root = await mkdtemp(path.join(os.tmpdir(), "part-evidence-"));
    try {
      const file = await writePartEvidence({
        root,
        runId: "run_evidence",
        kind: "text",
        name: "opencode-raw.json",
        value: { Authorization: "Bearer secret", nested: { token: "secret", text: "visible" } }
      });
      expect(JSON.parse(await readFile(file, "utf8"))).toEqual({ nested: { text: "visible" } });
      expect(file).toContain("run_evidence/text/opencode-raw.json");
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });
  it("严格包含官方顺序的 12 种 Part", () => {
    expect(PART_KINDS).toEqual(["text", "subtask", "reasoning", "file", "tool", "step-start", "step-finish", "snapshot", "patch", "agent", "retry", "compaction"]);
    expect(PART_SPECS).toHaveLength(12);
    expect(PART_SPECS.map((item) => item.kind)).toEqual(PART_KINDS);
  });

  it.each(PART_KINDS)("%s 定义完整原生字段和 timeline 契约", (kind) => {
    const spec = PART_SPECS.find((item) => item.kind === kind)!;
    expect(spec.requiredFields).toContain("id");
    expect(spec.requiredFields).toContain("sessionID");
    expect(spec.requiredFields).toContain("messageID");
    expect(spec.requiredFields).toContain("type");
    expect(spec.projectionFields.length).toBeGreaterThanOrEqual(spec.requiredFields.length);
    expect(spec.projectionFields).toEqual(expectedProjectionFields[kind]);
    expect(spec.ui.current.locators.length).toBeGreaterThan(0);
    expect(spec.ui.history.locators.length).toBeGreaterThan(0);
    expect(spec.ui.history.timelineExpectation).toBe(spec.ui.current.timelineExpectation);
  });

  it("只要求 OpenCode 原生 assistant timeline 实际映射的四类 Part 可见", () => {
    const visible = PART_SPECS.filter((item) => item.ui.current.timelineExpectation === "visible").map((item) => item.kind);
    expect(visible).toEqual(["text", "reasoning", "tool", "compaction"]);
    for (const spec of PART_SPECS) {
      expect(spec.ui.current.forbiddenLocator).toBe(spec.ui.current.timelineExpectation === "visible" ? ".oc-unknown-part" : undefined);
    }
  });

  it("为适用交互提供确定 locator", () => {
    expect(PART_SPECS.find((item) => item.kind === "text")?.ui.current.interactionLocator).toBe(":scope button[aria-label='复制']");
    for (const spec of PART_SPECS.filter((item) => item.ui.current.interaction !== "none")) {
      expect(spec.ui.current.interactionLocator).toBeTruthy();
      expect(spec.ui.current.interactionLocator).toMatch(/^:scope(?:\b|\[|\s)/);
      expect(spec.ui.history.interactionLocator).toBe(spec.ui.current.interactionLocator);
    }
  });

  it.each(PART_KINDS)("%s 主 locator 同时绑定目标 partId 与 type", (kind) => {
    const primary = PART_SPECS.find((item) => item.kind === kind)!.ui.current.locators[0];
    expect(primary).toContain("[data-part-id='{partId}']");
    expect(primary).toContain(`[data-part-type='${kind}']`);
  });

  it.each(["text", "reasoning", "file", "tool"] as const)("%s locator/交互不跨目标容器", (kind) => {
    const contract = PART_SPECS.find((item) => item.kind === kind)!.ui.current;
    expect(contract.locators).toHaveLength(1);
    if (contract.interaction !== "none") expect(contract.interactionLocator).toMatch(/^:scope(?:\b|\[|\s)/);
  });

  it("subtask 原生 Part 不借 task tool 卡片伪装成 timeline 展示", () => {
    const contract = PART_SPECS.find((item) => item.kind === "subtask")!.ui.current;
    expect(contract.locators).toEqual(["[data-part-id='{partId}'][data-part-type='subtask']"]);
    expect(contract.locators).not.toContain(".oc-subagent-card");
    expect(contract.timelineExpectation).toBe("not-rendered");
    expect(interactionExpectation(contract, { targetPartId: "part_1" })).toBe("n/a");
  });

  it("patch 原生 Part 不直接渲染，diff 由消息 summary 单独验收", () => {
    const contract = PART_SPECS.find((item) => item.kind === "patch")!.ui.current;
    expect(contract.timelineExpectation).toBe("not-rendered");
    expect(interactionExpectation(contract, { diffAvailable: true })).toBe("n/a");
    expect(interactionExpectation(contract, { diffAvailable: false })).toBe("n/a");
  });

  it.each(["subtask", "step-start", "step-finish", "snapshot", "patch", "agent", "retry", "compaction"] as const)("%s 原生扩展 Part locator 均绑定目标 partId", (kind) => {
    const contract = PART_SPECS.find((item) => item.kind === kind)!.ui.current;
    expect(contract.locators.every((locator) => locator.includes("{partId}"))).toBe(true);
    if (kind === "retry") expect(contract.locators).not.toContain(".oc-retry-row");
  });

  it("step-start 对齐原生同步层，不额外制造可见标记", () => {
    const spec = PART_SPECS.find((item) => item.kind === "step-start")!;
    expect(spec.ui.current.locators).toContain("[data-part-id='{partId}'][data-part-type='step-start']");
    expect(spec.ui.current.timelineExpectation).toBe("not-rendered");
    expect(spec.ui.current.semantic).toContain("跳过");
  });

  it.each(PART_KINDS)("%s 按官方关键字段选择且缺字段失败", (kind) => {
    const selected = selectPartFields(kind, samples[kind]);
    expect(selected.type).toBe(kind);
    expect(() => selectPartFields(kind, { ...samples[kind], id: undefined })).toThrow(/id/);
  });
});

describe("三层 Part 定位与投影", () => {
  it("分别从原生消息、平台消息和真实 tree shape 定位", () => {
    const part = samples.text;
    expect(findRawPart([{ info: { id: "msg_1" }, parts: [part] }], "part_1")).toBe(part);
    expect(findPlatformMessagePart({ items: [{ messageId: "msg_1", parts: [part] }] }, "part_1")).toBe(part);
    expect(findTreeMessagePart({ messagesBySessionId: { ses_1: [{ messageID: "msg_1", part }] } }, "msg_1", "part_1")).toBe(part);
  });

  it.each(PART_KINDS)("%s 对 raw→messages 和 raw→tree 独立比较", (kind) => {
    const raw = samples[kind];
    const platform = { ...raw, partID: raw.id, partId: raw.id, messageId: raw.messageID };
    expect(() => assertPartProjection(kind, raw, { items: [{ parts: [platform] }] }, { messagesBySessionId: { ses_1: [{ messageID: raw.messageID, part: platform }] } })).not.toThrow();
    expect(() => assertPartProjection(kind, raw, { items: [{ parts: [{ ...platform, type: "broken" }] }] }, { messagesBySessionId: { ses_1: [{ messageID: raw.messageID, part: platform }] } })).toThrow(/platform messages/);
    expect(() => assertPartProjection(kind, raw, { items: [{ parts: [platform] }] }, { messagesBySessionId: { ses_1: [{ messageID: raw.messageID, part: { ...platform, type: "broken" } }] } })).toThrow(/platform tree/);
  });

  it.each(PART_KINDS)("%s 任一已出现官方字段在 messages/tree 丢失都会失败", (kind) => {
    const raw = samples[kind];
    const spec = PART_SPECS.find((item) => item.kind === kind)!;
    for (const field of spec.projectionFields.filter((path) => pathValue(raw, path) !== undefined)) {
      const messagesPart = structuredClone(raw) as Record<string, unknown>;
      deletePath(messagesPart, field);
      expect(() => assertPartProjection(kind, raw, { items: [{ parts: [messagesPart] }] }, treeWith(raw))).toThrow(/platform messages/);

      const treePart = structuredClone(raw) as Record<string, unknown>;
      deletePath(treePart, field);
      expect(() => assertPartProjection(kind, raw, { items: [{ parts: [raw] }] }, treeWith(treePart))).toThrow(/platform tree/);
    }
  });

  it("file source 的 file/symbol/resource 三种官方变体均无损选择", () => {
    const sources = [
      { type: "file", path: "a.ts", text: { value: "@a.ts", start: 0, end: 5 } },
      { type: "symbol", path: "a.ts", text: { value: "symbol", start: 0, end: 6 }, range: { start: { line: 1, character: 2 }, end: { line: 3, character: 4 } }, name: "fn", kind: 12 },
      { type: "resource", clientName: "mcp", uri: "resource://a", text: { value: "resource", start: 0, end: 8 } }
    ];
    for (const source of sources) expect(selectPartFields("file", { ...samples.file, source }).source).toEqual(source);
  });

  it("tool pending/running/completed/error 四种 state 官方字段均无损选择", () => {
    const states = [
      { status: "pending", input: { a: 1 }, raw: "raw-input" },
      { status: "running", input: { a: 1 }, title: "Running", metadata: { a: 1 }, time: { start: 1 } },
      samples.tool.state,
      { status: "error", input: { a: 1 }, error: "failed", metadata: { a: 1 }, time: { start: 1, end: 2 } }
    ];
    for (const state of states) expect(selectPartFields("tool", { ...samples.tool, state }).state).toEqual(state);
  });
});

function treeWith(part: Record<string, unknown>) {
  return { messagesBySessionId: { ses_1: [{ messageID: "msg_1", part }] } };
}

function pathValue(value: unknown, field: string): unknown {
  return field.split(".").reduce<unknown>((current, key) => current && typeof current === "object" ? (current as Record<string, unknown>)[key] : undefined, value);
}

function deletePath(value: Record<string, unknown>, field: string): void {
  const segments = field.split(".");
  const parent = segments.slice(0, -1).reduce<unknown>((current, key) => current && typeof current === "object" ? (current as Record<string, unknown>)[key] : undefined, value);
  if (parent && typeof parent === "object") delete (parent as Record<string, unknown>)[segments.at(-1)!];
}

describe("证据安全", () => {
  it("递归脱敏对象和数组中的鉴权、Cookie、token、key、secret", () => {
    expect(sanitizeEvidence({ Authorization: "Bearer x", nested: [{ cookie: "c", token: "t", key: "k", secret: "s", safe: "ok" }] })).toEqual({ nested: [{ safe: "ok" }] });
  });

  it("脱敏 retry responseHeaders 的代理鉴权、Set-Cookie 和大小写连字符变体", () => {
    expect(sanitizeEvidence({
      error: { data: { responseHeaders: { "Set-Cookie": "sid=x", "proxy-Authorization": "Basic x", "X-API-KEY": "key", "x-auth-token": "token", "Content-Type": "application/json" } } }
    })).toEqual({ error: { data: { responseHeaders: { "Content-Type": "application/json" } } } });
  });

  it("不会因包含 key/token/cookie/secret 字样过度删除普通字段", () => {
    const ordinary = { keyboardLayout: "us", monkeyCount: 2, tokenCount: 3, cookiePolicy: "strict", secretariat: "office", authorizationMode: "rbac" };
    expect(sanitizeEvidence(ordinary)).toEqual(ordinary);
  });

  it("生成逐 run/逐 kind 证据路径并拒绝目录穿越", () => {
    expect(evidencePath("run_1", "text", "raw.json")).toBe(".tmp/e2e/opencode-parts/run_1/text/raw.json");
    expect(() => evidencePath("../run", "text", "raw.json")).toThrow(/unsafe/);
    expect(() => evidencePath("run_1", "text", "../tool/raw.json")).toThrow(/unsafe/);
    expect(() => evidencePath("run_1", "unknown" as never, "raw.json")).toThrow(/kind/);
  });

  it.each(["line\nbreak", "tab\tname", "中文", "emoji😀", "space name", "a/b", "a\\b", ".", "..", ""])("证据路径拒绝非 ASCII allowlist 段 %j", (segment) => {
    expect(() => evidencePath(segment, "text", "raw.json")).toThrow(/unsafe/);
    expect(() => evidencePath("run_1", "text", segment)).toThrow(/unsafe/);
  });
});

describe("OpenCode 原生 SQLite fixture", () => {
  it.each(PART_KINDS)("%s 构造官方完整 payload 与唯一 manifest", (kind) => {
    const fixture = buildNativePartFixture({
      kind,
      marker: "e2e_part_fixture_abc",
      remoteSessionId: ownedNativeSessionId,
      directory: "/owned/e2e_part_fixture_abc",
      title: "e2e-part-fixture-abc",
      now: 1_700_000_000_000
    });
    expect(fixture.manifest).toMatchObject({ marker: "e2e_part_fixture_abc", kind, remoteSessionId: ownedNativeSessionId });
    expect(fixture.parentMessage.id).toMatch(/^msg_[0-9a-f]{12}[0-9A-Za-z]{14}$/);
    expect(fixture.message.id).toMatch(/^msg_[0-9a-f]{12}[0-9A-Za-z]{14}$/);
    expect(fixture.part.id).toMatch(/^prt_[0-9a-f]{12}[0-9A-Za-z]{14}$/);
    expect(fixture.parentMessage.id < fixture.message.id).toBe(true);
    expect(selectPartFields(kind, fixture.part).type).toBe(kind);
    expect(fixture.message.data).toMatchObject({ role: "assistant", parentID: expect.stringMatching(/^msg_/), finish: "stop" });
  });

  it("生成带busy timeout、外键与立即事务的schema匹配SQL", () => {
    const fixture = buildNativePartFixture({
      kind: "retry", marker: "e2e_part_fixture_sql", remoteSessionId: ownedNativeSessionId,
      directory: "/owned/e2e_part_fixture_sql", title: "e2e-part-fixture-sql", now: 1
    });
    const sql = buildNativeFixtureSql(fixture);
    expect(sql).toContain(".timeout 5000");
    expect(sql).toContain("PRAGMA foreign_keys=ON");
    expect(sql).toContain("BEGIN IMMEDIATE");
    expect(sql).not.toContain("INSERT INTO session");
    expect(sql).toContain("fixture_session_guard");
    expect(sql).toContain("INSERT INTO message");
    expect(sql).toContain("INSERT INTO part");
    expect(sql.trimEnd().endsWith("COMMIT;")).toBe(true);
  });

  it.each(["bad", "e2e_part_other"])("拒绝不属于本轮manifest的marker %s", (marker) => {
    expect(() => buildNativePartFixture({
      kind: "text", marker, remoteSessionId: ownedNativeSessionId,
      directory: "/owned/e2e_part_fixture_safe", title: "e2e-part-fixture-safe", now: 1
    })).toThrow(/marker|manifest/);
  });

  it("HTTP probe成功后才移交fixture，cleanup幂等且中断恢复可执行", async () => {
    const executeSql = vi.fn().mockResolvedValue(undefined);
    const probe = vi.fn().mockResolvedValue(true);
    const remove = vi.fn().mockResolvedValue(undefined);
    const persistManifest = vi.fn().mockResolvedValue(undefined);
    const clearManifest = vi.fn().mockResolvedValue(undefined);
    const controller = createNativeFixtureController({ executeSql, probe, remove, persistManifest, clearManifest, sleep: async () => undefined });
    const fixture = buildNativePartFixture({
      kind: "compaction", marker: "e2e_part_fixture_recovery", remoteSessionId: ownedNativeSessionId,
      directory: "/owned/e2e_part_fixture_recovery", title: "e2e-part-fixture-recovery", now: 1
    });
    const owned = await controller.insert(fixture);
    expect(executeSql).toHaveBeenCalledOnce();
    expect(persistManifest).toHaveBeenCalledBefore(executeSql);
    expect(probe).toHaveBeenCalled();
    await owned.cleanup();
    await owned.cleanup();
    expect(remove).toHaveBeenCalledOnce();
    expect(clearManifest).toHaveBeenCalledOnce();
    await controller.recover(fixture.manifest);
    expect(remove).toHaveBeenCalledTimes(2);
  });

  it("probe失败时自动清理并汇总原始错误", async () => {
    const remove = vi.fn().mockResolvedValue(undefined);
    const fixture = buildNativePartFixture({
      kind: "text", marker: "e2e_part_fixture_probe", remoteSessionId: ownedNativeSessionId,
      directory: "/owned/e2e_part_fixture_probe", title: "e2e-part-fixture-probe", now: 1
    });
    const controller = createNativeFixtureController({
      executeSql: vi.fn().mockResolvedValue(undefined), probe: vi.fn().mockResolvedValue(false), remove,
      persistManifest: vi.fn().mockResolvedValue(undefined), clearManifest: vi.fn().mockResolvedValue(undefined), sleep: async () => undefined,
      probeTimeoutMs: 2, now: (() => { let n = 0; return () => ++n; })()
    });
    await expect(controller.insert(fixture)).rejects.toThrow(/probe/);
    expect(remove).toHaveBeenCalledOnce();
  });

  it("真实sqlite3在part写入失败时回滚session/message且cleanup三重匹配", async () => {
    const root = await mkdtemp(path.join(os.tmpdir(), "native-fixture-sqlite-"));
    const database = path.join(root, "opencode", "opencode.db");
    await mkdir(path.dirname(database), { recursive: true });
    const exec = promisify(execFile);
    const schema = [
      "PRAGMA foreign_keys=ON;",
      "CREATE TABLE project (id text PRIMARY KEY);",
      "CREATE TABLE session (id text PRIMARY KEY, project_id text NOT NULL, slug text NOT NULL, directory text NOT NULL, title text NOT NULL, version text NOT NULL, time_created integer NOT NULL, time_updated integer NOT NULL, FOREIGN KEY(project_id) REFERENCES project(id) ON DELETE CASCADE);",
      "CREATE TABLE message (id text PRIMARY KEY, session_id text NOT NULL, time_created integer NOT NULL, time_updated integer NOT NULL, data text NOT NULL, FOREIGN KEY(session_id) REFERENCES session(id) ON DELETE CASCADE);",
      "CREATE TABLE part (id text PRIMARY KEY, message_id text NOT NULL, session_id text NOT NULL, time_created integer NOT NULL, time_updated integer NOT NULL, data text NOT NULL, FOREIGN KEY(message_id) REFERENCES message(id) ON DELETE CASCADE);",
      "INSERT INTO project(id) VALUES ('project_fixture');",
      `INSERT INTO session(id,project_id,slug,directory,title,version,time_created,time_updated) VALUES ('${ownedNativeSessionId}','project_fixture','owned','/owned/e2e_part_fixture_rollback','e2e-part-fixture-rollback','1.17.7',1,1);`,
      "CREATE TRIGGER reject_fixture_part BEFORE INSERT ON part BEGIN SELECT RAISE(ABORT, 'fixture rejection'); END;"
    ].join("\n");
    await exec("sqlite3", [database, schema]);
    const trigger = await exec("sqlite3", [database, "SELECT count(*) FROM sqlite_master WHERE type='trigger' AND name='reject_fixture_part';"]);
    expect(trigger.stdout.trim()).toBe("1");
    const fixture = buildNativePartFixture({
      kind: "tool", marker: "e2e_part_fixture_rollback", remoteSessionId: ownedNativeSessionId,
      directory: "/owned/e2e_part_fixture_rollback", title: "e2e-part-fixture-rollback", now: 1
    });
    try {
      await expect(executeNativeFixtureSql(database, fixture)).rejects.toThrow();
      const { stdout } = await exec("sqlite3", ["-separator", "|", database, "SELECT (SELECT count(*) FROM session),(SELECT count(*) FROM message),(SELECT count(*) FROM part);"]);
      expect(stdout.trim()).toBe("1|0|0");
      const cleanupSql = buildNativeFixtureCleanupSql(fixture.manifest);
      expect(cleanupSql).toContain(`id='${fixture.manifest.remoteSessionId}'`);
      expect(cleanupSql).toContain(`title='${fixture.manifest.title}'`);
      expect(cleanupSql).toContain(`directory='${fixture.manifest.directory}'`);
      await exec("sqlite3", [database, "DROP TRIGGER reject_fixture_part;"]);
      await executeNativeFixtureSql(database, fixture);
      const inserted = await exec("sqlite3", ["-separator", "|", database, "SELECT (SELECT count(*) FROM session),(SELECT count(*) FROM message),(SELECT count(*) FROM part);"]);
      expect(inserted.stdout.trim()).toBe("1|2|1");
      // 模拟写入后进程崩溃：manifest 保留；若 Session 所有权谓词被篡改，恢复必须失败且不能清 manifest。
      const clearManifest = vi.fn().mockResolvedValue(undefined);
      const recovery = createNativeFixtureController({
        executeSql: vi.fn(), probe: vi.fn(), remove: (manifest) => executeNativeFixtureCleanupSql(database, manifest),
        persistManifest: vi.fn(), clearManifest
      });
      await exec("sqlite3", [database, `UPDATE session SET title='predicate-mismatch' WHERE id='${fixture.manifest.remoteSessionId}';`]);
      await expect(recovery.recover(fixture.manifest)).rejects.toThrow(/CHECK constraint|transaction failed/);
      expect(clearManifest).not.toHaveBeenCalled();
      const retained = await exec("sqlite3", ["-separator", "|", database, "SELECT (SELECT count(*) FROM session),(SELECT count(*) FROM message),(SELECT count(*) FROM part);"]);
      expect(retained.stdout.trim()).toBe("1|2|1");
      await exec("sqlite3", [database, `UPDATE session SET title='${fixture.manifest.title}' WHERE id='${fixture.manifest.remoteSessionId}';`]);
      await recovery.recover(fixture.manifest);
      expect(clearManifest).toHaveBeenCalledOnce();
      const cleaned = await exec("sqlite3", ["-separator", "|", database, "SELECT (SELECT count(*) FROM session),(SELECT count(*) FROM message),(SELECT count(*) FROM part);"]);
      expect(cleaned.stdout.trim()).toBe("1|0|0");
      await exec("sqlite3", [database, `DELETE FROM session WHERE id='${fixture.manifest.remoteSessionId}';`]);
      await expect(executeNativeFixtureSql(database, fixture)).rejects.toThrow(/CHECK constraint|transaction failed/);
      const refused = await exec("sqlite3", ["-separator", "|", database, "SELECT (SELECT count(*) FROM session),(SELECT count(*) FROM message),(SELECT count(*) FROM part);"]);
      expect(refused.stdout.trim()).toBe("0|0|0");
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });
});
