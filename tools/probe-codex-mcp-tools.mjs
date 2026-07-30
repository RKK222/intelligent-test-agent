#!/usr/bin/env node

import assert from "node:assert/strict";
import { Client } from "/usr/local/lib/opencode/node_modules/@modelcontextprotocol/sdk/dist/esm/client/index.js";
import { StdioClientTransport } from "/usr/local/lib/opencode/node_modules/@modelcontextprotocol/sdk/dist/esm/client/stdio.js";

const mode = process.argv[2];
const command = mode === "raw"
  ? "/usr/local/lib/codex/bin/codex-official"
  : "/usr/local/bin/test-agent-codex-mcp";
const args = mode === "raw" ? ["mcp-server"] : [];
const expected = ["codex", "codex-reply"];
const transport = new StdioClientTransport({
  command,
  args,
  cwd: "/data/testagent/data/agent-opencode/workspace",
  env: Object.fromEntries(Object.entries(process.env).filter(([, value]) => typeof value === "string")),
  stderr: "pipe",
});
transport.stderr?.on("data", () => {});
const client = new Client({ name: "codex-worker-smoke", version: "1.0.0" });
try {
  await client.connect(transport);
  const listed = await client.listTools();
  assert.deepEqual(listed.tools.map((tool) => tool.name).sort(), expected.slice().sort());
  const schema = JSON.stringify(listed.tools.find((tool) => tool.name === "codex")?.inputSchema);
  for (const nativeInput of [
    "cwd",
    "model",
    "config",
    "sandbox",
    "approval-policy",
    "base-instructions",
    "developer-instructions",
  ]) {
    assert.equal(schema.includes(`\"${nativeInput}\"`), true, nativeInput);
  }
  process.stdout.write(`${mode}:official-contract:${expected.join(",")}\n`);
} finally {
  await client.close().catch(() => {});
  await transport.close().catch(() => {});
}
