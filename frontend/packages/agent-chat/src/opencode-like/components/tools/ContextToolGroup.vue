<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type ContextToolGroupProps = {
  parts: Array<Extract<MessagePart, { type: "tool" }>>;
  busy?: boolean;
};
</script>

<script setup lang="ts">
import { computed, ref } from "vue";
import { ChevronDown, ChevronRight } from "lucide-vue-next";
import { getToolInfo } from "../../state/tool-registry";

const props = defineProps<ContextToolGroupProps>();
const open = ref(false);
const statusText = computed(() => props.busy ? "进行中" : "已读取");
const subtitleText = computed(() => `读取 ${props.parts.length} 次`);
</script>

<template>
  <section class="oc-context-group" data-testid="oc-context-group">
    <button type="button" class="oc-context-group__trigger" @click="open = !open">
      <span class="oc-tool__title">探索</span>
      <span class="oc-tool__subtitle">{{ subtitleText }}</span>
      <span :class="['oc-tool__status', props.busy ? 'is-running' : 'is-completed']">{{ statusText }}</span>
      <ChevronDown v-if="open" class="oc-tool__chevron" />
      <ChevronRight v-else class="oc-tool__chevron" />
    </button>
    <div v-if="open" class="oc-context-group__body">
      <div v-for="part in parts" :key="part.partId" class="oc-context-group__item">
        <span class="oc-context-group__kind">{{ getToolInfo(part).title }}</span>
        <span class="oc-context-group__path" :title="getToolInfo(part).fullSubtitle ?? getToolInfo(part).subtitle ?? part.toolName">
          {{ getToolInfo(part).subtitle ?? part.toolName }}
        </span>
      </div>
    </div>
  </section>
</template>
