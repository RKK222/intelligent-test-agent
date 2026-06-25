<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  ArrowUpRight,
  ChevronDown,
  ChevronRight,
  Eye,
  EyeOff,
  History,
  ListTodo,
  PanelRightClose,
  Plus,
  Send,
  Square,
  Upload,
  X,
} from 'lucide-vue-next'
import type { AgentMessage } from '@test-agent/shared-types'
import aiHeaderUrl from '../assets/figma/ai-header.svg'
import planLoadingUrl from '../assets/figma/plan-loadding.gif'
import panelCloseUrl from '../assets/figma/panel-close.svg'

type ChatMessageInput = AgentMessage & { content?: string }

type ChatMessage = {
  id: string
  role: 'user' | 'assistant'
  content: string
  meta?: string
}

function partText(part: unknown): string {
  if (part && typeof part === 'object' && 'text' in part) {
    const text = (part as { text?: unknown }).text
    return typeof text === 'string' ? text : ''
  }
  return ''
}

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
  thoughtFor?: string
}

type OpencodeProcessState = {
  status: string;
  initializable: boolean;
  message: string;
  baseUrl?: string;
};

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

const props = defineProps<{
  messages: ChatMessageInput[];
  running?: boolean;
  placeholder?: string;
  inputValue?: string;
  title?: string;
  /** 任务消耗（来自 SSE 事件统计） */
  taskUsage?: TaskUsage;
  /** 文件变更行（来自 SSE 事件统计） */
  fileChanges?: FileChangeStat[];
  /** 历史对话列表 */
  history?: Array<{ id: string; title: string; createdAt?: string }>;
  /** 当前选中的模型展示名 */
  selectedModelLabel?: string;
  /** 模型选择按钮是否禁用 */
  modelPickerDisabled?: boolean;
  /** 终止按钮是否禁用 */
  stopDisabled?: boolean;
  /** 终止按钮禁用原因 */
  stopDisabledReason?: string;
  /** 当前用户 opencode 进程状态，控制是否允许发起对话 */
  processStatus?: OpencodeProcessState | null;
  processRequired?: boolean;
  processLoading?: boolean;
  processInitializing?: boolean;
}>();

const emit = defineEmits<{
  (e: "send", prompt: string): void;
  (e: "stop"): void;
  (e: "new-conversation"): void;
  (e: "close"): void;
  (e: "open-history"): void;
  (e: "open-tasks"): void;
  (e: "update:inputValue", value: string): void;
  (e: "open-diff", path: string): void;
  (e: "open-model-picker"): void;
  (e: "initialize-process"): void;
}>();

const localInput = ref(props.inputValue ?? '')
const inputComposing = ref(false)

// ===== 文件变更抽屉 =====
// 抽屉默认选中第一个文件；打开后通过 fileChanges 变化自动跟随到最新一个文件（与原有的“跟随最近一次变化”心智一致）。
const drawerOpen = ref(false)
const drawerSelectedPath = ref<string>('')
const drawerScroll = ref<HTMLElement | null>(null)
const attachmentDialogOpen = ref(false)
// 是否在 diff 视图中显示 unified diff 的上下文行（未改动的行）。
// 默认关闭：用户在文件变更抽屉里通常只想看真正的 +/- 行，避免出现
// “只改一行但全文飘红” 的体验。当后端 patch 是整文件重写时，关闭上下文
// 仍然会看到完整的 del+add 列表，但能配合新增的 toggle 切换为完整上下文做核对。
const showContext = ref(false)
const thinkingExpanded = ref(false)

// 从最新消息中提取思考过程（reasoning + tool 操作摘要）
function toolSummary(
  toolName: string,
  input: Record<string, unknown> | undefined
): string {
  const name = toolName || 'tool'
  if (!input) return `[${name}]`
  // 优先展示文件路径（filePath 是 opencode 主字段名）
  const file =
    (typeof input.filePath === 'string' && input.filePath) ||
    (typeof input.path === 'string' && input.path) ||
    (typeof input.file_path === 'string' && input.file_path) ||
    (typeof input.file === 'string' && input.file) ||
    (typeof input.directory === 'string' && input.directory) ||
    (typeof input.target === 'string' && input.target) ||
    ''
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
const reasoningText = computed(() => thinkingLines.value.join('\n'))
const reasoningHtml = computed(() =>
  reasoningText.value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\[(\w+)\]/g, '<strong>[$1]</strong>')
)

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
const processReady = computed(() => {
  if (props.processRequired) {
    return !props.processLoading && props.processStatus?.status === "READY";
  }
  return !props.processLoading && (!props.processStatus || props.processStatus.status === "READY");
});
const processStatusVisible = computed(() => props.processRequired || props.processLoading || props.processStatus != null);
const processStatusTitle = computed(() => {
  if (props.processLoading) return "正在检查 opencode 进程";
  if (props.processRequired && !props.processStatus) return "正在检查 opencode 进程";
  if (!props.processStatus) return "";
  if (props.processStatus.status === "READY") return "opencode 进程可用";
  if (props.processStatus.status === "NEEDS_INITIALIZATION") return "需要初始化 opencode 进程";
  return "opencode 进程不可用";
});
const processStatusText = computed(() => {
  if (props.processLoading) return "正在检查当前用户可用进程";
  if (props.processRequired && !props.processStatus) return "正在检查当前用户可用进程";
  if (!props.processStatus) return "";
  return props.processStatus.baseUrl ?? props.processStatus.message;
});

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
  void nextTick(() => drawerScroll.value?.scrollTo({ top: 0 }))
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

// Esc 关闭面板内浮层：监听全局 keydown，只在当前浮层打开时响应。
function onOverlayKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && attachmentDialogOpen.value) {
    event.preventDefault()
    closeAttachmentDialog()
    return
  }
  if (event.key === 'Escape' && drawerOpen.value) {
    event.preventDefault()
    closeChangesDrawer()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onOverlayKeydown)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onOverlayKeydown)
})

watch(
  () => props.inputValue,
  (v) => {
    if (typeof v === 'string' && v !== localInput.value) localInput.value = v
  }
)

watch(
  () => props.running,
  (now, prev) => {
    if (now && !prev) thinkingExpanded.value = false
  }
)

const displayMessages = computed<ChatMessage[]>(() => {
  return (props.messages || [])
    .map((m, index): ChatMessage | null => {
      if (m.role !== 'user' && m.role !== 'assistant') return null
      let text = ''
      if (typeof m.content === 'string') {
        text = m.content
      } else if (typeof m.text === 'string') {
        text = m.text
      } else if (Array.isArray(m.parts)) {
        text = m.parts.map((p) => partText(p)).join('')
      }
      if (!text.trim()) return null
      return {
        id: m.messageId ?? m.id ?? `${m.role}-${index}`,
        role: m.role,
        content: text,
        meta: m.createdAt ? formatTime(m.createdAt) : undefined,
      }
    })
    .filter((m): m is ChatMessage => m !== null)
})

const lastAssistant = computed(() => {
  for (let i = displayMessages.value.length - 1; i >= 0; i -= 1) {
    if (displayMessages.value[i].role === 'assistant')
      return displayMessages.value[i]
  }
  return null
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
      (props.taskUsage.duration ||
        props.taskUsage.tokens !== undefined ||
        props.taskUsage.thoughtFor)
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

const hasTaskUsageDisplay = computed(
  () =>
    !!(
      props.taskUsage &&
      (props.taskUsage.duration ||
        displayTokens.value !== undefined ||
        props.taskUsage.thoughtFor)
    )
)

const scrollEl = ref<HTMLElement | null>(null)

watch(
  () => props.messages.length,
  () =>
    nextTick(() =>
      scrollEl.value?.scrollTo({
        top: scrollEl.value.scrollHeight,
        behavior: 'smooth',
      })
    )
)

function formatTime(iso: string) {
  try {
    const d = new Date(iso)
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } catch {
    return ''
  }
}

function submit() {
  const text = localInput.value.trim()
  if (!text || props.running) return
  emit('send', text)
  localInput.value = ''
  emit('update:inputValue', '')
}

function stop() {
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
  <div class="figma-chat-root">
    <header class="figma-chat-header">
      <h2 class="figma-chat-title">{{ title || '生成测试案例' }}</h2>
    </header>

    <div ref="scrollEl" class="figma-chat-scroll">
      <template v-for="message in displayMessages" :key="message.id">
        <!-- 用户消息气泡 (右对齐) -->
        <div v-if="message.role === 'user'" class="figma-chat-bubble figma-chat-bubble--user">
          <div class="figma-chat-bubble-content">{{ message.content }}</div>
          <div v-if="message.meta" class="figma-chat-bubble-meta">
            你 · {{ message.meta }}
          </div>
        </div>

        <!-- 助手消息 (左对齐) -->
        <div v-else class="figma-chat-assistant">
          <div class="figma-chat-avatar">
            <img :src="aiHeaderUrl" alt="AI" class="figma-chat-avatar-icon" />
          </div>
          <div class="figma-chat-assistant-content">
            <div class="figma-chat-bubble figma-chat-bubble--assistant">
              <div class="figma-chat-bubble-content">
                {{ message.content }}
              </div>
            </div>
            <div v-if="message.meta" class="figma-chat-bubble-meta">
              测试智能体 · {{ message.meta }}
            </div>
          </div>
        </div>
      </template>

      <!-- 空态 -->
      <div v-if="displayMessages.length === 0" class="figma-chat-empty">
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
      <div v-if="running" class="figma-chat-status">
        <div class="figma-chat-status-dot" />
        <span>智能体正在思考...</span>
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
        v-if="running && thinkingExpanded && reasoningText"
        class="figma-chat-thinking-body"
      >
        <pre class="figma-chat-thinking-text" v-html="reasoningHtml" />
      </div>
    </div>

    <!-- 文件变更提示（位于任务消耗上方）。点击后弹出右侧抽屉，列出本次变更文件并展示 diff。 -->
    <button
      v-if="hasFileChanges"
      type="button"
      class="figma-chat-changes-card"
      :title="`${fileChanges?.length} 个文件已更改，点击查看 diff`"
      :aria-expanded="drawerOpen"
      aria-haspopup="dialog"
      @click="openChangesDrawer"
    >
      <span class="figma-chat-changes-icon" aria-hidden="true">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path
            d="M7 18C4.79 18 3 16.21 3 14C3 11.95 4.5 10.27 6.5 10.03C6.97 7.64 9.05 5.85 11.57 5.85C13.95 5.85 15.94 7.42 16.55 9.6C16.97 9.45 17.42 9.36 17.9 9.36C20.18 9.36 22 11.18 22 13.46C22 15.74 20.18 17.56 17.9 17.56"
            stroke="white"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </span>
      <span class="figma-chat-changes-title"
        >{{ fileChanges?.length }} 个文件已更改</span
      >
      <span class="figma-chat-changes-spacer" />
      <span class="figma-chat-changes-stats">
        <span v-if="totalAdditions" class="figma-chat-add"
          >+{{ totalAdditions }}</span
        >
        <span v-if="totalDeletions" class="figma-chat-del"
          >-{{ totalDeletions }}</span
        >
      </span>
      <ArrowUpRight class="figma-chat-changes-arrow" :size="16" />
    </button>

    <!-- 任务消耗提示（位于输入框上方） -->
    <div v-if="hasTaskUsageDisplay" class="figma-chat-usage">
      <img v-if="running" :src="planLoadingUrl" alt="" class="figma-chat-usage-icon" />
      <span v-else class="figma-chat-usage-dot" aria-hidden="true" />
      <span class="figma-chat-usage-label">任务消耗：</span>
      <span class="figma-chat-usage-value">
        <template
          v-if="
            taskUsage?.duration ||
            displayTokens !== undefined ||
            taskUsage?.thoughtFor
          "
          >(</template
        >
        <template v-if="taskUsage?.duration">{{ taskUsage.duration }}</template>
        <template v-if="displayTokens !== undefined">
          · ↓ {{ displayTokens }} tokens</template
        >
        <template v-if="taskUsage?.thoughtFor">
          · thought for {{ taskUsage.thoughtFor }}</template
        >
        <template
          v-if="
            taskUsage?.duration ||
            displayTokens !== undefined ||
            taskUsage?.thoughtFor
          "
          >)</template
        >
      </span>
    </div>

    <div
      v-if="processStatusVisible"
      :class="['figma-chat-process-status', processReady ? 'is-ready' : 'is-blocking']"
    >
      <div class="figma-chat-process-copy">
        <span class="figma-chat-process-title">{{ processStatusTitle }}</span>
        <span v-if="processStatusText" class="figma-chat-process-message">{{ processStatusText }}</span>
      </div>
      <button
        v-if="processStatus?.status === 'NEEDS_INITIALIZATION'"
        type="button"
        class="figma-chat-process-init"
        :disabled="processInitializing || processLoading || !processStatus.initializable"
        @click="emit('initialize-process')"
      >
        {{ processInitializing ? "初始化中" : "初始化进程" }}
      </button>
    </div>

    <div class="figma-chat-composer">
      <div class="figma-chat-input-row">
        <textarea
          v-model="localInput"
          class="figma-chat-textarea"
          :placeholder="placeholder || 'Ask the AI agent...'"
          rows="1"
          :disabled="running || !processReady"
          @keydown="onKeydown"
          @compositionstart="onCompositionStart"
          @compositionend="onCompositionEnd"
        />
        <button
          v-if="!running"
          type="button"
          class="figma-chat-send figma-chat-send--inline"
          :disabled="!localInput.trim() || !processReady"
          aria-label="发送"
          @click="submit"
        >
          <Send class="figma-chat-send-icon" />
        </button>
        <button
          v-else
          type="button"
          class="figma-chat-stop figma-chat-send--inline"
          :disabled="stopDisabled"
          :title="stopDisabledReason || '停止执行'"
          aria-label="停止执行"
          @click="stop"
        >
          <Square class="figma-chat-stop-icon" fill="currentColor" />
        </button>
      </div>
      <div class="figma-chat-composer-actions">
        <button
          type="button"
          class="figma-chat-icon-btn figma-chat-attachment-btn"
          aria-label="上传附件"
          title="上传附件"
          @click="openAttachmentDialog"
        >
          <Upload class="figma-chat-btn-icon" />
        </button>
        <div class="figma-chat-composer-spacer" />
        <button
          type="button"
          class="figma-chat-icon-btn figma-chat-model-btn"
          :disabled="modelPickerDisabled"
          title="切换模型"
          aria-label="切换模型"
          @click="emit('open-model-picker')"
        >
          <span class="figma-chat-model-label">{{ selectedModelLabel || '选择模型' }}</span>
          <ChevronDown class="figma-chat-btn-icon" />
        </button>
        <button
          type="button"
          class="figma-chat-icon-btn figma-chat-new-btn"
          :disabled="running || !processReady"
          @click="emit('new-conversation')"
        >
          <Plus class="figma-chat-btn-icon" />
          <span>新建对话</span>
        </button>
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
            <p class="figma-chat-attachment-subtitle">附件会随测试任务一起提交</p>
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
        <button
          type="button"
          class="figma-chat-attachment-drop"
          @click.prevent
        >
          <span class="figma-chat-attachment-drop-icon" aria-hidden="true">
            <Upload :size="22" />
          </span>
          <span class="figma-chat-attachment-drop-title">选择或拖拽文件到这里</span>
          <span class="figma-chat-attachment-drop-hint">支持文档、图片和日志文件，后台接口接入后开放上传。</span>
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
      v-if="drawerOpen"
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
                        ? '新增'
                        : file.status === 'deleted'
                        ? '删除'
                        : '修改'
                    }}</span
                  >
                  <span class="figma-chat-drawer-file-path">{{
                    file.path
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
                点击右上角“上下文”可显示完整 diff 行。
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
  </div>
</template>

<style scoped>
.figma-chat-root {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #fff;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  /* 抽屉遮罩使用 position: absolute 覆盖在聊天面板上，需要 root 作为定位上下文 */
  position: relative;
}

/* ---- Header ---- */
.figma-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  flex-shrink: 0;
  padding: 0 6px 0 14px;
  background: #fff;
  border-bottom: 1px solid #ddd;
}

.figma-chat-title {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.0143em;
  color: #18181b;
  margin: 0;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  line-height: 20px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
  padding: 18px 18px 12px;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 14px;
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
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
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
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 20px;
  letter-spacing: -0.0107em;
  word-break: break-word;
  white-space: pre-wrap;
}

.figma-chat-bubble--user {
  align-self: flex-end;
  background: #f4f4f5;
  color: #111;
  max-width: 80%;
  border-top-right-radius: 2px;
}

.figma-chat-bubble--assistant {
  background: transparent;
  padding: 0;
  color: #333;
  border-top-left-radius: 2px;
}

.figma-chat-bubble-content {
  font-size: 14px;
  line-height: 22px;
  color: inherit;
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
}

.figma-chat-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: transparent;
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
.figma-chat-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 8px;
  font-size: 12px;
  color: #666;
  align-self: flex-start;
}

.figma-chat-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #18a978;
  animation: figma-chat-pulse 1.4s infinite ease-in-out;
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

.figma-chat-thinking-toggle:hover {
  background: #e8e8e8;
  color: #666;
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
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
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
.figma-chat-usage {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 6px 18px 8px;
  background: #fff;
  font-family: 'JetBrains Mono', 'PingFang SC', monospace;
  font-size: 12px;
  line-height: 20px;
  color: #a40dbc;
  letter-spacing: -0.0125em;
}

.figma-chat-usage-icon {
  width: 20px;
  height: 20px;
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
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
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
  gap: 10px;
  margin: 0 12px 4px;
  padding: 8px 10px;
  border: 1px solid #d7d7d7;
  border-radius: 6px;
  background: #fafafa;
  color: #333;
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
  font-size: 12px;
  line-height: 16px;
  font-weight: 600;
}

.figma-chat-process-message {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  line-height: 16px;
  color: #666;
}

.figma-chat-process-init {
  flex-shrink: 0;
  height: 26px;
  padding: 0 10px;
  border: 1px solid #b5b5b5;
  border-radius: 6px;
  background: #fff;
  color: #333;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
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
  padding: 8px 12px 12px;
  background: #fff;
}

.figma-chat-input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 44px;
  align-items: center;
  gap: 8px;
}

.figma-chat-textarea {
  width: 100%;
  min-height: 56px;
  max-height: 120px;
  padding: 8px 10px;
  font-family: 'Inter', 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #111;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 8px;
  resize: none;
  outline: none;
  box-sizing: border-box;
  align-self: stretch;
  transition: border-color 0.12s ease;
}

.figma-chat-textarea:focus {
  border-color: #999;
}

.figma-chat-textarea:disabled {
  background: #fafafa;
  color: #999;
  cursor: not-allowed;
}

.figma-chat-textarea::placeholder {
  color: rgba(51, 51, 51, 0.5);
}

.figma-chat-composer-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.figma-chat-composer-spacer {
  flex: 1;
}

.figma-chat-icon-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 8px;
  border: 1px solid #d7d7d7;
  border-radius: 6px;
  background: #fff;
  color: #555;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  opacity: 0.85;
  transition: opacity 0.12s ease, background-color 0.12s ease,
    border-color 0.12s ease;
}

.figma-chat-icon-btn:not(:disabled):hover {
  opacity: 1;
  background: #f5f5f5;
  border-color: #b5b5b5;
}

.figma-chat-icon-btn:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.figma-chat-new-btn {
  background: #fff;
  border-color: #d7d7d7;
  color: #555;
}

.figma-chat-attachment-btn {
  width: 28px;
  height: 28px;
  justify-content: center;
  padding: 0;
}

.figma-chat-model-btn {
  max-width: 156px;
  min-width: 0;
}

.figma-chat-model-label {
  min-width: 0;
  max-width: 112px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.figma-chat-btn-icon {
  width: 12px;
  height: 12px;
}

.figma-chat-send,
.figma-chat-stop {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  cursor: pointer;
  transition: background-color 0.12s ease, opacity 0.12s ease;
}

.figma-chat-send--inline {
  width: 44px;
  height: 44px;
  align-self: center;
  border-radius: 999px;
}

.figma-chat-send {
  background: #3366ff;
  color: #fff;
  border-radius: 50%;
  opacity: 0.5;
}

.figma-chat-send:not(:disabled) {
  opacity: 1;
}

.figma-chat-send:not(:disabled):hover {
  background: #2855e0;
}

.figma-chat-stop {
  background: #fff;
  color: #3366ff;
  border: 1.5px solid #3366ff;
  border-radius: 999px;
}

.figma-chat-stop:hover {
  background: #f0f4ff;
}

.figma-chat-stop:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.figma-chat-send-icon,
.figma-chat-stop-icon {
  width: 13px;
  height: 13px;
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
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
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
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
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
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
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

/* 上下文行切换：默认 “仅变更” 状态（is-active）时高亮，让用户一眼分辨当前是哪种模式。
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
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
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
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
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
</style>
