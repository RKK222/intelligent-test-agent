export type PreparedRawOutputBody = {
  body: string;
  truncated?: boolean;
};

export const RAW_OUTPUT_MAX_ENTRIES_PER_SESSION = 2_000;

const RAW_OUTPUT_SENSITIVE_KEYS = new Set([
  "authorization",
  "accesstoken",
  "cookie",
  "contexttoken",
  "password",
  "refreshtoken",
  "secret",
  "sessiondigest",
  "setcookie",
  "ticket",
  "token"
]);

/**
 * 原始输出列表仅保留最新固定数量，避免长会话无限占用页面内存且不修改现有数组。
 */
export function appendLatestRawOutputEntry<T>(current: readonly T[], entry: T): T[] {
  return [...current, entry].slice(-RAW_OUTPUT_MAX_ENTRIES_PER_SESSION);
}

/**
 * 原始输出统一在进入页面缓存前脱敏并截断，避免 HTTP/SSE 新入口绕过安全边界。
 */
export function prepareRawOutputBody(body: string, maxLength: number): PreparedRawOutputBody {
  const redactedBody = redactSensitiveDataFromJson(body);
  if (redactedBody.length <= maxLength) {
    return { body: redactedBody };
  }
  return {
    body: `${redactedBody.slice(0, maxLength)}\n...[已截断，原始长度 ${redactedBody.length} 字符]`,
    truncated: true
  };
}

function redactSensitiveDataFromJson(body: string): string {
  try {
    return JSON.stringify(redactSensitiveData(JSON.parse(body)));
  } catch {
    // SSE data 前缀、截断 JSON 或表单文本都可能让整体解析失败；调试副本仍必须 fail-closed 脱敏。
    return redactSensitiveDataFromText(body);
  }
}

function redactSensitiveDataFromText(body: string): string {
  const keyPattern = /(["']?)\b(?:authorization|access[-_]?token|cookie|context[-_]?token|password|refresh[-_]?token|secret|session[-_]?digest|set-cookie|ticket|token)\b\1\s*[:=]\s*/gi;
  let redacted = "";
  let cursor = 0;
  let match: RegExpExecArray | null;
  while ((match = keyPattern.exec(body)) !== null) {
    redacted += body.slice(cursor, match.index) + match[0];
    const valueStart = keyPattern.lastIndex;
    const quote = body[valueStart];
    let valueEnd = valueStart;
    let closedQuote = false;
    if (quote === '"' || quote === "'") {
      valueEnd += 1;
      let escaped = false;
      while (valueEnd < body.length) {
        const current = body[valueEnd];
        if (current === "\n" || current === "\r") break;
        if (escaped) {
          escaped = false;
        } else if (current === "\\") {
          escaped = true;
        } else if (current === quote) {
          closedQuote = true;
          valueEnd += 1;
          break;
        }
        valueEnd += 1;
      }
      redacted += `${quote}[REDACTED]${closedQuote ? quote : ""}`;
    } else {
      while (valueEnd < body.length && !/[\s,;&}\]]/.test(body[valueEnd])) valueEnd += 1;
      redacted += "[REDACTED]";
    }
    cursor = valueEnd;
    keyPattern.lastIndex = valueEnd;
  }
  return redacted + body.slice(cursor);
}

function redactSensitiveData(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(redactSensitiveData);
  }
  if (!value || typeof value !== "object") {
    return value;
  }
  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>).map(([key, item]) => [
      key,
      RAW_OUTPUT_SENSITIVE_KEYS.has(key.toLowerCase().replace(/[-_]/g, ""))
        ? "[REDACTED]"
        : redactSensitiveData(item)
    ])
  );
}
