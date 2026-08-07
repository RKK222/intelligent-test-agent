<script setup lang="ts">
import { CircleHelp } from "lucide-vue-next";

defineProps<{
  label: string;
  description: string;
}>();
</script>

<template>
  <span class="ta-metric-help-label">
    <span>{{ label }}</span>
    <el-tooltip
      :content="description"
      placement="top"
      :show-after="100"
      :trigger="['hover', 'focus']"
      popper-class="ta-metric-help-popper"
    >
      <button
        type="button"
        class="ta-metric-help-button"
        :aria-label="`查看${label}说明`"
      >
        <CircleHelp :size="12" :stroke-width="1.8" aria-hidden="true" />
      </button>
    </el-tooltip>
  </span>
</template>

<style scoped>
.ta-metric-help-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  min-width: 0;
}

.ta-metric-help-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 14px;
  height: 14px;
  padding: 0;
  border: 0;
  background: transparent;
  color: currentColor;
  opacity: 0.72;
  cursor: help;
}

.ta-metric-help-button:hover,
.ta-metric-help-button:focus-visible {
  opacity: 1;
  outline: none;
}

:global(.ta-metric-help-popper) {
  max-width: 320px;
  line-height: 1.55;
}
</style>
