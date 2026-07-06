<script setup lang="ts">
import { computed, ref } from "vue";
import type { ChatContextItem } from "../stores/chatContextStore";
import { CHAT_CONTEXT_LIMITS } from "../stores/chatContextStore";
import ChatContextAttachmentCard from "./ChatContextAttachmentCard.vue";
import ChatContextPreviewDrawer from "./ChatContextPreviewDrawer.vue";

const props = defineProps<{
  items: ChatContextItem[];
  totalCharCount: number;
  overLimit?: boolean;
  error?: string | null;
}>();

const emit = defineEmits<{
  remove: [id: string];
  clear: [];
}>();

const previewItem = ref<ChatContextItem | null>(null);

const summaryText = computed(() =>
  `已添加 ${props.items.length} 个上下文，约 ${props.totalCharCount.toLocaleString("zh-CN")} / ${CHAT_CONTEXT_LIMITS.MAX_TOTAL_CONTEXT_CHARS.toLocaleString("zh-CN")} 字`
);
</script>

<template>
  <section v-if="items.length || error" class="chat-context-list" aria-label="工作区上下文附件">
    <div class="chat-context-list-head">
      <span :class="['chat-context-list-summary', overLimit && 'is-warning']">{{ summaryText }}</span>
      <button v-if="items.length" type="button" class="chat-context-list-clear" @click="emit('clear')">清空</button>
    </div>
    <div v-if="items.length" class="chat-context-list-cards">
      <ChatContextAttachmentCard
        v-for="item in items"
        :key="item.id"
        :item="item"
        @preview="previewItem = $event"
        @remove="emit('remove', $event)"
      />
    </div>
    <div v-if="overLimit || error" class="chat-context-list-error">
      {{ error || '上下文过长，暂不能发送。请删除部分文件，或改为选择关键片段。' }}
    </div>
    <ChatContextPreviewDrawer :item="previewItem" @close="previewItem = null" />
  </section>
</template>

<style scoped>
.chat-context-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 8px;
  padding: 8px;
  border: 1px solid var(--ta-border, #e5e7eb);
  border-radius: 8px;
  background: #f8fafc;
}

.chat-context-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.chat-context-list-summary {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ta-chat-muted, #64748b);
  font-size: 12px;
}

.chat-context-list-summary.is-warning,
.chat-context-list-error {
  color: #b45309;
}

.chat-context-list-clear {
  flex: 0 0 auto;
  border: 0;
  background: transparent;
  color: #475569;
  font-size: 12px;
  cursor: pointer;
}

.chat-context-list-clear:hover {
  color: #111827;
}

.chat-context-list-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.chat-context-list-error {
  font-size: 12px;
}
</style>
