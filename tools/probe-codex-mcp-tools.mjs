#!/usr/bin/env node

import assert from "node:assert/strict";
import { Client } from "/usr/local/lib/opencode/node_modules/@modelcontextprotocol/sdk/dist/esm/client/index.js";
import { StdioClientTransport } from "/usr/local/lib/opencode/node_modules/@modelcontextprotocol/sdk/dist/esm/client/stdio.js";

const mode = process.argv[2];
const command = mode === "raw"
  ? "/usr/local/lib/codex/bin/codex-official"
  : "/usr/local/bin/test-agent-codex-mcp";
const args = mode === "raw" ? ["mcp-server"] : [];
const expected = mode === "raw"
  ? ["codex", "codex-reply"]
  : ["whitebox_analyze", "whitebox_reply"];
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
  if (mode === "facade") {
    const schema = JSON.stringify(listed.tools);
    for (const forbidden of ["cwd", "model", "config", "sandbox", "approval-policy", "developer-instructions"]) {
      assert.equal(schema.includes(`\"${forbidden}\"`), false, forbidden);
    }
  }
  process.stdout.write(`${mode}:${expected.join(",")}\n`);
} finally {
  await client.close().catch(() => {});
  await transport.close().catch(() => {});
}
