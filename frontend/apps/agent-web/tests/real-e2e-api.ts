export type ApiEnvelope<T> =
  | { success: true; traceId: string; data: T }
  | { success: false; traceId: string; error: { code: string; message: string } };

export type RealE2eApiOptions = {
  baseUrl?: string;
  fetcher?: typeof fetch;
  token?: string;
  traceId?: string;
};

export type RequestEnvelopeOptions = RealE2eApiOptions & {
  method: "GET" | "POST" | "DELETE";
  body?: unknown;
};

export type WorkspaceCreateOperation = {
  status: string;
  currentStep?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  workspaceId?: string | null;
  versionId?: string | null;
};

export type WaitForWorkspaceOperationOptions = {
  getOperation: (operationId: string) => Promise<WorkspaceCreateOperation>;
  intervalMs?: number;
  timeoutMs?: number;
  sleep?: (delayMs: number) => Promise<void>;
};

export type CleanupTask = () => void | Promise<void>;

export type CleanupScope = {
  defer: (task: CleanupTask) => void;
  release: () => void;
  cleanup: () => Promise<void>;
};

export type RemoteSessionSourceOptions = {
  loadTree: () => Promise<unknown>;
  loadPlatformMessages: () => Promise<unknown>;
  onObserved?: (remoteSessionId: string) => void;
  validateTree?: (tree: unknown) => void | Promise<void>;
};

export type MyOpenCodeProcess = {
  port?: number | null;
  baseUrl?: string | null;
};

export type OpenCodeDatabaseLocationOptions = {
  projectRoot: string;
  stateRoot?: string;
  findListenerPid?: (port: number) => Promise<number>;
};

export type NativeSessionCounts = { session: number; message: number; part: number };

/**
 * 调用真实 E2E 使用的平台信封接口，并统一处理鉴权、trace 与错误脱敏。
 * 该客户端只访问平台 API，不允许用它绕过平台直连 OpenCode 服务。
 */
export async function requestEnvelope<T = unknown>(pathname: string, options: RequestEnvelopeOptions): Promise<T> {
  const token = options.token ?? process.env.TEST_AGENT_API_TOKEN;
  const headers: Record<string, string> = {
    ...authHeaders(token),
    Accept: "application/json",
    "X-Trace-Id": options.traceId ?? createTraceId()
  };
  const init: RequestInit = { method: options.method, headers };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    init.body = JSON.stringify(options.body);
  }

  const fetcher = options.fetcher ?? fetch;
  const baseUrl = stripTrailingSlash(options.baseUrl ?? process.env.TEST_AGENT_BASE_URL ?? "http://127.0.0.1:8080");
  const response = await fetcher(`${baseUrl}${normalizePathname(pathname)}`, init);
  const payload = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;
  if (!response.ok || !payload?.success) {
    const failure = payload && "error" in payload ? payload.error : undefined;
    const message = `${response.status} ${failure?.code ?? "API_ERROR"} ${failure?.message ?? response.statusText}`;
    throw new Error(redactSecret(message, token));
  }
  return payload.data;
}

/** 发送真实平台 GET 请求。 */
export function apiGet<T = unknown>(pathname: string, options: RealE2eApiOptions = {}): Promise<T> {
  return requestEnvelope<T>(pathname, { ...options, method: "GET" });
}

/** 发送真实平台 POST JSON 请求。 */
export function apiPost<T = unknown>(pathname: string, body: unknown, options: RealE2eApiOptions = {}): Promise<T> {
  return requestEnvelope<T>(pathname, { ...options, method: "POST", body });
}

/** 发送真实平台 DELETE 请求。 */
export function apiDelete<T = unknown>(pathname: string, options: RealE2eApiOptions = {}): Promise<T> {
  return requestEnvelope<T>(pathname, { ...options, method: "DELETE" });
}

/** Bearer token 为空时不发送 Authorization，便于兼容本地免鉴权环境。 */
export function authHeaders(token = process.env.TEST_AGENT_API_TOKEN): Record<string, string> {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/**
 * 等待配置管理的异步 Workspace 创建结束；失败和超时都保留 operation/step，
 * 让真实 E2E 能直接定位 Git、运行态 Workspace 等具体阶段。
 */
export async function waitForWorkspaceOperation(
  operationId: string,
  options: WaitForWorkspaceOperationOptions
): Promise<WorkspaceCreateOperation> {
  const intervalMs = options.intervalMs ?? 1_000;
  const timeoutMs = options.timeoutMs ?? 90_000;
  const sleep = options.sleep ?? ((delayMs) => new Promise<void>((resolve) => setTimeout(resolve, delayMs)));
  const deadline = Date.now() + timeoutMs;
  while (Date.now() <= deadline) {
    const operation = await options.getOperation(operationId);
    if (operation.status === "SUCCEEDED") {
      return operation;
    }
    if (operation.status === "FAILED") {
      throw new Error(
        `Workspace operation ${operationId} failed at ${operation.currentStep ?? "UNKNOWN"}: ${operation.errorCode ?? "WORKSPACE_CREATE_FAILED"} ${operation.errorMessage ?? "unknown error"}`
      );
    }
    await sleep(intervalMs);
  }
  throw new Error(`Workspace operation ${operationId} timed out after ${timeoutMs}ms`);
}

/** 独立执行全部清理动作，避免前一项失败阻断后续资源释放。 */
export async function runCleanupTasks(tasks: CleanupTask[]): Promise<void> {
  const results = await Promise.allSettled(tasks.map((task) => Promise.resolve().then(task)));
  const failures = results.flatMap((result) => (result.status === "rejected" ? [result.reason] : []));
  if (failures.length > 0) {
    throw new AggregateError(failures, `${failures.length} cleanup task(s) failed`);
  }
}

/** 按依赖顺序执行清理阶段；单阶段失败会被记录，但不会阻断后续阶段。 */
export async function runCleanupStages(stages: CleanupTask[]): Promise<void> {
  const failures: unknown[] = [];
  for (const stage of stages) {
    try {
      await stage();
    } catch (error) {
      failures.push(error);
    }
  }
  if (failures.length > 0) {
    throw new AggregateError(failures, `${failures.length} cleanup stage(s) failed`);
  }
}

/**
 * 持有尚未移交的资源清理责任；release 只能在完整 fixture 返回前调用。
 * 清理按登记逆序启动，并继承 runCleanupTasks 的全量执行与错误汇总语义。
 */
export function createCleanupScope(): CleanupScope {
  const tasks: CleanupTask[] = [];
  let released = false;
  return {
    defer(task) {
      tasks.push(task);
    },
    release() {
      released = true;
    },
    async cleanup() {
      if (!released) {
        await runCleanupTasks([...tasks].reverse());
      }
    }
  };
}

/**
 * 按 Session tree（含其返回的原生事件快照）和平台消息两个独立来源恢复远端 Session ID。
 * ID 一经观察便先交给 cleanup owner，再执行其余投影校验，确保后续异常不丢失原生删除责任。
 */
export async function resolveRemoteSessionIdFromSources(options: RemoteSessionSourceOptions): Promise<string> {
  const failures: unknown[] = [];
  let tree: unknown;
  let treeLoaded = false;
  try {
    tree = await options.loadTree();
    treeLoaded = true;
  } catch (error) {
    failures.push(error);
  }
  if (treeLoaded) {
    const treeId = findTreeRootSessionId(tree) ?? findNativeSessionId(tree);
    if (treeId) {
      options.onObserved?.(treeId);
      // 已登记 cleanup ownership 后的投影失败必须原样暴露，不能被另一来源掩盖。
      await options.validateTree?.(tree);
      return treeId;
    }
    try {
      await options.validateTree?.(tree);
      failures.push(new Error("session tree contained no remote OpenCode session id"));
    } catch (error) {
      failures.push(error);
    }
  }

  try {
    const messages = await options.loadPlatformMessages();
    const messageId = findNativeSessionId(messages);
    if (messageId) {
      options.onObserved?.(messageId);
      return messageId;
    }
    failures.push(new Error("platform messages contained no remote OpenCode session id"));
  } catch (error) {
    failures.push(error);
  }
  throw new AggregateError(failures, "remote OpenCode session id was not found; session tree and platform messages both failed");
}

/**
 * 从平台目标进程定位同端口 manager state，并只接受项目拥有用户 Session 根下的固定 SQLite 文件。
 * state、Session 路径和数据库任一层包含符号链接、端口/baseUrl/PID 不匹配都会拒绝。
 */
export async function resolveOwnedOpenCodeDatabase(
  process: MyOpenCodeProcess,
  options: OpenCodeDatabaseLocationOptions
): Promise<{ databasePath: string; sessionPath: string; pid: number; port: number }> {
  const rawPort = process.port;
  if (typeof rawPort !== "number" || !Number.isInteger(rawPort) || rawPort <= 0) {
    throw new Error("platform OpenCode process has no valid port");
  }
  const port = rawPort;
  const lexicalProjectRoot = path.resolve(options.projectRoot);
  const projectRoot = await realpath(lexicalProjectRoot);
  const configuredStateRoot = options.stateRoot ?? path.join(lexicalProjectRoot, ".tmp", "dev-services", "opencode-manager-state", "processes");
  const stateRoot = rebaseProjectOwnedPath(configuredStateRoot, lexicalProjectRoot, projectRoot, "manager state");
  const statePath = path.join(stateRoot, `${port}.json`);
  await assertPathWithoutSymlink(projectRoot, statePath, "manager state");
  const state = JSON.parse(await readFile(statePath, "utf8")) as {
    port?: number;
    pid?: number;
    baseUrl?: string;
    sessionPath?: string;
  };
  if (state.port !== port) throw new Error(`manager state port ${state.port ?? "missing"} does not match platform port ${port}`);
  if (typeof state.pid !== "number" || !Number.isInteger(state.pid) || state.pid <= 0) throw new Error("manager state has no valid PID");
  const statePid = state.pid;
  if (urlPort(state.baseUrl) !== port || urlPort(process.baseUrl) !== port) {
    throw new Error("manager state/platform baseUrl port does not match target process port");
  }

  const ownedSessionRoot = path.join(projectRoot, ".testagent", "agent-opencode", ".session", "users");
  if (!state.sessionPath) throw new Error("manager state has no sessionPath");
  const sessionPath = rebaseProjectOwnedPath(state.sessionPath, lexicalProjectRoot, projectRoot, "owned session root");
  await assertPathWithoutSymlink(ownedSessionRoot, sessionPath, "owned session root");
  const canonicalSessionPath = await realpath(sessionPath);
  const databasePath = path.join(canonicalSessionPath, "opencode", "opencode.db");
  await assertPathWithoutSymlink(ownedSessionRoot, databasePath, "OpenCode database");
  const canonicalDatabasePath = await realpath(databasePath);

  const listenerPid = await (options.findListenerPid ?? findListenerPid)(port);
  if (listenerPid !== statePid) {
    throw new Error(`manager state PID ${statePid} does not match listener PID ${listenerPid}`);
  }
  return { databasePath: canonicalDatabasePath, sessionPath: canonicalSessionPath, pid: statePid, port };
}

/** 只读查询指定测试 Session 的三张原生表，任何非零计数都作为资源泄漏失败。 */
export async function assertNativeSessionAbsentInSqlite(
  databasePath: string,
  remoteSessionId: string,
  options: { queryCounts?: (databasePath: string, remoteSessionId: string) => Promise<NativeSessionCounts> } = {}
): Promise<NativeSessionCounts> {
  const counts = await (options.queryCounts ?? queryNativeSessionCounts)(databasePath, remoteSessionId);
  if (counts.session !== 0 || counts.message !== 0 || counts.part !== 0) {
    throw new Error(
      `native OpenCode SQLite residue for ${safeIdPrefix(remoteSessionId)}: session=${counts.session}, message=${counts.message}, part=${counts.part}`
    );
  }
  return counts;
}

/** 对物理清理目标做 canonical、路径段和符号链接三重校验。 */
export async function resolveOwnedCleanupPath(candidate: string, ownedRoot: string, marker: string): Promise<string> {
  const canonicalRoot = await realpath(ownedRoot);
  const lexicalCandidate = path.resolve(candidate);
  const lexicalRoot = path.resolve(ownedRoot);
  const relative = path.relative(lexicalRoot, lexicalCandidate);
  if (!relative || relative.startsWith(`..${path.sep}`) || relative === ".." || path.isAbsolute(relative)) {
    throw new Error("cleanup path is outside owned root or equals owned root");
  }
  const segments = relative.split(path.sep);
  if (!segments.includes(marker)) {
    throw new Error("cleanup path does not contain the marker segment");
  }
  let cursor = lexicalRoot;
  for (const segment of segments) {
    cursor = path.join(cursor, segment);
    if ((await lstat(cursor)).isSymbolicLink()) {
      throw new Error("cleanup path contains a symbolic link");
    }
  }
  const canonicalCandidate = await realpath(lexicalCandidate);
  const canonicalRelative = path.relative(canonicalRoot, canonicalCandidate);
  if (!canonicalRelative || canonicalRelative.startsWith(`..${path.sep}`) || canonicalRelative === ".." || path.isAbsolute(canonicalRelative)) {
    throw new Error("canonical cleanup path is outside owned root");
  }
  return canonicalCandidate;
}

function findTreeRootSessionId(value: unknown): string | undefined {
  if (!isRecord(value) || !Array.isArray(value.sessions)) return undefined;
  for (const session of value.sessions) {
    if (!isRecord(session) || session.childSession === true) continue;
    if (isNativeSessionId(session.sessionId)) return session.sessionId;
  }
  return undefined;
}

function findNativeSessionId(value: unknown, seen = new Set<object>()): string | undefined {
  if (Array.isArray(value)) {
    for (const item of value) {
      const found = findNativeSessionId(item, seen);
      if (found) return found;
    }
    return undefined;
  }
  if (!isRecord(value) || seen.has(value)) return undefined;
  seen.add(value);
  for (const key of ["sessionID", "rootSessionId"] as const) {
    if (isNativeSessionId(value[key])) return value[key];
  }
  for (const nested of Object.values(value)) {
    const found = findNativeSessionId(nested, seen);
    if (found) return found;
  }
  return undefined;
}

function isNativeSessionId(value: unknown): value is string {
  return typeof value === "string" && /^ses[A-Za-z0-9_-]+$/.test(value);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function rebaseProjectOwnedPath(candidate: string, lexicalProjectRoot: string, canonicalProjectRoot: string, label: string): string {
  const relative = path.relative(lexicalProjectRoot, path.resolve(candidate));
  if (!relative || relative === ".." || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    throw new Error(`${label} path is outside owned session root or equals it`);
  }
  return path.join(canonicalProjectRoot, relative);
}

async function assertPathWithoutSymlink(ownedRoot: string, candidate: string, label: string): Promise<void> {
  const lexicalRoot = path.resolve(ownedRoot);
  const lexicalCandidate = path.resolve(candidate);
  const relative = path.relative(lexicalRoot, lexicalCandidate);
  if (!relative || relative === ".." || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    throw new Error(`${label} path is outside owned session root or equals it`);
  }
  const rootStat = await lstat(lexicalRoot);
  if (rootStat.isSymbolicLink()) throw new Error(`${label} path contains a symbolic link`);
  let cursor = lexicalRoot;
  for (const segment of relative.split(path.sep)) {
    cursor = path.join(cursor, segment);
    if ((await lstat(cursor)).isSymbolicLink()) throw new Error(`${label} path contains a symbolic link`);
  }
  const canonicalRoot = await realpath(lexicalRoot);
  const canonicalCandidate = await realpath(lexicalCandidate);
  const canonicalRelative = path.relative(canonicalRoot, canonicalCandidate);
  if (!canonicalRelative || canonicalRelative === ".." || canonicalRelative.startsWith(`..${path.sep}`) || path.isAbsolute(canonicalRelative)) {
    throw new Error(`${label} canonical path is outside owned session root`);
  }
}

async function findListenerPid(port: number): Promise<number> {
  const { stdout } = await execFileAsync("lsof", ["-nP", `-iTCP:${port}`, "-sTCP:LISTEN", "-t"], { encoding: "utf8" });
  const pids = stdout.trim().split(/\s+/).filter(Boolean).map(Number);
  if (pids.length !== 1 || !Number.isInteger(pids[0])) throw new Error(`expected one listener PID for port ${port}`);
  return pids[0]!;
}

async function queryNativeSessionCounts(databasePath: string, remoteSessionId: string): Promise<NativeSessionCounts> {
  const id = remoteSessionId.replaceAll("'", "''");
  const sql = [
    `SELECT 'session', COUNT(*) FROM session WHERE id='${id}'`,
    `SELECT 'message', COUNT(*) FROM message WHERE session_id='${id}'`,
    `SELECT 'part', COUNT(*) FROM part WHERE session_id='${id}'`
  ].join(" UNION ALL ");
  const { stdout } = await execFileAsync("sqlite3", ["-readonly", "-separator", "\t", databasePath, sql], { encoding: "utf8" });
  const counts: NativeSessionCounts = { session: -1, message: -1, part: -1 };
  for (const line of stdout.trim().split("\n")) {
    const [table, rawCount] = line.split("\t");
    if (table === "session" || table === "message" || table === "part") counts[table] = Number(rawCount);
  }
  if (Object.values(counts).some((count) => !Number.isInteger(count) || count < 0)) {
    throw new Error("SQLite residue query did not return all session/message/part counts");
  }
  return counts;
}

function safeIdPrefix(value: string): string {
  return value.slice(0, 12);
}

function urlPort(value: string | null | undefined): number | undefined {
  try {
    const parsed = new URL(value ?? "");
    const rawPort = parsed.port || (parsed.protocol === "https:" ? "443" : parsed.protocol === "http:" ? "80" : "");
    const port = Number(rawPort);
    return Number.isInteger(port) && port > 0 ? port : undefined;
  } catch {
    return undefined;
  }
}

function stripTrailingSlash(value: string): string {
  return value.replace(/\/+$/, "");
}

function normalizePathname(pathname: string): string {
  return pathname.startsWith("/") ? pathname : `/${pathname}`;
}

function createTraceId(): string {
  return `trace_real_e2e_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

function redactSecret(message: string, token: string | undefined): string {
  return token ? message.split(token).join("[REDACTED]") : message;
}
import { execFile } from "node:child_process";
import { lstat, readFile, realpath } from "node:fs/promises";
import path from "node:path";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
