/**
 * OpenCode Custom Tool — Dubbo RPC 泛化调用
 *
 * 使用方式：将此文件放到 .opencode/tools/rpc-call.ts 即可。
 * 工具名自动为 "rpc_call"。
 *
 * 前置条件：确保 Java 后端代理服务已启动（默认 localhost:1994）。
 *           启动命令: cd 项目目录 && mvn spring-boot:run
 */

import { tool } from "@opencode-ai/plugin"

/** 后端代理服务地址，按需修改 */
const PROXY_BASE_URL_ENV = "TEST_AGENT_RPC_PROXY_BASE_URL"
const DEFAULT_PROXY_BASE_URL = "http://interface.sdc.cs.icbc/contract-api"

// ============================================================
// 参数 Schema 定义
// ============================================================

const argSchema = {
  /** 注册中心地址 */
  registryAddress: tool.schema
    .string()
    .optional()
    .describe("注册中心地址，如 127.0.0.1:2181。直连模式可不填"),

  /** 接口全限定名 */
  interfaceName: tool.schema
    .string()
    .describe("Dubbo 接口全限定名，如 com.example.service.UserService"),

  /** 方法名 */
  methodName: tool.schema
    .string()
    .describe("要调用的方法名，如 getUserById"),

  /** 参数类型列表 */
  parameterTypes: tool.schema
    .array(tool.schema.string())
    .optional()
    .describe("参数类型全限定名数组，如 ['java.lang.Long', 'java.lang.String']"),

  /** 参数值列表 */
  args: tool.schema
    .array(tool.schema.string())
    .optional()
    .describe("参数值 JSON 字符串数组，如 ['1', '{\"name\":\"张三\"}']"),

  /** 接口版本号 */
  version: tool.schema
    .string()
    .optional()
    .describe("接口版本号，默认 1.0.0"),

  /** 接口分组 */
  group: tool.schema
    .string()
    .optional()
    .describe("接口分组，默认空字符串"),

  /** 调用超时 */
  timeout: tool.schema
    .number()
    .int()
    .optional()
    .describe("调用超时(ms)，默认 30000"),

  /** 是否直连模式 */
  direct: tool.schema
    .boolean()
    .optional()
    .describe("是否跳过注册中心直连服务，默认 false"),

  /** 直连地址 */
  directUrl: tool.schema
    .string()
    .optional()
    .describe("直连地址，如 127.0.0.1:20880，direct=true 时必填"),

  /** 隐式参数 */
  attachments: tool.schema
    .record(tool.schema.string(), tool.schema.string())
    .optional()
    .describe("Dubbo 隐式参数（Attachment），如 { tenantId: '123' }"),
}

// ============================================================
// 工具定义
// ============================================================

export default tool({
  description: [
    "通过后端 RPC 代理服务发起 Dubbo 泛化调用。",
    "适用于：调用 Dubbo 接口、测试 RPC 服务、集成调试等。",
    "支持注册中心模式（ZooKeeper/Nacos）和直连模式。",
    "返回接口调用结果及耗时。",
  ].join(" "),

  args: argSchema,

  async execute(args) {
    const startTime = Date.now()

    try {
      const proxyBaseUrl = serviceBaseUrl()
      // 调用后端 RPC 代理
      const response = await fetch(`${proxyBaseUrl}/opencode/interface/rpc/call`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          registryAddress: args.registryAddress,
          interfaceName: args.interfaceName,
          methodName: args.methodName,
          parameterTypes: args.parameterTypes ?? [],
          args: args.args ?? [],
          version: args.version ?? "1.0.0",
          group: args.group ?? "",
          timeout: args.timeout ?? 30000,
          direct: args.direct ?? false,
          directUrl: args.directUrl,
          attachments: args.attachments,
        }),
      })

      if (!response.ok) {
        const text = await response.text().catch(() => "")
        return `❌ 代理服务错误 (${response.status}): ${text}`
      }

      const data = await response.json()

      // 格式化输出给 LLM
      const elapsed = Date.now() - startTime

      if (!data.success) {
        return [
          `━━━ RPC 调用失败 ━━━`,
          `接口: ${args.interfaceName}`,
          `方法: ${args.methodName}`,
          `耗时: ${elapsed}ms`,
          `错误: ${data.errorMessage}`,
          `━━━━━━━━━━━━━━━━━━━━`,
        ].join("\n")
      }

      const attachStr = data.attachments && Object.keys(data.attachments).length > 0
        ? Object.entries(data.attachments)
            .map(([k, v]) => `  ${k}: ${v}`)
            .join("\n")
        : "  (无)"

      return [
        `━━━ RPC 调用结果 ━━━`,
        `接口: ${args.interfaceName}`,
        `方法: ${args.methodName}`,
        `耗时: ${elapsed}ms`,
        ``,
        `返回数据:`,
        data.data ?? "(空)",
        ``,
        `隐式参数回传:`,
        attachStr,
        `━━━━━━━━━━━━━━━━━━━━`,
      ].join("\n")
    } catch (err: any) {
      const elapsed = Date.now() - startTime
      return [
        `━━━ RPC 调用失败 ━━━`,
        `接口: ${args.interfaceName}`,
        `方法: ${args.methodName}`,
        `耗时: ${elapsed}ms`,
        `异常: ${err.stack ?? String(err)}`,
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
