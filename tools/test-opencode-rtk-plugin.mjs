#!/usr/bin/env node

import assert from "node:assert/strict"
import test from "node:test"

import { createRtkPlugin } from "../deploy/internal/opencode-rtk-plugin.mjs"

function invoke(plugin, tool, command) {
  const output = { args: { command } }
  return Promise.resolve(plugin["tool.execute.before"]?.({ tool }, output)).then(() => output.args.command)
}

test("keeps the hook disabled unless the managed runtime flag is true", async () => {
  const plugin = createRtkPlugin({ env: { TEST_AGENT_RTK_ENABLED: "false" } })
  assert.deepEqual(plugin, {})
})
test("rewrites bash and shell commands through the pinned RTK binary", async () => {
  const calls = []
  const plugin = createRtkPlugin({
    env: { TEST_AGENT_RTK_ENABLED: "true", TEST_AGENT_RTK_BIN: "/runtime/bin/rtk" },
    runner: (binary, args, options, callback) => {
      calls.push({ binary, args, options })
      callback(null, "git diff --stat\n", "")
    },
  })

  assert.equal(await invoke(plugin, "bash", "git diff"), "git diff --stat")
  assert.equal(await invoke(plugin, "shell", "git diff"), "git diff --stat")
  assert.equal(calls.length, 2)
  assert.deepEqual(calls[0].args, ["rewrite", "git diff"])
  assert.equal(calls[0].binary, "/runtime/bin/rtk")
  assert.equal(calls[0].options.timeout, 2000)
  assert.equal(calls[0].options.maxBuffer, 1024 * 1024)
})

test("fails open for unsupported tools, deny responses, and binary failures", async () => {
  const plugin = createRtkPlugin({
    env: { TEST_AGENT_RTK_ENABLED: "true" },
    runner: (_binary, args, _options, callback) => {
      if (args[1] === "deny") callback(Object.assign(new Error("deny"), { code: 2 }), "", "")
      else callback(new Error("missing"), "", "")
    },
  })

  assert.equal(await invoke(plugin, "read", "git diff"), "git diff")
  assert.equal(await invoke(plugin, "bash", "deny"), "deny")
  assert.equal(await invoke(plugin, "bash", "git diff"), "git diff")
})
