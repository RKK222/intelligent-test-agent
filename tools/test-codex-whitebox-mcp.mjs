#!/usr/bin/env node

import assert from "node:assert/strict";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import test from "node:test";

const root = process.env.TEST_AGENT_OPENCODE_RUNTIME_ROOT;
if (!root) throw new Error("TEST_AGENT_OPENCODE_RUNTIME_ROOT is required");
const sdkRoot = pathToFileURL(`${resolve(root, "node_modules/@modelcontextprotocol/sdk/dist/esm")}/`);
const facadeUrl = process.env.TEST_AGENT_CODEX_FACADE_PATH
  ? pathToFileURL(resolve(process.env.TEST_AGENT_CODEX_FACADE_PATH)).href
  : new URL("../deploy/internal/codex-whitebox-mcp.mjs", import.meta.url).href;
const managedPolicyPath = process.env.TEST_AGENT_CODEX_POLICY_PATH
  ?? resolve("deploy/internal/codex-whitebox-requirements.toml");
const [{ Client }, { InMemoryTransport }, facade] = await Promise.all([
  import(new URL("client/index.js", sdkRoot).href),
  import(new URL("inMemory.js", sdkRoot).href),
  import(facadeUrl),
]);

function configuration() {
  return {
    workspace: "/workspace/current",
    proxyBaseUrl: "http://backend:8080/api/internal/platform/opencode-runtime/internal-model-proxy/v1",
    proxyApiKey: "proxy-secret-value",
    providerId: "deepseek-prod",
    model: "DeepSeek-V4-Flash-W8A8",
    contextWindow: 65536,
    ucid: "001177621",
    codexBin: "/programs/codex/bin/codex-official",
  };
}

function fakeRawClient() {
  const calls = [];
  let closeCount = 0;
  return {
    calls,
    get closeCount() {
      return closeCount;
    },
    async callTool(request, _schema, options) {
      calls.push({ request, options });
      if (options?.signal?.aborted) throw new Error("aborted");
      const threadId = request.name === "codex" ? "thread-owned" : request.arguments.threadId;
      return {
        content: [{ type: "text", text: request.name === "codex" ? "分析正文" : "续写正文" }],
        structuredContent: { threadId },
      };
    },
    async close() {
      closeCount += 1;
    },
  };
}

async function connectedClient(rawClient = fakeRawClient()) {
  const runtime = new facade.CodexRuntime(configuration(), {
    clientFactory: async () => rawClient,
  });
  const server = facade.createWhiteboxServer(runtime);
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: "whitebox-contract-test", version: "1.0.0" });
  await Promise.all([server.connect(serverTransport), client.connect(clientTransport)]);
  return { client, server, runtime, rawClient };
}

test("only exposes two safe tools without cwd model config or sandbox inputs", async () => {
  const active = await connectedClient();
  try {
    const listed = await active.client.listTools();
    assert.deepEqual(listed.tools.map((tool) => tool.name).sort(), ["whitebox_analyze", "whitebox_reply"]);
    const serialized = JSON.stringify(listed.tools);
    for (const dangerous of ["cwd", "model", "config", "sandbox", "approval-policy", "developer-instructions"]) {
      assert.equal(serialized.includes(`\"${dangerous}\"`), false, dangerous);
    }
    assert.equal(listed.tools.every((tool) => tool.annotations?.readOnlyHint === true), true);
    assert.equal(listed.tools.every((tool) => tool.annotations?.destructiveHint === false), true);
  } finally {
    await active.client.close();
    await active.server.close();
    await active.runtime.reset();
  }
});

test("fixes workspace model approval policy and accepts only owned threads", async () => {
  const active = await connectedClient();
  try {
    const analyze = await active.client.callTool({
      name: "whitebox_analyze",
      arguments: { prompt: "定位代码入口" },
    });
    assert.equal(analyze.isError, undefined);
    assert.equal(analyze.structuredContent.threadId, "thread-owned");
    const rawAnalyze = active.rawClient.calls[0].request;
    assert.equal(rawAnalyze.name, "codex");
    assert.equal(rawAnalyze.arguments.cwd, configuration().workspace);
    assert.equal(rawAnalyze.arguments.model, configuration().model);
    assert.equal(rawAnalyze.arguments["approval-policy"], "never");
    assert.equal(typeof rawAnalyze.arguments["developer-instructions"], "string");
    assert.equal(Object.hasOwn(rawAnalyze.arguments, "config"), false);
    assert.equal(Object.hasOwn(rawAnalyze.arguments, "sandbox"), false);

    const unknown = await active.client.callTool({
      name: "whitebox_reply",
      arguments: { threadId: "thread-from-another-process", prompt: "继续" },
    });
    assert.equal(unknown.isError, true);
    assert.match(unknown.content[0].text, /WHITEBOX_THREAD_NOT_FOUND/);
    assert.equal(active.rawClient.calls.length, 1);

    const reply = await active.client.callTool({
      name: "whitebox_reply",
      arguments: { threadId: "thread-owned", prompt: "继续" },
    });
    assert.equal(reply.structuredContent.threadId, "thread-owned");
    assert.equal(active.rawClient.calls[1].request.name, "codex-reply");
  } finally {
    await active.client.close();
    await active.server.close();
    await active.runtime.reset();
  }
});

test("fails closed when configuration or managed policy is missing", async () => {
  const env = {
    TEST_AGENT_INTERNAL_PROXY_BASE_URL: configuration().proxyBaseUrl,
    TEST_AGENT_INTERNAL_PROXY_API_KEY: configuration().proxyApiKey,
    TEST_AGENT_CODEX_PROVIDER_ID: configuration().providerId,
    TEST_AGENT_CODEX_MODEL: configuration().model,
    TEST_AGENT_CODEX_CONTEXT_WINDOW: String(configuration().contextWindow),
    ENTERPRISE_UCID: configuration().ucid,
    TEST_AGENT_CODEX_BIN: configuration().codexBin,
  };
  await assert.rejects(
    facade.loadConfiguration(env, { workspace: process.cwd(), policyPath: "/does-not-exist/requirements.toml" }),
    /WHITEBOX_POLICY_MISSING/,
  );
  delete env.TEST_AGENT_CODEX_MODEL;
  await assert.rejects(
    facade.loadConfiguration(env, {
      workspace: process.cwd(),
      policyPath: managedPolicyPath,
    }),
    /WHITEBOX_CONFIG_MISSING:TEST_AGENT_CODEX_MODEL/,
  );
});

test("sanitizes audit logs and closes raw client after failure cancellation and timeout", async () => {
  const rawClient = fakeRawClient();
  rawClient.callTool = async () => {
    throw new Error(`upstream leaked secret prompt body ${configuration().proxyApiKey} ${configuration().workspace}`);
  };
  const runtime = new facade.CodexRuntime(configuration(), { clientFactory: async () => rawClient });
  const originalWrite = process.stderr.write;
  let logs = "";
  process.stderr.write = (chunk) => {
    logs += String(chunk);
    return true;
  };
  try {
    const server = facade.createWhiteboxServer(runtime);
    const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
    const client = new Client({ name: "cancel-test", version: "1.0.0" });
    await Promise.all([server.connect(serverTransport), client.connect(clientTransport)]);
    const failed = await client.callTool({
      name: "whitebox_analyze",
      arguments: { prompt: "secret prompt body" },
    });
    assert.equal(failed.isError, true);
    assert.match(failed.content[0].text, /WHITEBOX_CODEX_FAILED/);
    assert.match(logs, /"status":"FAILED"/);
    assert.match(logs, /"errorCode":"WHITEBOX_CODEX_FAILED"/);
    assert.equal(rawClient.closeCount, 1);
    assert.equal(logs.includes("secret prompt body"), false);
    assert.equal(logs.includes(configuration().proxyApiKey), false);
    assert.equal(logs.includes(configuration().workspace), false);

    const cancelledClient = fakeRawClient();
    const cancelledRuntime = new facade.CodexRuntime(configuration(), {
      clientFactory: async () => cancelledClient,
    });
    const controller = new AbortController();
    controller.abort();
    await assert.rejects(cancelledRuntime.analyze("cancelled prompt", controller.signal), /WHITEBOX_CANCELLED/);
    assert.equal(cancelledClient.closeCount, 1);
    await cancelledRuntime.reset();

    const timedOutClient = fakeRawClient();
    timedOutClient.callTool = async () => {
      throw new Error("MCP request timed out with sensitive upstream details");
    };
    const timedOutRuntime = new facade.CodexRuntime(configuration(), {
      clientFactory: async () => timedOutClient,
    });
    await assert.rejects(timedOutRuntime.analyze("timeout prompt"), /WHITEBOX_TIMEOUT/);
    assert.equal(timedOutClient.closeCount, 1);
    await timedOutRuntime.reset();
    await client.close();
    await server.close();
  } finally {
    process.stderr.write = originalWrite;
    await runtime.reset();
  }
});
