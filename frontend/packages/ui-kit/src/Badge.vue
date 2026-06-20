<script setup lang="ts">
import { cn } from "./lib";

withDefaults(
  defineProps<{ tone?: "neutral" | "info" | "success" | "warning" | "danger" }>(),
  { tone: "neutral" }
);

// tone → 背景与文字色映射
const toneClass: Record<string, string> = {
  neutral: "bg-[var(--ta-control)] text-[var(--ta-subtle)]",
  info: "bg-[var(--ta-control)] text-[var(--ta-subtle)]",
  success: "bg-[rgba(34,197,94,.14)] text-[#3f7a5a]",
  warning: "bg-[rgba(245,158,11,.15)] text-[#946015]",
  danger: "bg-[rgba(239,68,68,.14)] text-[#9e3b34]"
};
</script>

<template>
  <span :class="cn('inline-flex rounded-full px-2 py-0.5 text-[11px] font-medium', toneClass[tone])">
    <slot />
  </span>
</template>
