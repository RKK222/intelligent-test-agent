<script setup lang="ts">
import type { ChatActivityItem, ChatActivitySummary } from './chat-activity-summary'

defineProps<{
  open: boolean
  summary: ChatActivitySummary
  /** 聊天容器窄于断点时改用模态抽屉，桌面端维持非模态浮层。 */
  presentation: 'popover' | 'drawer'
  panelId: string
}>()

const emit = defineEmits<{
  (event: 'close'): void
}>()

/** 只将聚合状态转为简短文案，不复制原生 Ask / Permission 的处理动作。 */
function itemLabel(item: ChatActivityItem): string {
  switch (item.kind) {
    case 'confirmation':
      return item.title
    case 'subagent':
      return item.title || item.agentName
    case 'todo':
      return item.title
    case 'run-failed':
      return '本轮运行失败'
  }
}

function itemHint(item: ChatActivityItem): string {
  switch (item.kind) {
    case 'confirmation':
      return '请在对话中处理'
    case 'subagent':
      return `子 Agent · ${item.status}`
    case 'todo':
      return '进行中任务'
    case 'run-failed':
      return item.status
  }
}
</script>

<template>
  <section
    v-if="open"
    class="figma-chat-activity-panel"
    data-testid="chat-activity-panel"
    :id="panelId"
    :role="presentation === 'drawer' ? 'dialog' : 'region'"
    :aria-modal="presentation === 'drawer' ? 'true' : undefined"
    aria-label="本轮活动"
    :tabindex="presentation === 'drawer' ? -1 : undefined"
  >
    <header class="figma-chat-activity-panel-header">
      <div>
        <h3>本轮活动</h3>
        <p v-if="summary.pendingConfirmationCount > 0">
          {{ summary.pendingConfirmationCount }} 项等待确认
        </p>
      </div>
      <button
        type="button"
        class="figma-chat-activity-close"
        aria-label="关闭本轮活动"
        @click="emit('close')"
      >
        关闭
      </button>
    </header>
    <ul class="figma-chat-activity-list">
      <li v-for="item in summary.items" :key="`${item.kind}-${'requestId' in item ? item.requestId : 'sessionId' in item ? item.sessionId : 'todoId' in item ? item.todoId : item.runId}`">
        <span class="figma-chat-activity-item-label">{{ itemLabel(item) }}</span>
        <span class="figma-chat-activity-item-hint">{{ itemHint(item) }}</span>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.figma-chat-activity-panel {
  width: min(360px, calc(100% - 24px));
  max-height: min(420px, calc(100vh - 96px));
  overflow: auto;
  border: 1px solid var(--ta-chat-border, #eaeaea);
  border-radius: 10px;
  background: var(--ta-chat-surface, #fff);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.16);
  color: var(--ta-text, #18181b);
}

.figma-chat-activity-panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-bottom: 1px solid var(--ta-chat-border, #eaeaea);
}

.figma-chat-activity-panel h3,
.figma-chat-activity-panel p {
  margin: 0;
}

.figma-chat-activity-panel h3 {
  font-size: 14px;
  line-height: 20px;
}

.figma-chat-activity-panel p,
.figma-chat-activity-item-hint {
  color: var(--ta-chat-muted, #71717a);
  font-size: 12px;
  line-height: 18px;
}

.figma-chat-activity-close {
  border: 0;
  border-radius: 5px;
  padding: 4px 6px;
  background: transparent;
  color: var(--ta-chat-muted, #71717a);
  cursor: pointer;
}

.figma-chat-activity-close:hover {
  background: var(--ta-hover, #f4f4f5);
  color: var(--ta-text, #18181b);
}

.figma-chat-activity-list {
  display: grid;
  gap: 2px;
  margin: 0;
  padding: 6px;
  list-style: none;
}

.figma-chat-activity-list li {
  display: grid;
  gap: 2px;
  padding: 8px 6px;
  border-radius: 6px;
}

.figma-chat-activity-list li:hover {
  background: var(--ta-hover, #f4f4f5);
}

.figma-chat-activity-item-label {
  overflow: hidden;
  font-size: 13px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
