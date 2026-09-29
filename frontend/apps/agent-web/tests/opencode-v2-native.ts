/** 固定 OpenCode 2.0.18 /doc 的原生消息形状，仅供真实 E2E 取证使用。 */
export type NativeV2Message = {
  id: string;
  type: string;
  text?: string;
  content?: Array<{ type?: string; text?: string; id?: string; [key: string]: unknown }>;
  [key: string]: unknown;
};

function nativeUrl(baseUrl: string, pathname: string): URL {
  return new URL(pathname, `${baseUrl.replace(/\/$/, "")}/`);
}

/** 真实 E2E 与 Worker 共用受控密码；未配置时沿用本地无鉴权启动方式。 */
export function nativeV2AuthHeaders(): Record<string, string> {
  const password = process.env.TEST_AGENT_OPENCODE_SERVER_PASSWORD ?? process.env.OPENCODE_PASSWORD;
  return password ? { Authorization: `Basic ${Buffer.from(`opencode:${password}`).toString("base64")}` } : {};
}

function nativeData(payload: unknown): Record<string, unknown> {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    throw new Error("OpenCode V2 response is not an object");
  }
  return payload as Record<string, unknown>;
}

/** V2 消息分页使用不透明 cursor；后续页不能再带 order。 */
export async function listNativeV2Messages(
  baseUrl: string,
  sessionId: string,
  fetcher: typeof fetch = fetch
): Promise<NativeV2Message[]> {
  const pathname = `/api/session/${encodeURIComponent(sessionId)}/message`;
  const result: NativeV2Message[] = [];
  const seen = new Set<string>();
  let cursor: string | undefined;
  for (let page = 0; page < 100; page++) {
    const url = nativeUrl(baseUrl, pathname);
    url.searchParams.set("limit", "200");
    if (cursor) url.searchParams.set("cursor", cursor);
    else url.searchParams.set("order", "asc");
    const response = await fetcher(url, { headers: nativeV2AuthHeaders() });
    if (!response.ok) throw new Error(`OpenCode V2 messages failed: HTTP ${response.status}`);
    const envelope = nativeData(await response.json());
    if (!Array.isArray(envelope.data)) throw new Error("OpenCode V2 messages have no data array");
    for (const item of envelope.data) {
      if (!item || typeof item !== "object" || typeof item.id !== "string" || typeof item.type !== "string") {
        throw new Error("OpenCode V2 message is invalid");
      }
      result.push(item as NativeV2Message);
    }
    const next = nativeData(envelope.cursor).next;
    if (next === null || next === undefined || next === "") return result;
    if (typeof next !== "string" || seen.has(next)) throw new Error("OpenCode V2 message cursor is invalid or repeated");
    seen.add(next);
    cursor = next;
  }
  throw new Error("OpenCode V2 messages exceeded 100 pages");
}

export async function getNativeV2Session(baseUrl: string, sessionId: string, fetcher: typeof fetch = fetch): Promise<Record<string, unknown>> {
  const response = await fetcher(nativeUrl(baseUrl, `/api/session/${encodeURIComponent(sessionId)}`), { headers: nativeV2AuthHeaders() });
  if (!response.ok) throw new Error(`OpenCode V2 session failed: HTTP ${response.status}`);
  return nativeData(nativeData(await response.json()).data);
}

export async function patchNativeV2SessionTitle(
  baseUrl: string,
  sessionId: string,
  title: string,
  fetcher: typeof fetch = fetch
): Promise<void> {
  const response = await fetcher(nativeUrl(baseUrl, `/api/session/${encodeURIComponent(sessionId)}`), {
    method: "PATCH",
    headers: { ...nativeV2AuthHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify({ title })
  });
  if (!response.ok) throw new Error(`OpenCode V2 session title update failed: HTTP ${response.status}`);
}

export function nativeV2SessionDirectory(session: Record<string, unknown>): string | undefined {
  const location = session.location;
  if (!location || typeof location !== "object" || Array.isArray(location)) return undefined;
  const directory = (location as Record<string, unknown>).directory;
  return typeof directory === "string" ? directory : undefined;
}

/** 从源 user 到下一条 user 的时间线边界收集 assistant；V2 不再给 assistant 附 parentID。 */
export function assistantMessagesAfter(messages: NativeV2Message[], userId: string): NativeV2Message[] {
  const index = messages.findIndex((message) => message.id === userId && message.type === "user");
  if (index < 0) throw new Error(`OpenCode V2 user message ${userId} was not found`);
  const answer: NativeV2Message[] = [];
  for (const message of messages.slice(index + 1)) {
    if (message.type === "user") break;
    if (message.type === "assistant") answer.push(message);
  }
  return answer;
}

/** 与 Java client 的 V2 content 投影使用同一 ID 规则，供原生→平台 DTO 对照。 */
export function projectedNativeV2Parts(messages: NativeV2Message[], sessionId: string): Array<Record<string, unknown>> {
  return messages.flatMap((message) => {
    const content: Array<Record<string, unknown>> = message.type === "assistant"
      ? (message.content ?? []).filter((item): item is Record<string, unknown> => Boolean(item && typeof item === "object"))
      : message.type === "user"
        ? [
            ...(typeof message.text === "string" && message.text.length > 0 ? [{ type: "text", text: message.text }] : []),
            ...(Array.isArray(message.files) ? message.files.filter((item): item is Record<string, unknown> => Boolean(item && typeof item === "object"))
              .map((item) => ({ ...item, type: "file" })) : [])
          ]
        : [];
    return content.map((item, index) => {
      const id = typeof item.id === "string" && item.id.length > 0 ? item.id : `part_${message.id}_${index}`;
      const part: Record<string, unknown> = {
        ...item, id, partID: id, partId: id,
        sessionID: sessionId, sessionId,
        messageID: message.id, messageId: message.id
      };
      if (item.type === "reasoning" && item.time && typeof item.time === "object") {
        const time = item.time as Record<string, unknown>;
        part.time = { ...time, start: time.created, end: time.completed };
      }
      if (item.type === "tool") {
        part.callID = id;
        part.callId = id;
        if (typeof item.name === "string") {
          part.tool = item.name;
          part.toolName = item.name;
        }
        if (item.state && typeof item.state === "object") {
          const state = { ...(item.state as Record<string, unknown>) };
          if (state.status === "streaming") state.status = "pending";
          if (Array.isArray(state.content)) {
            const output = state.content
              .filter((value): value is { type: string; text: string } => Boolean(value && typeof value === "object" && value.type === "text" && typeof value.text === "string"))
              .map((value) => value.text).join("\n");
            if (output) {
              state.output = output;
              part.output = output;
            }
          }
          if (item.time && typeof item.time === "object") {
            const time = item.time as Record<string, unknown>;
            state.time = { start: time.created, end: time.completed };
          }
          part.state = state;
        }
      }
      return part;
    });
  });
}
