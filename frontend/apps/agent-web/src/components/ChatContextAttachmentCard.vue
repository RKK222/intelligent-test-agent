<script setup lang="ts">
import { FileText, Scissors, X } from "lucide-vue-next";
import type { ChatContextItem } from "../stores/chatContextStore";
import { CHAT_CONTEXT_LIMITS, getContextItemLineText } from "../stores/chatContextStore";

const props = defineProps<{
  item: ChatContextItem;
}>();

const emit = defineEmits<{
  preview: [item: ChatContextItem];
  remove: [id: string];
}>();

function formatNumber(value: number): string {
  return value.toLocaleString("zh-CN");
}

function itemOverLimit(): boolean {
  return props.item.type === "selection"
    ? props.item.charCount > CHAT_CONTEXT_LIMITS.MAX_SELECTION_CHARS
    : props.item.charCount > CHAT_CONTEXT_LIMITS.MAX_FILE_CHARS;
}

function onCardKeydown(event: KeyboardEvent) {
  if (event.key !== "Enter" && event.key !== " ") {
    return;
  }
  event.preventDefault();
  emit("preview", props.item);
}
</script>

<template>
  <div
    :class="['chat-context-card', itemOverLimit() && 'is-warning']"
    role="button"
    tabindex="0"
    :title="item.path"
    @click="emit('preview', item)"
    @keydown="onCardKeydown"
  >
    <component :is="item.type === 'selection' ? Scissors : FileText" class="chat-context-card-icon" />
    <span class="chat-context-card-type">{{ item.type === 'selection' ? '选区' : '文件' }}</span>
    <span class="chat-context-card-name">{{ item.fileName }}</span>
    <span class="chat-context-card-meta">{{ getContextItemLineText(item) }} · {{ formatNumber(item.charCount) }} 字</span>
    <button
      type="button"
      class="chat-context-card-remove"
      aria-label="删除上下文"
      @click.stop="emit('remove', item.id)"
    >
      <X class="chat-context-card-remove-icon" />
    </button>
  </div>
</template>

<style scoped>
.chat-context-card {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  max-width: 100%;
  height: 30px;
  gap: 6px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fafafa;
  color: var(--ta-chat-text, #1f2937);
  padding: 0 4px 0 9px;
  font-size: 12px;
  cursor: pointer;
}

.chat-context-card:hover {
  border-color: #c7d2fe;
  background: #fff;
}

.chat-context-card.is-warning {
  border-color: #f59e0b;
  background: #fff7ed;
}

.chat-context-card-icon,
.chat-context-card-remove-icon {
  width: 14px;
  height: 14px;
  flex: 0 0 auto;
}

.chat-context-card-type {
  flex: 0 0 auto;
  color: var(--ta-chat-muted, #64748b);
}

.chat-context-card-name {
  min-width: 0;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.chat-context-card-meta {
  flex: 0 0 auto;
  color: var(--ta-chat-muted, #64748b);
}

.chat-context-card-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--ta-chat-muted, #64748b);
  cursor: pointer;
}

.chat-context-card-remove:hover {
  background: #eef2f7;
  color: #111827;
}
</style>
