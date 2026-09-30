#!/usr/bin/env node

/**
 * 在独立 HOME、配置目录和临时端口启动冻结的 V2 CLI，使用自有 stdio MCP fixture
 * 验证握手、资源目录、模型工具可见性和真实 tools/call。fixture 只读临时目录，
 * 不接触平台运行实例。
 *
 * 用法：OPENCODE_V2_BIN=/path/to/opencode node tools/test-opencode-v2-mcp-smoke.mjs
 */

import assert from "node:assert/strict";
import { execFile, spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { appendFileSync } from "node:fs";
import { access, mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { createServer } from "node:net";
import { createServer as createHttpServer } from "node:http";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import { promisify } from "node:util";

const exec = promisify(execFile);

const cli = process.env.OPENCODE_V2_BIN;
if (!cli) throw new Error("Set OPENCODE_V2_BIN to the frozen OpenCode 2.0.18 executable");
await access(cli);

async function freePort() {
  const server = createServer();
  await new Promise((done, fail) => server.once("error", fail).listen(0, "127.0.0.1", done));
  const port = server.address().port;
  await new Promise((done) => server.close(done));
  return port;
}

function mockMcpServerSource(traceFile) {
  return `import { appendFileSync } from "node:fs";
const traceFile = ${JSON.stringify(traceFile)};
let pending = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => {
  pending += chunk;
  for (let end; (end = pending.indexOf("\\n")) >= 0;) {
    const line = pending.slice(0, end).trim();
    pending = pending.slice(end + 1);
    if (!line) continue;
    let request;
    try { request = JSON.parse(line); } catch { continue; }
    if (!request.method) continue;
    appendFileSync(traceFile, request.method === "tools/call"
      ? "tools/call:" + request.params?.name + "\\n"
      : request.method + "\\n");
    if (request.id === undefined) continue;
    let result;
    switch (request.method) {
      case "initialize":
        result = { protocolVersion: request.params?.protocolVersion ?? "2025-03-26", capabilities: { tools: { listChanged: false }, resources: { listChanged: false } }, serverInfo: { name: "testagent-v2-mcp-probe", version: "1.0.0" } };
        break;
      case "tools/list":
        result = { tools: [{ name: "ping", description: "Local V2 MCP probe", inputSchema: { type: "object", properties: {}, additionalProperties: false } }] };
        break;
      case "tools/call":
        result = { content: [{ type: "text", text: "TESTAGENT_MCP_OK" }] };
        break;
      case "resources/list":
        result = { resources: [{ uri: "testagent://v2-probe", name: "V2 probe", mimeType: "text/plain" }] };
        break;
      case "resources/templates/list":
        result = { resourceTemplates: [] };
        break;
      case "resources/read":
        result = { contents: [{ uri: "testagent://v2-probe", mimeType: "text/plain", text: "TESTAGENT_MCP_RESOURCE_OK" }] };
        break;
      case "prompts/list":
        result = { prompts: [] };
        break;
      default:
        process.stdout.write(JSON.stringify({ jsonrpc: "2.0", id: request.id, error: { code: -32601, message: "Method not found" } }) + "\\n");
        continue;
    }
    process.stdout.write(JSON.stringify({ jsonrpc: "2.0", id: request.id, result }) + "\\n");
  }
});
`;
}

async function getJson(url, password) {
  const response = await fetch(url, {
    headers: { Authorization: `Basic ${Buffer.from(`opencode:${password}`).toString("base64")}` },
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) throw new Error(`${new URL(url).pathname} returned HTTP ${response.status}`);
  return response.json();
}

async function readJsonLinesUntilTool(path, expectedTool, timeoutMs = 30_000) {
  const deadline = Date.now() + timeoutMs;
  let entries = [];
  while (Date.now() < deadline) {
    const lines = (await readFile(path, "utf8")).trim().split("\n").filter(Boolean);
    entries = lines.map((line) => JSON.parse(line));
    if (entries.some((entry) => entry.tools?.includes(expectedTool))) return entries;
    await delay(250);
  }
  return entries;
}

async function readLinesUntil(path, expectedLine, timeoutMs = 30_000) {
  const deadline = Date.now() + timeoutMs;
  let lines = [];
  while (Date.now() < deadline) {
    lines = (await readFile(path, "utf8")).trim().split("\n").filter(Boolean);
    if (lines.includes(expectedLine)) return lines;
    await delay(250);
  }
  return lines;
}

async function startModelProbe(traceFile) {
  let requestCount = 0;
  let subagentRequested = false;
  const modelServer = createHttpServer((request, response) => {
    if (request.method !== "POST") {
      response.writeHead(404).end();
      return;
    }
    const chunks = [];
    request.on("data", (chunk) => chunks.push(chunk));
    request.on("end", () => {
      let payload = {};
      try {
        payload = JSON.parse(Buffer.concat(chunks).toString("utf8"));
      } catch {
        response.writeHead(400).end();
        return;
      }
      const tools = Array.isArray(payload.tools) ? payload.tools : [];
      const names = tools.map((tool) => tool?.function?.name ?? tool?.name).filter(Boolean);
      requestCount += 1;
      const messages = Array.isArray(payload.messages) ? payload.messages : [];
      const subagentRequest = JSON.stringify(messages).includes("V2_SUBAGENT");
      const compactionRequest = messages.some((message) => typeof message?.content === "string"
        && message.content.includes("## Objective") && message.content.includes("## Work State"));
      const toolMessages = Array.isArray(payload.messages)
        ? payload.messages.filter((message) => message?.role === "tool")
        : [];
      const toolResultHasMarker = JSON.stringify(toolMessages).includes("TESTAGENT_MCP_OK");
      appendModelTrace(traceFile, {
        tools: names,
        routeProvider: request.headers["x-enterprise-model-provider"],
        requestPath: request.url,
        toolResultHasMarker,
        compactionRequest,
        subagentRequest,
        messages: Array.isArray(payload.messages)
          ? payload.messages.map((message) => ({
              role: message?.role,
              tool: message?.tool_call_id,
              calls: (message?.tool_calls ?? []).map((call) => call?.function?.name).filter(Boolean),
            }))
          : [],
      });
      // 标题等辅助模型请求没有 MCP 工具，应直接返回文本；只有主请求调用工具。
      const shouldCallMcp = names.includes("testagent_probe_ping") && toolMessages.length === 0 && !compactionRequest;
      const shouldCallSubagent = names.includes("subagent") && subagentRequest && !subagentRequested;
      if (shouldCallSubagent) subagentRequested = true;
      const delta = shouldCallSubagent
        ? {
            role: "assistant",
            tool_calls: [{
              index: 0,
              id: `call_testagent_subagent_${requestCount}`,
              type: "function",
              function: {
                name: "subagent",
                arguments: JSON.stringify({
                  agent: "probe-subagent",
                  description: "V2 child probe",
                  prompt: "Reply exactly V2_SUBAGENT_CHILD_OK.",
                }),
              },
            }],
          }
        : shouldCallMcp
        ? {
            role: "assistant",
            tool_calls: [{
              index: 0,
              id: `call_testagent_mcp_${requestCount}`,
              type: "function",
              function: {
                name: "testagent_probe_ping",
                arguments: "{}",
              },
            }],
          }
        : { role: "assistant", content: compactionRequest
          ? "## Objective\n- Complete the isolated V2 smoke.\n\n## Work State\n### Completed\n- Native prompt succeeded.\n\n## Next Move\n1. Finish verification."
          : toolMessages.length === 0 ? "MCP probe title" : "TEST_AGENT_MCP_TOOL_CALL_OK" };
      const chunk = {
        id: "testagent-v2-mcp-model",
        object: "chat.completion.chunk",
        model: "mock",
        choices: [{ index: 0, delta, finish_reason: shouldCallMcp ? "tool_calls" : "stop" }],
      };
      response.writeHead(200, {
        "content-type": "text/event-stream",
        "cache-control": "no-cache",
        connection: "close",
      });
      response.write(`data: ${JSON.stringify(chunk)}\n\n`);
      response.end("data: [DONE]\n\n");
    });
  });
  await new Promise((done, fail) => modelServer.once("error", fail).listen(0, "127.0.0.1", done));
  return { server: modelServer, port: modelServer.address().port };
}

function appendModelTrace(traceFile, value) {
  // 模型探针只记录工具名，不记录提示词、凭据或工作区内容。
  appendFileSync(traceFile, `${JSON.stringify(value)}\n`);
}

const root = await mkdtemp(join(tmpdir(), "testagent-v2-mcp-"));
const configDir = join(root, "config");
const workspace = join(root, "workspace");
const home = join(root, "home");
const mcpScript = join(root, "mcp-probe.mjs");
const traceFile = join(root, "mcp-methods.log");
const modelTraceFile = join(root, "model-tools.log");
const port = await freePort();
const password = randomUUID();
let server;
let modelProbe;
let spawnError;
try {
  await Promise.all([mkdir(configDir), mkdir(workspace), mkdir(home)]);
  await writeFile(mcpScript, mockMcpServerSource(traceFile));
  await writeFile(traceFile, "");
  await writeFile(modelTraceFile, "");
  modelProbe = await startModelProbe(modelTraceFile);
  // 使用交付样例本身验证 provider/model/policy 配置，测试专用模型和 MCP 端点只存在于临时目录。
  const publicConfig = JSON.parse(await readFile(
    new URL("../deploy/internal/opencode.jsonc.example", import.meta.url), "utf8"));
  publicConfig.mcp.servers = {
    testagent_probe: {
      type: "local",
      command: [process.execPath, mcpScript],
      disabled: false,
      codemode: false,
    },
  };
  publicConfig.agents.build = { permissions: [{ action: "*", resource: "*", effect: "allow" }] };
  publicConfig.agents["probe-subagent"] = {
    mode: "subagent",
    model: "enterprise-deepseek/DeepSeek-V4-Flash-W8A8",
    permissions: [{ action: "*", resource: "*", effect: "allow" }],
  };
  publicConfig.experimental.subagent_depth = 1;
  publicConfig.commands = {
    "v2-native-smoke": { template: "Reply exactly V2_COMMAND_OK.", description: "Isolated V2 command probe" },
  };
  await writeFile(join(configDir, "opencode.jsonc"), JSON.stringify(publicConfig));
  server = spawn(resolve(cli), ["serve", "--hostname", "127.0.0.1", "--port", String(port)], {
    cwd: workspace,
    // 避免继承操作者的模型密钥、插件配置和平台运行环境。
    env: {
      PATH: process.env.PATH,
      TMPDIR: process.env.TMPDIR,
      LANG: process.env.LANG,
      HOME: home,
      XDG_CONFIG_HOME: join(root, "xdg-config"),
      OPENCODE_CONFIG_DIR: configDir,
      OPENCODE_MODELS_PATH: resolve(new URL("../deploy/internal/opencode-models.json", import.meta.url).pathname),
      OPENCODE_DISABLE_AUTOUPDATE: "true",
      OPENCODE_PASSWORD: password,
      TEST_AGENT_INTERNAL_PROXY_BASE_URL: `http://127.0.0.1:${modelProbe.port}/v1`,
      TEST_AGENT_INTERNAL_PROXY_API_KEY: "testagent-probe",
      ENTERPRISE_UCID: "testagent-probe",
    },
    stdio: "ignore",
  });
  server.on("error", (error) => { spawnError = error; });

  const base = `http://127.0.0.1:${port}`;
  const deadline = Date.now() + 45_000;
  let info;
  while (Date.now() < deadline) {
    if (spawnError) throw spawnError;
    if (server.exitCode !== null) throw new Error(`OpenCode exited early (${server.exitCode})`);
    try {
      info = await getJson(`${base}/api/info`, password);
      break;
    } catch {
      await delay(250);
    }
  }
  if (!info) throw new Error("OpenCode V2 /api/info readiness timed out");
  assert.equal(info?.version, "2.0.18", "frozen CLI version");
  const patchResponse = await fetch(`${base}/api/experimental/config`, {
    method: "PATCH",
    headers: {
      Authorization: `Basic ${Buffer.from(`opencode:${password}`).toString("base64")}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({ shell: null }),
  });
  assert.equal(patchResponse.status, 204, "V2 shell config PATCH");
  const query = new URLSearchParams({ "location[directory]": workspace });
  let mcp;
  while (Date.now() < deadline) {
    mcp = await getJson(`${base}/api/mcp?${query}`, password);
    if (mcp?.data?.some((item) => item.name === "testagent_probe" && item.status?.status === "connected")) break;
    await delay(250);
  }
  const probe = mcp?.data?.find((item) => item.name === "testagent_probe");
  assert.equal(probe?.status?.status, "connected", `MCP connection: ${JSON.stringify(probe?.status)}`);
  const catalog = await getJson(`${base}/api/mcp/resource?${query}`, password);
  const trace = (await readFile(traceFile, "utf8")).trim().split("\n");
  assert.ok(trace.includes("initialize"), "MCP initialize handshake");
  assert.ok(trace.includes("tools/list"), "MCP tool discovery");
  assert.ok(trace.includes("resources/list"), "MCP resource discovery");
  assert.ok(JSON.stringify(catalog.data).includes("testagent://v2-probe"), "MCP resource catalog");
  await delay(2_000);
  const sessionResponse = await fetch(`${base}/api/session`, {
    method: "POST",
    headers: {
      Authorization: `Basic ${Buffer.from(`opencode:${password}`).toString("base64")}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({ title: "MCP tool invocation", agent: "build", location: { directory: workspace } }),
  });
  assert.equal(sessionResponse.status, 200, "MCP probe session create");
  const sessionPayload = await sessionResponse.json();
  const session = sessionPayload?.data?.id ?? sessionPayload?.id;
  assert.match(session, /^ses/, "MCP probe session id");
  const promptResponse = await fetch(`${base}/api/session/${encodeURIComponent(session)}/prompt`, {
    method: "POST",
    headers: {
      Authorization: `Basic ${Buffer.from(`opencode:${password}`).toString("base64")}`,
      "content-type": "application/json",
    },
    signal: AbortSignal.timeout(45_000),
    body: JSON.stringify({ text: "Call the testagent_probe_ping MCP tool and return its marker." }),
  });
  assert.equal(promptResponse.status, 200, "MCP tool invocation prompt");
  const modelTrace = await readJsonLinesUntilTool(modelTraceFile, "testagent_probe_ping");
  assert.ok(
    modelTrace.some((entry) => entry.tools.includes("testagent_probe_ping")),
    `MCP tool is visible to model: ${JSON.stringify(modelTrace)}`,
  );
  const invocationTrace = await readLinesUntil(traceFile, "tools/call:ping");
  assert.ok(
    invocationTrace.includes("tools/call:ping"),
    `MCP tools/call invocation: ${JSON.stringify(invocationTrace)} model=${JSON.stringify(modelTrace)}`,
  );
  const modelResultTrace = (await readFile(modelTraceFile, "utf8")).trim().split("\n").filter(Boolean)
    .map((line) => JSON.parse(line));
  assert.ok(modelResultTrace.some((entry) => entry.toolResultHasMarker), "MCP result reached the model");
  assert.ok(modelResultTrace.some((entry) => entry.routeProvider === "deepseek-prod"),
    "V2 provider headers reached the model proxy");
  // 原生矩阵与 MCP 探针共用冻结 CLI、临时配置和假模型，覆盖命令、交互回复与子 Agent。
  const nativeScript = new URL("./test-opencode-v2-native-smoke.mjs", import.meta.url);
  const nativeResult = await exec(process.execPath, [nativeScript.pathname], {
    env: {
      PATH: process.env.PATH,
      OPENCODE_BASE_URL: base,
      TEST_AGENT_OPENCODE_SERVER_PASSWORD: password,
      OPENCODE_NATIVE_SMOKE_COMMAND_NAME: "v2-native-smoke",
      OPENCODE_NATIVE_SMOKE_SUBAGENT_NAME: "probe-subagent",
      OPENCODE_NATIVE_SMOKE_TIMEOUT_MS: "90000",
    },
    timeout: 180_000,
    maxBuffer: 1024 * 1024,
  }).catch(async (error) => {
    const trace = (await readFile(modelTraceFile, "utf8")).trim().split("\n").filter(Boolean)
      .map((line) => JSON.parse(line));
    throw new Error(`${error.stderr?.trim() ?? error.message}; modelProbe=${JSON.stringify(trace.map((entry) => ({
      subagentRequest: entry.subagentRequest,
      tools: entry.tools.filter((name) => name === "subagent"),
      calls: entry.messages.flatMap((message) => message.calls),
    })))}`);
  });
  const native = JSON.parse(nativeResult.stdout.trim());
  assert.equal(native.version, "2.0.18", "native matrix used frozen V2 CLI");
  assert.equal(native.command, true, "native session command completed");
  assert.equal(native.formReply, true, "native Form reply completed");
  assert.equal(native.permissionReply, true, "native permission reply completed");
  assert.equal(native.subagent, true, "native subagent completed");
  assert.equal(native.pagination, true, "native message pagination completed");
  assert.equal(native.errorRecovery, true, "native JSON error recovery completed");
  process.stdout.write(JSON.stringify({
    version: info.version,
    status: probe.status.status,
    methods: [...new Set(invocationTrace)],
    resource: "testagent://v2-probe",
    toolVisibleToModel: true,
    toolInvoked: true,
    native,
  }) + "\n");
} finally {
  if (server?.pid && server.exitCode === null && server.signalCode === null) {
    server.kill("SIGTERM");
    await Promise.race([new Promise((done) => server.once("exit", done)), delay(3_000)]);
    if (server.exitCode === null && server.signalCode === null) {
      server.kill("SIGKILL");
      await Promise.race([new Promise((done) => server.once("exit", done)), delay(3_000)]);
    }
  }
  if (modelProbe?.server) {
    await new Promise((done) => modelProbe.server.close(done));
  }
  await rm(root, { recursive: true, force: true });
}
