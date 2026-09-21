import { tool } from "@opencode-ai/plugin"

const ENDPOINT_PATH = "/api/internal/agent/opencode/workspace-git-tool"
const WRITE_ACTIONS = new Set([
  "pull",
  "stage",
  "unstage",
  "discard",
  "commit",
  "publish",
  "resolve_conflict",
  "abort_merge",
  "complete_merge",
])
const HIGH_RISK_ACTIONS = new Set(["discard", "publish", "resolve_conflict", "abort_merge"])

const args = {
  action: tool.schema.enum([
    "status",
    "pull",
    "stage",
    "unstage",
    "discard",
    "commit",
    "publish_preview",
    "publish",
    "conflict",
    "resolve_conflict",
    "abort_merge",
    "complete_merge",
  ]).describe("要执行的工作区 Git 操作"),
  files: tool.schema.array(tool.schema.string()).optional()
    .describe("明确选择的当前 workspace 相对文件路径；写操作不能省略"),
  message: tool.schema.string().optional().describe("commit 或 publish 使用的中文提交说明"),
  path: tool.schema.string().optional().describe("查看或解决冲突时的单个相对文件路径"),
  resolution: tool.schema.enum(["CURRENT", "INCOMING", "BOTH", "MANUAL", "DELETE"]).optional()
    .describe("冲突解决方式：个人版本、远程版本、两者拼接、手工正文或删除"),
  content: tool.schema.string().optional().describe("resolution=MANUAL 时写入的完整文件正文"),
  expectedApplicationHead: tool.schema.string().optional()
    .describe("publish_preview 返回的 applicationHead；正式发布时原样传回以防并发覆盖"),
}

function requiredEnvironment(name: string): string {
  const value = process.env[name]?.trim()
  if (!value) {
    throw new Error(`缺少 ${name}，请重新启动当前用户的 TestAgent 进程后再试`)
  }
  return value
}

function confirmationPatterns(input: {
  action: string
  files?: string[]
  path?: string
}): string[] {
  const paths = input.files?.filter(Boolean) ?? []
  if (input.path) paths.push(input.path)
  return paths.length > 0 ? paths : [input.action]
}

function safeErrorDetails(value: unknown): unknown {
  if (!value || typeof value !== "object") return undefined
  const source = value as Record<string, unknown>
  const allowed = [
    "reason",
    "files",
    "blockingFiles",
    "field",
    "resolution",
    "expectedApplicationHead",
    "actualApplicationHead",
    "currentStep",
  ]
  return Object.fromEntries(allowed.flatMap((key) => key in source ? [[key, source[key]]] : []))
}

export default tool({
  description: [
    "通过平台受控接口处理当前对话绑定的个人 workspace Git 变更。",
    "支持查看变更、拉取远程、暂存、取消暂存、回退、个人提交、发布预览、提交并推送和合并冲突。",
    "必须使用本工具而不是 bash git，以保留 owner、.opencode、spec、应用同步和 dispose 规则。",
  ].join(" "),
  args,

  async execute(input, context) {
    const baseUrl = requiredEnvironment("TEST_AGENT_PLATFORM_BASE_URL").replace(/\/$/, "")
    const token = requiredEnvironment("TEST_AGENT_WORKSPACE_GIT_TOOL_TOKEN")

    if (WRITE_ACTIONS.has(input.action)) {
      await context.ask({
        permission: HIGH_RISK_ACTIONS.has(input.action)
          ? "workspace_git_destructive"
          : "workspace_git_write",
        patterns: confirmationPatterns(input),
        always: [],
        metadata: {
          action: input.action,
          files: input.files ?? [],
          path: input.path,
        },
      })
    }

    context.metadata({
      title: `工作区 Git：${input.action}`,
      metadata: { action: input.action, files: input.files ?? [], path: input.path },
    })

    const response = await fetch(`${baseUrl}${ENDPOINT_PATH}`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        sessionId: context.sessionID,
        action: input.action,
        files: input.files,
        message: input.message,
        path: input.path,
        resolution: input.resolution,
        content: input.content,
        expectedApplicationHead: input.expectedApplicationHead,
      }),
      signal: AbortSignal.any([context.abort, AbortSignal.timeout(120_000)]),
    })

    const body = await response.json().catch(() => null) as Record<string, unknown> | null
    if (!response.ok) {
      const code = typeof body?.code === "string" ? body.code : `HTTP_${response.status}`
      const message = typeof body?.message === "string" ? body.message : "平台未返回可读错误"
      const details = safeErrorDetails(body?.details)
      return JSON.stringify({ success: false, code, message, details }, null, 2)
    }

    return JSON.stringify({ success: true, data: body?.data ?? body }, null, 2)
  },
})
