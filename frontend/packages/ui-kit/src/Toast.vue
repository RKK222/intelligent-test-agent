<script lang="ts">
export type Feedback = {
  kind: "info" | "success" | "error";
  title: string;
  description?: string;
  traceId?: string;
};
</script>

<script setup lang="ts">
import { AlertCircle, CheckCircle2 } from "lucide-vue-next";
import { cn } from "./lib";

defineProps<{ feedback?: Feedback | null }>();
</script>

<template>
  <div
    v-if="feedback"
    :class="cn(
      'flex items-start gap-2 border-t border-slate-800 bg-slate-950 px-3 py-2 text-[12px]',
      feedback.kind === 'error' && 'border-red-900/70 bg-red-950/30',
      feedback.kind === 'success' && 'border-emerald-900/70 bg-emerald-950/30'
    )"
  >
    <AlertCircle v-if="feedback.kind !== 'success'" class="mt-0.5 h-4 w-4 shrink-0" />
    <CheckCircle2 v-else class="mt-0.5 h-4 w-4 shrink-0" />
    <div class="min-w-0">
      <div class="font-medium text-slate-100">{{ feedback.title }}</div>
      <div v-if="feedback.description" class="mt-0.5 text-slate-400">{{ feedback.description }}</div>
      <div v-if="feedback.traceId" class="mt-1 font-mono text-[11px] text-slate-500">
        traceId: {{ feedback.traceId }}
      </div>
    </div>
  </div>
</template>
