import { tool } from "./tool-compat"

/** 模型只能读取当前问题签发的审阅范围，文件正文始终走平台文件 WebSocket。 */
export default tool({
  description: "按需只读管理视图的应用/成员文件。必须使用本轮提供的 scopeId。先 list 或 search，再用返回的 contentVersion 分段 read；遇到 latestUncertain 必须请用户选择成员，不能猜测最新。不得改写、删除、执行成员文件。",
  args: {
    scopeId: tool.schema.string().describe("本轮管理对话给出的审阅范围 ID，不是登录凭据"),
    action: tool.schema.enum(["list", "search", "read"]),
    path: tool.schema.string().optional().describe("范围内相对目录或文件路径；根目录为空字符串"),
    query: tool.schema.string().optional().describe("按相对路径检索文件"),
    remainingDirectories: tool.schema.array(tool.schema.string()).optional().describe("搜索返回的后续目录游标"),
    contentVersion: tool.schema.string().optional().describe("list/search 返回的 selected.file.contentVersion，read 时必须原样携带"),
    offset: tool.schema.number().int().min(0).optional().describe("read 返回的 chunk.nextOffset；首段为 0"),
  },
  async execute(input, context) {
    const base = process.env.TEST_AGENT_PLATFORM_BASE_URL?.replace(/\/$/, "")
    const token = process.env.TEST_AGENT_TEAM_REVIEW_TOOL_TOKEN
    if (!base || !token) throw new Error("审阅 Tool 未配置，请通过平台受管流程重启当前用户进程")
    context.metadata({ title: `只读审阅：${input.action} ${input.path ?? ""}`, metadata: { action: input.action } })
    const response = await fetch(`${base}/api/internal/agent/opencode/team-review-tool/ticket`, {
      method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ scopeId: input.scopeId, sessionId: context.sessionID }),
      signal: AbortSignal.any([context.abort, AbortSignal.timeout(30_000)]),
    })
    const envelope = await response.json() as { data?: { baseUrl: string; webSocketUrl: string; origin: string }; code?: string }
    if (!response.ok || !envelope.data) return JSON.stringify({ success: false, code: envelope.code ?? "UNAVAILABLE" })
    const connection = envelope.data
    // OpenCode Tool 运行于 Bun；自定义 Origin 必须来自服务器允许列表，不关闭 Origin 校验。
    const Socket = globalThis.WebSocket as unknown as {
      new (url: string, options: { headers: Record<string, string> }): WebSocket
    }
    const socket = new Socket(connection.baseUrl.replace(/^http/, "ws") + connection.webSocketUrl,
      { headers: { Origin: connection.origin } })
    return await new Promise<string>((resolve, reject) => {
      let settled = false
      const finish = (error?: Error, result?: string) => {
        if (settled) return
        settled = true; clearTimeout(timer); context.abort.removeEventListener("abort", abort); socket.close()
        if (error) reject(error); else resolve(result ?? "")
      }
      const abort = () => finish(new Error("审阅读取已取消"))
      const timer = setTimeout(() => finish(new Error("审阅读取超时")), 120_000)
      context.abort.addEventListener("abort", abort, { once: true })
      if (context.abort.aborted) { abort(); return }
      socket.onopen = () => socket.send(JSON.stringify({ id: "review", op: `team.review.${input.action}`, params: {
        path: input.path ?? "", query: input.query, remainingDirectories: input.remainingDirectories,
        contentVersion: input.contentVersion, offset: input.offset ?? 0,
      } }))
      socket.onmessage = (event) => {
        const text = String(event.data)
        if (text.length > 8 * 1024 * 1024) { finish(new Error("审阅结果超过预算")); return }
        finish(undefined, text)
      }
      socket.onerror = () => finish(new Error("审阅文件通道不可用"))
      socket.onclose = () => finish(new Error("审阅文件通道已关闭"))
    })
  },
})
