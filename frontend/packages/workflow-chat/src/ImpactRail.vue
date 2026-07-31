<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{ status?: string; reportReady: boolean; hasManifest?: boolean }>();
const stages = ["意图", "冻结", "差异", "分析", "报告"];
const activeIndex = computed(() => {
  if (props.reportReady || ["SUCCEEDED", "PARTIAL_FAILED"].includes(props.status ?? "")) return 4;
  if (props.status === "RUNNING") return props.hasManifest ? 3 : 1;
  if (props.status === "WAITING_INPUT") return 1;
  if (props.status === "QUEUED") return 0;
  return 0;
});
</script>

<template>
  <ol class="impact-rail" aria-label="影响分析进度">
    <li v-for="(stage, index) in stages" :key="stage" :class="{ active: index <= activeIndex, current: index === activeIndex }">
      <span>{{ index + 1 }}</span><strong>{{ stage }}</strong>
    </li>
  </ol>
</template>
