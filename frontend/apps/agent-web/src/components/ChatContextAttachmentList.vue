<script setup lang="ts">
import { computed, ref } from "vue";
import { ChevronDown, ChevronUp } from "lucide-vue-next";
import type { ChatContextItem } from "../stores/chatContextStore";
import { CHAT_CONTEXT_LIMITS } from "../stores/chatContextStore";
import ChatContextAttachmentCard from "./ChatContextAttachmentCard.vue";

const props = defineProps<{
  items: ChatContextItem[];
  totalCharCount: number;
  overLimit?: boolean;
  error?: string | null;
}>();

const emit = defineEmits<{
  remove: [id: string];
  clear: [];
  preview: [item: ChatContextItem];
}>();

const summaryText = computed(() =>
  `已添加 ${props.items.length} 个上下文，约 ${props.totalCharCount.toLocaleString("zh-CN")} / ${CHAT_CONTEXT_LIMITS.MAX_TOTAL_CONTEXT_CHARS.toLocaleString("zh-CN")} 字`
);

const COLLAPSED_VISIBLE_COUNT = 3;
const expanded = ref(false);
const hasMoreItems = computed(() => props.items.length > COLLAPSED_VISIBLE_COUNT);
const hiddenItemCount = computed(() => Math.max(0, props.items.length - COLLAPSED_VISIBLE_COUNT));
const visibleItems = computed(() =>
  expanded.value || !hasMoreItems.value ? props.items : props.items.slice(0, COLLAPSED_VISIBLE_COUNT)
);

/** 按标准工作区阶段汇总附件，帮助大量文件场景快速确认上下文构成。 */
const stageSummaryText = computed(() => {
  const stages = [
    { directory: "01-需求", label: "需求" },
    { directory: "02-设计", label: "设计" },
    { directory: "03-编码", label: "编码" },
    { directory: "04-测试", label: "测试" }
  ];
  const counts = new Map(stages.map((stage) => [stage.directory, 0]));
  let matchedCount = 0;
  for (const item of props.items) {
    const segments = item.path.replace(/\\/g, "/").split("/");
    const stage = stages.find((candidate) => segments.includes(candidate.directory));
    if (stage) {
      counts.set(stage.directory, (counts.get(stage.directory) ?? 0) + 1);
      matchedCount += 1;
    }
  }
  const parts = stages
    .filter((stage) => (counts.get(stage.directory) ?? 0) > 0)
    .map((stage) => `${stage.label} ${counts.get(stage.directory)}`);
  if (matchedCount < props.items.length) {
    parts.push(`其他 ${props.items.length - matchedCount}`);
  }
  return parts.join(" · ");
});
</script>

<template>
  <section v-if="items.length || error" class="chat-context-list" aria-label="工作区上下文附件">
    <div class="chat-context-list-head">
      <span :class="['chat-context-list-summary', overLimit && 'is-warning']">{{ summaryText }}</span>
      <button v-if="items.length" type="button" class="chat-context-list-clear" @click="emit('clear')">清空</button>
    </div>
    <div v-if="stageSummaryText" class="chat-context-list-stage-summary">{{ stageSummaryText }}</div>
    <div v-if="items.length" :class="['chat-context-list-cards', expanded && hasMoreItems && 'is-expanded']">
      <ChatContextAttachmentCard
        v-for="item in visibleItems"
        :key="item.id"
        :item="item"
        @preview="emit('preview', $event)"
        @remove="emit('remove', $event)"
      />
    </div>
    <button
      v-if="hasMoreItems"
      type="button"
      class="chat-context-list-more"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >
      <component :is="expanded ? ChevronUp : ChevronDown" class="chat-context-list-more-icon" />
      {{ expanded ? '收起附件列表' : `查看其余 ${hiddenItemCount} 个文件` }}
    </button>
    <div v-if="overLimit || error" class="chat-context-list-error">
      {{ error || '上下文过长，暂不能发送。请删除部分文件，或改为选择关键片段。' }}
    </div>
  </section>
</template>

<style scoped>
.chat-context-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 6px;
  margin-left: 10px;
  margin-right: 10px;
  padding: 6px 8px;
  border: 1px solid rgba(0, 0, 0, 0.04);
  border-radius: 8px;
  background: #fafafa;
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
  color: var(--ta-chat-muted, #6b7280);
  font-size: 12px;
}

.chat-context-list-summary.is-warning,
.chat-context-list-error {
  color: #b45309;
}

.chat-context-list-stage-summary {
  overflow: hidden;
  color: #8a8f98;
  font-size: 11px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-context-list-clear {
  flex: 0 0 auto;
  border: 0;
  background: transparent;
  color: #4b5563;
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

.chat-context-list-cards.is-expanded {
  max-height: 220px;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 2px;
}

.chat-context-list-more {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  gap: 4px;
  border: 0;
  background: transparent;
  color: #596579;
  padding: 1px 0;
  font-size: 11px;
  cursor: pointer;
}

.chat-context-list-more:hover {
  color: #1f2937;
}

.chat-context-list-more-icon {
  width: 13px;
  height: 13px;
}

.chat-context-list-error {
  font-size: 12px;
}
</style>
