<!-- 作废说明：旧气泡消息 part 渲染路径已被 opencode-like/OpencodeTimeline 取代；仅为历史兼容保留，不再扩展新能力。 -->
<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type ToolPartBlockProps = {
  part: Extract<MessagePart, { type: "tool" }>;
  running?: boolean;
};
</script>

<script setup lang="ts">
import { computed, ref } from "vue";
import { ChevronDown, ChevronRight } from "lucide-vue-next";
import ToolDetail from "./ToolDetail.vue";
import { normalizeProcessStatus, toolPartIsSkill } from "./process-status";
import { skillNameFromPart, toolPurpose } from "./chat-utils";

const props = defineProps<ToolPartBlockProps>();
const skill = computed(() => toolPartIsSkill(props.part));
const purpose = computed(() => toolPurpose(props.part));
const skillName = computed(() => (skill.value ? skillNameFromPart(props.part) : undefined));

const summaryText = computed(() =>
  skill.value
    ? `${skillName.value ?? ""}${skillName.value && purpose.value ? "｜" : ""}${purpose.value ?? ""}`
    : purpose.value ?? ""
);

const isRunning = computed(() => {
  const norm = normalizeProcessStatus(props.part.status);
  return norm === "running" && props.running;
});

const normalizedStatus = computed(() => {
  const norm = normalizeProcessStatus(props.part.status);
  if (norm === "running" && !props.running) {
    return "completed";
  }
  return norm;
});

const expanded = ref(normalizeProcessStatus(props.part.status) === "running");
const detailLabel = computed(() => (skill.value ? skillName.value ?? "Skill 调用" : props.part.toolName));
</script>

<template>
  <div :data-testid="`${skill ? 'skill' : 'tool'}-part-${part.partId}`" class="py-1 text-[12px]">
    <!-- 头部横行 -->
    <div class="flex items-center justify-between gap-2">
      <div class="flex items-center gap-2 min-w-0">
        <!-- 状态小圆点 -->
        <span
          :class="[
            'h-1.5 w-1.5 rounded-full shrink-0',
            isRunning ? 'animate-pulse bg-[var(--ta-cyan)]' : 'bg-[var(--ta-chat-muted)]'
          ]"
        />
        <!-- 状态文字（流光效果或普通颜色） -->
        <span
          :class="[
            'font-medium truncate',
            isRunning ? 'ta-text-shimmer' : 'text-[var(--ta-chat-subtle)]'
          ]"
        >
          <span v-if="isRunning">正在调用能力: {{ part.toolName }}...</span>
          <span v-else>✓ 调用能力: {{ part.toolName }}</span>
        </span>
        <!-- 能力说明描述 -->
        <span v-if="summaryText && !isRunning" class="text-[11px] text-[var(--ta-chat-muted)] truncate max-w-[200px]">
          ({{ summaryText }})
        </span>
      </div>

      <!-- 操作区：展开收起 -->
      <button
        type="button"
        class="inline-flex h-5 items-center gap-0.5 rounded px-1.5 text-[10px] text-[var(--ta-chat-muted)] hover:bg-[var(--ta-chat-hover)] hover:text-[var(--ta-chat-text)] cursor-pointer shrink-0"
        @click="expanded = !expanded"
      >
        <span>{{ expanded ? '收起' : '查看' }}</span>
        <ChevronDown v-if="expanded" class="h-3 w-3" />
        <ChevronRight v-else class="h-3 w-3" />
      </button>
    </div>

    <!-- 折叠的工具调用详细输入输出 -->
    <div v-if="expanded" class="mt-2 border-l border-[var(--ta-chat-border)] ml-[3px] pl-3 space-y-2">
      <ToolDetail
        :label="detailLabel"
        :status="normalizedStatus"
        :purpose="purpose"
        :input="part.input"
        :output="part.output"
        :metadata="part.metadata"
        :status-kind="skill ? 'skill' : 'tool'"
        :started-at="part.startedAt"
        :ended-at="part.endedAt"
      />
    </div>
  </div>
</template>
