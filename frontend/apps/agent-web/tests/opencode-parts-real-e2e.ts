import { posix as path } from "node:path";
import { dirname, join } from "node:path";
import { chmod, lstat, mkdir, open, readFile, realpath, rename, unlink, writeFile } from "node:fs/promises";
import { isDeepStrictEqual } from "node:util";
import { spawn } from "node:child_process";
import { randomBytes } from "node:crypto";
import os from "node:os";
import { resolveOwnedOpenCodeDatabase, type MyOpenCodeProcess, type OpenCodeDatabaseLocationOptions } from "./real-e2e-api";

const verifiedDatabases = new WeakSet<object>();
const databaseHandleBrand = Symbol("verified-native-database");

export type VerifiedNativeDatabase = Readonly<{ databasePath: string; sessionPath: string; [databaseHandleBrand]: true }>;

export const PART_KINDS = [
  "text",
  "subtask",
  "reasoning",
  "file",
  "tool",
  "step-start",
  "step-finish",
  "snapshot",
  "patch",
  "agent",
  "retry",
  "compaction"
] as const;

export type PartKind = (typeof PART_KINDS)[number];
type JsonRecord = Record<string, unknown>;

export type NaturalAttemptResult = {
  classification: "natural-pass" | "native-fixture-required";
  kind: PartKind;
  runId?: string;
  reason?: string;
  rawSnapshot?: unknown;
};

export type NaturalAttemptOptions = {
  kind: PartKind;
  timeoutMs?: number;
  pollIntervalMs?: number;
  trigger: () => Promise<{ runId: string }>;
  observe: (runId: string) => Promise<{ observed: boolean; rawSnapshot?: unknown }>;
  skipReason?: string;
  sleep?: (delayMs: number) => Promise<void>;
  now?: () => number;
};

export type NativeFixtureManifest = {
  marker: string;
  kind: PartKind;
  remoteSessionId: string;
  messageId: string;
  parentMessageId: string;
  partId: string;
  directory: string;
  title: string;
};

export type NativePartFixture = {
  manifest: NativeFixtureManifest;
  parentMessage: { id: string; sessionId: string; timeCreated: number; timeUpdated: number; data: JsonRecord };
  message: { id: string; sessionId: string; timeCreated: number; timeUpdated: number; data: JsonRecord };
  part: JsonRecord;
};

export type NativeFixtureControllerOptions = {
  executeSql: (sql: string) => Promise<void>;
  probe: (manifest: NativeFixtureManifest) => Promise<boolean>;
  remove: (manifest: NativeFixtureManifest) => Promise<void>;
  persistManifest: (manifest: NativeFixtureManifest) => Promise<void>;
  clearManifest: (manifest: NativeFixtureManifest) => Promise<void>;
  sleep?: (delayMs: number) => Promise<void>;
  now?: () => number;
  probeTimeoutMs?: number;
  probeIntervalMs?: number;
};

export type NativeFixtureManifestStore = {
  path: string;
  write: (manifest: NativeFixtureManifest) => Promise<void>;
  read: () => Promise<NativeFixtureManifest | undefined>;
  clear: (expected?: NativeFixtureManifest) => Promise<void>;
};

export type PartUiContract = {
  locators: readonly string[];
  semantic: string;
  interaction: "copy" | "expand" | "open-diff" | "subagent-navigation" | "none";
  interactionLocator?: string;
  interactionRequirement: "always" | "child-mapping" | "diff-available" | "never";
  /** 与 OpenCode 原生 assistant timeline 对齐，而不是把所有数据 Part 都强制画成卡片。 */
  timelineExpectation: "visible" | "not-rendered";
  forbiddenLocator?: ".oc-unknown-part";
};

export type PartSpec = {
  kind: PartKind;
  requiredFields: readonly string[];
  projectionFields: readonly string[];
  ui: { current: PartUiContract; history: PartUiContract };
};

/**
 * 原生 SQLite fixture 在浏览器侧的精确验收锚点。不要使用虚构的统一 data-part-id：
 * timeline 实际按各类 renderer 分组/折叠，必须用该类真实写入的唯一字段定位。
 */
export type FixtureUiProbe = Readonly<{
  uniqueText: string;
  /** 只有 OpenCode 原生 assistant timeline 真的绘制该 Part 时才有 renderer。 */
  timelineExpectation: PartUiContract["timelineExpectation"];
  rendererSelector?: string;
  unknownSelector: ".oc-unknown-part";
}>;

export function fixtureUiProbe(kind: PartKind, marker: string): FixtureUiProbe {
  const native = `NATIVE_${kind.replaceAll("-", "_").toUpperCase()}_${marker}`;
  const probes: Record<PartKind, FixtureUiProbe> = {
    text: { uniqueText: native, timelineExpectation: "visible", rendererSelector: ".oc-text-part", unknownSelector: ".oc-unknown-part" },
    // SubtaskPart 本身不绘制；真实子 Agent 入口由同一轮的 tool=task 卡片单独验收。
    subtask: { uniqueText: native, timelineExpectation: "not-rendered", unknownSelector: ".oc-unknown-part" },
    reasoning: { uniqueText: native, timelineExpectation: "visible", rendererSelector: ".oc-reasoning-part", unknownSelector: ".oc-unknown-part" },
    file: { uniqueText: `${marker}.txt`, timelineExpectation: "not-rendered", unknownSelector: ".oc-unknown-part" },
    tool: { uniqueText: marker, timelineExpectation: "visible", rendererSelector: "[data-testid='oc-tool-group']", unknownSelector: ".oc-unknown-part" },
    "step-start": { uniqueText: `snapshot-start-${marker}`, timelineExpectation: "not-rendered", unknownSelector: ".oc-unknown-part" },
    "step-finish": { uniqueText: `snapshot-end-${marker}`, timelineExpectation: "not-rendered", unknownSelector: ".oc-unknown-part" },
    snapshot: { uniqueText: `snapshot-${marker}`, timelineExpectation: "not-rendered", unknownSelector: ".oc-unknown-part" },
    patch: { uniqueText: `hash-${marker}`, timelineExpectation: "not-rendered", unknownSelector: ".oc-unknown-part" },
    agent: { uniqueText: native, timelineExpectation: "not-rendered", unknownSelector: ".oc-unknown-part" },
    // 产品已有 retry 错误行是对 session.status.retry 的兼容展示，不能因为 OpenCode 原生 app
    // 未单列 RetryPart renderer 而把既有失败信息藏掉。
    retry: { uniqueText: `retry-${marker}`, timelineExpectation: "visible", rendererSelector: ".oc-retry-row", unknownSelector: ".oc-unknown-part" },
    // compaction 的唯一 DOM 锚点由真实 Part ID 组成；uniqueText 保留其真实 tail 字段用于证据检查。
    compaction: { uniqueText: "上下文已压缩", timelineExpectation: "visible", rendererSelector: "[data-testid='compaction-part-{partId}']", unknownSelector: ".oc-unknown-part" }
  };
  return probes[kind];
}

const common = ["id", "sessionID", "messageID", "type"] as const;

/**
 * OpenCode 1.17.7 的 12 类 Part 验收矩阵。所有类型都必须在 raw/messages/tree 中无损；
 * assistant timeline 只对齐原生实际渲染的 text/reasoning/tool/compaction；retry 是本产品
 * 已有的失败兼容行，须继续展示，其他 Part 不因“类型齐全”额外制造可见卡片。
 */
export const PART_SPECS: readonly PartSpec[] = [
  spec("text", ["text"], ["text", "synthetic", "ignored", "time.start", "time.end", "metadata"], "visible", "最终文本可见", "copy", "always", ":scope button[aria-label='复制']"),
  spec("subtask", ["prompt", "description", "agent"], ["prompt", "description", "agent", "model.providerID", "model.modelID", "command"], "not-rendered", "原生 assistant timeline 不直接渲染 SubtaskPart", "none", "never"),
  spec("reasoning", ["text", "time.start"], ["text", "metadata", "time.start", "time.end"], "visible", "非空 reasoning 在启用摘要时可展开", "expand", "always", ":scope .oc-disclosure__trigger"),
  spec("file", ["mime", "url"], ["mime", "filename", "url", "source.type", "source.text.value", "source.text.start", "source.text.end", "source.path", "source.range.start.line", "source.range.start.character", "source.range.end.line", "source.range.end.character", "source.name", "source.kind", "source.clientName", "source.uri"], "not-rendered", "FilePart 作为输入附件存在，不在 assistant timeline 单独渲染", "none", "never"),
  spec("tool", ["callID", "tool", "state.status", "state.input"], ["callID", "tool", "state.status", "state.input", "state.raw", "state.title", "state.output", "state.error", "state.metadata", "state.time.start", "state.time.end", "state.time.compacted", "state.attachments", "metadata"], "visible", "非隐藏工具显示名称、状态与输出", "expand", "always", ":scope .oc-tool-group__trigger, :scope .oc-disclosure__trigger"),
  spec("step-start", [], ["snapshot"], "not-rendered", "原生同步层跳过 step-start", "none", "never"),
  spec("step-finish", ["reason", "cost", "tokens.input", "tokens.output", "tokens.reasoning", "tokens.cache.read", "tokens.cache.write"], ["reason", "snapshot", "cost", "tokens.total", "tokens.input", "tokens.output", "tokens.reasoning", "tokens.cache.read", "tokens.cache.write"], "not-rendered", "原生同步层跳过 step-finish", "none", "never"),
  spec("snapshot", ["snapshot"], ["snapshot"], "not-rendered", "原生 assistant timeline 无 SnapshotPart renderer", "none", "never"),
  spec("patch", ["hash", "files"], ["hash", "files"], "not-rendered", "原生同步层跳过 PatchPart，diff 使用消息 summary", "none", "never"),
  spec("agent", ["name"], ["name", "source.value", "source.start", "source.end"], "not-rendered", "AgentPart 用于输入引用，不在 assistant timeline 单独渲染", "none", "never"),
  spec("retry", ["attempt", "error.name", "error.data.message", "error.data.isRetryable", "time.created"], ["attempt", "error.name", "error.data.message", "error.data.statusCode", "error.data.isRetryable", "error.data.responseHeaders", "error.data.responseBody", "error.data.metadata", "time.created"], "visible", "保留平台已有 retry 错误行，避免隐藏可恢复失败信息", "none", "never"),
  spec("compaction", ["auto"], ["auto", "overflow", "tail_start_id"], "visible", "上下文压缩显示为原生分隔线", "none", "never")
];

/**
 * 构造仅属于本轮测试的 OpenCode 原生记录。数据库 JSON 与 OpenCode hydrate 规则一致：
 * message/part 的主键和外键放表列，data 只保存 schema 自身字段。
 */
export function buildNativePartFixture(input: {
  kind: PartKind;
  marker: string;
  remoteSessionId: string;
  directory: string;
  title: string;
  now?: number;
}): NativePartFixture {
  assertFixtureOwnership(input);
  const now = input.now ?? Date.now();
  const parentId = openCodeId("msg", now, 1);
  const messageId = openCodeId("msg", now, 2);
  const partId = openCodeId("prt", now, 3);
  const base = { id: partId, sessionID: input.remoteSessionId, messageID: messageId, type: input.kind };
  const part = nativePartPayload(input.kind, base, input.marker, now, parentId);
  // 该 assistant 已显式完成，避免测试 fixture 被 OpenCode 当作待续写工作。
  const messageData: JsonRecord = {
    role: "assistant",
    time: { created: now, completed: now },
    parentID: parentId,
    modelID: "e2e-fixture-model",
    providerID: "e2e-fixture-provider",
    mode: "build",
    agent: "build",
    path: { cwd: input.directory, root: input.directory },
    cost: 0,
    tokens: { total: 0, input: 0, output: 0, reasoning: 0, cache: { read: 0, write: 0 } },
    finish: "stop"
  };
  return {
    manifest: { marker: input.marker, kind: input.kind, remoteSessionId: input.remoteSessionId, messageId, parentMessageId: parentId, partId, directory: input.directory, title: input.title },
    parentMessage: {
      id: parentId,
      sessionId: input.remoteSessionId,
      timeCreated: now,
      timeUpdated: now,
      data: { role: "user", time: { created: now }, agent: "build", model: { providerID: "e2e-fixture-provider", modelID: "e2e-fixture-model" } }
    },
    message: { id: messageId, sessionId: input.remoteSessionId, timeCreated: now, timeUpdated: now, data: messageData },
    part
  };
}

/** 生成由 sqlite3 CLI 原子执行的脚本；任一句失败时 CLI 会在连接关闭时回滚未提交事务。 */
export function buildNativeFixtureSql(fixture: NativePartFixture): string {
  assertFixtureManifest(fixture);
  const partData = { ...fixture.part };
  delete partData.id;
  delete partData.sessionID;
  delete partData.messageID;
  return [
    ".timeout 5000",
    "PRAGMA foreign_keys=ON;",
    "BEGIN IMMEDIATE;",
    "CREATE TEMP TABLE fixture_session_guard (value INTEGER NOT NULL CHECK(value=1));",
    `INSERT INTO fixture_session_guard SELECT count(*) FROM session WHERE id=${sql(fixture.manifest.remoteSessionId)} AND title=${sql(fixture.manifest.title)} AND directory=${sql(fixture.manifest.directory)};`,
    `INSERT INTO message (id, session_id, time_created, time_updated, data) VALUES (${sql(fixture.parentMessage.id)}, ${sql(fixture.parentMessage.sessionId)}, ${fixture.parentMessage.timeCreated}, ${fixture.parentMessage.timeUpdated}, ${sql(JSON.stringify(fixture.parentMessage.data))});`,
    `INSERT INTO message (id, session_id, time_created, time_updated, data) VALUES (${sql(fixture.message.id)}, ${sql(fixture.message.sessionId)}, ${fixture.message.timeCreated}, ${fixture.message.timeUpdated}, ${sql(JSON.stringify(fixture.message.data))});`,
    `INSERT INTO part (id, message_id, session_id, time_created, time_updated, data) VALUES (${sql(fixture.manifest.partId)}, ${sql(fixture.manifest.messageId)}, ${sql(fixture.manifest.remoteSessionId)}, ${fixture.message.timeCreated}, ${fixture.message.timeUpdated}, ${sql(JSON.stringify(partData))});`,
    "COMMIT;"
  ].join("\n");
}

/** 通过 argv 调用 sqlite3，不经过 shell；SQL 中所有动态文本均由 buildNativeFixtureSql 转义。 */
export async function executeNativeFixtureSql(database: VerifiedNativeDatabase, fixture: NativePartFixture): Promise<void> {
  const databasePath = verifiedDatabasePath(database);
  // dot-command 作为 argv 会吞掉后续换行 SQL，必须通过 stdin；spawn 仍是无 shell argv 调用。
  await runSqliteScript(databasePath, buildNativeFixtureSql(fixture));
}

/** 中断恢复保留真实 Session，仅删除 manifest 拥有的 user/assistant/part，并在提交前证明残留为零。 */
export function buildNativeFixtureCleanupSql(manifest: NativeFixtureManifest): string {
  assertFixtureOwnership(manifest);
  return [
    ".timeout 5000",
    "PRAGMA foreign_keys=ON;",
    "BEGIN IMMEDIATE;",
    "CREATE TEMP TABLE fixture_cleanup_guard (value INTEGER NOT NULL CHECK(value=1));",
    `INSERT INTO fixture_cleanup_guard SELECT count(*) FROM session WHERE id=${sql(manifest.remoteSessionId)} AND title=${sql(manifest.title)} AND directory=${sql(manifest.directory)};`,
    `DELETE FROM part WHERE id=${sql(manifest.partId)} AND message_id=${sql(manifest.messageId)} AND session_id=${sql(manifest.remoteSessionId)};`,
    `DELETE FROM message WHERE id IN (${sql(manifest.messageId)}, ${sql(manifest.parentMessageId)}) AND session_id=${sql(manifest.remoteSessionId)};`,
    "CREATE TEMP TABLE fixture_residue_guard (value INTEGER NOT NULL CHECK(value=0));",
    `INSERT INTO fixture_residue_guard SELECT (SELECT count(*) FROM part WHERE id=${sql(manifest.partId)} OR (message_id=${sql(manifest.messageId)} AND session_id=${sql(manifest.remoteSessionId)})) + (SELECT count(*) FROM message WHERE id IN (${sql(manifest.messageId)}, ${sql(manifest.parentMessageId)}) AND session_id=${sql(manifest.remoteSessionId)});`,
    "COMMIT;"
  ].join("\n");
}

/** 对已通过 manager 归属校验的数据库执行 manifest 三重匹配清理。 */
export async function executeNativeFixtureCleanupSql(database: VerifiedNativeDatabase, manifest: NativeFixtureManifest): Promise<void> {
  const databasePath = verifiedDatabasePath(database);
  await runSqliteScript(databasePath, buildNativeFixtureCleanupSql(manifest));
}

/** 生产真实 E2E 只能通过 Task2 的 manager state/PID/realpath 全链路校验获得句柄。 */
export async function resolveVerifiedNativeDatabase(
  process: MyOpenCodeProcess,
  options: OpenCodeDatabaseLocationOptions
): Promise<VerifiedNativeDatabase> {
  const resolved = await resolveOwnedOpenCodeDatabase(process, options);
  return registerVerifiedDatabase(resolved.databasePath, resolved.sessionPath);
}

/** 仅供单元测试：限定系统临时根、真实 opencode/opencode.db 且逐段拒绝符号链接。 */
export async function createTestVerifiedNativeDatabase(tempRoot: string, databasePath: string): Promise<VerifiedNativeDatabase> {
  const canonicalTmp = await realpath(os.tmpdir());
  const canonicalRoot = await realpath(tempRoot);
  if (!isChildPath(canonicalTmp, canonicalRoot)) throw new Error("test database root must be owned by the system temp directory");
  await assertNoSymlinkPath(tempRoot, databasePath);
  const canonicalDatabase = await realpath(databasePath);
  if (!isChildPath(canonicalRoot, canonicalDatabase) || !canonicalDatabase.endsWith(`${path.sep}opencode${path.sep}opencode.db`)) {
    throw new Error("test database must be an owned opencode/opencode.db");
  }
  return registerVerifiedDatabase(canonicalDatabase, canonicalRoot);
}

/** durable manifest 使用同目录临时文件、file/dir fsync 与 tombstone 恢复窗口。 */
export function createNativeFixtureManifestStore(database: VerifiedNativeDatabase, name: string): NativeFixtureManifestStore {
  const databasePath = verifiedDatabasePath(database);
  if (!/^[A-Za-z0-9._-]+\.json$/.test(name) || name === ".json") throw new Error("unsafe native fixture manifest name");
  const directory = join(dirname(databasePath), ".e2e-manifests");
  const target = join(directory, name);
  const temporary = `${target}.tmp`;
  const clearing = `${target}.clearing`;
  return {
    path: target,
    async write(manifest) {
      validateManifest(manifest);
      await mkdir(directory, { recursive: true, mode: 0o700 });
      await chmod(directory, 0o700);
      await assertManifestPathsSafe(directory, [target, temporary, clearing]);
      if (await readManifestIfPresent(target) || await readManifestIfPresent(clearing)) {
        throw new Error("native fixture manifest already owns an unfinished cleanup");
      }
      await unlink(temporary).catch(ignoreMissing);
      const file = await open(temporary, "wx", 0o600);
      try {
        await file.writeFile(`${JSON.stringify(manifest)}\n`, "utf8");
        await file.sync();
      } finally {
        await file.close();
      }
      await rename(temporary, target);
      await chmod(target, 0o600);
      await fsyncDirectory(directory);
    },
    async read() {
      await mkdir(directory, { recursive: true, mode: 0o700 });
      await assertManifestPathsSafe(directory, [target, temporary, clearing]);
      const main = await readManifestIfPresent(target);
      const tombstone = await readManifestIfPresent(clearing);
      if (main && tombstone) throw new Error("native fixture manifest has ambiguous crash state");
      return main ?? tombstone;
    },
    async clear(expected) {
      await assertManifestPathsSafe(directory, [target, temporary, clearing]);
      const current = await this.read();
      if (!current) return;
      if (expected && !isDeepStrictEqual(current, expected)) throw new Error("native fixture manifest does not match cleanup owner");
      if (await exists(target)) {
        await rename(target, clearing);
        await fsyncDirectory(directory);
      }
      await unlink(clearing).catch(ignoreMissing);
      await fsyncDirectory(directory);
    }
  };
}

/**
 * 管理 fixture 的所有权移交和中断恢复。manifest 必须先落盘，进程在 SQL 后中断时，
 * 下一次运行才能依据唯一 ID 执行同一清理程序。
 */
export function createNativeFixtureController(options: NativeFixtureControllerOptions) {
  const sleep = options.sleep ?? ((delayMs: number) => new Promise<void>((resolve) => setTimeout(resolve, delayMs)));
  const now = options.now ?? Date.now;
  const probeTimeoutMs = options.probeTimeoutMs ?? 5_000;
  const probeIntervalMs = options.probeIntervalMs ?? 100;
  async function cleanup(manifest: NativeFixtureManifest): Promise<void> {
    await options.remove(manifest);
    await options.clearManifest(manifest);
  }
  return {
    async insert(fixture: NativePartFixture): Promise<{ fixture: NativePartFixture; cleanup: () => Promise<void> }> {
      assertFixtureManifest(fixture);
      await options.persistManifest(fixture.manifest);
      try {
        await options.executeSql(buildNativeFixtureSql(fixture));
        const deadline = now() + probeTimeoutMs;
        while (now() <= deadline) {
          if (await options.probe(fixture.manifest)) {
            let cleaned = false;
            return {
              fixture,
              cleanup: async () => {
                if (cleaned) return;
                await cleanup(fixture.manifest);
                cleaned = true;
              }
            };
          }
          await sleep(probeIntervalMs);
        }
        throw new Error(`native fixture HTTP probe timed out for ${fixture.manifest.kind}`);
      } catch (error) {
        try {
          await cleanup(fixture.manifest);
        } catch (cleanupError) {
          throw new AggregateError([error, cleanupError], "native fixture insertion failed and cleanup also failed");
        }
        throw error;
      }
    },
    recover: cleanup
  };
}

function nativePartPayload(kind: PartKind, base: JsonRecord, marker: string, now: number, tailStartMessageId: string): JsonRecord {
  const variants: Record<PartKind, JsonRecord> = {
    text: { ...base, text: `NATIVE_TEXT_${marker}`, synthetic: false, ignored: false, time: { start: now, end: now }, metadata: { fixture: marker } },
    subtask: { ...base, prompt: `NATIVE_SUBTASK_${marker}`, description: "fixture subtask", agent: "build", model: { providerID: "e2e-fixture-provider", modelID: "e2e-fixture-model" }, command: "fixture" },
    reasoning: { ...base, text: `NATIVE_REASONING_${marker}`, metadata: { fixture: marker }, time: { start: now, end: now } },
    file: { ...base, mime: "text/plain", filename: `${marker}.txt`, url: `data:text/plain,${marker}`, source: { type: "file", path: `${marker}.txt`, text: { value: `@${marker}.txt`, start: 0, end: marker.length + 5 } } },
    tool: { ...base, callID: `call_${marker}`, tool: "read", state: { status: "completed", input: { filePath: `${marker}.txt` }, output: marker, title: "Read fixture", metadata: { fixture: marker }, time: { start: now, end: now, compacted: now }, attachments: [] }, metadata: { fixture: marker } },
    "step-start": { ...base, snapshot: `snapshot-start-${marker}` },
    "step-finish": { ...base, reason: "stop", snapshot: `snapshot-end-${marker}`, cost: 0, tokens: { total: 0, input: 0, output: 0, reasoning: 0, cache: { read: 0, write: 0 } } },
    snapshot: { ...base, snapshot: `snapshot-${marker}` },
    patch: { ...base, hash: `hash-${marker}`, files: [`${marker}.txt`] },
    agent: { ...base, name: `NATIVE_AGENT_${marker}`, source: { value: "@build", start: 0, end: 6 } },
    retry: { ...base, attempt: 1, error: { name: "APIError", data: { message: `retry-${marker}`, statusCode: 429, isRetryable: true, responseHeaders: { "retry-after": "1" }, responseBody: "fixture busy", metadata: { fixture: marker } } }, time: { created: now } },
    compaction: { ...base, auto: true, overflow: false, tail_start_id: tailStartMessageId }
  };
  return variants[kind];
}

function assertFixtureOwnership(input: { marker: string; remoteSessionId: string; directory: string; title: string }): void {
  if (!/^e2e_part_[a-z0-9_]+$/.test(input.marker)) throw new Error("fixture marker is not owned by the E2E namespace");
  // remote Session ID 由 OpenCode 生成时不携带 marker；只校验官方前缀，所有权由独立目录段和唯一标题绑定。
  const titleOwnsMarker = input.title.includes(input.marker) || input.title.includes(input.marker.replaceAll("_", "-"));
  if (!/^ses_[0-9a-f]{12}[0-9A-Za-z]{14}$/.test(input.remoteSessionId) || !input.directory.split(/[\\/]/).includes(input.marker) || !titleOwnsMarker) {
    throw new Error("fixture manifest resources do not share the unique marker");
  }
}

function assertFixtureManifest(fixture: NativePartFixture): void {
  assertFixtureOwnership({ marker: fixture.manifest.marker, remoteSessionId: fixture.manifest.remoteSessionId, directory: fixture.manifest.directory, title: fixture.manifest.title });
  if (fixture.parentMessage.id !== fixture.manifest.parentMessageId || fixture.message.id !== fixture.manifest.messageId || fixture.part.id !== fixture.manifest.partId
    || fixture.parentMessage.sessionId !== fixture.manifest.remoteSessionId || fixture.message.sessionId !== fixture.manifest.remoteSessionId
    || fixture.message.data.parentID !== fixture.manifest.parentMessageId) {
    throw new Error("fixture records do not match manifest ownership");
  }
  for (const [prefix, value] of [["msg", fixture.manifest.parentMessageId], ["msg", fixture.manifest.messageId], ["prt", fixture.manifest.partId]] as const) {
    if (!new RegExp(`^${prefix}_[0-9a-f]{12}[0-9A-Za-z]{14}$`).test(value)) throw new Error(`fixture ${prefix} ID does not match OpenCode format`);
  }
}

/** 与 OpenCode Identifier.create 一致：ascending 时间段加 14 个独立加密随机字节映射 Base62。 */
function openCodeId(prefix: "msg" | "prt", timestamp: number, sequence: number): string {
  const encoded = (BigInt(timestamp) * 0x1000n + BigInt(sequence)).toString(16).padStart(12, "0").slice(-12);
  const alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  const tail = [...randomBytes(14)].map((value) => alphabet[value % alphabet.length]).join("");
  return `${prefix}_${encoded}${tail}`;
}

function sql(value: string): string {
  return `'${value.replaceAll("'", "''")}'`;
}

function runSqliteScript(databasePath: string, script: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const child = spawn("sqlite3", ["-bail", databasePath], { stdio: ["pipe", "pipe", "pipe"] });
    let stderr = "";
    child.stderr.setEncoding("utf8");
    child.stderr.on("data", (chunk: string) => { stderr += chunk; });
    child.once("error", reject);
    child.once("close", (code) => {
      if (code === 0) resolve();
      else reject(new Error(`sqlite3 fixture transaction failed (${code ?? "signal"}): ${stderr.trim()}`));
    });
    child.stdin.end(script);
  });
}

function registerVerifiedDatabase(databasePath: string, sessionPath: string): VerifiedNativeDatabase {
  const handle = Object.freeze({ databasePath, sessionPath, [databaseHandleBrand]: true as const });
  verifiedDatabases.add(handle);
  return handle;
}

function verifiedDatabasePath(handle: VerifiedNativeDatabase): string {
  if (!handle || typeof handle !== "object" || !verifiedDatabases.has(handle) || handle[databaseHandleBrand] !== true) {
    throw new Error("native database handle was not produced by an ownership-verifying factory");
  }
  return handle.databasePath;
}

function isChildPath(root: string, candidate: string): boolean {
  const relative = path.relative(root, candidate);
  return Boolean(relative) && relative !== ".." && !relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative);
}

async function assertNoSymlinkPath(root: string, candidate: string): Promise<void> {
  const relative = path.relative(root, candidate);
  if (!relative || relative === ".." || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) throw new Error("test database escapes owned root");
  let cursor = root;
  for (const segment of relative.split(path.sep)) {
    cursor = join(cursor, segment);
    if ((await lstat(cursor)).isSymbolicLink()) throw new Error("test database path contains a symbolic link");
  }
}

async function assertManifestPathsSafe(directory: string, files: string[]): Promise<void> {
  if ((await lstat(directory)).isSymbolicLink()) throw new Error("native fixture manifest directory is a symbolic link");
  for (const file of files) {
    try {
      if ((await lstat(file)).isSymbolicLink()) throw new Error("native fixture manifest path is a symbolic link");
    } catch (error) {
      if (!isMissing(error)) throw error;
    }
  }
}

async function fsyncDirectory(directory: string): Promise<void> {
  const handle = await open(directory, "r");
  try { await handle.sync(); } finally { await handle.close(); }
}

async function readManifestIfPresent(file: string): Promise<NativeFixtureManifest | undefined> {
  let raw: string;
  try { raw = await readFile(file, "utf8"); } catch (error) {
    if (isMissing(error)) return undefined;
    throw error;
  }
  let value: unknown;
  try { value = JSON.parse(raw); } catch { throw new Error(`native fixture manifest is corrupted: ${file}`); }
  validateManifest(value);
  return value;
}

function validateManifest(value: unknown): asserts value is NativeFixtureManifest {
  if (!value || typeof value !== "object") throw new Error("native fixture manifest must be an object");
  const manifest = value as Partial<NativeFixtureManifest>;
  if (!PART_KINDS.includes(manifest.kind as PartKind)
    || typeof manifest.marker !== "string" || typeof manifest.remoteSessionId !== "string"
    || typeof manifest.messageId !== "string" || typeof manifest.parentMessageId !== "string" || typeof manifest.partId !== "string"
    || typeof manifest.directory !== "string" || typeof manifest.title !== "string") {
    throw new Error("native fixture manifest schema is invalid");
  }
  assertFixtureOwnership(manifest as NativeFixtureManifest);
  if (!/^msg_[0-9a-f]{12}[0-9A-Za-z]{14}$/.test(manifest.messageId)
    || !/^msg_[0-9a-f]{12}[0-9A-Za-z]{14}$/.test(manifest.parentMessageId)
    || !/^prt_[0-9a-f]{12}[0-9A-Za-z]{14}$/.test(manifest.partId)) throw new Error("native fixture manifest IDs are invalid");
}

async function exists(file: string): Promise<boolean> {
  try { await lstat(file); return true; } catch (error) { if (isMissing(error)) return false; throw error; }
}

function ignoreMissing(error: unknown): void {
  if (!isMissing(error)) throw error;
}

function isMissing(error: unknown): boolean {
  return Boolean(error && typeof error === "object" && "code" in error && (error as { code?: unknown }).code === "ENOENT");
}

function partLocator(kind: PartKind): string {
  return `[data-part-id='{partId}'][data-part-type='${kind}']`;
}

function spec(
  kind: PartKind,
  requiredFields: readonly string[],
  projectionFields: readonly string[],
  timelineExpectation: PartUiContract["timelineExpectation"],
  semantic: string,
  interaction: PartUiContract["interaction"],
  interactionRequirement: PartUiContract["interactionRequirement"],
  interactionLocator?: string
): PartSpec {
  const contract: PartUiContract = {
    locators: [partLocator(kind)],
    semantic,
    interaction,
    interactionLocator,
    interactionRequirement,
    timelineExpectation,
    forbiddenLocator: timelineExpectation === "visible" ? ".oc-unknown-part" : undefined
  };
  return {
    kind,
    requiredFields: [...common, ...requiredFields],
    projectionFields: [...common, ...projectionFields],
    ui: { current: contract, history: { ...contract } }
  };
}

/** 根据真实资源上下文判定条件交互；缺 child/diff 时明确记为 N/A，而不是伪造失败。 */
export function interactionExpectation(
  contract: PartUiContract,
  context: { targetPartId?: string; childMappingPartId?: string; diffAvailable?: boolean }
): "required" | "n/a" {
  if (contract.interactionRequirement === "always") return "required";
  if (contract.interactionRequirement === "child-mapping") {
    return context.targetPartId !== undefined && context.childMappingPartId === context.targetPartId ? "required" : "n/a";
  }
  if (contract.interactionRequirement === "diff-available") return context.diffAvailable ? "required" : "n/a";
  return "n/a";
}

/** 从 OpenCode `/session/{id}/message` 的 envelope 数组中按原生 part id 定位。 */
export function findRawPart(rawMessages: unknown, partId: string): JsonRecord | undefined {
  for (const message of arrayValue(rawMessages)) {
    for (const part of arrayValue(recordValue(message)?.parts)) {
      const candidate = recordValue(part);
      if (candidate && partIdentifier(candidate) === partId) return candidate;
    }
  }
  return undefined;
}

/** 从平台 messages 的 `data.items` 或已解包 `items` 中按 part id 定位。 */
export function findPlatformMessagePart(platformMessages: unknown, partId: string): JsonRecord | undefined {
  const root = unwrapData(platformMessages);
  for (const message of arrayValue(recordValue(root)?.items ?? root)) {
    for (const part of arrayValue(recordValue(message)?.parts)) {
      const candidate = recordValue(part);
      if (candidate && partIdentifier(candidate) === partId) return candidate;
    }
  }
  return undefined;
}

/**
 * 从 preflight 证实的 `messagesBySessionId` 结构定位 Part；同时匹配 messageID，避免不同
 * 消息复用异常 id 时误判。
 */
export function findTreeMessagePart(tree: unknown, remoteMessageId: string, partId: string): JsonRecord | undefined {
  const root = recordValue(unwrapData(tree));
  const groups = recordValue(root?.messagesBySessionId);
  if (!groups) return undefined;
  for (const entries of Object.values(groups)) {
    for (const entryValue of arrayValue(entries)) {
      const entry = recordValue(entryValue);
      const part = recordValue(entry?.part);
      const messageId = stringValue(entry?.messageID) ?? stringValue(entry?.messageId) ?? stringValue(part?.messageID) ?? stringValue(part?.messageId);
      if (part && messageId === remoteMessageId && partIdentifier(part) === partId) return part;
    }
  }
  return undefined;
}

/** 选择每类官方必需字段；任何字段缺失都会让契约在接触 UI 之前失败。 */
export function selectPartFields(kind: PartKind, part: unknown): JsonRecord {
  const source = recordValue(part);
  if (!source) throw new Error(`${kind} part must be an object`);
  const partType = source.type;
  if (partType !== kind) throw new Error(`${kind} part type mismatch: ${String(partType)}`);
  const requiredFields = getSpec(kind).requiredFields;
  validateVariant(kind, source);
  const result: JsonRecord = {};
  for (const field of requiredFields) {
    const value = readPath(source, field);
    if (value === undefined || value === null) throw new Error(`${kind} part missing required field ${field}`);
    writePath(result, field, value);
  }
  for (const field of getSpec(kind).projectionFields) {
    const value = readPath(source, field);
    if (value !== undefined) writePath(result, field, value);
  }
  return result;
}

function validateVariant(kind: PartKind, source: JsonRecord): void {
  if (kind === "text" && source.time !== undefined) requirePaths(kind, source, ["time.start"]);
  if (kind === "subtask" && source.model !== undefined) requirePaths(kind, source, ["model.providerID", "model.modelID"]);
  if (kind === "file" && source.source !== undefined) {
    requirePaths(kind, source, ["source.type", "source.text.value", "source.text.start", "source.text.end"]);
    const sourceType = readPath(source, "source.type");
    if (sourceType === "file") requirePaths(kind, source, ["source.path"]);
    else if (sourceType === "symbol") requirePaths(kind, source, ["source.path", "source.range.start.line", "source.range.start.character", "source.range.end.line", "source.range.end.character", "source.name", "source.kind"]);
    else if (sourceType === "resource") requirePaths(kind, source, ["source.clientName", "source.uri"]);
    else throw new Error(`${kind} part has unknown source.type ${String(sourceType)}`);
  }
  if (kind === "tool") {
    const status = readPath(source, "state.status");
    if (status === "pending") requirePaths(kind, source, ["state.raw"]);
    else if (status === "running") requirePaths(kind, source, ["state.time.start"]);
    else if (status === "completed") requirePaths(kind, source, ["state.output", "state.title", "state.metadata", "state.time.start", "state.time.end"]);
    else if (status === "error") requirePaths(kind, source, ["state.error", "state.time.start", "state.time.end"]);
    else throw new Error(`${kind} part has unknown state.status ${String(status)}`);
  }
  if (kind === "agent" && source.source !== undefined) requirePaths(kind, source, ["source.value", "source.start", "source.end"]);
}

function requirePaths(kind: PartKind, source: JsonRecord, fields: readonly string[]): void {
  for (const field of fields) {
    const value = readPath(source, field);
    if (value === undefined || value === null) throw new Error(`${kind} part missing required field ${field}`);
  }
}

/** 分别验证 raw→平台 messages 与 raw→平台 tree，禁止一个来源替另一个来源兜底。 */
export function assertPartProjection(kind: PartKind, rawPart: unknown, platformMessages: unknown, platformTree: unknown): void {
  const raw = recordValue(rawPart);
  if (!raw) throw new Error(`${kind} raw part must be an object`);
  const partId = partIdentifier(raw);
  const messageId = stringValue(raw.messageID) ?? stringValue(raw.messageId);
  if (!partId || !messageId) throw new Error(`${kind} raw part has no id/messageID`);
  const expected = selectPartFields(kind, raw);
  assertProjection("platform messages", expected, findPlatformMessagePart(platformMessages, partId), kind);
  assertProjection("platform tree", expected, findTreeMessagePart(platformTree, messageId, partId), kind);
}

function assertProjection(layer: string, expected: JsonRecord, actualPart: JsonRecord | undefined, kind: PartKind): void {
  if (!actualPart) throw new Error(`${kind} ${layer} part not found`);
  let actual: JsonRecord;
  try {
    actual = selectPartFields(kind, actualPart);
  } catch (error) {
    throw new Error(`${kind} ${layer} projection invalid: ${error instanceof Error ? error.message : String(error)}`);
  }
  if (!isDeepStrictEqual(actual, expected)) throw new Error(`${kind} ${layer} projection differs from raw`);
}

/** 递归删除证据中的常见凭据字段；不会改变原对象，便于失败后仍可继续清理。 */
export function sanitizeEvidence<T>(value: T): T {
  if (Array.isArray(value)) return value.map((item) => sanitizeEvidence(item)) as T;
  if (!value || typeof value !== "object") return value;
  const sanitized: JsonRecord = {};
  for (const [key, item] of Object.entries(value as JsonRecord)) {
    if (isSecretKey(key)) continue;
    sanitized[key] = sanitizeEvidence(item);
  }
  return sanitized as T;
}

/** 生成隔离证据路径；每一段只允许安全文件名，防止跨 run 或跨 kind 覆盖。 */
export function evidencePath(runId: string, kind: PartKind, name: string): string {
  if (!PART_KINDS.includes(kind)) throw new Error(`unknown Part kind: ${String(kind)}`);
  for (const [label, segment] of [["runId", runId], ["name", name]] as const) {
    if (!/^[A-Za-z0-9._-]+$/.test(segment) || segment === "." || segment === "..") {
      throw new Error(`unsafe evidence ${label}`);
    }
  }
  return path.join(".tmp/e2e/opencode-parts", runId, kind, name);
}

/**
 * 每类自然触发只允许调用模型一次。超时后返回 fixture 分类并保留最后一次原生快照，
 * 调用方不得以继续对话的方式重试目标 Part。
 */
export async function runNaturalAttempt(options: NaturalAttemptOptions): Promise<NaturalAttemptResult> {
  if (options.skipReason) {
    return { classification: "native-fixture-required", kind: options.kind, reason: options.skipReason };
  }
  const timeoutMs = options.timeoutMs ?? 45_000;
  const pollIntervalMs = options.pollIntervalMs ?? 500;
  const sleep = options.sleep ?? ((delayMs: number) => new Promise<void>((resolve) => setTimeout(resolve, delayMs)));
  const now = options.now ?? Date.now;
  const startedAt = now();
  const { runId } = await options.trigger();
  let rawSnapshot: unknown;
  while (now() - startedAt <= timeoutMs) {
    const observation = await options.observe(runId);
    rawSnapshot = observation.rawSnapshot;
    if (observation.observed) {
      return { classification: "natural-pass", kind: options.kind, runId, rawSnapshot };
    }
    await sleep(pollIntervalMs);
  }
  return {
    classification: "native-fixture-required",
    kind: options.kind,
    runId,
    reason: `not-observed-within-${timeoutMs}ms`,
    rawSnapshot
  };
}

/** 写入单类真实 E2E 证据；JSON 在落盘前递归脱敏，空内容不能伪装为证据。 */
export async function writePartEvidence(options: {
  root?: string;
  runId: string;
  kind: PartKind;
  name: string;
  value: unknown;
}): Promise<string> {
  const relative = evidencePath(options.runId, options.kind, options.name);
  const target = options.root
    ? join(options.root, options.runId, options.kind, options.name)
    : relative;
  const serialized = JSON.stringify(sanitizeEvidence(options.value), null, 2);
  if (!serialized || serialized === "undefined") throw new Error("evidence must not be empty");
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, `${serialized}\n`, "utf8");
  return target;
}

/** 只读 provider 目录中只有显式测试隔离、且声明可控 429/503 的能力才允许 retry 自然探测。 */
export function detectSafeRetryProvider(providers: unknown): { providerId: string; statusCode: 429 | 503 } | undefined {
  for (const value of arrayValue(providers)) {
    const provider = recordValue(value);
    const metadata = recordValue(provider?.metadata);
    const codes = arrayValue(metadata?.controllableStatusCodes);
    const providerId = stringValue(provider?.id) ?? stringValue(provider?.providerID);
    if (providerId && metadata?.testIsolated === true) {
      if (codes.includes(429)) return { providerId, statusCode: 429 };
      if (codes.includes(503)) return { providerId, statusCode: 503 };
    }
  }
  return undefined;
}

/** 保留最近上下文，并同时满足 OpenCode compact 探测的 50 条/48000 字符硬上限。 */
export function buildCompactionPreparation(messages: readonly string[]): string[] {
  const result: string[] = [];
  let characters = 0;
  for (let index = messages.length - 1; index >= 0 && result.length < 50; index -= 1) {
    const remaining = 48_000 - characters;
    if (remaining <= 0) break;
    const message = messages[index]!.slice(0, remaining);
    result.unshift(message);
    characters += message.length;
  }
  return result;
}

/** tracing 尚未启动成功时没有 trace owner；失败必须先归还已经移交的资源所有权。 */
export async function startOwnedTrace(start: () => Promise<void>, cleanupOnFailure: () => Promise<void>): Promise<void> {
  try {
    await start();
  } catch (error) {
    try {
      await cleanupOnFailure();
    } catch (cleanupError) {
      throw new AggregateError([error, cleanupError], "trace start failed and owned resource cleanup also failed");
    }
    throw error;
  }
}

/** 对 trace 的 JSONL 与普通 header 文本同时脱敏；未知敏感字段整行删除而不是保留字段名。 */
export function sanitizeTraceText(source: string, token?: string): string {
  const sanitizedLines: string[] = [];
  for (const line of source.split(/\r?\n/)) {
    if (/^\s*(authorization|proxy-authorization|cookie|set-cookie|token|key|secret)\s*[:=]/i.test(line)) continue;
    try {
      sanitizedLines.push(JSON.stringify(sanitizeEvidence(JSON.parse(line))));
    } catch {
      sanitizedLines.push(line.replace(
        /(["']?)(authorization|proxy-authorization|cookie|set-cookie|token|key|secret)\1\s*[:=]\s*[^,;\s}\]]+/gi,
        "[REDACTED]"
      ));
    }
  }
  let result = sanitizedLines.join("\n");
  if (token) result = result.split(token).join("[REDACTED]");
  return result;
}

/** raw 已命中后给异步 SSE 投影一个有界追赶窗口，超时明确失败，禁止先 abort 再检查。 */
export async function waitForCapturedPart(
  events: readonly unknown[],
  kind: PartKind,
  partId: string,
  options: { timeoutMs: number; sleep?: (delayMs: number) => Promise<void>; now?: () => number }
): Promise<void> {
  const now = options.now ?? Date.now;
  const sleep = options.sleep ?? ((delayMs: number) => new Promise<void>((resolve) => setTimeout(resolve, delayMs)));
  const deadline = now() + options.timeoutMs;
  while (now() <= deadline) {
    const serialized = JSON.stringify(events);
    if (serialized.includes(`\"type\":\"${kind}\"`) && (!partId || serialized.includes(partId))) return;
    await sleep(100);
  }
  throw new Error(`${kind} target Part was not observed in RunEvent SSE within ${options.timeoutMs}ms`);
}

function getSpec(kind: PartKind): PartSpec {
  const found = PART_SPECS.find((item) => item.kind === kind);
  if (!found) throw new Error(`unknown Part kind: ${String(kind)}`);
  return found;
}

function isSecretKey(key: string): boolean {
  const normalized = key.toLowerCase().replace(/[-_]/g, "");
  return normalized === "authorization" || normalized === "proxyauthorization" || normalized === "cookie" || normalized === "setcookie" || normalized === "token" || normalized.endsWith("token") || normalized === "key" || normalized.endsWith("apikey") || normalized === "secret" || normalized.endsWith("secret");
}

function unwrapData(value: unknown): unknown {
  const record = recordValue(value);
  return record && "data" in record ? record.data : value;
}

function recordValue(value: unknown): JsonRecord | undefined {
  return value !== null && typeof value === "object" && !Array.isArray(value) ? value as JsonRecord : undefined;
}

function arrayValue(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function stringValue(value: unknown): string | undefined {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function partIdentifier(part: JsonRecord): string | undefined {
  return stringValue(part.id) ?? stringValue(part.partID) ?? stringValue(part.partId);
}

function readPath(source: JsonRecord, field: string): unknown {
  return field.split(".").reduce<unknown>((value, key) => recordValue(value)?.[key], source);
}

function writePath(target: JsonRecord, field: string, value: unknown): void {
  const segments = field.split(".");
  let cursor = target;
  segments.forEach((segment, index) => {
    if (index === segments.length - 1) cursor[segment] = value;
    else cursor = cursor[segment] = recordValue(cursor[segment]) ?? {} as JsonRecord;
  });
}
