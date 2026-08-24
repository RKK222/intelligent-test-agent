import { createHash, randomUUID } from "node:crypto"

const SCHEMA_VERSION = "1.0"
const DEFAULT_QUEUE_BYTES = 16 * 1024 * 1024
const DEFAULT_BATCH_BYTES = 96 * 1024
const DEFAULT_BATCH_EVENTS = 64
const DEFAULT_FLUSH_INTERVAL_MS = 250
const MAX_TRANSPORT_EVENT_BYTES = 192 * 1024
const EVENTS_PATH = "/api/internal/agent/opencode-observability/v1/events"
const REDACTED = "[REDACTED]"
const SENSITIVE_KEY = /(?:authorization|cookie|set-cookie|password|passwd|private[_-]?key|access[_-]?token|refresh[_-]?token|api[_-]?key|client[_-]?secret|secret|credential)/iu
const SECRET_ASSIGNMENT = /\b([A-Z][A-Z0-9_]*(?:TOKEN|SECRET|PASSWORD|PRIVATE_KEY|API_KEY|ACCESS_KEY)[A-Z0-9_]*)\s*=\s*([^\s,;]+)/gu
const BEARER_TOKEN = /\bBearer\s+[A-Za-z0-9._~+/=-]+/giu
const JWT_TOKEN = /\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/gu
const PRIVATE_KEY = /-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----/gu
const CLOUD_ACCESS_KEY = /\b(?:AKIA|ASIA)[A-Z0-9]{16}\b/gu
const BASE64_DATA_URI = /^data:([^;,]+);base64,([A-Za-z0-9+/=\r\n]+)$/u

function positiveInteger(value, fallback) {
  const parsed = Number.parseInt(value ?? "", 10)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : fallback
}

function sha256(value) {
  return createHash("sha256").update(String(value)).digest("hex")
}

function traceId(runtimeGeneration, sessionID) {
  return `trc_${sha256(`${runtimeGeneration}\u0000${sessionID}`).slice(0, 32)}`
}

function redactString(value) {
  return value
    .replace(PRIVATE_KEY, REDACTED)
    .replace(BEARER_TOKEN, `Bearer ${REDACTED}`)
    .replace(JWT_TOKEN, REDACTED)
    .replace(CLOUD_ACCESS_KEY, REDACTED)
    .replace(SECRET_ASSIGNMENT, (_match, key) => `${key}=${REDACTED}`)
}

/**
 * 脱敏在后台微任务执行，既不阻塞 Hook，也确保任何正文进入本地 spool 或网络前已清除常见凭据。
 * 二进制只保留类型、长度和摘要，禁止复制正文。
 */
export function sanitizeObservabilityValue(value, seen = new WeakSet(), depth = 0) {
  if (value === null || value === undefined || typeof value === "boolean" || typeof value === "number") return value
  if (typeof value === "bigint") return value.toString()
  if (typeof value === "string") {
    const dataUri = BASE64_DATA_URI.exec(value)
    if (dataUri) {
      const bytes = Buffer.from(dataUri[2], "base64")
      return {
        binary: true,
        contentType: dataUri[1],
        size: bytes.byteLength,
        sha256: createHash("sha256").update(bytes).digest("hex"),
      }
    }
    return redactString(value)
  }
  if (typeof value === "function" || typeof value === "symbol") return String(value)
  if (depth >= 24) return "[MAX_DEPTH]"
  if (value instanceof Uint8Array || value instanceof ArrayBuffer) {
    const bytes = value instanceof Uint8Array ? value : new Uint8Array(value)
    return {
      binary: true,
      contentType: value.constructor?.name ?? "binary",
      size: bytes.byteLength,
      sha256: createHash("sha256").update(bytes).digest("hex"),
    }
  }
  if (value instanceof Date) return value.toISOString()
  if (typeof value !== "object") return String(value)
  if (seen.has(value)) return "[CIRCULAR]"
  seen.add(value)
  try {
    if (Array.isArray(value)) {
      return value.map((entry) => sanitizeObservabilityValue(entry, seen, depth + 1))
    }
    const sanitized = {}
    for (const [key, entry] of Object.entries(value)) {
      sanitized[key] = SENSITIVE_KEY.test(key)
        ? REDACTED
        : sanitizeObservabilityValue(entry, seen, depth + 1)
    }
    return sanitized
  } finally {
    seen.delete(value)
  }
}

function observabilityEndpoint(baseUrl) {
  if (!baseUrl) return null
  const normalized = baseUrl.replace(/\/+$/u, "")
  return normalized.endsWith(EVENTS_PATH) ? normalized : `${normalized}${EVENTS_PATH}`
}

function sessionIdOf(input, output) {
  const transformedMessages = output?.messages ?? (Array.isArray(output) ? output : null)
  return input?.sessionID
    ?? input?.sessionId
    ?? input?.message?.sessionID
    ?? input?.event?.properties?.sessionID
    ?? input?.event?.properties?.sessionId
    ?? input?.event?.properties?.info?.id
    // OpenCode 1.18.4 的 messages.transform 输入为空，Session 只能从输出消息读取。
    ?? transformedMessages?.[0]?.info?.sessionID
    ?? transformedMessages?.[0]?.info?.sessionId
    ?? transformedMessages?.[0]?.sessionID
    ?? output?.sessionID
    ?? "unknown"
}

function correlationOf(input, output) {
  const source = input?.event?.properties ?? input ?? {}
  // OpenCode 1.18.4 的 session.created 把父 Session 放在 properties.info.parentID，
  // 而 sessionID 仍位于 properties 顶层；两种结构都要兼容，才能让整棵 Agent 树共用 Trace。
  const sessionInfo = source.info ?? source.session ?? {}
  const part = source.part ?? {}
  const eventType = input?.event?.type ?? ""
  const partIsStep = part.type === "step-start" || part.type === "step-finish"
  return {
    runId: source.runID ?? source.runId ?? output?.runID ?? output?.runId ?? null,
    turnId: source.turnID ?? source.turnId ?? output?.turnID ?? output?.turnId ?? null,
    stepId: source.stepID ?? source.stepId ?? (partIsStep ? part.id : null) ?? output?.stepID ?? output?.stepId ?? null,
    messageId: source.messageID
      ?? source.messageId
      ?? source.message?.id
      ?? part.messageID
      ?? (eventType.startsWith("message.") ? sessionInfo.id : null)
      ?? output?.messageID
      ?? output?.messageId
      ?? null,
    callId: source.callID ?? source.callId ?? part.callID ?? part.callId ?? output?.callID ?? output?.callId ?? null,
    parentId: source.parentID
      ?? source.parentId
      ?? source.parentSessionID
      ?? source.parentSessionId
      ?? sessionInfo.parentID
      ?? sessionInfo.parentId
      ?? null,
  }
}

function parentSessionIdOf(input) {
  const event = input?.event
  if (event?.type !== "session.created" && event?.type !== "session.updated") return null
  const properties = event.properties ?? {}
  const sessionInfo = properties.info ?? properties.session ?? {}
  return properties.parentSessionID
    ?? properties.parentSessionId
    ?? sessionInfo.parentID
    ?? sessionInfo.parentId
    // 旧 OpenCode 事件把父 Session 放在 properties 顶层，仅在 Session 生命周期事件中兼容。
    ?? properties.parentID
    ?? properties.parentId
    ?? null
}

function runtimeIdentity(env) {
  const generation = env.TEST_AGENT_OBSERVABILITY_GENERATION?.trim()
    || `${process.pid}-${Date.now()}-${randomUUID()}`
  return {
    kind: env.TEST_AGENT_OBSERVABILITY_RUNTIME_KIND?.trim() || "SERVER_PROCESS",
    generation,
    processId: env.TEST_AGENT_OBSERVABILITY_PROCESS_ID?.trim() || null,
    serverId: env.TEST_AGENT_OBSERVABILITY_SERVER_ID?.trim() || null,
    clientInstanceId: env.TEST_AGENT_OBSERVABILITY_CLIENT_INSTANCE_ID?.trim() || null,
  }
}

/** 大正文拆分后仍在主事件保留可索引字段；完整脱敏 payload 由后续 PAYLOAD_FRAGMENT 无损承载。 */
function fragmentedPayloadSummary(payload, fragmentGroupId, fragmentCount, originalBytes) {
  const summary = {
    fragmentedPayload: {
      fragmentGroupId,
      fragmentCount,
      encoding: "base64-json-utf8",
      originalBytes,
    },
  }
  for (const key of [
    "tool", "skillName", "agentName", "capabilityKind", "recordKind", "status", "durationMs", "title",
    "startedAt", "ttftMs", "decodeMs", "tokensInput", "tokensOutput", "tokensReasoning",
    "tokensCacheRead", "tokensCacheWrite", "tokensTotal", "decodeTokens", "cost", "usage",
  ]) {
    if (payload?.[key] !== undefined) summary[key] = payload[key]
  }
  if (payload?.metadata?.name !== undefined) summary.metadata = { name: payload.metadata.name }
  return summary
}

/** 创建可注入 OpenCode 1.18.4 的同一份插件实现；测试可传入 fetch/clock 调度器而不访问网络。 */
export function createObservabilityPlugin(options = {}) {
  const env = options.env ?? process.env
  const endpoint = observabilityEndpoint(env.TEST_AGENT_OBSERVABILITY_BASE_URL)
  const token = env.TEST_AGENT_OBSERVABILITY_TOKEN?.trim() || ""
  const runtime = runtimeIdentity(env)
  const coverageStartAt = new Date(options.now?.() ?? Date.now()).toISOString()
  const fetchImpl = options.fetch ?? globalThis.fetch
  const queueMicrotaskImpl = options.queueMicrotask ?? globalThis.queueMicrotask
  const setTimeoutImpl = options.setTimeout ?? globalThis.setTimeout
  const clearTimeoutImpl = options.clearTimeout ?? globalThis.clearTimeout
  const maxQueueBytes = positiveInteger(env.TEST_AGENT_OBSERVABILITY_QUEUE_BYTES, DEFAULT_QUEUE_BYTES)
  const maxBatchBytes = positiveInteger(env.TEST_AGENT_OBSERVABILITY_BATCH_BYTES, DEFAULT_BATCH_BYTES)
  const maxBatchEvents = positiveInteger(env.TEST_AGENT_OBSERVABILITY_BATCH_EVENTS, DEFAULT_BATCH_EVENTS)
  const flushIntervalMs = positiveInteger(env.TEST_AGENT_OBSERVABILITY_FLUSH_INTERVAL_MS, DEFAULT_FLUSH_INTERVAL_MS)

  const pendingRaw = []
  const serialized = []
  const sessionSequences = new Map()
  const traceIdsBySession = new Map()
  const traceDroppedCounts = new Map()
  const toolCalls = new Map()
  const parentSessionBySession = new Map()
  const turnSequenceBySession = new Map()
  const currentTurnBySession = new Map()
  const currentStepByMessage = new Map()
  const currentStepBySession = new Map()
  const sessionStats = new Map()
  let queuedBytes = 0
  let globalSequence = 0
  let droppedCount = 0
  let drainScheduled = false
  let flushTimer = null
  let sending = false
  let retryAttempt = 0
  let disposed = false

  function traceIdForSession(sessionId) {
    const existing = traceIdsBySession.get(sessionId)
    if (existing) return existing
    const created = traceId(runtime.generation, sessionId)
    traceIdsBySession.set(sessionId, created)
    return created
  }

  function bindParentTrace(sessionId, parentId) {
    if (!parentId || parentId === sessionId) return
    // session.created 是子 Agent 的首个生命周期事件；从这一刻起整棵调用树共用根 Trace。
    traceIdsBySession.set(sessionId, traceIdForSession(parentId))
    parentSessionBySession.set(sessionId, parentId)
    const parentTurn = currentTurnBySession.get(parentId)
    if (parentTurn) currentTurnBySession.set(sessionId, parentTurn)
  }

  function recordDrop(sessionId) {
    droppedCount += 1
    const affectedTraceId = traceIdForSession(sessionId)
    traceDroppedCounts.set(affectedTraceId, (traceDroppedCounts.get(affectedTraceId) ?? 0) + 1)
  }

  function statsForSession(sessionId) {
    let stats = sessionStats.get(sessionId)
    if (!stats) {
      stats = {
        lastTurnId: null,
        turns: 0,
        steps: 0,
        llmMs: 0,
        toolMs: 0,
        ttftMs: 0,
        ttftSteps: 0,
        decodeMs: 0,
        decodeTokens: 0,
        cost: 0,
      }
      sessionStats.set(sessionId, stats)
    }
    return stats
  }

  function sessionMetricsPayload(sessionId) {
    const stats = statsForSession(sessionId)
    return {
      // 字段名与 DeepSeek Harness session-stats 投影保持一致，便于后续直接对标演进。
      turns: stats.turns,
      steps: stats.steps,
      llmMs: stats.llmMs,
      toolMs: stats.toolMs,
      ttftMs: stats.ttftMs,
      ttftSteps: stats.ttftSteps,
      decodeMs: stats.decodeMs,
      decodeTokens: stats.decodeTokens,
      cost: stats.cost,
    }
  }

  function envelope(raw, type, sequence, sessionSequence) {
    return {
      schemaVersion: SCHEMA_VERSION,
      eventId: `evt_${sha256(`${runtime.generation}\u0000${sequence}\u0000${type}`).slice(0, 40)}`,
      traceId: traceIdForSession(raw.sessionId),
      type,
      timestamp: raw.timestamp,
      globalSequence: sequence,
      sessionSequence,
      sessionId: raw.sessionId,
      ...raw.correlation,
    }
  }

  function scheduleFlush(delayMs = flushIntervalMs) {
    if (!endpoint || !token || disposed || sending || flushTimer !== null || serialized.length === 0) return
    flushTimer = setTimeoutImpl(() => {
      flushTimer = null
      void flush()
    }, delayMs)
    flushTimer?.unref?.()
  }

  function serializePending() {
    drainScheduled = false
    while (pendingRaw.length > 0) {
      const raw = pendingRaw.shift()
      queuedBytes = Math.max(0, queuedBytes - raw.estimate)
      let payload
      try {
        payload = sanitizeObservabilityValue(raw.payload)
      } catch {
        recordDrop(raw.sessionId)
        continue
      }

      const firstSequence = globalSequence + 1
      const firstSessionSequence = (sessionSequences.get(raw.sessionId) ?? 0) + 1
      const primaryEnvelope = envelope(raw, raw.type, firstSequence, firstSessionSequence)
      let entries
      try {
        const text = JSON.stringify({ ...primaryEnvelope, payload })
        const bytes = Buffer.byteLength(text)
        const eventBudget = Math.max(4 * 1024, Math.min(maxBatchBytes, MAX_TRANSPORT_EVENT_BYTES))
        if (bytes <= eventBudget) {
          entries = [{ text, bytes, traceId: primaryEnvelope.traceId }]
        } else {
          const payloadBytes = Buffer.from(JSON.stringify(payload), "utf8")
          const fragmentBytes = Math.max(1024, Math.min(64 * 1024, Math.floor(eventBudget * 0.55)))
          const fragmentCount = Math.ceil(payloadBytes.length / fragmentBytes)
          const fragmentGroupId = `frg_${sha256(`${primaryEnvelope.eventId}\u0000${payloadBytes.length}`).slice(0, 32)}`
          const primaryText = JSON.stringify({
            ...primaryEnvelope,
            payload: fragmentedPayloadSummary(payload, fragmentGroupId, fragmentCount, payloadBytes.length),
          })
          entries = [{
            text: primaryText,
            bytes: Buffer.byteLength(primaryText),
            traceId: primaryEnvelope.traceId,
          }]
          for (let index = 0; index < fragmentCount; index += 1) {
            const sequence = firstSequence + index + 1
            const sessionSequence = firstSessionSequence + index + 1
            const fragmentText = JSON.stringify({
              ...envelope(raw, "PAYLOAD_FRAGMENT", sequence, sessionSequence),
              payload: {
                fragmentGroupId,
                fragmentIndex: index,
                fragmentCount,
                encoding: "base64-json-utf8",
                originalType: raw.type,
                originalLane: raw.type.startsWith("TOOL_") ? "TOOLS" : "MODEL",
                dataBase64: payloadBytes.subarray(
                  index * fragmentBytes,
                  Math.min(payloadBytes.length, (index + 1) * fragmentBytes),
                ).toString("base64"),
              },
            })
            entries.push({
              text: fragmentText,
              bytes: Buffer.byteLength(fragmentText),
              traceId: primaryEnvelope.traceId,
            })
          }
        }
      } catch {
        recordDrop(raw.sessionId)
        continue
      }
      const totalBytes = entries.reduce((total, entry) => total + entry.bytes, 0)
      if (totalBytes > maxQueueBytes || queuedBytes + totalBytes > maxQueueBytes) {
        recordDrop(raw.sessionId)
        continue
      }
      globalSequence += entries.length
      sessionSequences.set(raw.sessionId, firstSessionSequence + entries.length - 1)
      serialized.push(...entries)
      queuedBytes += totalBytes
    }
    scheduleFlush()
  }

  function enqueue(type, input, output, payload, overrides = {}) {
    if (!endpoint || !token || disposed) return
    const sessionId = overrides.sessionId ?? sessionIdOf(input, output)
    // OpenCode 的 event hook 同时广播 server/plugin 等进程级事件；它们没有 Session，
    // 不属于任何 Agent 轨迹。禁止把这类事件聚合到 generation + "unknown" 的伪 Trace。
    if (!sessionId || sessionId === "unknown") return
    const baseCorrelation = correlationOf(input, output)
    const correlation = {
      ...baseCorrelation,
      turnId: baseCorrelation.turnId ?? currentTurnBySession.get(sessionId) ?? null,
      stepId: baseCorrelation.stepId ?? currentStepBySession.get(sessionId)?.stepId ?? null,
      ...overrides.correlation,
    }
    bindParentTrace(sessionId, parentSessionIdOf(input))
    const timestamp = new Date(options.now?.() ?? Date.now()).toISOString()
    const estimate = 1024
    if (queuedBytes + estimate > maxQueueBytes) {
      recordDrop(sessionId)
      return
    }
    pendingRaw.push({ type, sessionId, timestamp, correlation, payload, estimate })
    queuedBytes += estimate
    if (!drainScheduled) {
      drainScheduled = true
      queueMicrotaskImpl(serializePending)
    }
  }

  function takeBatch() {
    const entries = []
    let bytes = 0
    const firstTraceId = serialized[0]?.traceId
    while (serialized.length > 0 && entries.length < maxBatchEvents) {
      const next = serialized[0]
      if (next.traceId !== firstTraceId) break
      if (entries.length > 0 && bytes + next.bytes > maxBatchBytes) break
      serialized.shift()
      entries.push(next)
      bytes += next.bytes
    }
    return {
      entries,
      bytes,
      traceId: firstTraceId,
      droppedCount: traceDroppedCounts.get(firstTraceId) ?? 0,
    }
  }

  async function flush() {
    if (sending || !endpoint || !token || serialized.length === 0) return
    sending = true
    const batch = takeBatch()
    const events = batch.entries.map((entry) => JSON.parse(entry.text))
    try {
      const response = await fetchImpl(endpoint, {
        method: "POST",
        headers: {
          authorization: `Bearer ${token}`,
          "content-type": "application/json",
          "x-test-agent-observability-generation": runtime.generation,
        },
        body: JSON.stringify({
          schemaVersion: SCHEMA_VERSION,
          runtime,
          coverageStartAt,
          droppedCount: batch.droppedCount,
          complete: batch.droppedCount === 0,
          events,
        }),
      })
      if (!response.ok) throw new Error(`observability endpoint returned ${response.status}`)
      for (const entry of batch.entries) queuedBytes = Math.max(0, queuedBytes - entry.bytes)
      retryAttempt = 0
    } catch {
      serialized.unshift(...batch.entries)
      retryAttempt += 1
    } finally {
      sending = false
      if (serialized.length > 0 && !disposed) {
        const retryDelay = retryAttempt === 0
          ? 0
          : Math.min(30_000, 500 * (2 ** Math.min(retryAttempt - 1, 6)))
        scheduleFlush(retryDelay)
      }
    }
  }

  function toolBefore(input, output) {
    const callId = input?.callID ?? input?.callId
    const args = output?.args ?? {}
    const identity = {
      callId,
      tool: input?.tool ?? "unknown",
      args,
      sessionId: input?.sessionID ?? input?.sessionId ?? "unknown",
      startedAtMs: options.now?.() ?? Date.now(),
    }
    if (callId) toolCalls.set(callId, identity)
    const skillName = isSkillInvocation(identity.tool) ? args?.name ?? null : null
    const agentName = identity.tool === "task"
      ? args?.subagent_type ?? args?.agent ?? args?.name ?? "child-agent"
      : null
    enqueue("TOOL_EXECUTE_BEFORE", input, output, {
      tool: identity.tool,
      args,
      skillName,
      agentName,
      recordKind: parentSessionBySession.has(input?.sessionID) ? "subtool" : "tool",
      capabilityKind: skillName ? "SKILL" : agentName ? "AGENT" : "TOOL",
    }, { correlation: { callId: callId ?? null } })
  }

  function toolAfter(input, output) {
    const callId = input?.callID ?? input?.callId
    const before = callId ? toolCalls.get(callId) : null
    if (callId) toolCalls.delete(callId)
    const tool = before?.tool ?? input?.tool ?? "unknown"
    const args = before?.args ?? input?.args ?? {}
    const skillName = isSkillInvocation(tool) ? args?.name ?? output?.metadata?.name ?? null : null
    const agentName = tool === "task"
      ? args?.subagent_type ?? args?.agent ?? args?.name ?? output?.metadata?.agent ?? "child-agent"
      : null
    const durationMs = Number.isFinite(output?.durationMs)
      ? Math.max(0, output.durationMs)
      : before ? Math.max(0, (options.now?.() ?? Date.now()) - before.startedAtMs) : null
    const sessionId = before?.sessionId ?? input?.sessionID ?? input?.sessionId ?? "unknown"
    if (Number.isFinite(durationMs)) statsForSession(sessionId).toolMs += durationMs
    enqueue("TOOL_EXECUTE_AFTER", input, output, {
      tool,
      args,
      skillName,
      agentName,
      recordKind: parentSessionBySession.has(input?.sessionID) ? "subtool" : "tool",
      capabilityKind: skillName ? "SKILL" : agentName ? "AGENT" : "TOOL",
      // 1.18.4 的 task 失败会以 undefined 触发 after；普通工具失败则只发 tool part error 事件。
      status: toolExecutionStatus(output),
      title: output?.title ?? null,
      result: output?.output ?? null,
      metadata: output?.metadata ?? null,
      durationMs,
    }, { sessionId, correlation: { callId: callId ?? null } })
  }

  function toolExecutionStatus(output) {
    if (output != null && !output?.error) return "SUCCEEDED"
    const error = typeof output?.error === "string"
      ? output.error
      : output?.error?.message ?? ""
    return /cancelled|canceled|abort/iu.test(error) ? "CANCELLED" : "FAILED"
  }

  /** 受保护本地工作区通过固定 MCP 工具读取冻结 Skill；名称仍只接受 args/metadata.name。 */
  function isSkillInvocation(tool) {
    return tool === "skill" || tool === "local_files_read_skill_resource"
  }

  function finalizeStep(input, sessionId, step, status, completedAtMs, tokens = {}) {
    const timingRecorded = Number.isFinite(completedAtMs)
    const completed = timingRecorded ? Math.max(step.startedAtMs, completedAtMs) : null
    const firstTokenAtMs = timingRecorded ? step.firstTokenAtMs : null
    const durationMs = completed === null ? null : completed - step.startedAtMs
    const ttftMs = firstTokenAtMs === null ? null : Math.max(0, firstTokenAtMs - step.startedAtMs)
    const decodeMs = firstTokenAtMs === null ? null : Math.max(0, completed - firstTokenAtMs)
    const stats = statsForSession(sessionId)
    if (step.turnId && step.turnId !== stats.lastTurnId) {
      stats.lastTurnId = step.turnId
      stats.turns += 1
    }
    stats.steps += 1
    if (durationMs !== null) stats.llmMs += durationMs
    if (ttftMs !== null) {
      stats.ttftMs += ttftMs
      stats.ttftSteps += 1
    }
    if (decodeMs !== null && Number.isFinite(tokens.output)) {
      stats.decodeMs += decodeMs
      stats.decodeTokens += Math.max(0, tokens.output)
    }
    if (Number.isFinite(step.cost)) stats.cost += Math.max(0, step.cost)
    enqueue("ASSISTANT_STEP_METRICS", input, null, {
      recordKind: "message",
      status,
      timingRecorded,
      usageProvided: Number.isFinite(tokens.output),
      startedAt: new Date(step.startedAtMs).toISOString(),
      durationMs,
      ttftMs,
      decodeMs,
      tokensInput: tokens.input ?? 0,
      tokensOutput: tokens.output ?? 0,
      tokensReasoning: tokens.reasoning ?? 0,
      tokensCacheRead: tokens.cache?.read ?? 0,
      tokensCacheWrite: tokens.cache?.write ?? 0,
      tokensTotal: tokens.total ?? ((tokens.input ?? 0) + (tokens.output ?? 0) + (tokens.reasoning ?? 0)),
      decodeTokens: tokens.output ?? 0,
      cost: Number.isFinite(step.cost) ? Math.max(0, step.cost) : 0,
      finishReason: step.finishReason ?? null,
    }, {
      sessionId,
      correlation: {
        turnId: step.turnId,
        stepId: step.stepId,
        messageId: step.messageId,
      },
    })
    currentStepByMessage.delete(step.messageId)
    if (currentStepBySession.get(sessionId)?.stepId === step.stepId) currentStepBySession.delete(sessionId)
  }

  function onEvent(input) {
    const event = input?.event
    const properties = event?.properties ?? {}
    const part = properties.part
    const sessionId = properties.sessionID ?? part?.sessionID
    const metricBoundary = event?.type === "message.part.delta"
      || (event?.type === "message.part.updated"
        && (part?.type === "step-start" || part?.type === "step-finish"))
    const observedAtMs = metricBoundary
      ? Number.isFinite(properties.time) ? properties.time : options.now?.() ?? Date.now()
      : null

    if (event?.type === "session.created" || event?.type === "session.updated") {
      bindParentTrace(sessionId, parentSessionIdOf(input))
    }
    if (event?.type === "message.part.updated" && part?.type === "step-start" && sessionId) {
      const step = {
        stepId: part.id,
        messageId: part.messageID,
        sessionId,
        startedAtMs: observedAtMs,
        firstTokenAtMs: null,
        turnId: currentTurnBySession.get(sessionId) ?? null,
      }
      currentStepByMessage.set(part.messageID, step)
      currentStepBySession.set(sessionId, step)
    }
    if (event?.type === "message.part.delta" && typeof properties.delta === "string"
        && properties.delta.length > 0) {
      const step = currentStepByMessage.get(properties.messageID)
      if (step && step.firstTokenAtMs === null) step.firstTokenAtMs = observedAtMs
    }
    if (event?.type === "message.part.updated" && part?.type === "tool" && part?.state?.status === "error") {
      const callId = part.callID ?? part.callId
      const before = callId ? toolCalls.get(callId) : null
      // 普通工具异常不会调用 tool.execute.after；用权威 part error 补齐一次失败事实。
      if (before) {
        toolAfter({
          tool: before.tool,
          sessionID: event.properties.sessionID ?? part.sessionID,
          callID: callId,
          messageID: part.messageID,
          args: before.args,
        }, {
          error: part.state.error ?? "tool execution failed",
          metadata: part.state.metadata ?? null,
          output: null,
          title: null,
          durationMs: Number.isFinite(part.state.time?.start) && Number.isFinite(part.state.time?.end)
            ? Math.max(0, part.state.time.end - part.state.time.start)
            : null,
        })
      }
    }
    enqueue("OPENCODE_EVENT", input, null, { event: event ?? input })
    if (event?.type === "message.part.updated" && part?.type === "step-finish" && sessionId) {
      const step = currentStepByMessage.get(part.messageID)
        ?? currentStepBySession.get(sessionId)
        ?? {
          stepId: part.id,
          messageId: part.messageID,
          sessionId,
          startedAtMs: observedAtMs,
          firstTokenAtMs: null,
          turnId: currentTurnBySession.get(sessionId) ?? null,
        }
      // 1.18.4 的 step-finish 先于已组装 assistant message；只暂存 usage，不能提前结束 DSH llmMs。
      step.tokens = part.tokens ?? {}
      step.cost = part.cost
      step.finishReason = part.reason
      currentStepByMessage.set(step.messageId, step)
      currentStepBySession.set(sessionId, step)
    }
    if (event?.type === "message.updated" && properties.info?.role === "assistant" && sessionId
        && Number.isFinite(properties.info.time?.completed)) {
      const step = currentStepByMessage.get(properties.info.id)
      if (step) {
        const interrupted = Boolean(properties.info.error) || !step.tokens
        finalizeStep(
          input,
          sessionId,
          step,
          interrupted ? "INTERRUPTED" : "COMPLETED",
          interrupted ? null : properties.info.time.completed,
          interrupted ? {} : (step.tokens ?? properties.info.tokens ?? {}),
        )
      }
    }
    if ((event?.type === "session.error" || event?.type === "session.idle" || event?.type === "session.deleted")
        && sessionId) {
      const unfinished = currentStepBySession.get(sessionId)
      if (unfinished) finalizeStep(input, sessionId, unfinished, "INTERRUPTED", null, {})
    }
    if ((event?.type === "session.idle" || event?.type === "session.deleted") && sessionId) {
      enqueue("SESSION_METRICS", input, null, sessionMetricsPayload(sessionId), { sessionId })
    }
    if (event?.type === "session.deleted" && sessionId) {
      // 删除后不再需要累积投影；延后清理，确保本轮已入队事件先取得连续 Session 序号和根 Trace。
      queueMicrotaskImpl(() => {
        sessionStats.delete(sessionId)
        currentTurnBySession.delete(sessionId)
        turnSequenceBySession.delete(sessionId)
        currentStepBySession.delete(sessionId)
        parentSessionBySession.delete(sessionId)
      })
    }
  }

  function chatMessage(input, output) {
    const sessionId = sessionIdOf(input, output)
    const turn = (turnSequenceBySession.get(sessionId) ?? 0) + 1
    turnSequenceBySession.set(sessionId, turn)
    const messageId = input?.messageID ?? output?.message?.id ?? output?.id ?? null
    const turnId = messageId ? `turn:${messageId}` : `turn:${turn}`
    currentTurnBySession.set(sessionId, turnId)
    enqueue("CHAT_MESSAGE", input, output, {
      recordKind: "user",
      opensTurn: true,
      turn,
      // OpenCode 1.18.4 chat.message 的公开 input.agent 是本轮 Agent 权威名称。
      agentName: input?.agent ?? null,
      input,
      message: output?.message ?? output,
    }, { correlation: { turnId, messageId } })
  }

  async function dispose() {
    disposed = true
    if (flushTimer !== null) {
      clearTimeoutImpl(flushTimer)
      flushTimer = null
    }
    if (drainScheduled) serializePending()
    const deadline = Date.now() + 1_000
    while (!sending && serialized.length > 0 && Date.now() < deadline) await flush()
  }

  const hooks = {
    "tool.execute.before": toolBefore,
    "tool.execute.after": toolAfter,
    "chat.message": chatMessage,
    "experimental.chat.system.transform": (input, output) => enqueue(
      "SYSTEM_PROMPT", input, output, { recordKind: "system", system: output?.system ?? output }),
    "experimental.chat.messages.transform": (input, output) => enqueue(
      "CONTEXT_MESSAGES", input, output, {
        recordKind: "context",
        contextKind: (output?.messages ?? []).some((message) =>
          message?.info?.mode === "compaction"
          || message?.info?.summary === true
          || message?.parts?.some((part) => part?.type === "compaction"))
          ? "COMPACTED"
          : "ACTIVE",
        messages: output?.messages ?? output,
      }),
    event: onEvent,
    "experimental.session.compacting": (input, output) => enqueue(
      "COMPACTION_CONTEXT", input, output, {
        recordKind: "context",
        contextKind: "COMPACTION_REQUEST",
        context: output?.context ?? [],
        prompt: output?.prompt ?? null,
      }),
    dispose,
  }

  return {
    hooks,
    inspect: () => ({
      coverageStartAt,
      droppedCount,
      globalSequence,
      pendingRaw: pendingRaw.length,
      serialized: serialized.length,
      queuedBytes,
      runtime: { ...runtime },
    }),
    flush,
  }
}

async function TestAgentObservabilityPlugin() {
  return createObservabilityPlugin().hooks
}

// OpenCode 1.18.x 的文件插件使用 V1 模块入口；稳定 id 避免 loader 回退到枚举全部命名导出的 legacy 模式。
export default {
  id: "test-agent-opencode-observability",
  server: TestAgentObservabilityPlugin,
}
