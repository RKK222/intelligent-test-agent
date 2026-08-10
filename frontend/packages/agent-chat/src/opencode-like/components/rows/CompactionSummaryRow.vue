<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type CompactionSummaryRowProps = {
  part: Extract<MessagePart, { type: "compaction" }>;
  summary: string;
};
</script>

<script setup lang="ts">
import { computed, ref } from "vue";
import { Minimize2 } from "lucide-vue-next";
import MarkdownView from "../../../MarkdownView.vue";
import OcIconButton from "../primitives/OcIconButton.vue";

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

const modeLabel = computed(() => props.part.overflow
  ? "超出窗口"
  : props.part.auto ? "自动压缩" : "手动压缩");
const expanded = ref(false);
const detailId = computed(() => `oc-compaction-detail-${props.part.partId.replace(/[^a-zA-Z0-9_-]/g, "-")}`);
</script>

<template>
  <section
    class="oc-compaction-summary"
    :data-testid="`compaction-part-${part.partId}`"
  >
    <div class="oc-compaction-summary__summary">
      <OcIconButton
        class="oc-compaction-summary__trigger"
        :label="expanded ? '收起上下文压缩详情' : '展开上下文压缩详情'"
        :aria-expanded="expanded"
        :aria-controls="detailId"
        @click="expanded = !expanded"
      >
        <Minimize2 aria-hidden="true" />
      </OcIconButton>
    </div>
    <div
      v-if="expanded"
      :id="detailId"
      class="oc-compaction-summary__panel"
      role="region"
      aria-label="上下文压缩详情"
    >
      <div class="oc-compaction-summary__heading">
        <strong>上下文已压缩</strong>
        <span>{{ modeLabel }}</span>
      </div>
      <div class="oc-compaction-summary__body">
        <p>较早的对话已整理为续写摘要。</p>
        <p>
          这不是新的回答。系统用这份摘要替代较早的对话内容，让模型在有限上下文窗口内继续当前任务。
        </p>
        <p v-if="part.overflow" class="oc-compaction-summary__note">
          本次由上下文接近容量上限触发。
        </p>
        <MarkdownView
          v-if="displaySummary.trim()"
          :source="displaySummary"
          :highlight="false"
          body-class="oc-compaction-summary__markdown"
        />
        <p v-else class="oc-compaction-summary__empty">压缩摘要正在同步。</p>
      </div>
    </div>
  </section>
</template>
