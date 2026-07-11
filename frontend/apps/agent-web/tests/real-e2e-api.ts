export type ApiEnvelope<T> =
  | { success: true; traceId: string; data: T }
  | { success: false; traceId: string; error: { code: string; message: string } };

export type RealE2eApiOptions = {
  baseUrl?: string;
  fetcher?: typeof fetch;
  token?: string;
  traceId?: string;
};

export type RequestEnvelopeOptions = RealE2eApiOptions & {
  method: "GET" | "POST" | "DELETE";
  body?: unknown;
};

/**
 * 调用真实 E2E 使用的平台信封接口，并统一处理鉴权、trace 与错误脱敏。
 * 该客户端只访问平台 API，不允许用它绕过平台直连 OpenCode 服务。
 */
export async function requestEnvelope<T = unknown>(pathname: string, options: RequestEnvelopeOptions): Promise<T> {
  const token = options.token ?? process.env.TEST_AGENT_API_TOKEN;
  const headers: Record<string, string> = {
    ...authHeaders(token),
    Accept: "application/json",
    "X-Trace-Id": options.traceId ?? createTraceId()
  };
  const init: RequestInit = { method: options.method, headers };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    init.body = JSON.stringify(options.body);
  }

  const fetcher = options.fetcher ?? fetch;
  const baseUrl = stripTrailingSlash(options.baseUrl ?? process.env.TEST_AGENT_BASE_URL ?? "http://127.0.0.1:8080");
  const response = await fetcher(`${baseUrl}${normalizePathname(pathname)}`, init);
  const payload = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;
  if (!response.ok || !payload?.success) {
    const failure = payload && "error" in payload ? payload.error : undefined;
    const message = `${response.status} ${failure?.code ?? "API_ERROR"} ${failure?.message ?? response.statusText}`;
    throw new Error(redactSecret(message, token));
  }
  return payload.data;
}

/** 发送真实平台 GET 请求。 */
export function apiGet<T = unknown>(pathname: string, options: RealE2eApiOptions = {}): Promise<T> {
  return requestEnvelope<T>(pathname, { ...options, method: "GET" });
}

/** 发送真实平台 POST JSON 请求。 */
export function apiPost<T = unknown>(pathname: string, body: unknown, options: RealE2eApiOptions = {}): Promise<T> {
  return requestEnvelope<T>(pathname, { ...options, method: "POST", body });
}

/** 发送真实平台 DELETE 请求。 */
export function apiDelete<T = unknown>(pathname: string, options: RealE2eApiOptions = {}): Promise<T> {
  return requestEnvelope<T>(pathname, { ...options, method: "DELETE" });
}

/** Bearer token 为空时不发送 Authorization，便于兼容本地免鉴权环境。 */
export function authHeaders(token = process.env.TEST_AGENT_API_TOKEN): Record<string, string> {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function stripTrailingSlash(value: string): string {
  return value.replace(/\/+$/, "");
}

function normalizePathname(pathname: string): string {
  return pathname.startsWith("/") ? pathname : `/${pathname}`;
}

function createTraceId(): string {
  return `trace_real_e2e_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

function redactSecret(message: string, token: string | undefined): string {
  return token ? message.split(token).join("[REDACTED]") : message;
}
