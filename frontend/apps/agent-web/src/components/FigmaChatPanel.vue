<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { Download, History, ListTodo, PanelRightClose, PencilLine, Plus, Send, Square, Upload } from "lucide-vue-next";

type ChatMessageInput = {
  id: string;
  role: string;
  content?: string;
  text?: string;
  parts?: Array<{ type: string; text?: string }>;
  createdAt?: string;
};

type ChatMessage = {
  role: "user" | "assistant";
  content: string;
  meta?: string;
};

export type FileChangeStat = {
  path: string;
  additions?: number;
  deletions?: number;
  status?: "added" | "modified" | "deleted" | string;
  patch?: string;
};

export type TaskUsage = {
  duration?: string;
  tokens?: number;
  thoughtFor?: string;
};

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
}>();

const emit = defineEmits<{
  (e: "send", prompt: string): void;
  (e: "stop"): void;
  (e: "new-conversation"): void;
  (e: "close"): void;
  (e: "open-history"): void;
  (e: "open-tasks"): void;
  (e: "update:inputValue", value: string): void;
  (e: "download-files"): void;
  (e: "open-diff", path: string): void;
}>();

const localInput = ref(props.inputValue ?? "");

watch(
  () => props.inputValue,
  (v) => {
    if (typeof v === "string" && v !== localInput.value) localInput.value = v;
  }
);

const displayMessages = computed<ChatMessage[]>(() => {
  return (props.messages || [])
    .map((m): ChatMessage | null => {
      if (m.role !== "user" && m.role !== "assistant") return null;
      let text = "";
      if (typeof m.content === "string") {
        text = m.content;
      } else if (typeof m.text === "string") {
        text = m.text;
      } else if (Array.isArray(m.parts)) {
        text = m.parts.map((p) => p?.text ?? "").join("");
      }
      return {
        role: m.role,
        content: text,
        meta: m.createdAt ? formatTime(m.createdAt) : undefined
      };
    })
    .filter((m): m is ChatMessage => m !== null);
});

const lastAssistant = computed(() => {
  for (let i = displayMessages.value.length - 1; i >= 0; i -= 1) {
    if (displayMessages.value[i].role === "assistant") return displayMessages.value[i];
  }
  return null;
});

const lastUser = computed(() => {
  for (let i = displayMessages.value.length - 1; i >= 0; i -= 1) {
    if (displayMessages.value[i].role === "user") return displayMessages.value[i];
  }
  return null;
});

const hasTaskUsage = computed(
  () => !!(props.taskUsage && (props.taskUsage.duration || props.taskUsage.tokens !== undefined || props.taskUsage.thoughtFor))
);

const hasFileChanges = computed(() => (props.fileChanges?.length ?? 0) > 0);

const totalAdditions = computed(() =>
  (props.fileChanges || []).reduce((sum, f) => sum + (f.additions ?? 0), 0)
);

const totalDeletions = computed(() =>
  (props.fileChanges || []).reduce((sum, f) => sum + (f.deletions ?? 0), 0)
);

const visibleFiles = computed(() => (props.fileChanges || []).slice(0, 3));

const scrollEl = ref<HTMLElement | null>(null);

watch(
  () => props.messages.length,
  () => nextTick(() => scrollEl.value?.scrollTo({ top: scrollEl.value.scrollHeight, behavior: "smooth" }))
);

function formatTime(iso: string) {
  try {
    const d = new Date(iso);
    return d.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
  } catch {
    return "";
  }
}

function submit() {
  const text = localInput.value.trim();
  if (!text || props.running) return;
  emit("send", text);
  localInput.value = "";
  emit("update:inputValue", "");
}

function stop() {
  emit("stop");
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    submit();
  }
}
</script>

<template>
  <div class="figma-chat-root">
    <header class="figma-chat-header">
      <h2 class="figma-chat-title">{{ title || "生成测试案例" }}</h2>
      <button
        type="button"
        class="figma-chat-close"
        aria-label="收起对话面板"
        @click="emit('close')"
      >
        <PanelRightClose class="figma-chat-close-icon" />
      </button>
    </header>

    <!-- 任务消耗提示（位于对话框上方） -->
    <div v-if="hasTaskUsage" class="figma-chat-usage">
      <span class="figma-chat-usage-text">
        <span class="figma-chat-usage-label">· 任务消耗：</span>
        <span v-if="taskUsage?.duration" class="figma-chat-usage-value">({{ taskUsage.duration }}</span>
        <span v-if="taskUsage?.tokens !== undefined" class="figma-chat-usage-value"> · ↓ {{ taskUsage.tokens }} tokens</span>
        <span v-if="taskUsage?.thoughtFor" class="figma-chat-usage-value"> · thought for {{ taskUsage.thoughtFor }}</span>
        <span v-if="hasTaskUsage" class="figma-chat-usage-value">)</span>
        <span v-if="hasFileChanges" class="figma-chat-usage-stats">
          <span class="figma-chat-add">+{{ totalAdditions }}</span>
          <span class="figma-chat-del">-{{ totalDeletions }}</span>
        </span>
      </span>
    </div>

    <div ref="scrollEl" class="figma-chat-scroll">
      <!-- 文件变更行卡片 -->
      <div v-if="hasFileChanges" class="figma-chat-changes-card">
        <div class="figma-chat-changes-icon">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
            <rect x="3" y="2" width="11" height="16" rx="2" stroke="#777" stroke-width="1.4" fill="none"/>
            <path d="M5.5 7H11.5M5.5 10H11.5M5.5 13H9" stroke="#777" stroke-width="1.4" stroke-linecap="round"/>
            <path d="M14 14L18 10M18 10H14M18 10V14" stroke="#18a978" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <div class="figma-chat-changes-info">
          <span class="figma-chat-changes-title">{{ fileChanges?.length }} 个文件已更改</span>
          <div class="figma-chat-changes-paths">
            <div
              v-for="file in visibleFiles"
              :key="file.path"
              class="figma-chat-changes-path"
              role="button"
              tabindex="0"
              @click="emit('open-diff', file.path)"
            >
              <span class="figma-chat-changes-path-name">{{ file.path }}</span>
              <span class="figma-chat-changes-path-stats">
                <span v-if="file.additions" class="figma-chat-add">+{{ file.additions }}</span>
                <span v-if="file.deletions" class="figma-chat-del">-{{ file.deletions }}</span>
              </span>
            </div>
            <div v-if="(fileChanges?.length ?? 0) > 3" class="figma-chat-changes-more">
              还有 {{ (fileChanges?.length ?? 0) - 3 }} 个文件...
            </div>
          </div>
        </div>
      </div>

      <!-- 用户消息气泡 (右对齐) -->
      <div v-if="lastUser" class="figma-chat-bubble figma-chat-bubble--user">
        <div class="figma-chat-bubble-content">{{ lastUser.content }}</div>
        <div v-if="lastUser.meta" class="figma-chat-bubble-meta">你 · {{ lastUser.meta }}</div>
      </div>

      <!-- 助手消息 (左对齐) -->
      <div v-if="lastAssistant" class="figma-chat-assistant">
        <div class="figma-chat-avatar">
          <span class="figma-chat-avatar-text">AI</span>
        </div>
        <div class="figma-chat-assistant-content">
          <div class="figma-chat-bubble figma-chat-bubble--assistant">
            <div class="figma-chat-bubble-content">{{ lastAssistant.content }}</div>
          </div>
          <div v-if="lastAssistant.meta" class="figma-chat-bubble-meta">测试智能体 · {{ lastAssistant.meta }}</div>
        </div>
      </div>

      <!-- 空态 -->
      <div v-if="displayMessages.length === 0" class="figma-chat-empty">
        <div class="figma-chat-empty-icon">
          <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
            <circle cx="16" cy="16" r="12" stroke="#ccc" stroke-width="1.4" stroke-dasharray="2 2"/>
            <path d="M12 16L15 19L20 13" stroke="#ccc" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <p class="figma-chat-empty-title">开始一次新的对话</p>
        <p class="figma-chat-empty-hint">告诉我你想测试的模块或功能，我会自动生成测试用例</p>
      </div>

      <!-- 运行中状态 -->
      <div v-if="running" class="figma-chat-status">
        <div class="figma-chat-status-dot" />
        <span>智能体正在思考...</span>
      </div>
    </div>

    <div class="figma-chat-composer">
      <textarea
        v-model="localInput"
        class="figma-chat-textarea"
        :placeholder="placeholder || 'Ask the AI agent...'"
        rows="1"
        :disabled="running"
        @keydown="onKeydown"
      />
      <div class="figma-chat-composer-actions">
        <button
          type="button"
          class="figma-chat-icon-btn"
          aria-label="清空输入"
          :disabled="!localInput || running"
          @click="localInput = ''"
        >
          <PencilLine class="figma-chat-btn-icon" />
        </button>
        <button
          type="button"
          class="figma-chat-icon-btn"
          aria-label="下载文件"
          :disabled="!hasFileChanges"
          @click="emit('download-files')"
        >
          <Download class="figma-chat-btn-icon" />
        </button>
        <div class="figma-chat-composer-spacer" />
        <button
          type="button"
          class="figma-chat-icon-btn figma-chat-new-btn"
          :disabled="running"
          @click="emit('new-conversation')"
        >
          <Plus class="figma-chat-btn-icon" />
          <span>新建对话</span>
        </button>
        <button
          v-if="!running"
          type="button"
          class="figma-chat-send"
          :disabled="!localInput.trim()"
          aria-label="发送"
          @click="submit"
        >
          <Send class="figma-chat-send-icon" />
        </button>
        <button
          v-else
          type="button"
          class="figma-chat-stop"
          aria-label="停止执行"
          @click="stop"
        >
          <Square class="figma-chat-stop-icon" fill="currentColor" />
        </button>
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
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
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
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
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

/* ---- File Changes Card ---- */
.figma-chat-changes-card {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  background: #fafafa;
  border: 1px solid #efefef;
  border-radius: 8px;
}

.figma-chat-changes-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  margin-top: 2px;
}

.figma-chat-changes-info {
  flex: 1;
  min-width: 0;
}

.figma-chat-changes-title {
  font-size: 12px;
  font-weight: 500;
  color: #18181b;
  line-height: 18px;
  display: block;
  margin-bottom: 6px;
}

.figma-chat-changes-paths {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.figma-chat-changes-path {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 3px 6px;
  border-radius: 4px;
  font-size: 11px;
  color: #555;
  cursor: pointer;
  transition: background-color 0.1s ease;
  font-family: "JetBrains Mono", monospace;
}

.figma-chat-changes-path:hover {
  background: #f0f0f0;
}

.figma-chat-changes-path-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.figma-chat-changes-path-stats {
  display: flex;
  gap: 4px;
  font-family: "JetBrains Mono", monospace;
  font-size: 10px;
}

.figma-chat-changes-more {
  font-size: 11px;
  color: #999;
  padding: 2px 6px;
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
  align-items: flex-start;
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
  background: #f4f4f5;
  flex-shrink: 0;
  color: #555;
  font-size: 10px;
  font-weight: 600;
  font-family: "Inter", sans-serif;
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

@keyframes figma-chat-pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 1; }
}

/* ---- Task Usage (above scroll area) ---- */
.figma-chat-usage {
  flex-shrink: 0;
  padding: 8px 18px 4px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  font-family: "JetBrains Mono", "PingFang SC", monospace;
  font-size: 12px;
  line-height: 20px;
  color: #a40dbc;
  letter-spacing: -0.0125em;
}

.figma-chat-usage-text {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.figma-chat-usage-label {
  color: #a40dbc;
  font-weight: 500;
}

.figma-chat-usage-value {
  color: #a40dbc;
  font-weight: 400;
}

.figma-chat-usage-stats {
  margin-left: 8px;
  display: inline-flex;
  gap: 4px;
  font-weight: 500;
}

.figma-chat-add {
  color: #18a978;
  font-family: "JetBrains Mono", monospace;
}

.figma-chat-del {
  color: #eb5e53;
  font-family: "JetBrains Mono", monospace;
}

/* ---- Composer ---- */
.figma-chat-composer {
  flex-shrink: 0;
  padding: 8px 12px 12px;
  border-top: 1px solid #ddd;
  background: #fff;
}

.figma-chat-textarea {
  width: 100%;
  min-height: 56px;
  max-height: 120px;
  padding: 8px 10px;
  font-family: "Inter", "PingFang SC", sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #111;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 8px;
  resize: none;
  outline: none;
  box-sizing: border-box;
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
  margin-top: 6px;
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
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  opacity: 0.85;
  transition: opacity 0.12s ease, background-color 0.12s ease, border-color 0.12s ease;
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
  border-radius: 50%;
}

.figma-chat-stop:hover {
  background: #f0f4ff;
}

.figma-chat-send-icon,
.figma-chat-stop-icon {
  width: 13px;
  height: 13px;
}
</style>
