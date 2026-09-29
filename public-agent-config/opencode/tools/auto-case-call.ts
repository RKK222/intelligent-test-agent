import { tool } from "./tool-compat"

const TCDS_BASE_URL_ENV = "TCDS_BASE_URL"
const DEFAULT_TCDS_BASE_URL = "http://tcds-prod.sdc.icbc"
const AUTO_CASE_AI_INVOKE = "/auto/case/ai/invoke"

const argSchema = {

    appName: tool.schema.string().describe("应用名称，从对话的信息中提取"),

    itemNo: tool.schema.string().describe("需求子条目，从对话的信息中提取"),

    testCaseDirectory: tool.schema.string().describe("测试案例目录路径，格式必须以'/'开头，例如:'/全部用例/归集案例/批量/DBF批量'"),
}

export default tool({
  description: [
    "自动化案例执行工具。",
    "当用户说'自动化案例调度'或者'执行自动化案例'时，你必须在回复中调用此工具，不要用文字回复代替工具调用。",
    "",
    "使用流程：",
    "1. 检查用户是否提供了所有必填参数（appName、itemNo、testCaseDirectory）。",
    "2. 必须通过对话向用户询问，收集完整信息后再调用本工具。",
    "4. 所有必填参数齐全后，一次性调用本工具。",
    "5. 执行成功后，工具会返回格式化的结果字符串。",
    "   【核心要求】你必须一字不差地原样输出工具返回的全部内容，禁止以下行为：",
    "   - 禁止做总结、摘要、提炼或改写",
    "   - 禁止添加任何括号注释，如（结果已省略）、（详细内容）等",
    "   - 禁止把工具返回的多行内容压缩成一句话",
    "   - 禁止自行编造行数、数据内容或任何描述",
    "   正确示例：工具返回了表格和\"返回行数: 1000\"，你就直接展示那个表格和行数信息。",
    "   错误示例：把工具返回的内容总结成\"执行成功，返回 1000 行数据（结果已省略详细内容）\"——这种总结是绝对禁止的。",
    "",
    "调用方式：",
    "调度自动化案例执行接口：auto-case-invoke({ appName: 'xxx', itemNo: 'xxx', testCaseDirectory: '' })",
    "",
    "【输出格式】工具返回数据后，必须直接展示工具返回的内容。禁止使用question弹窗让用户选择，禁止额外生成选择器UI。",
  ].join("\n"),

  args: argSchema,

  async execute(args) {

    try {
      if (!args.appName || !args.itemNo|| !args.testCaseDirectory) {
        return [
          `缺少参数：需要从对话上下文中提取 appName 和 itemNo。`,
          `上下文格式：[当前应用: 名称(appName) | 需求子条目: S20260617-000000 | 测试案例目录路径: '/全部案例/归集案例/批量/DBF批量']`,
        ].join("\n")
      }


      return await executeAutoCaseMenu(args.appName, args.itemNo, args.testCaseDirectory)

    } catch (err: any) {
      return `执行失败：${err.message ?? String(err)}`
    }
  },
})


async function executeAutoCaseMenu(appName: string, itemNo: string, testCaseDirectory: string): Promise<string> {
  const requestBody ={
    "appName":appName,
    "itemNo":itemNo,
    "testCaseDirectory":testCaseDirectory
  }
  const response = await fetch(`${serviceBaseUrl()}${AUTO_CASE_AI_INVOKE}`, {
    method: "POST",
    headers: { "Content-Type": "application/json"},
    body: JSON.stringify(requestBody),
  })

  if (!response.ok) {
    const text = await response.text().catch(() => "")
    return `执行失败：${response.status} - ${text || "未知错误"}`
  }

  const data = await response.json()

  if (data.code !== "0" && data.code !== 0) {
    const errMsg = data.data || data.msg || "未知错误"
    return `执行失败：${data.code} - ${typeof errMsg === "object" ? JSON.stringify(errMsg) : errMsg}`
  }

  return formatSuccessResponse(data.msg, appName, itemNo, testCaseDirectory)
}

function serviceBaseUrl(): string {
  const value = (process.env[TCDS_BASE_URL_ENV] ?? DEFAULT_TCDS_BASE_URL).trim()
  if (!value) throw new Error(`${TCDS_BASE_URL_ENV} 未配置`)
  const parsed = new URL(value)
  if (!["http:", "https:"].includes(parsed.protocol) || parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error(`${TCDS_BASE_URL_ENV} 配置无效`)
  }
  return value.replace(/\/+$/, "")
}

function formatSuccessResponse(resultData: any, appName: string, itemNo: string, testCaseDirectory: string): string {
  const lines: string[] = []

  lines.push(`━━━ 执行成功 ━━━`)
  lines.push(`应用名称: ${appName}`)
  lines.push(`需求子条目:${itemNo}`)
  lines.push(`测试案例目录:${testCaseDirectory}`)

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

function escapeMarkdown(str: string): string {
  return String(str).replace(/\|/g, "\\|").replace(/\n/g, " ")
}
