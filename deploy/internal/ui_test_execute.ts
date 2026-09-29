import { createHash } from "node:crypto"
import { tool } from "./opencode-v2-tool-compat.mjs"

const ENDPOINT_PATH = "/api/integration/v1/ui-executions"
const CONFIG_ENDPOINT_PATH = "/api/internal/agent/opencode/ui-test-tool/config"
const PLATFORM_BASE_URL_ENV = "TEST_AGENT_PLATFORM_BASE_URL"
const POLL_INTERVAL_MS = 2_000
const POLL_TIMEOUT_MS = 930_000
const HTTP_TIMEOUT_MS = 20_000
const TERMINAL_STATUSES = new Set(["SUCCEEDED", "FAILED"])

type ExecutionData = {
  executionId: string
  requestId: string
  caseName: string
  status: string
  success: boolean | null
  message: string
  errors: string[]
  stepCount: number
  durationSeconds: number
  reportUrl: string | null
}

type UiPlatformExecution = {
  execution_id: string
  request_id: string
  case_name: string
  status: string
  success: boolean | null
  message: string
  errors: string[]
  step_count: number
  duration_seconds: number
  report_url: string | null
}

type PlatformApiResponse<T> = {
  success: boolean
  data: T
  traceId: string
}

type UiTestToolConfig = {
  configured: boolean
  baseUrl: string | null
}

export default tool({
  description: "将被测系统环境和一行四列测试案例提交给独立 UI 平台执行一次，并等待最终结果。",
  args: {
    testEnvironment: tool.schema.string().min(1).max(20_000).describe("被测系统环境；由用户提供或父 Agent 从用户指定路径读取，不得补造"),
    caseName: tool.schema.string().max(500).optional().default("").describe("案例名称，仅用于标识"),
    testSteps: tool.schema.string().min(1).max(20_000).describe("测试步骤，唯一操作流程"),
    testData: tool.schema.string().max(20_000).optional().default("").describe("测试数据，仅用于步骤输入"),
    expectedResult: tool.schema.string().max(20_000).optional().default("").describe("预期结果，仅用于验证"),
  },
  async execute(args, context) {
    const baseUrl = await resolveUiPlatformBaseUrl(context.abort)
    const requestId = buildRequestId(context.sessionID, context.messageID, args)
    context.metadata({
      title: `执行 UI 案例：${args.caseName || "未命名案例"}`,
      metadata: { requestId, status: "SUBMITTING" },
    })

    // 该 POST 是一次 Tool 调用中唯一会创建外部自动化的请求。
    let execution = await uiPlatformRequest<UiPlatformExecution>(
      `${baseUrl}${ENDPOINT_PATH}`,
      context.abort,
      {
        method: "POST",
        body: JSON.stringify({
          request_id: requestId,
          test_environment: args.testEnvironment,
          case_name: args.caseName,
          test_steps: args.testSteps,
          test_data: args.testData,
          expected_result: args.expectedResult,
        }),
      },
    ).then((response) => normalizeExecution(response, baseUrl))

    context.metadata({
      title: `执行 UI 案例：${args.caseName || "未命名案例"}`,
      metadata: { executionId: execution.executionId, status: execution.status },
    })

    const deadline = Date.now() + POLL_TIMEOUT_MS
    while (!TERMINAL_STATUSES.has(execution.status)) {
      if (Date.now() >= deadline) {
        throw new Error(`UI 测试执行状态等待超时，executionId=${execution.executionId}`)
      }
      await abortableDelay(POLL_INTERVAL_MS, context.abort)
      execution = await uiPlatformRequest<UiPlatformExecution>(
        `${baseUrl}${ENDPOINT_PATH}/${encodeURIComponent(execution.executionId)}`,
        context.abort,
        { method: "GET" },
      ).then((response) => normalizeExecution(response, baseUrl))
      context.metadata({
        title: `执行 UI 案例：${args.caseName || "未命名案例"}`,
        metadata: { executionId: execution.executionId, status: execution.status },
      })
    }

    return {
      title: execution.success ? "UI 测试执行成功" : "UI 测试执行失败",
      output: JSON.stringify(execution, null, 2),
      metadata: {
        executionId: execution.executionId,
        requestId: execution.requestId,
        status: execution.status,
        success: execution.success,
      },
    }
  },
})

async function resolveUiPlatformBaseUrl(signal: AbortSignal): Promise<string> {
  const platformBaseUrl = requiredEnvironmentBaseUrl(PLATFORM_BASE_URL_ENV)
  const response = await platformConfigRequest<PlatformApiResponse<UiTestToolConfig>>(
    `${platformBaseUrl}${CONFIG_ENDPOINT_PATH}`,
    signal,
  )
  if (response.success !== true || !response.data || typeof response.data.configured !== "boolean") {
    throw new Error("UI 测试执行配置查询返回格式无效")
  }
  if (!response.data.configured) {
    throw new Error("UI 测试执行尚未配置：请超级管理员在系统管理的通用参数中配置 UITEST_BASE_URL")
  }
  if (typeof response.data.baseUrl !== "string" || !response.data.baseUrl.trim()) {
    throw new Error("UI 测试执行配置查询返回格式无效")
  }
  return normalizeBaseUrl(response.data.baseUrl, "超级管理员配置的 UITEST_BASE_URL 无效")
}

function requiredEnvironmentBaseUrl(name: string): string {
  const value = process.env[name]?.trim()
  if (!value) {
    throw new Error(`UI 测试执行无法读取平台配置：缺少 ${name}`)
  }
  return normalizeBaseUrl(value, `UI 测试执行平台回调配置无效：${name} 必须是 HTTP/HTTPS 地址`)
}

function normalizeBaseUrl(value: string, invalidMessage: string): string {
  const normalized = value.trim()
  try {
    const parsed = new URL(normalized)
    if (
      !["http:", "https:"].includes(parsed.protocol)
      || !parsed.hostname
      || parsed.username
      || parsed.password
      || parsed.search
      || parsed.hash
    ) {
      throw new Error("invalid UI platform URL")
    }
  } catch {
    throw new Error(invalidMessage)
  }
  return normalized.replace(/\/+$/, "")
}

function buildRequestId(
  sessionId: string,
  messageId: string,
  args: unknown,
): string {
  const digest = createHash("sha256")
    .update(JSON.stringify(args))
    .digest("hex")
    .slice(0, 24)
  const session = safeIdPart(sessionId).slice(0, 64)
  const message = safeIdPart(messageId).slice(0, 64)
  return `opencode:${session}:${message}:${digest}`
}

function safeIdPart(value: string): string {
  const normalized = value.replace(/[^A-Za-z0-9_.-]/g, "_")
  return normalized || "unknown"
}

async function uiPlatformRequest<T>(
  url: string,
  parentSignal: AbortSignal,
  init: { method: "GET" | "POST"; body?: string },
): Promise<T> {
  const controller = new AbortController()
  const onAbort = () => controller.abort()
  parentSignal.addEventListener("abort", onAbort, { once: true })
  const timer = setTimeout(() => controller.abort(), HTTP_TIMEOUT_MS)
  try {
    const response = await fetch(url, {
      method: init.method,
      headers: {
        Accept: "application/json",
        ...(init.body ? { "Content-Type": "application/json" } : {}),
      },
      body: init.body,
      signal: controller.signal,
    })
    const text = await response.text()
    const payload = text ? JSON.parse(text) : null
    if (!response.ok || !payload) {
      const detail = typeof payload?.detail === "string" ? `，${payload.detail}` : ""
      throw new Error(`UI 平台调用失败（HTTP ${response.status}${detail}）`)
    }
    return payload as T
  } catch (error) {
    if (parentSignal.aborted) {
      throw new Error("UI 测试执行已取消")
    }
    if (controller.signal.aborted) {
      throw new Error("UI 平台请求超时")
    }
    throw error
  } finally {
    clearTimeout(timer)
    parentSignal.removeEventListener("abort", onAbort)
  }
}

async function platformConfigRequest<T>(url: string, parentSignal: AbortSignal): Promise<T> {
  const controller = new AbortController()
  const onAbort = () => controller.abort()
  parentSignal.addEventListener("abort", onAbort, { once: true })
  const timer = setTimeout(() => controller.abort(), HTTP_TIMEOUT_MS)
  try {
    const response = await fetch(url, {
      method: "GET",
      headers: { Accept: "application/json" },
      signal: controller.signal,
    })
    const text = await response.text()
    let payload: unknown = null
    try {
      payload = text ? JSON.parse(text) : null
    } catch {
      throw new Error("UI 测试执行配置查询返回的 JSON 无效")
    }
    if (!response.ok || !payload) {
      const message = payload && typeof payload === "object" && "message" in payload
        ? (payload as { message?: unknown }).message
        : null
      const reason = typeof message === "string"
        ? `，${sanitizeExecutionDetail(message)}`
        : ""
      throw new Error(`UI 测试执行配置查询失败（HTTP ${response.status}${reason}）`)
    }
    return payload as T
  } catch (error) {
    if (parentSignal.aborted) {
      throw new Error("UI 测试执行已取消")
    }
    if (controller.signal.aborted) {
      throw new Error("UI 测试执行配置查询超时")
    }
    throw error
  } finally {
    clearTimeout(timer)
    parentSignal.removeEventListener("abort", onAbort)
  }
}

function normalizeExecution(source: UiPlatformExecution, baseUrl: string): ExecutionData {
  if (!source.execution_id || !source.request_id || !source.status) {
    throw new Error("UI 平台返回的执行结果格式无效")
  }
  return {
    executionId: source.execution_id,
    requestId: source.request_id,
    caseName: source.case_name || "",
    status: source.status,
    success: source.success,
    message: sanitizeExecutionDetail(source.message),
    errors: Array.isArray(source.errors)
      ? source.errors.map(sanitizeExecutionDetail).filter(Boolean).slice(0, 20)
      : [],
    stepCount: Number(source.step_count || 0),
    durationSeconds: Number(source.duration_seconds || 0),
    reportUrl: source.report_url ? new URL(source.report_url, `${baseUrl}/`).toString() : null,
  }
}

function sanitizeExecutionDetail(value: unknown): string {
  if (typeof value !== "string") {
    return ""
  }
  return value
    .replace(/https?:\/\/[^\s"'<>]+/giu, "[URL]")
    .replace(/[\u0000-\u001f\u007f]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 1_000)
}

function abortableDelay(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(new Error("UI 测试执行已取消"))
      return
    }
    const timer = setTimeout(() => {
      signal.removeEventListener("abort", onAbort)
      resolve()
    }, milliseconds)
    const onAbort = () => {
      clearTimeout(timer)
      reject(new Error("UI 测试执行已取消"))
    }
    signal.addEventListener("abort", onAbort, { once: true })
  })
}
