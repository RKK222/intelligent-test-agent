import { createHash } from "node:crypto"
import { appendFile, lstat, mkdir, readFile, realpath, stat } from "node:fs/promises"
import path from "node:path"
import {
  chromium,
  type Browser,
  type BrowserContext,
  type ConnectOverCDPTransport,
  type Locator,
  type Page,
} from "playwright-core"
import { tool, type ToolContext } from "@opencode-ai/plugin"

const MAX_SESSIONS = 4
const SESSION_IDLE_MS = 30 * 60_000
const BROWSER_IDLE_MS = 5 * 60_000
const SNAPSHOT_TEXT_LIMIT = 12_000
const SNAPSHOT_CONTROLS_LIMIT = 200
const CDP_CONNECT_TIMEOUT_MS = 30_000
const CDP_VERSION_RESPONSE_LIMIT = 1024 * 1024
const CDP_EARLY_MESSAGE_LIMIT = 64

type RelayStatus = {
  running: boolean
  compatible: boolean
  browserVersion: string | null
  protocolVersion: string | null
  chromiumMajor: number | null
  status: string
  cdpEndpoint: string | null
}

type Target = {
  role?: string
  name?: string
  label?: string
  placeholder?: string
  text?: string
  testId?: string
  css?: string
  exact?: boolean
}

type SessionBrowser = {
  pages: Map<string, Page>
  activeTabId: string
  lastActivity: number
  allowedOrigins: Set<string>
}

type BrowserRuntime = {
  browser: Browser | null
  context: BrowserContext | null
  sessions: Map<string, SessionBrowser>
  sequence: number
  browserIdleSince: number | null
}

const RUNTIME_KEY = Symbol.for("testagent.local-browser.runtime.v1")
const runtimeHost = globalThis as typeof globalThis & { [RUNTIME_KEY]?: BrowserRuntime }
const runtime: BrowserRuntime = runtimeHost[RUNTIME_KEY] ?? {
  browser: null,
  context: null,
  sessions: new Map(),
  sequence: 0,
  browserIdleSince: null,
}
runtimeHost[RUNTIME_KEY] = runtime
const pageSessions = new WeakMap<Page, SessionBrowser>()

const args = {
  action: tool.schema.enum([
    "status", "open", "new_tab", "navigate", "back", "forward", "reload", "snapshot", "click", "type", "select", "check",
    "hover", "press", "wait", "screenshot", "upload", "download", "tabs",
    "switch_tab", "close_tab", "close_browser",
  ]).describe("浏览器操作"),
  url: tool.schema.string().max(8_192).optional().describe("open/navigate 使用的 HTTP(S) 地址"),
  tabId: tool.schema.string().max(80).optional().describe("要切换或关闭的当前 Session 标签页 ID"),
  target: tool.schema.object({
    role: tool.schema.string().max(80).optional(),
    name: tool.schema.string().max(500).optional(),
    label: tool.schema.string().max(500).optional(),
    placeholder: tool.schema.string().max(500).optional(),
    text: tool.schema.string().max(1_000).optional(),
    testId: tool.schema.string().max(500).optional(),
    css: tool.schema.string().max(2_000).optional(),
    exact: tool.schema.boolean().optional(),
  }).optional().describe("语义定位；优先 role/name、label、placeholder、text、testId，CSS 仅兜底"),
  value: tool.schema.string().max(100_000).optional().describe("输入、选择或按键值；不会出现在 Tool 输出或日志元数据中"),
  checked: tool.schema.boolean().optional().describe("check 操作的目标勾选状态"),
  relativePath: tool.schema.string().max(4_096).optional().describe("upload 使用的当前工作区相对文件"),
  timeoutMs: tool.schema.number().int().min(100).max(120_000).optional().default(30_000),
}

export default tool({
  description: [
    "在当前用户的 TestAgent 专用 360 浏览器 profile 中执行受控页面操作。",
    "只使用语义定位和当前 workspace 相对文件；不得通过 bash、curl 或日常浏览器 profile 绕过授权。",
    "snapshot 只返回有界可见文本和控件结构，截图与下载只保存为 workspace 相对产物。",
  ].join(" "),
  args,

  async execute(input, context) {
    await cleanupIdleSessions()
    context.metadata({
      title: `本地浏览器：${input.action}`,
      metadata: { action: input.action, tabId: input.tabId ?? null },
    })

    if (input.action === "status") {
      return result({ status: await relay("status", "GET") })
    }
    if (input.action === "close_browser") {
      await closeSession(context.sessionID)
      if (runtime.sessions.size === 0) await stopBrowser()
      return result({ status: "CLOSED" })
    }

    const session = await ensureSession(context.sessionID)
    session.lastActivity = Date.now()
    const page = activePage(session)
    const timeout = input.timeoutMs ?? 30_000

    switch (input.action) {
      case "open":
      case "navigate": {
        const url = requiredUrl(input.url)
        await authorizeOrigin(context, session, url)
        await page.goto(url, { waitUntil: "domcontentloaded", timeout })
        return pageResult(page, session, true)
      }
      case "new_tab": {
        const next = await runtime.context!.newPage()
        const tabId = registerPage(context.sessionID, session, next)
        session.activeTabId = tabId
        if (input.url) {
          const url = requiredUrl(input.url)
          await authorizeOrigin(context, session, url)
          await next.goto(url, { waitUntil: "domcontentloaded", timeout })
        }
        return pageResult(next, session, true)
      }
      case "back":
        await authorizeCurrentOrigin(context, session, page)
        await page.goBack({ waitUntil: "domcontentloaded", timeout })
        return pageResult(page, session, true)
      case "forward":
        await authorizeCurrentOrigin(context, session, page)
        await page.goForward({ waitUntil: "domcontentloaded", timeout })
        return pageResult(page, session, true)
      case "reload":
        await authorizeCurrentOrigin(context, session, page)
        await page.reload({ waitUntil: "domcontentloaded", timeout })
        return pageResult(page, session, true)
      case "snapshot":
        return pageResult(page, session, true)
      case "click": {
        const locator = locate(page, input.target)
        await authorizeCurrentOrigin(context, session, page)
        await authorizeLinkTarget(context, session, page, locator)
        await authorizeSubmit(context, page, locator)
        await locator.click({ timeout })
        await page.waitForLoadState("domcontentloaded", { timeout: Math.min(timeout, 10_000) }).catch(() => undefined)
        return pageResult(page, session, true)
      }
      case "type": {
        const locator = locate(page, input.target)
        await authorizeCurrentOrigin(context, session, page)
        await locator.fill(requiredValue(input.value), { timeout })
        return pageResult(page, session, false)
      }
      case "select": {
        const locator = locate(page, input.target)
        await authorizeCurrentOrigin(context, session, page)
        await locator.selectOption(requiredValue(input.value), { timeout })
        return pageResult(page, session, false)
      }
      case "check": {
        const locator = locate(page, input.target)
        await authorizeCurrentOrigin(context, session, page)
        if (input.checked === false) await locator.uncheck({ timeout })
        else await locator.check({ timeout })
        return pageResult(page, session, false)
      }
      case "hover":
        await authorizeCurrentOrigin(context, session, page)
        await locate(page, input.target).hover({ timeout })
        return pageResult(page, session, false)
      case "press":
        await authorizeCurrentOrigin(context, session, page)
        {
          const locator = locate(page, input.target)
          const key = requiredValue(input.value)
          if (/^(?:Enter|NumpadEnter)$/.test(key)) await authorizeSubmit(context, page, locator, true)
          await locator.press(key, { timeout })
        }
        return pageResult(page, session, false)
      case "wait":
        if (input.target) await locate(page, input.target).waitFor({ state: "visible", timeout })
        else await page.waitForLoadState("networkidle", { timeout })
        return pageResult(page, session, true)
      case "screenshot": {
        await authorizeCurrentOrigin(context, session, page)
        const relative = await artifactPath(context.directory, "browser-artifacts", context.sessionID, "png")
        await page.screenshot({ path: path.join(context.directory, relative), fullPage: true })
        return result({ tabId: session.activeTabId, artifact: relative, url: safeUrl(page.url()) })
      }
      case "upload": {
        await authorizeCurrentOrigin(context, session, page)
        const relative = requiredRelativePath(input.relativePath)
        const upload = await workspaceFile(context.directory, relative)
        await context.ask({
          permission: "local_browser_upload",
          patterns: [origin(page.url()), relative],
          always: [],
          metadata: { origin: origin(page.url()), relativePath: relative },
        })
        await locate(page, input.target).setInputFiles(upload, { timeout })
        return result({ tabId: session.activeTabId, uploaded: relative, url: safeUrl(page.url()) })
      }
      case "download": {
        await authorizeCurrentOrigin(context, session, page)
        await context.ask({
          permission: "local_browser_download",
          patterns: [origin(page.url())],
          always: [],
          metadata: { origin: origin(page.url()) },
        })
        const locator = locate(page, input.target)
        await authorizeSubmit(context, page, locator)
        const [download] = await Promise.all([
          page.waitForEvent("download", { timeout }),
          locator.click({ timeout }),
        ])
        const relative = await downloadPath(context.directory, context.sessionID, download.suggestedFilename())
        await download.saveAs(path.join(context.directory, relative))
        return result({ tabId: session.activeTabId, download: relative, url: safeUrl(page.url()) })
      }
      case "tabs":
        return result({ tabs: await tabList(session), activeTabId: session.activeTabId })
      case "switch_tab": {
        const tabId = requiredTab(input.tabId, session)
        session.activeTabId = tabId
        await activePage(session).bringToFront()
        return pageResult(activePage(session), session, true)
      }
      case "close_tab": {
        const tabId = requiredTab(input.tabId ?? session.activeTabId, session)
        const closing = session.pages.get(tabId)!
        session.pages.delete(tabId)
        await closing.close()
        if (session.pages.size === 0) {
          runtime.sessions.delete(context.sessionID)
          runtime.browserIdleSince = Date.now()
          return result({ status: "SESSION_CLOSED" })
        }
        session.activeTabId = session.pages.keys().next().value as string
        return pageResult(activePage(session), session, true)
      }
      default:
        throw new Error("不支持的浏览器操作")
    }
  },
})

async function ensureSession(sessionId: string): Promise<SessionBrowser> {
  await ensureConnected()
  const existing = runtime.sessions.get(sessionId)
  if (existing) return existing
  if (runtime.sessions.size >= MAX_SESSIONS) {
    throw new Error("本地浏览器已有 4 个并行 Session，请关闭或等待空闲任务")
  }
  const context = runtime.context!
  const claimed = runtime.sessions.size === 0
    ? context.pages().find(page => page.url() === "about:blank")
    : undefined
  const page = claimed ?? await context.newPage()
  const session: SessionBrowser = {
    pages: new Map(),
    activeTabId: "",
    lastActivity: Date.now(),
    allowedOrigins: new Set(),
  }
  runtime.sessions.set(sessionId, session)
  const tabId = registerPage(sessionId, session, page)
  session.activeTabId = tabId
  runtime.browserIdleSince = null
  return session
}

function registerPage(sessionId: string, session: SessionBrowser, page: Page): string {
  const id = nextTabId()
  session.pages.set(id, page)
  pageSessions.set(page, session)
  page.on("popup", popup => {
    const popupId = registerPage(sessionId, session, popup)
    session.activeTabId = popupId
    session.lastActivity = Date.now()
  })
  page.on("close", () => removeClosedPages(sessionId, session))
  return id
}

async function ensureConnected(): Promise<void> {
  if (runtime.browser?.isConnected() && runtime.context) return
  const status = await relay("start", "POST")
  if (!status.compatible || !status.cdpEndpoint) {
    throw new Error("本地 360 浏览器未通过兼容性自检")
  }
  const transport = await connectNativeCdpTransport(status.cdpEndpoint, CDP_CONNECT_TIMEOUT_MS)
  try {
    // OpenCode 单文件程序运行在 Bun；使用其原生 WebSocket，避开 Playwright Node ws transport
    // 在企业 360 完成 HTTP 101 后无法进入 open 的兼容问题。
    runtime.browser = await chromium.connectOverCDP(transport, {
      timeout: CDP_CONNECT_TIMEOUT_MS,
      isLocal: true,
    })
  } catch (error) {
    transport.close()
    throw error
  }
  runtime.context = runtime.browser.contexts()[0]
  if (!runtime.context) throw new Error("360 浏览器未提供默认自动化上下文")
  // 主文档导航在发出网络请求前校验 Session 已授权 origin；未知 JS 跳转和 popup 会被阻断。
  await runtime.context.route("**/*", async route => {
    const request = route.request()
    if (!request.isNavigationRequest() || request.frame() !== request.frame().page().mainFrame()) {
      await route.continue()
      return
    }
    const page = request.frame().page()
    let session = pageSessions.get(page)
    if (!session) {
      const opener = await page.opener().catch(() => null)
      if (opener) session = pageSessions.get(opener)
      if (session) pageSessions.set(page, session)
    }
    const targetOrigin = httpOrigin(request.url())
    if (session && targetOrigin && !session.allowedOrigins.has(targetOrigin)) {
      await route.abort("blockedbyclient")
      return
    }
    await route.continue()
  })
  runtime.browser.on("disconnected", () => resetRuntime())
}

/**
 * 复用 Playwright 公开的 ConnectOverCDPTransport 扩展点，但由 Bun 原生 WebSocket 建立连接。
 * 端点必须来自本地客户端已鉴权 Relay，并再次限制为同一 127.0.0.1 随机端口。
 */
export async function connectNativeCdpTransport(
  cdpEndpoint: string,
  timeoutMs = CDP_CONNECT_TIMEOUT_MS,
): Promise<ConnectOverCDPTransport> {
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 100 || timeoutMs > 120_000) {
    throw new Error("浏览器 CDP 连接超时参数无效")
  }
  const webSocketUrl = await resolveBrowserWebSocketUrl(cdpEndpoint, timeoutMs)
  return NativeCdpTransport.connect(webSocketUrl, timeoutMs)
}

/** 原生 WebSocket 与 Playwright transport 之间只传递 JSON 对象，不暴露页面或端点信息。 */
class NativeCdpTransport implements ConnectOverCDPTransport {
  private messageHandler: ((message: object) => void) | undefined
  private closeHandler: ((reason?: string) => void) | undefined
  private readonly queuedMessages: object[] = []
  private closed = false
  private closeReason = ""

  private constructor(private readonly socket: WebSocket) {
    socket.addEventListener("message", event => this.handleMessage(event))
    socket.addEventListener("error", () => this.fail("浏览器 CDP WebSocket 异常"))
    socket.addEventListener("close", event => this.notifyClosed(event.reason || this.closeReason))
  }

  static async connect(webSocketUrl: string, timeoutMs: number): Promise<NativeCdpTransport> {
    let socket: WebSocket
    try {
      socket = new WebSocket(webSocketUrl)
    } catch {
      throw new Error("浏览器 CDP WebSocket 创建失败")
    }
    const transport = new NativeCdpTransport(socket)
    await transport.waitForOpen(timeoutMs)
    return transport
  }

  get onmessage(): ((message: object) => void) | undefined {
    return this.messageHandler
  }

  set onmessage(handler: ((message: object) => void) | undefined) {
    this.messageHandler = handler
    if (!handler) return
    for (const message of this.queuedMessages.splice(0)) this.deliverMessage(handler, message)
  }

  get onclose(): ((reason?: string) => void) | undefined {
    return this.closeHandler
  }

  set onclose(handler: ((reason?: string) => void) | undefined) {
    this.closeHandler = handler
    if (handler && this.closed) handler(this.closeReason)
  }

  send(message: object): void {
    if (this.socket.readyState !== WebSocket.OPEN) {
      throw new Error("浏览器 CDP WebSocket 未连接")
    }
    this.socket.send(JSON.stringify(message))
  }

  close(): void {
    if (this.socket.readyState === WebSocket.CONNECTING || this.socket.readyState === WebSocket.OPEN) {
      this.socket.close()
      return
    }
    this.notifyClosed(this.closeReason)
  }

  private waitForOpen(timeoutMs: number): Promise<void> {
    return new Promise((resolve, reject) => {
      let settled = false
      const finish = (failure?: Error) => {
        if (settled) return
        settled = true
        clearTimeout(timer)
        if (failure) reject(failure)
        else resolve()
      }
      const timer = setTimeout(() => {
        this.closeReason = "浏览器 CDP WebSocket 连接超时"
        this.close()
        finish(new Error(this.closeReason))
      }, timeoutMs)
      this.socket.addEventListener("open", () => finish(), { once: true })
      this.socket.addEventListener("error", () => finish(new Error("浏览器 CDP WebSocket 连接失败")), { once: true })
      this.socket.addEventListener("close", () => finish(new Error("浏览器 CDP WebSocket 提前关闭")), { once: true })
    })
  }

  private handleMessage(event: MessageEvent): void {
    if (typeof event.data !== "string") {
      this.fail("浏览器 CDP 返回了非文本消息")
      return
    }
    try {
      const message = JSON.parse(event.data) as object
      if (!message || typeof message !== "object" || Array.isArray(message)) {
        throw new Error("invalid CDP payload")
      }
      if (this.messageHandler) this.deliverMessage(this.messageHandler, message)
      else if (this.queuedMessages.length < CDP_EARLY_MESSAGE_LIMIT) this.queuedMessages.push(message)
      else this.fail("浏览器 CDP 提前消息超过上限")
    } catch {
      this.fail("浏览器 CDP 返回了无效消息")
    }
  }

  private deliverMessage(handler: (message: object) => void, message: object): void {
    try {
      handler(message)
    } catch {
      this.fail("浏览器 CDP 消息处理失败")
    }
  }

  private fail(reason: string): void {
    this.closeReason = reason
    if (this.socket.readyState === WebSocket.CONNECTING || this.socket.readyState === WebSocket.OPEN) {
      this.socket.close()
    } else {
      this.notifyClosed(reason)
    }
  }

  private notifyClosed(reason: string): void {
    if (this.closed) return
    this.closed = true
    this.closeReason = reason
    this.closeHandler?.(reason)
  }
}

/** 从 360 的有界版本响应取得 browser WebSocket，并锁定为 Relay 已确认的同一 loopback 端口。 */
async function resolveBrowserWebSocketUrl(cdpEndpoint: string, timeoutMs: number): Promise<string> {
  let base: URL
  try {
    base = new URL(cdpEndpoint)
  } catch {
    throw new Error("浏览器 CDP 地址无效")
  }
  if (base.protocol !== "http:"
    || base.hostname !== "127.0.0.1"
    || !base.port
    || base.username
    || base.password
    || base.pathname !== "/"
    || base.search
    || base.hash) {
    throw new Error("浏览器 CDP 地址不在本机受控范围")
  }

  const versionUrl = new URL("/json/version", base)
  let response: Response
  let body: string
  try {
    response = await fetch(versionUrl, {
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(timeoutMs),
    })
    body = await response.text()
  } catch {
    throw new Error("浏览器 CDP 版本请求失败")
  }
  if (!response.ok || body.length < 2 || body.length > CDP_VERSION_RESPONSE_LIMIT) {
    throw new Error("浏览器 CDP 版本响应无效")
  }

  let rawWebSocketUrl: unknown
  try {
    rawWebSocketUrl = (JSON.parse(body) as Record<string, unknown>).webSocketDebuggerUrl
  } catch {
    throw new Error("浏览器 CDP 版本响应无效")
  }
  if (typeof rawWebSocketUrl !== "string") {
    throw new Error("浏览器 CDP 未提供 WebSocket 地址")
  }

  let webSocketUrl: URL
  try {
    webSocketUrl = new URL(rawWebSocketUrl)
  } catch {
    throw new Error("浏览器 CDP WebSocket 地址无效")
  }
  if (webSocketUrl.protocol !== "ws:"
    || webSocketUrl.hostname !== base.hostname
    || webSocketUrl.port !== base.port
    || webSocketUrl.username
    || webSocketUrl.password
    || webSocketUrl.search
    || webSocketUrl.hash
    || !/^\/devtools\/browser\/[A-Za-z0-9._-]{1,256}$/.test(webSocketUrl.pathname)) {
    throw new Error("浏览器 CDP WebSocket 地址不在本机受控范围")
  }
  return webSocketUrl.toString()
}

async function relay(action: "status" | "start" | "stop", method: "GET" | "POST"): Promise<RelayStatus> {
  const base = requiredEnvironment("TEST_AGENT_LOCAL_BROWSER_BASE_URL").replace(/\/$/, "")
  const token = requiredEnvironment("TEST_AGENT_LOCAL_BROWSER_TOKEN")
  const response = await fetch(`${base}/${action}`, {
    method,
    headers: { Authorization: `Bearer ${token}`, Accept: "application/json" },
    signal: AbortSignal.timeout(30_000),
  })
  const payload = await response.json().catch(() => null) as {
    success?: boolean; data?: RelayStatus; message?: string
  } | null
  if (!response.ok || payload?.success !== true || !payload.data) {
    throw new Error(typeof payload?.message === "string" ? payload.message : "本地浏览器控制不可用")
  }
  return payload.data
}

function locate(page: Page, target?: Target): Locator {
  if (!target) throw new Error("该操作缺少 target")
  const exact = target.exact ?? true
  let locator: Locator
  if (target.role) locator = page.getByRole(target.role as never, { name: target.name, exact })
  else if (target.label) locator = page.getByLabel(target.label, { exact })
  else if (target.placeholder) locator = page.getByPlaceholder(target.placeholder, { exact })
  else if (target.text) locator = page.getByText(target.text, { exact })
  else if (target.testId) locator = page.getByTestId(target.testId)
  else if (target.css) locator = page.locator(target.css)
  else throw new Error("target 必须提供一种语义定位字段")
  return locator.first()
}

async function authorizeOrigin(context: ToolContext, session: SessionBrowser, rawUrl: string) {
  const allowedOrigin = origin(rawUrl)
  await context.ask({
    permission: "local_browser_site",
    patterns: [allowedOrigin],
    always: [allowedOrigin],
    metadata: { origin: allowedOrigin },
  })
  session.allowedOrigins.add(allowedOrigin)
}

async function authorizeCurrentOrigin(context: ToolContext, session: SessionBrowser, page: Page) {
  if (page.url() === "about:blank") throw new Error("请先打开 HTTP(S) 页面")
  await authorizeOrigin(context, session, page.url())
}

async function authorizeLinkTarget(
  context: ToolContext, session: SessionBrowser, page: Page, locator: Locator,
) {
  const href = await locator.getAttribute("href").catch(() => null)
  if (!href) return
  const target = new URL(href, page.url())
  if (target.origin !== origin(page.url())) await authorizeOrigin(context, session, target.toString())
}

async function authorizeSubmit(
  context: ToolContext, page: Page, locator: Locator, includeFormAncestor = false,
) {
  const submit = await locator.evaluate((element, includeAncestor) => {
    const node = element as HTMLElement
    return (node instanceof HTMLButtonElement && (node.type || "submit") === "submit")
      || (node instanceof HTMLInputElement && ["submit", "image"].includes(node.type))
      || (node.getAttribute("role") === "button" && Boolean(node.closest("form")))
      || (includeAncestor && Boolean(node.closest("form")))
  }, includeFormAncestor).catch(() => false)
  if (!submit) return
  const currentOrigin = origin(page.url())
  await context.ask({
    permission: "local_browser_submit",
    patterns: [currentOrigin],
    always: [],
    metadata: { origin: currentOrigin },
  })
}

async function pageResult(page: Page, session: SessionBrowser, includeSnapshot: boolean): Promise<string> {
  const data: Record<string, unknown> = {
    tabId: session.activeTabId,
    title: cleanText(await page.title().catch(() => ""), 500),
    url: safeUrl(page.url()),
  }
  if (includeSnapshot) {
    data.visibleText = cleanText(await page.locator("body").innerText({ timeout: 5_000 }).catch(() => ""), SNAPSHOT_TEXT_LIMIT)
    // 只读取控件类型、名称和状态，刻意不读取 input.value，防止密码或已输入内容进入模型上下文。
    const controls = await page.locator("button, a, input, select, textarea, [role]").evaluateAll(
      (elements, limit) => elements.slice(0, limit).map(element => {
        const node = element as HTMLElement
        const input = node instanceof HTMLInputElement ? node : null
        const text = (node.getAttribute("aria-label")
          || node.getAttribute("title")
          || (node instanceof HTMLInputElement || node instanceof HTMLTextAreaElement ? node.placeholder : "")
          || node.innerText
          || "").replace(/\s+/g, " ").trim().slice(0, 300)
        return {
          tag: node.tagName.toLowerCase(),
          role: node.getAttribute("role"),
          name: text,
          type: input?.type ?? null,
          checked: input && ["checkbox", "radio"].includes(input.type) ? input.checked : null,
          disabled: "disabled" in node ? Boolean((node as HTMLButtonElement).disabled) : false,
        }
      }),
      SNAPSHOT_CONTROLS_LIMIT,
    ).catch(() => [])
    data.controls = controls.map(control => ({ ...control, name: cleanText(control.name, 300) }))
  }
  return result(data)
}

async function artifactPath(root: string, category: string, sessionId: string, extension: string): Promise<string> {
  const relativeDirectory = path.join(category, safeId(sessionId))
  await mkdir(path.join(root, relativeDirectory), { recursive: true })
  await ensureGitExclude(root, `${category}/`)
  return path.join(relativeDirectory, `${Date.now()}-${createHash("sha256").update(String(Math.random())).digest("hex").slice(0, 8)}.${extension}`)
}

async function downloadPath(root: string, sessionId: string, suggested: string): Promise<string> {
  const directory = path.join("browser-downloads", safeId(sessionId))
  await mkdir(path.join(root, directory), { recursive: true })
  await ensureGitExclude(root, "browser-downloads/")
  const parsed = path.parse(suggested.replace(/[\\/\u0000-\u001f]/g, "_").slice(0, 180) || "download")
  let candidate = path.join(directory, parsed.base)
  let index = 1
  while (await exists(path.join(root, candidate))) {
    candidate = path.join(directory, `${parsed.name}-${index++}${parsed.ext}`)
  }
  return candidate
}

async function workspaceFile(root: string, relative: string): Promise<string> {
  const rootReal = await realpath(root)
  const requested = path.resolve(rootReal, relative)
  const requestedReal = await realpath(requested)
  if (requestedReal !== rootReal && !requestedReal.startsWith(rootReal + path.sep)) {
    throw new Error("上传文件越出当前工作区")
  }
  const link = await lstat(requested)
  const file = await stat(requestedReal)
  if (link.isSymbolicLink() || !file.isFile() || file.size > 100 * 1024 * 1024) {
    throw new Error("上传文件必须是工作区内不超过 100 MiB 的普通文件")
  }
  return requestedReal
}

async function ensureGitExclude(root: string, entry: string) {
  const exclude = path.join(root, ".git", "info", "exclude")
  if (!await exists(exclude)) return
  const current = await readFile(exclude, "utf8")
  if (current.split(/\r?\n/).includes(entry)) return
  await appendFile(exclude, `${current.endsWith("\n") || current.length === 0 ? "" : "\n"}${entry}\n`, "utf8")
}

async function cleanupIdleSessions() {
  const now = Date.now()
  for (const [sessionId, session] of runtime.sessions) {
    if (now - session.lastActivity > SESSION_IDLE_MS) await closeSession(sessionId)
  }
  if (runtime.sessions.size === 0 && runtime.browser?.isConnected()) {
    runtime.browserIdleSince ??= now
    if (now - runtime.browserIdleSince > BROWSER_IDLE_MS) await stopBrowser()
  }
}

async function closeSession(sessionId: string) {
  const session = runtime.sessions.get(sessionId)
  if (!session) return
  runtime.sessions.delete(sessionId)
  await Promise.allSettled([...session.pages.values()].map(page => page.close()))
  if (runtime.sessions.size === 0) runtime.browserIdleSince = Date.now()
}

async function stopBrowser() {
  const browser = runtime.browser
  resetRuntime()
  await browser?.close().catch(() => undefined)
  await relay("stop", "POST").catch(() => undefined)
}

function resetRuntime() {
  runtime.browser = null
  runtime.context = null
  runtime.sessions.clear()
  runtime.browserIdleSince = null
}

function removeClosedPages(sessionId: string, session: SessionBrowser) {
  for (const [id, page] of session.pages) if (page.isClosed()) session.pages.delete(id)
  if (session.pages.size === 0) runtime.sessions.delete(sessionId)
  else if (!session.pages.has(session.activeTabId)) session.activeTabId = session.pages.keys().next().value as string
}

async function tabList(session: SessionBrowser) {
  return Promise.all([...session.pages.entries()].map(async ([tabId, page]) => ({
    tabId,
    title: page.isClosed() ? "" : cleanText(await page.title().catch(() => ""), 500),
    url: page.isClosed() ? null : safeUrl(page.url()),
  })))
}

function activePage(session: SessionBrowser): Page {
  const page = session.pages.get(session.activeTabId)
  if (!page || page.isClosed()) throw new Error("当前浏览器标签页已经关闭")
  return page
}

function nextTabId() { return `tab_${++runtime.sequence}` }
function safeId(value: string) { return value.replace(/[^A-Za-z0-9_.-]/g, "_").slice(0, 80) || "session" }
function result(data: unknown) { return JSON.stringify({ success: true, data }, null, 2) }
function requiredValue(value?: string) { if (value === undefined) throw new Error("该操作缺少 value"); return value }
function requiredRelativePath(value?: string) {
  if (!value || path.isAbsolute(value) || value.split(/[\\/]/).includes("..")) throw new Error("relativePath 必须是工作区相对路径")
  return value
}
function requiredTab(value: string | undefined, session: SessionBrowser) {
  if (!value || !session.pages.has(value)) throw new Error("标签页 ID 不属于当前 Session")
  return value
}
function requiredUrl(value?: string) {
  if (!value) throw new Error("该操作缺少 url")
  const parsed = new URL(value)
  if (!["http:", "https:"].includes(parsed.protocol) || parsed.username || parsed.password) throw new Error("只允许无内嵌凭据的 HTTP(S) 地址")
  return parsed.toString()
}
function origin(value: string) { return new URL(value).origin }
function httpOrigin(value: string) { try { const url = new URL(value); return ["http:", "https:"].includes(url.protocol) ? url.origin : null } catch { return null } }
function safeUrl(value: string) { const url = new URL(value); url.username = ""; url.password = ""; url.search = ""; url.hash = ""; return url.toString() }
function cleanText(value: string, limit: number) {
  return value
    .replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, " ")
    .replace(/\bBearer\s+[A-Za-z0-9._~+/=-]+/gi, "[REDACTED_TOKEN]")
    .replace(/\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}(?:\.[A-Za-z0-9_-]{10,})?/g, "[REDACTED_TOKEN]")
    .replace(/\b(?:sk|ak|api|token|secret)[-_][A-Za-z0-9_-]{16,}\b/gi, "[REDACTED_TOKEN]")
    .replace(/\b[A-Fa-f0-9]{32,}\b/g, "[REDACTED_TOKEN]")
    .replace(/(^|\n)(\s*(?:password|passwd|密码|口令|access[_ -]?token|api[_ -]?key|secret)\s*[:=]\s*)[^\n]+/gi, "$1$2[REDACTED]")
    .replace(/\n{4,}/g, "\n\n\n")
    .slice(0, limit)
}
function requiredEnvironment(name: string) { const value = process.env[name]?.trim(); if (!value) throw new Error(`当前客户端缺少 ${name}，请升级并重启`); return value }
async function exists(file: string) { try { await stat(file); return true } catch { return false } }
