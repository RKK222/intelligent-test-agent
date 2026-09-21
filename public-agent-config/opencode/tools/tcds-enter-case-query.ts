/**
 * OpenCode Custom Tool — 准入测试案例查询
 *
 * 功能：查询“未导入准入测试案例明细”和“未执行准入测试案例明细”。
 *       通过子进程调用同级的 http_call.py 发起 HTTP POST（自动携带 toolId 头）。
 *
 * 触发（用户原话，按关键词匹配即可，不必逐字相同）：
 *   1) “查询xxx开发部门xxx应用 xxx版本的未导入准入测试案例的明细” → queryType=未导入
 *   2) “查询xxx开发部门xxx应用 xxx版本的未执行准入测试案例的明细” → queryType=未执行
 *
 * 返回：接口返回 {code,data,msg}；code=0 展示 data 内容，code!=0 展示 msg 信息。
 */

import { tool } from "@opencode-ai/plugin"
import { existsSync } from "node:fs"
import path from "node:path"

// ==================== 可修改配置 ====================
/** TCDS 服务基础地址（含端口），按需修改 */
const TCDS_BASE_URL = "http://122.210.62.32:9080"
/** 未导入准入案例接口路径 */
const NO_ENTER_ENDPOINT = "/tcds/tasks/no-enter-case"
/** 未执行准入案例接口路径 */
const NO_EXCUTE_ENDPOINT = "/tcds/tasks/no-excute-enter-case"
/** http_call.py 脚本文件名（与本工具同级，同处 tools/ 目录） */
const HTTP_CALL_SCRIPT = "http_call.py"
/** 全局工具目录（默认查找位置，与全局安装位置一致） */
const GLOBAL_TOOLS_DIR = path.join(process.env.HOME ?? "", ".config", "opencode", "tools")
/** python 解释器（Linux/Mac 通用：优先用环境变量 TCDS_PYTHON_BIN 覆盖；默认使用 PATH 上的 python3） */
const PYTHON_BIN = "/usr/local/bin/python3"
/** 子进程硬超时（ms），防止请求挂起 */
const SUBPROCESS_TIMEOUT_MS = 120000
// =====================================================

/** 解析 http_call.py 绝对路径：优先与本工具同级的 http_call.py，其次会话项目目录下的 tools/，最后全局工具目录。 */
function resolveHttpCallScript(context: any): string {
  // 同级优先：http_call.py 与本 .ts 同处 tools/ 目录
  let thisDir = ""
  try {
    thisDir = path.dirname(new URL(import.meta.url).pathname)
  } catch {
    thisDir = ""
  }
  if (thisDir) {
    const sibling = path.join(thisDir, HTTP_CALL_SCRIPT)
    if (existsSync(sibling)) return sibling
  }
  if (context?.directory) {
    const local = path.join(context.directory, "tools", HTTP_CALL_SCRIPT)
    if (existsSync(local)) return local
  }
  return path.join(GLOBAL_TOOLS_DIR, HTTP_CALL_SCRIPT)
}

const argSchema = {
  queryType: tool.schema
    .enum(["未导入", "未执行"])
    .describe(`查询类型。“未导入”=未导入准入测试案例明细；“未执行”=未执行准入测试案例明细。按用户原话中的“未导入/未执行”选择。`),

  deptName: tool.schema
    .string()
    .describe(`开发部门名称。从用户原话“xxx开发部门”中提取 xxx，如“杭州开发四部”。`),

  appName: tool.schema
    .string()
    .describe(`应用名称。从用户原话“xxx应用”中提取 xxx，如“F-BASE”。`),

  editionId: tool.schema
    .string()
    .describe(`版本名称。从用户原话“xxx版本”中提取 xxx，如“2026年8月”。`),
}

export default tool({
  description: [
    `准入测试案例查询工具。`,
    ``,
    `触发场景（用户原话，按关键词匹配即可，不必逐字相同）：`,
    `1) “查询xxx开发部门xxx应用 xxx版本的未导入准入测试案例的明细” → queryType=未导入`,
    `2) “查询xxx开发部门xxx应用 xxx版本的未执行准入测试案例的明细” → queryType=未执行`,
    ``,
    `参数提取规则：`,
    `- deptName：取“xxx开发部门”中的 xxx（保留“开发部门”前的完整名称，如“杭州开发四部”）`,
    `- appName：取“xxx应用”中的 xxx（如“F-BASE”）`,
    `- editionId：取“xxx版本”中的 xxx（如“2026年8月”）`,
    `- 任一参数缺失时，必须先向用户询问补齐，禁止自行编造或填默认值。`,
    ``,
    `内部行为：通过子进程执行 python3 http_call.py POST <url> -b '{...}' 发起调用（http_call.py 与本工具同级，自动携带 toolId 头）。`,
    `返回处理：接口返回 {code,data,msg}；code=0 → 原样展示 data 内容；code!=0 → 展示 msg 信息。`,
    `【输出要求】工具返回结果后，必须直接原样展示工具返回的全部内容，禁止摘要/省略/改写。`,
  ].join("\n"),

  args: argSchema,

  async execute(args, context) {
    const startTime = Date.now()

    // 参数校验
    const missing: string[] = []
    if (!args.deptName) missing.push("deptName(开发部门)")
    if (!args.appName) missing.push("appName(应用)")
    if (!args.editionId) missing.push("editionId(版本)")
    if (missing.length > 0) {
      return `缺少参数：${missing.join("、")}。请提供完整信息后重试。`
    }

    const endpoint = args.queryType === "未导入" ? NO_ENTER_ENDPOINT : NO_EXCUTE_ENDPOINT
    const url = `${TCDS_BASE_URL}${endpoint}`

    const bodyObj = {
      deptName: args.deptName,
      appName: args.appName,
      editionId: args.editionId,
    }
    const bodyJson = JSON.stringify(bodyObj)

    // 调用 http_call.py：python3 <script> POST <url> -b '<bodyJson>'
    const script = resolveHttpCallScript(context)
    const cmd = [PYTHON_BIN, script, "POST", url, "-b", bodyJson]

    let stdout = ""
    let stderr = ""
    let exitCode = 0
    try {
      const r = await runSubprocess(cmd, SUBPROCESS_TIMEOUT_MS)
      stdout = r.stdout
      stderr = r.stderr
      exitCode = r.code
    } catch (err: any) {
      return [
        `━━━ 准入案例查询失败（子进程异常）━━━`,
        `查询类型: ${args.queryType}`,
        `URL: ${url}`,
        `请求体: ${bodyJson}`,
        `耗时: ${Date.now() - startTime}ms`,
        ``,
        `错误: ${err?.message ?? String(err)}`,
        `提示: 请确认 ${PYTHON_BIN} 可用、脚本 ${script} 存在。`,
        `━━━━━━━━━━━━━━━━━━━━━━━`,
      ].join("\n")
    }

    const elapsed = Date.now() - startTime

    if (exitCode !== 0) {
      return [
        `━━━ 准入案例查询失败（http_call.py 退出码 ${exitCode}）━━━`,
        `查询类型: ${args.queryType}`,
        `URL: ${url}`,
        `请求体: ${bodyJson}`,
        `耗时: ${elapsed}ms`,
        ``,
        `stderr:`,
        stderr || "(无)",
        `━━━━━━━━━━━━━━━━━━━━━━━`,
      ].join("\n")
    }

    // 从 http_call.py 格式化输出中提取“响应体”后的 JSON
    const parsed = extractBodyJson(stdout)
    if (!parsed) {
      // 无法解析为 JSON，原样输出 http_call.py 的结果
      return [
        `━━━ 准入案例查询结果（原始）━━━`,
        `查询类型: ${args.queryType}`,
        `URL: ${url}`,
        `请求体: ${bodyJson}`,
        `耗时: ${elapsed}ms`,
        ``,
        stdout.trim(),
        `━━━━━━━━━━━━━━━━━━━━━━━`,
      ].join("\n")
    }

    // code=0 打印 data；code!=0 打印 msg
    const lines: string[] = []
    lines.push(`━━━ 准入案例查询结果 ━━━`)
    lines.push(`查询类型: ${args.queryType}`)
    lines.push(`开发部门: ${args.deptName}`)
    lines.push(`应用: ${args.appName}`)
    lines.push(`版本: ${args.editionId}`)
    lines.push(`耗时: ${elapsed}ms`)
    lines.push(``)

    const code = parsed.code
    if (code === 0 || code === "0") {
      lines.push(`状态: 成功`)
      lines.push(``)
      lines.push(`data:`)
      lines.push(formatData(parsed.data))
    } else {
      lines.push(`状态: 失败（code=${code}）`)
      lines.push(``)
      lines.push(`msg:`)
      lines.push(typeof parsed.msg === "string" ? parsed.msg : JSON.stringify(parsed.msg, null, 2))
    }
    lines.push(`━━━━━━━━━━━━━━━━━━━━━━━`)

    return lines.join("\n")
  },
})

// ============================================================
// 辅助函数
// ============================================================

/** 运行子进程，兼容 Bun（优先）与 Node 运行时。 */
async function runSubprocess(cmd: string[], timeoutMs: number): Promise<{ stdout: string; stderr: string; code: number }> {
  const anyGlobal = globalThis as any

  // 优先使用 Bun.spawn
  if (anyGlobal.Bun?.spawn) {
    const proc = anyGlobal.Bun.spawn({
      cmd,
      stdout: "pipe",
      stderr: "pipe",
    })

    let timer: any
    const timeoutPromise = new Promise<number>((resolve) => {
      timer = setTimeout(() => {
        try { proc.kill?.() } catch {}
        resolve(-1)
      }, timeoutMs)
    })

    const [stdout, stderr, code] = await Promise.all([
      new Response(proc.stdout).text(),
      new Response(proc.stderr).text(),
      Promise.race([proc.exited, timeoutPromise]),
    ])
    clearTimeout(timer)

    return { stdout: stdout ?? "", stderr: stderr ?? "", code: code ?? 0 }
  }

  // 回退：node:child_process（同步）
  const cp: any = await import("node:child_process")
  const res = cp.spawnSync(cmd[0], cmd.slice(1), {
    encoding: "utf8",
    timeout: timeoutMs,
    maxBuffer: 10 * 1024 * 1024,
  })
  return {
    stdout: res.stdout ?? "",
    stderr: res.stderr ?? "",
    code: res.status ?? 0,
  }
}

/** 从 http_call.py 格式化输出中提取“响应体”后的 JSON。 */
function extractBodyJson(stdout: string): any | null {
  const marker = "响应体:"
  const idx = stdout.indexOf(marker)
  if (idx === -1) return null
  const tail = stdout.slice(idx + marker.length)
  const sep = "━━━━━━━━━━━━━━━━━━━━"
  const endIdx = tail.lastIndexOf(sep)
  const bodyStr = (endIdx === -1 ? tail : tail.slice(0, endIdx)).trim()
  if (!bodyStr) return null
  try {
    return JSON.parse(bodyStr)
  } catch {
    return null
  }
}

/** 案例表格列定义：表头 → 数据 key */
const CASE_COLUMNS = [
  { header: "需求子条目编号", key: "itemNo" },
  { header: "测试经理", key: "tester" },
  { header: "测试组", key: "groupName" },
]

/** 格式化 data 内容用于展示（数组按表格输出）。 */
function formatData(data: any): string {
  if (data === null || data === undefined) return "(空)"
  if (typeof data === "string") return data
  if (Array.isArray(data)) {
    if (data.length === 0) return "(空数组)"
    return renderCaseTable(data)
  }
  if (typeof data === "object") {
    return JSON.stringify(data, null, 2)
  }
  return String(data)
}

/** 将案例数组渲染为 Markdown 表格。 */
function renderCaseTable(rows: any[]): string {
  const MAX_ROWS = 200
  const MAX_CELL = 80
  const lines: string[] = []
  lines.push(`共 ${rows.length} 条`)
  lines.push(``)
  lines.push(`| ${CASE_COLUMNS.map(c => c.header).join(" | ")} |`)
  lines.push(`| ${CASE_COLUMNS.map(() => "---").join(" | ")} |`)
  for (const row of rows.slice(0, MAX_ROWS)) {
    const cells = CASE_COLUMNS.map(c => {
      const v = row?.[c.key]
      let s = v === null || v === undefined ? "-" : String(v)
      s = s.replace(/\|/g, "\\|").replace(/\r?\n/g, " ")
      if (s.length > MAX_CELL) s = s.substring(0, MAX_CELL - 3) + "..."
      return s
    })
    lines.push(`| ${cells.join(" | ")} |`)
  }
  if (rows.length > MAX_ROWS) {
    lines.push(``)
    lines.push(`(共 ${rows.length} 条，仅显示前 ${MAX_ROWS} 条)`)
  }
  return lines.join("\n")
}
