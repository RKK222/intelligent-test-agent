<script lang="ts">
import type { Run } from "@test-agent/shared-types";

export type TestRunnerPanelProps = {
  run?: Run | null;
  logs: string[];
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import { RotateCcw, Square } from "lucide-vue-next";
import { Badge, Button } from "@test-agent/ui-kit";

const props = defineProps<TestRunnerPanelProps>();
const emit = defineEmits<{ cancel: []; retry: [] }>();

const running = computed(() => props.run?.status === "RUNNING" || props.run?.status === "CANCELLING");
</script>

<template>
  <div class="flex h-full min-h-0 flex-col bg-[var(--ta-panel)]">
    <div class="flex h-10 items-center gap-2 border-b border-slate-800 bg-slate-950 px-3">
      <div class="min-w-0 flex-1 text-[12px] font-semibold text-slate-200">运行</div>
      <Badge :tone="run?.status === 'FAILED' ? 'danger' : run?.status === 'SUCCEEDED' ? 'success' : running ? 'info' : 'neutral'">
        {{ run?.status ?? "IDLE" }}
      </Badge>
      <Button size="sm" variant="secondary" :disabled="!running" @click="emit('cancel')">
        <Square class="h-3.5 w-3.5" />
        取消
      </Button>
      <Button size="sm" variant="secondary" @click="emit('retry')">
        <RotateCcw class="h-3.5 w-3.5" />
        重试
      </Button>
    </div>
    <pre class="min-h-0 flex-1 overflow-auto whitespace-pre-wrap p-3 font-mono text-[12px] leading-6 text-slate-300">{{ logs.length ? logs.join('\n') : '等待运行输出...' }}</pre>
  </div>
</template>
