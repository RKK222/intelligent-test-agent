import { createHash } from "node:crypto"
import { tool } from "@opencode-ai/plugin"

const ENDPOINT_PATH = "/api/internal/agent/opencode/ui-test-executions"
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

type PlatformResponse = {
  success: boolean
  data: ExecutionData
  traceId: string
}

export default tool({
  description: "将一行四列测试案例提交给独立 uitest6 平台执行一次，并等待最终结果。",
  args: {
    caseName: tool.schema.string().max(500).optional().default("").describe("案例名称，仅用于标识"),
    testSteps: tool.schema.string().min(1).max(20_000).describe("测试步骤，唯一操作流程"),
    testData: tool.schema.string().max(20_000).optional().default("").describe("测试数据，仅用于步骤输入"),
    expectedResult: tool.schema.string().max(20_000).optional().default("").describe("预期结果，仅用于验证"),
  },
  async execute(args, context) {
    const baseUrl = requiredEnvironment("TEST_AGENT_PLATFORM_BASE_URL").replace(/\/+$/, "")
    const token = requiredEnvironment("TEST_AGENT_UI_TEST_TOOL_TOKEN")
    const requestId = buildRequestId(context.sessionID, context.messageID, args)
    context.metadata({
      title: `执行 UI 案例：${args.caseName || "未命名案例"}`,
      metadata: { requestId, status: "SUBMITTING" },
    })

    // 该 POST 是一次 Tool 调用中唯一会创建外部自动化的请求。
    let execution = await platformRequest<PlatformResponse>(
      `${baseUrl}${ENDPOINT_PATH}`,
      token,
      context.abort,
      {
        method: "POST",
        body: JSON.stringify({
          requestId,
          caseName: args.caseName,
          testSteps: args.testSteps,
          testData: args.testData,
          expectedResult: args.expectedResult,
        }),
      },
    ).then((response) => response.data)

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
      execution = await platformRequest<PlatformResponse>(
        `${baseUrl}${ENDPOINT_PATH}/${encodeURIComponent(execution.executionId)}`,
        token,
        context.abort,
        { method: "GET" },
      ).then((response) => response.data)
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

function requiredEnvironment(name: string): string {
  const value = process.env[name]?.trim()
  if (!value) {
    throw new Error(`UI 测试执行尚未配置：缺少 ${name}`)
  }
  return value
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

async function platformRequest<T>(
  url: string,
  token: string,
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
        Authorization: `Bearer ${token}`,
        Accept: "application/json",
        ...(init.body ? { "Content-Type": "application/json" } : {}),
      },
      body: init.body,
      signal: controller.signal,
    })
    const text = await response.text()
    const payload = text ? JSON.parse(text) : null
    if (!response.ok || !payload?.success || !payload?.data) {
      const code = typeof payload?.code === "string" ? `，code=${payload.code}` : ""
      throw new Error(`UI 测试执行桥接调用失败（HTTP ${response.status}${code}）`)
    }
    return payload as T
  } catch (error) {
    if (parentSignal.aborted) {
      throw new Error("UI 测试执行已取消")
    }
    if (controller.signal.aborted) {
      throw new Error("UI 测试执行桥接请求超时")
    }
    throw error
  } finally {
    clearTimeout(timer)
    parentSignal.removeEventListener("abort", onAbort)
  }
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
