#!/usr/bin/env node

import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { chmod, mkdir, readFile, writeFile } from "node:fs/promises";
import http from "node:http";
import { Client } from "/usr/local/lib/opencode/node_modules/@modelcontextprotocol/sdk/dist/esm/client/index.js";
import { StdioClientTransport } from "/usr/local/lib/opencode/node_modules/@modelcontextprotocol/sdk/dist/esm/client/stdio.js";

if (process.argv.includes("--verify-routing")) {
  const decision = inspectFakeModelInput([
    { type: "message", role: "user", content: "SCENARIO_READ" },
    { type: "function_call_output", output: "WHITEBOX_MARKER=original" },
    { type: "message", role: "user", content: "SCENARIO_REPLY 继续总结" },
  ]);
  assert.equal(decision.kind, "reply");
  assert.equal(decision.toolOutputs.length, 1);
  assert.match(decision.serialized, /SCENARIO_READ/);
  process.stdout.write("codex-official-e2e-routing:reply-priority-ok\n");
  process.exit(0);
}

const protocolOnly = process.argv.includes("--protocol-only");

const workspace = "/workspace/test-agent-codex-e2e-workspace";
await mkdir(workspace, { recursive: true });
await writeFile(`${workspace}/source.txt`, "WHITEBOX_MARKER=original\n", "utf8");
await chmod(`${workspace}/source.txt`, 0o644);
execFileSync("git", ["init", "-q"], { cwd: workspace });
execFileSync("git", ["add", "source.txt"], { cwd: workspace });
const gitBefore = execFileSync("git", ["status", "--short"], { cwd: workspace, encoding: "utf8" });

const commandOutputs = [];
const server = http.createServer(async (request, response) => {
  if (request.method !== "POST" || request.url !== "/v1/responses") {
    response.writeHead(404).end();
    return;
  }
  assert.equal(request.headers.authorization, "Bearer smoke-proxy-key");
  assert.equal(request.headers["x-enterprise-model-provider"], "smoke-provider");
  assert.equal(request.headers.ucid, "smoke-ucid");
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  const body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
  assert.equal(body.model, "smoke-model");
  assert.equal(body.stream, true);

  const decision = inspectFakeModelInput(body.input);
  // 续写请求包含前一轮 function_call_output；必须优先识别本轮提示，不能误回第一轮结果。
  if (decision.kind === "reply") {
    assert.match(decision.serialized, /SCENARIO_(?:READ|TEXT)/);
    if (decision.serialized.includes("SCENARIO_READ")) {
      assert.ok(decision.toolOutputs.length > 0, "reply request did not preserve prior tool output");
    }
    sendTextResponse(response, "follow-up observed");
    return;
  }
  if (decision.kind === "tool-output") {
    commandOutputs.push(...decision.toolOutputs);
    sendTextResponse(response, "command observed");
    return;
  }
  if (decision.kind === "text") {
    sendTextResponse(response, "follow-up observed");
    return;
  }
  const tool = body.tools.find((candidate) =>
    candidate.type === "function" && ["exec_command", "shell"].includes(candidate.name));
  assert.ok(tool, `missing shell tool: ${body.tools.map((item) => item.name).join(",")}`);
  const properties = tool.parameters?.properties || {};
  const args = Object.hasOwn(properties, "cmd")
    ? { cmd: decision.command }
    : { command: decision.command };
  sendFunctionResponse(response, tool.name, args);
});
await new Promise((resolve, reject) => {
  server.once("error", reject);
  server.listen(18080, "127.0.0.1", resolve);
});

process.chdir(workspace);
const transport = new StdioClientTransport({
  command: "/usr/local/bin/test-agent-codex-mcp",
  cwd: workspace,
  env: {
    ...Object.fromEntries(Object.entries(process.env).filter(([, value]) => typeof value === "string")),
    TEST_AGENT_INTERNAL_PROXY_BASE_URL: "http://127.0.0.1:18080/v1",
    TEST_AGENT_INTERNAL_PROXY_API_KEY: "smoke-proxy-key",
    TEST_AGENT_CODEX_PROVIDER_ID: "smoke-provider",
    TEST_AGENT_CODEX_MODEL: "smoke-model",
    TEST_AGENT_CODEX_CONTEXT_WINDOW: "131072",
    ENTERPRISE_UCID: "smoke-ucid",
  },
  stderr: "pipe",
});
transport.stderr?.on("data", (chunk) => {
  if (process.env.TEST_AGENT_CODEX_E2E_DEBUG === "true") process.stderr.write(chunk);
});
const client = new Client({ name: "codex-official-e2e", version: "1.0.0" });

try {
  await client.connect(transport);
  if (protocolOnly) {
    // Apple Silicon 的 amd64 仿真无法运行嵌套 namespace；仍需真实覆盖企业 Responses 与续写链路。
    const first = await analyze("SCENARIO_TEXT", "danger-full-access");
    assert.match(first.content[0].text, /follow-up observed/);
    assert.equal(typeof first.structuredContent.threadId, "string");
    const reply = await replyTo(first.structuredContent.threadId);
    assert.match(reply.content[0].text, /follow-up observed/);
    process.stdout.write("codex-official-e2e:enterprise-responses-ok,thread-id-ok,reply-ok\n");
    process.exitCode = 0;
  } else {
    const readResult = await analyze("SCENARIO_READ");
    assert.match(readResult.content[0].text, /command observed/);
    assert.equal(typeof readResult.structuredContent.threadId, "string");
    assert.match(commandOutputs.at(-1), /WHITEBOX_MARKER=original/);
    assert.match(commandOutputs.at(-1), /test-agent-codex-e2e-workspace/);

    await analyze("SCENARIO_WRITE");
    assert.equal(await readFile(`${workspace}/source.txt`, "utf8"), "WHITEBOX_MARKER=original\n");
    assert.doesNotMatch(commandOutputs.at(-1), /hacked/);

    const reply = await replyTo(readResult.structuredContent.threadId);
    assert.match(reply.content[0].text, /follow-up observed/);

    const gitAfter = execFileSync("git", ["status", "--short"], { cwd: workspace, encoding: "utf8" });
    assert.equal(gitAfter, gitBefore);
    process.stdout.write("codex-official-e2e:cwd-ok,read-ok,native-read-only-write-denied,git-clean,reply-ok\n");
  }
} finally {
  await client.close().catch(() => {});
  await transport.close().catch(() => {});
  await new Promise((resolve) => server.close(resolve));
}

async function analyze(prompt, sandbox = "read-only") {
  const result = await client.callTool(
    {
      name: "codex",
      arguments: {
        prompt,
        cwd: workspace,
        sandbox,
        "approval-policy": "never",
      },
    },
    undefined,
    { timeout: 60_000, maxTotalTimeout: 60_000 },
  );
  assert.equal(result.isError, undefined, JSON.stringify(result));
  return result;
}

async function replyTo(threadId) {
  const result = await client.callTool({
    name: "codex-reply",
    arguments: { threadId, prompt: "SCENARIO_REPLY 继续总结" },
  }, undefined, { timeout: 60_000, maxTotalTimeout: 60_000 });
  assert.equal(result.isError, undefined, JSON.stringify(result));
  return result;
}

function inspectFakeModelInput(input) {
  const serialized = JSON.stringify(input);
  const toolOutputs = input
    .filter((candidate) => candidate.type === "function_call_output")
    .map((item) => String(item.output || ""));
  if (serialized.includes("SCENARIO_REPLY")) {
    return { kind: "reply", serialized, toolOutputs };
  }
  if (toolOutputs.length > 0) {
    return { kind: "tool-output", serialized, toolOutputs };
  }
  const command = serialized.includes("SCENARIO_READ")
    ? "pwd && rg -n WHITEBOX_MARKER source.txt"
    : serialized.includes("SCENARIO_WRITE")
      ? "printf 'hacked\\n' > source.txt"
      : null;
  return command
    ? { kind: "command", command, serialized, toolOutputs }
    : { kind: "text", serialized, toolOutputs };
}

function writeEvent(response, event, data) {
  response.write(`event: ${event}\n`);
  response.write(`data: ${JSON.stringify(data)}\n\n`);
}

function beginResponse(response, id) {
  response.writeHead(200, {
    "content-type": "text/event-stream",
    "cache-control": "no-cache",
    connection: "keep-alive",
  });
  writeEvent(response, "response.created", {
    type: "response.created",
    response: { id, object: "response", status: "in_progress", output: [] },
  });
}

function sendTextResponse(response, text) {
  const id = `resp_${Date.now()}`;
  const itemId = `msg_${Date.now()}`;
  beginResponse(response, id);
  writeEvent(response, "response.output_item.added", {
    type: "response.output_item.added",
    output_index: 0,
    item: { type: "message", id: itemId, role: "assistant", content: [{ type: "output_text", text: "" }] },
  });
  writeEvent(response, "response.output_text.delta", {
    type: "response.output_text.delta",
    item_id: itemId,
    output_index: 0,
    content_index: 0,
    delta: text,
  });
  writeEvent(response, "response.output_item.done", {
    type: "response.output_item.done",
    output_index: 0,
    item: { type: "message", id: itemId, role: "assistant", content: [{ type: "output_text", text }] },
  });
  complete(response, id, true);
}

function sendFunctionResponse(response, name, args) {
  const id = `resp_${Date.now()}`;
  const itemId = `fc_${Date.now()}`;
  const callId = `call_${Date.now()}`;
  const argumentsText = JSON.stringify(args);
  beginResponse(response, id);
  writeEvent(response, "response.output_item.added", {
    type: "response.output_item.added",
    output_index: 0,
    item: { type: "function_call", id: itemId, call_id: callId, name, arguments: "" },
  });
  writeEvent(response, "response.function_call_arguments.delta", {
    type: "response.function_call_arguments.delta",
    item_id: itemId,
    call_id: callId,
    output_index: 0,
    delta: argumentsText,
  });
  writeEvent(response, "response.output_item.done", {
    type: "response.output_item.done",
    output_index: 0,
    item: { type: "function_call", id: itemId, call_id: callId, name, arguments: argumentsText },
  });
  complete(response, id, false);
}

function complete(response, id, endTurn) {
  writeEvent(response, "response.completed", {
    type: "response.completed",
    response: {
      id,
      status: "completed",
      end_turn: endTurn,
      usage: {
        input_tokens: 10,
        input_tokens_details: { cached_tokens: 0, cache_write_tokens: 0 },
        output_tokens: 2,
        output_tokens_details: { reasoning_tokens: 0 },
        total_tokens: 12,
      },
    },
  });
  response.end();
}
