/**
 * OpenCode Custom Tool — HTTP 代理调用
 *
 * 使用方式：将此文件放到 .opencode/tools/http-call.ts 即可。
 * 工具名自动为 "http_call"。
 *
 * 前置条件：确保 Java 后端代理服务已启动（默认 localhost:8080）。
 *           启动命令: cd 项目目录 && mvn spring-boot:run
 */

import { tool } from "@opencode-ai/plugin"

/** 后端代理服务地址，按需修改 */
const PROXY_BASE_URL_ENV = "TEST_AGENT_HTTP_PROXY_BASE_URL"
const DEFAULT_PROXY_BASE_URL = "http://interface.sdc.cs.icbc/contract-api"

// ============================================================
// 参数 Schema 定义
// ============================================================

const argSchema = {
  /** 目标 URL（必填），支持 http/https */
  uri: tool.schema
    .string()
    .describe("目标 URL，如 https://api.example.com/users 或 https://api.example.com/health"),

  /** HTTP 方法（选填） */
  method: tool.schema
    .enum(["GET", "POST", "PUT", "DELETE", "PATCH"])
    .optional()
    .describe("HTTP 方法，默认 GET"),

  /** 查询参数（选填），拼到 URL 末尾 */
  queryParams: tool.schema
    .record(tool.schema.string(), tool.schema.string())
    .optional()
    .describe("查询参数键值对，如 { page: '1', size: '10' } → ?page=1&size=10"),

  /** 请求头（选填） */
  headers: tool.schema
    .record(tool.schema.string(), tool.schema.string())
    .optional()
    .describe("请求头，如 { Authorization: 'Bearer xxx', 'Content-Type': 'application/json' }"),

  /** 请求体（选填），POST/PUT/PATCH 时使用 */
  body: tool.schema
    .string()
    .optional()
    .describe("请求体字符串。JSON 需 JSON.stringify() 后再传"),

  /** 连接超时（毫秒），默认 10000 */
  connectTimeout: tool.schema
    .number()
    .int()
    .optional()
    .describe("TCP 连接超时(ms)，默认 10000，内网可设 3000"),

  /** 读取超时（毫秒），默认 30000 */
  readTimeout: tool.schema
    .number()
    .int()
    .optional()
    .describe("等待响应超时(ms)，默认 30000，快速接口可设 5000"),
}

// ============================================================
// 工具定义
// ============================================================

export default tool({
  description: [
    "通过后端 HTTP 代理服务发起任意 HTTP 调用。",
    "适用于：调用外部 API、请求内网服务、健康检查、发送 webhook 等。",
    "支持 GET / POST / PUT / DELETE / PATCH 方法。",
    "返回目标服务的状态码、响应头、响应体及耗时。",
  ].join(" "),

  args: argSchema,

  async execute(args) {
    const startTime = Date.now()

    try {
      const proxyBaseUrl = serviceBaseUrl()
      // 调用后端代理
      const response = await fetch(`${proxyBaseUrl}/opencode/interface/http/call`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          uri: args.uri,
          method: args.method ?? "GET",
          queryParams: args.queryParams,
          headers: args.headers,
          body: args.body,
          connectTimeout: args.connectTimeout ?? 10000,
          readTimeout: args.readTimeout ?? 30000,
        }),
      })

      if (!response.ok) {
        // 代理服务本身出错（Java 端异常）
        const text = await response.text().catch(() => "")
        return `❌ 代理服务错误 (${response.status}): ${text}`
      }

      const data = await response.json()

      // 格式化输出给 LLM
      const elapsed = Date.now() - startTime
      const headerStr = data.headers
        ? Object.entries(data.headers)
            .map(([k, v]) => `  ${k}: ${v}`)
            .join("\n")
        : "  (无)"

      return [
        `━━━ HTTP 调用结果 ━━━`,
        `请求:  ${args.method ?? "GET"} ${args.uri}`,
        `状态码: ${data.statusCode}`,
        `耗时:   ${elapsed}ms`,
        ``,
        `响应头:`,
        headerStr,
        ``,
        `响应体:`,
        data.body ?? "(空)",
        `━━━━━━━━━━━━━━━━━━━━`,
      ].join("\n")
    } catch (err: any) {
      const elapsed = Date.now() - startTime
      return [
        `━━━ HTTP 调用失败 ━━━`,
        `请求:  ${args.method ?? "GET"} ${args.uri}`,
        `耗时:   ${elapsed}ms`,
        `错误:   ${err.message ?? String(err)}`,
        `━━━━━━━━━━━━━━━━━━━━`,
      ].join("\n")
    }
  },
})

function serviceBaseUrl(): string {
  const value = (process.env[PROXY_BASE_URL_ENV] ?? DEFAULT_PROXY_BASE_URL).trim()
  if (!value) throw new Error(`${PROXY_BASE_URL_ENV} 未配置`)
  const parsed = new URL(value)
  if (!["http:", "https:"].includes(parsed.protocol) || parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error(`${PROXY_BASE_URL_ENV} 配置无效`)
  }
  return value.replace(/\/+$/, "")
}
