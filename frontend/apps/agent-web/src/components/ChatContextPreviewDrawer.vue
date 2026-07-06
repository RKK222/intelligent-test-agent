<script setup lang="ts">
import { computed } from "vue";
import { X } from "lucide-vue-next";
import type { ChatContextItem } from "../stores/chatContextStore";
import { getContextItemText } from "../stores/chatContextStore";

const props = defineProps<{
  item?: ChatContextItem | null;
}>();

const emit = defineEmits<{
  close: [];
}>();

const title = computed(() => {
  if (!props.item) return "";
  return props.item.type === "selection"
    ? `${props.item.fileName} L${props.item.startLine}-L${props.item.endLine}`
    : props.item.fileName;
});
</script>

<template>
  <Teleport to="body">
    <div v-if="item" class="chat-context-preview-backdrop" @click="emit('close')" />
    <aside v-if="item" class="chat-context-preview" role="dialog" aria-label="上下文预览">
      <header class="chat-context-preview-head">
        <div class="chat-context-preview-title">
          <span>{{ item.type === 'selection' ? '选区预览' : '文件预览' }}</span>
          <strong :title="item.path">{{ title }}</strong>
        </div>
        <button type="button" class="chat-context-preview-close" aria-label="关闭预览" @click="emit('close')">
          <X class="chat-context-preview-close-icon" />
        </button>
      </header>
      <pre class="chat-context-preview-body">{{ getContextItemText(item) }}</pre>
    </aside>
  </Teleport>
</template>

<style scoped>
.chat-context-preview-backdrop {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgb(15 23 42 / 18%);
}

.chat-context-preview {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 3001;
  display: flex;
  width: min(560px, 92vw);
  flex-direction: column;
  background: #fff;
  box-shadow: -16px 0 40px rgb(15 23 42 / 16%);
}

.chat-context-preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 52px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--ta-border, #e5e7eb);
}

.chat-context-preview-title {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: var(--ta-chat-muted, #64748b);
}

.chat-context-preview-title strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ta-chat-text, #111827);
  font-size: 13px;
}

.chat-context-preview-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
}

.chat-context-preview-close:hover {
  background: #f1f5f9;
  color: #111827;
}

.chat-context-preview-close-icon {
  width: 16px;
  height: 16px;
}

.chat-context-preview-body {
  flex: 1;
  min-height: 0;
  margin: 0;
  overflow: auto;
  padding: 14px;
  background: #fbfdff;
  color: #1f2937;
  font-family: "Geist Mono", Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
