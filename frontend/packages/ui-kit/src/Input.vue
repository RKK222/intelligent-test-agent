<script setup lang="ts">
import { cn } from "./lib";

// 支持 v-model；其余原生 input 属性（placeholder/type/disabled 等）透传到内部 input
const model = defineModel<string>({ default: "" });
</script>

<template>
  <input
    v-model="model"
    :class="cn(
      'h-8 w-full rounded border border-[var(--ta-border)] bg-[var(--ta-surface)] px-2 text-[12px] text-[var(--ta-text)] outline-none transition placeholder:text-[var(--ta-muted)] focus:border-[var(--ta-border-strong)]'
    )"
  />
</template>
