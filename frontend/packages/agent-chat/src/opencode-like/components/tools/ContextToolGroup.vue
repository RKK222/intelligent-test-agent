<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type ContextToolGroupProps = {
  parts: Array<Extract<MessagePart, { type: "tool" }>>;
  busy?: boolean;
};
</script>

<script setup lang="ts">
import { computed, ref } from "vue";
import { ChevronDown, ChevronRight, Files } from "lucide-vue-next";
import { getToolInfo } from "../../state/tool-registry";

const props = defineProps<ContextToolGroupProps>();
const open = ref(false);
const title = computed(() => `上下文 ${props.parts.length}`);
</script>

<template>
  <section class="oc-context-group" data-testid="oc-context-group">
    <button type="button" class="oc-context-group__trigger" @click="open = !open">
      <Files class="oc-tool__icon" />
      <span class="oc-tool__title">{{ title }}</span>
      <span v-if="busy" class="oc-status-pill">读取中</span>
      <ChevronDown v-if="open" class="oc-tool__chevron" />
      <ChevronRight v-else class="oc-tool__chevron" />
    </button>
    <div v-if="open" class="oc-context-group__body">
      <div v-for="part in parts" :key="part.partId" class="oc-context-group__item">
        <span class="oc-context-group__kind">{{ getToolInfo(part).title }}</span>
        <span class="oc-context-group__path">{{ getToolInfo(part).subtitle ?? part.toolName }}</span>
      </div>
    </div>
  </section>
</template>
