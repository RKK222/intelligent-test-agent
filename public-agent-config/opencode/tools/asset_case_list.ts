/**
 * OpenCode Custom Tool — 资产案例列表获取（工具 ID：asset_case_list）
 *
 * 仅供 stock-case-recommendation 子 Agent 做材料准备。通过同级 http_call.py
 * 查询当前应用、当前子条目下的存量案例，并返回可机器判定完整性的 JSON。
 */

import { tool } from "@opencode-ai/plugin"
import { createHash } from "node:crypto"
import { existsSync } from "node:fs"
import { fileURLToPath } from "node:url"
import path from "node:path"

const BASE_URL_ENV = "ASSET_CASE_BASE_URL"
const DEFAULT_BASE_URL = "http://122.210.62.32:9080"
const LIST_ENDPOINT = "/tcds/cases/by-menu"
const HTTP_CALL_SCRIPT = "http_call.py"
const GLOBAL_TOOLS_DIR = path.join(process.env.HOME ?? "", ".config", "opencode", "tools")
const PYTHON_BIN = process.env.ASSET_CASE_PYTHON_BIN ?? (process.platform === "win32" ? "python" : "python3")
const SUBPROCESS_TIMEOUT_MS = 120_000
const MAX_CASES = 500
const MAX_OUTPUT_BYTES = 45 * 1024

type AssetCase = {
  caseId: string
  name: string
  data: string
  step: string
  expect: string
  aiAiCase: string
  isUpdate: string
}

type AssetCaseRequest = {
  subItemNo: string
  app: string
  path?: string
}

type SubprocessResult = {
  stdout: string
  stderr: string
  code: number
  timedOut: boolean
}

const argSchema = {
  subItemNo: tool.schema.string().min(1).max(128).describe("子条目编号，如 S20230202-000054。必填。"),
  app: tool.schema.string().min(1).max(128).describe("当前应用标识，如 F-COSS。必填。"),
  path: tool.schema.string().min(1).max(1024).optional().describe("用户指定的存量案例参考路径；按原字符串整体传递。"),
}

export default tool({
  description: [
    "【内部工具】仅供 stock-case-recommendation 子 Agent 准备材料。",
    "按应用、子条目和可选参考路径查询资产案例。",
    "每个工作单元只调用一次；返回 JSON 中 success=false 或 complete=false 时必须停止材料准备并返回 INCOMPLETE。",
  ].join("\n"),
  args: argSchema,

  async execute(args, context) {
    const fetchedAt = new Date()
    const request = {
      subItemNo: args.subItemNo.trim(),
      app: args.app.trim(),
      ...(args.path !== undefined ? { path: args.path } : {}),
    }

    let baseUrl: string
    try {
      baseUrl = resolveBaseUrl()
    } catch {
      return failure("CONFIG_ERROR", "资产案例服务地址配置无效", request, fetchedAt)
    }

    const script = resolveHttpCallScript(context)
    if (!existsSync(script)) {
      return failure("SCRIPT_NOT_FOUND", "资产案例查询脚本不可用", request, fetchedAt)
    }

    const bodyJson = JSON.stringify(request)
    const command = [PYTHON_BIN, script, "POST", baseUrl + LIST_ENDPOINT, "-b", bodyJson]
    let result: SubprocessResult
    try {
      result = await runSubprocess(command, SUBPROCESS_TIMEOUT_MS)
    } catch {
      return failure("SUBPROCESS_ERROR", "资产案例查询进程启动失败", request, fetchedAt)
    }

    if (result.timedOut) {
      return failure("TIMEOUT", "资产案例查询超时", request, fetchedAt)
    }
    if (result.code !== 0) {
      return failure("SUBPROCESS_FAILED", "资产案例查询进程执行失败", request, fetchedAt)
    }

    const response = parseHttpCallOutput(result.stdout)
    if (!response) {
      return failure("INVALID_RESPONSE", "资产案例服务返回无法解析", request, fetchedAt)
    }
    if (response.status < 200 || response.status >= 300) {
      return failure("HTTP_ERROR", "资产案例服务返回 HTTP " + response.status, request, fetchedAt)
    }

    const payload = response.body
    if (payload && typeof payload === "object" && "code" in payload && !isSuccessCode(payload.code)) {
      return failure("SERVICE_ERROR", "资产案例服务返回失败", request, fetchedAt)
    }

    const extracted = extractCaseArray(payload)
    if (!extracted) {
      return failure("INVALID_CASE_LIST", "资产案例服务未返回可识别的案例列表", request, fetchedAt)
    }
    if (extracted.items.length > MAX_CASES) {
      return failure("RESULT_TOO_LARGE", "资产案例数量超过单次上限 " + MAX_CASES, request, fetchedAt)
    }
    if (extracted.total !== null && extracted.total !== extracted.items.length) {
      return failure("PARTIAL_RESULT", "资产案例服务返回数量与总数不一致，不能据此准备材料", request, fetchedAt)
    }

    const normalized = normalizeAssetCases(extracted.items)
    if (!normalized.ok) {
      return failure("INVALID_CASE_RECORD", normalized.message, request, fetchedAt)
    }

    const materialSnapshot = createHash("sha256")
      .update(JSON.stringify({ request, cases: normalized.cases }))
      .digest("hex")
    const output = JSON.stringify({
      success: true,
      complete: true,
      fetchedAt: fetchedAt.toISOString(),
      fileTimestamp: formatLocalTimestamp(fetchedAt),
      materialSnapshot,
      request,
      count: normalized.cases.length,
      total: extracted.total ?? normalized.cases.length,
      cases: normalized.cases,
      warnings: [],
    })
    if (new TextEncoder().encode(output).byteLength > MAX_OUTPUT_BYTES) {
      return failure("RESULT_TOO_LARGE", "资产案例结果超过安全输出上限，不能保证完整传递", request, fetchedAt)
    }
    return output
  },
})

function resolveBaseUrl(): string {
  const raw = (process.env[BASE_URL_ENV] ?? DEFAULT_BASE_URL).trim()
  if (!raw) throw new Error(`${BASE_URL_ENV} is required`)
  const parsed = new URL(raw)
  if (!["http:", "https:"].includes(parsed.protocol) || parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error("invalid asset case base URL")
  }
  return raw.replace(/\/+$/, "")
}

function resolveHttpCallScript(context: any): string {
  try {
    const sibling = path.join(path.dirname(fileURLToPath(import.meta.url)), HTTP_CALL_SCRIPT)
    if (existsSync(sibling)) return sibling
  } catch {}

  if (context?.directory) {
    const local = path.join(context.directory, "tools", HTTP_CALL_SCRIPT)
    if (existsSync(local)) return local
  }
  return path.join(GLOBAL_TOOLS_DIR, HTTP_CALL_SCRIPT)
}

async function runSubprocess(command: string[], timeoutMs: number): Promise<SubprocessResult> {
  const anyGlobal = globalThis as any
  if (anyGlobal.Bun?.spawn) {
    const proc = anyGlobal.Bun.spawn({ cmd: command, stdout: "pipe", stderr: "pipe" })
    let timedOut = false
    let timer: ReturnType<typeof setTimeout> | undefined
    const timeout = new Promise<number>((resolve) => {
      timer = setTimeout(() => {
        timedOut = true
        try { proc.kill?.() } catch {}
        resolve(-1)
      }, timeoutMs)
    })
    const [stdout, stderr, code] = await Promise.all([
      new Response(proc.stdout).text(),
      new Response(proc.stderr).text(),
      Promise.race([proc.exited, timeout]),
    ])
    if (timer) clearTimeout(timer)
    return { stdout, stderr, code: code ?? -1, timedOut }
  }

  const childProcess: any = await import("node:child_process")
  const result = childProcess.spawnSync(command[0], command.slice(1), {
    encoding: "utf8",
    timeout: timeoutMs,
    maxBuffer: 10 * 1024 * 1024,
  })
  return {
    stdout: result.stdout ?? "",
    stderr: result.stderr ?? "",
    code: typeof result.status === "number" ? result.status : -1,
    timedOut: result.error?.code === "ETIMEDOUT",
  }
}

export function parseHttpCallOutput(stdout: string): { status: number; body: any } | null {
  const statusMatch = stdout.match(/状态码:\s*(\d{3})/)
  const marker = "响应体:"
  const markerIndex = stdout.indexOf(marker)
  if (!statusMatch || markerIndex < 0) return null
  const tail = stdout.slice(markerIndex + marker.length)
  const separatorIndex = tail.lastIndexOf("━━━━━━━━━━━━━━━━━━━━")
  const bodyText = (separatorIndex < 0 ? tail : tail.slice(0, separatorIndex)).trim()
  if (!bodyText) return null
  try {
    return { status: Number(statusMatch[1]), body: JSON.parse(bodyText) }
  } catch {
    return null
  }
}

function isSuccessCode(code: unknown): boolean {
  return code === 0 || code === "0" || code === 200 || code === "200"
}

function extractCaseArray(payload: any): { items: any[]; total: number | null } | null {
  let data = payload
  if (payload && typeof payload === "object" && Object.prototype.hasOwnProperty.call(payload, "data")) {
    data = payload.data ?? []
  } else if (payload && typeof payload === "object" && Object.prototype.hasOwnProperty.call(payload, "cases")) {
    data = payload.cases ?? []
  }
  let items: unknown = data
  if (!Array.isArray(items) && data && typeof data === "object") {
    items = data.records ?? data.items ?? data.list ?? data.rows ?? (hasAnyKey(data, ["caseId", "id"]) ? [data] : undefined)
  }
  if (!Array.isArray(items)) return null

  const totalCandidate = payload?.total ?? data?.total ?? data?.totalCount
  const totalNumber = Number(totalCandidate)
  return {
    items,
    total: Number.isFinite(totalNumber) && totalNumber >= 0 ? totalNumber : null,
  }
}

export function normalizeAssetCases(items: any[]): { ok: true; cases: AssetCase[] } | { ok: false; message: string } {
  const cases: AssetCase[] = []
  const ids = new Set<string>()
  for (let index = 0; index < items.length; index += 1) {
    const item = items[index]
    if (!item || typeof item !== "object") {
      return { ok: false, message: "第 " + (index + 1) + " 条资产案例不是对象" }
    }
    const caseId = toText(item.caseId ?? item.id).trim()
    const name = toText(item.name ?? item.caseName).trim()
    const hasStep = hasAnyKey(item, ["step", "steps", "testStep"])
    const hasData = hasAnyKey(item, ["data", "testData"])
    const hasExpect = hasAnyKey(item, ["expect", "expectedResult", "expected"])
    if (!caseId || !name || !hasStep || !hasData || !hasExpect) {
      return { ok: false, message: "第 " + (index + 1) + " 条资产案例缺少 caseId、name、step、data 或 expect" }
    }
    if (ids.has(caseId)) {
      return { ok: false, message: "资产案例编号重复：" + caseId }
    }
    ids.add(caseId)
    cases.push({
      caseId,
      name,
      data: toText(item.data ?? item.testData),
      step: toText(item.step ?? item.steps ?? item.testStep),
      expect: toText(item.expect ?? item.expectedResult ?? item.expected),
      aiAiCase: toText(item.aiAiCase ?? item.isAiCase),
      isUpdate: toText(item.isUpdate),
    })
  }
  return { ok: true, cases }
}

function hasAnyKey(value: Record<string, unknown>, keys: string[]): boolean {
  return keys.some((key) => Object.prototype.hasOwnProperty.call(value, key))
}

function toText(value: unknown): string {
  if (value === null || value === undefined) return ""
  if (typeof value === "string") return value
  if (["number", "boolean", "bigint"].includes(typeof value)) return String(value)
  try { return JSON.stringify(value) } catch { return String(value) }
}

export function formatLocalTimestamp(date: Date): string {
  const part = (value: number) => String(value).padStart(2, "0")
  return [
    date.getFullYear(),
    part(date.getMonth() + 1),
    part(date.getDate()),
    part(date.getHours()),
    part(date.getMinutes()),
    part(date.getSeconds()),
  ].join("-")
}

function failure(errorCode: string, message: string, request: AssetCaseRequest, fetchedAt: Date): string {
  return JSON.stringify({
    success: false,
    complete: false,
    errorCode,
    message,
    fetchedAt: fetchedAt.toISOString(),
    fileTimestamp: formatLocalTimestamp(fetchedAt),
    request,
    count: 0,
    total: null,
    cases: [],
  })
}
