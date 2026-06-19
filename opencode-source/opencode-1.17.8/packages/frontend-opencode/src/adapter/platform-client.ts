// 平台后端 HTTP 客户端：统一 fetch 封装、ApiResponse 解包、ApiErrorResponse → 统一错误。
// 后端契约：ApiResponse{success,data,traceId}；ApiErrorResponse{success:false,code,message,traceId,details}。

import { PLATFORM_API_URL, PLATFORM_API_TOKEN } from "./config"

/** 统一前端错误对象（对齐 docs/frontend/frontend-backend-contract.md） */
export type PlatformError = {
  traceId?: string
  code: string
  message: string
  retryable: boolean
  details: Record<string, unknown>
}

/** 后端可重试错误码（前端按 code 派生 retryable） */
const RETRYABLE_CODES = new Set([
  "RATE_LIMITED",
  "OPENCODE_UNAVAILABLE",
  "OPENCODE_TIMEOUT",
  "INTERNAL_ERROR",
])

export class PlatformRequestError extends Error {
  readonly error: PlatformError
  constructor(error: PlatformError) {
    super(error.message)
    this.name = "PlatformRequestError"
    this.error = error
  }
}

type ApiResponse<T> = { success: true; data: T; traceId?: string }
type ApiErrorResponse = {
  success: false
  code: string
  message: string
  traceId?: string
  details?: Record<string, unknown>
}

function headers(extra?: Record<string, string>): Record<string, string> {
  const h: Record<string, string> = { "Content-Type": "application/json" }
  if (PLATFORM_API_TOKEN) h["Authorization"] = `Bearer ${PLATFORM_API_TOKEN}`
  return { ...h, ...extra }
}

function toError(body: ApiErrorResponse, fallbackTrace?: string): PlatformError {
  const code = body.code ?? "INTERNAL_ERROR"
  return {
    traceId: body.traceId ?? fallbackTrace,
    code,
    message: body.message ?? "请求失败",
    retryable: RETRYABLE_CODES.has(code),
    details: body.details ?? {},
  }
}

/** 发起 JSON 请求并解包 ApiResponse.data；失败抛 PlatformRequestError */
export async function request<T>(
  path: string,
  init: { method: "GET" | "POST" | "PUT" | "DELETE"; body?: unknown; signal?: AbortSignal } = {
    method: "GET",
  },
): Promise<T> {
  const url = `${PLATFORM_API_URL}${path}`
  let res: Response
  try {
    res = await fetch(url, {
      method: init.method,
      headers: headers(),
      body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
      signal: init.signal,
    })
  } catch (err) {
    throw new PlatformRequestError({
      code: "OPENCODE_UNAVAILABLE",
      message: "无法连接平台后端",
      retryable: true,
      details: { cause: String(err) },
    })
  }

  const traceId = res.headers.get("X-Trace-Id") ?? undefined
  let json: unknown
  try {
    json = await res.json()
  } catch {
    throw new PlatformRequestError({
      traceId,
      code: "INTERNAL_ERROR",
      message: `平台后端返回非 JSON（HTTP ${res.status}）`,
      retryable: true,
      details: {},
    })
  }

  if (!res.ok) {
    const errBody = json as Partial<ApiErrorResponse>
    throw new PlatformRequestError(
      toError(
        {
          success: false,
          code: errBody.code ?? "INTERNAL_ERROR",
          message: errBody.message ?? `请求失败（HTTP ${res.status}）`,
          traceId: errBody.traceId ?? traceId,
          details: errBody.details,
        },
        traceId,
      ),
    )
  }

  const ok = json as ApiResponse<T>
  if (!ok || ok.success !== true || ok.data === undefined) {
    const errBody = json as Partial<ApiErrorResponse>
    if (errBody && errBody.success === false) {
      throw new PlatformRequestError(
        toError(
          {
            success: false,
            code: errBody.code ?? "INTERNAL_ERROR",
            message: errBody.message ?? "请求失败",
            traceId: errBody.traceId ?? traceId,
            details: errBody.details,
          },
          traceId,
        ),
      )
    }
    throw new PlatformRequestError({
      traceId,
      code: "INTERNAL_ERROR",
      message: "平台后端响应格式异常",
      retryable: false,
      details: {},
    })
  }
  return ok.data
}
