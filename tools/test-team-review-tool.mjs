import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import vm from "node:vm";
import { test } from "node:test";
import assert from "node:assert/strict";

// 只替换插件的 schema 壳和网络边界；执行实际交付 Tool 的 execute，不复制其实现。
const require = createRequire(new URL("../frontend/package.json", import.meta.url));
const ts = require("typescript");
const raw = await readFile(new URL("../public-agent-config/opencode/tools/team-review.ts", import.meta.url), "utf8");
const source = ts.transpileModule(raw.replace('import { tool } from "@opencode-ai/plugin"', "const tool = globalThis.testTool"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
}).outputText;
function fixture({ configured = true, forbidden = false, deferred = false } = {}) {
  const chain = new Proxy({}, { get: () => () => chain });
  const tool = Object.assign(value => value, { schema: new Proxy({}, { get: () => () => chain }) });
  const sent = [];
  const sockets = [];
  const requests = [];
  class Socket {
    constructor(url, options) { this.url = url; this.options = options; sockets.push(this); queueMicrotask(() => this.onopen?.()); }
    send(value) { sent.push(JSON.parse(value)); if (!deferred) queueMicrotask(() => this.onmessage?.({ data: JSON.stringify({ id: "review", type: "result", data: { chunk: { content: "第30个文件", eof: true } } }) })); }
    close() { this.closed = true; this.onclose?.(); }
  }
  const sandbox = { exports: {}, testTool: tool, process: { env: configured ? {
    TEST_AGENT_PLATFORM_BASE_URL: "http://platform", TEST_AGENT_TEAM_REVIEW_TOOL_TOKEN: "private-credential"
  } : {} }, WebSocket: Socket, AbortSignal, setTimeout, clearTimeout,
    fetch: async (url, options) => { requests.push({ url, options }); return {
      ok: !forbidden, json: async () => forbidden ? { code: "FORBIDDEN" } : {
        data: { baseUrl: "http://owner", webSocketUrl: "/file/ws?ticket=one", origin: "http://allowed" }
      }
    }; } };
  vm.runInNewContext(source, sandbox);
  const abort = new AbortController();
  return { execute: sandbox.exports.default.execute, context: { sessionID: "native-session", abort: abort.signal, metadata() {} }, abort, sockets, sent, requests };
}
test("Tool reads beyond old snapshot through versioned RPC without leaking credential", async () => {
  const f = fixture();
  const result = await f.execute({ scopeId: "scope", action: "read", path: "spec/file-30.md", contentVersion: "sha256:version", offset: 512 }, f.context);
  assert.match(result, /第30个文件/); assert.doesNotMatch(result, /private-credential/);
  assert.equal(f.requests[0].url, "http://platform/api/internal/agent/opencode/team-review-tool/ticket");
  assert.deepEqual(JSON.parse(f.requests[0].options.body), { scopeId: "scope", sessionId: "native-session" });
  assert.equal(f.sent[0].op, "team.review.read");
  assert.equal(f.sent[0].params.contentVersion, "sha256:version");
  assert.equal(f.sent[0].params.offset, 512); assert.equal(f.sent[0].params.workspaceId, undefined);
  assert.equal(f.sockets[0].options.headers.Origin, "http://allowed"); assert.equal(f.sockets[0].closed, true);
});
test("Tool fails closed without its dedicated credential or when ticket is denied", async () => {
  const empty = fixture({ configured: false });
  await assert.rejects(empty.execute({ scopeId: "scope", action: "list" }, empty.context), /未配置/);
  const denied = fixture({ forbidden: true });
  assert.equal(JSON.parse(await denied.execute({ scopeId: "scope", action: "list" }, denied.context)).code, "FORBIDDEN");
  assert.equal(denied.sockets.length, 0);
});
test("search forwards explicit continuation and cancellation closes socket", async () => {
  const f = fixture({ deferred: true });
  const pending = f.execute({ scopeId: "scope", action: "search", query: "spec", remainingDirectories: ["spec/sub"] }, f.context);
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(f.sent[0].params.remainingDirectories, ["spec/sub"]);
  f.abort.abort(); await assert.rejects(pending, /已取消/);
  assert.equal(f.sockets[0].closed, true);
});
