#!/usr/bin/env node

import { mkdir, mkdtemp, readFile, realpath, rm, stat, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { performance } from "node:perf_hooks";
import { randomUUID } from "node:crypto";

const THIS_FILE = fileURLToPath(import.meta.url);
const DEFAULT_RUNTIME_ROOT = resolve(dirname(THIS_FILE), "..");
const RUNTIME_ROOT = process.env.TEST_AGENT_OPENCODE_RUNTIME_ROOT || DEFAULT_RUNTIME_ROOT;
const sdkModule = pathToFileURL(`${join(RUNTIME_ROOT, "node_modules/@modelcontextprotocol/sdk/dist/esm")}/`);
const [{ McpServer }, { StdioServerTransport }, { StdioClientTransport }, { Client }, { z }] = await Promise.all([
  import(new URL("server/mcp.js", sdkModule).href),
  import(new URL("server/stdio.js", sdkModule).href),
  import(new URL("client/stdio.js", sdkModule).href),
  import(new URL("client/index.js", sdkModule).href),
  import(pathToFileURL(join(RUNTIME_ROOT, "node_modules/zod/index.js")).href),
]);

const VERSION = "1.0.0";
const CODEX_VERSION = "0.145.0";
const POLICY_PATH = "/etc/codex/requirements.toml";
const TOOL_TIMEOUT_MS = 600_000;
const MAX_PROMPT_LENGTH = 65_536;
const PROVIDER_ID = "test-agent-internal";
const POLICY_PROFILE = "test-agent-whitebox-readonly";
const REQUIRED_POLICY_MARKERS = Object.freeze([
  'allowed_approval_policies = ["never"]',
  'allowed_web_search_modes = ["disabled"]',
  `default_permissions = "${POLICY_PROFILE}"`,
  `test-agent-whitebox-readonly = true`,
  '":root" = "deny"',
  '":minimal" = "read"',
  '":tmpdir" = "deny"',
  '":slash_tmp" = "deny"',
  '"." = "read"',
  "enabled = false",
]);
const FIXED_DEVELOPER_INSTRUCTIONS = [
  "你是 TestAgent 白盒代码分析器。只能读取当前 workspace 已存在的文件。",
  "允许使用 rg、目录列表和只读源码查看来定位证据；禁止写文件、修改 Git 状态、访问 workspace 外部、访问网络或请求扩大权限。",
  "输出必须包含证据文件、关键代码位置、调用链、风险和待确认项；缺少证据时明确说明，不得猜测。",
].join("\n");

function audit(traceId, status, durationMs, errorCode) {
  const record = {
    traceId,
    durationMs: Math.max(0, Math.round(durationMs)),
    status,
  };
  if (errorCode) record.errorCode = errorCode;
  process.stderr.write(`${JSON.stringify(record)}\n`);
}

function required(env, name) {
  const value = env[name];
  if (typeof value !== "string" || value.trim() === "") {
    throw new Error(`WHITEBOX_CONFIG_MISSING:${name}`);
  }
  return value.trim();
}

export async function loadConfiguration(env = process.env, options = {}) {
  const workspace = await realpath(options.workspace || process.cwd());
  const workspaceStat = await stat(workspace);
  if (!workspaceStat.isDirectory()) throw new Error("WHITEBOX_WORKSPACE_INVALID");

  const baseUrl = new URL(required(env, "TEST_AGENT_INTERNAL_PROXY_BASE_URL"));
  if (!["http:", "https:"].includes(baseUrl.protocol) || baseUrl.username || baseUrl.password) {
    throw new Error("WHITEBOX_PROXY_URL_INVALID");
  }
  const contextWindow = Number(required(env, "TEST_AGENT_CODEX_CONTEXT_WINDOW"));
  if (!Number.isSafeInteger(contextWindow) || contextWindow < 4096 || contextWindow > 2_000_000) {
    throw new Error("WHITEBOX_CONTEXT_WINDOW_INVALID");
  }

  const policyPath = options.policyPath || POLICY_PATH;
  const policyStat = await stat(policyPath).catch(() => null);
  if (!policyStat?.isFile()) throw new Error("WHITEBOX_POLICY_MISSING");
  const policy = await readFile(policyPath, "utf8");
  if (REQUIRED_POLICY_MARKERS.some((marker) => !policy.includes(marker))) {
    throw new Error("WHITEBOX_POLICY_INVALID");
  }

  return Object.freeze({
    workspace,
    policyPath,
    proxyBaseUrl: baseUrl.toString().replace(/\/$/, ""),
    proxyApiKey: required(env, "TEST_AGENT_INTERNAL_PROXY_API_KEY"),
    providerId: required(env, "TEST_AGENT_CODEX_PROVIDER_ID"),
    model: required(env, "TEST_AGENT_CODEX_MODEL"),
    contextWindow,
    ucid: required(env, "ENTERPRISE_UCID"),
    codexBin: required(env, "TEST_AGENT_CODEX_BIN"),
  });
}

function tomlString(value) {
  return JSON.stringify(String(value));
}

function codexConfigToml(configuration, codexHome) {
  const commandPath = "/usr/local/bin:/usr/bin:/bin";
  return [
    `model = ${tomlString(configuration.model)}`,
    `model_provider = ${tomlString(PROVIDER_ID)}`,
    `model_context_window = ${configuration.contextWindow}`,
    "model_supports_reasoning_summaries = false",
    'model_reasoning_summary = "none"',
    'web_search = "disabled"',
    'approval_policy = "never"',
    `default_permissions = ${tomlString(POLICY_PROFILE)}`,
    "allow_login_shell = false",
    "project_root_markers = []",
    "",
    "[shell_environment_policy]",
    'inherit = "none"',
    "ignore_default_excludes = false",
    `set = { PATH = ${tomlString(commandPath)}, HOME = ${tomlString(codexHome)} }`,
    'include_only = ["PATH", "HOME"]',
    "",
    `[model_providers.${PROVIDER_ID}]`,
    'name = "TestAgent internal model proxy"',
    `base_url = ${tomlString(configuration.proxyBaseUrl)}`,
    'env_key = "TEST_AGENT_INTERNAL_PROXY_API_KEY"',
    'wire_api = "responses"',
    "supports_websockets = false",
    "request_max_retries = 1",
    "stream_max_retries = 1",
    `stream_idle_timeout_ms = ${TOOL_TIMEOUT_MS}`,
    'env_http_headers = { "X-Enterprise-Model-Provider" = "TEST_AGENT_CODEX_PROVIDER_ID", "ucid" = "ENTERPRISE_UCID" }',
    "",
  ].join("\n");
}

function childEnvironment(configuration, codexHome) {
  const env = {
    PATH: "/usr/local/bin:/usr/bin:/bin",
    HOME: codexHome,
    CODEX_HOME: codexHome,
    LANG: process.env.LANG || "C.UTF-8",
    RUST_LOG: "off",
    TEST_AGENT_INTERNAL_PROXY_API_KEY: configuration.proxyApiKey,
    TEST_AGENT_CODEX_PROVIDER_ID: configuration.providerId,
    ENTERPRISE_UCID: configuration.ucid,
  };
  const proxyHost = new URL(configuration.proxyBaseUrl).hostname;
  const noProxy = ["127.0.0.1", "localhost", proxyHost].filter(Boolean).join(",");
  env.NO_PROXY = noProxy;
  env.no_proxy = noProxy;
  return env;
}

function publicResult(rawResult, threadId) {
  if (rawResult?.isError === true) throw new Error("WHITEBOX_CODEX_FAILED");
  const text = Array.isArray(rawResult?.content)
    ? rawResult.content
        .filter((item) => item?.type === "text" && typeof item.text === "string")
        .map((item) => item.text)
        .join("\n")
    : "";
  if (!text) throw new Error("WHITEBOX_EMPTY_RESULT");
  return {
    content: [{ type: "text", text }],
    structuredContent: { threadId },
  };
}

class CodexRuntime {
  constructor(configuration, dependencies = {}) {
    this.configuration = configuration;
    this.clientFactory = dependencies.clientFactory;
    this.threadIds = new Set();
    this.client = null;
    this.transport = null;
    this.codexHome = null;
    this.ensurePromise = null;
    this.resetPromise = null;
  }

  async ensureClient() {
    if (this.client) return this.client;
    if (this.ensurePromise) return this.ensurePromise;
    this.ensurePromise = this.startClient().finally(() => {
      this.ensurePromise = null;
    });
    return this.ensurePromise;
  }

  async startClient() {
    if (this.clientFactory) {
      this.client = await this.clientFactory();
      return this.client;
    }

    this.codexHome = await mkdtemp(join(tmpdir(), "test-agent-codex-"));
    try {
      await mkdir(this.codexHome, { recursive: true, mode: 0o700 });
      await writeFile(join(this.codexHome, "config.toml"), codexConfigToml(this.configuration, this.codexHome), {
        encoding: "utf8",
        mode: 0o600,
      });
      this.transport = new StdioClientTransport({
        command: this.configuration.codexBin,
        args: ["mcp-server"],
        cwd: this.configuration.workspace,
        env: childEnvironment(this.configuration, this.codexHome),
        stderr: "pipe",
      });
      // 官方 stderr 可能含上游错误正文，门面只消费不转发，避免提示词、代码或路径进入平台日志。
      this.transport.stderr?.on("data", () => {});
      const client = new Client({ name: "test-agent-codex-whitebox-facade", version: VERSION });
      await client.connect(this.transport);
      this.client = client;
      return client;
    } catch (error) {
      await this.reset();
      throw error;
    }
  }

  async analyze(prompt, signal) {
    const raw = await this.callRaw("codex", {
      prompt,
      cwd: this.configuration.workspace,
      model: this.configuration.model,
      "approval-policy": "never",
      "developer-instructions": FIXED_DEVELOPER_INSTRUCTIONS,
    }, signal);
    const threadId = raw?.structuredContent?.threadId;
    if (typeof threadId !== "string" || threadId.trim() === "") {
      throw new Error("WHITEBOX_THREAD_ID_MISSING");
    }
    this.threadIds.add(threadId);
    return publicResult(raw, threadId);
  }

  async reply(threadId, prompt, signal) {
    if (!this.threadIds.has(threadId)) throw new Error("WHITEBOX_THREAD_NOT_FOUND");
    const raw = await this.callRaw("codex-reply", { threadId, prompt }, signal);
    return publicResult(raw, threadId);
  }

  async callRaw(name, args, signal) {
    const client = await this.ensureClient();
    try {
      const result = await client.callTool(
        { name, arguments: args },
        undefined,
        { signal, timeout: TOOL_TIMEOUT_MS, maxTotalTimeout: TOOL_TIMEOUT_MS },
      );
      if (result?.isError === true) {
        await this.reset();
        throw new Error("WHITEBOX_CODEX_FAILED");
      }
      return result;
    } catch (error) {
      if (signal?.aborted || /timeout|timed out|abort/i.test(String(error?.message || error))) {
        await this.reset();
        throw new Error(signal?.aborted ? "WHITEBOX_CANCELLED" : "WHITEBOX_TIMEOUT");
      }
      await this.reset();
      throw new Error("WHITEBOX_CODEX_FAILED");
    }
  }

  async reset() {
    if (this.resetPromise) return this.resetPromise;
    this.resetPromise = (async () => {
      const client = this.client;
      const transport = this.transport;
      const codexHome = this.codexHome;
      this.client = null;
      this.transport = null;
      this.codexHome = null;
      this.threadIds.clear();
      await client?.close().catch(() => {});
      await transport?.close().catch(() => {});
      if (codexHome) await rm(codexHome, { recursive: true, force: true }).catch(() => {});
    })().finally(() => {
      this.resetPromise = null;
    });
    return this.resetPromise;
  }
}

export function createWhiteboxServer(runtime) {
  const server = new McpServer(
    { name: "test-agent-codex-whitebox", version: VERSION },
    { capabilities: { tools: {} } },
  );
  const promptSchema = z.string().trim().min(1).max(MAX_PROMPT_LENGTH);

  server.registerTool(
    "whitebox_analyze",
    {
      description: "严格只读分析当前 workspace，返回分析正文与本进程内可续写的 threadId。",
      inputSchema: { prompt: promptSchema },
      outputSchema: { threadId: z.string() },
      annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false },
    },
    async ({ prompt }, extra) => execute("analyze", (signal) => runtime.analyze(prompt, signal), extra.signal),
  );
  server.registerTool(
    "whitebox_reply",
    {
      description: "在当前 MCP 生命周期内继续一次严格只读白盒分析。",
      inputSchema: { threadId: z.string().trim().min(1).max(256), prompt: promptSchema },
      outputSchema: { threadId: z.string() },
      annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false },
    },
    async ({ threadId, prompt }, extra) =>
      execute("reply", (signal) => runtime.reply(threadId, prompt, signal), extra.signal),
  );
  return server;
}

async function execute(operation, callback, signal) {
  const traceId = `codex_${randomUUID().replaceAll("-", "")}`;
  const started = performance.now();
  try {
    const result = await callback(signal);
    audit(traceId, "SUCCESS", performance.now() - started);
    return result;
  } catch (error) {
    const errorCode = String(error?.message || "WHITEBOX_FAILED").startsWith("WHITEBOX_")
      ? String(error.message)
      : "WHITEBOX_FAILED";
    audit(traceId, "FAILED", performance.now() - started, errorCode);
    return {
      isError: true,
      content: [{ type: "text", text: errorCode }],
    };
  }
}

export async function startWhiteboxFacade(options = {}) {
  const configuration = options.configuration || await loadConfiguration(process.env, options);
  const runtime = options.runtime || new CodexRuntime(configuration, options);
  const server = createWhiteboxServer(runtime);
  const transport = options.transport || new StdioServerTransport();
  await server.connect(transport);
  return { server, runtime };
}

async function main() {
  let active;
  try {
    active = await startWhiteboxFacade();
  } catch (error) {
    const message = String(error?.message || "");
    const errorCode = message.startsWith("WHITEBOX_") ? message : "WHITEBOX_STARTUP_FAILED";
    audit(`codex_${randomUUID().replaceAll("-", "")}`, "FAILED", 0, errorCode);
    process.exitCode = 1;
    return;
  }

  let closing = false;
  const close = async () => {
    if (closing) return;
    closing = true;
    await active.runtime.reset();
    await active.server.close().catch(() => {});
  };
  process.stdin.once("end", () => void close());
  process.once("SIGTERM", () => void close().finally(() => process.exit(0)));
  process.once("SIGINT", () => void close().finally(() => process.exit(0)));
}

if (process.argv[1] && resolve(process.argv[1]) === resolve(THIS_FILE)) {
  await main();
}

export { CODEX_VERSION, CodexRuntime, POLICY_PATH, TOOL_TIMEOUT_MS };
