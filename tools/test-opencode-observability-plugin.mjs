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

test("counts a protected MCP skill resource read from args.name without parsing its path or title", async () => {
  const requests = []
  const runtime = testRuntime(async (_url, request) => {
    requests.push(JSON.parse(request.body))
    return { ok: true, status: 200 }
  }, [1000, 1010, 1020, 1040])

  runtime.hooks["tool.execute.before"](
    { tool: "local_files_read_skill_resource", sessionID: "ses-protected", callID: "call-protected" },
    { args: { name: "test-design", path: "untrusted-path-that-must-not-be-parsed" } },
  )
  runtime.hooks["tool.execute.after"](
    { tool: "local_files_read_skill_resource", sessionID: "ses-protected", callID: "call-protected" },
    { title: "untrusted title", output: "loaded" },
  )
  await runtime.flush()

  const facts = requests.flatMap((batch) => batch.events)
  assert.deepEqual(facts.map((event) => event.payload.skillName), ["test-design", "test-design"])
  assert.deepEqual(facts.map((event) => event.payload.capabilityKind), ["SKILL", "SKILL"])
  assert.deepEqual(facts.map((event) => event.callId), ["call-protected", "call-protected"])
})

test("uses the real OpenCode 1.18.4 messages transform output to bind context to its session", async () => {
  const requests = []
  const runtime = testRuntime(async (_url, request) => {
    requests.push(JSON.parse(request.body))
    return { ok: true, status: 200 }
  })

  runtime.hooks["experimental.chat.messages.transform"]({}, {
    messages: [{ info: { id: "msg-context", sessionID: "ses-context", role: "user" }, parts: [] }],
  })
  await runtime.flush()

  assert.equal(requests[0].events[0].sessionId, "ses-context")
  assert.notEqual(requests[0].events[0].traceId, "unknown")
})

test("turns a real OpenCode 1.18.4 tool error part into one failed capability fact", async () => {
  const requests = []
  const runtime = testRuntime(async (_url, request) => {
    requests.push(JSON.parse(request.body))
    return { ok: true, status: 200 }
  }, [1000, 1010, 1050, 1060])

  runtime.hooks["tool.execute.before"](
    { tool: "skill", sessionID: "ses-failed", callID: "call-failed" },
    { args: { name: "test-design", input: "broken" } },
  )
  runtime.hooks.event({
    event: {
      type: "message.part.updated",
      properties: {
        sessionID: "ses-failed",
        part: {
          id: "part-tool",
          sessionID: "ses-failed",
          messageID: "msg-failed",
          type: "tool",
          tool: "skill",
          callID: "call-failed",
          state: {
            status: "error",
            input: { name: "test-design" },
            error: "intentional failure",
            time: { start: 1000, end: 1050 },
          },
        },
      },
    },
  })
  while (runtime.inspect().serialized > 0) await runtime.flush()

  const events = requests.flatMap((batch) => batch.events)
  const failed = events.filter((event) => event.type === "TOOL_EXECUTE_AFTER")
  assert.equal(failed.length, 1)
  assert.equal(failed[0].callId, "call-failed")
  assert.equal(failed[0].messageId, "msg-failed")
  assert.equal(failed[0].payload.skillName, "test-design")
  assert.equal(failed[0].payload.status, "FAILED")
  assert.equal(failed[0].payload.durationMs, 50)
})

test("extracts message, call and step correlations from real OpenCode event parts", async () => {
  const requests = []
  const runtime = testRuntime(async (_url, request) => {
    requests.push(JSON.parse(request.body))
    return { ok: true, status: 200 }
  })

  runtime.hooks.event({
    event: {
      type: "message.part.updated",
      properties: {
        sessionID: "ses-part",
        part: { id: "step-1", type: "step-start", messageID: "msg-1", callID: "call-1" },
      },
    },
  })
  await runtime.flush()

  const event = requests[0].events[0]
  assert.equal(event.messageId, "msg-1")
  assert.equal(event.callId, "call-1")
  assert.equal(event.stepId, "step-1")
})

test("projects DSH-aligned turn step TTFT decode and cache-token metrics from OpenCode 1.18.4 events", async () => {
  const requests = []
  let clock = 990
  const runtime = createObservabilityPlugin({
    env: {
      TEST_AGENT_OBSERVABILITY_BASE_URL: "http://127.0.0.1:18081",
      TEST_AGENT_OBSERVABILITY_TOKEN: "local-token",
      TEST_AGENT_OBSERVABILITY_GENERATION: "generation-dsh",
      TEST_AGENT_OBSERVABILITY_RUNTIME_KIND: "SERVER_PROCESS",
      TEST_AGENT_OBSERVABILITY_BATCH_BYTES: "1048576",
    },
    fetch: async (_url, request) => {
      requests.push(JSON.parse(request.body))
      return { ok: true, status: 200 }
    },
    now: () => { clock += 10; return clock },
    queueMicrotask: (callback) => callback(),
    setTimeout: (callback) => ({ callback, unref() {} }),
    clearTimeout: () => {},
  })

  runtime.hooks["chat.message"](
    { sessionID: "ses-dsh", messageID: "msg-user", agent: "test-design" },
    { message: { id: "msg-user", sessionID: "ses-dsh", role: "user" }, parts: [] },
  )
  runtime.hooks.event({
    event: {
      type: "message.part.updated",
      properties: {
        sessionID: "ses-dsh",
        time: 1000,
        part: { id: "step-start-1", type: "step-start", sessionID: "ses-dsh", messageID: "msg-assistant" },
      },
    },
  })
  runtime.hooks.event({
    event: {
      type: "message.part.delta",
      properties: {
        sessionID: "ses-dsh",
        messageID: "msg-assistant",
        partID: "text-1",
        field: "text",
        delta: "首个非空 token",
      },
    },
  })
  runtime.hooks.event({
    event: {
      type: "message.part.updated",
      properties: {
        sessionID: "ses-dsh",
        time: 1120,
        part: {
          id: "step-finish-1",
          type: "step-finish",
          sessionID: "ses-dsh",
          messageID: "msg-assistant",
          reason: "stop",
          cost: 0.01,
          tokens: { input: 100, output: 20, reasoning: 7, cache: { read: 60, write: 5 } },
        },
      },
    },
  })
  runtime.hooks.event({
    event: {
      type: "message.updated",
      properties: {
        sessionID: "ses-dsh",
        info: {
          id: "msg-assistant",
          sessionID: "ses-dsh",
          role: "assistant",
          time: { created: 995, completed: 1140 },
          tokens: { input: 100, output: 20, reasoning: 7, cache: { read: 60, write: 5 } },
        },
      },
    },
  })
  runtime.hooks["tool.execute.before"](
    { tool: "skill", sessionID: "ses-dsh", callID: "call-dsh" },
    { args: { name: "test-design" } },
  )
  runtime.hooks["tool.execute.after"](
    { tool: "skill", sessionID: "ses-dsh", callID: "call-dsh" },
    { title: "done", output: "ok", metadata: {}, durationMs: 40 },
  )
  runtime.hooks.event({ event: { type: "session.idle", properties: { sessionID: "ses-dsh" } } })
  while (runtime.inspect().serialized > 0) await runtime.flush()

  const events = requests.flatMap((batch) => batch.events)
  const turnId = events.find((event) => event.type === "CHAT_MESSAGE").turnId
  const metric = events.find((event) => event.type === "ASSISTANT_STEP_METRICS")
  assert.equal(turnId, "turn:msg-user")
  assert.equal(metric.turnId, turnId)
  assert.equal(metric.stepId, "step-start-1")
  assert.equal(metric.messageId, "msg-assistant")
  assert.equal(metric.payload.durationMs, 140)
  assert.equal(metric.payload.ttftMs, 30)
  assert.equal(metric.payload.decodeMs, 110)
  assert.deepEqual(
    [
      metric.payload.tokensInput,
      metric.payload.tokensOutput,
      metric.payload.tokensReasoning,
      metric.payload.tokensCacheRead,
      metric.payload.tokensCacheWrite,
    ],
    [100, 20, 7, 60, 5],
  )
  assert.equal(metric.payload.tokensTotal, 127)
  assert.equal(metric.payload.decodeTokens, 20)
  assert.equal(metric.payload.cost, 0.01)
  assert.equal(metric.payload.finishReason, "stop")
  assert.deepEqual(events.find((event) => event.type === "SESSION_METRICS").payload, {
    turns: 1,
    steps: 1,
    llmMs: 140,
    toolMs: 40,
    ttftMs: 30,
    ttftSteps: 1,
    decodeMs: 110,
    decodeTokens: 20,
    cost: 0.01,
  })
})

test("counts an interrupted OpenCode 1.18.4 step without fabricating DSH wall time", async () => {
  const requests = []
  const runtime = testRuntime(async (_url, request) => {
    requests.push(JSON.parse(request.body))
    return { ok: true, status: 200 }
  }, [1000, 1010, 1020, 1030, 1040, 1050])

  runtime.hooks["chat.message"](
    { sessionID: "ses-interrupted", messageID: "msg-user" },
    { message: { id: "msg-user", sessionID: "ses-interrupted", role: "user" }, parts: [] },
  )
  runtime.hooks.event({ event: { type: "message.part.updated", properties: {
    sessionID: "ses-interrupted",
    time: 1010,
    part: { id: "step-interrupted", type: "step-start", sessionID: "ses-interrupted", messageID: "msg-ai" },
  } } })
  runtime.hooks.event({ event: { type: "message.updated", properties: {
    sessionID: "ses-interrupted",
    info: {
      id: "msg-ai",
      sessionID: "ses-interrupted",
      role: "assistant",
      time: { created: 1000, completed: 1050 },
      error: { name: "AbortError", message: "cancelled" },
    },
  } } })
  runtime.hooks.event({ event: { type: "session.idle", properties: { sessionID: "ses-interrupted" } } })
  while (runtime.inspect().serialized > 0) await runtime.flush()

  const events = requests.flatMap((batch) => batch.events)
  const step = events.find((event) => event.type === "ASSISTANT_STEP_METRICS")
  assert.equal(step.payload.status, "INTERRUPTED")
  assert.equal(step.payload.timingRecorded, false)
  assert.equal(step.payload.durationMs, null)
  assert.deepEqual(events.find((event) => event.type === "SESSION_METRICS").payload, {
    turns: 1,
    steps: 1,
    llmMs: 0,
    toolMs: 0,
    ttftMs: 0,
    ttftSteps: 0,
    decodeMs: 0,
    decodeTokens: 0,
    cost: 0,
  })
})

test("records cancelled tool calls once from the real OpenCode 1.18.4 error part", async () => {
  const requests = []
  const runtime = testRuntime(async (_url, request) => {
    requests.push(JSON.parse(request.body))
    return { ok: true, status: 200 }
  }, [1000, 1010, 1050, 1060])

  runtime.hooks["tool.execute.before"](
    { tool: "skill", sessionID: "ses-cancelled", callID: "call-cancelled" },
    { args: { name: "test-design" } },
  )
  runtime.hooks.event({ event: { type: "message.part.updated", properties: {
    sessionID: "ses-cancelled",
    time: 1050,
    part: {
      id: "part-cancelled",
      type: "tool",
      sessionID: "ses-cancelled",
      messageID: "msg-cancelled",
      callID: "call-cancelled",
      tool: "skill",
      state: {
        status: "error",
        input: { name: "test-design" },
        error: "Cancelled",
        time: { start: 1000, end: 1050 },
      },
    },
  } } })
  while (runtime.inspect().serialized > 0) await runtime.flush()

  const terminal = requests.flatMap((batch) => batch.events)
    .filter((event) => event.type === "TOOL_EXECUTE_AFTER")
  assert.equal(terminal.length, 1)
  assert.equal(terminal[0].callId, "call-cancelled")
  assert.equal(terminal[0].payload.skillName, "test-design")
  assert.equal(terminal[0].payload.status, "CANCELLED")
})

test("marks compacted context and captures the published 1.18.4 compaction hook", async () => {
  const requests = []
  const runtime = testRuntime(async (_url, request) => {
    requests.push(JSON.parse(request.body))
    return { ok: true, status: 200 }
  })

  runtime.hooks["experimental.chat.messages.transform"]({}, {
    messages: [{
      info: { id: "msg-summary", sessionID: "ses-compact", role: "assistant", mode: "compaction", summary: true },
      parts: [{ id: "part-compact", type: "compaction", sessionID: "ses-compact", messageID: "msg-summary", auto: true }],
    }],
  })
  runtime.hooks["experimental.session.compacting"](
    { sessionID: "ses-compact" },
    { context: ["保留的压缩上下文"], prompt: "压缩提示" },
  )
  while (runtime.inspect().serialized > 0) await runtime.flush()

  const events = requests.flatMap((batch) => batch.events)
  assert.equal(events.find((event) => event.type === "CONTEXT_MESSAGES").payload.contextKind, "COMPACTED")
  const compaction = events.find((event) => event.type === "COMPACTION_CONTEXT")
  assert.equal(compaction.sessionId, "ses-compact")
  assert.deepEqual(compaction.payload.context, ["保留的压缩上下文"])
  assert.equal(compaction.payload.prompt, "压缩提示")
})

test("redacts credentials and replaces binary bodies with metadata before transport", () => {
  const binary = new Uint8Array([1, 2, 3])
  const result = sanitizeObservabilityValue({
    Authorization: "Bearer abc.def.ghi",
    text: "SERVICE_API_KEY=very-secret Bearer raw-token AKIAABCDEFGHIJKLMNOP",
    nested: { cookie: "session=secret" },
    binary,
    attachment: "data:application/octet-stream;base64,AQID",
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
  assert.deepEqual(result.attachment, {
    binary: true,
    contentType: "application/octet-stream",
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
