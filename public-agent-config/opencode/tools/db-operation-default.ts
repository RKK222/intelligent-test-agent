/**
 * OpenCode Custom Tool — 数据库 SQL 执行（直连第三方接口）
 *
 * 使用方式：将此文件放到 .opencode/tools/db-operation-default.ts 即可。
 * 工具名自动为 "db_operation_default"。
 *
 * 功能：接收数据库连接信息与 SQL 语句，直接调用第三方数据库服务接口执行并返回结果。
 *       不经过任何后端代理，由 opencode 进程直接发起 HTTP 请求。
 */

import { tool } from "@opencode-ai/plugin"

/** 第三方数据库服务基础地址，按需修改 */
const DB_SERVICE_BASE_URL_ENV = "TEST_AGENT_DB_SERVICE_BASE_URL"

/** 数据库 SQL 执行接口路径 */
const DB_EXECUTE_ENDPOINT = "/aiTool/dbExecute"

// ============================================================
// 参数 Schema 定义
// ============================================================

const argSchema = {
  /** 数据库 IP 地址（必填） */
  host: tool.schema
    .string()
    .describe("数据库主机地址，如 db.example 或 localhost"),

  /** 数据库端口（必填） */
  port: tool.schema
    .number()
    .int()
    .describe("数据库端口号，如 3306（MySQL）、5432（PostgreSQL）、1521（Oracle）"),

  /** 数据库账号（必填） */
  username: tool.schema
    .string()
    .describe("数据库登录账号"),

  /** 数据库密码/授权码（必填） */
  password: tool.schema
    .string()
    .describe("数据库登录密码或授权码"),

  /** 数据库名称/Schema 名称（必填） */
  sid: tool.schema
    .string()
    .describe("数据库名称/Schema 名称，如 test_db、public。必须提供，不可为空。"),

  /** 是否纳管（必填，必须向用户确认，不得自行默认） */
  managed: tool.schema
    .string()
    .describe("是否纳管，必填。取值 \"1\" 表示纳管，\"0\" 表示不纳管。必须向用户确认后再填写，用户明确说\"是\"或\"纳管\"时传 \"1\"，用户明确说\"否\"或\"不纳管\"时传 \"0\"。禁止未经询问擅自填默认值。"),

  /** 数据库类型（选填，默认自动识别） */
  dbType: tool.schema
    .enum(["mysql", "postgresql", "oracle", "sqlserver", "sqlite"])
    .optional()
    .describe("数据库类型：mysql、postgresql、oracle、sqlserver、sqlite"),

  /** 待执行的 SQL 语句（必填） */
  sql: tool.schema
    .string()
    .describe("要执行的 SQL 语句。支持 SELECT / INSERT / UPDATE / DELETE / CREATE 等。注意：DML/DDL 会直接生效，请谨慎操作！"),

  /** 连接超时（毫秒），默认 10000 */
  connectTimeout: tool.schema
    .number()
    .int()
    .optional()
    .describe("数据库连接超时(ms)，默认 10000"),

  /** 查询超时（毫秒），默认 30000 */
  queryTimeout: tool.schema
    .number()
    .int()
    .optional()
    .describe("SQL 执行超时(ms)，默认 30000，复杂查询可设 60000"),
}

// ============================================================
// 工具定义
// ============================================================

export default tool({
  description: [
    "数据库 SQL 执行工具（默认版本）。",
    "当用户表达需要执行数据库查询、操作、或运行 SQL 语句时调用此工具。",
    "支持：查询数据(SELECT)、修改数据(INSERT/UPDATE/DELETE)、管理表结构(CREATE/ALTER/DROP)等。",
    "",
    "必填参数：host、port、username、password、sid（数据库名称）、managed（是否纳管）、sql",
    "- managed：必须向用户确认，禁止擅自填默认值。用户说\"是\"或\"纳管\"时传 \"1\"，用户说\"否\"或\"不纳管\"时传 \"0\"",
    "",
    "使用流程：",
    "1. 检查用户是否提供了所有必填参数（host、port、username、password、sid、managed、sql）。",
    "2. 如果缺少任意必填参数（尤其是 managed），必须通过对话向用户询问，收集完整信息后再调用本工具。",
    "3. 禁止对 managed 参数自行默认值，必须明确询问用户是否需要纳管。",
    "4. 所有必填参数齐全后，一次性调用本工具执行 SQL。",
    "5. 执行成功后，工具会返回格式化的结果字符串。",
    "   【核心要求】你必须一字不差地原样输出工具返回的全部内容，禁止以下行为：",
    "   - 禁止做总结、摘要、提炼或改写",
    "   - 禁止添加任何括号注释，如（结果已省略）、（LIMIT）、（详细内容）等",
    "   - 禁止把工具返回的多行内容压缩成一句话",
    "   - 禁止自行编造行数、数据内容或任何描述",
    "   正确示例：工具返回了表格和\"返回行数: 1000\"，你就直接展示那个表格和行数信息。",
    "   错误示例：把工具返回的内容总结成\"执行成功，返回 1000 行数据（结果已省略详细内容）\"——这种总结是绝对禁止的。",
    "",
    "⚠️ 注意：所有 SQL 都会直接在实际数据库上执行，操作前请确认风险！",
  ].join("\n"),

  args: argSchema,

  async execute(args) {
    const startTime = Date.now()

    try {
      const dbServiceBaseUrl = serviceBaseUrl()
      // 直连第三方数据库服务接口
      const response = await fetch(`${dbServiceBaseUrl}${DB_EXECUTE_ENDPOINT}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          host: args.host,
          port: args.port,
          username: args.username,
          password: args.password,
          sid: args.sid,
          managed: args.managed,
          dbType: args.dbType,
          sql: args.sql,
          connectTimeout: args.connectTimeout ?? 10000,
          queryTimeout: args.queryTimeout ?? 30000,
        }),
      })

      const elapsed = Date.now() - startTime

      if (!response.ok) {
        // 第三方服务返回非 2xx 状态码
        const text = await response.text().catch(() => "")
        return [
          `━━━ 数据库执行失败（服务层）━━━`,
          `状态码: ${response.status}`,
          `耗时:   ${elapsed}ms`,
          ``,
          `错误详情:`,
          text || "未知错误",
          `━━━━━━━━━━━━━━━━━━━━━━━`,
        ].join("\n")
      }

      const data = await response.json()

      // 第三方服务返回非 200 表示业务错误
      if (data.code !== "200" && data.code !== 200) {
        const errMsg = data.data || data.message || data.error || data.msg || "未知错误"
        return [
          `━━━ 数据库执行失败（业务层）━━━`,
          `错误码: ${data.code}`,
          `数据库: ${args.host}:${args.port}/${args.sid}`,
          `SQL:    ${args.sql}`,
          `耗时:   ${elapsed}ms`,
          ``,
          `错误信息:`,
          typeof errMsg === "object" ? JSON.stringify(errMsg, null, 2) : String(errMsg),
          `━━━━━━━━━━━━━━━━━━━━━━━`,
        ].join("\n")
      }

      // 成功响应 —— 美化输出
      return formatSuccessResponse(data.data, args, elapsed)

    } catch (err: any) {
      const elapsed = Date.now() - startTime
      return [
        `━━━ 数据库执行失败（网络/系统层）━━━`,
        `数据库: ${args.host}:${args.port}`,
        `SQL:    ${args.sql}`,
        `耗时:   ${elapsed}ms`,
        ``,
        `错误信息:`,
        err.message ?? String(err),
        `━━━━━━━━━━━━━━━━━━━━━━━`,
      ].join("\n")
    }
  },
})

// ============================================================
// 结果格式化辅助函数
// ============================================================

function formatSuccessResponse(resultData: any, args: any, elapsed: number): string {
  const lines: string[] = []

  lines.push(`━━━ 数据库执行成功 ━━━`)
  lines.push(``)
  lines.push(`数据库: ${args.host}:${args.port}/${args.sid}`)
  lines.push(`是否纳管: ${args.managed === "1" ? "是" : "否"}`)
  lines.push(`SQL:    ${truncateSql(args.sql, 80)}`)
  lines.push(`耗时:   ${elapsed}ms`)

  if (resultData === null || resultData === undefined) {
    lines.push(``)
    lines.push(`(返回结果为空)`)
  } else if (typeof resultData === "string") {
    lines.push(``)
    lines.push(`返回结果:`)
    lines.push(resultData)
  } else if (Array.isArray(resultData)) {
    lines.push(``)
    lines.push(`返回行数: ${resultData.length}`)

    if (resultData.length > 0) {
      const firstRow = resultData[0]
      if (typeof firstRow === "object" && firstRow !== null) {
        const MAX_CELL_WIDTH = 50
        const columns = Object.keys(firstRow)

        // Markdown 表格表头
        lines.push(``)
        lines.push(`| ${columns.join(" | ")} |`)
        lines.push(`| ${columns.map(() => "---").join(" | ")} |`)

        // Markdown 表格数据行（最多 50 行）
        const displayRows = resultData.slice(0, 50)
        for (const row of displayRows) {
          const values = columns.map(col => {
            const val = row[col]
            let str = val === null || val === undefined ? "NULL" : String(val)
            str = str.replace(/\|/g, "\\|").replace(/\n/g, " ")
            if (str.length > MAX_CELL_WIDTH) {
              str = str.substring(0, MAX_CELL_WIDTH - 3) + "..."
            }
            return str
          })
          lines.push(`| ${values.join(" | ")} |`)
        }

        if (resultData.length > 50) {
          lines.push(``)
          lines.push(`(共 ${resultData.length} 行，仅显示前 50 行)`)
        }
      } else {
        lines.push(``)
        for (const item of resultData) {
          lines.push(String(item))
        }
      }
    } else {
      lines.push(``)
      lines.push(`(结果集为空)`)
    }
  } else if (typeof resultData === "object") {
    lines.push(``)
    lines.push(`返回结果:`)
    const entries = Object.entries(resultData)
    for (const [key, val] of entries) {
      lines.push(`  ${key}: ${val === null || val === undefined ? "NULL" : String(val)}`)
    }
  } else {
    lines.push(``)
    lines.push(`返回结果:`)
    lines.push(String(resultData))
  }

  lines.push(``)
  lines.push(`━━━━━━━━━━━━━━━━━━━━━━━`)

  return lines.join("\n")
}

/** 截断 SQL 语句，防止过长 */
function truncateSql(sql: string, maxLen: number): string {
  const trimmed = sql.trim().replace(/\s+/g, " ")
  if (trimmed.length <= maxLen) return trimmed
  return trimmed.substring(0, maxLen - 3) + "..."
}

/** 截断字符串 */
function truncate(str: string, maxLen: number): string {
  if (str.length <= maxLen) return str
  return str.substring(0, maxLen - 3) + "..."
}

function serviceBaseUrl(): string {
  const value = (process.env[DB_SERVICE_BASE_URL_ENV] ?? "").trim()
  if (!value) throw new Error(`${DB_SERVICE_BASE_URL_ENV} 未配置`)
  const parsed = new URL(value)
  if (!["http:", "https:"].includes(parsed.protocol) || parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error(`${DB_SERVICE_BASE_URL_ENV} 配置无效`)
  }
  return value.replace(/\/+$/, "")
}
