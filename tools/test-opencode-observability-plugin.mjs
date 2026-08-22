#!/usr/bin/env node

import assert from "node:assert/strict"
import test from "node:test"

import {
  createObservabilityPlugin,
  sanitizeObservabilityValue,
} from "../deploy/internal/opencode-observability-plugin.mjs"
import observabilityPluginModule from "../deploy/internal/opencode-observability-plugin.mjs"

function testRuntime(fetchImpl, nowValues = []) {
  let index = 0
  return createObservabilityPlugin({
    env: {
      TEST_AGENT_OBSERVABILITY_BASE_URL: "http://127.0.0.1:18081",
      TEST_AGENT_OBSERVABILITY_TOKEN: "local-token",
      TEST_AGENT_OBSERVABILITY_GENERATION: "generation-1",
      TEST_AGENT_OBSERVABILITY_RUNTIME_KIND: "LOCAL_CLIENT",
      TEST_AGENT_OBSERVABILITY_QUEUE_BYTES: "8388608",
      TEST_AGENT_OBSERVABILITY_BATCH_BYTES: "1048576",
    },
    fetch: fetchImpl,
    now: () => nowValues[index++] ?? 1_700_000_000_000 + index,
    queueMicrotask: (callback) => callback(),
    setTimeout: (callback) => ({ callback, unref() {} }),
    clearTimeout: () => {},
  })
}

test("exports the OpenCode 1.18.x V1 server plugin entry", () => {
  assert.equal(observabilityPluginModule.id, "test-agent-opencode-observability")
  assert.equal(typeof observabilityPluginModule.server, "function")
})

test("correlates test-design skill before and after by callID without parsing title", async () => {
  const requests = []
  const runtime = testRuntime(async (url, request) => {
    requests.push({ url, request })
    return { ok: true, status: 200 }
  }, [1000, 1010, 1020, 1060, 1070])

  runtime.hooks["tool.execute.before"](
    { tool: "skill", sessionID: "ses-1", callID: "call-1" },
    { args: { name: "test-design", input: "设计登录用例" } },
  )
  runtime.hooks["tool.execute.after"](
    { tool: "skill", sessionID: "ses-1", callID: "call-1", args: {} },
    { title: "arbitrary title", output: "ok", metadata: { name: "wrong-fallback" } },
  )
  await runtime.flush()

  assert.equal(requests.length, 1)
  assert.equal(requests[0].url, "http://127.0.0.1:18081/api/internal/agent/opencode-observability/v1/events")
  const body = JSON.parse(requests[0].request.body)
  assert.equal(body.events.length, 2)
  assert.deepEqual(body.events.map((event) => event.callId), ["call-1", "call-1"])
  assert.deepEqual(body.events.map((event) => event.payload.skillName), ["test-design", "test-design"])
  assert.equal(body.events[1].payload.durationMs, 50)
  assert.deepEqual(body.events.map((event) => event.sessionSequence), [1, 2])
  assert.equal(new Set(body.events.map((event) => event.eventId)).size, 2)
})

test("redacts credentials and replaces binary bodies with metadata before transport", () => {
  const binary = new Uint8Array([1, 2, 3])
  const result = sanitizeObservabilityValue({
    Authorization: "Bearer abc.def.ghi",
    text: "SERVICE_API_KEY=very-secret Bearer raw-token AKIAABCDEFGHIJKLMNOP",
    nested: { cookie: "session=secret" },
    binary,
  })

  assert.equal(result.Authorization, "[REDACTED]")
  assert.equal(result.nested.cookie, "[REDACTED]")
  assert.doesNotMatch(result.text, /very-secret|raw-token|AKIAABCDEFGHIJKLMNOP/u)
  assert.deepEqual(result.binary, {
    binary: true,
    contentType: "Uint8Array",
    size: 3,
    sha256: "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81",
  })
})

test("never throws or waits on network from a hook and retains failed batches for retry", async () => {
  let calls = 0
  const runtime = testRuntime(async () => {
    calls += 1
    throw new Error("offline")
  })

  assert.doesNotThrow(() => runtime.hooks.event({
    event: { type: "message.part.updated", properties: { sessionID: "ses-2", delta: "reasoning" } },
  }))
  await runtime.flush()

  assert.equal(calls, 1)
  assert.equal(runtime.inspect().serialized, 1)
})

test("fragments a large tool result without losing its sanitized payload", async () => {
  const requests = []
  const runtime = testRuntime(async (_url, request) => {
    requests.push(JSON.parse(request.body))
    return { ok: true, status: 200 }
  })
  const largeResult = "完整工具输出".repeat(80_000)

  runtime.hooks["tool.execute.before"](
    { tool: "skill", sessionID: "ses-large", callID: "call-large" },
    { args: { name: "test-design" } },
  )
  runtime.hooks["tool.execute.after"](
    { tool: "skill", sessionID: "ses-large", callID: "call-large" },
    { output: largeResult, metadata: { name: "test-design" } },
  )
  while (runtime.inspect().serialized > 0) await runtime.flush()

  const events = requests.flatMap((batch) => batch.events)
  const primary = events.find((event) => event.type === "TOOL_EXECUTE_AFTER")
  const fragments = events
    .filter((event) => event.type === "PAYLOAD_FRAGMENT"
      && event.payload.fragmentGroupId === primary.payload.fragmentedPayload.fragmentGroupId)
    .sort((left, right) => left.payload.fragmentIndex - right.payload.fragmentIndex)
  const reconstructed = JSON.parse(Buffer.concat(
    fragments.map((event) => Buffer.from(event.payload.dataBase64, "base64")),
  ).toString("utf8"))

  assert.equal(primary.payload.skillName, "test-design")
  assert.equal(fragments.length, primary.payload.fragmentedPayload.fragmentCount)
  assert.equal(reconstructed.result, largeResult)
  assert.equal(new Set(events.map((event) => event.globalSequence)).size, events.length)
  assert.deepEqual(
    events.map((event) => event.globalSequence).sort((left, right) => left - right),
    Array.from({ length: events.length }, (_unused, index) => index + 1),
  )
})

test("keeps dropped completeness isolated to the affected trace", async () => {
  const requests = []
  const microtasks = []
  const runtime = createObservabilityPlugin({
    env: {
      TEST_AGENT_OBSERVABILITY_BASE_URL: "http://127.0.0.1:18081",
      TEST_AGENT_OBSERVABILITY_TOKEN: "local-token",
      TEST_AGENT_OBSERVABILITY_GENERATION: "generation-1",
      TEST_AGENT_OBSERVABILITY_RUNTIME_KIND: "LOCAL_CLIENT",
      TEST_AGENT_OBSERVABILITY_QUEUE_BYTES: "1024",
      TEST_AGENT_OBSERVABILITY_BATCH_BYTES: "1048576",
    },
    fetch: async (_url, request) => {
      requests.push(JSON.parse(request.body))
      return { ok: true, status: 200 }
    },
    queueMicrotask: (callback) => microtasks.push(callback),
    setTimeout: (callback) => ({ callback, unref() {} }),
    clearTimeout: () => {},
  })

  runtime.hooks.event({ event: { type: "message.updated", properties: { sessionID: "ses-dropped" } } })
  runtime.hooks.event({ event: { type: "message.part.updated", properties: { sessionID: "ses-dropped" } } })
  microtasks.shift()()
  await runtime.flush()
  runtime.hooks.event({ event: { type: "message.updated", properties: { sessionID: "ses-clean" } } })
  microtasks.shift()()
  await runtime.flush()

  assert.equal(requests.length, 2)
  assert.equal(requests[0].droppedCount, 1)
  assert.equal(requests[0].complete, false)
  assert.equal(requests[1].droppedCount, 0)
  assert.equal(requests[1].complete, true)
  assert.notEqual(requests[0].events[0].traceId, requests[1].events[0].traceId)
})

test("inherits the root trace for child agent sessions", async () => {
  const requests = []
  const runtime = testRuntime(async (_url, request) => {
    requests.push(JSON.parse(request.body))
    return { ok: true, status: 200 }
  })

  runtime.hooks.event({
    event: {
      type: "session.created",
      properties: { sessionID: "ses-root", info: { id: "ses-root" } },
    },
  })
  runtime.hooks.event({
    event: {
      type: "session.created",
      properties: {
        sessionID: "ses-child",
        info: { id: "ses-child", parentID: "ses-root", agent: "test-design-generation" },
      },
    },
  })
  runtime.hooks["tool.execute.before"](
    { tool: "skill", sessionID: "ses-child", callID: "call-child" },
    { args: { name: "test-design-api" } },
  )
  while (runtime.inspect().serialized > 0) await runtime.flush()

  const events = requests.flatMap((batch) => batch.events)
  assert.equal(new Set(events.map((event) => event.traceId)).size, 1)
  assert.equal(events[1].parentId, "ses-root")
  assert.equal(events[2].sessionId, "ses-child")
  assert.equal(events[2].payload.skillName, "test-design-api")
})

test("does not treat a parent message ID as a parent session", async () => {
  const requests = []
  const runtime = testRuntime(async (_url, request) => {
    requests.push(JSON.parse(request.body))
    return { ok: true, status: 200 }
  })

  runtime.hooks.event({
    event: {
      type: "session.created",
      properties: { sessionID: "ses-message-parent", info: { id: "ses-message-parent" } },
    },
  })
  runtime.hooks.event({
    event: {
      type: "message.updated",
      properties: { sessionID: "ses-message-parent", id: "msg-child", parentID: "msg-parent" },
    },
  })
  runtime.hooks.event({
    event: {
      type: "session.idle",
      properties: { sessionID: "ses-message-parent" },
    },
  })
  while (runtime.inspect().serialized > 0) await runtime.flush()

  const events = requests.flatMap((batch) => batch.events)
  assert.equal(new Set(events.map((event) => event.traceId)).size, 1)
  assert.equal(events[1].parentId, "msg-parent")
})
