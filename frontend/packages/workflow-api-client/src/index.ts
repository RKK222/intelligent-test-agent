export type WorkflowCurrentUser = {
  userId: string;
  username: string;
  unifiedAuthId: string;
  roles: string[];
};

export type WorkflowDefinition = {
  id: string;
  version: string;
  displayName: string;
  inputSchema: Record<string, unknown>;
  uiSchema: Record<string, unknown>;
  outputSchema: Record<string, unknown>;
  intentExamples: string[];
  requiredPermissions: string[];
  riskLevel: string;
  capabilities: string[];
  requiresSandbox: boolean;
};

export type WorkflowRepository = {
  id: string;
  name: string;
  englishName?: string | null;
};

export type WorkflowRepositoryGroup = {
  applicationId: string;
  applicationName: string;
  repositories: WorkflowRepository[];
};

export type WorkflowBranch = { name: string; default: boolean };

export type WorkflowMessage = {
  id: string;
  role: "user" | "assistant" | string;
  content: string;
  createdAt: string;
};

export type WorkflowConversation = {
  id: string;
  title: string;
  ownerUserId: string;
  createdAt: string;
  messages?: WorkflowMessage[];
};

export type RepositorySelection = {
  repositoryId: string;
  targetBranch: string;
  baselineBranch?: string;
};

export type ScopeSelector = {
  repositoryId: string;
  kind: "PROGRAM" | "MODULE" | "DIRECTORY" | "FILE" | "SYMBOL";
  value: string;
};

export type WorkflowStructuredInput = {
  repositories?: RepositorySelection[];
  mode?: "SINGLE" | "REVIEW";
  analyzerIds?: string[];
  scopeSelectors?: ScopeSelector[];
};

export type WorkflowSubmission = {
  messageId: string;
  assistantMessageId?: string | null;
  taskId?: string | null;
  runId?: string | null;
  status?: string | null;
  requiredInput: string[];
};

export type WorkflowReport = {
  id: string;
  taskId: string;
  runId: string;
  version: number;
  current: boolean;
  createdAt: string;
  structuredReport: Record<string, unknown>;
  markdownReport: string;
};

export type WorkflowAgUiEvent = {
  type: string;
  [key: string]: unknown;
};

export type WorkflowApiFailure = {
  code: string;
  message: string;
  traceId: string;
  details: Record<string, unknown>;
};

export class WorkflowApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly traceId?: string;
  readonly details: Record<string, unknown>;

  constructor(status: number, failure: Partial<WorkflowApiFailure>) {
    super(failure.message ?? "工作流服务调用失败");
    this.name = "WorkflowApiError";
    this.status = status;
    this.code = failure.code ?? "WORKFLOW_API_FAILED";
    this.traceId = failure.traceId;
    this.details = failure.details ?? {};
  }
}

class WorkflowSseConsumerError extends Error {
  constructor(cause: unknown) {
    super("AG-UI事件消费失败", { cause });
    this.name = "WorkflowSseConsumerError";
  }
}

export type WorkflowApiClientOptions = {
  baseUrl?: string;
  token: () => string | null;
  fetch?: typeof globalThis.fetch;
  onUnauthorized?: () => void;
};

export type WorkflowEventConnection = {
  close: () => void;
  done: Promise<void>;
  lastEventId: () => string | undefined;
};

export type WorkflowEventOptions = {
  lastEventId?: string;
  reconnect?: boolean;
  reconnectDelayMs?: number;
};

export class WorkflowApiClient {
  private readonly baseUrl: string;
  private readonly tokenProvider: () => string | null;
  private readonly fetcher: typeof globalThis.fetch;
  private readonly onUnauthorized?: () => void;

  constructor(options: WorkflowApiClientOptions) {
    this.baseUrl = (options.baseUrl ?? "").replace(/\/$/, "");
    this.tokenProvider = options.token;
    this.fetcher = options.fetch ?? globalThis.fetch.bind(globalThis);
    this.onUnauthorized = options.onUnauthorized;
  }

  me(): Promise<WorkflowCurrentUser> {
    return this.request("/workflow-api/v1/me");
  }

  definitions(): Promise<WorkflowDefinition[]> {
    return this.request("/workflow-api/v1/definitions");
  }

  repositories(): Promise<WorkflowRepositoryGroup[]> {
    return this.request("/workflow-api/v1/repositories");
  }

  branches(repositoryId: string): Promise<WorkflowBranch[]> {
    return this.request(`/workflow-api/v1/repositories/${encodeURIComponent(repositoryId)}/branches`);
  }

  conversations(ownerUserId?: string): Promise<WorkflowConversation[]> {
    const query = ownerUserId ? `?ownerUserId=${encodeURIComponent(ownerUserId)}` : "";
    return this.request(`/workflow-api/v1/conversations${query}`);
  }

  createConversation(title = "新对话"): Promise<WorkflowConversation> {
    return this.request("/workflow-api/v1/conversations", {
      method: "POST",
      body: JSON.stringify({ title }),
    });
  }

  conversation(conversationId: string): Promise<WorkflowConversation> {
    return this.request(`/workflow-api/v1/conversations/${encodeURIComponent(conversationId)}`);
  }

  submitMessage(
    conversationId: string,
    payload: { clientRequestId: string; text: string; structuredInput?: WorkflowStructuredInput },
  ): Promise<WorkflowSubmission> {
    return this.request(
      `/workflow-api/v1/conversations/${encodeURIComponent(conversationId)}/messages`,
      { method: "POST", body: JSON.stringify(payload) },
    );
  }

  cancelRun(runId: string): Promise<{ runId: string; status: string }> {
    return this.request(`/workflow-api/v1/runs/${encodeURIComponent(runId)}/cancel`, {
      method: "POST",
    });
  }

  reports(taskId: string): Promise<WorkflowReport[]> {
    return this.request(`/workflow-api/v1/tasks/${encodeURIComponent(taskId)}/reports`);
  }

  reportDownloadUrl(reportId: string): string {
    return `${this.baseUrl}/workflow-api/v1/reports/${encodeURIComponent(reportId)}/download`;
  }

  async downloadReport(reportId: string): Promise<string> {
    const response = await this.authorizedFetch(this.reportDownloadUrl(reportId));
    await this.ensureResponse(response);
    this.ensureNotHtmlResponse(response);
    return response.text();
  }

  connectEvents(
    conversationId: string,
    onEvent: (event: WorkflowAgUiEvent, id?: string) => void,
    options: WorkflowEventOptions = {},
  ): WorkflowEventConnection {
    const controller = new AbortController();
    let cursor = options.lastEventId;
    const reconnect = options.reconnect ?? true;
    const delay = options.reconnectDelayMs ?? 1_000;
    const path = `${this.baseUrl}/workflow-api/v1/conversations/${encodeURIComponent(conversationId)}/events`;

    const done = (async () => {
      while (!controller.signal.aborted) {
        try {
          const headers: Record<string, string> = { Accept: "text/event-stream" };
          const token = this.requiredToken();
          headers.Authorization = `Bearer ${token}`;
          if (cursor) headers["Last-Event-ID"] = cursor;
          const response = await this.fetcher(path, { headers, signal: controller.signal });
          await this.ensureResponse(response);
          this.ensureEventStreamResponse(response);
          if (!response.body) throw new WorkflowApiError(502, { code: "SSE_BODY_MISSING" });
          await consumeSse(response.body, (event, id) => {
            if (id) cursor = id;
            onEvent(event, id);
          }, controller.signal);
        } catch (error) {
          if (controller.signal.aborted) return;
          // 网络断开和服务端瞬时故障按最后一个持久事件续传；认证、权限和协议错误立即上抛。
          if (!reconnect || !isReconnectableSseFailure(error)) throw error;
        }
        if (!reconnect || controller.signal.aborted) return;
        await abortableDelay(delay, controller.signal);
      }
    })();

    return {
      close: () => controller.abort(),
      done,
      lastEventId: () => cursor,
    };
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers);
    if (init.body !== undefined) headers.set("Content-Type", "application/json");
    const response = await this.authorizedFetch(`${this.baseUrl}${path}`, { ...init, headers });
    await this.ensureResponse(response);
    const envelope = await this.parseJsonResponse(response);
    if (!isRecord(envelope) || !Object.hasOwn(envelope, "data")) {
      throw this.invalidResponse(response);
    }
    return envelope.data as T;
  }

  private authorizedFetch(url: string, init: RequestInit = {}): Promise<Response> {
    const headers = new Headers(init.headers);
    headers.set("Authorization", `Bearer ${this.requiredToken()}`);
    return this.fetcher(url, { ...init, headers });
  }

  private requiredToken(): string {
    const token = this.tokenProvider();
    if (!token) {
      this.onUnauthorized?.();
      throw new WorkflowApiError(401, { code: "UNAUTHENTICATED", message: "登录状态已失效" });
    }
    return token;
  }

  private async ensureResponse(response: Response): Promise<void> {
    if (response.ok) return;
    if (response.status === 401) this.onUnauthorized?.();
    let failure: Partial<WorkflowApiFailure> = {};
    try {
      const payload = await response.json() as unknown;
      if (isRecord(payload)) failure = payload;
    } catch {
      failure = {
        code: "WORKFLOW_API_UNAVAILABLE",
        message: `工作流服务暂不可用（HTTP ${response.status}）`,
        details: { contentType: response.headers.get("Content-Type") ?? "" },
      };
    }
    throw new WorkflowApiError(response.status, failure);
  }

  private async parseJsonResponse(response: Response): Promise<unknown> {
    try {
      return await response.json() as unknown;
    } catch {
      throw this.invalidResponse(response);
    }
  }

  private ensureEventStreamResponse(response: Response): void {
    const contentType = response.headers.get("Content-Type")?.toLowerCase() ?? "";
    if (!contentType.startsWith("text/event-stream")) throw this.invalidResponse(response);
  }

  private ensureNotHtmlResponse(response: Response): void {
    // 反向代理误路由和Vite SPA fallback都可能以2xx返回HTML，下载接口不能把它保存成Markdown。
    const contentType = response.headers.get("Content-Type")?.toLowerCase() ?? "";
    if (contentType.startsWith("text/html")) throw this.invalidResponse(response);
  }

  private invalidResponse(response: Response): WorkflowApiError {
    return new WorkflowApiError(502, {
      code: "WORKFLOW_API_INVALID_RESPONSE",
      message: "工作流服务响应格式异常，请检查 /workflow-api/ 路由和 Python 服务",
      details: {
        upstreamStatus: response.status,
        contentType: response.headers.get("Content-Type") ?? "",
      },
    });
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

export async function consumeSse(
  stream: ReadableStream<Uint8Array>,
  onEvent: (event: WorkflowAgUiEvent, id?: string) => void,
  signal?: AbortSignal,
): Promise<void> {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let data: string[] = [];
  let id: string | undefined;
  const dispatch = () => {
    if (data.length === 0) return;
    const parsed = JSON.parse(data.join("\n")) as WorkflowAgUiEvent;
    try {
      onEvent(parsed, id);
    } catch (error) {
      throw new WorkflowSseConsumerError(error);
    }
    data = [];
    id = undefined;
  };
  try {
    while (!signal?.aborted) {
      const chunk = await reader.read();
      if (chunk.done) break;
      buffer += decoder.decode(chunk.value, { stream: true }).replace(/\r\n/g, "\n");
      let boundary = buffer.indexOf("\n");
      while (boundary >= 0) {
        const line = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 1);
        if (line === "") dispatch();
        else if (line.startsWith("data:")) data.push(line.slice(5).trimStart());
        else if (line.startsWith("id:")) id = line.slice(3).trim();
        boundary = buffer.indexOf("\n");
      }
    }
    if (signal?.aborted) {
      await reader.cancel();
      return;
    }
    buffer += decoder.decode();
    if (buffer.startsWith("data:")) data.push(buffer.slice(5).trimStart());
    dispatch();
  } catch (error) {
    // reducer或协议解析失败属于终止错误；显式取消响应体，避免只解锁reader后遗留连接。
    try {
      await reader.cancel(error);
    } catch {
      // 保留最初错误，取消失败不能遮蔽真正的终止原因。
    }
    throw error;
  } finally {
    reader.releaseLock();
  }
}

function abortableDelay(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    if (signal.aborted) {
      resolve();
      return;
    }
    const onAbort = () => {
      globalThis.clearTimeout(timeout);
      resolve();
    };
    const timeout = globalThis.setTimeout(() => {
      signal.removeEventListener("abort", onAbort);
      resolve();
    }, milliseconds);
    signal.addEventListener("abort", onAbort, { once: true });
  });
}

function isReconnectableSseFailure(error: unknown): boolean {
  if (error instanceof SyntaxError || error instanceof WorkflowSseConsumerError) return false;
  if (error instanceof WorkflowApiError) {
    return error.status === 429 || error.status >= 500;
  }
  return true;
}
