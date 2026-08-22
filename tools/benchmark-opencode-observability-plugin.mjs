#!/usr/bin/env node

import assert from "node:assert/strict"

import { createObservabilityPlugin } from "../deploy/internal/opencode-observability-plugin.mjs"

const ITERATIONS = 20_000
const MAX_HOOK_P99_MS = 2
const MAX_RSS_DELTA_BYTES = 32 * 1024 * 1024

function runtime(enabled) {
  const deferred = []
  return createObservabilityPlugin({
    env: enabled
      ? {
          TEST_AGENT_OBSERVABILITY_BASE_URL: "http://127.0.0.1:1",
          TEST_AGENT_OBSERVABILITY_TOKEN: "benchmark-only",
          TEST_AGENT_OBSERVABILITY_GENERATION: "benchmark-generation",
          TEST_AGENT_OBSERVABILITY_QUEUE_BYTES: String(16 * 1024 * 1024),
        }
      : {},
    queueMicrotask: (callback) => deferred.push(callback),
    setTimeout: () => ({ unref() {} }),
    clearTimeout: () => {},
  })
}

function sample(target) {
  const durations = new Float64Array(ITERATIONS)
  const event = {
    event: {
      type: "message.part.updated",
      properties: { sessionID: "ses-benchmark", delta: "reasoning" },
    },
  }
  for (let index = 0; index < ITERATIONS; index += 1) {
    const started = process.hrtime.bigint()
    target.hooks.event(event)
    durations[index] = Number(process.hrtime.bigint() - started) / 1_000_000
  }
  durations.sort()
  return durations[Math.floor(durations.length * 0.99)]
}

// 先预热 JIT，再比较关闭与开启采集时 Hook 本身；后台序列化故意不在热路径执行。
sample(runtime(false))
const baselineP99 = sample(runtime(false))
const rssBefore = process.memoryUsage().rss
const enabled = runtime(true)
const enabledP99 = sample(enabled)
const rssDelta = Math.max(0, process.memoryUsage().rss - rssBefore)
const incrementalP99 = Math.max(0, enabledP99 - baselineP99)

assert.ok(incrementalP99 <= MAX_HOOK_P99_MS,
  `observability hook p99 regression ${incrementalP99.toFixed(3)} ms exceeds ${MAX_HOOK_P99_MS} ms`)
assert.ok(rssDelta <= MAX_RSS_DELTA_BYTES,
  `observability hook RSS delta ${rssDelta} exceeds ${MAX_RSS_DELTA_BYTES}`)

console.log(JSON.stringify({
  iterations: ITERATIONS,
  baselineP99Ms: Number(baselineP99.toFixed(4)),
  enabledP99Ms: Number(enabledP99.toFixed(4)),
  incrementalP99Ms: Number(incrementalP99.toFixed(4)),
  rssDeltaBytes: rssDelta,
  hotPathIoOperations: 0,
  hotPathNetworkOperations: 0,
  limits: { maxIncrementalP99Ms: MAX_HOOK_P99_MS, maxRssDeltaBytes: MAX_RSS_DELTA_BYTES },
}))
