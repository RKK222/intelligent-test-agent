#!/usr/bin/env node

/**
 * 在独立 HOME、配置目录和临时端口启动冻结的 V2 CLI，使用自有 stdio MCP fixture
 * 验证握手、工具发现及资源目录。fixture 只读临时目录，不接触平台运行实例。
 *
 * 用法：OPENCODE_V2_BIN=/path/to/opencode node tools/test-opencode-v2-mcp-smoke.mjs
 */

import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { access, mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { createServer } from "node:net";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";

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

function mockMcpServerSource() {
  return `import { appendFileSync } from "node:fs";
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
    appendFileSync(process.env.TEST_AGENT_MCP_TRACE, request.method + "\\n");
    if (request.id === undefined) continue;
    let result;
    switch (request.method) {
      case "initialize":
        result = { protocolVersion: request.params?.protocolVersion ?? "2025-03-26", capabilities: { tools: { listChanged: false }, resources: { listChanged: false } }, serverInfo: { name: "testagent-v2-mcp-probe", version: "1.0.0" } };
        break;
      case "tools/list":
        result = { tools: [{ name: "testagent_ping", description: "Local V2 MCP probe", inputSchema: { type: "object", properties: {}, additionalProperties: false } }] };
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

const root = await mkdtemp(join(tmpdir(), "testagent-v2-mcp-"));
const configDir = join(root, "config");
const workspace = join(root, "workspace");
const home = join(root, "home");
const mcpScript = join(root, "mcp-probe.mjs");
const traceFile = join(root, "mcp-methods.log");
const port = await freePort();
const password = randomUUID();
let server;
let spawnError;
try {
  await Promise.all([mkdir(configDir), mkdir(workspace), mkdir(home)]);
  await writeFile(mcpScript, mockMcpServerSource());
  await writeFile(traceFile, "");
  await writeFile(join(configDir, "opencode.jsonc"), JSON.stringify({
    mcp: {
      testagent_probe: {
        type: "local",
        command: [process.execPath, mcpScript],
        enabled: true,
        timeout: 30_000,
      },
    },
  }));
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
      OPENCODE_DISABLE_AUTOUPDATE: "true",
      OPENCODE_PASSWORD: password,
      TEST_AGENT_MCP_TRACE: traceFile,
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
  process.stdout.write(JSON.stringify({ version: info.version, status: probe.status.status, methods: [...new Set(trace)], resource: "testagent://v2-probe" }) + "\n");
} finally {
  if (server?.pid && server.exitCode === null && server.signalCode === null) {
    server.kill("SIGTERM");
    await Promise.race([new Promise((done) => server.once("exit", done)), delay(3_000)]);
    if (server.exitCode === null && server.signalCode === null) {
      server.kill("SIGKILL");
      await Promise.race([new Promise((done) => server.once("exit", done)), delay(3_000)]);
    }
  }
  await rm(root, { recursive: true, force: true });
}
