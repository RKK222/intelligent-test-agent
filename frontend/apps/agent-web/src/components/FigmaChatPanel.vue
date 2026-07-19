<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch, type CSSProperties } from 'vue'
import {
  AlertTriangle,
  ArrowUpRight,
  Bell,
  BookOpen,
  CircleHelp,
  CheckCircle,
  Clock3,
  ChevronDown,
  ChevronUp,
  ChevronRight,
  Circle,
  Eye,
  EyeOff,
  FileText,
  Folder,
  MessageSquare,
  Loader2,
  MinusCircle,
  PanelRightClose,
  SquarePen,
  Send,
  Square,
  ThumbsDown,
  ThumbsUp,
  Upload,
  User,
  X,
  Copy,
  Check,
  Download,
} from 'lucide-vue-next'
import type {
  AgentInfo,
  AgentMessage,
  AiFeedbackReasonCode,
  AiFeedbackRating,
  AiRunFeedback,
  FileSearchResult,
  MessageScope,
  MessagePart,
  NightExecutionSlots,
  NightExecutionTask,
  PermissionRequest,
  QuestionRequest,
  RunDiffFile,
  SubagentSession,
  TodoItem,
} from '@test-agent/shared-types'
import aiHeaderUrl from '../assets/figma/ai-header.svg'
import planLoadingUrl from '../assets/figma/plan-loadding.gif'
import panelCloseUrl from '../assets/figma/panel-close.svg'
import { MarkdownView, OpencodeTimeline, createOpencodeLikeState, type OpencodeLikeRuntimeStatus } from '@test-agent/agent-chat'
import ChatContextAttachmentList from './ChatContextAttachmentList.vue'
import { copyTextToClipboard, Spinner } from '@test-agent/ui-kit'
import type { ChatContextItem } from '../stores/chatContextStore'
import { validateChatSend } from '../stores/chatContextStore'
import type { WorkspaceRequirementReference } from './workbench-utils'
import { resolveSessionListDrawerPlacement } from './session-list-drawer'

type ChatMessageInput = AgentMessage & { content?: string }

type ReadOutputInfo = {
  kind: 'file' | 'directory'
  path: string
  content: string
  language?: string
  entries?: string[]
}

type ChatMessage = {
  id: string
  platformMessageId?: string
  remoteMessageId?: string
  feedbackMessageId?: string
  role: 'user' | 'assistant'
  content: string
  meta?: string
  parts: MessagePart[]
  _error?: boolean
  _skillName?: string
  readOutputs?: ReadOutputInfo[]
}

function getFileName(path: string): string {
  if (!path) return ''
  const normalized = path.replace(/\\/g, '/')
  return normalized.split('/').filter(Boolean).pop() || path
}

function recordValue(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined
}

function textValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : undefined
}

function numberValue(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

// 失败卡片优先展示后端或 RunEvent 给出的真实错误，再补充 code / traceId，避免退化成“未知错误”。
function eventFailureText(payload?: Record<string, unknown>): string {
  if (!payload) return ''
  const error = recordValue(payload.error)
  const errorData = recordValue(error?.data)
  const message =
    textValue(error?.message) ??
    textValue(errorData?.message) ??
    textValue(payload.message) ??
    textValue(error?.name) ??
    textValue(errorData?.name)
  const code = textValue(payload.code) ?? textValue(error?.code) ?? textValue(errorData?.code)
  const statusCode = numberValue(errorData?.statusCode) ?? numberValue(error?.statusCode) ?? numberValue(payload.statusCode)
  const traceId = textValue(payload.traceId) ?? textValue(error?.traceId) ?? textValue(errorData?.traceId)
  const suffix = [
    code ? `code: ${code}` : '',
    statusCode ? `status: ${statusCode}` : '',
    traceId ? `traceId: ${traceId}` : '',
  ].filter(Boolean)
  return [message ?? '', suffix.length ? `(${suffix.join(', ')})` : ''].filter(Boolean).join(' ')
}

/**
 * 解析 read 工具输出的 XML 格式文件/目录内容。
 */
function parseReadOutput(output: string): ReadOutputInfo | null {
  if (!output.includes('<path>')) return null
  const pathMatch = output.match(/<path>(.+?)<\/path>/)
  const typeMatch = output.match(/<type>(.+?)<\/type>/)
  if (!pathMatch || !typeMatch) return null
  const filePath = pathMatch[1].trim()
  const kind = typeMatch[1].trim() as 'file' | 'directory'

  if (kind === 'file') {
    const contentMatch = output.match(/<content>([\s\S]*?)<\/content>/)
    if (!contentMatch) return null
    const rawContent = contentMatch[1].replace(/^\d+:\s?/gm, '').trimEnd()
    const ext = filePath.split('.').pop()?.toLowerCase() ?? ''
    const langMap: Record<string, string> = {
      js: 'javascript', ts: 'typescript', jsx: 'javascript', tsx: 'typescript',
      vue: 'vue', html: 'xml', css: 'css', scss: 'scss', java: 'java',
      kt: 'kotlin', py: 'python', go: 'go', rs: 'rust', cpp: 'cpp',
      c: 'c', cs: 'csharp', php: 'php', swift: 'swift', md: 'markdown',
      json: 'json', yml: 'yaml', yaml: 'yaml', xml: 'xml', sql: 'sql',
      sh: 'bash', bash: 'bash', gradle: 'groovy',
    }
    return { kind, path: filePath, content: rawContent, language: langMap[ext] ?? ext }
  }

  // directory
  const entriesMatch = output.match(/<entries>([\s\S]*?)<\/entries>/)
  if (!entriesMatch) return null
  const entries = entriesMatch[1]
    .split('\n')
    .map((e) => e.trim())
    .filter((e) => e && !e.startsWith('(') && !e.startsWith(')'))
  return { kind, path: filePath, content: '', entries }
}

function partText(part: unknown): string {
  if (part && typeof part === 'object') {
    const pType = (part as { type?: string }).type
    // Only text parts should appear in the main message body.
    // All structured parts (tool/reasoning/file/subtask/retry/step-*/compaction)
    // are rendered separately as structured blocks below.
    if (!pType || pType !== 'text') {
      return ''
    }
    // For text parts, return the text content
    const text = (part as { text?: unknown }).text
    return typeof text === 'string' ? text : ''
  }
  return ''
}

function hasVisibleParts(msg: AgentMessage): boolean {
  if (msg.role !== 'assistant' || !Array.isArray(msg.parts)) return false
  return msg.parts.some((p) => p.type === 'tool' || p.type === 'file' || p.type === 'reasoning' || p.type === 'retry')
}

function messageFiles(msg: FileOperationMessage) {
  if (msg.role !== 'assistant' || !Array.isArray(msg.parts)) return []
  return msg.parts
    .filter((part): part is Extract<MessagePart, { type: 'file' }> => part.type === 'file')
    .map((part) => ({
      id: part.partId,
      name: part.name ?? part.path ?? part.partId,
      path: part.path,
      mimeType: part.mimeType,
      url: part.url,
    }))
}
// 将 AI 回复中的 <thinking> 标签转为折叠块，美观展示思考过程。
function formatThinking(text: string): string {
  return text.replace(/<thinking>([\s\S]*?)<\/thinking>/g, (_, c) => {
    const t = c.trim();
    if (!t) return '';
    return '\n> \u{1F4AD} **思考**\n> ' + t.replace(/\n/g, '\n> ') + '\n';
  });
}


// 连续助手快照合并时只保留一个边界换行，避免前一段自带换行后再次 join 产生空白段。
function joinAssistantContent(left: string, right: string): string {
  if (!left) return right
  if (!right) return left
  return `${left.replace(/\n+$/, '')}\n${right.replace(/^\n+/, '')}`
}

function taskPartLabel(
  toolName: string,
  input?: Record<string, unknown>
): { label: string; detail: string } {
  const file = input ? toolFilePath(toolName, input) : ''
  const fileName = getFileName(file)
  switch (toolName) {
    case 'bash': {
      const cmd = typeof input?.command === 'string' ? input.command : ''
      const shortCmd = cmd.length > 50 ? cmd.slice(0, 50) + '...' : cmd
      return { label: '执行命令', detail: shortCmd || 'bash' }
    }
    case 'read':
      return { label: '读取文件', detail: fileName || '文件' }
    case 'write':
      return { label: '写入文件', detail: fileName || '文件' }
    case 'edit':
      return { label: '编辑文件', detail: fileName || '文件' }
    case 'apply_patch':
      return { label: '应用补丁', detail: fileName || '' }
    case 'grep':
      return {
        label: '搜索代码',
        detail: typeof input?.pattern === 'string' ? input.pattern : '',
      }
    case 'glob':
      return {
        label: '查找文件',
        detail: typeof input?.pattern === 'string' ? input.pattern : '',
      }
    case 'task':
      return {
        label: '子任务',
        detail:
          typeof input?.description === 'string'
            ? input.description.slice(0, 60)
            : toolName || '',
      }
    default:
      return { label: toolName || '工具', detail: file || '' }
  }
}

/**
 * 从消息 parts 中提取需要结构化渲染的块：
 * - reasoning（已完成状态，运行中由 thinkingLines 实时展示）
 * - 非文件操作 tool part（bash/grep/glob 等，文件操作已由 file summaries 覆盖）
 * - retry part
 * 这些 part 不进入主正文，在本组件内以折叠块独立渲染。
 */
const URL_FETCH_TOOLS = new Set(['webfetch', 'fetch_url', 'read_url_content', 'browser_subagent', 'execute_url', 'read_url'])

function isUrlFetchTool(toolName?: string): boolean {
  if (!toolName) return false
  return URL_FETCH_TOOLS.has(toolName.toLowerCase())
}

function formatToolName(toolName?: string): string {
  if (!toolName) return 'Webfetch'
  const name = toolName.toLowerCase()
  if (name === 'webfetch') return 'Webfetch'
  if (name === 'read_url_content' || name === 'read_url') return 'Webfetch'
  return toolName
}

function getUrlFromInput(input?: Record<string, unknown>): string {
  if (!input) return ''
  return typeof input.url === 'string' ? input.url : typeof input.uri === 'string' ? input.uri : ''
}

function isSkillCall(part: MessagePart): boolean {
  if (part.type !== 'tool') return false
  const toolName = (part as any).toolName || ''
  if (!toolName) return false
  const lower = toolName.toLowerCase()
  const standardTools = new Set(['bash', 'grep', 'glob', 'read', 'write', 'edit', 'apply_patch', 'task'])
  if (standardTools.has(lower)) return false
  if (isUrlFetchTool(lower)) return false
  return true
}

function messageSkillCalls(msg: ChatMessage): MessagePart[] {
  if (!Array.isArray(msg.parts)) return []
  return msg.parts.filter(p => isSkillCall(p))
}

function getSkillDescription(skillName: string): string {
  if (!skillName) return ''
  const cmd = props.commands.find(c => c.name === skillName || c.commandId === skillName)
  return cmd?.description || ''
}

function formatSkillTime(part: MessagePart): string {
  const durationMs = (part as any).durationMs || 120000
  const start = new Date()
  const end = new Date(start.getTime() + durationMs)
  const format = (d: Date) => `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
  return `${format(start)}-${format(end)}`
}

type SubtaskItem = {
  label: string
  detail?: string
  status: string
}

function messageSubtasks(msg: ChatMessage): SubtaskItem[] {
  if (!Array.isArray(msg.parts)) return []
  const list: SubtaskItem[] = []
  for (const part of msg.parts) {
    if (part.type === 'subtask') {
      list.push({
        label: (part as any).title || (part as any).label || '子任务',
        detail: (part as any).detail || '',
        status: (part as any).status || 'pending',
      })
    } else if (part.type === 'tool' && (part as any).toolName === 'task') {
      const input = (part as any).input || {}
      list.push({
        label: typeof input.title === 'string' ? input.title : typeof input.description === 'string' ? input.description : '子任务',
        detail: typeof input.detail === 'string' ? input.detail : '',
        status: (part as any).status || 'pending',
      })
    }
  }
  return list
}

function getFileDir(filePath: string): string {
  if (!filePath) return ''
  const parts = filePath.split('/')
  parts.pop()
  return parts.join('/')
}

function getShortenedPath(path: string): string {
  if (!path) return ''
  if (path.length > 50) {
    const parts = path.split('/')
    if (parts.length > 4) {
      return '.../' + parts.slice(-4).join('/')
    }
  }
  return path
}

function renderCodeWithLineNumbers(content: string, filePath: string): string {
  const highlighted = highlightCode(content, filePath)
  const lines = highlighted.split('\n')
  return lines
    .map((line, idx) => {
      const lineNum = idx + 1
      return `<div class="figma-chat-code-line"><span class="figma-chat-code-lineno">${lineNum}</span><span class="figma-chat-code-linecontent">${line || ' '}</span></div>`
    })
    .join('')
}

function copyErrorMessage() {
  const errorText = taskFailureMessage.value
  copyTextWithClipboard(errorText, () => {
    console.log('Error copied to clipboard')
  })
}

const copySuccessId = ref<string | null>(null)
function copyText(text: string, id: string) {
  copyTextWithClipboard(text, () => {
    copySuccessId.value = id
    setTimeout(() => {
      if (copySuccessId.value === id) {
        copySuccessId.value = null
      }
    }, 1500)
  })
}

function copyTextWithClipboard(text: string, onSuccess: () => void) {
  void copyTextToClipboard(text).then((copied) => {
    if (copied) {
      onSuccess()
      return
    }
    console.warn('Clipboard copy is unavailable in this browser context')
  })
}

function messageOtherParts(msg: ChatMessage): MessagePart[] {
  if (!Array.isArray(msg.parts)) return []
  const reasoningParts = msg.parts.filter((p) => p.type === 'reasoning') as Array<Extract<MessagePart, { type: 'reasoning' }>>
  const otherParts = msg.parts.filter((p) => {
    if (p.type === 'reasoning') return false
    if (p.type === 'retry') return true
    if (p.type === 'tool') {
      const toolName = (p.toolName ?? '').toLowerCase()
      if (isSkillCall(p)) return false
      if (toolName === 'task') return false
      if (toolName === 'read' || FILE_WRITE_TOOLS.has(toolName) || FILE_EDIT_TOOLS.has(toolName)) {
        // 成功的文件工具统一由上方文件摘要渲染；失败时仍保留通用详情以展示错误原因。
        return toolIsFailed(p)
      }
      return true
    }
    return false
  })

  if (reasoningParts.length === 0) {
    return otherParts
  }

  const mergedText = reasoningParts
    .map((p) => p.text)
    .filter((t) => typeof t === 'string' && t.trim().length > 0)
    .join('\n\n')
    .trim()

  let mergedStatus = 'completed'
  if (props.running && msg.id === lastAssistant.value?.id) {
    const hasRunning = reasoningParts.some((p) => {
      const status = ((p as { status?: string }).status ?? '').toLowerCase()
      return ['running', 'in_progress', 'streaming', 'started', 'active'].includes(status)
    })
    if (hasRunning) {
      mergedStatus = 'running'
    }
  }

  let totalDurationMs = 0
  for (const p of reasoningParts) {
    if (typeof p.durationMs === 'number' && Number.isFinite(p.durationMs)) {
      totalDurationMs += p.durationMs
    }
  }

  const mergedReasoning = {
    partId: `${msg.id}-merged-reasoning`,
    type: 'reasoning',
    text: mergedText,
    status: mergedStatus,
    durationMs: totalDurationMs > 0 ? totalDurationMs : undefined
  } as MessagePart

  return [mergedReasoning, ...otherParts]
}

/** 从 tool part 获取展示用的输出文本 */
function toolOutputText(part: MessagePart): string | undefined {
  if (part.type !== 'tool') return undefined
  const p = part as { output?: unknown; state?: { output?: string; error?: string } }
  if (typeof p.output === 'string' && p.output) return p.output
  if (p.state?.output) return p.state.output
  if (p.state?.error) return p.state.error
  return undefined
}

/** 判断 tool part 是否失败 */
function toolIsFailed(part: MessagePart): boolean {
  if (part.type !== 'tool') return false
  const status = (part as { status?: string }).status ?? ''
  const st = status.toLowerCase()
  if (st === 'failed' || st === 'error') return true
  const p = part as { state?: { error?: string } }
  if (p.state?.error) return true
  return false
}

/** 提取 reasoning part 的耗时文本 */
function reasoningDurationText(part: MessagePart): string | undefined {
  if (part.type !== 'reasoning') return undefined
  const ms = (part as { durationMs?: number }).durationMs
  if (typeof ms !== 'number' || !Number.isFinite(ms) || ms <= 0) return undefined
  if (ms < 1000) return `${ms}ms`
  const seconds = ms / 1000
  if (seconds < 60) return `${seconds.toFixed(seconds < 10 ? 1 : 0)}s`
  const minutes = Math.floor(seconds / 60)
  const rest = Math.round(seconds % 60)
  return `${minutes}m ${rest}s`
}

/** 检查 part 是否处于运行中状态 */
function partIsRunning(part: MessagePart): boolean {
  if (!props.running) return false
  const status = ((part as { status?: string }).status ?? '').toLowerCase()
  return ['running', 'in_progress', 'streaming', 'started', 'active'].includes(status)
}

function reasoningIsRunning(message: ChatMessage, part: MessagePart): boolean {
  return partIsRunning(part) || (props.running && message.id === lastAssistant.value?.id)
}

/**
 * 当前 part 是否属于"最近一条 assistant 消息"。
 * 给最近一轮的工具/思考折叠块默认展开，让用户能直接看到这一次 Run 里发生过什么；
 * 之前轮的折叠块保持收起，避免历史会话回看时被一大片细节铺满。
 * 同时保留用户手动收起的偏好（由 <details> 自身的 open 状态管理）。
 */
function isInLastAssistantMessage(message: ChatMessage): boolean {
  return lastAssistant.value?.id === message.id
}

/**
 * 折叠块在以下任一情况时默认展开：
 * 1) part 本身仍在运行中（实时跟踪进行中的工具调用）
 * 2) 所属 assistant 消息是当前轮的最后一条（让用户能"一翻开就看到"最近一轮做了什么）
 * 当新一轮开始后，旧轮的 lastAssistant 会切走，折叠块自动重新收起，
 * 实现"先输出一小段、再缩起来"的轻量活动流。
 */
function partShouldOpen(message: ChatMessage, part: MessagePart): boolean {
  return partIsRunning(part) || isInLastAssistantMessage(message)
}

// 记录工具 part 的展开/收起状态（由用户手动点击 summary 触发切换）
const manualToolStates = ref<Record<string, boolean>>({})

function getToolPartKey(message: ChatMessage, part: MessagePart): string {
  return `${message.id}:${part.partId || `${part.type}-${(part as any).toolName || ''}`}`
}

function toggleToolOpen(message: ChatMessage, part: MessagePart) {
  const key = getToolPartKey(message, part)
  manualToolStates.value[key] = !isToolOpen(message, part)
}

function isToolOpen(message: ChatMessage, part: MessagePart): boolean {
  const key = getToolPartKey(message, part)
  const manualVal = manualToolStates.value[key]
  if (manualVal !== undefined) {
    return manualVal
  }
  // bash 工具默认收起，其他工具或运行中状态默认展开
  if (part.type === 'tool' && (part as any).toolName === 'bash') {
    return partIsRunning(part)
  }
  return partShouldOpen(message, part)
}

function messageToolsExpandedState(msgId: string): string {
  return Object.entries(manualToolStates.value)
    .filter(([k, v]) => k.startsWith(msgId + ':') && v)
    .map(([k]) => k)
    .join(',')
}

/** 从 tool input 生成一行摘要文字，显示在 tool 折叠块标题行 */
function summaryFromToolInput(toolName: string, input: Record<string, unknown> | undefined): string {
  if (!input) return ''
  const { label, detail } = taskPartLabel(toolName || 'tool', input)
  return detail || label || ''
}

const runStartMsgCount = ref(0)

type FileOperationMessage = Pick<ChatMessage, 'role' | 'parts'>

export type FileChangeStat = {
  path: string
  additions?: number
  deletions?: number
  status?: 'added' | 'modified' | 'deleted' | string
  patch?: string
}

export type TaskUsage = {
  duration?: string
  tokens?: number
  totalDuration?: string
}

export type RawOutputKind = 'request' | 'response' | 'sse'

export type RawOutputEntry = {
  id: string
  kind: RawOutputKind
  title: string
  method?: string
  path?: string
  status?: number
  eventName?: string
  traceId?: string
  runId?: string
  contentType?: string
  body: string
  truncated?: boolean
  occurredAt: string
}

type OpencodeProcessState = {
  status: string
  initializable: boolean
  message: string
  baseUrl?: string
  // 与头像菜单一致的二级状态：未分配 / 运行中 / 未运行；旧后端缺失时前端按 status/地址推断
  serviceStatus?: string
  serviceAddress?: string | null
  linuxServerId?: string
  port?: number | string
  messageSendAllowed?: boolean
  messageSendBlockedReason?: string | null
}

// 抽屉里 diff 行的解析结果：保留原始前缀符号供渲染和后续扩展使用
type DiffLineKind = 'add' | 'del' | 'ctx' | 'meta'

type DiffLine = {
  kind: DiffLineKind
  // meta 行没有行号，add 行只有 newNum，del 行只有 oldNum，ctx 行两者都有
  oldNum?: number
  newNum?: number
  // 去掉前缀符号后的真实文本
  text: string
}

const props =
  withDefaults(defineProps<{
    messages: ChatMessageInput[]
    running?: boolean
    runtimeStatus?: string
    timelineRuntimeStatus?: OpencodeLikeRuntimeStatus
    placeholder?: string
    inputValue?: string
    title?: string
    /** 任务消耗（来自 SSE 事件统计） */
    taskUsage?: TaskUsage
    /** 文件变更行（来自 SSE 事件统计） */
    fileChanges?: FileChangeStat[]
    /** 历史对话列表 */
    history?: Array<{
      id: string
      title: string
      createdAt?: string
      updatedAt?: string
      appName?: string
      workspaceName?: string
      version?: string
      runtimeState?: 'running' | 'completed'
      runId?: string
      runStatus?: string
      pendingQuestion?: boolean
      attentionEventId?: string
      attentionAt?: string
      sourceType?: string
    }>
    /** 当前用户历史中仍在运行的会话数。 */
    historyRunningCount?: number
    /** 当前用户历史中存在待回答 question 的会话数。 */
    historyQuestionCount?: number
    /** 受控历史搜索词，由父组件负责远端查询。 */
    historySearch?: string
    /** 当前搜索条件下的历史总数。 */
    historyTotal?: number
    /** 是否还有下一页历史。 */
    historyHasMore?: boolean
    /** 正在加载下一页历史。 */
    historyLoadingMore?: boolean
    /** 正在切换历史会话；旧正文在此期间隐藏，避免误以为点击无响应。 */
    historyLoading?: boolean
    /** 历史完整投影尚未完成；正文可见后仍阻止向未稳定的 Session 发送。 */
    historySubmitBlocked?: boolean
    /** 当前历史会话只读原因；存在时禁止继续发送。 */
    readonlyReason?: string
    /** 当前选中的模型展示名 */
    selectedModelLabel?: string
    /** 模型选择按钮是否禁用 */
    modelPickerDisabled?: boolean
    /** 可作为主运行入口选择的 Agent 列表 */
    agents?: AgentInfo[]
    /** 当前个人 worktree 中与 @ 查询匹配的文件。 */
    workspaceFileCandidates?: FileSearchResult[]
    workspaceFileCandidatesLoading?: boolean
    /** 当前个人 worktree 中按“spec/需求项/阶段/子条目”跨需求、设计、编码、测试聚合的引用。 */
    workspaceRequirementReferences?: WorkspaceRequirementReference[]
    workspaceRequirementReferencesLoading?: boolean
    /** Agent 目录首次加载中。 */
    agentsLoading?: boolean
    /** Agent 目录已有数据后的后台刷新中。 */
    agentsRefreshing?: boolean
    /** Agent 目录加载失败文案。 */
    agentsError?: string
    /** 当前选中的主 Agent 标识 */
    selectedAgent?: string
    /** 终止按钮是否禁用 */
    stopDisabled?: boolean
    /** 终止按钮禁用原因 */
    stopDisabledReason?: string
    /** 当前用户 opencode 进程状态，控制是否允许发起对话 */
    processStatus?: OpencodeProcessState | null
    /** 进程状态承载位置；工作台宠物模式下隐藏聊天区的旧状态点/卡片，但保留发送拦截。 */
    processStatusPlacement?: 'chat' | 'pet'
    processRequired?: boolean
    processLoading?: boolean
    /** 已有状态下的后台健康探测中；不阻塞输入，但要阻止提交旧状态。 */
    processRefreshing?: boolean
    /** 主动刷新才需要阻止提交；后台轮询刷新不应周期性打断用户发送。 */
    processRefreshBlocksSubmit?: boolean
    processInitializing?: boolean
    /** 可选模型列表（供快速标签使用） */
    models?: any[]
    /** Provider 列表；部分后端只在 provider.models 内返回模型目录。 */
    providers?: any[]
    /** 当前选中的模型标识 */
    selectedModel?: string
    /** 当前用户按 Run 保存的整轮评价。 */
    runFeedbacks?: Record<string, AiRunFeedback | null>
    /** 正在提交反馈的消息 */
    feedbackSubmitting?: Record<string, boolean>
    runStatusesByRunId?: Record<string, string>
    /** 可用命令列表（含 source=skill 的 Skill Command） */
    commands?: Array<{ commandId: string; name: string; description?: string; source?: string; arguments?: string }>
    /** 当前会话在前端内存中捕获的浏览器与平台后端原始报文 */
    rawOutputEntries?: RawOutputEntry[]
    /** message.part.delta 的流式 overlay，避免 text/reasoning 闪烁或重复。 */
    streamingTextByPartId?: Record<string, string>
    /** opencode Todo 当前快照，作为工作状态第三行展示。 */
    todos?: TodoItem[]
    /** 各用户轮次的 Todo 快照，历史工作状态展开时只展示该轮任务。 */
    todoSnapshotsByUserMessageId?: Record<string, TodoItem[]>
    /** 用户从工作区添加到本轮对话的选区/文件上下文附件。 */
    chatContexts?: ChatContextItem[]
    chatContextTotalChars?: number
    chatContextOverLimit?: boolean
    chatContextError?: string | null
    /** permission.asked 投影出的待处理权限请求。 */
    permissions?: PermissionRequest[]
    /** question.asked 投影出的待处理提问请求。 */
    questions?: QuestionRequest[]
    /** 当前 root 会话；历史切换时用于退出上一次子 Agent 视图。 */
    currentSessionId?: string
    currentSessionSourceType?: string | null
    /** 右侧对话栏是否可见；收起时同步关闭传送到 body 的会话列表浮层。 */
    panelVisible?: boolean
    /** RunEvent scope 索引：用于把主 Agent 与子 Agent 输出分离展示。 */
    messageScopesById?: Record<string, MessageScope>
    /** 当前运行期发现的子 Agent 会话索引。 */
    subagentsBySessionId?: Record<string, SubagentSession>
    /** task part 到子 Agent session 的索引。 */
    subagentByTaskPartId?: Record<string, string>
    /** 当前用户仍待执行的夜间任务，供“待执行任务”页签展示。 */
    nightTasks?: NightExecutionTask[]
    /** 当前会话的待执行任务；存在时只锁定当前会话的继续发送。 */
    currentNightTask?: NightExecutionTask | null
    /** 当前会话最近一次尚未关闭的夜间失败任务。 */
    nightVisibleFailure?: NightExecutionTask | null
    /** 当前夜间窗口的 15 分钟容量槽。 */
    nightSlots?: NightExecutionSlots | null
    nightSlotsLoading?: boolean
    nightTaskSubmitting?: boolean
    nightTaskActionPending?: Record<string, boolean>
  }>(), {
    processRefreshBlocksSubmit: true,
    processStatusPlacement: 'chat',
    commands: () => [],
    agents: () => [],
    workspaceFileCandidates: () => [],
    workspaceRequirementReferences: () => [],
    rawOutputEntries: () => [],
    streamingTextByPartId: () => ({}),
    todos: () => [],
    todoSnapshotsByUserMessageId: () => ({}),
    chatContexts: () => [],
    permissions: () => [],
    questions: () => [],
    messageScopesById: () => ({}),
    subagentsBySessionId: () => ({}),
    subagentByTaskPartId: () => ({}),
    nightTasks: () => [],
    currentNightTask: null,
    nightVisibleFailure: null,
    nightSlots: null,
    nightTaskActionPending: () => ({}),
    panelVisible: true,
  })

const emit =
  defineEmits<{
    (e: 'send', prompt: string): void
    (e: 'stop'): void
    (e: 'retry'): void
    (e: 'new-conversation'): void
    (e: 'request-night-slots'): void
    (e: 'schedule-night', payload: { prompt: string; slotStart: string }): void
    (e: 'adjust-night-task', payload: { taskId: string; slotStart: string }): void
    (e: 'cancel-night-task', taskId: string): void
    (e: 'dismiss-night-task', taskId: string): void
    (e: 'open-night-task-session', sessionId: string): void
    (e: 'close'): void
    (e: 'open-history'): void
    (e: 'history-search-change', query: string): void
    (e: 'load-more-history'): void
    (e: 'select-session', id: string): void
    (e: 'request-night-tasks'): void
    (e: 'update:inputValue', value: string): void
    (e: 'open-diff', path: string): void
    (e: 'open-file', path: string): void
    (e: 'initialize-process'): void
    (e: 'open-help', topic?: string): void
    (e: 'select-model', model: any): void
    (e: 'change-agent', agentId: string): void
    (e: 'refresh-agents'): void
    (e: 'search-workspace-files', query: string | null): void
    (e: 'load-workspace-requirements'): void
    (e: 'add-workspace-file-context', path: string): void
    (e: 'add-workspace-requirement-context', reference: WorkspaceRequirementReference): void
    (e: 'clear-raw-output'): void
    (e: 'remove-chat-context', id: string): void
    (e: 'clear-chat-contexts'): void
    (e: 'preview-context', item: ChatContextItem): void
    (e: 'reply-permission', requestId: string, decision: 'once' | 'always' | 'reject'): void
    (e: 'reply-question', requestId: string, answers: unknown[]): void
    (e: 'reject-question', requestId: string): void
    (
      e: 'submit-feedback',
      payload: {
        runId: string
        rating: AiFeedbackRating
        reasonCode?: AiFeedbackReasonCode | null
        comment?: string | null
      }
    ): void
  }>()

const collapsedMessages = ref<Record<string, boolean>>({})

const localInput = ref(props.inputValue ?? '')
const nightPickerOpen = ref(false)
const nightPickerMode = ref<'create' | 'adjust'>('create')
const adjustingNightTaskId = ref<string | null>(null)
const selectedNightSlotStart = ref('')
const nightSubmissionAwaiting = ref(false)
const submittedNightTaskId = ref<string | null>(null)
const cancelConfirmTaskId = ref<string | null>(null)
// 集中页签保持服务端时段顺序，但把当前正在查看的会话任务固定置顶，便于与对话卡相互确认。
const orderedNightTasks = computed(() => [...props.nightTasks].sort((left, right) => {
  const currentOrder = Number(right.sessionId === props.currentSessionId)
    - Number(left.sessionId === props.currentSessionId)
  return currentOrder
    || left.slotStart.localeCompare(right.slotStart)
    || left.createdAt.localeCompare(right.createdAt)
    || left.taskId.localeCompare(right.taskId)
}))
const COMPOSER_TEXTAREA_MIN_HEIGHT = 40
const COMPOSER_TEXTAREA_MAX_HEIGHT = 260
const composerTextareaHeight = ref(COMPOSER_TEXTAREA_MIN_HEIGHT)
const isResizingComposer = ref(false)
let composerResizeStartY = 0
let composerResizeStartHeight = COMPOSER_TEXTAREA_MIN_HEIGHT
const dropdownOpen = ref(false)
const modelSearch = ref('')
const agentDropdownOpen = ref(false)
const agentSearch = ref('')

function toggleDropdown(event: Event) {
  event.stopPropagation()
  agentDropdownOpen.value = false
  dropdownOpen.value = !dropdownOpen.value
}

function toggleAgentDropdown(event: Event) {
  event.stopPropagation()
  dropdownOpen.value = false
  const nextOpen = !agentDropdownOpen.value
  agentDropdownOpen.value = nextOpen
  if (nextOpen) {
    emit('refresh-agents')
  }
}

function closeDropdown() {
  dropdownOpen.value = false
  agentDropdownOpen.value = false
}

onMounted(() => {
  window.addEventListener('click', closeDropdown)
})

onBeforeUnmount(() => {
  window.removeEventListener('click', closeDropdown)
  stopComposerResize()
})

const composerTextareaStyle = computed<CSSProperties>(() => ({
  height: `${composerTextareaHeight.value}px`,
  maxHeight: `${COMPOSER_TEXTAREA_MAX_HEIGHT}px`,
  resize: 'none'
}))

function clampComposerTextareaHeight(height: number) {
  return Math.min(COMPOSER_TEXTAREA_MAX_HEIGHT, Math.max(COMPOSER_TEXTAREA_MIN_HEIGHT, height))
}

function onComposerResizeMove(event: PointerEvent) {
  if (!isResizingComposer.value) return
  const deltaY = composerResizeStartY - event.clientY
  composerTextareaHeight.value = clampComposerTextareaHeight(composerResizeStartHeight + deltaY)
}

function stopComposerResize() {
  if (!isResizingComposer.value) return
  isResizingComposer.value = false
  window.removeEventListener('pointermove', onComposerResizeMove)
  window.removeEventListener('pointerup', stopComposerResize)
  window.removeEventListener('pointercancel', stopComposerResize)
}

function startComposerResize(event: PointerEvent) {
  if (composerInteractionBlocked.value) return
  if (event.button !== 0 && event.pointerType === 'mouse') return
  isResizingComposer.value = true
  composerResizeStartY = event.clientY
  composerResizeStartHeight = composerTextareaHeight.value
  window.addEventListener('pointermove', onComposerResizeMove)
  window.addEventListener('pointerup', stopComposerResize)
  window.addEventListener('pointercancel', stopComposerResize)
}

/**
 * 点击输入框卡片空白区域时，自动聚焦到内部文本输入框（避开按钮、手柄和下拉框等子元素）
 */
function onComposerCardClick(event: MouseEvent) {
  if (composerInteractionBlocked.value) return
  const target = event.target as HTMLElement | null
  if (
    target?.closest('button') ||
    target?.closest('.figma-chat-composer-resize-handle') ||
    target?.closest('.figma-chat-agent-dropdown') ||
    target?.closest('.figma-chat-model-dropdown')
  ) {
    return
  }
  const textarea = (event.currentTarget as HTMLElement | null)?.querySelector('textarea')
  if (textarea && document.activeElement !== textarea) {
    textarea.focus()
  }
}

const allModels = computed(() => {
  const byValue = new Map<string, any>()
  for (const model of props.models ?? []) {
    byValue.set(modelValue(model), model)
  }
  for (const provider of props.providers ?? []) {
    for (const model of provider.models ?? []) {
      const providerModel = { ...model, providerId: model.providerId ?? provider.providerId }
      byValue.set(modelValue(providerModel), providerModel)
    }
  }
  return Array.from(byValue.values())
})

const recommendedModels = computed(() => {
  return allModels.value.slice(0, 4)
})

function getModelColor(model: any) {
  const name = (model.name || '').toLowerCase()
  if (name.includes('glm')) return '#18a978'
  if (name.includes('kimi')) return '#3366ff'
  if (name.includes('gpt')) return '#a855f7'
  if (name.includes('seedance') || name.includes('deepseek')) return '#f97316'
  return '#64748b'
}

function getProviderName(providerId?: string) {
  if (!providerId) return '其他'
  const provider = props.providers?.find((item) => item.providerId === providerId)
  if (provider?.name) return provider.name
  const names: Record<string, string> = {
    'openai': 'OpenAI',
    'anthropic': 'Anthropic',
    'google': 'Google',
    'moonshot': 'Moonshot Kimi',
    'deepseek': 'DeepSeek',
    'zhipu': '智谱 AI',
    'alibaba': '通义千问',
    'qwen': '通义千问',
  }
  return names[providerId.toLowerCase()] || providerId
}

const modelGroups = computed(() => {
  const keyword = modelSearch.value.trim().toLowerCase()
  const groups = new Map<string, { providerId: string; providerName: string; models: any[] }>()
  
  allModels.value.forEach((model) => {
    const haystack = `${model.name} ${model.id} ${model.providerId ?? ''}`.toLowerCase()
    if (keyword && !haystack.includes(keyword)) {
      return
    }
    const providerId = model.providerId || 'unknown'
    const existing = groups.get(providerId)
    if (existing) {
      existing.models.push(model)
    } else {
      groups.set(providerId, {
        providerId,
        providerName: getProviderName(providerId),
        models: [model]
      })
    }
  })
  
  return Array.from(groups.values()).filter((group) => group.models.length > 0)
})

function selectModel(model: any) {
  emit('select-model', model)
  dropdownOpen.value = false
}

function agentValue(agent: AgentInfo): string {
  return agent.agentId || agent.name
}

const BILINGUAL_DISPLAY_NAME_PATTERN = /^([A-Za-z][A-Za-z0-9 &+/-]*?)（([^）\n]+)）[。；]\s*/

// 公共配置首句统一为“English（中文）”；界面以英文为主、中文为辅，运行时仍提交稳定技术 ID。
function configuredDisplayName(name: string, description?: string): string {
  return description?.match(BILINGUAL_DISPLAY_NAME_PATTERN)?.[1]?.trim() || name
}

function configuredDescription(description?: string): string {
  if (!description) return ''
  const match = description.match(BILINGUAL_DISPLAY_NAME_PATTERN)
  if (!match) return description
  const details = description.slice(match[0].length).trim()
  return details ? `${match[2].trim()} · ${details}` : match[2].trim()
}

function agentLabel(agent: AgentInfo): string {
  return configuredDisplayName(agent.name || agent.agentId, agent.description)
}

function agentDescription(agent: AgentInfo): string {
  return configuredDescription(agent.description)
}

function agentMatches(agent: AgentInfo, query: string): boolean {
  if (!query) return true
  const q = query.toLowerCase()
  return `${agent.name} ${agent.agentId} ${agent.description ?? ''}`.toLowerCase().includes(q)
}

const mainAgentOptions = computed(() =>
  props.agents.filter((agent) => !agent.hidden && agent.mode !== 'subagent')
)

const mentionAgentOptions = computed(() =>
  props.agents.filter((agent) => !agent.hidden && agent.mode !== 'primary')
)

const filteredMainAgents = computed(() =>
  mainAgentOptions.value.filter((agent) => agentMatches(agent, agentSearch.value.trim()))
)

const selectedAgentInfo = computed(() => {
  if (!props.selectedAgent) return mainAgentOptions.value[0]
  return mainAgentOptions.value.find(
    (agent) => agentValue(agent) === props.selectedAgent || agent.name === props.selectedAgent
  ) ?? mainAgentOptions.value[0]
})

const selectedAgentLabel = computed(() => {
  const agent = selectedAgentInfo.value
  return agent ? agentLabel(agent) : '选择 Agent'
})

function isSelectedAgent(agent: AgentInfo): boolean {
  const selected = props.selectedAgent || agentValue(selectedAgentInfo.value ?? agent)
  return agentValue(agent) === selected || agent.name === selected
}

function selectAgent(agent: AgentInfo) {
  emit('change-agent', agentValue(agent))
  agentDropdownOpen.value = false
  agentSearch.value = ''
}

function retryAgentCatalog(event: Event) {
  event.stopPropagation()
  emit('refresh-agents')
}
const inputComposing = ref(false)
const negativeFeedbackOpen = ref(false)
const negativeFeedbackRunId = ref('')
const negativeFeedbackReason = ref<AiFeedbackReasonCode | ''>('')
const negativeFeedbackComment = ref('')

const feedbackReasonOptions: Array<{ value: AiFeedbackReasonCode; label: string }> = [
  { value: 'WRONG_ANSWER', label: '回答不正确' },
  { value: 'NOT_HELPFUL', label: '没有帮助' },
  { value: 'DID_NOT_FOLLOW_INSTRUCTION', label: '没按要求执行' },
  { value: 'CODE_QUALITY_LOW', label: '代码质量不满意' },
  { value: 'TEST_RESULT_BAD', label: '测试结果不满意' },
  { value: 'TOO_SLOW', label: '响应太慢' },
  { value: 'TOO_VERBOSE', label: '内容太长' },
  { value: 'TOO_SHORT', label: '内容太少' },
  { value: 'OTHER', label: '其他' },
]

function modelValue(model: { id: string; providerId?: string }) {
  return model.providerId ? `${model.providerId}/${model.id}` : model.id
}

function isPlatformMessageId(id: string | undefined): id is string {
  return /^msg_[0-9a-f]{32}$/i.test(id ?? '')
}

function isRemoteRuntimeMessageId(id: string | undefined): id is string {
  return Boolean(id?.startsWith('msg_') && !isPlatformMessageId(id))
}

function submitNegativeFeedback() {
  if (!negativeFeedbackRunId.value) return
  emit('submit-feedback', {
    runId: negativeFeedbackRunId.value,
    rating: 'NEGATIVE',
    reasonCode: negativeFeedbackReason.value || null,
    comment: negativeFeedbackComment.value.trim() || null,
  })
  negativeFeedbackOpen.value = false
}

function canFeedbackRun(row: { runId?: string; runStatus?: string }): row is { runId: string; runStatus?: string } {
  const status = row.runStatus?.toUpperCase()
  return !activeSubagentSessionId.value
    && !props.historyLoading
    && Boolean(row.runId)
    && (status === 'SUCCEEDED' || status === 'COMPLETED')
}

function runFeedbackFor(runId: string) {
  return props.runFeedbacks?.[runId] ?? null
}

function isRunFeedbackSubmitting(runId: string) {
  return props.feedbackSubmitting?.[runId] === true
}

function submitPositiveRunFeedback(runId: string) {
  emit('submit-feedback', { runId, rating: 'POSITIVE' })
}

function openNegativeRunFeedback(runId: string) {
  const current = runFeedbackFor(runId)
  negativeFeedbackRunId.value = runId
  negativeFeedbackReason.value = current?.rating === 'NEGATIVE' ? current.reasonCode ?? '' : ''
  negativeFeedbackComment.value = current?.rating === 'NEGATIVE' ? current.comment ?? '' : ''
  negativeFeedbackOpen.value = true
}
const wasStopped = ref(false)
const wasCompleted = ref(false)
const wasFailed = ref(false)

function normalizedRuntimeStatus(): string {
  return (props.runtimeStatus ?? '').toUpperCase()
}

function isRuntimeFailureStatus(): boolean {
  return ['FAILED', 'ERROR'].includes(normalizedRuntimeStatus())
}

function isRuntimeStoppedStatus(): boolean {
  return ['CANCELLED', 'STOPPED'].includes(normalizedRuntimeStatus())
}

function isRuntimeSuccessStatus(): boolean {
  return ['SUCCEEDED', 'COMPLETED'].includes(normalizedRuntimeStatus())
}

// ===== 运行计时器 =====
const runStartTime = ref<number | null>(null)
const runDurationSeconds = ref(0)
let runTimerId: ReturnType<typeof setInterval> | null = null

function startRunTimer() {
  stopRunTimer()
  runStartTime.value = Date.now()
  runDurationSeconds.value = 0
  runTimerId = setInterval(() => {
    if (runStartTime.value) {
      runDurationSeconds.value = Math.floor((Date.now() - runStartTime.value) / 1000)
    }
  }, 1000)
}

function stopRunTimer() {
  if (runTimerId) {
    clearInterval(runTimerId)
    runTimerId = null
  }
}

const formattedRunDuration = computed(() => {
  const m = Math.floor(runDurationSeconds.value / 60)
  const s = runDurationSeconds.value % 60
  return `${m}:${s.toString().padStart(2, '0')}s`
})

// ===== 事件驱动提问/权限 dock =====
const questionAnswers = ref<Record<string, string>>({})
const questionMultiAnswers = ref<Record<string, string[]>>({})
const questionCustomAnswers = ref<Record<string, string>>({})
const questionPageByRequestId = ref<Record<string, number>>({})

type FigmaQuestionItem = QuestionRequest['questions'][number]

function answerSingleQuestion(questionId: string, value: string) {
  questionAnswers.value = { ...questionAnswers.value, [questionId]: value }
  questionCustomAnswers.value = { ...questionCustomAnswers.value, [questionId]: '' }
}

function toggleMultiQuestionAnswer(questionId: string, value: string) {
  const current = questionMultiAnswers.value[questionId] ?? []
  const next = current.includes(value)
    ? current.filter((item) => item !== value)
    : [...current, value]
  questionMultiAnswers.value = { ...questionMultiAnswers.value, [questionId]: next }
}

function setQuestionCustomAnswer(question: FigmaQuestionItem, value: string) {
  questionCustomAnswers.value = { ...questionCustomAnswers.value, [question.questionId]: value }
  if (!isMultipleQuestion(question) && value.trim().length > 0) {
    questionAnswers.value = { ...questionAnswers.value, [question.questionId]: '' }
  }
}

function isMultipleQuestion(question: FigmaQuestionItem): boolean {
  return question.kind === 'multiple'
}

function isTextQuestion(question: FigmaQuestionItem): boolean {
  return question.kind === 'text' || !question.options?.length
}

function questionOptions(question: FigmaQuestionItem) {
  return question.options?.length ? question.options : []
}

function currentQuestionIndex(item: QuestionRequest): number {
  const current = questionPageByRequestId.value[item.requestId] ?? 0
  return Math.min(Math.max(current, 0), Math.max(item.questions.length - 1, 0))
}

function currentQuestion(item: QuestionRequest): FigmaQuestionItem | undefined {
  return item.questions[currentQuestionIndex(item)]
}

function currentQuestionRequired(item: QuestionRequest): FigmaQuestionItem {
  return currentQuestion(item) ?? item.questions[0]
}

function questionProgressText(item: QuestionRequest): string {
  return `${currentQuestionIndex(item) + 1}/${item.questions.length} 个问题`
}

function questionTypeText(question: FigmaQuestionItem): string {
  if (isMultipleQuestion(question)) {
    return '多选'
  }
  if (isTextQuestion(question)) {
    return '文本'
  }
  return '单选'
}

function questionHelpText(question: FigmaQuestionItem): string {
  if (isMultipleQuestion(question)) {
    return '可选择多个答案'
  }
  if (isTextQuestion(question)) {
    return '请输入答案'
  }
  return '选择一个答案'
}

function isFirstQuestionPage(item: QuestionRequest): boolean {
  return currentQuestionIndex(item) === 0
}

function isLastQuestionPage(item: QuestionRequest): boolean {
  return currentQuestionIndex(item) >= item.questions.length - 1
}

function showPreviousQuestion(item: QuestionRequest) {
  questionPageByRequestId.value = {
    ...questionPageByRequestId.value,
    [item.requestId]: Math.max(currentQuestionIndex(item) - 1, 0)
  }
}

function showNextQuestion(item: QuestionRequest) {
  questionPageByRequestId.value = {
    ...questionPageByRequestId.value,
    [item.requestId]: Math.min(currentQuestionIndex(item) + 1, Math.max(item.questions.length - 1, 0))
  }
}

function isQuestionOptionSelected(question: FigmaQuestionItem, label: string): boolean {
  if (isMultipleQuestion(question)) {
    return questionMultiAnswers.value[question.questionId]?.includes(label) ?? false
  }
  return questionAnswers.value[question.questionId] === label
}

/**
 * 自定义答案是否已输入并选中
 */
function isCustomAnswerSelected(question: FigmaQuestionItem): boolean {
  const val = questionCustomAnswers.value[question.questionId]
  return typeof val === 'string' && val.trim().length > 0
}

function chooseQuestionOption(question: FigmaQuestionItem, label: string) {
  if (isMultipleQuestion(question)) {
    toggleMultiQuestionAnswer(question.questionId, label)
    return
  }
  answerSingleQuestion(question.questionId, label)
}

function answersForQuestion(question: FigmaQuestionItem): string[] {
  const custom = questionCustomAnswers.value[question.questionId]?.trim()
  if (isMultipleQuestion(question)) {
    const selected = questionMultiAnswers.value[question.questionId] ?? []
    return custom ? [...selected, custom] : selected
  }
  if (isTextQuestion(question)) {
    return custom ? [custom] : []
  }
  const selected = questionAnswers.value[question.questionId]?.trim()
  return custom ? [custom] : selected ? [selected] : []
}

function buildQuestionAnswers(item: QuestionRequest): unknown[][] {
  return item.questions.map((question) => answersForQuestion(question))
}

function canReplyQuestion(item: QuestionRequest): boolean {
  return item.questions.every((question) => answersForQuestion(question).length > 0)
}

function replyQuestion(item: QuestionRequest) {
  if (!canReplyQuestion(item)) return
  emit('reply-question', item.requestId, buildQuestionAnswers(item))
}

// ===== 技能面板 =====
// 直接展示 OpenCode /command 返回的 source=skill 项；Agent 是否可调用由 OpenCode 原生 permission.skill 判定。
type SkillItem = { name: string; description: string; commandId: string }

function skillLabel(skill: SkillItem): string {
  return configuredDisplayName(skill.name, skill.description)
}

function skillDescription(skill: SkillItem): string {
  return configuredDescription(skill.description)
}

// 从 commands 中过滤出 source=skill 的命令作为技能列表
const skills = computed<SkillItem[]>(() => {
  if (!props.commands || props.commands.length === 0) return []
  return props.commands
    .filter((cmd) => cmd.source === 'skill')
    .map((cmd) => ({
      name: cmd.name,
      description: cmd.description || '',
      commandId: cmd.commandId
    }))
})

const showSkillPanel = ref(false)
const skillFilterText = ref('')
const showAgentPanel = ref(false)
const agentFilterText = ref('')
const showRequirementPanel = ref(false)
const requirementFilterText = ref('')

const filteredSkills = computed(() => {
  const q = skillFilterText.value.toLowerCase()
  if (!q) return skills.value
  return skills.value.filter(
    (s) =>
      s.name.toLowerCase().includes(q) ||
      s.description.toLowerCase().includes(q)
  )
})

const filteredMentionAgents = computed(() => {
  const q = agentFilterText.value.trim()
  return mentionAgentOptions.value.filter((agent) => agentMatches(agent, q))
})

const filteredMentionFiles = computed(() => props.workspaceFileCandidates)

const filteredRequirementReferences = computed(() => {
  const q = requirementFilterText.value.trim().toLowerCase()
  if (!q) return props.workspaceRequirementReferences
  return props.workspaceRequirementReferences.filter((reference) =>
    `${reference.requirementName} ${reference.subitemName}`.toLowerCase().includes(q)
  )
})

// 仅匹配输入末尾的 @query，和 opencode 原生 prompt autocomplete 一样不扫描全文。
function currentAgentMentionQuery(text: string): string | null {
  const match = text.match(/(^|\s)@([^\s@]*)$/)
  return match ? match[2] : null
}

function replaceAgentMentionQuery(text: string, label: string): string {
  return text.replace(/(^|\s)@([^\s@]*)$/, `$1@${label} `)
}

function currentRequirementQuery(text: string): string | null {
  const match = text.match(/(^|\s)#([^\s#]*)$/)
  return match ? match[2] : null
}

function removeCurrentReferenceQuery(text: string, marker: '@' | '#'): string {
  const pattern = marker === '@' ? /(^|\s)@([^\s@]*)$/ : /(^|\s)#([^\s#]*)$/
  return text.replace(pattern, '$1')
}

function onSkillInput(text: string) {
  const trimmed = text.trimStart()
  // 已选命令后的空格表示进入参数输入阶段，此时不再重复打开技能检索面板。
  if (/^\/\S*$/.test(trimmed) && !props.running) {
    const afterSlash = trimmed.slice(1)
    skillFilterText.value = afterSlash
    showSkillPanel.value = true
    showAgentPanel.value = false
    agentFilterText.value = ''
    showRequirementPanel.value = false
    requirementFilterText.value = ''
    emit('search-workspace-files', null)
  } else {
    showSkillPanel.value = false
    skillFilterText.value = ''
  }
}

function onAgentMentionInput(text: string) {
  const query = currentAgentMentionQuery(text)
  if (query !== null && !props.running) {
    agentFilterText.value = query
    showAgentPanel.value = true
    showRequirementPanel.value = false
    requirementFilterText.value = ''
    emit('search-workspace-files', query)
  } else {
    showAgentPanel.value = false
    agentFilterText.value = ''
    emit('search-workspace-files', null)
  }
}

function onRequirementInput(text: string): boolean {
  const query = currentRequirementQuery(text)
  if (query !== null && !props.running) {
    const shouldLoadRequirements = !showRequirementPanel.value
    requirementFilterText.value = query
    showRequirementPanel.value = true
    showAgentPanel.value = false
    agentFilterText.value = ''
    emit('search-workspace-files', null)
    if (shouldLoadRequirements) {
      emit('load-workspace-requirements')
    }
    return true
  }
  showRequirementPanel.value = false
  requirementFilterText.value = ''
  return false
}

function onComposerInput(text: string) {
  onSkillInput(text)
  if (showSkillPanel.value) {
    return
  }
  if (onRequirementInput(text)) {
    return
  }
  onAgentMentionInput(text)
}

function selectSkill(skill: SkillItem) {
  // 使用真实的 Skill Command，插入 `/skill-name ` 格式
  // 用户可以补充参数后发送，由工作台的 parseCommand 解析并通过平台 Run 启动技能。
  const commandText = `/${skill.name} `
  localInput.value = commandText
  emit('update:inputValue', commandText)
  showSkillPanel.value = false
  skillFilterText.value = ''
}

function selectMentionAgent(agent: AgentInfo) {
  const commandText = replaceAgentMentionQuery(localInput.value, agentValue(agent))
  localInput.value = commandText
  emit('update:inputValue', commandText)
  showAgentPanel.value = false
  agentFilterText.value = ''
}

function selectMentionFile(file: FileSearchResult) {
  const nextText = removeCurrentReferenceQuery(localInput.value, '@')
  localInput.value = nextText
  emit('update:inputValue', nextText)
  emit('add-workspace-file-context', file.path)
  dismissAgentPanel()
}

function selectRequirementReference(reference: WorkspaceRequirementReference) {
  const nextText = removeCurrentReferenceQuery(localInput.value, '#')
  localInput.value = nextText
  emit('update:inputValue', nextText)
  emit('add-workspace-requirement-context', reference)
  dismissRequirementPanel()
}

function dismissSkillPanel() {
  showSkillPanel.value = false
  skillFilterText.value = ''
}

function dismissAgentPanel() {
  showAgentPanel.value = false
  agentFilterText.value = ''
  emit('search-workspace-files', null)
}

function dismissRequirementPanel() {
  showRequirementPanel.value = false
  requirementFilterText.value = ''
}

// ===== 文件变更抽屉 =====
// 抽屉默认选中第一个文件；打开后通过 fileChanges 变化自动跟随到最新一个文件（与原有的"跟随最近一次变化"心智一致）。
const drawerOpen = ref(false)
const drawerSelectedPath = ref<string>('')
const drawerScroll = ref<HTMLElement | null>(null)
const attachmentDialogOpen = ref(false)
// 是否在 diff 视图中显示 unified diff 的上下文行（未改动的行）。
// 默认关闭：用户在文件变更抽屉里通常只想看真正的 +/- 行，避免出现
// "只改一行但全文飘红" 的体验。当后端 patch 是整文件重写时，关闭上下文
// 仍然会看到完整的 del+add 列表，但能配合新增的 toggle 切换为完整上下文做核对。
const showContext = ref(false)
const thinkingExpanded = ref(false)
// 文件操作清单展开/收起（按消息ID+类型独立控制）
const expandedFileKeys = ref(new Set<string>())
function toggleFileExpanded(msgId: string, opType: string) {
  const key = `${msgId}:${opType}`
  const next = new Set(expandedFileKeys.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expandedFileKeys.value = next
}
function isFileExpanded(msgId: string, opType: string) {
  return expandedFileKeys.value.has(`${msgId}:${opType}`)
}
function messageExpandedState(msgId: string): string {
  return Array.from(expandedFileKeys.value)
    .filter((k) => k.startsWith(msgId + ':'))
    .join(',')
}

// 从 tool input 中提取文件路径，bash 命令则尝试从 command 中解析
function toolFilePath(
  toolName: string,
  input: Record<string, unknown> | undefined
): string {
  if (!input) return ''
  const direct =
    (typeof input.filePath === 'string' && input.filePath) ||
    (typeof input.path === 'string' && input.path) ||
    (typeof input.file_path === 'string' && input.file_path) ||
    (typeof input.file === 'string' && input.file) ||
    (typeof input.directory === 'string' && input.directory) ||
    (typeof input.target === 'string' && input.target) ||
    ''
  if (direct && direct !== 'null') return direct
  // bash 命令：从 command 字符串中提取路径参数
  if (toolName === 'bash' && typeof input.command === 'string') {
    const cmd = input.command
    const parts = cmd.split(/\s+/)
    // 从后往前找第一个像路径的参数（含 /、扩展名、或者不是 flag/关键字）
    for (let i = parts.length - 1; i >= 0; i--) {
      const p = parts[i]
      if (p === 'null') continue
      if (p.includes('/') || p.includes('.') || p === '.' || p === '..') {
        return p
      }
    }
    // 没有明显的路径特征时，取最后一个非 flag、非管道/重定向符号的参数
    const skipKeywords = new Set([
      '&&',
      '||',
      '|',
      '>',
      '>>',
      '<',
      '<<',
      ';',
      '&',
    ])
    for (let i = parts.length - 1; i >= 0; i--) {
      const p = parts[i]
      if (
        p.startsWith('-') ||
        p.startsWith('"') ||
        p.startsWith("'") ||
        skipKeywords.has(p)
      )
        continue
      // 跳过常见命令名
      if (
        i === 0 &&
        /^(ls|cd|cat|echo|find|grep|mkdir|rm|mv|cp|touch|head|tail|tree|wc|file|stat|dirname|basename|realpath|readlink|source|export|set|unset|env|which|type|command|exec|xargs|sort|uniq|cut|tr|sed|awk|diff|cmp|chmod|chown|chgrp)$/.test(
          p
        )
      )
        continue
      return p
    }
  }
  return ''
}

// 从最新消息中提取思考过程（reasoning + tool 操作摘要）
function toolSummary(
  toolName: string,
  input: Record<string, unknown> | undefined
): string {
  const name = toolName || 'tool'
  if (!input) return `[${name}]`
  const file = toolFilePath(toolName, input)
  if (file) return `[${name}] ${file}`
  // bash / 命令类工具：展示描述或命令
  const desc = typeof input.description === 'string' ? input.description : ''
  const cmd = typeof input.command === 'string' ? input.command : ''
  if (desc) return `[${name}] ${desc}`
  if (cmd)
    return `[${name}] ${cmd.length > 60 ? cmd.slice(0, 60) + '...' : cmd}`
  return `[${name}]`
}
const thinkingLines = computed(() => {
  const lines: string[] = []
  for (let i = (props.messages || []).length - 1; i >= 0; i -= 1) {
    const msg = props.messages[i]
    // assistant 消息的 parts（reasoning / tool part）
    if (msg.role === 'assistant' && Array.isArray(msg.parts)) {
      for (const p of msg.parts) {
        if (p.type === 'reasoning' && p.text) {
          lines.push(p.text)
        } else if (p.type === 'tool') {
          const tool = p as {
            type: 'tool'
            toolName?: string
            input?: Record<string, unknown>
          }
          lines.push(toolSummary(tool.toolName ?? 'tool', tool.input))
        }
      }
    }
    // card 消息中的工具调用（tool.started / tool.finished 事件）
    if (
      msg.role === 'card' &&
      (msg as { cardType?: string }).cardType === 'tool'
    ) {
      const card = msg as unknown as {
        role: 'card'
        cardType: string
        payload?: Record<string, unknown>
      }
      const payload = card.payload ?? {}
      const name =
        (typeof payload.toolName === 'string' && payload.toolName) ||
        (typeof payload.name === 'string' && payload.name) ||
        (typeof payload.tool === 'string' && payload.tool) ||
        'tool'
      lines.push(
        toolSummary(name, (payload.input as Record<string, unknown>) ?? {})
      )
    }
    if (lines.length > 0) return lines
  }
  return lines
})
// 统计 message.updated 事件中 tool part 的文件操作
type FileOperation = {
  toolName: string
  filePath: string
  opType: 'read' | 'write' | 'edit'
  content?: string
}
const FILE_READ_TOOLS = new Set(['read', 'grep', 'glob', 'bash'])
const FILE_WRITE_TOOLS = new Set(['write', 'create_file'])
const FILE_EDIT_TOOLS = new Set([
  'edit',
  'apply_patch',
  'str_replace',
  'multi_edit',
])
// 从 tool input 中提取文件内容（write 工具用 content/text，edit 工具用 newText/new_str）
function toolInputContent(input: Record<string, unknown> | undefined): string {
  if (!input) return ''
  return (
    (typeof input.content === 'string' && input.content) ||
    (typeof input.text === 'string' && input.text) ||
    (typeof input.body === 'string' && input.body) ||
    (typeof input.newText === 'string' && input.newText) ||
    (typeof input.new_str === 'string' && input.new_str) ||
    (typeof input.newString === 'string' && input.newString) ||
    ''
  )
}

// 根据文件扩展名推断语言，返回高亮后的 HTML 片段
function highlightCode(code: string, filePath: string): string {
  const ext = (filePath.split('.').pop() ?? '').toLowerCase()
  let lang: string = ext
  if (ext === 'vue' || ext === 'svelte') lang = 'html'
  else if (
    ext === 'ts' ||
    ext === 'tsx' ||
    ext === 'js' ||
    ext === 'jsx' ||
    ext === 'mjs' ||
    ext === 'cjs'
  )
    lang = 'js'
  else if (ext === 'json' || ext === 'jsonc' || ext === 'json5') lang = 'json'
  else if (
    ext === 'css' ||
    ext === 'scss' ||
    ext === 'less' ||
    ext === 'sass' ||
    ext === 'styl'
  )
    lang = 'css'
  else if (ext === 'md' || ext === 'mdx' || ext === 'markdown') lang = 'md'
  else if (ext === 'yaml' || ext === 'yml') lang = 'yaml'
  else if (ext === 'sh' || ext === 'bash' || ext === 'zsh') lang = 'bash'
  else if (
    ext === 'py' ||
    ext === 'rb' ||
    ext === 'go' ||
    ext === 'rs' ||
    ext === 'java' ||
    ext === 'kt' ||
    ext === 'swift'
  )
    lang = 'code'
  else if (ext === 'sql') lang = 'sql'
  else if (ext === 'xml' || ext === 'svg') lang = 'xml'
  else if (ext === 'html' || ext === 'htm') lang = 'html'
  else if (ext === 'graphql' || ext === 'gql') lang = 'graphql'
  else lang = ''

  const escaped = code
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  if (lang === 'html' || lang === 'xml') {
    return escaped
      .replace(/(&lt;\!--[\s\S]*?--&gt;)/g, '<span class="ch">$1</span>')
      .replace(/(&lt;\/?)([\w-]+)/g, '$1<span class="kt">$2</span>')
      .replace(/(\s+)([\w-]+)(=)/g, '$1<span class="na">$2</span>$3')
      .replace(/(=)(&quot;|&#39;)(.*?)(\2)/g, '$1$2<span class="s">$3</span>$4')
      .replace(/(&lt;!--[\s\S]*?--&gt;)/g, '<span class="ch">$1</span>')
  }
  if (lang === 'js' || lang === 'json') {
    return escaped
      .replace(/(\/\/[^\n]*)/g, '<span class="ch">$1</span>')
      .replace(/(&quot;.*?&quot;)(?=\s*:)/g, '<span class="na">$1</span>')
      .replace(
        /(&quot;.*?&quot;|&#39;.*?&#39;|`[^`]*`)/g,
        '<span class="s">$1</span>'
      )
      .replace(
        /\b(true|false|null|undefined|NaN|Infinity)\b/g,
        '<span class="nb">$1</span>'
      )
      .replace(
        /\b(const|let|var|function|return|if|else|for|while|do|switch|case|break|continue|new|class|extends|import|export|from|default|async|await|try|catch|throw|finally|typeof|instanceof|in|of|yield|static|get|set|as|type|interface|enum|implements|abstract|private|public|protected|readonly)\b/g,
        '<span class="k">$1</span>'
      )
      .replace(/\b(\d+\.?\d*(?:e[+-]?\d+)?)\b/g, '<span class="m">$1</span>')
  }
  if (lang === 'css') {
    return escaped
      .replace(/(\/\*[\s\S]*?\*\/)/g, '<span class="ch">$1</span>')
      .replace(/([.#@][\w-]+)/g, '<span class="nc">$1</span>')
      .replace(/(&quot;.*?&quot;|&#39;.*?&#39;)/g, '<span class="s">$1</span>')
      .replace(/(:[\s]*)([^;{}]+)/g, '$1<span class="m">$2</span>')
      .replace(/\b([\w-]+)(?=\s*:)/g, '<span class="na">$1</span>')
  }
  if (lang === 'md') {
    return escaped
      .replace(/^(#{1,6}\s+.+)$/gm, '<span class="k">$1</span>')
      .replace(/(`[^`]+`)/g, '<span class="s">$1</span>')
      .replace(/(\*\*[^*]+\*\*|__[^_]+__)/g, '<span class="nb">$1</span>')
      .replace(/^(\s*[-*+>]\s+.+)$/gm, '<span class="na">$1</span>')
  }
  if (lang === 'bash') {
    return escaped
      .replace(/^(#.*)$/gm, '<span class="ch">$1</span>')
      .replace(/(&quot;.*?&quot;|&#39;.*?&#39;)/g, '<span class="s">$1</span>')
      .replace(
        /\b(ls|cd|cat|echo|find|grep|mkdir|rm|mv|cp|touch|head|tail|tree|wc|file|stat|npm|pnpm|yarn|node|python|git|docker|curl|wget|chmod|chown|export|source|set|unset|env|which|if|then|else|fi|for|do|done|while|case|esac|function|return|exit|exec|xargs|sort|uniq|cut|tr|sed|awk|diff|cmp|make)\b/g,
        '<span class="k">$1</span>'
      )
      .replace(/(\s|^)(--?[\w-]+)/g, '$1<span class="m">$2</span>')
  }
  if (lang === 'yaml') {
    return escaped
      .replace(/^(#.*)$/gm, '<span class="ch">$1</span>')
      .replace(/(&quot;.*?&quot;|&#39;.*?&#39;)/g, '<span class="s">$1</span>')
      .replace(/^(\s*)([\w-]+)(?=\s*:)/gm, '$1<span class="na">$2</span>')
      .replace(
        /\b(true|false|null|yes|no|on|off)\b/g,
        '<span class="nb">$1</span>'
      )
  }
  if (lang === 'sql') {
    return escaped
      .replace(/(--[^\n]*)/g, '<span class="ch">$1</span>')
      .replace(/(&#39;.*?&#39;)/g, '<span class="s">$1</span>')
      .replace(
        /\b(SELECT|FROM|WHERE|INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|TABLE|INDEX|INTO|VALUES|SET|AND|OR|NOT|IN|LIKE|BETWEEN|JOIN|LEFT|RIGHT|INNER|OUTER|ON|AS|ORDER|BY|GROUP|HAVING|LIMIT|OFFSET|UNION|ALL|DISTINCT|COUNT|SUM|AVG|MIN|MAX|CASE|WHEN|THEN|ELSE|END|NULL|PRIMARY|KEY|FOREIGN|REFERENCES|CASCADE|DEFAULT|CHECK|UNIQUE|CONSTRAINT|EXISTS|IF|BEGIN|COMMIT|ROLLBACK|TRANSACTION)\b/gi,
        '<span class="k">$1</span>'
      )
      .replace(/\b(\d+\.?\d*)\b/g, '<span class="m">$1</span>')
  }
  if (lang === 'graphql') {
    return escaped
      .replace(/(#[^\n]*)/g, '<span class="ch">$1</span>')
      .replace(/(&quot;.*?&quot;)/g, '<span class="s">$1</span>')
      .replace(
        /\b(query|mutation|subscription|fragment|on|type|input|enum|interface|union|scalar|schema|extend|directive|implements)\b/g,
        '<span class="k">$1</span>'
      )
      .replace(/\b(true|false|null)\b/g, '<span class="nb">$1</span>')
  }
  if (lang === 'code') {
    return escaped
      .replace(/(\/\/[^\n]*|#[^\n]*)/g, '<span class="ch">$1</span>')
      .replace(
        /(&quot;.*?&quot;|&#39;.*?&#39;|""".*?""")/g,
        '<span class="s">$1</span>'
      )
      .replace(
        /\b(def|class|function|return|if|else|elif|for|while|import|from|as|try|except|raise|pass|break|continue|with|yield|lambda|async|await|fn|let|mut|pub|struct|impl|enum|trait|mod|use|self|super|where|match|loop|move|ref|unsafe|extern|crate|public|private|protected|static|final|void|int|string|bool|float|double|var|val|fun|object|package|new|this|throw|throws|catch|finally|extends|implements|abstract|interface|override|virtual|sealed|internal|namespace|using|global|require|module|export|default|type|const|interface|declare|readonly|keyof|infer|extends|implements)\b/g,
        '<span class="k">$1</span>'
      )
      .replace(
        /\b(true|false|null|nil|None|True|False|undefined)\b/g,
        '<span class="nb">$1</span>'
      )
      .replace(/\b(\d+\.?\d*(?:e[+-]?\d+)?)\b/g, '<span class="m">$1</span>')
  }
  return escaped
}
// 从单条消息提取文件操作明细
function messageFileOps(msg: FileOperationMessage): FileOperation[] {
  if (msg.role !== 'assistant' || !Array.isArray(msg.parts)) return []
  const ops: FileOperation[] = []
  for (const p of msg.parts) {
    if (p.type !== 'tool') continue
    const toolName = (p.toolName ?? '').toLowerCase()
    const input = p.input as Record<string, unknown> | undefined
    const filePath = toolFilePath(toolName, input)
    if (!filePath) continue
    if (FILE_READ_TOOLS.has(toolName)) {
      ops.push({ toolName, filePath, opType: 'read' })
    } else if (FILE_WRITE_TOOLS.has(toolName)) {
      ops.push({
        toolName,
        filePath,
        opType: 'write',
        content: toolInputContent(input),
      })
    } else if (FILE_EDIT_TOOLS.has(toolName)) {
      ops.push({
        toolName,
        filePath,
        opType: 'edit',
        content: toolInputContent(input),
      })
    }
  }
  return ops
}
function messageReadCount(msg: FileOperationMessage): number {
  if (msg.role !== 'assistant' || !Array.isArray(msg.parts)) return 0
  let count = 0
  for (const p of msg.parts) {
    if (
      p.type === 'tool' &&
      FILE_READ_TOOLS.has((p.toolName ?? '').toLowerCase())
    )
      count += 1
  }
  return count
}
function messageWriteCount(msg: FileOperationMessage): number {
  if (msg.role !== 'assistant' || !Array.isArray(msg.parts)) return 0
  let count = 0
  for (const p of msg.parts) {
    if (
      p.type === 'tool' &&
      FILE_WRITE_TOOLS.has((p.toolName ?? '').toLowerCase())
    )
      count += 1
  }
  return count
}
function messageEditCount(msg: FileOperationMessage): number {
  if (msg.role !== 'assistant' || !Array.isArray(msg.parts)) return 0
  let count = 0
  for (const p of msg.parts) {
    if (
      p.type === 'tool' &&
      FILE_EDIT_TOOLS.has((p.toolName ?? '').toLowerCase())
    )
      count += 1
  }
  return count
}

// 操作按文件路径去重，保留每个文件的最后一次操作
function uniqueOps(
  msg: FileOperationMessage,
  opType: 'edit' | 'write'
): FileOperation[] {
  if (msg.role !== 'assistant' || !Array.isArray(msg.parts)) return []
  const seen = new Map<string, FileOperation>()
  const ops = messageFileOps(msg).filter((o) => o.opType === opType)
  for (const op of ops) {
    seen.set(op.filePath, op)
  }
  return Array.from(seen.values())
}

const reasoningText = computed(() => thinkingLines.value.join('\n'))
const reasoningHtml = computed(() =>
  reasoningText.value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\[(\w+)\]/g, '<strong>[$1]</strong>')
)

// 渲染 markdown 内容，返回安全的 HTML
// 把 unified diff 文本按行解析为带 kind 的结构，供右侧 git-merge 风格渲染使用。
// 解析规则与 git apply 一致：每行首字符决定 kind；hunk header "@@" 重置行号计数器。
// 没有 patch 文本（部分 diff.proposed 事件只带 path/additions/deletions）时返回空数组，由模板降级展示空态。
function parseDiffLines(patch: string | undefined): DiffLine[] {
  if (!patch) return []
  const result: DiffLine[] = []
  let oldLine = 0
  let newLine = 0
  for (const raw of patch.split('\n')) {
    if (raw.startsWith('@@')) {
      const match = /^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@/.exec(raw)
      if (match) {
        oldLine = Number(match[1])
        newLine = Number(match[2])
      }
      // hunk header 始终原样展示，不去掉 @@ 前缀
      result.push({ kind: 'meta', text: raw })
      continue
    }
    if (raw.startsWith('---') || raw.startsWith('+++')) {
      // 文件头 a/path b/path 不进入行号计数
      result.push({ kind: 'meta', text: raw })
      continue
    }
    if (raw.startsWith('+')) {
      result.push({ kind: 'add', newNum: newLine++, text: raw.slice(1) })
      continue
    }
    if (raw.startsWith('-')) {
      result.push({ kind: 'del', oldNum: oldLine++, text: raw.slice(1) })
      continue
    }
    // context 行可能以空格开头，也可能没有前缀（malformed）
    const text = raw.startsWith(' ') ? raw.slice(1) : raw
    result.push({ kind: 'ctx', oldNum: oldLine++, newNum: newLine++, text })
  }
  return result
}

const hasFileChanges = computed(() => (props.fileChanges?.length ?? 0) > 0)

// 推断 opencode 专属进程当前网络地址（与头像菜单保持一致），
// linuxServerId 只代表稳定服务器身份，不能再拿来拼 host:port。
function resolveServiceAddress(process?: OpencodeProcessState | null): string {
  if (!process) return ''
  if (process.serviceAddress?.trim()) return process.serviceAddress.trim()
  // baseUrl 形如 http://host:port，退化取 host:port
  try {
    if (process.baseUrl) {
      const url = new URL(process.baseUrl)
      return url.hostname && url.port ? `${url.hostname}:${url.port}` : ''
    }
  } catch {
    /* ignore */
  }
  return ''
}

function resolveServerName(process?: OpencodeProcessState | null): string {
  return process?.linuxServerId?.trim() ?? ''
}

function resolveServiceTarget(process?: OpencodeProcessState | null): string {
  const serverName = resolveServerName(process)
  const address = resolveServiceAddress(process)
  if (serverName && address) return `${serverName} / ${address}`
  return serverName || address
}

const processReady = computed(() => {
  if (props.processRequired) {
    return props.processStatus?.status === 'READY'
  }
  return !props.processStatus || props.processStatus.status === 'READY'
})
const publicConfigMessageBlocked = computed(() => props.processStatus?.messageSendAllowed === false)
const publicConfigMessageBlockedReason = computed(
  () => props.processStatus?.messageSendBlockedReason?.trim()
    || '公共 Agent/Skill 配置正在同步，旧会话排空后将自动恢复发送'
)
const composerInteractionBlocked = computed(() => !processReady.value)
const nightSessionLocked = computed(() => {
  const status = props.currentNightTask?.status
  return status === 'SCHEDULED' || status === 'DISPATCHING'
})
const nightSessionLockedReason = computed(() =>
  props.currentNightTask?.status === 'DISPATCHING'
    ? '夜间任务正在启动，执行完成后可继续对话'
    : '当前对话已有待执行夜间任务，取消后可继续对话'
)
const agentPickerDisabled = computed(() => composerInteractionBlocked.value)
const modelSelectionDisabled = computed(() => props.modelPickerDisabled === true || composerInteractionBlocked.value)
const processSubmitBlocked = computed(
  () =>
    props.running ||
    !processReady.value ||
    publicConfigMessageBlocked.value ||
    (props.processRefreshing && props.processRefreshBlocksSubmit !== false)
)
const newConversationBlocked = computed(
  () => !processReady.value
    || publicConfigMessageBlocked.value
    || (props.processRefreshing && props.processRefreshBlocksSubmit !== false)
)
const readonlyBlockedReason = computed(() => props.readonlyReason?.trim() ?? '')
const readonlySubmitBlocked = computed(() => Boolean(readonlyBlockedReason.value))
const contextSendValidation = computed(() => validateChatSend(localInput.value.trim(), props.chatContexts ?? []))
const contextSubmitBlocked = computed(() => !contextSendValidation.value.ok || props.chatContextOverLimit === true)
const contextSendBlockedReason = computed(() => {
  if (!contextSendValidation.value.ok) return contextSendValidation.value.reason
  if (props.chatContextOverLimit) return '上下文超过限制，无法发送'
  return ''
})
const sendBlockedTitle = computed(() => {
  if (!processReady.value) return '请先初始化 TestAgent 进程'
  if (publicConfigMessageBlocked.value) return publicConfigMessageBlockedReason.value
  if (nightSessionLocked.value) return nightSessionLockedReason.value
  return readonlyBlockedReason.value || contextSendBlockedReason.value || '发送'
})
const sendSubmitBlocked = computed(
  () => props.historyLoading === true
    || props.historySubmitBlocked === true
    || processSubmitBlocked.value
    || nightSessionLocked.value
    || readonlySubmitBlocked.value
    || contextSubmitBlocked.value
)
const composerPlaceholder = computed(() => {
  if (props.processLoading && !props.processStatus) return '正在检查 TestAgent 进程…'
  if (!processReady.value) return '请先初始化 TestAgent 进程'
  if (publicConfigMessageBlocked.value) return publicConfigMessageBlockedReason.value
  if (nightSessionLocked.value) return nightSessionLockedReason.value
  return props.placeholder || 'Ask the AI agent...'
})
const nightScheduleBlocked = computed(() =>
  !localInput.value.trim()
    || props.running === true
    || composerInteractionBlocked.value
    || publicConfigMessageBlocked.value
    || props.historyLoading === true
    || props.historySubmitBlocked === true
    || readonlySubmitBlocked.value
    || contextSubmitBlocked.value
    || nightSessionLocked.value
    || props.nightTaskSubmitting === true
)
const processStatusVisible = computed(
  () =>
    props.processStatusPlacement !== 'pet' &&
    (props.processRequired || props.processLoading || props.processStatus != null)
)
// 有效 serviceStatus：优先用后端返回值，缺失时按头像菜单同样规则回退推断
// （READY→RUNNING；否则有地址→NOT_RUNNING；无地址→UNASSIGNED），保证两处展示一致
const effectiveServiceStatus = computed<string>(() => {
  const p = props.processStatus
  if (p?.serviceStatus) return p.serviceStatus
  if (p?.status === 'READY') return 'RUNNING'
  return resolveServiceTarget(p) ? 'NOT_RUNNING' : 'UNASSIGNED'
})
const processStatusTitle = computed(() => {
  if (props.processLoading && !props.processStatus) return '正在检查 TestAgent 进程'
  if (props.processRequired && !props.processStatus)
    return 'TestAgent 进程状态未知'
  if (!props.processStatus) return ''
  if (props.processStatus.status === 'READY') return 'TestAgent 进程可用'
  if (props.processStatus.status === 'NEEDS_INITIALIZATION') {
    // 二级状态区分"尚未分配"与"已分配未运行"，与头像菜单一致
    if (effectiveServiceStatus.value === 'NOT_RUNNING') return 'TestAgent 专属进程未运行'
    if (effectiveServiceStatus.value === 'UNASSIGNED') return '尚未分配 TestAgent 专属进程'
    return '需要初始化 TestAgent 进程' // serviceStatus 异常兜底
  }
  return 'TestAgent 进程不可用'
})
// 初始化按钮文案：未分配→分配专属进程，已分配未运行→启动进程；进行中分别显示分配中/启动中
const processInitButtonLabel = computed(() => {
  const starting = effectiveServiceStatus.value === 'NOT_RUNNING'
  if (props.processInitializing) return starting ? '启动中' : '分配中'
  return starting ? '启动进程' : '分配专属进程'
})
const processStatusText = computed(() => {
  if (props.processLoading && !props.processStatus)
    return '正在检查当前用户可用进程'
  if (props.processRequired && !props.processStatus)
    return '请刷新进程状态后重试'
  if (!props.processStatus) return ''
  return resolveServiceTarget(props.processStatus) || props.processStatus.message
})

const activeSubagentSessionId = ref<string | null>(null)

// 主会话只展示自己的 ask；进入 child 后只展示 child 自己的 ask，避免把两个会话的请求混在一起。
const interactionSessionId = computed(() => activeSubagentSessionId.value ?? props.currentSessionId)
const visiblePermissions = computed(() => {
  const sessionId = interactionSessionId.value
  return sessionId ? props.permissions.filter((item) => item.sessionId === sessionId) : props.permissions
})
const visibleQuestions = computed(() => {
  const sessionId = interactionSessionId.value
  return sessionId ? props.questions.filter((item) => item.sessionId === sessionId) : props.questions
})

watch(
  () => props.currentSessionId,
  (sessionId, previousSessionId) => {
    // 历史会话切换复用同一个面板实例；不能把上个会话的 child 选择带到新 root。
    if (sessionId && previousSessionId && sessionId !== previousSessionId) {
      activeSubagentSessionId.value = null
    }
  }
)

// 没有用户拖拽记录时沿用原对话顶部的内联状态卡；只有用户拖动后才进入可持久化的浮动模式。
const processStatusCollapsed = ref(false)
const processStatusFloating = ref(false)
const processStatusDotVisible = computed(
  () =>
    processStatusVisible.value &&
    processStatusCollapsed.value &&
    processReady.value &&
    processStatusFloating.value
)
// 观察器以卡片实际挂载条件为准，子 Agent 视图会卸载卡片，不能只看进程状态。
const processStatusCardVisible = computed(
  () =>
    !activeSubagentSessionId.value &&
    processStatusVisible.value &&
    !processStatusDotVisible.value
)
watch(
  () => props.processStatus?.status,
  (status) => {
    if (status && status !== 'READY') {
      processStatusCollapsed.value = false
    }
  }
)
function toggleProcessStatus() {
  if (!processStatusCollapsed.value) {
    // 首次从历史内联卡片收起时，以当前卡片左上角建立唯一锚点；展开仍会回到这里。
    prepareCardForFloatingDrag()
  }
  processStatusCollapsed.value = !processStatusCollapsed.value
}

// 收起态小圆点和展开态卡片共享同一个卡片左上角锚点，避免两套坐标在边缘和缩放时漂移。
const PROCESS_DOT_POS_KEY = 'figma-chat-process-dot-pos'
const PROCESS_DOT_SIZE = 8
const PROCESS_DOT_MARGIN = 16
const PROCESS_DOT_DRAG_THRESHOLD = 4
const PROCESS_STATUS_CARD_GAP = 8
const PROCESS_STATUS_CARD_MARGIN = 16
const PROCESS_STATUS_CARD_FALLBACK_SIZE = { width: 280, height: 84 }
const PROCESS_STATUS_CARD_VIEWPORT_INSET = PROCESS_STATUS_CARD_MARGIN * 2
type ProcessStatusDotSide = 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right'
const processStatusCard = ref<HTMLElement | null>(null)
const processStatusCardSize = ref({ ...PROCESS_STATUS_CARD_FALLBACK_SIZE })
// v2 的唯一持久化坐标：卡片固定定位的左上角；绿点仅由它派生，绝不单独持久化。
const processStatusCardAnchor = ref<{ x: number; y: number } | null>(null)
// 方位只描述绿点相对卡片的方向，不引入第二套可持久化坐标。
const processStatusDotSide = ref<ProcessStatusDotSide>('top-left')
const processStatusCardMeasured = ref(false)
const isDraggingProcessDot = ref(false)
const didDragProcessDot = ref(false)
let suppressNextProcessStatusClick = false
let suppressNextProcessStatusClickTimer: ReturnType<typeof setTimeout> | null = null
// 旧版只保存绿点坐标。迁移前暂存原坐标，避免回退尺寸把右下角绿点提前挪走。
const processStatusPendingLegacyDot = ref<{ x: number; y: number } | null>(null)
const processStatusNeedsDotSideUpgrade = ref(false)
let processStatusCardResizeObserver: ResizeObserver | null = null
let dragPointerId: number | null = null
let dragStartX = 0
let dragStartY = 0
let dragStartedOnCard = false
let processDragTarget: HTMLElement | null = null
let dragCardAnchorOrigin: { x: number; y: number } | null = null
let dragDotOrigin: { x: number; y: number } | null = null

function clampProcessDotPos(x: number, y: number) {
  if (typeof window === 'undefined') return { x, y }
  const maxX = Math.max(
    PROCESS_DOT_MARGIN,
    window.innerWidth - PROCESS_DOT_SIZE - PROCESS_DOT_MARGIN
  )
  const maxY = Math.max(
    PROCESS_DOT_MARGIN,
    window.innerHeight - PROCESS_DOT_SIZE - PROCESS_DOT_MARGIN
  )
  return {
    x: Math.min(Math.max(x, PROCESS_DOT_MARGIN), maxX),
    y: Math.min(Math.max(y, PROCESS_DOT_MARGIN), maxY),
  }
}

function effectiveProcessStatusCardSize(cardSize: { width: number; height: number }) {
  return {
    width: Math.min(cardSize.width, Math.max(0, window.innerWidth - PROCESS_STATUS_CARD_VIEWPORT_INSET)),
    height: Math.min(cardSize.height, Math.max(0, window.innerHeight - PROCESS_STATUS_CARD_VIEWPORT_INSET)),
  }
}

function clampProcessStatusCardAnchor(position: { x: number; y: number }) {
  const size = effectiveProcessStatusCardSize(processStatusCardSize.value)
  const maxX = Math.max(PROCESS_STATUS_CARD_MARGIN, window.innerWidth - size.width - PROCESS_STATUS_CARD_MARGIN)
  const maxY = Math.max(PROCESS_STATUS_CARD_MARGIN, window.innerHeight - size.height - PROCESS_STATUS_CARD_MARGIN)
  return {
    x: Math.min(Math.max(position.x, PROCESS_STATUS_CARD_MARGIN), maxX),
    y: Math.min(Math.max(position.y, PROCESS_STATUS_CARD_MARGIN), maxY),
  }
}

function isFinitePosition(position: { x?: unknown; y?: unknown }): position is { x: number; y: number } {
  return (
    typeof position.x === 'number' &&
    typeof position.y === 'number' &&
    Number.isFinite(position.x) &&
    Number.isFinite(position.y)
  )
}

function isProcessStatusDotSide(value: unknown): value is ProcessStatusDotSide {
  return value === 'top-left' || value === 'top-right' || value === 'bottom-left' || value === 'bottom-right'
}

function saveProcessStatusCardAnchor() {
  if (typeof window === 'undefined' || !processStatusCardAnchor.value) return
  try {
    const { x: cardX, y: cardY } = processStatusCardAnchor.value
    window.localStorage.setItem(
      PROCESS_DOT_POS_KEY,
      JSON.stringify({ v: 2, cardX, cardY, dotSide: processStatusDotSide.value })
    )
  } catch {
    // 忽略 localStorage 写入失败（如隐私模式）
  }
}

function armProcessStatusClickSuppression() {
  suppressNextProcessStatusClick = true
  if (suppressNextProcessStatusClickTimer) clearTimeout(suppressNextProcessStatusClickTimer)
  // pointerup 可能发生在组件外，浏览器不会再补发 click；短 token 避免吞掉之后的真实点击。
  suppressNextProcessStatusClickTimer = setTimeout(() => {
    suppressNextProcessStatusClick = false
    suppressNextProcessStatusClickTimer = null
  }, 500)
}

function consumeProcessStatusClickSuppression() {
  if (!suppressNextProcessStatusClick) return false
  suppressNextProcessStatusClick = false
  if (suppressNextProcessStatusClickTimer) clearTimeout(suppressNextProcessStatusClickTimer)
  suppressNextProcessStatusClickTimer = null
  didDragProcessDot.value = false
  return true
}

function loadProcessDotPos() {
  if (typeof window === 'undefined') return
  try {
    const raw = window.localStorage.getItem(PROCESS_DOT_POS_KEY)
    if (!raw) {
      processStatusFloating.value = false
      return
    }
    const parsed = JSON.parse(raw) as {
      v?: unknown
      cardX?: unknown
      cardY?: unknown
      dotSide?: unknown
      x?: unknown
      y?: unknown
    }
    const v2Position = { x: parsed.cardX, y: parsed.cardY }
    const legacyDotPosition = { x: parsed.x, y: parsed.y }
    if (parsed.v === 2 && isFinitePosition(v2Position) && isProcessStatusDotSide(parsed.dotSide)) {
      // 已校验的 v2 不在回退尺寸阶段夹取或回写，等展开后拿到真实卡片尺寸再处理。
      processStatusCardAnchor.value = v2Position
      processStatusDotSide.value = parsed.dotSide
      processStatusNeedsDotSideUpgrade.value = false
      processStatusFloating.value = true
      processStatusCollapsed.value = true
    } else if (parsed.v === 2 && isFinitePosition(v2Position)) {
      // 兼容最早一版缺少方位的 v2；同样等待真实测量后再升级写回。
      processStatusCardAnchor.value = v2Position
      processStatusDotSide.value = 'top-left'
      processStatusNeedsDotSideUpgrade.value = true
      processStatusFloating.value = true
      processStatusCollapsed.value = true
    } else if (isFinitePosition(legacyDotPosition)) {
      // 兼容 v1 绿点坐标：先保留原坐标，等卡片取得真实尺寸后再折算并持久化。
      processStatusPendingLegacyDot.value = clampProcessDotPos(legacyDotPosition.x, legacyDotPosition.y)
      processStatusCardAnchor.value = null
      processStatusFloating.value = true
      processStatusCollapsed.value = true
    } else {
      processStatusFloating.value = false
    }
  } catch {
    processStatusFloating.value = false
  }
}

const processStatusDotStyle = computed(() => {
  if (processStatusPendingLegacyDot.value) {
    return {
      '--figma-process-dot-x': `${processStatusPendingLegacyDot.value.x}px`,
      '--figma-process-dot-y': `${processStatusPendingLegacyDot.value.y}px`,
    } as Record<string, string>
  }
  const pos = processStatusCardAnchor.value
    ? dotPositionForCardAnchor(processStatusCardAnchor.value, processStatusDotSide.value)
    : { x: PROCESS_DOT_MARGIN, y: PROCESS_DOT_MARGIN }
  return {
    '--figma-process-dot-x': `${pos.x}px`,
    '--figma-process-dot-y': `${pos.y}px`,
  } as Record<string, string>
})

// 仅用于兼容旧版 v1 绿点记录的迁移：同时保存旧绿点相对卡片的视觉方位。
function calculateProcessStatusCardPlacement(
  dotPos: { x: number; y: number },
  cardSize: { width: number; height: number },
  preferredSide?: ProcessStatusDotSide
) {
  if (typeof window === 'undefined') {
    return { anchor: { x: dotPos.x, y: dotPos.y }, dotSide: 'top-left' as ProcessStatusDotSide }
  }

  // CSS 会把卡片限制在视口内，这里用同一有效尺寸计算位置，避免测量值过大时定位失真。
  const effectiveCardSize = effectiveProcessStatusCardSize(cardSize)
  const maxX = Math.max(PROCESS_STATUS_CARD_MARGIN, window.innerWidth - effectiveCardSize.width - PROCESS_STATUS_CARD_MARGIN)
  const maxY = Math.max(PROCESS_STATUS_CARD_MARGIN, window.innerHeight - effectiveCardSize.height - PROCESS_STATUS_CARD_MARGIN)
  const rightFits = dotPos.x + PROCESS_DOT_SIZE + PROCESS_STATUS_CARD_GAP <= maxX
  const belowFits = dotPos.y + PROCESS_DOT_SIZE + PROCESS_STATUS_CARD_GAP <= maxY
  const desiredSide = `${belowFits ? 'top' : 'bottom'}-${rightFits ? 'left' : 'right'}` as ProcessStatusDotSide
  const candidates = (['top-left', 'top-right', 'bottom-left', 'bottom-right'] as ProcessStatusDotSide[]).map(
    (dotSide) => {
      const dotIsTop = dotSide.startsWith('top')
      const dotIsLeft = dotSide.endsWith('left')
      return {
        dotSide,
        anchor: {
          x: dotIsLeft
            ? dotPos.x + PROCESS_DOT_SIZE + PROCESS_STATUS_CARD_GAP
            : dotPos.x - PROCESS_STATUS_CARD_GAP - effectiveCardSize.width,
          y: dotIsTop
            ? dotPos.y + PROCESS_DOT_SIZE + PROCESS_STATUS_CARD_GAP
            : dotPos.y - PROCESS_STATUS_CARD_GAP - effectiveCardSize.height,
        },
      }
    }
  )
  const isValid = (candidate: (typeof candidates)[number]) =>
    candidate.anchor.x >= PROCESS_STATUS_CARD_MARGIN &&
    candidate.anchor.x <= maxX &&
    candidate.anchor.y >= PROCESS_STATUS_CARD_MARGIN &&
    candidate.anchor.y <= maxY
  const validCandidates = candidates.filter(isValid)
  const selected =
    (preferredSide && validCandidates.find((candidate) => candidate.dotSide === preferredSide)) ||
    validCandidates.find((candidate) => candidate.dotSide === desiredSide) ||
    validCandidates[0] ||
    candidates.find((candidate) => candidate.dotSide === desiredSide) ||
    candidates[0]
  return {
    anchor: {
      x: Math.min(Math.max(selected.anchor.x, PROCESS_STATUS_CARD_MARGIN), maxX),
      y: Math.min(Math.max(selected.anchor.y, PROCESS_STATUS_CARD_MARGIN), maxY),
    },
    dotSide: selected.dotSide,
  }
}

const processStatusCardStyle = computed(() => {
  if (!processStatusFloating.value || !processStatusCardAnchor.value) {
    return {} as CSSProperties
  }
  const pos = processStatusCardAnchor.value
  return {
    position: 'fixed',
    left: `${pos.x}px`,
    top: `${pos.y}px`,
    maxWidth: `calc(100vw - ${PROCESS_STATUS_CARD_VIEWPORT_INSET}px)`,
    maxHeight: `calc(100vh - ${PROCESS_STATUS_CARD_VIEWPORT_INSET}px)`,
    overflowY: 'auto',
    overflowWrap: 'anywhere',
    boxSizing: 'border-box',
  } as CSSProperties
})

function measureProcessStatusCard() {
  const card = processStatusCard.value
  if (!card) return
  const rect = card.getBoundingClientRect()
  if (rect.width > 0 && rect.height > 0) {
    const currentDot = processStatusPendingLegacyDot.value
      ? processStatusPendingLegacyDot.value
      : processStatusCardAnchor.value
        ? dotPositionForCardAnchor(processStatusCardAnchor.value, processStatusDotSide.value)
        : null
    processStatusCardSize.value = { width: rect.width, height: rect.height }
    processStatusCardMeasured.value = true
    if (processStatusPendingLegacyDot.value && currentDot) {
      const placement = calculateProcessStatusCardPlacement(currentDot, processStatusCardSize.value)
      processStatusCardAnchor.value = placement.anchor
      processStatusDotSide.value = placement.dotSide
      processStatusPendingLegacyDot.value = null
      processStatusNeedsDotSideUpgrade.value = false
      saveProcessStatusCardAnchor()
    } else if (processStatusNeedsDotSideUpgrade.value && processStatusCardAnchor.value) {
      processStatusDotSide.value = dotSideForCardAnchor(processStatusCardAnchor.value)
      processStatusNeedsDotSideUpgrade.value = false
      clampFloatingProcessStatusCardAnchor()
      saveProcessStatusCardAnchor()
    } else {
      clampFloatingProcessStatusCardAnchor()
    }
  }
}

async function startProcessStatusCardObservation() {
  await nextTick()
  if (!processStatusCardVisible.value) return
  const card = processStatusCard.value
  if (!card) return
  measureProcessStatusCard()
  // 卡片展开后才监听内容尺寸；收起或卸载时立即断开，避免保留失效 DOM 引用。
  if (typeof ResizeObserver !== 'undefined') {
    processStatusCardResizeObserver?.disconnect()
    processStatusCardResizeObserver = new ResizeObserver(() => measureProcessStatusCard())
    processStatusCardResizeObserver.observe(card)
  }
}

function stopProcessStatusCardObservation() {
  processStatusCardResizeObserver?.disconnect()
  processStatusCardResizeObserver = null
}

watch(processStatusCardVisible, (visible) => {
  if (!visible) {
    stopProcessStatusCardObservation()
  } else {
    void startProcessStatusCardObservation()
  }
})

function dotSideForCardAnchor(cardPos: { x: number; y: number }): ProcessStatusDotSide {
  const left = cardPos.x - PROCESS_DOT_SIZE - PROCESS_STATUS_CARD_GAP
  const top = cardPos.y - PROCESS_DOT_SIZE - PROCESS_STATUS_CARD_GAP
  return `${top >= PROCESS_DOT_MARGIN ? 'top' : 'bottom'}-${left >= PROCESS_DOT_MARGIN ? 'left' : 'right'}` as ProcessStatusDotSide
}

// 卡片是唯一真实坐标；绿点仅以方位、卡片大小和安全边距派生，不单独保存坐标。
function dotPositionForCardAnchor(cardPos: { x: number; y: number }, dotSide: ProcessStatusDotSide) {
  const effectiveSize = effectiveProcessStatusCardSize(processStatusCardSize.value)
  const dotIsTop = dotSide.startsWith('top')
  const dotIsLeft = dotSide.endsWith('left')
  return clampProcessDotPos(
    dotIsLeft
      ? cardPos.x - PROCESS_DOT_SIZE - PROCESS_STATUS_CARD_GAP
      : cardPos.x + effectiveSize.width + PROCESS_STATUS_CARD_GAP,
    dotIsTop
      ? cardPos.y - PROCESS_DOT_SIZE - PROCESS_STATUS_CARD_GAP
      : cardPos.y + effectiveSize.height + PROCESS_STATUS_CARD_GAP
  )
}

function prepareCardForFloatingDrag() {
  const card = processStatusCard.value
  if (!card || processStatusFloating.value) return
  const rect = card.getBoundingClientRect()
  if (rect.width > 0 && rect.height > 0) {
    processStatusCardSize.value = { width: rect.width, height: rect.height }
    processStatusCardMeasured.value = true
  }
  // 内联转浮动时直接采用拖动前的实际 rect，确保首次越过阈值不会发生视觉跳位。
  processStatusCardAnchor.value = clampProcessStatusCardAnchor({ x: rect.left, y: rect.top })
  processStatusDotSide.value = dotSideForCardAnchor(processStatusCardAnchor.value)
  processStatusFloating.value = true
}

function clampFloatingProcessStatusCardAnchor() {
  const anchor = processStatusCardAnchor.value
  if (!processStatusFloating.value || !anchor || !processStatusCardMeasured.value) return
  const clamped = clampProcessStatusCardAnchor(anchor)
  if (clamped.x === anchor.x && clamped.y === anchor.y) return
  processStatusCardAnchor.value = clamped
  saveProcessStatusCardAnchor()
}

function onProcessDotPointerMove(event: PointerEvent) {
  if (!isDraggingProcessDot.value) return
  if (dragPointerId !== event.pointerId) return
  const dx = event.clientX - dragStartX
  const dy = event.clientY - dragStartY
  if (
    !didDragProcessDot.value &&
    Math.hypot(dx, dy) >= PROCESS_DOT_DRAG_THRESHOLD
  ) {
    didDragProcessDot.value = true
    armProcessStatusClickSuppression()
    if (dragStartedOnCard && !processStatusFloating.value) {
      prepareCardForFloatingDrag()
      dragCardAnchorOrigin = processStatusCardAnchor.value
      // 首次跨过阈值只完成内联 → fixed 的无跳位切换；以当前指针为新的拖动原点，
      // 后续 pointermove 才叠加位移，避免本次事件把卡片立即推离原始 rect。
      dragStartX = event.clientX
      dragStartY = event.clientY
      return
    }
  }
  if (didDragProcessDot.value && dragStartedOnCard && dragCardAnchorOrigin) {
    processStatusCardAnchor.value = clampProcessStatusCardAnchor({
      x: dragCardAnchorOrigin.x + dx,
      y: dragCardAnchorOrigin.y + dy,
    })
  } else if (didDragProcessDot.value && dragDotOrigin) {
    // 绿点拖动以目标绿点坐标重新选方位，避免旧 dotSide 把卡片锚点夹住而导致绿点停在边缘。
    const intendedDot = clampProcessDotPos(dragDotOrigin.x + dx, dragDotOrigin.y + dy)
    processStatusPendingLegacyDot.value = null
    const placement = calculateProcessStatusCardPlacement(
      intendedDot,
      processStatusCardSize.value,
      processStatusDotSide.value
    )
    processStatusCardAnchor.value = placement.anchor
    processStatusDotSide.value = placement.dotSide
  }
}

function onProcessDotPointerEnd(event: PointerEvent) {
  if (dragPointerId !== event.pointerId) return
  if (isDraggingProcessDot.value) {
    isDraggingProcessDot.value = false
    if (didDragProcessDot.value) {
      saveProcessStatusCardAnchor()
    }
  }
  dragPointerId = null
  if (processDragTarget?.hasPointerCapture?.(event.pointerId)) {
    processDragTarget.releasePointerCapture?.(event.pointerId)
  }
  processDragTarget = null
  dragStartedOnCard = false
  dragCardAnchorOrigin = null
  dragDotOrigin = null
  window.removeEventListener('pointermove', onProcessDotPointerMove)
  window.removeEventListener('pointerup', onProcessDotPointerEnd)
  window.removeEventListener('pointercancel', onProcessDotPointerEnd)
}

function startProcessStatusDrag(event: PointerEvent, startedOnCard: boolean) {
  if (event.button !== 0 && event.pointerType === 'mouse') return
  const target = event.currentTarget as HTMLElement | null
  target?.setPointerCapture?.(event.pointerId)
  processDragTarget = target
  dragPointerId = event.pointerId
  dragStartX = event.clientX
  dragStartY = event.clientY
  dragStartedOnCard = startedOnCard
  dragCardAnchorOrigin = processStatusCardAnchor.value
  dragDotOrigin = processStatusPendingLegacyDot.value
    ? processStatusPendingLegacyDot.value
    : processStatusCardAnchor.value
      ? dotPositionForCardAnchor(processStatusCardAnchor.value, processStatusDotSide.value)
      : null
  didDragProcessDot.value = false
  isDraggingProcessDot.value = true
  window.addEventListener('pointermove', onProcessDotPointerMove)
  window.addEventListener('pointerup', onProcessDotPointerEnd)
  window.addEventListener('pointercancel', onProcessDotPointerEnd)
}

function onProcessStatusDotPointerDown(event: PointerEvent) {
  startProcessStatusDrag(event, false)
}

function onProcessStatusCardPointerDown(event: PointerEvent) {
  // 初始化按钮是独立操作，不能被卡片拖动逻辑截获。
  if ((event.target as HTMLElement | null)?.closest('.figma-chat-process-action')) return
  startProcessStatusDrag(event, true)
}

function handleProcessStatusDotClick() {
  // 拖动产生的 pointerup 会触发 click，这里通过阈值标记过滤掉真实拖动
  if (consumeProcessStatusClickSuppression()) return
  toggleProcessStatus()
}

function handleProcessStatusCardClick() {
  if (consumeProcessStatusClickSuppression()) return
  toggleProcessStatus()
}

onMounted(() => {
  if (props.processStatusPlacement === 'pet') return
  loadProcessDotPos()
  window.addEventListener('resize', onProcessStatusDotResize)
  if (processStatusCardVisible.value) {
    void startProcessStatusCardObservation()
  }
})
onBeforeUnmount(() => {
  stopProcessStatusCardObservation()
  window.removeEventListener('resize', onProcessStatusDotResize)
  window.removeEventListener('pointermove', onProcessDotPointerMove)
  window.removeEventListener('pointerup', onProcessDotPointerEnd)
  window.removeEventListener('pointercancel', onProcessDotPointerEnd)
  if (suppressNextProcessStatusClickTimer) clearTimeout(suppressNextProcessStatusClickTimer)
  suppressNextProcessStatusClickTimer = null
})

function onProcessStatusDotResize() {
  // 视口变化先刷新实际尺寸，再以唯一卡片锚点夹取并在发生变化时持久化。
  measureProcessStatusCard()
  clampFloatingProcessStatusCardAnchor()
}

function onContextPreview(item: ChatContextItem) {
  emit('preview-context', item)
}

// 抽屉可见文件列表（按 props 顺序）；选中态基于 drawerSelectedPath。
const drawerFiles = computed(() => props.fileChanges ?? [])

// 当前抽屉选中的文件对象；fallback 到第一个，保证初次打开一定有内容。
const drawerSelectedFile = computed(
  () =>
    drawerFiles.value.find((file) => file.path === drawerSelectedPath.value) ??
    drawerFiles.value[0]
)

// 当前文件的解析后 diff 行；用于右侧纯文本渲染。
const drawerSelectedLines = computed(() =>
  parseDiffLines(drawerSelectedFile.value?.patch)
)

// 仅展示变更行：过滤掉 unified diff 中的 ctx 上下文行，保留 hunk header (meta)
// 让用户在没有上下文干扰的情况下快速看到 +/- 变更。
// 总 add/del 数仍按 drawerSelectedLines 统计，保证抽屉头部的 +/- 徽标与文件列表一致。
const visibleDiffLines = computed(() => {
  if (showContext.value) return drawerSelectedLines.value
  return drawerSelectedLines.value.filter((line) => line.kind !== 'ctx')
})

// 当前文件的增减行数；若 patch 没解析出来，则回退到 FileChangeStat 自带的 additions/deletions。
const drawerSelectedAdditions = computed(
  () =>
    drawerSelectedLines.value.filter((line) => line.kind === 'add').length ||
    drawerSelectedFile.value?.additions ||
    0
)
const drawerSelectedDeletions = computed(
  () =>
    drawerSelectedLines.value.filter((line) => line.kind === 'del').length ||
    drawerSelectedFile.value?.deletions ||
    0
)

function openChangesDrawer() {
  if (!hasFileChanges.value) return
  // 默认选中第一个文件
  drawerSelectedPath.value = drawerFiles.value[0]?.path ?? ''
  drawerOpen.value = true
  // 让 diff 区域滚回顶部，避免上次浏览的中间位置被保留
  void nextTick(() => drawerScroll.value?.scrollTo?.({ top: 0 }))
}

function closeChangesDrawer() {
  drawerOpen.value = false
}

function openAttachmentDialog() {
  attachmentDialogOpen.value = true
}

function closeAttachmentDialog() {
  attachmentDialogOpen.value = false
}

function selectDrawerFile(path: string) {
  if (!path || drawerSelectedPath.value === path) return
  drawerSelectedPath.value = path
  // 切换文件时重置滚动
  void nextTick(() => drawerScroll.value?.scrollTo({ top: 0 }))
}

type HistoryDrawerView = 'sessions' | 'night'

const chatRootEl = ref<HTMLElement | null>(null)
const historyDrawerTriggerEl = ref<HTMLButtonElement | null>(null)
const historyDrawerSearchEl = ref<HTMLInputElement | null>(null)
const historyDrawerSessionsTabEl = ref<HTMLButtonElement | null>(null)
const historyDrawerNightTabEl = ref<HTMLButtonElement | null>(null)
const historyDrawerId = useId()
const historyDrawerSessionsTabId = `${historyDrawerId}-sessions-tab`
const historyDrawerNightTabId = `${historyDrawerId}-night-tab`
const historyDrawerSessionsPanelId = `${historyDrawerId}-sessions-panel`
const historyDrawerNightPanelId = `${historyDrawerId}-night-panel`
const historyDrawerOpen = ref(false)
const historyDrawerView = ref<HistoryDrawerView>('sessions')
const historyDrawerPlacement = ref<ReturnType<typeof resolveSessionListDrawerPlacement> | null>(null)
let historyDrawerResizeObserver: ResizeObserver | null = null
const localHistorySearchQuery = ref('')
const historySearchQuery = computed({
  get: () => props.historySearch ?? localHistorySearchQuery.value,
  set: (value: string) => {
    localHistorySearchQuery.value = value
    emit('history-search-change', value)
  }
})
const visibleHistory = computed(() => props.history || [])
const historyTotalCount = computed(() => props.historyTotal ?? visibleHistory.value.length)
const visibleHistoryRunningCount = computed(() => visibleHistory.value.filter((item) => item.runtimeState === 'running').length)
const visibleHistoryQuestionCount = computed(() => visibleHistory.value.filter((item) => item.pendingQuestion).length)
const historyRunningCount = computed(() => props.historyRunningCount ?? visibleHistoryRunningCount.value)
const historyQuestionCount = computed(() => props.historyQuestionCount ?? visibleHistoryQuestionCount.value)
const historyDrawerPanelStyle = computed<CSSProperties>(() => {
  const placement = historyDrawerPlacement.value
  if (!placement) return {}
  return {
    top: `${placement.top}px`,
    left: `${placement.left}px`,
    width: `${placement.width}px`,
    height: `${placement.height}px`,
  }
})

function historyTime(value?: string) {
  if (!value) return '暂无'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value.replace('T', ' ').slice(0, 16)
  }
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

// 会话抽屉通过 Teleport 脱离右栏布局，位置统一从右栏实测矩形计算，避免拖拽或缩放后漂移。
function updateHistoryDrawerPlacement() {
  const root = chatRootEl.value
  if (!root || typeof window === 'undefined') return
  const rect = root.getBoundingClientRect()
  historyDrawerPlacement.value = resolveSessionListDrawerPlacement(
    { top: rect.top, left: rect.left, width: rect.width, height: rect.height },
    { width: window.innerWidth, height: window.innerHeight },
  )
}

function openHistoryDrawer() {
  historyDrawerView.value = 'sessions'
  updateHistoryDrawerPlacement()
  historyDrawerOpen.value = true
  emit('open-history')
  void nextTick(() => {
    updateHistoryDrawerPlacement()
    historyDrawerSearchEl.value?.focus()
  })
}

function toggleHistoryDrawer() {
  if (historyDrawerOpen.value) {
    closeHistoryDrawer()
    return
  }
  openHistoryDrawer()
}

function showHistoryDrawerView(view: HistoryDrawerView) {
  historyDrawerView.value = view
  if (view === 'night') emit('request-night-tasks')
}

// 按 WAI-ARIA Tabs 模式提供循环方向键与 Home/End 导航，选中页签同时进入 Tab 顺序。
function onHistoryDrawerTabKeydown(event: KeyboardEvent, current: HistoryDrawerView) {
  let target: HistoryDrawerView | null = null
  if (event.key === 'Home') target = 'sessions'
  if (event.key === 'End') target = 'night'
  if (event.key === 'ArrowLeft') target = current === 'sessions' ? 'night' : 'sessions'
  if (event.key === 'ArrowRight') target = current === 'sessions' ? 'night' : 'sessions'
  if (!target) return
  event.preventDefault()
  if (historyDrawerView.value !== target) showHistoryDrawerView(target)
  void nextTick(() => {
    const tab = target === 'sessions' ? historyDrawerSessionsTabEl.value : historyDrawerNightTabEl.value
    tab?.focus()
  })
}

function closeHistoryDrawer(restoreFocus = true) {
  historyDrawerOpen.value = false
  if (restoreFocus) {
    void nextTick(() => historyDrawerTriggerEl.value?.focus())
  }
}
function selectHistoryItem(id: string) {
  emit('select-session', id)
}

function historyContextText(item: { appName?: string; workspaceName?: string; version?: string }) {
  return [
    item.appName?.trim() || '未关联应用',
    item.workspaceName?.trim() || '未知工作空间',
    item.version?.trim() || '无版本'
  ].join(' · ')
}

function historyRuntimeLabel(item: { runtimeState?: string }) {
  return item.runtimeState === 'running' ? '运行中' : '已完成'
}

type RawOutputFilter = 'all' | RawOutputKind
type RawOutputHighlightPart = { text: string; matched: boolean }

const rawOutputOpen = ref(false)
const rawOutputFilter = ref<RawOutputFilter>('all')
const rawOutputSearchQuery = ref('')
const rawOutputPosition = ref(defaultRawOutputPosition())
let rawDragState: { startX: number; startY: number; originX: number; originY: number } | null = null

const rawOutputPanelStyle = computed(() => ({
  left: `${rawOutputPosition.value.x}px`,
  top: `${rawOutputPosition.value.y}px`
}))

const filteredRawOutputEntries = computed(() => {
  const entries = props.rawOutputEntries ?? []
  const typedEntries = rawOutputFilter.value === 'all'
    ? entries
    : entries.filter((entry) => entry.kind === rawOutputFilter.value)
  const query = rawOutputSearchQuery.value.trim().toLowerCase()
  if (!query) return typedEntries
  return typedEntries.filter((entry) => rawOutputSearchText(entry).includes(query))
})

const rawOutputFilterOptions: Array<{ value: RawOutputFilter; label: string }> = [
  { value: 'all', label: '全部' },
  { value: 'request', label: '请求' },
  { value: 'response', label: '响应' },
  { value: 'sse', label: 'SSE' },
]

function defaultRawOutputPosition() {
  if (typeof window === 'undefined') {
    return { x: 80, y: 72 }
  }
  return {
    x: Math.max(24, Math.min(360, window.innerWidth - 800)),
    y: 72,
  }
}

function openRawOutput() {
  rawOutputOpen.value = true
}

function closeRawOutput() {
  rawOutputOpen.value = false
  stopRawOutputDrag()
}

function rawOutputSearchText(entry: RawOutputEntry) {
  return [
    entry.kind,
    rawOutputKindLabel(entry.kind),
    entry.title,
    entry.method,
    entry.path,
    entry.eventName,
    entry.status,
    entry.contentType,
    entry.traceId,
    entry.runId,
    entry.occurredAt,
    entry.body,
  ]
    .filter((item) => item !== undefined && item !== null)
    .join('\n')
    .toLowerCase()
}

function rawOutputQuery() {
  return rawOutputSearchQuery.value.trim()
}

function highlightRawOutputText(value: string | number | undefined | null): RawOutputHighlightPart[] {
  const text = value === undefined || value === null ? '' : String(value)
  const query = rawOutputQuery()
  if (!text || !query) {
    return [{ text, matched: false }]
  }
  const lowerText = text.toLowerCase()
  const lowerQuery = query.toLowerCase()
  const parts: RawOutputHighlightPart[] = []
  let cursor = 0
  while (cursor < text.length) {
    const index = lowerText.indexOf(lowerQuery, cursor)
    if (index < 0) {
      parts.push({ text: text.slice(cursor), matched: false })
      break
    }
    if (index > cursor) {
      parts.push({ text: text.slice(cursor, index), matched: false })
    }
    parts.push({ text: text.slice(index, index + query.length), matched: true })
    cursor = index + query.length
  }
  return parts.length > 0 ? parts : [{ text, matched: false }]
}

function rawOutputKindLabel(kind: RawOutputKind) {
  if (kind === 'request') return '请求'
  if (kind === 'response') return '响应'
  return 'SSE'
}

function rawOutputTime(value: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value.replace('T', ' ').slice(0, 19)
  }
  const pad = (n: number) => n.toString().padStart(2, '0')
  const yyyy = date.getFullYear()
  const MM = pad(date.getMonth() + 1)
  const dd = pad(date.getDate())
  const hh = pad(date.getHours())
  const mm = pad(date.getMinutes())
  const ss = pad(date.getSeconds())
  return `${yyyy}-${MM}-${dd} ${hh}:${mm}:${ss}`
}

function rawOutputBody(entry: RawOutputEntry) {
  return entry.body || '（空报文体）'
}

// 生成原始输出导出文件名用的本地时间戳（普通 Vue 组件可用 new Date，Workflow 脚本限制不适用）。
function formatRawOutputStamp() {
  const pad = (n: number) => n.toString().padStart(2, '0')
  const now = new Date()
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
}

// 下载当前过滤后的原始报文为文本文件。导出的是页面缓存中已脱敏、已截断后的可见副本，
// 不绕过 prepareRawOutputBody 的安全边界；仅导出与浮层计数一致的被过滤条目。
function downloadRawOutput() {
  const entries = filteredRawOutputEntries.value
  if (entries.length === 0) return
  const lines: string[] = [`# 原始输出（共 ${entries.length} 条）`, '']
  entries.forEach((entry, index) => {
    const detail = [
      `时间: ${rawOutputTime(entry.occurredAt)}`,
      entry.status ? `状态: ${entry.status}` : '',
      entry.contentType ? `Content-Type: ${entry.contentType}` : '',
      entry.traceId ? `trace: ${entry.traceId}` : '',
      entry.runId ? `run: ${entry.runId}` : '',
    ].filter(Boolean)
    lines.push(`===== [${rawOutputKindLabel(entry.kind)}] ${entry.title} =====`)
    lines.push(detail.join('\n'))
    lines.push('')
    lines.push(rawOutputBody(entry))
    if (index < entries.length - 1) lines.push('')
  })
  const blob = new Blob([lines.join('\n')], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `raw-output-${formatRawOutputStamp()}.txt`
  link.click()
  URL.revokeObjectURL(url)
}

function startRawOutputDrag(event: PointerEvent) {
  if ((event.target as HTMLElement | null)?.closest('button')) return
  event.preventDefault()
  rawDragState = {
    startX: event.clientX,
    startY: event.clientY,
    originX: rawOutputPosition.value.x,
    originY: rawOutputPosition.value.y,
  }
  window.addEventListener('pointermove', moveRawOutputPanel)
  window.addEventListener('pointerup', stopRawOutputDrag, { once: true })
}

function moveRawOutputPanel(event: PointerEvent) {
  if (!rawDragState) return
  const nextX = rawDragState.originX + event.clientX - rawDragState.startX
  const nextY = rawDragState.originY + event.clientY - rawDragState.startY
  rawOutputPosition.value = {
    x: clampRawOutputPosition(nextX, 8, typeof window === 'undefined' ? nextX : window.innerWidth - 120),
    y: clampRawOutputPosition(nextY, 8, typeof window === 'undefined' ? nextY : window.innerHeight - 56),
  }
}

function stopRawOutputDrag() {
  rawDragState = null
  if (typeof window !== 'undefined') {
    window.removeEventListener('pointermove', moveRawOutputPanel)
    window.removeEventListener('pointerup', stopRawOutputDrag)
  }
}

function clampRawOutputPosition(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value))
}

// Esc 关闭面板内浮层：监听全局 keydown，只在当前浮层打开时响应。
function onOverlayKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && attachmentDialogOpen.value) {
    event.preventDefault()
    closeAttachmentDialog()
    return
  }
  if (event.key === 'Escape' && rawOutputOpen.value) {
    event.preventDefault()
    closeRawOutput()
    return
  }
  if (event.key === 'Escape' && historyDrawerOpen.value) {
    event.preventDefault()
    closeHistoryDrawer()
    return
  }
  if (event.key === 'Escape' && drawerOpen.value) {
    event.preventDefault()
    closeChangesDrawer()
  }
}

function handleHistoryDrawerViewportChange() {
  if (historyDrawerOpen.value) updateHistoryDrawerPlacement()
}

onMounted(() => {
  window.addEventListener('keydown', onOverlayKeydown)
  window.addEventListener('resize', handleHistoryDrawerViewportChange)
  window.addEventListener('scroll', handleHistoryDrawerViewportChange, true)
  if (typeof ResizeObserver !== 'undefined' && chatRootEl.value) {
    historyDrawerResizeObserver = new ResizeObserver(handleHistoryDrawerViewportChange)
    historyDrawerResizeObserver.observe(chatRootEl.value)
  }
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onOverlayKeydown)
  window.removeEventListener('resize', handleHistoryDrawerViewportChange)
  window.removeEventListener('scroll', handleHistoryDrawerViewportChange, true)
  historyDrawerResizeObserver?.disconnect()
  historyDrawerResizeObserver = null
  stopRawOutputDrag()
  stopRunTimer()
})

watch(
  () => props.panelVisible,
  (visible) => {
    if (!visible && historyDrawerOpen.value) closeHistoryDrawer(false)
  },
)

watch(
  () => props.inputValue,
  (v) => {
    if (typeof v === 'string' && v !== localInput.value) localInput.value = v
  }
)

watch(localInput, (v) => onComposerInput(v))

watch(
  () => props.nightSlots,
  (window) => {
    if (nightPickerMode.value === 'adjust' && selectedNightSlotStart.value) return
    const selectedStillAvailable = window?.slots.some(
      (slot) => slot.slotStart === selectedNightSlotStart.value && slot.available
    )
    if (selectedStillAvailable) return
    selectedNightSlotStart.value =
      window?.slots.find((slot) => slot.available && slot.recommended)?.slotStart
      ?? window?.slots.find((slot) => slot.available)?.slotStart
      ?? ''
  },
  { immediate: true }
)

watch(
  () => props.currentNightTask?.taskId ?? null,
  (taskId) => {
    if (!nightSubmissionAwaiting.value || !taskId || taskId === submittedNightTaskId.value) return
    nightSubmissionAwaiting.value = false
    submittedNightTaskId.value = taskId
    nightPickerOpen.value = false
    localInput.value = ''
    emit('update:inputValue', '')
  }
)

watch(
  () => props.nightTaskSubmitting,
  (submitting, previous) => {
    // 创建失败时保留原输入和已选时间，用户可以直接重试。
    if (previous && !submitting && nightSubmissionAwaiting.value) {
      nightSubmissionAwaiting.value = false
    }
  }
)

watch(
  () => props.running,
  (now, prev) => {
    if (now && !prev) {
      thinkingExpanded.value = false
      wasStopped.value = false
      wasCompleted.value = false
      wasFailed.value = false
      runStartMsgCount.value = displayMessages.value.length
      startRunTimer()
    }
    if (!now && prev) {
      stopRunTimer()
      if (!wasStopped.value) {
        const hasError = displayMessages.value.some((m) => m._error)
        if (hasError || isRuntimeFailureStatus()) wasFailed.value = true
        else if (isRuntimeStoppedStatus()) wasStopped.value = true
        else wasCompleted.value = true
      }
    }
  }
)

const displayMessages = computed<ChatMessage[]>(() => {
  const raw = (props.messages || [])
    .map((m, index): ChatMessage | null => {
      // card 消息：run 失败等事件转为助手气泡展示
      if (m.role === 'card') {
        const card = m as {
          role: 'card'
          cardType?: string
          title?: string
          payload?: Record<string, unknown>
        }
        if (card.cardType === 'event') {
          const detail = eventFailureText(card.payload)
          return {
            id: m.id ?? `card-${index}`,
            role: 'assistant' as const,
            content: detail,
            meta: m.createdAt ? formatTime(m.createdAt) : undefined,
            parts: [],
            _error: true,
          }
        }
        return null
      }
      if (m.role !== 'user' && m.role !== 'assistant') return null
      let text = ''
      if (typeof m.content === 'string' && m.content) {
        text = m.content
      } else if (typeof m.text === 'string' && m.text) {
        text = m.text
      } else if (Array.isArray(m.parts)) {
        text = m.parts.map((p) => partText(p)).join('')
      }
      // 检测技能消息标记 __SKILL__<name>__PROMPT__<prompt>
      let skillName: string | undefined
      const skillMatch = text.match(/^__SKILL__(.+?)__PROMPT__(.+)$/s)
      if (skillMatch) {
        skillName = skillMatch[1]
        text = skillMatch[2]
      }
      const hasParts = hasVisibleParts(m as AgentMessage)
      // 有 tool/file part 的消息即使没有文本也保留，不因 running 状态变化而消失
      if (!text.trim() && !hasParts) return null
      const platformMessageId = m.role === 'assistant'
        ? ([m.platformMessageId, m.messageId, m.id].find(isPlatformMessageId) as string | undefined)
        : undefined
      const remoteMessageId = m.role === 'assistant'
        ? m.remoteMessageId ?? ([m.messageId, m.id].find(isRemoteRuntimeMessageId) as string | undefined)
        : undefined
      return {
        id: m.messageId ?? m.id ?? `${m.role}-${index}`,
        platformMessageId,
        remoteMessageId,
        feedbackMessageId: platformMessageId,
        role: m.role,
        content: text,
        _skillName: skillName,
        meta: m.createdAt ? formatTime(m.createdAt) : undefined,
        parts: m.role === 'assistant' ? [...(m.parts ?? [])] : [],
        readOutputs: m.role === 'assistant' && Array.isArray(m.parts)
          ? m.parts
            .filter((p): p is Extract<MessagePart, { type: 'tool' }> => p.type === 'tool' && p.toolName === 'read')
            .map((p) => parseReadOutput(typeof p.output === 'string' ? p.output : ''))
            // 目录扫描已在“探索”读取列表中表达，不再额外展示容易误解的目录结果卡片。
            .filter((f): f is ReadOutputInfo => f?.kind === 'file')
          : undefined,
      }
    })
    .filter((m): m is ChatMessage => m !== null)

  // 合并连续的 assistant 消息为一个对话气泡，避免工具操作分散成多条回复
  const merged: ChatMessage[] = []
  for (const msg of raw) {
    const last = merged.length > 0 ? merged[merged.length - 1] : null
    if (msg.role === 'assistant' && last && last.role === 'assistant') {
      last.content = joinAssistantContent(last.content, msg.content)
      last.parts = [...last.parts, ...msg.parts]
      if (msg.meta) last.meta = msg.meta
      // 连续 assistant 消息会合并展示；反馈必须绑定后端落库的 msg_*，不能沿用运行时临时 id。
      last.platformMessageId = msg.platformMessageId ?? last.platformMessageId
      last.remoteMessageId = msg.remoteMessageId ?? last.remoteMessageId
      last.feedbackMessageId = msg.feedbackMessageId ?? last.feedbackMessageId
    } else {
      merged.push(msg)
    }
  }
  return merged
})

const hasVisibleMessages = computed(() => displayMessages.value.length > 0)
const showTaskFailed = computed(() =>
  !props.running && hasVisibleMessages.value && (wasFailed.value || isRuntimeFailureStatus())
)
const showTaskStopped = computed(() =>
  !props.running && hasVisibleMessages.value && !showTaskFailed.value && (wasStopped.value || isRuntimeStoppedStatus())
)
const showTaskCompleted = computed(() =>
  !props.running && hasVisibleMessages.value && !showTaskFailed.value && !showTaskStopped.value && (wasCompleted.value || isRuntimeSuccessStatus())
)
const fallbackTaskFailureMessage = '您的请求断开，请重试。'
const taskFailureMessage = computed(() => {
  const error = [...displayMessages.value]
    .reverse()
    .find((message) => message._error && message.content.trim().length > 0)
  return error?.content.trim() || fallbackTaskFailureMessage
})

const timelineMessages = computed<AgentMessage[]>(() =>
  (props.messages ?? []).map((message, index) => {
    if (message.role === 'card') {
      return message
    }
    const text =
      typeof message.text === 'string' && message.text.length > 0
        ? message.text
        : typeof message.content === 'string'
          ? message.content
          : ''
    return {
      ...message,
      id: message.id ?? message.messageId ?? `${message.role}-${index}`,
      text,
    } as AgentMessage
  })
)

const timelineDiffFiles = computed<RunDiffFile[]>(() =>
  (props.fileChanges ?? []).map((file) => ({
    path: file.path,
    patch: file.patch ?? '',
    additions: file.additions ?? 0,
    deletions: file.deletions ?? 0,
    status: file.status ?? 'modified',
  }))
)

const opencodeTimelineState = computed(() =>
  createOpencodeLikeState({
    messages: timelineMessages.value,
    running: props.running,
    status: props.runtimeStatus,
    runtimeStatus: props.timelineRuntimeStatus,
    diffFiles: timelineDiffFiles.value,
    streamingTextByPartId: props.streamingTextByPartId,
    todos: props.todos,
    todoSnapshotsByUserMessageId: props.todoSnapshotsByUserMessageId,
    runStatusesByRunId: props.runStatusesByRunId,
    messageScopesById: props.messageScopesById,
    subagentsBySessionId: props.subagentsBySessionId,
    subagentByTaskPartId: props.subagentByTaskPartId,
    activeSubagentSessionId: activeSubagentSessionId.value,
  })
)

const workStatusDockRef = ref<HTMLElement | null>(null)

function openTimelineDiff() {
  emit('open-diff', props.fileChanges?.at(-1)?.path ?? '')
}

function selectSubagent(sessionId: string) {
  if (props.subagentsBySessionId[sessionId]) {
    activeSubagentSessionId.value = sessionId
  }
}

function returnToRootAgent() {
  activeSubagentSessionId.value = null
}

watch(
  () => props.subagentsBySessionId,
  (value) => {
    if (activeSubagentSessionId.value && !value[activeSubagentSessionId.value]) {
      activeSubagentSessionId.value = null
    }
  }
)

const lastAssistant = computed(() => {
  for (let i = displayMessages.value.length - 1; i >= 0; i -= 1) {
    if (displayMessages.value[i].role === 'assistant')
      return displayMessages.value[i]
  }
  return null
})

const showRunningAssistant = computed(() => {
  if (!props.running) return false
  const last = lastAssistant.value
  if (!last) return true
  // 如果最新的助理消息里已经开始显示 reasoning 或 tool 的 part 卡片，则不需要展示最底部的全局 "思考中..." 卡片以避免冗余
  if (Array.isArray(last.parts) && last.parts.length > 0) {
    return false
  }
  if (last.content && last.content.trim().length > 0) {
    return false
  }
  return true
})

const lastUser = computed(() => {
  for (let i = displayMessages.value.length - 1; i >= 0; i -= 1) {
    if (displayMessages.value[i].role === 'user')
      return displayMessages.value[i]
  }
  return null
})

const hasTaskUsage = computed(
  () =>
    !!(
      props.taskUsage &&
      (props.taskUsage.tokens !== undefined ||
        props.taskUsage.totalDuration)
    )
)

const totalAdditions = computed(() =>
  (props.fileChanges || []).reduce((sum, f) => sum + (f.additions ?? 0), 0)
)

const totalDeletions = computed(() =>
  (props.fileChanges || []).reduce((sum, f) => sum + (f.deletions ?? 0), 0)
)

// 从最近一条助手回复中解析 token 数量。
// 支持的格式：↓ 826 tokens、tokens: 826、tokens：826、826 tokens 等。
function parseTokensFromText(text: string | undefined): number | undefined {
  if (!text) return undefined
  const patterns: RegExp[] = [
    /↓\s*(\d[\d,]*)\s*tokens/i,
    /tokens\s*[:：]\s*(\d[\d,]*)/i,
    /\b(\d[\d,]*)\s*tokens\b/i,
  ]
  for (const re of patterns) {
    const m = text.match(re)
    if (m) {
      const n = Number(m[1].replace(/,/g, ''))
      if (Number.isFinite(n)) return n
    }
  }
  return undefined
}

const parsedTokens = computed(() =>
  parseTokensFromText(lastAssistant.value?.content)
)

// 任务消耗 token 优先取 props.taskUsage.tokens（来自 step-finish 累计），assistant 文本里偶然出现的
// "826 tokens" 字样仅作为兜底解析，避免 props 缺失时整行空白。
const displayTokens = computed<number | undefined>(
  () => props.taskUsage?.tokens ?? parsedTokens.value
)

function formatTokens(n: number): string {
  if (n >= 10000) return `${(n / 10000).toFixed(1)}w`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return String(n)
}

const hasTaskUsageDisplay = computed(
  () =>
    !!(
      props.taskUsage &&
      (displayTokens.value !== undefined ||
        props.taskUsage.totalDuration)
    )
)

const scrollEl = ref<HTMLElement | null>(null)
const isAtBottom = ref(true)
const hasNewContent = ref(false)
const userInterruptedScroll = ref(false)
let isProgrammaticScroll = false
const scrollBottomThreshold = 36

function clearNewContentNotice() {
  hasNewContent.value = false
}

function resetScrollFollowState() {
  clearNewContentNotice()
  userInterruptedScroll.value = false
  isAtBottom.value = true
}

function viewportIsAtBottom(viewport: HTMLElement) {
  return viewport.scrollHeight - viewport.scrollTop - viewport.clientHeight <= scrollBottomThreshold
}

// 仅在用户仍停留在底部时跟随流式输出；用户向上阅读后不再强制拉回底部。
function shouldFollowOutput() {
  return !userInterruptedScroll.value && isAtBottom.value
}

function handleChatScroll(event: Event) {
  const viewport = event.currentTarget as HTMLElement
  if (isProgrammaticScroll) {
    isProgrammaticScroll = false
    return
  }
  const atBottom = viewportIsAtBottom(viewport)
  isAtBottom.value = atBottom
  if (atBottom) {
    hasNewContent.value = false
    userInterruptedScroll.value = false
  } else {
    userInterruptedScroll.value = true
  }
}

// 滚动到底部，使用 setTimeout 确保 DOM 完全更新
function scrollToBottom() {
  setTimeout(() => {
    if (scrollEl.value) {
      isProgrammaticScroll = true
      scrollEl.value.scrollTop = scrollEl.value.scrollHeight
      isAtBottom.value = true
      hasNewContent.value = false
      setTimeout(() => {
        isProgrammaticScroll = false
      }, 50)
    }
  }, 50)
}

function jumpToBottom() {
  userInterruptedScroll.value = false
  scrollToBottom()
}

function messageIdentity(message: ChatMessageInput, index: number) {
  return message.id ?? (message as { messageId?: string }).messageId ?? `${message.role}-${index}`
}

function lastMessageContentLength(message: ChatMessageInput | undefined) {
  if (!message || (message.role !== 'user' && message.role !== 'assistant')) return 0
  if (typeof message.content === 'string') return message.content.length
  if (typeof (message as { text?: string }).text === 'string') {
    return (message as { text?: string }).text?.length ?? 0
  }
  if (Array.isArray((message as { parts?: unknown[] }).parts)) {
    return ((message as { parts?: unknown[] }).parts ?? []).reduce(
      (n: number, p: unknown) => n + (partText(p)?.length || 0),
      0
    )
  }
  return 0
}

// 监听消息变化（数量或内容），流式回复时消息内容增长但数量不变，也需要滚动
watch(
  () => {
    const msgs = props.messages
    if (!msgs || msgs.length === 0) return { length: 0, lastId: '', lastLen: 0 }
    const last = msgs[msgs.length - 1]
    return {
      length: msgs.length,
      lastId: messageIdentity(last, msgs.length - 1),
      lastLen: lastMessageContentLength(last)
    }
  },
  (current, previous) => {
    if (current.length === 0) {
      resetScrollFollowState()
      return
    }
    const sameLastMessage = current.lastId === previous?.lastId
    const grewLastMessage = sameLastMessage && current.lastLen > (previous?.lastLen ?? 0)
    const appendedMessage = current.length > (previous?.length ?? 0)
    const receivedLiveOutput = props.running && (grewLastMessage || appendedMessage)
    if (!receivedLiveOutput) {
      // 运行中的工具状态、part 状态或反馈元数据更新不代表有新正文输出；
      // 必须保留用户上滑后的滚动锁，避免工作中状态刷新把视口强制拉回底部。
      if (!props.running) {
        clearNewContentNotice()
      }
      if (!props.running && shouldFollowOutput()) {
        nextTick(scrollToBottom)
      }
      return
    }
    if (shouldFollowOutput()) {
      nextTick(scrollToBottom)
    } else {
      hasNewContent.value = true
    }
  }
)

watch([wasCompleted, wasStopped, wasFailed], () => {
  if (shouldFollowOutput()) {
    nextTick(scrollToBottom)
  }
})

watch(
  () => [props.historyLoading, props.running] as const,
  ([historyLoading, running], [previousHistoryLoading, previousRunning]) => {
    if (historyLoading || (!running && previousRunning)) {
      clearNewContentNotice()
    }
    if (previousHistoryLoading && !historyLoading) {
      nextTick(scrollToBottom)
    }
  }
)

function formatTime(iso: string) {
  try {
    const d = new Date(iso)
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } catch {
    return ''
  }
}

const nightDateFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  month: 'numeric',
  day: 'numeric',
  weekday: 'short',
})
const nightTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

function formatNightDate(iso: string): string {
  return nightDateFormatter.format(new Date(iso))
}

function formatNightTime(iso: string): string {
  return nightTimeFormatter.format(new Date(iso))
}

function formatNightRange(task: Pick<NightExecutionTask, 'slotStart' | 'slotEnd'>): string {
  return `${formatNightDate(task.slotStart)} ${formatNightTime(task.slotStart)}–${formatNightTime(task.slotEnd)}`
}

function formatNightCreatedAt(iso: string): string {
  return `${formatNightDate(iso)} ${formatNightTime(iso)}`
}

function nightTaskStatusLabel(task: NightExecutionTask): string {
  if (task.status === 'DISPATCHING') return '启动中'
  if (task.status === 'FAILED') return '执行失败'
  if (task.rolloverCount > 0) return `已顺延 ${task.rolloverCount} 次`
  return '等待执行'
}

function nightTaskIsPending(task: NightExecutionTask): boolean {
  return task.status === 'SCHEDULED' || task.status === 'DISPATCHING'
}

function nightActionPending(taskId: string): boolean {
  return props.nightTaskActionPending?.[taskId] === true
}

function openNightPicker() {
  if (nightScheduleBlocked.value) return
  nightPickerMode.value = 'create'
  adjustingNightTaskId.value = null
  submittedNightTaskId.value = props.currentNightTask?.taskId ?? null
  nightPickerOpen.value = !nightPickerOpen.value
  if (nightPickerOpen.value) emit('request-night-slots')
}

function openNightTaskConversation(sessionId: string) {
  emit('open-night-task-session', sessionId)
}

function closeNightPicker() {
  if (props.nightTaskSubmitting) return
  nightPickerOpen.value = false
  nightPickerMode.value = 'create'
  adjustingNightTaskId.value = null
}

function selectNightSlot(slotStart: string, available: boolean) {
  if (!available || props.nightTaskSubmitting) return
  selectedNightSlotStart.value = slotStart
}

function confirmNightSchedule() {
  if (!selectedNightSlotStart.value || props.nightTaskSubmitting) return
  if (nightPickerMode.value === 'adjust' && adjustingNightTaskId.value) {
    emit('adjust-night-task', {
      taskId: adjustingNightTaskId.value,
      slotStart: selectedNightSlotStart.value,
    })
    closeNightPicker()
    return
  }
  const prompt = localInput.value.trim()
  if (!prompt || nightScheduleBlocked.value) return
  nightSubmissionAwaiting.value = true
  submittedNightTaskId.value = props.currentNightTask?.taskId ?? null
  emit('schedule-night', { prompt, slotStart: selectedNightSlotStart.value })
}

function beginAdjustNightTask(task: NightExecutionTask) {
  if (task.status !== 'SCHEDULED' || nightActionPending(task.taskId)) return
  nightPickerMode.value = 'adjust'
  adjustingNightTaskId.value = task.taskId
  selectedNightSlotStart.value = task.slotStart
  nightPickerOpen.value = true
  emit('request-night-slots')
}

function askCancelNightTask(taskId: string) {
  cancelConfirmTaskId.value = taskId
}

function confirmCancelNightTask(taskId: string) {
  cancelConfirmTaskId.value = null
  emit('cancel-night-task', taskId)
}

function submit() {
  const text = localInput.value.trim()
  if (!text || sendSubmitBlocked.value) return
  wasStopped.value = false
  wasCompleted.value = false
  wasFailed.value = false
  emit('send', text)
  localInput.value = ''
  emit('update:inputValue', '')
}

function stop() {
  wasStopped.value = true
  emit('stop')
}

function onKeydown(event: KeyboardEvent) {
  // 输入法候选词确认也可能触发 Enter keydown；组合输入阶段必须交给 IME，
  // 否则中文/英文混输时会在用户未确认发送前提交半截内容。
  if (event.isComposing || inputComposing.value || event.keyCode === 229) return
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    submit()
  }
}

function onCompositionStart() {
  inputComposing.value = true
}

function onCompositionEnd() {
  inputComposing.value = false
}
</script>

<template>
  <div ref="chatRootEl" class="figma-chat-root">
    <header class="figma-chat-header">
      <div class="figma-chat-header-left">
        <h2 class="figma-chat-title" :title="title">{{ title }}</h2>
        <span
          v-if="currentSessionSourceType === 'SCHEDULED_TASK'"
          class="figma-chat-night-source-badge"
          title="该对话由夜间定时任务创建"
        >
          <Clock3 :size="11" /> 夜间执行
        </span>
        <button
          type="button"
          class="figma-chat-header-btn"
          title="查看前端与平台后端原始报文"
          @click="openRawOutput"
        >
          <FileText :size="15" />
          <span>原始输出</span>
        </button>
        <button
          ref="historyDrawerTriggerEl"
          type="button"
          class="figma-chat-header-btn"
          title="查看会话列表"
          :aria-expanded="historyDrawerOpen"
          aria-haspopup="dialog"
          @click="toggleHistoryDrawer"
        >
          <span class="figma-chat-history-header-icon">
            <Spinner
              v-if="historyRunningCount > 0"
              class="figma-chat-history-header-spinner"
              style="width: 15px; height: 15px;"
            />
            <MessageSquare v-else :size="15" />
            <span v-if="historyRunningCount > 0" class="figma-chat-history-running-badge">
              {{ historyRunningCount }}
            </span>
          </span>
          <span>会话列表</span>
          <Bell
            v-if="historyQuestionCount > 0"
            :size="13"
            class="figma-chat-history-alert-bell"
          />
        </button>
      </div>
    </header>

    <div ref="scrollEl" class="figma-chat-scroll" @scroll="handleChatScroll">
      <div v-if="historyLoading" class="figma-chat-history-loading" role="status">
        <Spinner />
        <span>正在加载会话内容…</span>
      </div>
      <OpencodeTimeline
        v-else
        :state="opencodeTimelineState"
        :work-status-dock-target="activeSubagentSessionId ? undefined : workStatusDockRef"
        @open-diff="openTimelineDiff"
        @open-file="(path) => emit('open-file', path)"
        @select-subagent="selectSubagent"
      >
        <template #completed-status-actions="{ row }">
          <div
            v-if="canFeedbackRun(row)"
            class="figma-chat-feedback figma-chat-completed-feedback"
          >
            <button
              type="button"
              :class="[
                'figma-chat-feedback-btn',
                runFeedbackFor(row.runId)?.rating === 'POSITIVE' && 'is-selected',
              ]"
              :disabled="isRunFeedbackSubmitting(row.runId)"
              title="满意"
              aria-label="满意"
              @click="submitPositiveRunFeedback(row.runId)"
            >
              <ThumbsUp :size="12" />
            </button>
            <button
              type="button"
              :class="[
                'figma-chat-feedback-btn',
                'figma-chat-feedback-btn--negative',
                runFeedbackFor(row.runId)?.rating === 'NEGATIVE' && 'is-selected',
              ]"
              :disabled="isRunFeedbackSubmitting(row.runId)"
              title="不满意"
              aria-label="不满意"
              @click="openNegativeRunFeedback(row.runId)"
            >
              <ThumbsDown :size="12" />
            </button>
          </div>
        </template>
      </OpencodeTimeline>
      <article
        v-if="currentNightTask && nightTaskIsPending(currentNightTask)"
        class="figma-chat-night-card figma-chat-night-card--current"
        :class="{ 'is-dispatching': currentNightTask.status === 'DISPATCHING' }"
        data-testid="current-night-task-card"
      >
        <div class="figma-chat-night-card-head">
          <span class="figma-chat-night-status">{{ nightTaskStatusLabel(currentNightTask) }}</span>
          <span class="figma-chat-night-time">{{ formatNightRange(currentNightTask) }}</span>
        </div>
        <strong class="figma-chat-night-title">夜间执行已安排</strong>
        <p class="figma-chat-night-preview">{{ currentNightTask.contentPreview }}</p>
        <div class="figma-chat-night-actions">
          <template v-if="currentNightTask.status === 'SCHEDULED'">
            <button
              type="button"
              :disabled="nightActionPending(currentNightTask.taskId)"
              @click="beginAdjustNightTask(currentNightTask)"
            >调整时间</button>
            <button
              v-if="cancelConfirmTaskId !== currentNightTask.taskId"
              type="button"
              class="is-danger"
              :disabled="nightActionPending(currentNightTask.taskId)"
              @click="askCancelNightTask(currentNightTask.taskId)"
            >取消任务</button>
            <template v-else>
              <span class="figma-chat-night-confirm-copy">取消后可继续对话，确定取消？</span>
              <button type="button" class="is-danger" @click="confirmCancelNightTask(currentNightTask.taskId)">确认</button>
              <button type="button" @click="cancelConfirmTaskId = null">返回</button>
            </template>
          </template>
          <span v-else class="figma-chat-night-dispatch-copy">任务正在启动，请稍候…</span>
        </div>
      </article>
      <article
        v-if="nightVisibleFailure"
        class="figma-chat-night-card figma-chat-night-card--failed"
        data-testid="night-failure-card"
      >
        <div class="figma-chat-night-card-head">
          <span class="figma-chat-night-status">夜间执行失败</span>
          <span class="figma-chat-night-time">{{ formatNightRange(nightVisibleFailure) }}</span>
        </div>
        <strong class="figma-chat-night-title">{{ nightVisibleFailure.contentPreview }}</strong>
        <p class="figma-chat-night-error">{{ nightVisibleFailure.errorMessage || '任务未能在夜间窗口内启动' }}</p>
        <div class="figma-chat-night-actions">
          <button
            type="button"
            :disabled="nightActionPending(nightVisibleFailure.taskId)"
            @click="emit('dismiss-night-task', nightVisibleFailure.taskId)"
          >关闭提示</button>
        </div>
      </article>
      <button
        v-if="hasNewContent"
        type="button"
        class="figma-chat-new-content-btn"
        @click="jumpToBottom"
      >
        查看新内容
      </button>
      <!-- 作废说明：旧气泡消息循环已被 OpencodeTimeline 主路径取代；保留未激活代码仅便于短期比对，不再扩展。 -->
      <template v-if="false" v-for="message in displayMessages" :key="message.id">
        <!-- 用户消息气泡 (右对齐) -->
        <div
          v-if="message.role === 'user'"
          class="figma-chat-user-message"
          v-memo="[message.content, message.meta, message._skillName, copySuccessId === message.id + '-copy']"
        >
          <div class="figma-chat-user-meta-row">
            <div v-if="message.meta" class="figma-chat-bubble-meta">
              你 · {{ message.meta }}
            </div>
            <div class="figma-chat-avatar figma-chat-avatar--user">
              <User class="figma-chat-avatar-icon" />
            </div>
          </div>
          <div class="figma-chat-bubble figma-chat-bubble--user">
            <div v-if="message._skillName" class="figma-chat-skill-msg">
              <BookOpen :size="14" class="figma-chat-skill-msg-icon" />
              <span>{{ message._skillName }}</span>
            </div>
            <div v-else class="figma-chat-bubble-content">
              {{ message.content }}
            </div>
          </div>
          <div v-if="!message._skillName" class="figma-chat-user-feedback">
            <button
              type="button"
              class="figma-chat-action-btn"
              title="复制"
              @click="copyText(message.content, message.id + '-copy')"
            >
              <Check v-if="copySuccessId === message.id + '-copy'" :size="12" style="color: #2ecc71" />
              <Copy v-else :size="12" />
              <span>{{ copySuccessId === message.id + '-copy' ? '已复制' : '复制' }}</span>
            </button>
          </div>
        </div>

        <!-- 助手消息 (左对齐) -->
        <div
          v-else
          class="figma-chat-assistant"
          v-memo="[
            message.content,
            message.meta,
            message.parts?.length,
            message.parts?.map(p => ('status' in p ? p.status : '')).join(','),
            message._error,
            messageExpandedState(message.id),
            messageToolsExpandedState(message.id),
            copySuccessId === message.id + '-copy'
          ]"
        >
          <div class="figma-chat-avatar">
            <img :src="aiHeaderUrl" alt="AI" class="figma-chat-avatar-icon" />
          </div>
          <div class="figma-chat-assistant-content">
            <div v-if="message.meta" class="figma-chat-bubble-meta">
              测试智能体 · {{ message.meta }}
            </div>
            <div
              :class="[
                'figma-chat-bubble figma-chat-bubble--assistant',
                message._error && 'figma-chat-bubble--error',
              ]"
            >
              <div class="figma-chat-bubble-content">
                <template
                  v-if="
                    messageReadCount(message) > 0 ||
                    messageWriteCount(message) > 0 ||
                    messageEditCount(message) > 0
                  "
                >
                  <!-- 探索：read 工具调用列表，默认收起并允许用户手工展开。 -->
                  <div
                    v-if="messageReadCount(message) > 0"
                    class="figma-chat-file-summary"
                  >
                    <div
                      class="figma-chat-file-summary-row"
                      @click="toggleFileExpanded(message.id, 'read')"
                    >
                      <span>{{
                        running && message.id === lastAssistant?.id
                          ? '正在探索'
                          : '已探索'
                      }}</span>
                      <span style="color: #a1a5b1"
                        >读取 {{ messageReadCount(message) }} 次</span
                      >
                      <ChevronRight
                        v-if="!isFileExpanded(message.id, 'read')"
                        class="figma-chat-read-chevron"
                        :size="14"
                      />
                      <ChevronDown
                        v-else
                        class="figma-chat-read-chevron"
                        :size="14"
                      />
                    </div>
                    <ul
                      v-if="isFileExpanded(message.id, 'read')"
                      class="figma-chat-file-list"
                    >
                      <li
                        v-for="(op, i) in messageFileOps(message).filter(
                          (o) => o.opType === 'read'
                        )"
                        :key="i"
                        class="figma-chat-file-item"
                      >
                        <span
                          class="figma-chat-file-tag figma-chat-file-tag--read"
                          >读取</span
                        >
                        <span class="figma-chat-file-path">{{
                          getFileName(op.filePath)
                        }}</span>
                      </li>
                    </ul>
                  </div>
                  <!-- 写入：每个文件独立展开（已去重） -->
                  <template
                    v-for="op in uniqueOps(message, 'write')"
                    :key="'write-' + op.filePath"
                  >
                    <div class="figma-chat-file-summary">
                      <div
                        class="figma-chat-file-summary-row"
                        @click="
                          toggleFileExpanded(message.id, 'write-' + op.filePath)
                        "
                      >
                        <span>
                          写入
                          <span style="color: #1a1a1a; font-weight: 500; margin-left: 4px">{{ getFileName(op.filePath) }}</span>
                          <span style="color: #8c8c8c; font-size: 11px; margin-left: 6px">{{ getFileDir(op.filePath) }}</span>
                        </span>
                        <ChevronRight
                          v-if="
                            !isFileExpanded(message.id, 'write-' + op.filePath)
                          "
                          class="figma-chat-read-chevron"
                          :size="14"
                        />
                        <ChevronDown
                          v-else
                          class="figma-chat-read-chevron"
                          :size="14"
                        />
                      </div>
                      <div
                        v-if="
                          isFileExpanded(message.id, 'write-' + op.filePath)
                        "
                        class="figma-chat-code-wrapper"
                      >
                        <div class="figma-chat-code-header">
                          <span class="figma-chat-code-path">{{ getShortenedPath(op.filePath) }}</span>
                        </div>
                        <div class="figma-chat-file-item">
                          <pre
                            v-if="op.content"
                            v-memo="[op.content, op.filePath]"
                            class="figma-chat-write-preview"
                            v-html="
                              renderCodeWithLineNumbers(op.content || '', op.filePath || '')
                            "
                          ></pre>
                        </div>
                      </div>
                    </div>
                  </template>
                  <!-- 编写：每个文件独立展开（已去重） -->
                  <template
                    v-for="op in uniqueOps(message, 'edit')"
                    :key="'edit-' + op.filePath"
                  >
                    <div class="figma-chat-file-summary">
                      <div
                        class="figma-chat-file-summary-row"
                        @click="
                          toggleFileExpanded(message.id, 'edit-' + op.filePath)
                        "
                      >
                        <span>
                          编写
                          <span style="color: #1a1a1a; font-weight: 500; margin-left: 4px">{{ getFileName(op.filePath) }}</span>
                          <span style="color: #8c8c8c; font-size: 11px; margin-left: 6px">{{ getFileDir(op.filePath) }}</span>
                        </span>
                        <ChevronRight
                          v-if="
                            !isFileExpanded(message.id, 'edit-' + op.filePath)
                          "
                          class="figma-chat-read-chevron"
                          :size="14"
                        />
                        <ChevronDown
                          v-else
                          class="figma-chat-read-chevron"
                          :size="14"
                        />
                      </div>
                      <div
                        v-if="isFileExpanded(message.id, 'edit-' + op.filePath)"
                        class="figma-chat-code-wrapper"
                      >
                        <div class="figma-chat-code-header">
                          <span class="figma-chat-code-path">{{ getShortenedPath(op.filePath) }}</span>
                        </div>
                        <div class="figma-chat-file-item">
                          <pre
                            v-if="op.content"
                            v-memo="[op.content, op.filePath]"
                            class="figma-chat-write-preview"
                            v-html="
                              renderCodeWithLineNumbers(op.content || '', op.filePath || '')
                            "
                          ></pre>
                        </div>
                      </div>
                    </div>
                  </template>

                  <!-- 技能调用：每个技能独立展开 -->
                  <div
                    v-if="messageSkillCalls(message).length > 0"
                    class="figma-chat-file-summary"
                  >
                    <div
                      class="figma-chat-file-summary-row"
                      @click="toggleFileExpanded(message.id, 'skills')"
                    >
                      <span>
                        已调用
                        <span style="color: #1a1a1a; font-weight: 500; margin-left: 4px">
                          {{ messageSkillCalls(message).length }} 次技能
                        </span>
                      </span>
                      <ChevronRight
                        v-if="!isFileExpanded(message.id, 'skills')"
                        class="figma-chat-read-chevron"
                        :size="14"
                      />
                      <ChevronDown
                        v-else
                        class="figma-chat-read-chevron"
                        :size="14"
                      />
                    </div>
                    <div
                      v-if="isFileExpanded(message.id, 'skills')"
                      class="figma-chat-skill-call-list"
                    >
                      <div
                        v-for="(part, idx) in messageSkillCalls(message)"
                        :key="idx"
                        class="figma-chat-skill-call-card"
                      >
                        <div class="figma-chat-skill-call-header">
                          <BookOpen :size="12" class="figma-chat-skill-call-icon" />
                          <span class="figma-chat-skill-call-title">Launched skill {{ (part as any).toolName }}</span>
                        </div>
                        <div class="figma-chat-skill-call-body">
                          {{ getSkillDescription((part as any).toolName) || (part as any).output || 'Running skill...' }}
                        </div>
                        <div class="figma-chat-skill-call-footer">
                          技能启动完成 {{ formatSkillTime(part) }}
                        </div>
                      </div>
                    </div>
                  </div>
                </template>

                <!-- 读取的文件/目录展示 -->
                <template v-if="message.readOutputs?.length">
                  <div
                    v-for="(ro, ri) in message.readOutputs"
                    :key="'read-output-' + ri"
                    class="figma-chat-file-summary"
                  >
                    <div
                      class="figma-chat-file-summary-row"
                      @click="toggleFileExpanded(message.id, 'read-output-' + ri)"
                    >
                      <template v-if="ro.kind === 'file'">
                        <span>读取的文件</span>
                        <span class="figma-chat-file-name">{{ getFileName(ro.path) }}</span>
                        <span style="color: #a1a5b1; margin-left: auto; font-size: 10px;">{{ ro.language }}</span>
                      </template>
                      <template v-else>
                        <Folder :size="14" class="shrink-0" style="color: #a1a5b1" />
                        <span class="figma-chat-file-name">{{ getFileName(ro.path) }}</span>
                        <span style="color: #a1a5b1; margin-left: 6px">目录 · {{ ro.entries?.length ?? 0 }} 项</span>
                      </template>
                      <ChevronRight
                        v-if="!isFileExpanded(message.id, 'read-output-' + ri)"
                        class="figma-chat-read-chevron"
                        :size="14"
                      />
                      <ChevronDown
                        v-else
                        class="figma-chat-read-chevron"
                        :size="14"
                      />
                    </div>
                    <div
                      v-if="isFileExpanded(message.id, 'read-output-' + ri)"
                      class="figma-chat-file-list"
                    >
                      <!-- 文件内容 -->
                      <div v-if="ro.kind === 'file'" class="figma-chat-code-wrapper">
                        <div class="figma-chat-code-header">
                          <span class="figma-chat-code-path">{{ getShortenedPath(ro.path) }}</span>
                        </div>
                        <div class="figma-chat-file-item">
                          <pre
                            v-memo="[ro.content, ro.path]"
                            class="figma-chat-write-preview"
                            v-html="
                              renderCodeWithLineNumbers(ro.content, ro.path)
                            "
                          ></pre>
                        </div>
                      </div>
                      <!-- 目录列表 -->
                      <div v-else class="figma-chat-dir-list">
                        <div
                          v-for="entry in ro.entries"
                          :key="entry"
                          class="figma-chat-dir-item"
                        >
                          <Folder v-if="entry.endsWith('/')" :size="13" class="figma-chat-dir-icon" />
                          <FileText v-else :size="13" class="figma-chat-dir-icon" />
                          <span>{{ entry }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </template>

                <!-- 结构化 part 块：非文件操作的 tool/reasoning/retry 以折叠块独立渲染，不进入正文 -->
                <template v-for="part in messageOtherParts(message)" :key="part.partId || `${message.id}-${part.type}`">
                  <!-- Webfetch tool call links -->
                  <div
                    v-if="part.type === 'tool' && isUrlFetchTool((part as any).toolName)"
                    class="figma-chat-url-fetch-row"
                  >
                    <span class="figma-chat-url-fetch-title">{{ formatToolName((part as any).toolName) }}</span>
                    <a
                      :href="getUrlFromInput((part as any).input)"
                      target="_blank"
                      rel="noopener noreferrer"
                      class="figma-chat-url-fetch-link"
                    >
                      {{ getUrlFromInput((part as any).input) }}
                      <ArrowUpRight :size="12" class="figma-chat-url-fetch-icon" />
                    </a>
                  </div>

                  <!-- reasoning 折叠块：
                       - 默认仅在"当前轮的最后一条 assistant 消息"或"part 仍在运行"时展开，
                         之后轮进来时自动收起；让用户感受到"先输出一段，再缩起来"的活动流。
                       - 旧轮（lastAssistant 切换走）则保持收起，回看历史更紧凑。 -->
                  <details
                    v-else-if="part.type === 'reasoning' && (part as any).text"
                    :open="partShouldOpen(message, part)"
                    class="figma-chat-process-detail"
                    :class="{ 'is-running': reasoningIsRunning(message, part) }"
                  >
                    <summary class="figma-chat-process-summary">
                      <span
                        :class="[
                          'figma-chat-process-dot',
                          reasoningIsRunning(message, part) && 'figma-chat-process-dot--running',
                        ]"
                      />
                      <span
                        :class="[
                          'figma-chat-process-title',
                          reasoningIsRunning(message, part) && 'ta-text-shimmer'
                        ]"
                      >思考状态</span>
                      <span
                        v-if="reasoningDurationText(part)"
                        class="figma-chat-process-meta"
                      >已思考 {{ reasoningDurationText(part) }}</span>
                      <span
                        :class="[
                          'figma-chat-process-status-label',
                          reasoningIsRunning(message, part) && 'ta-text-shimmer'
                        ]"
                      >
                        {{ reasoningIsRunning(message, part) ? '思考中' : '已完成' }}
                      </span>
                      <ChevronRight class="figma-chat-process-chevron" :size="14" />
                    </summary>
                    <div class="figma-chat-process-body">
                      <MarkdownView
                        :source="(part as any).text || ''"
                        body-class="max-h-44 overflow-auto text-[12px] leading-5 text-[var(--ta-chat-muted)]"
                      />
                    </div>
                  </details>

                  <!-- tool 折叠块（bash/grep/glob 等，含输入/输出/错误）：
                       - 默认仅在"当前轮的最后一条 assistant 消息"或"part 仍在运行"时展开，
                         与 reasoning 保持一致的"先输出一段再缩起来"行为；但 bash 命令默认收起。 -->
                  <details
                    v-else-if="part.type === 'tool' && !isUrlFetchTool((part as any).toolName)"
                    :open="isToolOpen(message, part)"
                    :class="[
                      'figma-chat-process-detail',
                      toolIsFailed(part) && 'figma-chat-process-detail--error',
                      partIsRunning(part) && 'is-running'
                    ]"
                  >
                    <summary class="figma-chat-process-summary" @click.prevent="toggleToolOpen(message, part)">
                      <span
                        :class="[
                          'figma-chat-process-dot',
                          partIsRunning(part) && 'figma-chat-process-dot--running',
                          toolIsFailed(part) && 'figma-chat-process-dot--error',
                        ]"
                      />
                      <span
                        :class="[
                          'figma-chat-process-title',
                          partIsRunning(part) && 'ta-text-shimmer'
                        ]"
                      >{{ (part as any).toolName || '工具' }}</span>
                      <span
                        v-if="(part as any).input"
                        class="figma-chat-process-meta"
                      >
                        {{ summaryFromToolInput((part as any).toolName, (part as any).input) }}
                      </span>
                      <span
                        :class="[
                          'figma-chat-process-status-label',
                          toolIsFailed(part) && 'figma-chat-process-status-label--error',
                          partIsRunning(part) && 'ta-text-shimmer'
                        ]"
                      >{{
                        partIsRunning(part) ? '执行中' :
                        toolIsFailed(part) ? '失败' :
                        (part as any).status === 'completed' || (part as any).status === 'success' ? '已完成' : '等待'
                      }}</span>
                      <ChevronRight class="figma-chat-process-chevron" :size="14" />
                    </summary>
                    <div class="figma-chat-process-body">
                      <div
                        v-if="(part as any).input && Object.keys((part as any).input).length > 0"
                        class="figma-chat-tool-section"
                      >
                        <div class="figma-chat-tool-section-label">入参</div>
                        <pre class="figma-chat-tool-code max-h-28">{{ JSON.stringify((part as any).input, null, 2) }}</pre>
                      </div>
                      <div
                        v-if="toolOutputText(part)"
                        class="figma-chat-tool-section"
                      >
                        <div class="figma-chat-tool-section-label">
                          {{ toolIsFailed(part) ? '错误' : '输出' }}
                        </div>
                        <pre
                          :class="[
                            'figma-chat-tool-code',
                            toolIsFailed(part) ? 'max-h-24' : 'max-h-48',
                            toolIsFailed(part) && 'figma-chat-tool-code--error',
                          ]"
                        >{{ toolOutputText(part) }}</pre>
                      </div>
                    </div>
                  </details>

                  <!-- retry 错误块 -->
                  <div
                    v-else-if="part.type === 'retry'"
                    class="figma-chat-retry-block"
                  >
                    <AlertTriangle :size="14" class="figma-chat-retry-icon" />
                    <span class="figma-chat-retry-text">
                      重试第 {{ (part as any).attempt || '?' }} 次
                    </span>
                    <span
                      v-if="(part as any).error?.message"
                      class="figma-chat-retry-detail"
                    >{{ (part as any).error.message }}</span>
                  </div>
                </template>

                <div v-if="message._error" class="figma-chat-error-row">
                  <AlertTriangle :size="14" class="figma-chat-error-icon" />
                  <span class="figma-chat-error-text">{{
                    message.content
                  }}</span>
                </div>
                <div v-else-if="message.content.trim()" class="figma-chat-text-bubble">
                  <MarkdownView :source="formatThinking(message.content)" />
                </div>

                <!-- Inline Subtasks List -->
                <div
                  v-if="messageSubtasks(message).length > 0"
                  :class="['figma-chat-task-panel', 'inline-task-panel', collapsedMessages[message.id] && 'figma-chat-task-panel--collapsed']"
                >
                  <div :class="['figma-chat-task-summary', collapsedMessages[message.id] && 'figma-chat-task-summary--collapsed']">
                    <span>
                      已完成
                      {{ messageSubtasks(message).filter((t) => t.status === 'completed' || t.status === 'success').length }}
                      个任务 （共 {{ messageSubtasks(message).length }} 个）
                    </span>
                    <button
                      type="button"
                      class="figma-chat-task-collapse-btn"
                      @click="collapsedMessages[message.id] = !collapsedMessages[message.id]"
                      :title="collapsedMessages[message.id] ? '展开任务列表' : '收起任务列表'"
                    >
                      <ChevronDown v-if="collapsedMessages[message.id]" :size="12" />
                      <ChevronUp v-else :size="12" />
                    </button>
                  </div>
                  <div v-show="!collapsedMessages[message.id]" class="figma-chat-task-list">
                    <div
                      v-for="(tp, idx) in messageSubtasks(message)"
                      :key="idx"
                      :class="['figma-chat-task-row', `figma-chat-task-row--${tp.status}`]"
                    >
                      <CheckCircle
                        v-if="tp.status === 'completed' || tp.status === 'success'"
                        :size="12"
                        class="figma-chat-task-icon figma-chat-task-icon--completed"
                      />
                      <Loader2
                        v-else-if="tp.status === 'running' || tp.status === 'in_progress'"
                        :size="12"
                        class="figma-chat-task-icon figma-chat-task-icon--running figma-chat-spin"
                      />
                      <Circle
                        v-else
                        :size="12"
                        class="figma-chat-task-icon figma-chat-task-icon--pending"
                      />
                      <span class="figma-chat-task-label">{{ tp.label }}</span>
                      <span v-if="tp.detail" class="figma-chat-task-detail">{{ tp.detail }}</span>
                    </div>
                  </div>
                </div>
                <div
                  v-if="messageFiles(message).length > 0"
                  class="figma-chat-document-list"
                  aria-label="智能体返回文档"
                >
                  <component
                    :is="file.url ? 'a' : 'div'"
                    v-for="file in messageFiles(message)"
                    :key="file.id"
                    :href="file.url"
                    :target="file.url ? '_blank' : undefined"
                    :rel="file.url ? 'noreferrer' : undefined"
                    class="figma-chat-document-item"
                    :title="file.path || file.name"
                  >
                    <FileText :size="14" />
                    <span>{{ file.name }}</span>
                    <small v-if="file.mimeType">{{ file.mimeType }}</small>
                  </component>
                </div>
              </div>
            </div>
            <div v-if="message.content && message.content.trim()" class="figma-chat-feedback">
              <button
                v-if="message.content && message.content.trim()"
                type="button"
                class="figma-chat-action-btn"
                title="复制内容"
                @click="copyText(message.content, message.id + '-copy')"
              >
                <Check v-if="copySuccessId === message.id + '-copy'" :size="12" style="color: #2ecc71" />
                <Copy v-else :size="12" />
                <span>{{ copySuccessId === message.id + '-copy' ? '已复制' : '复制' }}</span>
              </button>
            </div>
          </div>
        </div>
      </template>



      <!-- 重试卡片 -->
      <div
        v-if="!activeSubagentSessionId && showTaskFailed"
        class="figma-chat-retry-card"
      >
        <div class="figma-chat-retry-card-header">
          <AlertTriangle :size="14" class="figma-chat-retry-card-icon" />
          <span class="figma-chat-retry-card-text">{{ taskFailureMessage }}</span>
          <span class="figma-chat-retry-card-copy" @click="copyErrorMessage">复制错误信息</span>
        </div>
        <button class="figma-chat-retry-card-btn" @click="emit('retry')">重试</button>
      </div>



      <!-- 空态 -->
      <div v-if="false && displayMessages.length === 0" class="figma-chat-empty">
        <div class="figma-chat-empty-icon">
          <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
            <circle
              cx="16"
              cy="16"
              r="12"
              stroke="#ccc"
              stroke-width="1.4"
              stroke-dasharray="2 2"
            />
            <path
              d="M12 16L15 19L20 13"
              stroke="#ccc"
              stroke-width="1.4"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </div>
        <p class="figma-chat-empty-title">开始一次新的对话</p>
        <p class="figma-chat-empty-hint">
          告诉我你想测试的模块或功能，我会自动生成测试用例
        </p>
      </div>

      <!-- 运行中状态 -->
      <div
        v-if="false && showRunningAssistant"
        class="figma-chat-running-assistant"
      >
        <div class="figma-chat-avatar">
          <img :src="aiHeaderUrl" alt="AI" class="figma-chat-avatar-icon" />
        </div>
        <div class="figma-chat-running-content">
          <div class="figma-chat-status">
            <Loader2 :size="14" class="figma-chat-status-loader figma-chat-spin" />
            <span class="figma-chat-status-text">思考中... <span class="figma-chat-running-timer">{{ formattedRunDuration }}</span></span>
            <button
              v-if="reasoningText"
              type="button"
              class="figma-chat-thinking-toggle"
              :aria-label="thinkingExpanded ? '收起思考过程' : '展开思考过程'"
              @click="thinkingExpanded = !thinkingExpanded"
            >
              <ChevronDown
                v-if="thinkingExpanded"
                class="figma-chat-thinking-chevron"
              />
              <ChevronRight v-else class="figma-chat-thinking-chevron" />
            </button>
          </div>
          <div
            v-if="thinkingExpanded && reasoningText"
            class="figma-chat-thinking-body"
          >
            <pre class="figma-chat-thinking-text" v-html="reasoningHtml" />
          </div>
        </div>
      </div>
    </div>





    <!-- 收起态：仅浮动模式展示柔光小圆点；点击展开，支持拖动改位置 -->
    <button
      v-if="!activeSubagentSessionId && processStatusDotVisible"
      type="button"
      :class="[
        'figma-chat-process-status-dot',
        processReady ? 'is-ready' : 'is-blocking',
        isDraggingProcessDot && 'is-dragging',
      ]"
      :style="processStatusDotStyle"
      :title="processStatusTitle"
      :aria-label="`展开进程状态：${processStatusTitle}`"
      @click="handleProcessStatusDotClick"
      @pointerdown="onProcessStatusDotPointerDown"
    />

    <!-- 展开态：无用户拖动记录时保留历史内联位置，进入浮动模式后锚定到圆点附近。 -->
    <div
      v-else-if="!activeSubagentSessionId && processStatusVisible"
      :class="[
        'figma-chat-process-status',
        processReady ? 'is-ready' : 'is-blocking',
      ]"
      ref="processStatusCard"
      :style="processStatusCardStyle"
      role="button"
      tabindex="0"
      :title="`收起进程状态`"
      aria-label="收起进程状态"
      @click="handleProcessStatusCardClick"
      @keydown.enter.prevent="handleProcessStatusCardClick"
      @keydown.space.prevent="handleProcessStatusCardClick"
      @pointerdown="onProcessStatusCardPointerDown"
    >
      <div class="figma-chat-process-copy">
        <span class="figma-chat-process-title">{{ processStatusTitle }}</span>
        <span v-if="processStatusText" class="figma-chat-process-message">{{
          processStatusText
        }}</span>
      </div>
      <div v-if="processStatus?.status === 'NEEDS_INITIALIZATION'" class="figma-chat-process-actions">
        <button
          type="button"
          class="figma-chat-process-action figma-chat-process-help"
          data-testid="chat-process-help"
          aria-label="查看 TestAgent 进程初始化手册"
          title="查看初始化说明"
          @click.stop="emit('open-help', 'process-initialization')"
          @keydown.stop
        >
          <CircleHelp :size="15" />
        </button>
        <button
          type="button"
          class="figma-chat-process-action figma-chat-process-init"
          :disabled="
            processInitializing || processLoading || !processStatus.initializable
          "
          @click.stop="emit('initialize-process')"
          @keydown.stop
        >
          {{ processInitButtonLabel }}
        </button>
      </div>
    </div>

    <!-- 技能面板：输入 / 触发 -->
    <div v-if="!activeSubagentSessionId && showSkillPanel" class="figma-chat-skill-panel">
      <div class="figma-chat-choice-header">
        <div class="figma-chat-choice-question">技能</div>
        <button
          type="button"
          class="figma-chat-choice-close"
          @click="dismissSkillPanel"
        >
          <X :size="14" />
        </button>
      </div>
      <div class="figma-chat-skill-list">
        <div
          v-for="skill in filteredSkills"
          :key="skill.name"
          class="figma-chat-skill-row"
          @click="selectSkill(skill)"
        >
          <BookOpen :size="16" class="figma-chat-skill-icon" />
          <div class="figma-chat-skill-info">
            <span class="figma-chat-skill-name">{{ skillLabel(skill) }}</span
            >&nbsp;&nbsp;
            <span v-if="skillDescription(skill)" class="figma-chat-skill-desc"> {{ skillDescription(skill) }}</span>
          </div>
        </div>
        <div v-if="filteredSkills.length === 0" class="figma-chat-skill-empty">
          无匹配技能
        </div>
      </div>
    </div>

    <!-- # 子条目候选：聚合当前个人 worktree 的 spec 目录中同一子条目在需求、设计、编码、测试阶段的文件。 -->
    <div v-if="!activeSubagentSessionId && showRequirementPanel" class="figma-chat-agent-panel figma-chat-requirement-panel">
      <div class="figma-chat-choice-header">
        <div class="figma-chat-choice-question">需求子条目</div>
        <button
          type="button"
          class="figma-chat-choice-close"
          @click="dismissRequirementPanel"
        >
          <X :size="14" />
        </button>
      </div>
      <div class="figma-chat-agent-list">
        <div
          v-for="reference in filteredRequirementReferences"
          :key="reference.id"
          class="figma-chat-agent-row figma-chat-requirement-row"
          @click="selectRequirementReference(reference)"
        >
          <BookOpen :size="16" class="figma-chat-requirement-icon" />
          <div class="figma-chat-agent-info figma-chat-reference-info">
            <span class="figma-chat-agent-name figma-chat-reference-name-full">{{ reference.subitemName }}</span>
            <span class="figma-chat-agent-desc">{{ reference.requirementName }} · {{ reference.filePaths.length }} 个关联文件</span>
          </div>
        </div>
        <div v-if="workspaceRequirementReferencesLoading" class="figma-chat-agent-empty">正在读取当前工作区需求结构…</div>
        <div
          v-else-if="filteredRequirementReferences.length === 0"
          class="figma-chat-agent-empty"
        >
          当前工作区没有匹配的需求子条目
        </div>
      </div>
    </div>

    <!-- @ 候选：保留可提及 Agent，并增加当前个人 worktree 文件。 -->
    <div v-if="!activeSubagentSessionId && showAgentPanel" class="figma-chat-agent-panel">
      <div class="figma-chat-choice-header">
        <div class="figma-chat-choice-question">Agent 与文件</div>
        <button
          type="button"
          class="figma-chat-choice-close"
          @click="dismissAgentPanel"
        >
          <X :size="14" />
        </button>
      </div>
      <div class="figma-chat-agent-list">
        <div v-if="filteredMentionAgents.length" class="figma-chat-suggestion-section">Agent</div>
        <div
          v-for="agent in filteredMentionAgents"
          :key="agentValue(agent)"
          class="figma-chat-agent-row"
          @click="selectMentionAgent(agent)"
        >
          <User :size="16" class="figma-chat-agent-icon" />
          <div class="figma-chat-agent-info">
            <span class="figma-chat-agent-name">{{ agentLabel(agent) }}</span>
            <span v-if="agentDescription(agent)" class="figma-chat-agent-desc">{{ agentDescription(agent) }}</span>
          </div>
        </div>
        <div v-if="filteredMentionFiles.length" class="figma-chat-suggestion-section">文件</div>
        <div
          v-for="file in filteredMentionFiles"
          :key="file.path"
          class="figma-chat-agent-row figma-chat-file-row"
          @click="selectMentionFile(file)"
        >
          <FileText :size="16" class="figma-chat-file-icon" />
          <div class="figma-chat-agent-info figma-chat-file-info">
            <span class="figma-chat-agent-name figma-chat-file-name-full" :title="file.path">{{ file.name || getFileName(file.path) }}</span>
            <span class="figma-chat-agent-desc" :title="file.path">{{ file.directory || '工作区根目录' }}</span>
          </div>
        </div>
        <div v-if="workspaceFileCandidatesLoading" class="figma-chat-agent-empty">正在搜索当前工作区文件…</div>
        <div
          v-else-if="filteredMentionAgents.length === 0 && filteredMentionFiles.length === 0"
          class="figma-chat-agent-empty"
        >
          无匹配 Agent 或文件
        </div>
      </div>
    </div>

    <!-- 作废说明：运行中旧任务面板已由 OpencodeTimeline 的工具/事件行取代，避免两套来源显示不一致。 -->
    <section
      v-if="visiblePermissions.length || visibleQuestions.length"
      class="figma-chat-question-dock"
    >
      <div
        v-for="permission in visiblePermissions"
        :key="permission.requestId"
        class="figma-chat-permission-card"
      >
        <div class="figma-chat-question-title">{{ permission.title ?? permission.type }}</div>
        <div class="figma-chat-question-description">
          {{ permission.description ?? permission.pattern ?? permission.requestId }}
        </div>
        <div class="figma-chat-question-actions">
          <button type="button" class="figma-chat-question-submit" @click="emit('reply-permission', permission.requestId, 'once')">一次</button>
          <button type="button" class="figma-chat-question-submit" @click="emit('reply-permission', permission.requestId, 'always')">始终</button>
          <button type="button" class="figma-chat-question-reject" @click="emit('reply-permission', permission.requestId, 'reject')">拒绝</button>
        </div>
      </div>
      <template v-for="item in visibleQuestions" :key="item.requestId">
        <div
          v-if="item.questions.length > 0"
          class="figma-chat-question-card"
        >
          <div class="figma-chat-question-scroll">
            <div class="figma-chat-question-page-head">
              <div class="figma-chat-question-page-meta">
                <div class="figma-chat-question-progress">{{ questionProgressText(item) }}</div>
                <div class="figma-chat-question-type">{{ questionTypeText(currentQuestionRequired(item)) }}</div>
              </div>
              <div v-if="currentQuestionRequired(item).header" class="figma-chat-question-header">
                {{ currentQuestionRequired(item).header }}
              </div>
            </div>
            <div class="figma-chat-question-item">
              <div class="figma-chat-question-title">{{ currentQuestionRequired(item).text }}</div>
              <div class="figma-chat-question-hint">{{ questionHelpText(currentQuestionRequired(item)) }}</div>
              <input
                v-if="isTextQuestion(currentQuestionRequired(item))"
                :value="questionCustomAnswers[currentQuestionRequired(item).questionId] ?? ''"
                class="figma-chat-question-custom-input"
                placeholder="输入你的答案..."
                @input="setQuestionCustomAnswer(currentQuestionRequired(item), ($event.target as HTMLInputElement).value)"
              />
              <div v-else class="figma-chat-question-options" :class="{ 'is-multiple': isMultipleQuestion(currentQuestionRequired(item)) }">
                <button
                  v-for="option in questionOptions(currentQuestionRequired(item))"
                  :key="option.id"
                  type="button"
                  :class="[
                    'figma-chat-question-option',
                    isQuestionOptionSelected(currentQuestionRequired(item), option.label) && 'is-selected',
                  ]"
                  @click="chooseQuestionOption(currentQuestionRequired(item), option.label)"
                >
                  <span class="figma-chat-question-option-mark" aria-hidden="true"></span>
                  <span class="figma-chat-question-option-copy">
                    <span class="figma-chat-question-option-label">{{ option.label }}</span>
                    <span v-if="option.description" class="figma-chat-question-option-description">{{ option.description }}</span>
                  </span>
                </button>
                <label
                  :class="[
                    'figma-chat-question-custom-card',
                    isCustomAnswerSelected(currentQuestionRequired(item)) && 'is-selected',
                  ]"
                >
                  <span class="figma-chat-question-option-mark" aria-hidden="true"></span>
                  <span class="figma-chat-question-option-copy">
                    <span class="figma-chat-question-option-label">输入自己的答案</span>
                    <input
                      :value="questionCustomAnswers[currentQuestionRequired(item).questionId] ?? ''"
                      class="figma-chat-question-custom-input"
                      placeholder="输入你的答案..."
                      @input="setQuestionCustomAnswer(currentQuestionRequired(item), ($event.target as HTMLInputElement).value)"
                    />
                  </span>
                </label>
              </div>
            </div>
          </div>
          <div class="figma-chat-question-actions">
            <button
              type="button"
              class="figma-chat-question-reject"
              @click="emit('reject-question', item.requestId)"
            >
              忽略
            </button>
            <div class="figma-chat-question-action-spacer"></div>
            <button
              v-if="!isFirstQuestionPage(item)"
              type="button"
              class="figma-chat-question-prev"
              @click="showPreviousQuestion(item)"
            >
              上一步
            </button>
            <button
              v-if="!isLastQuestionPage(item)"
              type="button"
              class="figma-chat-question-next"
              @click="showNextQuestion(item)"
            >
              下一步
            </button>
            <button
              v-else
              type="button"
              class="figma-chat-question-submit"
              :disabled="!canReplyQuestion(item)"
              @click="replyQuestion(item)"
            >
              提交
            </button>
          </div>
        </div>
      </template>
    </section>
    <div
      v-if="!activeSubagentSessionId"
      ref="workStatusDockRef"
      data-testid="figma-work-status-dock"
    />
    <ChatContextAttachmentList
      v-if="!activeSubagentSessionId"
      :items="chatContexts"
      :total-char-count="chatContextTotalChars ?? 0"
      :over-limit="chatContextOverLimit || contextSubmitBlocked"
      :error="chatContextError || contextSendBlockedReason"
      @remove="emit('remove-chat-context', $event)"
      @clear="emit('clear-chat-contexts')"
      @preview="onContextPreview"
    />
    <!-- 统一输入卡片：textarea + 底部工具行（附件、模型、新建、发送/停止）整合在一个圆角卡片内 -->
    <div v-if="!activeSubagentSessionId" class="figma-chat-composer">
      <section
        v-if="nightPickerOpen"
        class="figma-chat-night-picker"
        data-testid="night-slot-picker"
        aria-label="选择夜间执行时间"
      >
        <div class="figma-chat-night-picker-head">
          <div>
            <strong>{{ nightPickerMode === 'adjust' ? '调整启动时间' : '安排夜间执行' }}</strong>
            <span>北京时间 · 每 15 分钟一个启动时间段</span>
          </div>
          <button type="button" aria-label="关闭时间选择" @click="closeNightPicker"><X :size="14" /></button>
        </div>
        <div v-if="nightSlotsLoading" class="figma-chat-night-picker-loading">
          <Spinner /> 正在计算合适时间…
        </div>
        <div v-else-if="!nightSlots?.slots.length" class="figma-chat-night-picker-empty">
          暂无可用夜间时间段，请稍后重试。
        </div>
        <div v-else class="figma-chat-night-track">
          <button
            v-for="slot in nightSlots.slots"
            :key="slot.slotStart"
            type="button"
            data-testid="night-slot-option"
            :class="[
              'figma-chat-night-slot',
              selectedNightSlotStart === slot.slotStart && 'is-selected',
              slot.recommended && 'is-recommended',
            ]"
            :disabled="!slot.available || nightTaskSubmitting"
            :title="slot.available ? `${slot.reservedCount}/${slot.capacity} 已预约` : '该时间段已满'"
            @click="selectNightSlot(slot.slotStart, slot.available)"
          >
            <span>{{ formatNightTime(slot.slotStart) }}</span>
            <small v-if="slot.recommended">系统推荐</small>
            <small v-else-if="!slot.available">已满</small>
            <small v-else>{{ slot.reservedCount }}/{{ slot.capacity }}</small>
          </button>
        </div>
        <div class="figma-chat-night-picker-foot">
          <span v-if="selectedNightSlotStart">
            将在 {{ formatNightDate(selectedNightSlotStart) }} {{ formatNightTime(selectedNightSlotStart) }}–{{
              formatNightTime(nightSlots?.slots.find((slot) => slot.slotStart === selectedNightSlotStart)?.slotEnd || selectedNightSlotStart)
            }} 内启动
          </span>
          <span v-else>请选择一个仍有容量的时间段</span>
          <button
            type="button"
            data-testid="night-schedule-confirm"
            :disabled="!selectedNightSlotStart || nightTaskSubmitting"
            @click="confirmNightSchedule"
          >
            <Spinner v-if="nightTaskSubmitting" />
            {{ nightPickerMode === 'adjust' ? '保存调整' : '确认定时执行' }}
          </button>
        </div>
      </section>
      <div
        class="figma-chat-input-card"
        :class="{
          'is-resizing': isResizingComposer,
          'is-disabled': composerInteractionBlocked,
          'is-night-locked': nightSessionLocked,
        }"
        @click="onComposerCardClick"
      >
        <div
          class="figma-chat-composer-resize-handle"
          role="separator"
          aria-label="拖动调整输入框高度"
          aria-orientation="horizontal"
          @pointerdown.stop.prevent="startComposerResize"
        />
        <textarea
          v-model="localInput"
          class="figma-chat-textarea"
          :style="composerTextareaStyle"
          :placeholder="composerPlaceholder"
          rows="1"
          :disabled="running || composerInteractionBlocked || readonlySubmitBlocked || nightSessionLocked"
          :title="sendBlockedTitle"
          @keydown="onKeydown"
          @compositionstart="onCompositionStart"
          @compositionend="onCompositionEnd"
        />
        <div class="figma-chat-card-actions">
          <!-- 左侧：附件上传 -->
          <el-tooltip
            content="上传附件"
            placement="top"
            :show-after="0"
          >
            <button
              type="button"
              class="figma-chat-card-btn figma-chat-attachment-btn"
              aria-label="上传附件"
              :disabled="composerInteractionBlocked"
              @click="openAttachmentDialog"
            >
              <Upload class="figma-chat-btn-icon" />
            </button>
          </el-tooltip>
          <!-- 主 Agent 选择：遵循 opencode local.agent.list()，只展示 primary/all 且非 hidden 的 Agent。 -->
          <div class="figma-chat-agent-select-wrapper">
            <el-tooltip
              :content="selectedAgentLabel"
              placement="top"
              :show-after="100"
              :disabled="agentPickerDisabled && mainAgentOptions.length === 0"
            >
              <button
                type="button"
                class="figma-chat-card-btn figma-chat-agent-btn"
                :disabled="agentPickerDisabled"
                aria-label="切换 Agent"
                @click.stop="toggleAgentDropdown"
              >
                <User class="figma-chat-btn-icon" />
                <span class="figma-chat-agent-label">{{ selectedAgentLabel }}</span>
                <ChevronDown class="figma-chat-btn-icon" />
              </button>
            </el-tooltip>
            <div v-if="agentDropdownOpen" class="figma-chat-agent-dropdown" role="dialog" aria-label="Agent 选择" @click.stop>
              <div class="figma-chat-agent-dropdown-search">
                <input
                  v-model="agentSearch"
                  type="text"
                  placeholder="搜索 Agent..."
                  class="figma-chat-agent-search-input"
                  @keydown.enter.prevent
                />
              </div>
              <div v-if="agentsRefreshing" class="figma-chat-agent-refreshing">刷新中...</div>
              <div class="figma-chat-agent-dropdown-list">
                <div v-if="agentsLoading" class="figma-chat-agent-empty">
                  正在加载 Agent...
                </div>
                <div v-else-if="agentsError" class="figma-chat-agent-empty figma-chat-agent-error">
                  <span>{{ agentsError }}</span>
                  <button type="button" class="figma-chat-agent-retry" aria-label="重新加载 Agent" @click="retryAgentCatalog">
                    重新加载
                  </button>
                </div>
                <template v-else>
                  <button
                    v-for="agent in filteredMainAgents"
                    :key="agentValue(agent)"
                    type="button"
                    :class="['figma-chat-agent-option-item', isSelectedAgent(agent) && 'is-active']"
                    @click="selectAgent(agent)"
                  >
                    <div class="figma-chat-agent-option-info">
                      <span
                        class="figma-chat-agent-option-dot"
                        :style="{ backgroundColor: agent.color || '#3366ff' }"
                      />
                      <span class="figma-chat-agent-option-text">
                        <span class="figma-chat-agent-option-name">{{ agentLabel(agent) }}</span>
                        <span v-if="agentDescription(agent)" class="figma-chat-agent-option-desc">{{ agentDescription(agent) }}</span>
                      </span>
                    </div>
                    <span v-if="isSelectedAgent(agent)" class="figma-chat-agent-option-checked">✓</span>
                  </button>
                  <div v-if="filteredMainAgents.length === 0" class="figma-chat-agent-empty">
                    {{ mainAgentOptions.length === 0 ? '暂无可用 Agent' : '暂无匹配 Agent' }}
                  </div>
                </template>
              </div>
            </div>
          </div>
          <!-- 中间：模型选择 -->
          <div class="figma-chat-model-select-wrapper">
            <el-tooltip
              :content="selectedModelLabel || '选择模型'"
              placement="top"
              :show-after="100"
              :disabled="modelSelectionDisabled"
            >
              <button
                type="button"
                class="figma-chat-card-btn figma-chat-model-btn"
                :disabled="modelSelectionDisabled"
                aria-label="切换模型"
                @click.stop="toggleDropdown"
              >
                <span class="figma-chat-model-label">{{
                  selectedModelLabel || '选择模型'
                }}</span>
                <ChevronDown class="figma-chat-btn-icon" />
              </button>
            </el-tooltip>
            <div v-if="dropdownOpen" class="figma-chat-model-dropdown" role="dialog" aria-label="模型选择" @click.stop>
              <div class="figma-chat-model-dropdown-search">
                <input
                  v-model="modelSearch"
                  type="text"
                  placeholder="搜索模型..."
                  class="figma-chat-model-search-input"
                  @keydown.enter.prevent
                />
              </div>
              <div class="figma-chat-model-dropdown-list">
                <!-- 上新推荐 -->
                <div v-if="!modelSearch.trim() && recommendedModels.length" class="figma-chat-model-section">
                  <div class="figma-chat-model-section-title">上新推荐</div>
                  <div class="figma-chat-model-recommended-grid">
                    <el-tooltip
                      v-for="model in recommendedModels"
                      :key="modelValue(model)"
                      :content="model.name"
                      placement="top"
                      :show-after="100"
                    >
                      <button
                        type="button"
                        :class="['figma-chat-model-rec-item', modelValue(model) === selectedModel && 'is-active']"
                        @click="selectModel(model)"
                      >
                        <span class="figma-chat-model-rec-dot" :style="{ backgroundColor: getModelColor(model) }" />
                        <span class="figma-chat-model-rec-name">{{ model.name }}</span>
                      </button>
                    </el-tooltip>
                  </div>
                </div>

                <!-- 所有模型 -->
                <div
                  v-for="group in modelGroups"
                  :key="group.providerId"
                  class="figma-chat-model-group"
                >
                  <div class="figma-chat-model-group-title">{{ group.providerName }}</div>
                  <el-tooltip
                    v-for="model in group.models"
                    :key="modelValue(model)"
                    :content="model.name"
                    placement="top"
                    :show-after="100"
                  >
                    <button
                      type="button"
                      :class="['figma-chat-model-option-item', modelValue(model) === selectedModel && 'is-active']"
                      @click="selectModel(model)"
                    >
                      <div class="figma-chat-model-option-info">
                        <span class="figma-chat-model-option-dot" :style="{ backgroundColor: getModelColor(model) }" />
                        <span class="figma-chat-model-option-name">{{ model.name }}</span>
                      </div>
                      <span v-if="modelValue(model) === selectedModel" class="figma-chat-model-option-checked">✓</span>
                    </button>
                  </el-tooltip>
                </div>
                
                <div v-if="modelGroups.length === 0" class="figma-chat-model-empty">
                  暂无匹配模型
                </div>
              </div>
            </div>
          </div>
          <div class="figma-chat-card-spacer" />
          <!-- 右侧：新建对话 + 发送/停止 -->
          <el-tooltip
            content="新建对话"
            placement="top"
            :show-after="0"
          >
            <button
              type="button"
              class="figma-chat-card-btn figma-chat-new-btn"
              aria-label="新建对话"
              data-onboarding="new-conversation"
              :disabled="newConversationBlocked"
              @click="emit('new-conversation')"
            >
              <SquarePen class="figma-chat-btn-icon" />
            </button>
          </el-tooltip>
          <el-tooltip
            :content="nightSessionLocked ? nightSessionLockedReason : '定时执行'"
            placement="top"
            :show-after="0"
          >
            <button
              type="button"
              class="figma-chat-card-btn figma-chat-night-btn"
              aria-label="定时执行"
              :disabled="nightScheduleBlocked"
              :aria-expanded="nightPickerOpen"
              @click="openNightPicker"
            >
              <Clock3 class="figma-chat-btn-icon" />
            </button>
          </el-tooltip>
          <button
            v-if="!running"
            type="button"
            class="figma-chat-send-card"
            :disabled="!localInput.trim() || sendSubmitBlocked"
            :title="sendBlockedTitle"
            aria-label="发送"
            @click="submit"
          >
            <Send class="figma-chat-send-icon" />
          </button>
          <button
            v-else
            type="button"
            class="figma-chat-stop-card"
            :disabled="stopDisabled"
            :title="stopDisabledReason || '停止执行'"
            aria-label="停止执行"
            @click="stop"
          >
            <Square class="figma-chat-stop-icon" fill="currentColor" />
          </button>
        </div>
      </div>
    </div>
    <div v-else class="figma-chat-subagent-notice">
      <span>子 Agent 不支持对话，</span>
      <button type="button" class="figma-chat-subagent-return" @click="returnToRootAgent">切换到主 Agent</button>
      <span>。</span>
    </div>
    <!-- 与左侧面板、中心面板底部栏等高的常驻 footer -->
    <div class="figma-chat-footer">
      <div v-if="!activeSubagentSessionId" class="figma-chat-usage">
        <span
          v-if="showTaskStopped"
          class="figma-chat-status-item figma-chat-status-stopped"
        >
          <MinusCircle class="figma-chat-status-icon-inline" />
          <span>已手动终止</span>
        </span>
        <span
          v-else-if="showTaskFailed"
          class="figma-chat-status-item figma-chat-status-failed"
        >
          <MinusCircle class="figma-chat-status-icon-inline" />
          <span>任务失败</span>
        </span>
        <span
          v-else-if="showTaskCompleted"
          class="figma-chat-status-item figma-chat-status-completed"
        >
          <CheckCircle class="figma-chat-status-icon-inline" />
          <span>任务完成</span>
        </span>

        <span
          v-if="(showTaskStopped || showTaskFailed || showTaskCompleted) && hasTaskUsageDisplay"
          class="figma-chat-status-divider"
          aria-hidden="true"
        >·</span>

        <template v-if="hasTaskUsageDisplay">
          <img
            v-if="running"
            :src="planLoadingUrl"
            alt=""
            class="figma-chat-usage-icon"
          />
          <span v-else class="figma-chat-usage-dot" aria-hidden="true" />
          <span class="figma-chat-usage-label">任务消耗：</span>
          <span class="figma-chat-usage-value">
            <template v-if="taskUsage?.totalDuration">
              {{ taskUsage.totalDuration }}
            </template>
            <template v-if="taskUsage?.totalDuration && displayTokens !== undefined">
              &nbsp;
            </template>
            <template v-if="displayTokens !== undefined">
              {{ formatTokens(displayTokens) }} tokens
            </template>
          </span>
        </template>
      </div>
    </div>

    <div
      v-if="attachmentDialogOpen"
      class="figma-chat-attachment-mask"
      role="presentation"
      @click.self="closeAttachmentDialog"
    >
      <section
        class="figma-chat-attachment-dialog"
        role="dialog"
        aria-modal="true"
        aria-label="上传附件"
      >
        <header class="figma-chat-attachment-header">
          <div>
            <h3 class="figma-chat-attachment-title">上传附件</h3>
            <p class="figma-chat-attachment-subtitle">
              附件会随测试任务一起提交
            </p>
          </div>
          <button
            type="button"
            class="figma-chat-attachment-close"
            aria-label="关闭上传附件弹窗"
            @click="closeAttachmentDialog"
          >
            <X :size="14" />
          </button>
        </header>
        <button type="button" class="figma-chat-attachment-drop" @click.prevent>
          <span class="figma-chat-attachment-drop-icon" aria-hidden="true">
            <Upload :size="22" />
          </span>
          <span class="figma-chat-attachment-drop-title"
            >选择或拖拽文件到这里</span
          >
          <span class="figma-chat-attachment-drop-hint"
            >支持文档、图片和日志文件，后台接口接入后开放上传。</span
          >
        </button>
        <div class="figma-chat-attachment-disabled">
          <span class="figma-chat-attachment-disabled-dot" aria-hidden="true" />
          当前仅展示前端样式，暂未连接后台上传能力
        </div>
      </section>
    </div>

    <!--
      文件变更抽屉：覆盖在聊天面板上方，左侧是文件列表，右侧是 git-merge 风格 diff 视图。
      - 左侧点击切换文件；首次打开默认选中第一个文件。
      - 右侧按 unified diff 行渲染：+ 绿底、- 红底、@@ 与文件头作为元信息展示。
      - 关闭：点击 X、点击遮罩、按 Esc。
    -->
    <div
      v-if="false"
      class="figma-chat-drawer-mask"
      role="presentation"
      @click.self="closeChangesDrawer"
    >
      <div
        class="figma-chat-drawer"
        role="dialog"
        aria-modal="true"
        aria-label="文件变更 Diff"
        :data-testid="'chat-changes-drawer'"
      >
        <header class="figma-chat-drawer-header">
          <div class="figma-chat-drawer-title">
            <span class="figma-chat-drawer-title-text">文件变更</span>
            <span class="figma-chat-drawer-count">{{
              drawerFiles.length
            }}</span>
          </div>
          <div class="figma-chat-drawer-summary">
            <span class="figma-chat-add">+{{ totalAdditions }}</span>
            <span class="figma-chat-del">-{{ totalDeletions }}</span>
          </div>
          <button
            type="button"
            :class="['figma-chat-drawer-toggle', showContext && 'is-active']"
            :title="showContext ? '只显示变更行' : '显示上下文行'"
            :aria-pressed="showContext"
            :aria-label="showContext ? '隐藏上下文行' : '显示上下文行'"
            data-testid="chat-drawer-context-toggle"
            @click="showContext = !showContext"
          >
            <component :is="showContext ? EyeOff : Eye" :size="14" />
            <span class="figma-chat-drawer-toggle-text">{{
              showContext ? '上下文' : '仅变更'
            }}</span>
          </button>
          <button
            type="button"
            class="figma-chat-drawer-close"
            aria-label="关闭 diff 抽屉"
            @click="closeChangesDrawer"
          >
            <X :size="14" />
          </button>
        </header>
        <div class="figma-chat-drawer-body">
          <aside class="figma-chat-drawer-files" aria-label="变更文件列表">
            <ul class="figma-chat-drawer-files-list">
              <li v-for="file in drawerFiles" :key="file.path">
                <button
                  type="button"
                  :class="[
                    'figma-chat-drawer-file-item',
                    file.path === drawerSelectedFile?.path && 'is-active',
                  ]"
                  :data-testid="`chat-drawer-file-${file.path}`"
                  :title="file.path"
                  @click="selectDrawerFile(file.path)"
                >
                  <span
                    :class="[
                      'figma-chat-drawer-file-badge',
                      file.status === 'added' && 'is-add',
                      file.status === 'deleted' && 'is-del',
                      file.status === 'modified' && 'is-mod',
                    ]"
                    >{{
                      file.status === 'added'
                        ? 'A'
                        : file.status === 'deleted'
                        ? 'D'
                        : 'M'
                    }}</span
                  >
                  <span class="figma-chat-drawer-file-path">{{
                    getFileName(file.path)
                  }}</span>
                  <span class="figma-chat-drawer-file-stats">
                    <span v-if="file.additions" class="figma-chat-add"
                      >+{{ file.additions }}</span
                    >
                    <span v-if="file.deletions" class="figma-chat-del"
                      >-{{ file.deletions }}</span
                    >
                  </span>
                </button>
              </li>
            </ul>
          </aside>
          <section class="figma-chat-drawer-diff" aria-label="文件 diff 视图">
            <header
              v-if="drawerSelectedFile"
              class="figma-chat-drawer-diff-header"
            >
              <span
                class="figma-chat-drawer-diff-path"
                :title="drawerSelectedFile.path"
              >
                {{ drawerSelectedFile.path }}
              </span>
              <span class="figma-chat-drawer-diff-stats">
                <span class="figma-chat-add"
                  >+{{ drawerSelectedAdditions }}</span
                >
                <span class="figma-chat-del"
                  >-{{ drawerSelectedDeletions }}</span
                >
              </span>
            </header>
            <div
              v-if="drawerSelectedLines.length && visibleDiffLines.length"
              ref="drawerScroll"
              class="figma-chat-drawer-diff-scroll"
              :data-testid="'chat-drawer-diff-scroll'"
            >
              <div
                v-for="(line, index) in visibleDiffLines"
                :key="`${line.kind}-${index}`"
                :class="[
                  'figma-chat-drawer-diff-line',
                  line.kind === 'add' && 'is-add',
                  line.kind === 'del' && 'is-del',
                  line.kind === 'meta' && 'is-meta',
                ]"
              >
                <span class="figma-chat-drawer-diff-num">{{
                  line.oldNum ?? ''
                }}</span>
                <span class="figma-chat-drawer-diff-num">{{
                  line.newNum ?? ''
                }}</span>
                <span class="figma-chat-drawer-diff-sign">
                  {{
                    line.kind === 'add'
                      ? '+'
                      : line.kind === 'del'
                      ? '-'
                      : line.kind === 'meta'
                      ? ''
                      : ' '
                  }}
                </span>
                <span class="figma-chat-drawer-diff-text">{{
                  line.text || ' '
                }}</span>
              </div>
            </div>
            <div
              v-else-if="drawerSelectedLines.length && !visibleDiffLines.length"
              class="figma-chat-drawer-diff-empty"
            >
              <p>当前文件没有可显示的变更行</p>
              <p class="figma-chat-drawer-diff-empty-hint">
                点击右上角"上下文"可显示完整 diff 行。
              </p>
            </div>
            <div v-else class="figma-chat-drawer-diff-empty">
              <p>暂无 diff 内容</p>
              <p class="figma-chat-drawer-diff-empty-hint">
                后端事件未携带 patch 文本，请稍候或重新触发 Run。
              </p>
            </div>
          </section>
        </div>
      </div>
    </div>

    <!-- 会话列表使用 Teleport 覆盖左侧编辑区，保留右侧对话栏可见且可连续切换。 -->
    <Teleport to="body">
      <aside
        v-if="historyDrawerOpen && historyDrawerPlacement"
        class="figma-chat-history-drawer"
        :class="`figma-chat-history-drawer--${historyDrawerPlacement.mode}`"
        :style="historyDrawerPanelStyle"
        :data-placement="historyDrawerPlacement.mode"
        role="dialog"
        aria-modal="false"
        aria-label="会话列表"
      >
        <div class="figma-chat-history-tabs" role="tablist" aria-label="会话列表内容">
          <button
            :id="historyDrawerSessionsTabId"
            ref="historyDrawerSessionsTabEl"
            type="button"
            class="figma-chat-history-tab"
            :class="{ 'is-active': historyDrawerView === 'sessions' }"
            role="tab"
            :aria-selected="historyDrawerView === 'sessions'"
            :aria-controls="historyDrawerSessionsPanelId"
            :tabindex="historyDrawerView === 'sessions' ? 0 : -1"
            data-testid="session-list-sessions-tab"
            @click="showHistoryDrawerView('sessions')"
            @keydown="onHistoryDrawerTabKeydown($event, 'sessions')"
          >
            会话 <span>{{ historyTotalCount }}</span>
          </button>
          <button
            :id="historyDrawerNightTabId"
            ref="historyDrawerNightTabEl"
            type="button"
            class="figma-chat-history-tab"
            :class="{ 'is-active': historyDrawerView === 'night' }"
            role="tab"
            :aria-selected="historyDrawerView === 'night'"
            :aria-controls="historyDrawerNightPanelId"
            :tabindex="historyDrawerView === 'night' ? 0 : -1"
            data-testid="session-list-night-tasks-tab"
            @click="showHistoryDrawerView('night')"
            @keydown="onHistoryDrawerTabKeydown($event, 'night')"
          >
            待执行任务 <span>{{ nightTasks.length }}</span>
          </button>
          <button
            type="button"
            class="figma-chat-history-drawer-close"
            aria-label="关闭会话列表抽屉"
            @click="closeHistoryDrawer()"
          >
            <X :size="14" />
          </button>
        </div>

        <div v-if="historyDrawerView === 'sessions'" class="figma-chat-history-search">
          <input
            ref="historyDrawerSearchEl"
            v-model="historySearchQuery"
            type="text"
            placeholder="搜索会话..."
            class="figma-chat-history-search-input"
            aria-label="搜索会话列表"
          />
        </div>

        <div
          v-if="historyDrawerView === 'sessions'"
          :id="historyDrawerSessionsPanelId"
          class="figma-chat-history-body"
          role="tabpanel"
          :aria-labelledby="historyDrawerSessionsTabId"
        >
          <div v-if="visibleHistory.length === 0" class="figma-chat-history-empty">
            <MessageSquare :size="32" class="figma-chat-history-empty-icon" />
            <p class="figma-chat-history-empty-text">
              {{ historySearchQuery.trim() ? '无匹配会话' : '暂无会话记录，快在下方开启新会话吧~' }}
            </p>
          </div>
          <ul v-else class="figma-chat-history-list">
            <li v-for="item in visibleHistory" :key="item.id">
              <button
                type="button"
                class="figma-chat-history-card"
                :class="{ 'is-active': item.id === currentSessionId }"
                :title="item.title"
                :aria-current="item.id === currentSessionId ? 'true' : undefined"
                @click="selectHistoryItem(item.id)"
              >
                <div class="figma-chat-history-card-icon">
                  <Spinner
                    v-if="item.runtimeState === 'running'"
                    class="figma-chat-history-card-spinner"
                    style="width: 15px; height: 15px;"
                  />
                  <CheckCircle
                    v-else
                    :size="15"
                    class="figma-chat-history-card-completed"
                  />
                  <Bell
                    v-if="item.pendingQuestion"
                    :size="10"
                    class="figma-chat-history-card-attention"
                  />
                </div>
                <div class="figma-chat-history-card-content">
                  <div class="figma-chat-history-card-title-row">
                    <div class="figma-chat-history-card-title">{{ item.title || '新对话' }}</div>
                    <span v-if="item.sourceType === 'SCHEDULED_TASK'" class="figma-chat-history-source-badge">夜间执行</span>
                    <span
                      :class="[
                        'figma-chat-history-card-status',
                        item.runtimeState === 'running'
                          ? 'figma-chat-history-card-status--running'
                          : 'figma-chat-history-card-status--completed'
                      ]"
                    >
                      {{ historyRuntimeLabel(item) }}
                    </span>
                  </div>
                  <div class="figma-chat-history-card-context">{{ historyContextText(item) }}</div>
                  <div class="figma-chat-history-card-meta">
                    <span>创建 {{ historyTime(item.createdAt) }}</span>
                    <span>更新 {{ historyTime(item.updatedAt) }}</span>
                    <span class="figma-chat-history-card-id">#{{ item.id.slice(-6) }}</span>
                  </div>
                </div>
              </button>
            </li>
          </ul>
          <div v-if="historyHasMore" class="figma-chat-history-load-more">
            <button
              type="button"
              class="figma-chat-history-load-more-btn"
              :disabled="historyLoadingMore"
              @click="emit('load-more-history')"
            >
              {{ historyLoadingMore ? '加载中...' : '显示更多会话' }}
            </button>
          </div>
        </div>
        <div
          v-else
          :id="historyDrawerNightPanelId"
          class="figma-chat-history-body figma-chat-history-body--night"
          role="tabpanel"
          :aria-labelledby="historyDrawerNightTabId"
        >
          <section class="figma-chat-night-list" data-testid="night-task-list">
            <div v-if="nightTasks.length === 0" class="figma-chat-night-empty">
              <Clock3 :size="22" />
              <strong>暂无待执行任务</strong>
              <span>在消息框中写好任务后，点击定时执行即可安排到今晚。</span>
            </div>
            <template v-else>
              <article
                v-for="task in orderedNightTasks"
                :key="task.taskId"
                class="figma-chat-night-card"
                :class="{ 'is-dispatching': task.status === 'DISPATCHING' }"
              >
                <div class="figma-chat-night-card-head">
                  <span class="figma-chat-night-status">{{ nightTaskStatusLabel(task) }}</span>
                  <span class="figma-chat-night-time">{{ formatNightRange(task) }}</span>
                </div>
                <strong class="figma-chat-night-title">{{ task.sessionTitle || '新对话' }}</strong>
                <p class="figma-chat-night-preview">{{ task.contentPreview }}</p>
                <span class="figma-chat-night-created">创建于 {{ formatNightCreatedAt(task.createdAt) }}</span>
                <div class="figma-chat-night-actions">
                  <button type="button" @click="openNightTaskConversation(task.sessionId)">查看对话</button>
                  <template v-if="task.status === 'SCHEDULED'">
                    <button type="button" :disabled="nightActionPending(task.taskId)" @click="beginAdjustNightTask(task)">调整时间</button>
                    <button
                      v-if="cancelConfirmTaskId !== task.taskId"
                      type="button"
                      class="is-danger"
                      :disabled="nightActionPending(task.taskId)"
                      @click="askCancelNightTask(task.taskId)"
                    >取消任务</button>
                    <template v-else>
                      <span class="figma-chat-night-confirm-copy">确定取消？</span>
                      <button type="button" class="is-danger" @click="confirmCancelNightTask(task.taskId)">确认</button>
                      <button type="button" @click="cancelConfirmTaskId = null">返回</button>
                    </template>
                  </template>
                  <span v-else class="figma-chat-night-dispatch-copy">正在复用对话后台执行能力启动…</span>
                </div>
              </article>
            </template>
          </section>
        </div>
      </aside>
    </Teleport>

    <div
      v-if="rawOutputOpen"
      class="figma-chat-raw-output-panel"
      :style="rawOutputPanelStyle"
      role="dialog"
      aria-modal="false"
      aria-label="原始输出"
    >
      <header class="figma-chat-raw-output-header" @pointerdown="startRawOutputDrag">
        <div class="figma-chat-raw-output-title">
          <FileText :size="15" />
          <span>原始输出</span>
          <span class="figma-chat-raw-output-count">{{ filteredRawOutputEntries.length }}/{{ props.rawOutputEntries?.length ?? 0 }}</span>
        </div>
        <div class="figma-chat-raw-output-actions">
          <button
            v-for="option in rawOutputFilterOptions"
            :key="option.value"
            type="button"
            :class="['figma-chat-raw-filter', rawOutputFilter === option.value && 'is-active']"
            @click="rawOutputFilter = option.value"
          >
            {{ option.label }}
          </button>
          <button type="button" class="figma-chat-raw-action" @click="emit('clear-raw-output')">清空</button>
          <button
            type="button"
            class="figma-chat-raw-action"
            :disabled="filteredRawOutputEntries.length === 0"
            aria-label="下载原始输出"
            title="下载当前过滤结果为文本文件"
            @click="downloadRawOutput"
          >
            <Download :size="14" />
            <span>下载</span>
          </button>
          <button
            type="button"
            class="figma-chat-raw-close"
            aria-label="关闭原始输出"
            @click="closeRawOutput"
          >
            <X :size="14" />
          </button>
        </div>
      </header>
      <div class="figma-chat-raw-search">
        <input
          v-model="rawOutputSearchQuery"
          type="search"
          class="figma-chat-raw-search-input"
          placeholder="搜索原始输出内容、URL、traceId、runId..."
          aria-label="搜索原始输出"
        />
      </div>
      <div class="figma-chat-raw-output-body">
        <div v-if="filteredRawOutputEntries.length === 0" class="figma-chat-raw-empty">
          {{ rawOutputSearchQuery.trim() || rawOutputFilter !== 'all' ? '无匹配的原始报文' : '当前会话暂无原始报文' }}
        </div>
        <template v-else>
          <section
            v-for="entry in filteredRawOutputEntries"
            :key="entry.id"
            class="figma-chat-raw-entry"
          >
            <div class="figma-chat-raw-entry-meta">
              <span :class="['figma-chat-raw-kind', `figma-chat-raw-kind--${entry.kind}`]">{{ rawOutputKindLabel(entry.kind) }}</span>
              <span class="figma-chat-raw-entry-title">
                <span
                  v-for="(part, index) in highlightRawOutputText(entry.title)"
                  :key="`title-${entry.id}-${index}`"
                  :class="part.matched && 'figma-chat-raw-highlight'"
                >{{ part.text }}</span>
              </span>
              <span class="figma-chat-raw-entry-time">{{ rawOutputTime(entry.occurredAt) }}</span>
            </div>
            <div class="figma-chat-raw-entry-details">
              <span v-if="entry.status">
                <span
                  v-for="(part, index) in highlightRawOutputText(`status ${entry.status}`)"
                  :key="`status-${entry.id}-${index}`"
                  :class="part.matched && 'figma-chat-raw-highlight'"
                >{{ part.text }}</span>
              </span>
              <span v-if="entry.contentType">
                <span
                  v-for="(part, index) in highlightRawOutputText(entry.contentType)"
                  :key="`content-type-${entry.id}-${index}`"
                  :class="part.matched && 'figma-chat-raw-highlight'"
                >{{ part.text }}</span>
              </span>
              <span v-if="entry.traceId">
                <span
                  v-for="(part, index) in highlightRawOutputText(`trace ${entry.traceId}`)"
                  :key="`trace-${entry.id}-${index}`"
                  :class="part.matched && 'figma-chat-raw-highlight'"
                >{{ part.text }}</span>
              </span>
              <span v-if="entry.runId">
                <span
                  v-for="(part, index) in highlightRawOutputText(`run ${entry.runId}`)"
                  :key="`run-${entry.id}-${index}`"
                  :class="part.matched && 'figma-chat-raw-highlight'"
                >{{ part.text }}</span>
              </span>
              <span v-if="entry.truncated">已截断</span>
            </div>
            <pre class="figma-chat-raw-pre"><span
              v-for="(part, index) in highlightRawOutputText(rawOutputBody(entry))"
              :key="`body-${entry.id}-${index}`"
              :class="part.matched && 'figma-chat-raw-highlight'"
            >{{ part.text }}</span></pre>
          </section>
        </template>
      </div>
    </div>

    <el-dialog
      v-model="negativeFeedbackOpen"
      title="不满意反馈"
      width="360px"
      append-to-body
      class="figma-chat-feedback-dialog"
    >
      <div class="figma-chat-feedback-reasons">
        <button
          v-for="reason in feedbackReasonOptions"
          :key="reason.value"
          type="button"
          :class="[
            'figma-chat-feedback-reason',
            negativeFeedbackReason === reason.value && 'is-selected',
          ]"
          @click="negativeFeedbackReason = reason.value"
        >
          {{ reason.label }}
        </button>
      </div>
      <el-input
        v-model="negativeFeedbackComment"
        type="textarea"
        :rows="3"
        maxlength="300"
        show-word-limit
        placeholder="补充说明"
      />
      <template #footer>
        <button type="button" class="figma-chat-feedback-cancel" @click="negativeFeedbackOpen = false">取消</button>
        <button type="button" class="figma-chat-feedback-submit" @click="submitNegativeFeedback">提交</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.figma-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 56px 0 16px;
  height: 30px;
  border-bottom: 1px solid var(--ta-border);
  background: var(--ta-surface);
  user-select: none;
}
.figma-chat-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}
.figma-chat-night-source-badge,
.figma-chat-history-source-badge {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 3px;
  min-height: 18px;
  padding: 0 6px;
  border: 1px solid #d8dbe7;
  border-radius: 9px;
  background: #f1f2f8;
  color: #3d466e;
  font-size: 10px;
  font-weight: 650;
  line-height: 16px;
}
.figma-chat-history-source-badge {
  margin-left: auto;
}
.figma-chat-header-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--ta-muted);
  background: transparent;
  border: 1px solid transparent;
  transition: all 0.15s ease;
  cursor: pointer;
  flex-shrink: 0;
}
.figma-chat-header-btn:hover {
  color: var(--ta-text);
  background: var(--ta-hover);
  border-color: transparent;
}
.figma-chat-history-tabs {
  display: flex;
  align-items: stretch;
  gap: 4px;
  min-height: 38px;
  padding: 4px 12px 0;
  border-bottom: 1px solid var(--ta-border, #e4e5e7);
  background: var(--ta-panel, #fafafa);
}
.figma-chat-history-drawer-close {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  align-self: center;
  width: 22px;
  height: 22px;
  margin-bottom: 4px;
  border: none;
  background: transparent;
  color: var(--ta-muted, #71717a);
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.12s ease;
}
.figma-chat-history-drawer-close:hover {
  background: var(--ta-hover, #f0f0f0);
  color: var(--ta-text, #18181b);
}
.figma-chat-history-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 8px;
  border: 0;
  background: transparent;
  color: #71717a;
  font: inherit;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}
.figma-chat-history-tab::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  border-radius: 2px 2px 0 0;
  background: transparent;
}
.figma-chat-history-tab.is-active {
  color: #18181b;
  font-weight: 650;
}
.figma-chat-history-tab.is-active::after {
  background: #27325e;
}
.figma-chat-history-tab span {
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  border-radius: 9px;
  background: #eef0f8;
  color: #27325e;
  font-size: 10px;
  line-height: 17px;
  text-align: center;
}
.figma-chat-history-header-icon {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
}
.figma-chat-history-header-spinner {
  color: #2563eb;
}
.figma-chat-history-running-badge {
  position: absolute;
  top: -8px;
  right: -10px;
  min-width: 15px;
  height: 15px;
  padding: 0 4px;
  border-radius: 999px;
  background: #dc2626;
  color: #fff;
  border: 1px solid var(--ta-surface);
  font-size: 10px;
  font-weight: 700;
  line-height: 14px;
  text-align: center;
}
.figma-chat-history-alert-bell {
  color: #d97706;
  animation: figma-chat-bell 1.35s ease-in-out infinite;
  transform-origin: 50% 0;
}
.figma-chat-raw-output-panel {
  position: fixed;
  z-index: 2500;
  width: min(760px, calc(100vw - 48px));
  height: min(620px, calc(100vh - 96px));
  min-width: 360px;
  min-height: 260px;
  max-width: calc(100vw - 16px);
  max-height: calc(100vh - 16px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  resize: both;
  border: 1px solid var(--ta-border);
  border-radius: 8px;
  background: var(--ta-surface);
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.18);
}
.figma-chat-raw-output-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 42px;
  padding: 8px 10px 8px 12px;
  border-bottom: 1px solid var(--ta-border);
  background: var(--ta-panel);
  cursor: move;
  user-select: none;
}
.figma-chat-raw-output-title {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--ta-text);
}
.figma-chat-raw-output-count {
  color: var(--ta-muted);
  font-size: 12px;
  font-weight: 500;
}
.figma-chat-raw-output-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.figma-chat-raw-filter,
.figma-chat-raw-action,
.figma-chat-raw-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 26px;
  border: 1px solid var(--ta-border);
  border-radius: 6px;
  background: var(--ta-surface);
  color: var(--ta-muted);
  font-size: 12px;
  cursor: pointer;
}
.figma-chat-raw-filter {
  min-width: 42px;
  padding: 0 8px;
}
.figma-chat-raw-filter.is-active {
  color: var(--ta-text);
  border-color: var(--ta-ink);
  background: var(--ta-hover);
}
.figma-chat-raw-action {
  padding: 0 9px;
  gap: 4px;
}
.figma-chat-raw-close {
  width: 26px;
}
.figma-chat-raw-filter:hover,
.figma-chat-raw-action:hover,
.figma-chat-raw-close:hover {
  color: var(--ta-text);
  background: var(--ta-hover);
}
.figma-chat-raw-action:disabled,
.figma-chat-raw-action:disabled:hover {
  cursor: not-allowed;
  opacity: 0.5;
  color: var(--ta-muted);
  background: var(--ta-surface);
}
.figma-chat-raw-search {
  flex-shrink: 0;
  padding: 9px 12px;
  border-bottom: 1px solid var(--ta-border);
  background: var(--ta-panel);
}
.figma-chat-raw-search-input {
  width: 100%;
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--ta-border);
  border-radius: 6px;
  background: var(--ta-surface);
  color: var(--ta-text);
  font-size: 12px;
  outline: none;
}
.figma-chat-raw-search-input:focus {
  border-color: var(--ta-focus, #3b82f6);
}
.figma-chat-raw-output-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px;
  background: var(--ta-surface);
}
.figma-chat-raw-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ta-muted);
  font-size: 13px;
}
.figma-chat-raw-entry {
  border: 1px solid var(--ta-border);
  border-radius: 8px;
  background: var(--ta-panel);
  overflow: hidden;
}
.figma-chat-raw-entry + .figma-chat-raw-entry {
  margin-top: 10px;
}
.figma-chat-raw-entry-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px 4px;
  min-width: 0;
}
.figma-chat-raw-kind {
  flex-shrink: 0;
  border-radius: 4px;
  padding: 2px 6px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
}
.figma-chat-raw-kind--request {
  color: #075985;
  background: #e0f2fe;
}
.figma-chat-raw-kind--response {
  color: #166534;
  background: #dcfce7;
}
.figma-chat-raw-kind--sse {
  color: #7c2d12;
  background: #ffedd5;
}
.figma-chat-raw-entry-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ta-text);
  font-size: 12px;
  font-weight: 600;
}
.figma-chat-raw-entry-time {
  margin-left: auto;
  flex-shrink: 0;
  color: var(--ta-muted);
  font-size: 11px;
}
.figma-chat-raw-entry-details {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 10px 8px;
  color: var(--ta-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 11px;
}
.figma-chat-raw-highlight {
  border-radius: 3px;
  padding: 0 1px;
  background: #fef08a;
  color: #713f12;
  box-decoration-break: clone;
  -webkit-box-decoration-break: clone;
}
.figma-chat-raw-pre {
  margin: 0;
  max-height: none;
  overflow: auto;
  border-top: 1px solid var(--ta-border);
  padding: 10px;
  color: var(--ta-text);
  background: #f8fafc;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}
.figma-chat-history-drawer {
  position: fixed;
  overflow: hidden;
  border: 1px solid var(--ta-border, #dedfe3);
  border-radius: 10px 0 0 10px;
  background: var(--ta-surface);
  box-shadow: -8px 0 28px rgba(15, 23, 42, 0.14);
  display: flex;
  flex-direction: column;
  z-index: 1200;
  font-family: var(--font-sans);
  animation: figma-chat-session-drawer-in 0.2s cubic-bezier(0.2, 0.7, 0.2, 1);
}
.figma-chat-history-drawer--overlay {
  border-radius: 10px;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.2);
}
.figma-chat-history-search {
  padding: 12px 16px;
  border-bottom: 1px solid var(--ta-border);
  background: var(--ta-panel);
}
.figma-chat-history-search-input {
  width: 100%;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--ta-text);
  background: var(--ta-surface);
  border: 1px solid var(--ta-border);
  outline: none;
  transition: border-color 0.15s ease;
}
.figma-chat-history-search-input:focus {
  border-color: var(--ta-focus, #3b82f6);
}
.figma-chat-history-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 16px;
}
.figma-chat-history-body--night {
  background: var(--ta-panel, #fafafa);
}
.figma-chat-history-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  color: var(--ta-muted);
  text-align: center;
}
.figma-chat-history-empty-icon {
  opacity: 0.4;
  margin-bottom: 12px;
}
.figma-chat-history-empty-text {
  font-size: 13px;
  line-height: 1.5;
}
.figma-chat-history-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  list-style: none;
  padding: 0;
  margin: 0;
}
.figma-chat-history-card {
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--ta-surface);
  border: 1px solid var(--ta-border);
  text-align: left;
  transition: all 0.15s ease;
  cursor: pointer;
}
.figma-chat-history-card:hover {
  background: var(--ta-hover);
  border-color: var(--ta-muted);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transform: translateY(-1px);
}
.figma-chat-history-card.is-active {
  border-color: #27325e;
  background: #f3f4f9;
  box-shadow: inset 3px 0 0 #27325e;
}
.figma-chat-history-card-icon {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex: 0 0 auto;
  color: var(--ta-muted);
  padding-top: 2px;
}
.figma-chat-history-card-spinner {
  color: #2563eb;
}
.figma-chat-history-card-completed {
  color: #16a34a;
}
.figma-chat-history-card-attention {
  position: absolute;
  top: -3px;
  right: -4px;
  color: #d97706;
  fill: #fbbf24;
  animation: figma-chat-bell 1.35s ease-in-out infinite;
  transform-origin: 50% 0;
}
.figma-chat-history-card-content {
  flex: 1;
  min-width: 0;
}
.figma-chat-history-card-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  margin-bottom: 2px;
}
.figma-chat-history-card-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--ta-text);
  margin-bottom: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.figma-chat-history-card-status {
  flex: 0 0 auto;
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
}
.figma-chat-history-card-status--running {
  color: #1d4ed8;
  background: #dbeafe;
}
.figma-chat-history-card-status--completed {
  color: #166534;
  background: #dcfce7;
}
.figma-chat-history-card-context {
  margin-bottom: 2px;
  color: var(--ta-muted);
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.figma-chat-history-card-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 10px;
  font-size: 11px;
  color: var(--ta-muted);
}
.figma-chat-history-card-id {
  font-family: var(--font-mono);
  opacity: 0.8;
}
.figma-chat-history-load-more {
  display: flex;
  justify-content: center;
  padding: 12px 0 4px;
}
.figma-chat-history-load-more-btn {
  min-width: 160px;
  min-height: 32px;
  border-radius: 6px;
  border: 1px solid var(--ta-border);
  background: var(--ta-surface);
  color: var(--ta-text);
  font-size: 13px;
  cursor: pointer;
}
.figma-chat-history-load-more-btn:hover:not(:disabled) {
  background: var(--ta-hover);
}
.figma-chat-history-load-more-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.figma-chat-root {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #fff;
  font-family: var(--font-sans);
  /* diff 等面板内浮层仍使用 root 作为定位上下文；会话列表独立 Teleport 到 body。 */
  position: relative;
}

@keyframes figma-chat-session-drawer-in {
  from {
    transform: translateX(12px);
    opacity: 0.72;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .figma-chat-history-drawer {
    animation: none;
  }
}

/* ---- Header ---- */

.figma-chat-title {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.0143em;
  color: #18181b;
  margin: 0;
  font-family: var(--font-sans);
  line-height: 20px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}

.figma-chat-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 0.8px solid #dfdfdf;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: background-color 0.12s ease;
}

.figma-chat-close:hover {
  background: #f0f0f0;
}

.figma-chat-close-icon {
  width: 14px;
  height: 14px;
  color: #777;
}

/* ---- Scroll Area ---- */
.figma-chat-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior-y: none;
  padding: 12px 12px 6px;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.figma-chat-new-content-btn {
  position: sticky;
  bottom: 8px;
  z-index: 8;
  align-self: center;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 999px;
  background: #fff;
  color: var(--ta-text, #18181b);
  padding: 4px 12px;
  font-size: 12px;
  line-height: 18px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.12);
  cursor: pointer;
  transition: background-color 0.12s ease, border-color 0.12s ease;
}

.figma-chat-new-content-btn:hover {
  background: var(--ta-hover, #f4f4f5);
  border-color: var(--ta-border-strong, #d4d4d8);
}

.figma-chat-night-list {
  display: grid;
  gap: 10px;
  align-content: start;
}
.figma-chat-night-empty {
  display: flex;
  min-height: 180px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: #71717a;
  text-align: center;
}
.figma-chat-night-empty strong {
  color: #3f3f46;
  font-size: 13px;
}
.figma-chat-night-empty span {
  max-width: 250px;
  font-size: 12px;
  line-height: 18px;
}
.figma-chat-night-card {
  display: grid;
  gap: 7px;
  padding: 11px 12px;
  border: 1px solid #dfe2eb;
  border-left: 3px solid #27325e;
  border-radius: 9px;
  background: linear-gradient(115deg, #fbfbfd 0%, #f6f7fb 100%);
  color: #27272a;
}
.figma-chat-night-card.is-dispatching {
  border-left-color: #d9993b;
  background: linear-gradient(115deg, #fffdf8 0%, #faf7ef 100%);
}
.figma-chat-night-card--current {
  margin-top: 2px;
}
.figma-chat-night-card--failed {
  border-color: #efc9c6;
  border-left-color: #b5423b;
  background: #fffafa;
}
.figma-chat-night-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.figma-chat-night-status {
  color: #27325e;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
}
.figma-chat-night-card.is-dispatching .figma-chat-night-status {
  color: #986218;
}
.figma-chat-night-card--failed .figma-chat-night-status {
  color: #a23731;
}
.figma-chat-night-time {
  color: #71717a;
  font-size: 11px;
  white-space: nowrap;
}
.figma-chat-night-title {
  overflow: hidden;
  color: #27272a;
  font-size: 13px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.figma-chat-night-preview,
.figma-chat-night-error {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  color: #52525b;
  font-size: 12px;
  line-height: 18px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.figma-chat-night-error {
  color: #9f302a;
}
.figma-chat-night-created {
  color: #8a8a93;
  font-size: 11px;
  line-height: 16px;
}
.figma-chat-night-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  min-height: 24px;
}
.figma-chat-night-actions button {
  min-height: 24px;
  padding: 0 8px;
  border: 1px solid #d6d8df;
  border-radius: 6px;
  background: #fff;
  color: #3f3f46;
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}
.figma-chat-night-actions button:hover:not(:disabled) {
  border-color: #9ca3b7;
  background: #f7f7fa;
}
.figma-chat-night-actions button.is-danger {
  color: #a23731;
}
.figma-chat-night-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.figma-chat-night-confirm-copy,
.figma-chat-night-dispatch-copy {
  color: #71717a;
  font-size: 11px;
}

/* ---- File Changes Card (above task usage) ---- */
.figma-chat-changes-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  margin: 8px 18px 0;
  background: #fafafa;
  border: 1px solid #efefef;
  border-radius: 8px;
  flex-shrink: 0;
  cursor: pointer;
  font-family: var(--font-sans);
  transition: background-color 0.12s ease, border-color 0.12s ease;
}

.figma-chat-changes-card:hover {
  background: #f0f0f0;
  border-color: #e0e0e0;
}

.figma-chat-changes-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  border-radius: 6px;
  background: linear-gradient(135deg, #3b5bff 0%, #5b6cff 100%);
}

.figma-chat-changes-title {
  font-size: 12px;
  font-weight: 500;
  color: #18181b;
  line-height: 18px;
  white-space: nowrap;
}

.figma-chat-changes-spacer {
  flex: 1;
}

.figma-chat-changes-stats {
  display: inline-flex;
  gap: 6px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  font-weight: 500;
}

.figma-chat-changes-arrow {
  width: 14px;
  height: 14px;
  color: #999;
  flex-shrink: 0;
}

/* ---- Message Bubbles ---- */
.figma-chat-bubble {
  display: inline-block;
  max-width: 100%;
  padding: 4px 6px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 20px;
  letter-spacing: -0.0107em;
  word-break: break-word;
  white-space: pre-wrap;
}

.figma-chat-user-message {
  align-self: flex-end;
  max-width: 80%;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.figma-chat-user-meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  align-items: flex-end;
}

.figma-chat-user-meta-row .figma-chat-bubble-meta {
  margin-top: 0;
}

.figma-chat-avatar--user {
  margin-top: 0;
  background: transparent;
  border-radius: 6px;
  color: #a1a5b1;
}

.figma-chat-avatar--user .figma-chat-avatar-icon {
  width: 14px;
  height: 14px;
}

.figma-chat-bubble--user {
  display: block;
  background: var(--ta-chat-user-bg);
  color: #111;
  border-radius: 12px;
  position: relative;
}

.figma-chat-bubble--assistant {
  display: block;
  background: transparent;
  padding: 0;
  color: #333;
  border-top-left-radius: 2px;
}

.figma-chat-text-bubble {
  background: #fafafc;
  border: 1px solid #eef0f3;
  padding: 14px 16px;
  border-radius: 12px;
  border-top-left-radius: 2px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  color: #1a1a1a;
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.6;
  position: relative;
}

.figma-chat-bubble-copy-btn {
  position: absolute;
  top: 6px;
  right: 6px;
  opacity: 0.35;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 4px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: #ffffff;
  color: var(--ta-chat-muted, #888);
  cursor: pointer;
  transition: opacity 0.2s, background-color 0.12s;
  z-index: 10;
  padding: 0;
}

.figma-chat-bubble-copy-btn--assistant {
  top: 10px;
  right: 10px;
}

.figma-chat-bubble--user:hover .figma-chat-bubble-copy-btn,
.figma-chat-text-bubble:hover .figma-chat-bubble-copy-btn {
  opacity: 1;
}

.figma-chat-bubble-copy-btn:hover {
  background: var(--ta-panel-2, #f4f4f5);
  color: var(--ta-chat-text, #333);
  opacity: 1;
}

.figma-chat-bubble-copy-btn:active {
  background: var(--ta-panel-3, #e4e4e7);
}

/* ===== 目录/文件浏览结构样式 ===== */
.figma-chat-dir-list {
  padding: 4px 0;
  margin-top: 4px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 200px;
  overflow-y: auto;
}

.figma-chat-dir-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ta-chat-text, #333);
  padding: 2px 0;
  cursor: default;
}

.figma-chat-dir-icon {
  color: var(--ta-chat-muted, #8c8c8c);
  flex-shrink: 0;
}

.figma-chat-document-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 6px;
}

.figma-chat-document-item {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding: 5px 8px;
  border: 1px solid var(--ta-chat-border);
  border-radius: 6px;
  background: var(--ta-chat-process-bg);
  color: var(--ta-chat-text);
  font-size: 12px;
  text-decoration: none;
}

.figma-chat-document-item span {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.figma-chat-document-item small {
  color: var(--ta-chat-muted);
  font-size: 10px;
}

.figma-chat-bubble--error {
  display: block;
  background: rgba(235, 94, 83, 0.06);
  border: 1px solid rgba(235, 94, 83, 0.25);
  border-radius: 8px;
  padding: 10px 12px;
}

.figma-chat-error-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.figma-chat-error-icon {
  flex-shrink: 0;
  color: #eb5e53;
  margin-top: 2px;
}

.figma-chat-error-text {
  font-size: 13px;
  line-height: 20px;
  color: #8a2f29;
  word-break: break-word;
  white-space: pre-wrap;
}

.figma-chat-feedback {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  margin-top: 6px;
  flex-shrink: 0;
}

.figma-chat-completed-feedback {
  margin-top: 0;
}

.figma-chat-completed-feedback .figma-chat-feedback-btn {
  border: none;
  background: transparent;
  padding: 0;
  width: 20px;
  height: 20px;
  border-radius: 4px;
}

.figma-chat-completed-feedback .figma-chat-feedback-btn:hover:not(:disabled),
.figma-chat-completed-feedback .figma-chat-feedback-btn.is-selected {
  background: transparent;
  border-color: transparent;
  color: #1f5fbf;
}

.figma-chat-completed-feedback .figma-chat-feedback-btn--negative:hover:not(:disabled),
.figma-chat-completed-feedback .figma-chat-feedback-btn--negative.is-selected {
  background: transparent;
  border-color: transparent;
  color: #b94030;
}

.figma-chat-feedback-btn,
.figma-chat-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 26px;
  padding: 0 8px;
  border: 1px solid #dfe3ea;
  border-radius: 6px;
  background: #fff;
  color: #5b6472;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  flex-shrink: 0;
  white-space: nowrap;
}

.figma-chat-feedback-btn:hover:not(:disabled),
.figma-chat-feedback-btn.is-selected,
.figma-chat-action-btn:hover:not(:disabled) {
  border-color: #8ab4ff;
  background: #eef5ff;
  color: #1f5fbf;
}

.figma-chat-feedback-btn--negative:hover:not(:disabled),
.figma-chat-feedback-btn--negative.is-selected {
  border-color: #f1b8ae;
  background: #fff3f1;
  color: #b94030;
}

.figma-chat-feedback-btn:disabled {
  cursor: wait;
  opacity: 0.65;
}

.figma-chat-feedback-reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.figma-chat-feedback-reason {
  height: 28px;
  padding: 0 10px;
  border: 1px solid #dfe3ea;
  border-radius: 6px;
  background: #fff;
  color: #394150;
  font-size: 12px;
  cursor: pointer;
}

.figma-chat-feedback-reason.is-selected,
.figma-chat-feedback-reason:hover {
  border-color: #f1b8ae;
  background: #fff3f1;
  color: #b94030;
}

.figma-chat-feedback-cancel,
.figma-chat-feedback-submit {
  min-width: 64px;
  height: 30px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.figma-chat-feedback-cancel {
  border: 1px solid #dfe3ea;
  background: #fff;
  color: #4b5563;
}

.figma-chat-feedback-submit {
  border: 1px solid #1f5fbf;
  background: #2563eb;
  color: #fff;
}

.figma-chat-bubble-content {
  font-size: 14px;
  line-height: 22px;
  color: inherit;
}

.figma-chat-file-summary {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  cursor: pointer;
  user-select: none;
}
.figma-chat-file-summary-row {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  line-height: 18px;
  color: var(--ta-chat-text, #333);
  font-weight: 600;
  white-space: nowrap;
}

.figma-chat-history-loading {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--ta-chat-muted, #8c8c8c);
  font-size: 13px;
}

.figma-chat-history-loading-icon {
  animation: figma-chat-spin 0.9s linear infinite;
}

.figma-chat-user-feedback {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}
.figma-chat-file-name {
  color: #a1a5b1;
  margin-left: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 1;
  min-width: 0;
}
.figma-chat-read-chevron {
  flex-shrink: 0;
  color: #6b7280;
}
.figma-chat-file-list {
  margin: 4px 0 8px 3px;
  padding: 4px 0 4px 12px;
  border-left: 1px solid var(--ta-chat-border, #e5e5e5);
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.figma-chat-file-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  line-height: 18px;
}
.figma-chat-file-tag {
  flex-shrink: 0;
  padding: 0 2px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 18px;
  font-weight: 500;
}
.figma-chat-file-tag--read,
.figma-chat-file-tag--write,
.figma-chat-file-tag--edit {
  /* background: #f3f4f6; */
  color: #1f2937;
}
.figma-chat-file-path {
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.figma-chat-file-item-write {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.figma-chat-write-preview {
  margin: 0;
  padding: 6px 8px;
  background: #f8f8f8;
  border: 1px solid #e5e5e5;
  border-radius: 4px;
  font-family: 'JetBrains Mono', 'Cascadia Mono', monospace;
  font-size: 11px;
  line-height: 17px;
  color: #374151;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
  user-select: none;
}
.figma-chat-write-preview :deep(.ch) {
  color: #6b7280;
  font-style: italic;
}
.figma-chat-write-preview :deep(.kt) {
  color: #7c3aed;
}
.figma-chat-write-preview :deep(.na) {
  color: #1d4ed8;
}
.figma-chat-write-preview :deep(.s) {
  color: #059669;
}
.figma-chat-write-preview :deep(.k) {
  color: #7c3aed;
  font-weight: 600;
}
.figma-chat-write-preview :deep(.nb) {
  color: #d97706;
}
.figma-chat-write-preview :deep(.m) {
  color: #0891b2;
}
.figma-chat-write-preview :deep(.nc) {
  color: #c2410c;
}

.figma-chat-bubble-meta {
  margin-top: 4px;
  font-size: 12px;
  line-height: 20px;
  color: #a1a5b1;
  letter-spacing: -0.0125em;
}

.figma-chat-bubble--user + .figma-chat-bubble-meta {
  text-align: right;
}

.figma-chat-assistant {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  align-self: flex-start;
  max-width: 100%;
  align-items: flex-start;
}

.figma-chat-avatar {
  display: flex;
  margin-top: 5px;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  flex-shrink: 0;
  overflow: hidden;
}

.figma-chat-avatar-icon {
  width: 24px;
  height: 24px;
  display: block;
}

.figma-chat-assistant-content {
  flex: 1;
  min-width: 0;
}

/* ---- Empty state ---- */
.figma-chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px 16px;
  color: #999;
  margin: auto 0;
}

.figma-chat-empty-icon {
  margin-bottom: 12px;
}

.figma-chat-empty-title {
  font-size: 14px;
  font-weight: 500;
  color: #555;
  margin: 0 0 4px;
}

.figma-chat-empty-hint {
  font-size: 12px;
  line-height: 18px;
  color: #999;
  margin: 0;
  max-width: 240px;
}

/* ---- Status ---- */
.figma-chat-running-assistant {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  align-self: stretch;
  max-width: 100%;
}

.figma-chat-running-content {
  flex: 1;
  min-width: 0;
}

.figma-chat-status {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 24px;
  border-radius: 8px;
  font-size: 12px;
  color: #8c8c8c;
  font-weight: 500;
  align-self: flex-start;
}

.figma-chat-status-loader {
  color: #8c8c8c;
  flex-shrink: 0;
}

@keyframes figma-chat-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.figma-chat-spin {
  animation: figma-chat-spin 1s linear infinite;
}

@keyframes figma-chat-bell {
  0%,
  100% {
    transform: rotate(0deg);
  }
  20% {
    transform: rotate(14deg);
  }
  40% {
    transform: rotate(-12deg);
  }
  60% {
    transform: rotate(8deg);
  }
  80% {
    transform: rotate(-6deg);
  }
}

.figma-chat-running-timer {
  color: #8c8c8c;
  margin-left: 4px;
}

.figma-chat-thinking-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-left: 2px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: #999;
  cursor: pointer;
  transition: background-color 0.14s ease, color 0.14s ease;
}

.figma-chat-thinking-chevron {
  width: 14px;
  height: 14px;
}

.figma-chat-thinking-body {
  align-self: flex-start;
  width: 100%;
  padding: 8px 12px;
  background: #f5f5f5;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
}

.figma-chat-thinking-text {
  margin: 0;
  font-size: 11px;
  line-height: 1.6;
  color: #666;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-sans);
}

.figma-chat-stopped,
.figma-chat-completed,
.figma-chat-failed {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-left: 12px;
  margin-top: 4px;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--ta-chat-muted, #888);
  font-weight: 500;
  align-self: flex-start;
}

.figma-chat-stopped-icon,
.figma-chat-completed-icon,
.figma-chat-failed-icon {
  flex-shrink: 0;
}

.figma-chat-stopped-icon {
  color: #eb5e53;
}

.figma-chat-completed-icon {
  color: #18a978;
}

.figma-chat-failed-icon {
  color: #eb5e53;
}

/* ---- Task Panel (above input, during running) ---- */
.figma-chat-task-panel {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 10px;
  margin: 0 10px -8px 10px;
  background: var(--ta-chat-process-bg, rgba(0, 0, 0, 0.03));
  border-radius: 8px;
  border: 1px solid var(--ta-chat-border, rgba(0, 0, 0, 0.06));
  max-height: 100px;
  overflow-y: auto;
}

.figma-chat-task-summary {
  position: sticky;
  top: -6px;
  background: var(--ta-chat-process-bg, rgba(0, 0, 0, 0.03));
  font-size: 12px;
  color: var(--ta-chat-muted, #8b8ea0);
  padding-bottom: 4px;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.figma-chat-task-summary--collapsed {
  padding-bottom: 0;
}

.figma-chat-task-panel--collapsed {
  max-height: none;
  overflow-y: hidden;
}

.figma-chat-task-collapse-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  padding: 2px;
  cursor: pointer;
  color: var(--ta-chat-muted, #8b8ea0);
  border-radius: 4px;
  transition: all 0.2s ease;
}

.figma-chat-task-collapse-btn:hover {
  background: var(--ta-chat-hover-bg, rgba(0, 0, 0, 0.05));
  color: var(--ta-chat-text, #333);
}

.figma-chat-task-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.figma-chat-task-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  line-height: 18px;
}

.figma-chat-task-icon {
  flex-shrink: 0;
  margin-right: 2px;
}

.figma-chat-task-icon--running {
  color: #555;
  animation: figma-chat-pulse 1.4s ease-in-out infinite;
}

.figma-chat-task-icon--completed {
  color: #219653;
}

.figma-chat-task-icon--error {
  color: #d1423a;
}

.figma-chat-task-icon--pending {
  color: #a1a5b1;
}

.figma-chat-task-label {
  color: #333;
  flex-shrink: 0;
}

.figma-chat-task-detail {
  color: #888;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---- 技能/Agent 候选共享头部 ---- */
.figma-chat-choice-header {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  justify-content: space-between;
}

.figma-chat-choice-question {
  font-size: 13px;
  font-weight: 700;
  color: #1a1a1a;
  padding: 6px 2px 4px;
  line-height: 1.45;
  flex: 1;
}

.figma-chat-choice-close {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: #999;
  cursor: pointer;
  border-radius: 4px;
}

.figma-chat-choice-close:hover {
  background: #f5f5f5;
  color: #555;
}

.figma-chat-question-dock {
  flex-shrink: 0;
  min-height: 0;
  max-height: min(62vh, calc(100vh - 260px));
  margin: 0 10px 10px;
  padding: 6px;
  border: 1px solid var(--ta-chat-border, #eaeaea);
  border-radius: 8px;
  background: var(--ta-chat-surface, #ffffff);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow: hidden;
}

.figma-chat-question-card,
.figma-chat-permission-card {
  display: flex;
  flex-direction: column;
  min-height: 0;
  gap: 10px;
  padding: 10px 8px;
  border: 1px solid var(--ta-chat-border, #eaeaea);
  border-radius: 8px;
  background: var(--ta-chat-surface, #ffffff);
}

.figma-chat-question-card {
  max-height: 100%;
  flex: 1 1 auto;
}

.figma-chat-permission-card {
  border-color: rgba(148, 96, 21, 0.35);
  background: rgba(148, 96, 21, 0.06);
}

.figma-chat-question-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.figma-chat-question-scroll {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 4px;
}

.figma-chat-question-scroll::-webkit-scrollbar {
  width: 6px;
}

.figma-chat-question-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: var(--ta-chat-border-strong, #cfcfcf);
}

.figma-chat-question-page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.figma-chat-question-page-meta {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
}

.figma-chat-question-progress {
  font-size: 12px;
  line-height: 16px;
  font-weight: 400;
  color: var(--ta-chat-muted, #7a7a7a);
}

.figma-chat-question-type {
  flex: 0 0 auto;
  border: 1px solid var(--ta-chat-border, #eaeaea);
  border-radius: 999px;
  padding: 1px 7px;
  background: var(--ta-chat-process-bg, #f5f5f5);
  color: var(--ta-chat-subtle, #555555);
  font-size: 12px;
  font-weight: 500;
  line-height: 16px;
}

.figma-chat-question-header {
  overflow: hidden;
  color: var(--ta-chat-muted, #7a7a7a);
  font-size: 12px;
  font-weight: 500;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.figma-chat-question-title {
  font-size: 14px;
  line-height: 18px;
  font-weight: 600;
  color: var(--ta-chat-text, #333333);
}

.figma-chat-question-description {
  white-space: pre-wrap;
  font-size: 12px;
  line-height: 16px;
  color: var(--ta-chat-muted, #7a7a7a);
}

.figma-chat-question-hint {
  font-size: 12px;
  line-height: 16px;
  color: var(--ta-chat-muted, #7a7a7a);
}

.figma-chat-question-options {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.figma-chat-question-option,
.figma-chat-question-custom-card {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  min-height: 44px;
  border-radius: 6px;
  padding: 6px 10px;
  border: 1px solid var(--ta-chat-border, #eaeaea);
  background: var(--ta-chat-surface, #ffffff);
  color: var(--ta-chat-text, #333333);
  text-align: left;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.12s ease, border-color 0.12s ease, color 0.12s ease;
}

.figma-chat-question-submit,
.figma-chat-question-reject,
.figma-chat-question-prev,
.figma-chat-question-next {
  min-height: 28px;
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.12s ease, border-color 0.12s ease, color 0.12s ease;
}

.figma-chat-question-option:hover,
.figma-chat-question-option.is-selected,
.figma-chat-question-custom-card:focus-within,
.figma-chat-question-custom-card.is-selected {
  border-color: var(--ta-accent, #333333);
  background: var(--ta-chat-hover, #eeeeee);
  color: var(--ta-chat-text, #333333);
}

.figma-chat-question-option-mark {
  position: relative;
  flex: 0 0 auto;
  width: 14px;
  height: 14px;
  margin-top: 3px;
  border: 1px solid var(--ta-chat-border, #eaeaea);
  border-radius: 999px;
  background: var(--ta-chat-surface, #ffffff);
}

.figma-chat-question-option.is-selected .figma-chat-question-option-mark,
.figma-chat-question-custom-card.is-selected .figma-chat-question-option-mark {
  border-color: var(--ta-accent, #333333);
}

.figma-chat-question-option.is-selected .figma-chat-question-option-mark::after,
.figma-chat-question-custom-card.is-selected .figma-chat-question-option-mark::after {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--ta-accent, #333333);
  content: "";
}

/* 多选题勾选框样式 */
.is-multiple .figma-chat-question-option-mark {
  border-radius: 3px;
}

.is-multiple .figma-chat-question-option.is-selected .figma-chat-question-option-mark,
.is-multiple .figma-chat-question-custom-card.is-selected .figma-chat-question-option-mark {
  background: var(--ta-accent, #333333);
  border-color: var(--ta-accent, #333333);
}

.is-multiple .figma-chat-question-option.is-selected .figma-chat-question-option-mark::after,
.is-multiple .figma-chat-question-custom-card.is-selected .figma-chat-question-option-mark::after {
  position: absolute;
  top: 1px;
  left: 4px;
  width: 4px;
  height: 7px;
  border: solid #ffffff;
  border-width: 0 2px 2px 0;
  border-radius: 0;
  transform: rotate(45deg);
  background: transparent;
  content: "";
}

.figma-chat-question-option-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.figma-chat-question-option-label {
  color: var(--ta-chat-text, #333333);
  font-size: 14px;
  font-weight: 500;
  line-height: 18px;
}

.figma-chat-question-option-description {
  color: var(--ta-chat-muted, #7a7a7a);
  font-size: 12px;
  font-weight: 400;
  line-height: 16px;
}

.figma-chat-question-custom-input {
  min-height: 30px;
  width: 100%;
  border: 1px solid var(--ta-chat-border, #eaeaea);
  border-radius: 6px;
  padding: 4px 8px;
  background: var(--ta-chat-surface, #ffffff);
  color: var(--ta-chat-text, #333333);
  font-size: 14px;
  outline: none;
}

.figma-chat-question-custom-card .figma-chat-question-custom-input {
  min-height: 24px;
  border: none;
  padding: 0;
  background: transparent;
}

.figma-chat-question-custom-input:focus {
  border-color: var(--ta-accent, #333333);
}

.figma-chat-question-custom-card .figma-chat-question-custom-input:focus {
  border-color: transparent;
}

.figma-chat-question-actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
}

.figma-chat-question-action-spacer {
  flex: 1;
}

.figma-chat-question-submit {
  border: 1px solid var(--ta-accent, #333333);
  background: var(--ta-accent, #333333);
  color: var(--primary-foreground, #ffffff);
}

.figma-chat-question-submit:not(:disabled):hover {
  background: var(--ta-accent-2, #555555);
  border-color: var(--ta-accent-2, #555555);
}

.figma-chat-question-submit:disabled {
  border-color: var(--ta-chat-border, #eaeaea);
  background: var(--ta-chat-process-bg, #f5f5f5);
  color: var(--ta-chat-muted, #7a7a7a);
  cursor: not-allowed;
}

.figma-chat-question-reject {
  border: 1px solid var(--ta-chat-border, #eaeaea);
  background: var(--ta-chat-surface, #ffffff);
  color: var(--ta-chat-subtle, #555555);
}

.figma-chat-question-prev,
.figma-chat-question-next {
  border: 1px solid var(--ta-chat-border, #eaeaea);
  background: var(--ta-chat-surface, #ffffff);
  color: var(--ta-chat-text, #333333);
}

.figma-chat-question-reject:hover,
.figma-chat-question-prev:hover,
.figma-chat-question-next:hover {
  background: var(--ta-chat-hover, #eeeeee);
  color: var(--ta-chat-text, #333333);
}

/* ---- 技能面板 ---- */
.figma-chat-skill-panel {
  padding: 12px 16px;
  border: 1px solid var(--ta-chat-border, #e0e0e0);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  margin: 0 10px 10px;
  flex-shrink: 0;
}

.figma-chat-skill-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 200px;
  overflow-y: auto;
}

.figma-chat-skill-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  cursor: pointer;
  border-radius: 6px;
  background: #ffffff;
  transition: background 0.12s ease;
}

.figma-chat-skill-row:hover {
  background: #f5f5f5;
}

.figma-chat-skill-icon {
  color: #3366ff;
  flex-shrink: 0;
}

.figma-chat-skill-info {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.figma-chat-skill-name {
  font-size: 13px;
  font-weight: 600;
  color: #1a1a1a;
  flex-shrink: 0;
}

.figma-chat-skill-desc {
  font-size: 12px;
  color: #8c8c8c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  margin-left: 8px;
}

.figma-chat-skill-empty {
  padding: 12px 4px;
  font-size: 12px;
  color: #8c8c8c;
  text-align: center;
}

/* ---- Agent @ 候选面板 ---- */
.figma-chat-agent-panel {
  padding: 12px 16px;
  border: 1px solid var(--ta-chat-border, #e0e0e0);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  margin: 0 10px 10px;
  flex-shrink: 0;
}

.figma-chat-agent-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 200px;
  overflow-y: auto;
}

.figma-chat-suggestion-section {
  padding: 6px 12px 2px;
  color: #8c8c8c;
  font-size: 11px;
  font-weight: 600;
}

.figma-chat-agent-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  cursor: pointer;
  border-radius: 6px;
  background: #ffffff;
  transition: background 0.12s ease;
}

.figma-chat-agent-row:hover {
  background: #f5f5f5;
}

.figma-chat-agent-icon {
  color: #3366ff;
  flex-shrink: 0;
}

.figma-chat-file-icon {
  color: #64748b;
  flex-shrink: 0;
}

.figma-chat-requirement-icon {
  color: #7c3aed;
  flex-shrink: 0;
}

.figma-chat-agent-info {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

/* # 需求候选不展开文件，但完整展示子条目与需求项，避免长名称撑出横向滚动。 */
.figma-chat-reference-info {
  flex-direction: column;
  align-items: stretch;
  gap: 3px;
}

.figma-chat-requirement-row {
  align-items: flex-start;
}

.figma-chat-requirement-row .figma-chat-requirement-icon {
  margin-top: 2px;
}

.figma-chat-reference-name-full,
.figma-chat-reference-info .figma-chat-agent-desc {
  min-width: 0;
  overflow: visible;
  text-overflow: clip;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.figma-chat-reference-info .figma-chat-agent-desc {
  align-self: stretch;
  margin-left: 0;
  line-height: 1.4;
}

/* @ 文件候选同时完整展示文件名和相对路径，窄面板下自然换行而不省略。 */
.figma-chat-file-info {
  flex-direction: column;
  align-items: stretch;
  gap: 3px;
}

.figma-chat-file-row {
  align-items: flex-start;
}

.figma-chat-file-row .figma-chat-file-icon {
  margin-top: 2px;
}

.figma-chat-file-name-full {
  min-width: 0;
  overflow: visible;
  text-overflow: clip;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.figma-chat-file-info .figma-chat-agent-desc {
  align-self: stretch;
  margin-left: 0;
  overflow: visible;
  text-overflow: clip;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
  font-size: 11px;
  line-height: 1.4;
}

.figma-chat-agent-name {
  font-size: 13px;
  font-weight: 600;
  color: #1a1a1a;
  flex-shrink: 0;
}

.figma-chat-agent-desc {
  font-size: 12px;
  color: #8c8c8c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  margin-left: 8px;
}

.figma-chat-agent-empty {
  padding: 12px 4px;
  font-size: 12px;
  color: #8c8c8c;
  text-align: center;
}

.figma-chat-skill-msg {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #3366ff;
  font-weight: 500;
}

.figma-chat-skill-msg-icon {
  flex-shrink: 0;
}

@keyframes figma-chat-pulse {
  0%,
  100% {
    opacity: 0.4;
  }
  50% {
    opacity: 1;
  }
}

/* ---- Task Usage (above input box) ---- */
.figma-chat-file-changes-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 18px;
  font-size: 12px;
  cursor: pointer;
  border-top: 1px solid var(--ta-chat-border, rgba(0, 0, 0, 0.06));
}
.figma-chat-file-changes-bar:hover {
  background: var(--ta-chat-process-bg, rgba(0, 0, 0, 0.02));
}
.figma-chat-file-changes-label {
  color: var(--ta-chat-text);
  font-weight: 500;
}
.figma-chat-file-changes-hint {
  color: var(--ta-chat-muted, #8b8ea0);
  margin-left: auto;
  font-size: 11px;
}

.figma-chat-usage {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 6px;
  padding: 0;
  background: transparent;
  font-family: 'JetBrains Mono', 'PingFang SC', monospace;
  font-size: 11px;
  line-height: 1;
  color: #a40dbc;
  letter-spacing: -0.0125em;
}

.figma-chat-usage-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  display: block;
}

.figma-chat-usage-dot {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  display: block;
  border-radius: 999px;
  background: #a40dbc;
}

.figma-chat-usage-label {
  color: #a40dbc;
  font-weight: 600;
  font-family: var(--font-sans);
}

.figma-chat-usage-value {
  color: #6b6b73;
  font-weight: 400;
}

.figma-chat-usage-stats {
  margin-left: 4px;
  display: inline-flex;
  gap: 4px;
  font-weight: 500;
}

.figma-chat-status-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  margin-right: 4px;
  flex-shrink: 0;
}

.figma-chat-status-stopped,
.figma-chat-status-failed {
  color: #cf222e;
}

.figma-chat-status-completed {
  color: #1a7f37;
}

.figma-chat-status-icon-inline {
  width: 13px;
  height: 13px;
  flex-shrink: 0;
}

.figma-chat-status-divider {
  color: var(--ta-chat-muted, #8b8ea0);
  opacity: 0.5;
  margin: 0 2px;
  flex-shrink: 0;
}

.figma-chat-add {
  color: #18a978;
  font-family: 'JetBrains Mono', monospace;
}

.figma-chat-del {
  color: #eb5e53;
  font-family: 'JetBrains Mono', monospace;
}

.figma-chat-process-status {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  padding: 12px 16px;
  border: 1px solid #d7d7d7;
  border-radius: 12px;
  background: #fafafa;
  color: #333;
  cursor: pointer;
  user-select: none;
  z-index: 50;
  min-width: 0;
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.figma-chat-process-status:hover {
  opacity: 0.92;
}
.figma-chat-process-status:active {
  transform: scale(0.99);
}

/* 收起态：8px 半透明柔光点；独立类避免时间线圆点覆盖其视觉契约。 */
.figma-chat-process-status-dot {
  flex-shrink: 0;
  align-self: flex-end;
  width: 8px;
  height: 8px;
  border-radius: 9999px;
  border: none;
  margin: 0;
  padding: 0;
  cursor: grab;
  position: fixed;
  left: 0;
  top: 0;
  /* 通过 CSS 变量承载位置，避免 :hover 的 transform: scale 覆盖 translate */
  transform: translate3d(
    var(--figma-process-dot-x, 0px),
    var(--figma-process-dot-y, 0px),
    0
  );
  outline: none;
  touch-action: none;
  z-index: 50;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.figma-chat-process-status-dot:hover {
  transform: translate3d(
      var(--figma-process-dot-x, 0px),
      var(--figma-process-dot-y, 0px),
      0
    )
    scale(1.15);
}
.figma-chat-process-status-dot:active,
.figma-chat-process-status-dot.is-dragging {
  cursor: grabbing;
  transition: none;
}
.figma-chat-process-status-dot::after {
  content: '';
  position: absolute;
  inset: -7px;
  border-radius: 9999px;
  background: inherit;
  filter: blur(8px);
  opacity: 0.42;
  z-index: -1;
  pointer-events: none;
}
.figma-chat-process-status-dot.is-ready {
  background: rgba(24, 169, 120, 0.42);
  box-shadow: 0 0 4px rgba(24, 169, 120, 0.26);
}
.figma-chat-process-status-dot.is-blocking {
  background: rgba(235, 94, 83, 0.42);
  box-shadow: 0 0 4px rgba(235, 94, 83, 0.26);
}
.figma-chat-process-status-dot:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 4px;
}

.figma-chat-process-status.is-ready {
  border-color: rgba(24, 169, 120, 0.35);
  background: rgba(24, 169, 120, 0.08);
}

.figma-chat-process-status.is-blocking {
  border-color: rgba(235, 94, 83, 0.35);
  background: rgba(235, 94, 83, 0.07);
}

.figma-chat-process-copy {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.figma-chat-process-title {
  font-size: 13px;
  line-height: 18px;
  font-weight: 600;
}

.figma-chat-process-message {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  line-height: 16px;
  color: #666;
}

.figma-chat-process-init {
  flex-shrink: 0;
  height: 28px;
  padding: 0 12px;
  border: 1px solid #b5b5b5;
  border-radius: 8px;
  background: #fff;
  color: #333;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.figma-chat-process-actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
}

.figma-chat-process-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid #b5b5b5;
  border-radius: 8px;
  background: #fff;
  color: #526173;
  cursor: pointer;
}

.figma-chat-process-help:hover,
.figma-chat-process-help:focus-visible {
  background: #f0f3f8;
  color: #27384b;
  outline: none;
}

.figma-chat-process-init:not(:disabled):hover {
  background: #f0f3f8;
}

.figma-chat-process-init:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

/* ---- Composer ---- */
.figma-chat-composer {
  flex-shrink: 0;
  padding: 8px 10px 10px;
  background: transparent;
}

.figma-chat-night-picker {
  display: grid;
  gap: 10px;
  margin-bottom: 8px;
  padding: 11px;
  border: 1px solid #d8dbe7;
  border-radius: 12px;
  background: #fbfbfd;
  box-shadow: 0 10px 24px rgba(39, 50, 94, 0.11);
}
.figma-chat-night-picker-head,
.figma-chat-night-picker-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.figma-chat-night-picker-head > div {
  display: grid;
  gap: 2px;
}
.figma-chat-night-picker-head strong {
  color: #27272a;
  font-size: 13px;
  line-height: 18px;
}
.figma-chat-night-picker-head span,
.figma-chat-night-picker-foot > span {
  color: #71717a;
  font-size: 11px;
  line-height: 16px;
}
.figma-chat-night-picker-head > button {
  display: inline-grid;
  width: 25px;
  height: 25px;
  place-items: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #71717a;
  cursor: pointer;
}
.figma-chat-night-picker-head > button:hover {
  background: #eceef4;
  color: #27272a;
}
.figma-chat-night-track {
  display: grid;
  grid-template-columns: repeat(5, minmax(50px, 1fr));
  gap: 5px;
  max-height: 154px;
  overflow-y: auto;
  padding: 1px;
}
.figma-chat-night-slot {
  display: grid;
  min-height: 43px;
  place-content: center;
  gap: 1px;
  border: 1px solid #e0e1e7;
  border-radius: 7px;
  background: #fff;
  color: #3f3f46;
  font: inherit;
  cursor: pointer;
}
.figma-chat-night-slot span {
  font-size: 11px;
  font-weight: 650;
}
.figma-chat-night-slot small {
  color: #8a8a93;
  font-size: 9px;
}
.figma-chat-night-slot.is-recommended {
  border-color: #c4a66b;
  background: #fffdf7;
}
.figma-chat-night-slot.is-recommended small {
  color: #936621;
}
.figma-chat-night-slot.is-selected {
  border-color: #27325e;
  background: #27325e;
  color: #fff;
  box-shadow: 0 0 0 1px #27325e;
}
.figma-chat-night-slot.is-selected small {
  color: #e3e7f8;
}
.figma-chat-night-slot:disabled {
  border-style: dashed;
  background: #f3f3f4;
  color: #aaaab0;
  cursor: not-allowed;
  opacity: 0.65;
}
.figma-chat-night-picker-loading,
.figma-chat-night-picker-empty {
  display: flex;
  min-height: 62px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: #71717a;
  font-size: 12px;
}
.figma-chat-night-picker-foot > button {
  flex: 0 0 auto;
  min-height: 29px;
  padding: 0 11px;
  border: 1px solid #27325e;
  border-radius: 7px;
  background: #27325e;
  color: #fff;
  font: inherit;
  font-size: 11px;
  font-weight: 650;
  cursor: pointer;
}
.figma-chat-night-picker-foot > button:disabled {
  border-color: #c7c9d1;
  background: #c7c9d1;
  cursor: not-allowed;
}

.figma-chat-subagent-notice {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 46px;
  padding: 10px 12px;
  border-top: 1px solid var(--ta-chat-border);
  background: var(--ta-chat-bg);
  color: var(--ta-chat-muted);
  font-size: 13px;
  line-height: 20px;
}

.figma-chat-subagent-return {
  border: 0;
  background: transparent;
  color: var(--ta-accent);
  font: inherit;
  font-weight: 600;
  padding: 0;
  cursor: pointer;
}

.figma-chat-subagent-return:hover {
  color: var(--ta-accent-strong, var(--ta-accent));
  text-decoration: underline;
}

/* 统一输入卡片：圆角边框容器，textarea + 底部工具行整合在一起 */
.figma-chat-input-card {
  position: relative;
  display: flex;
  flex-direction: column;
  border: 1px solid #d4d4d4;
  border-radius: 16px;
  background: #fff;
  box-shadow: none;
  overflow: visible;
  transition: border-color 0.15s ease;
}

.figma-chat-composer-resize-handle {
  position: absolute;
  z-index: 2;
  top: -5px;
  left: 0;
  right: 0;
  height: 10px;
  cursor: ns-resize;
  touch-action: none;
  display: flex;
  justify-content: center;
}

.figma-chat-composer-resize-handle::before {
  content: "";
  position: absolute;
  top: 3px;
  width: 36px;
  height: 4px;
  border-radius: 2px;
  background: rgba(0, 0, 0, 0.1);
  transition: background-color 0.15s ease, width 0.15s ease;
}

.figma-chat-composer-resize-handle:hover::before,
.figma-chat-input-card.is-resizing .figma-chat-composer-resize-handle::before {
  background: rgba(0, 0, 0, 0.3);
  width: 48px;
}

.figma-chat-input-card.is-resizing {
  border-color: #3366ff;
  box-shadow: none;
}

.figma-chat-input-card:focus-within {
  border-color: #3366ff;
  box-shadow: none;
}

/* 进程不可用时整张输入卡进入统一灰显态；无 Session 时仍允许直接输入首条消息。 */
.figma-chat-input-card.is-disabled {
  border-color: #dedfe2;
  background: #f3f4f6;
  color: #9a9da3;
  cursor: not-allowed;
}

.figma-chat-input-card.is-disabled:focus-within {
  border-color: #dedfe2;
}

.figma-chat-input-card.is-disabled .figma-chat-composer-resize-handle {
  pointer-events: none;
}

.figma-chat-input-card.is-disabled .figma-chat-composer-resize-handle::before {
  background: rgba(107, 114, 128, 0.12);
}

.figma-chat-input-card.is-disabled .figma-chat-card-actions {
  border-top-color: #e4e5e8;
  background: #f3f4f6;
}

.figma-chat-input-card.is-disabled .figma-chat-textarea::placeholder {
  color: #989ca3;
  opacity: 1;
}
.figma-chat-input-card.is-night-locked {
  border-color: #d6d9e6;
  background: #f7f7fa;
}
.figma-chat-input-card.is-night-locked .figma-chat-textarea::placeholder {
  color: #74788c;
  opacity: 1;
}
.figma-chat-night-btn[aria-expanded='true'] {
  border-color: #c6cadc;
  background: #eef0f8;
  color: #27325e;
}

.figma-chat-textarea {
  width: 100%;
  min-height: 40px;
  max-height: 260px;
  padding: 8px 12px 4px;
  font-family: var(--font-sans);
  font-size: 14px;
  line-height: 20px;
  color: #111;
  background: transparent;
  border: none;
  resize: none;
  outline: none;
  box-sizing: border-box;
  overflow-y: auto;
}

.figma-chat-textarea:disabled {
  color: #999;
  cursor: not-allowed;
}

.figma-chat-textarea::placeholder {
  color: rgba(51, 51, 51, 0.38);
}

/* 底部工具行（附件、模型、新建对话、发送/停止） */
.figma-chat-card-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px 8px;
  border-top: 1px solid #f0f0f0;
  border-bottom-left-radius: 15px;
  border-bottom-right-radius: 15px;
}

.figma-chat-card-spacer {
  flex: 1;
}

/* 工具行小按钮（附件、模型、新建对话） */
.figma-chat-card-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: #f4f4f5;
  color: #555;
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.12s ease, border-color 0.12s ease,
    color 0.12s ease;
}

.figma-chat-card-btn:hover:not(:disabled) {
  background: #e2e2e5;
  color: #111111;
}

.figma-chat-card-btn:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.figma-chat-attachment-btn {
  width: 28px;
  padding: 0;
  justify-content: center;
}

.figma-chat-agent-btn {
  max-width: 140px;
  min-width: 0;
}

.figma-chat-agent-label {
  min-width: 0;
  max-width: 88px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.figma-chat-model-btn {
  max-width: 150px;
  min-width: 0;
}

.figma-chat-model-label {
  min-width: 0;
  max-width: 108px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.figma-chat-new-btn {
  color: #555;
}

.figma-chat-btn-icon {
  width: 12px;
  height: 12px;
  flex-shrink: 0;
}

/* 卡片内发送 / 停止按钮 */
.figma-chat-send-card,
.figma-chat-stop-card {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  transition: background-color 0.12s ease, opacity 0.12s ease;
  flex-shrink: 0;
}

.figma-chat-send-card {
  background: #18181b;
  color: #fff;
  opacity: 0.35;
}

.figma-chat-send-card:not(:disabled) {
  opacity: 1;
}

.figma-chat-send-card:not(:disabled):hover {
  background: #000;
}

.figma-chat-stop-card {
  background: #ffe8e8;
  color: #d1423a;
  border-radius: 50%;
}

.figma-chat-stop-card:hover {
  background: #ffd0d0;
}

.figma-chat-stop-card:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.figma-chat-send-icon,
.figma-chat-stop-icon {
  width: 14px;
  height: 14px;
}

/* ---- 常驻底部 footer（与左侧面板、中心面板底栏等高对齐） ---- */
.figma-chat-footer {
  flex-shrink: 0;
  height: 30px;
  background: #fff;
  border-top: 1px solid var(--ta-border);
  display: flex;
  align-items: center;
  padding: 0 16px;
}

/* ---- Attachment Dialog ---- */
.figma-chat-attachment-mask {
  position: absolute;
  inset: 0;
  z-index: 35;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 84px 12px 16px;
  background: rgba(15, 15, 18, 0.28);
  animation: figma-chat-drawer-fade 0.16s ease-out;
}

.figma-chat-attachment-dialog {
  width: min(100%, 360px);
  border: 1px solid #e4e4e7;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 18px 40px rgba(15, 15, 18, 0.18);
  font-family: var(--font-sans);
  animation: figma-chat-attachment-pop 0.18s cubic-bezier(0.2, 0.7, 0.2, 1);
}

.figma-chat-attachment-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 14px 10px;
}

.figma-chat-attachment-title {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  font-weight: 600;
  color: #18181b;
}

.figma-chat-attachment-subtitle {
  margin: 2px 0 0;
  font-size: 11px;
  line-height: 16px;
  color: #777;
}

.figma-chat-attachment-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  border: 0.8px solid #dfdfdf;
  border-radius: 6px;
  background: #fff;
  color: #777;
  cursor: pointer;
  transition: background-color 0.12s ease, border-color 0.12s ease;
}

.figma-chat-attachment-close:hover {
  background: #f0f0f0;
  border-color: #cfcfcf;
  color: #333;
}

.figma-chat-attachment-drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: calc(100% - 28px);
  margin: 0 14px;
  padding: 20px 14px;
  border: 1px dashed #c8d2ee;
  border-radius: 8px;
  background: #f8faff;
  color: #333;
  cursor: default;
  font-family: var(--font-sans);
}

.figma-chat-attachment-drop-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  margin-bottom: 10px;
  border-radius: 10px;
  background: #eaf0ff;
  color: #3366ff;
}

.figma-chat-attachment-drop-title {
  font-size: 13px;
  line-height: 18px;
  font-weight: 600;
  color: #18181b;
}

.figma-chat-attachment-drop-hint {
  margin-top: 4px;
  max-width: 260px;
  font-size: 11px;
  line-height: 17px;
  color: #777;
  text-align: center;
}

.figma-chat-attachment-disabled {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 12px 14px 14px;
  padding: 8px 10px;
  border-radius: 7px;
  background: #fafafa;
  color: #666;
  font-size: 11px;
  line-height: 16px;
}

.figma-chat-attachment-disabled-dot {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  border-radius: 999px;
  background: #a1a5b1;
}

@keyframes figma-chat-attachment-pop {
  from {
    transform: translateY(-8px);
    opacity: 0.7;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

/* ---- Changes Drawer ----
   抽屉打开时覆盖在右侧聊天面板之上。遮罩半透明，点击空白处关闭；
   抽屉本体占满面板宽度，左侧是文件菜单，右侧是 git-merge 风格的 diff。*/
.figma-chat-drawer-mask {
  position: absolute;
  inset: 0;
  z-index: 40;
  background: rgba(15, 15, 18, 0.32);
  display: flex;
  justify-content: stretch;
  align-items: stretch;
  animation: figma-chat-drawer-fade 0.16s ease-out;
}

.figma-chat-drawer {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: #fff;
  box-shadow: -8px 0 24px rgba(15, 15, 18, 0.16);
  font-family: var(--font-sans);
  animation: figma-chat-drawer-slide 0.2s cubic-bezier(0.2, 0.7, 0.2, 1);
}

@keyframes figma-chat-drawer-fade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes figma-chat-drawer-slide {
  from {
    transform: translateX(20px);
    opacity: 0.6;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

.figma-chat-drawer-header {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  flex-shrink: 0;
  padding: 0 8px 0 14px;
  border-bottom: 1px solid #ddd;
  background: #fff;
}

.figma-chat-drawer-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.figma-chat-drawer-title-text {
  font-size: 14px;
  font-weight: 600;
  color: #18181b;
  line-height: 20px;
  letter-spacing: 0.0143em;
}

.figma-chat-drawer-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 18px;
  padding: 0 6px;
  border-radius: 9px;
  background: #f0f0f0;
  color: #555;
  font-size: 11px;
  font-weight: 500;
  line-height: 18px;
}

.figma-chat-drawer-summary {
  display: inline-flex;
  gap: 8px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  font-weight: 500;
  line-height: 18px;
}

.figma-chat-drawer-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0.8px solid #dfdfdf;
  border-radius: 6px;
  background: #fff;
  color: #777;
  cursor: pointer;
  transition: background-color 0.12s ease, border-color 0.12s ease;
}

.figma-chat-drawer-close:hover {
  background: #f0f0f0;
  border-color: #cfcfcf;
  color: #333;
}

/* 上下文行切换：默认 "仅变更" 状态（is-active）时高亮，让用户一眼分辨当前是哪种模式。
   按钮放在摘要数字和关闭按钮之间，避免和右上角关闭按钮位置冲突。*/
.figma-chat-drawer-toggle {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 8px;
  border: 0.8px solid #dfdfdf;
  border-radius: 6px;
  background: #fff;
  color: #555;
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
  transition: background-color 0.12s ease, border-color 0.12s ease,
    color 0.12s ease;
}

.figma-chat-drawer-toggle:hover {
  background: #f0f0f0;
  border-color: #cfcfcf;
  color: #333;
}

.figma-chat-drawer-toggle.is-active {
  background: #eaf0ff;
  border-color: #b9c8ff;
  color: #1d3fb0;
}

.figma-chat-drawer-toggle.is-active:hover {
  background: #dde7ff;
}

.figma-chat-drawer-toggle-text {
  font-family: var(--font-sans);
}

.figma-chat-drawer-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(140px, 38%) minmax(0, 1fr);
  background: #fff;
}

.figma-chat-drawer-files {
  border-right: 1px solid #ededed;
  background: #fafafa;
  overflow-y: auto;
  min-height: 0;
}

.figma-chat-drawer-files-list {
  list-style: none;
  margin: 0;
  padding: 6px 6px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.figma-chat-drawer-file-item {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 6px 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #333;
  text-align: left;
  cursor: pointer;
  font-family: var(--font-sans);
  font-size: 12px;
  line-height: 18px;
  transition: background-color 0.1s ease, border-color 0.1s ease;
}

.figma-chat-drawer-file-item:hover {
  background: #efefef;
}

.figma-chat-drawer-file-item.is-active {
  background: #eaf0ff;
  border-color: #b9c8ff;
  color: #1d3fb0;
}

.figma-chat-drawer-file-badge {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 18px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 500;
  line-height: 18px;
  background: #e7eee9;
  color: #3f7a5a;
}

.figma-chat-drawer-file-badge.is-del {
  background: #f6dfdc;
  color: #9e3b34;
}

.figma-chat-drawer-file-badge.is-mod {
  background: #f3ecdc;
  color: #946015;
}

.figma-chat-drawer-file-path {
  min-width: 0;
  flex: 1;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  line-height: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.figma-chat-drawer-file-stats {
  flex-shrink: 0;
  display: inline-flex;
  gap: 4px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  font-weight: 500;
  line-height: 16px;
}

.figma-chat-drawer-diff {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: #fff;
}

.figma-chat-drawer-diff-header {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  padding: 6px 12px;
  border-bottom: 1px solid #ededed;
  background: #fafafa;
  font-size: 12px;
}

.figma-chat-drawer-diff-path {
  min-width: 0;
  flex: 1;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.figma-chat-drawer-diff-stats {
  display: inline-flex;
  gap: 6px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  font-weight: 500;
}

.figma-chat-drawer-diff-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  font-family: 'JetBrains Mono', 'Cascadia Mono', monospace;
  font-size: 12px;
  line-height: 20px;
  background: #fff;
}

.figma-chat-drawer-diff-line {
  display: grid;
  grid-template-columns: 44px 44px 18px minmax(0, 1fr);
  align-items: start;
  white-space: pre;
  color: #333;
}

.figma-chat-drawer-diff-line.is-add {
  background: rgba(63, 122, 90, 0.12);
}

.figma-chat-drawer-diff-line.is-add .figma-chat-drawer-diff-sign,
.figma-chat-drawer-diff-line.is-add .figma-chat-drawer-diff-text {
  color: #2c6b4b;
}

.figma-chat-drawer-diff-line.is-del {
  background: rgba(158, 59, 52, 0.12);
}

.figma-chat-drawer-diff-line.is-del .figma-chat-drawer-diff-sign,
.figma-chat-drawer-diff-line.is-del .figma-chat-drawer-diff-text {
  color: #8a2f29;
}

.figma-chat-drawer-diff-line.is-meta {
  background: #f0f3f8;
  color: #6a6a6a;
  font-style: normal;
}

.figma-chat-drawer-diff-line.is-meta .figma-chat-drawer-diff-num,
.figma-chat-drawer-diff-line.is-meta .figma-chat-drawer-diff-sign {
  color: transparent;
}

.figma-chat-drawer-diff-num {
  text-align: right;
  padding: 0 8px 0 0;
  color: #b5b5b5;
  user-select: none;
  border-right: 1px solid #ededed;
  background: rgba(245, 245, 245, 0.6);
}

.figma-chat-drawer-diff-sign {
  text-align: center;
  color: #b5b5b5;
  user-select: none;
}

.figma-chat-drawer-diff-text {
  padding-right: 12px;
  white-space: pre;
  overflow: hidden;
  text-overflow: ellipsis;
}

.figma-chat-drawer-diff-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #888;
  font-size: 12px;
  text-align: center;
  padding: 24px 16px;
}

.figma-chat-drawer-diff-empty-hint {
  font-size: 11px;
  color: #aaa;
  max-width: 240px;
  line-height: 18px;
}

/* markdown 内容去除 github-markdown-css 自带的背景色，适配聊天气泡 */
.figma-chat-bubble-content :deep(.markdown-body) {
  background: transparent;
  color: inherit;
  font-size: inherit;
}

.figma-chat-bubble-content :deep(.markdown-body pre) {
  background: rgba(0, 0, 0, 0.04);
}

.figma-chat-bubble-content :deep(.markdown-body code) {
  background: rgba(0, 0, 0, 0.04);
}

.figma-chat-bubble-content :deep(.markdown-body table tr:nth-child(2n)) {
  background: rgba(0, 0, 0, 0.02);
}

.figma-chat-bubble-content :deep(.markdown-body blockquote) {
  background: transparent;
}

/* Agent 下拉选择框 */
.figma-chat-agent-select-wrapper {
  position: relative;
  display: inline-block;
}

.figma-chat-agent-dropdown {
  position: absolute;
  bottom: calc(100% + 12px);
  left: 0;
  width: 260px;
  max-height: 340px;
  background: var(--ta-panel, #ffffff);
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.15), 0 1px 4px rgba(15, 23, 42, 0.05);
  z-index: 1000;
  display: flex;
  flex-direction: column;
  overflow: visible;
}

.figma-chat-agent-dropdown::after {
  content: '';
  position: absolute;
  bottom: -6px;
  left: 36px;
  transform: rotate(45deg);
  width: 12px;
  height: 12px;
  background: var(--ta-panel, #ffffff);
  border-right: 1px solid var(--ta-border, #e4e4e7);
  border-bottom: 1px solid var(--ta-border, #e4e4e7);
  z-index: -1;
}

.figma-chat-agent-dropdown-search {
  padding: 10px 12px 6px;
  border-bottom: 1px solid var(--ta-border, #e4e4e7);
}

.figma-chat-agent-refreshing {
  padding: 6px 12px 0;
  font-size: 11px;
  color: var(--ta-muted, #8c8c8c);
}

.figma-chat-agent-search-input {
  width: 100%;
  height: 32px;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 8px;
  background: var(--ta-panel-2, #f4f4f5);
  color: var(--ta-text, #18181b);
  padding: 0 10px;
  font-size: 12px;
  outline: none;
  box-sizing: border-box;
}

.figma-chat-agent-search-input:focus {
  border-color: var(--ta-ink, #3b82f6);
}

.figma-chat-agent-dropdown-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 12px 12px;
}

.figma-chat-agent-error {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.figma-chat-agent-retry {
  border: 0;
  background: transparent;
  color: var(--ta-ink, #3b82f6);
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  padding: 0;
}

.figma-chat-agent-retry:hover,
.figma-chat-agent-retry:focus-visible {
  text-decoration: underline;
}

.figma-chat-agent-option-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 36px;
  padding: 6px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--ta-text, #18181b);
  font-size: 12px;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.12s;
}

.figma-chat-agent-option-item:hover {
  background: var(--ta-panel-2, #f4f4f5);
}

.figma-chat-agent-option-item.is-active {
  background: #eaf0ff;
  color: #1d3fb0;
  font-weight: 600;
}

.figma-chat-agent-option-info {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}

.figma-chat-agent-option-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.figma-chat-agent-option-text {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.figma-chat-agent-option-name,
.figma-chat-agent-option-desc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.figma-chat-agent-option-desc {
  color: var(--ta-muted, #71717a);
  font-weight: 400;
}

.figma-chat-agent-option-checked {
  color: #1d3fb0;
  font-weight: bold;
}

/* 模型下拉选择框 */
.figma-chat-model-select-wrapper {
  position: relative;
  display: inline-block;
}

.figma-chat-model-dropdown {
  position: absolute;
  bottom: calc(100% + 12px);
  left: 0;
  width: 290px;
  max-height: 400px;
  background: var(--ta-panel, #ffffff);
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.15), 0 1px 4px rgba(15, 23, 42, 0.05);
  z-index: 1000;
  display: flex;
  flex-direction: column;
  overflow: visible;
}

/* 下拉框指示小箭头 */
.figma-chat-model-dropdown::after {
  content: '';
  position: absolute;
  bottom: -6px;
  left: 36px;
  transform: rotate(45deg);
  width: 12px;
  height: 12px;
  background: var(--ta-panel, #ffffff);
  border-right: 1px solid var(--ta-border, #e4e4e7);
  border-bottom: 1px solid var(--ta-border, #e4e4e7);
  z-index: -1;
}

.figma-chat-model-dropdown-search {
  padding: 10px 12px 6px;
  border-bottom: 1px solid var(--ta-border, #e4e4e7);
}

.figma-chat-model-search-input {
  width: 100%;
  height: 32px;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 8px;
  background: var(--ta-panel-2, #f4f4f5);
  color: var(--ta-text, #18181b);
  padding: 0 10px;
  font-size: 12px;
  outline: none;
  box-sizing: border-box;
}

.figma-chat-model-search-input:focus {
  border-color: var(--ta-ink, #3b82f6);
}

.figma-chat-model-dropdown-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 12px 12px;
}

.figma-chat-model-dropdown-list::-webkit-scrollbar {
  width: 4px;
}

.figma-chat-model-dropdown-list::-webkit-scrollbar-thumb {
  background: var(--ta-border, #e4e4e7);
  border-radius: 2px;
}

.figma-chat-model-section {
  padding: 6px 0 10px;
  border-bottom: 1px solid var(--ta-border, #e4e4e7);
  margin-bottom: 8px;
}

.figma-chat-model-section-title,
.figma-chat-model-group-title {
  font-size: 11px;
  font-weight: 700;
  color: var(--ta-muted, #71717a);
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.figma-chat-model-group-title {
  margin-top: 8px;
}

.figma-chat-model-recommended-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
}

.figma-chat-model-rec-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 8px;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 8px;
  background: var(--ta-panel-2, #f4f4f5);
  color: var(--ta-text, #18181b);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.12s ease;
  text-align: left;
  overflow: hidden;
  white-space: nowrap;
  max-width: 100%;
}

.figma-chat-model-rec-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.figma-chat-model-rec-item:hover {
  background: var(--ta-border, #e4e4e7);
}

.figma-chat-model-rec-item.is-active {
  background: #eaf0ff;
  border-color: #b9c8ff;
  color: #1d3fb0;
}

.figma-chat-model-rec-dot,
.figma-chat-model-option-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.figma-chat-model-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.figma-chat-model-option-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 32px;
  padding: 6px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--ta-text, #18181b);
  font-size: 12px;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.12s;
}

.figma-chat-model-option-item:hover {
  background: var(--ta-panel-2, #f4f4f5);
}

.figma-chat-model-option-item.is-active {
  background: #eaf0ff;
  color: #1d3fb0;
  font-weight: 600;
}

.figma-chat-model-option-info {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}

.figma-chat-model-option-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.figma-chat-model-option-checked {
  color: #1d3fb0;
  font-weight: bold;
}

.figma-chat-model-empty {
  padding: 24px 0;
  color: var(--ta-muted, #71717a);
  font-size: 12px;
  text-align: center;
}

/* ===== 结构化 Part 折叠块样式 ===== */
/* 与 agent-chat 包的 ProcessDisclosure 风格保持一致 */

.figma-chat-process-detail {
  margin-top: 4px;
  overflow: hidden;
  border: none;
  background: transparent;
}

.figma-chat-process-detail:hover {
  background: transparent;
  border-color: transparent;
}

@keyframes ta-shimmer {
  0% {
    background-position: -200% 0;
  }
  100% {
    background-position: 200% 0;
  }
}

.figma-chat-process-detail.is-running {
  background: transparent;
  border: none;
  box-shadow: none;
}

.figma-chat-process-detail--error {
  border: none;
  background: transparent;
}

.figma-chat-process-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 0;
  cursor: pointer;
  user-select: none;
  font-size: 12px;
  line-height: 18px;
  color: var(--ta-chat-text, #333);
  list-style: none;
}

.figma-chat-process-summary::-webkit-details-marker {
  display: none;
}

.figma-chat-process-dot {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--ta-chat-border-strong, #c0c0c0);
}

.figma-chat-process-dot--running {
  background: linear-gradient(135deg, #3366ff, #9b51e0);
  animation: figma-chat-pulse 1.4s ease-in-out infinite;
}

.figma-chat-process-dot--error {
  background: var(--ta-chat-status-error, #eb5e53);
}

.figma-chat-process-title {
  font-weight: 600;
  font-size: 12px;
  color: var(--ta-chat-text, #333);
  flex-shrink: 0;
}

.figma-chat-process-meta {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ta-chat-muted, #888);
  font-size: 11px;
}

.figma-chat-process-status-label {
  flex-shrink: 0;
  padding: 0;
  font-size: 11px;
  color: var(--ta-chat-muted, #888);
  background: transparent;
}

.figma-chat-process-status-label--error {
  background: transparent;
  color: #c0392b;
}

.figma-chat-process-chevron {
  flex-shrink: 0;
  color: var(--ta-chat-muted, #999);
  transition: transform 0.15s ease;
}

details[open] .figma-chat-process-chevron {
  transform: rotate(90deg);
}

.figma-chat-process-body {
  border-top: none;
  border-left: 1px solid var(--ta-chat-border, #e5e5e5);
  margin-left: 3px;
  padding: 4px 0 8px 12px;
}

/* 工具 part 内部区域 */
.figma-chat-tool-section {
  margin-bottom: 6px;
}

.figma-chat-tool-section:last-child {
  margin-bottom: 0;
}

.figma-chat-tool-section--error {
  border-left: 2px solid var(--ta-chat-status-error, #eb5e53);
  padding-left: 8px;
}

.figma-chat-tool-section-label {
  font-size: 10px;
  font-weight: 600;
  color: var(--ta-chat-muted, #888);
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.figma-chat-tool-code {
  margin: 0;
  padding: 6px 8px;
  border-radius: 4px;
  background: var(--ta-chat-detail-bg, #f5f5f5);
  font-family: 'JetBrains Mono', 'Cascadia Mono', monospace;
  font-size: 11px;
  line-height: 16px;
  color: var(--ta-chat-text, #374151);
  white-space: pre-wrap;
  word-break: break-all;
  overflow-y: auto;
}

.figma-chat-tool-code--error {
  color: #c0392b;
  background: rgba(235, 94, 83, 0.06);
}

/* retry 错误块 */
.figma-chat-retry-block {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 6px;
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(235, 94, 83, 0.06);
  border: 1px solid rgba(235, 94, 83, 0.2);
  font-size: 12px;
  line-height: 18px;
}

.figma-chat-retry-icon {
  flex-shrink: 0;
  color: #eb5e53;
  margin-top: 2px;
}

.figma-chat-retry-text {
  font-weight: 500;
  color: #c0392b;
}

.figma-chat-retry-detail {
  color: #888;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

/* ---- Code Preview Box with path header and line numbers ---- */
.figma-chat-code-wrapper {
  margin-top: 8px;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  background: #fdfdfd;
  overflow: hidden;
}

.figma-chat-code-header {
  padding: 6px 12px;
  background: #f5f5f5;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  align-items: center;
}

.figma-chat-code-path {
  font-size: 11px;
  font-family: var(--ta-font-mono, monospace);
  color: #8c8c8c;
  word-break: break-all;
}

.figma-chat-code-body {
  margin: 0;
  padding: 8px 0;
  font-size: 12px;
  line-height: 1.6;
  overflow-x: auto;
  background: #fafafa;
}

.figma-chat-code-line {
  display: flex;
  padding: 0 12px;
}

.figma-chat-code-line:hover {
  background: #f0f0f0;
}

.figma-chat-code-lineno {
  width: 28px;
  color: #bfbfbf;
  text-align: right;
  margin-right: 12px;
  user-select: none;
  font-family: var(--ta-font-mono, monospace);
  font-size: 11px;
}

.figma-chat-code-linecontent {
  flex: 1;
  white-space: pre;
  font-family: var(--ta-font-mono, monospace);
  color: #262626;
}

/* ---- Webfetch Link Rows ---- */
.figma-chat-url-fetch-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 30px;
  font-size: 13px;
  color: #262626;
}

.figma-chat-url-fetch-title {
  font-weight: 700;
  color: #1a1a1a;
  flex-shrink: 0;
}

.figma-chat-url-fetch-link {
  color: #3366ff;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.figma-chat-url-fetch-link:hover {
  text-decoration: underline;
}

.figma-chat-url-fetch-icon {
  color: #3366ff;
}

/* ---- Skill Execution Card ---- */
.figma-chat-skill-call-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 6px;
}

.figma-chat-skill-call-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 12px;
}

.figma-chat-skill-call-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  color: #1e293b;
  font-weight: 500;
  font-size: 13px;
}

.figma-chat-skill-call-icon {
  color: #3b82f6;
}

.figma-chat-skill-call-body {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
  word-break: break-word;
}

.figma-chat-skill-call-footer {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 8px;
  text-align: left;
}

/* ---- Inline Task Panel in message content ---- */
.inline-task-panel {
  margin: 8px 0 0 0;
  max-height: none;
  background: #f9f9f9;
  border: 1px solid #e0e0e0;
  padding: 10px 12px;
}

.inline-task-panel .figma-chat-task-summary {
  background: #f9f9f9;
  font-weight: 600;
  color: #595959;
  margin-bottom: 6px;
  position: static;
  padding: 0;
}

.inline-task-panel .figma-chat-task-summary--collapsed {
  margin-bottom: 0;
}

.inline-task-panel .figma-chat-task-row {
  padding: 4px 0;
}

.inline-task-panel .figma-chat-task-row--running .figma-chat-task-label {
  font-weight: 600;
  color: #1a1a1a;
}

.inline-task-panel .figma-chat-task-row--completed .figma-chat-task-label {
  color: #2c3e50;
}

.inline-task-panel .figma-chat-task-row--pending .figma-chat-task-label {
  color: #8c8c8c;
}

/* ---- Retry Card ---- */
.figma-chat-retry-card {
  margin: 4px 10px 6px 46px;
  background: #fff1f0;
  border: 1px solid #ffa39e;
  border-radius: 6px;
  padding: 8px 12px;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12px;
  align-self: auto;
}

.figma-chat-retry-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #ff4d4f;
  flex: 1;
}

.figma-chat-retry-card-icon {
  color: #ff4d4f;
  flex-shrink: 0;
}

.figma-chat-retry-card-text {
  color: #ff4d4f;
  font-weight: 500;
}

.figma-chat-retry-card-copy {
  color: #1890ff;
  cursor: pointer;
  font-size: 11px;
  margin-left: 8px;
  user-select: none;
}

.figma-chat-retry-card-copy:hover {
  text-decoration: underline;
}

.figma-chat-retry-card-btn {
  height: 24px;
  padding: 0 12px;
  background: #ffffff;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  color: #595959;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.12s ease;
  white-space: nowrap;
}

.figma-chat-retry-card-btn:hover {
  background: #f5f5f5;
  border-color: #d9d9d9;
  color: #262626;
}

/* Global styles for appended-to-body feedback dialog */
:global(.figma-chat-feedback-dialog) {
  border-radius: 8px !important;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1) !important;
}
:global(.figma-chat-feedback-dialog .el-dialog__header) {
  padding: 16px 16px 12px 16px !important;
  margin: 0 !important;
  border-bottom: 1px solid #e5e7eb !important;
}
:global(.figma-chat-feedback-dialog .el-dialog__title) {
  font-size: 14px !important;
  font-weight: 600 !important;
  color: #111827 !important;
  line-height: 20px !important;
}
:global(.figma-chat-feedback-dialog .el-dialog__body) {
  padding: 16px !important;
}
:global(.figma-chat-feedback-dialog .el-dialog__footer) {
  padding: 12px 16px 16px 16px !important;
  border-top: 1px solid #e5e7eb !important;
  margin: 0 !important;
  text-align: right !important;
}
:global(.figma-chat-feedback-reasons) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
:global(.figma-chat-feedback-reason) {
  height: 28px;
  padding: 0 10px;
  border: 1px solid #dfe3ea;
  border-radius: 6px;
  background: #fff;
  color: #394150;
  font-size: 12px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}
:global(.figma-chat-feedback-reason.is-selected),
:global(.figma-chat-feedback-reason:hover) {
  border-color: rgba(49, 91, 220, 0.3) !important;
  background: rgba(49, 91, 220, 0.05) !important;
  color: #3366ff !important;
}
:global(.figma-chat-feedback-cancel),
:global(.figma-chat-feedback-submit) {
  min-width: 64px;
  height: 28px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
  margin-left: 8px;
  transition: all 0.15s ease;
}
:global(.figma-chat-feedback-cancel) {
  border: 1px solid #dfe3ea;
  background: #fff;
  color: #4b5563;
}
:global(.figma-chat-feedback-cancel:hover) {
  background: #f9fafb;
}
:global(.figma-chat-feedback-submit) {
  border: 1px solid #3366ff;
  background: #3366ff;
  color: #fff;
}
:global(.figma-chat-feedback-submit:hover) {
  background: #2552d4;
  border-color: #2552d4;
}
</style>
