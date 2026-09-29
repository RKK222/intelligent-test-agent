import { tool } from "./tool-compat"

const DB_SERVICE_BASE_URL_ENV = "TEST_AGENT_DB_SERVICE_BASE_URL"
const DEFAULT_DB_SERVICE_BASE_URL = "http://interface.sdc.cs.icbc/contract-api"
const QUERY_DATA_SOURCES_ENDPOINT = "/dbOperation/queryDataSources"
const DB_EXECUTE_BY_KEY_ENDPOINT = "/aiTool/dbExecuteByKey"

const argSchema = {
  appId: tool.schema
    .string()
    .describe("应用ID，从对话开头的环境信息 '- appId: xxx' 中提取"),

  version: tool.schema
    .string()
    .describe("版本号，从对话开头的环境信息 '- version: xxx' 中提取"),

  dataSourceKey: tool.schema
    .string()
    .optional()
    .describe("数据源名称。查询列表时不传，执行SQL时传入"),

  sql: tool.schema
    .string()
    .optional()
    .describe("SQL语句。仅执行时需要"),
}

export default tool({
  description: [
    "一体化数据源专用的数据库SQL执行工具。",
    "",
    "当用户说'一体化数据源'并要执行SQL时，你必须在回复中调用此工具，不要用文字回复代替工具调用。",
    "",
    "调用方式：",
    "- 第一步（查数据源列表）：db_operation_yth({ appId: 'xxx', version: 'xxx' })",
    "- 第二步（执行SQL）：db_operation_yth({ appId: 'xxx', version: 'xxx', dataSourceKey: 'xxx', sql: 'xxx' })",
    "",
    "appId和version从对话开头的环境信息中提取（- appId: xxx / - version: xxx）。",
    "不要询问用户appId或version，直接从上下文提取。",
     "不要询问用户host/port/username/password/sid等数据库连接信息。",
     "",
     "【输出格式】工具返回数据源列表后，必须直接展示工具返回的内容。禁止使用question弹窗让用户选择，禁止额外生成选择器UI。",
  ].join("\n"),

  args: argSchema,

  async execute(args, context) {
    const startTime = Date.now()

    try {
      if (!args.appId || !args.version) {
        return [
          `缺少参数：需要从对话上下文中提取 appId 和 version。`,
          `上下文格式：[当前应用: 名称(appId) | 版本: xxxx年x月]`,
        ].join("\n")
      }

      if (!args.dataSourceKey) {
        return await queryDataSources(args.appId, args.version, startTime)
      }

      if (!args.sql) {
        return `错误：提供了 dataSourceKey 但未提供 sql。`
      }

      return await executeSqlByKey(args.dataSourceKey, args.sql, startTime)

    } catch (err: any) {
      return `执行失败：${err.message ?? String(err)}`
    }
  },
})

async function queryDataSources(appId: string, version: string, startTime: number): Promise<string> {
  const response = await fetch(`${serviceBaseUrl()}${QUERY_DATA_SOURCES_ENDPOINT}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ appId, appVersion:version }),
  })

  const elapsed = Date.now() - startTime

  if (!response.ok) {
    const text = await response.text().catch(() => "")
    return `查询数据源失败：${response.status} - ${text || "未知错误"}`
  }

  const data = await response.json()

  if (data.code !== "200" && data.code !== 200) {
    const errMsg = data.data || data.message || data.error || data.msg || "未知错误"
    return `查询数据源失败：${data.code} - ${typeof errMsg === "object" ? JSON.stringify(errMsg) : errMsg}`
  }

  const resultData = data.data || []
  return formatDataSourceList(resultData, appId, version, elapsed)
}

function formatDataSourceList(dataSources: any[], appId: string, version: string, elapsed: number): string {
  const lines: string[] = []

  lines.push(`━━━ 数据源列表 ━━━`)

  if (!Array.isArray(dataSources) || dataSources.length === 0) {
    lines.push(`(未找到可用的数据源)`)
  } else {
    lines.push(``)
    lines.push(`| 序号 | 数据库名 | dataSourceName |`)
    lines.push(`| --- | --- | --- |`)

    dataSources.forEach((ds, index) => {
      const num = index + 1
      const dbName = ds.dataBaseName || ds.databaseName || "-"
      const sourceName = ds.dataSourceName || "-"
      lines.push(`| ${num} | ${escapeMarkdown(dbName)} | ${escapeMarkdown(sourceName)} |`)
    })

    lines.push(``)
    lines.push(`请选择一个数据库名。调用工具时，传入对应行的 dataSourceName 作为 dataSourceKey。`)
  }

  lines.push(`━━━━━━━━━━━━━━━━━━━━━━━`)

  return lines.join("\n")
}

async function executeSqlByKey(dataSourceKey: string, sql: string, startTime: number): Promise<string> {
  const response = await fetch(`${serviceBaseUrl()}${DB_EXECUTE_BY_KEY_ENDPOINT}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ key: dataSourceKey, sql }),
  })

  const elapsed = Date.now() - startTime

  if (!response.ok) {
    const text = await response.text().catch(() => "")
    return `执行失败：${response.status} - ${text || "未知错误"}`
  }

  const data = await response.json()

  if (data.code !== "200" && data.code !== 200) {
    const errMsg = data.data || data.message || data.error || data.msg || "未知错误"
    return `执行失败：${data.code} - ${typeof errMsg === "object" ? JSON.stringify(errMsg) : errMsg}`
  }

  return formatSuccessResponse(data.data, dataSourceKey, sql, elapsed)
}

function formatSuccessResponse(resultData: any, dataSourceKey: string, sql: string, elapsed: number): string {
  const lines: string[] = []

  lines.push(`━━━ 执行成功 ━━━`)
  lines.push(`数据源: ${dataSourceKey}`)
  lines.push(`SQL:    ${truncateSql(sql, 80)}`)
  lines.push(`耗时:   ${elapsed}ms`)

  if (resultData === null || resultData === undefined) {
    lines.push(`(返回结果为空)`)
  } else if (Array.isArray(resultData)) {
    lines.push(`返回行数: ${resultData.length}`)

    if (resultData.length > 0) {
      const firstRow = resultData[0]
      if (typeof firstRow === "object" && firstRow !== null) {
        const columns = Object.keys(firstRow).slice(0, 10)
        lines.push(``)
        lines.push(`| ${columns.join(" | ")} |`)
        lines.push(`| ${columns.map(() => "---").join(" | ")} |`)

        const displayRows = resultData.slice(0, 50)
        for (const row of displayRows) {
          const values = columns.map(col => {
            const val = row[col]
            let str = val === null || val === undefined ? "NULL" : String(val)
            str = escapeMarkdown(str)
            if (str.length > 30) str = str.substring(0, 27) + "..."
            return str
          })
          lines.push(`| ${values.join(" | ")} |`)
        }

        if (resultData.length > 50) {
          lines.push(`(共 ${resultData.length} 行，仅显示前 50 行)`)
        }
      }
    }
  } else if (typeof resultData === "object") {
    const entries = Object.entries(resultData)
    for (const [key, val] of entries) {
      lines.push(`  ${key}: ${val === null || val === undefined ? "NULL" : String(val)}`)
    }
  } else {
    lines.push(String(resultData))
  }

  lines.push(`━━━━━━━━━━━━━━━━━━━━━━━`)

  return lines.join("\n")
}

function truncateSql(sql: string, maxLen: number): string {
  const trimmed = sql.trim().replace(/\s+/g, " ")
  if (trimmed.length <= maxLen) return trimmed
  return trimmed.substring(0, maxLen - 3) + "..."
}

function escapeMarkdown(str: string): string {
  return String(str).replace(/\|/g, "\\|").replace(/\n/g, " ")
}

function serviceBaseUrl(): string {
  const value = (process.env[DB_SERVICE_BASE_URL_ENV] ?? DEFAULT_DB_SERVICE_BASE_URL).trim()
  if (!value) throw new Error(`${DB_SERVICE_BASE_URL_ENV} 未配置`)
  const parsed = new URL(value)
  if (!["http:", "https:"].includes(parsed.protocol) || parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error(`${DB_SERVICE_BASE_URL_ENV} 配置无效`)
  }
  return value.replace(/\/+$/, "")
}
