<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type CompactionSummaryRowProps = {
  part: Extract<MessagePart, { type: "compaction" }>;
  summary: string;
};
</script>

<script setup lang="ts">
import { computed, ref } from "vue";
import { ChevronDown, ChevronRight, Minimize2 } from "lucide-vue-next";
import MarkdownView from "../../../MarkdownView.vue";

const props = defineProps<CompactionSummaryRowProps>();

const headingTranslations = new Map([
  ["Objective", "当前目标"],
  ["Important Details", "关键信息"],
  ["Work State", "工作状态"],
  ["Completed", "已完成"],
  ["Active", "进行中"],
  ["Blocked", "阻塞项"],
  ["Next Move", "下一步"],
  ["Relevant Files", "相关文件"]
]);

/** 内部摘要仍保留原文，只在展示层翻译固定结构标题和空值占位。 */
const displaySummary = computed(() => props.summary.split("\n").map((line) => {
  if (line.trim() === "(none)") return line.replace("(none)", "无");
  const match = line.match(/^(\s*(?:#{1,6}\s+)?)(\*\*)?([^:*]+?)(\*\*)?\s*:?\s*$/);
  if (!match) return line;
  const translated = headingTranslations.get(match[3]?.trim() ?? "");
  if (!translated) return line;
  const bold = match[2] === "**" && match[4] === "**" ? "**" : "";
  return `${match[1]}${bold}${translated}${bold}`;
}).join("\n"));

const triggerLabel = computed(() => props.part.auto || props.part.overflow
  ? "上下文已自动压缩"
  : "上下文已手动压缩");
const expanded = ref(false);
const detailId = computed(() => `oc-compaction-detail-${props.part.partId.replace(/[^a-zA-Z0-9_-]/g, "-")}`);
</script>

<template>
  <section
    class="oc-compaction-summary"
    :data-testid="`compaction-part-${part.partId}`"
  >
    <div class="oc-compaction-summary__summary">
      <button
        type="button"
        class="oc-compaction-summary__trigger"
        :class="{ 'is-expanded': expanded }"
        :aria-label="expanded ? '收起上下文压缩详情' : '展开上下文压缩详情'"
        :title="expanded ? '收起上下文压缩详情' : '展开上下文压缩详情'"
        :aria-expanded="expanded"
        :aria-controls="detailId"
        @click="expanded = !expanded"
      >
        <Minimize2 class="oc-compaction-summary__icon" aria-hidden="true" />
        <span class="oc-compaction-summary__label">{{ triggerLabel }}</span>
        <ChevronDown v-if="expanded" class="oc-compaction-summary__chevron" aria-hidden="true" />
        <ChevronRight v-else class="oc-compaction-summary__chevron" aria-hidden="true" />
      </button>
      <span class="oc-compaction-summary__rule" aria-hidden="true" />
    </div>
    <div
      v-if="expanded"
      :id="detailId"
      class="oc-compaction-summary__panel"
      role="region"
      aria-label="上下文压缩详情"
    >
      <MarkdownView
        :source="displaySummary"
        :highlight="false"
        body-class="oc-compaction-summary__markdown"
      />
    </div>
  </section>
</template>
