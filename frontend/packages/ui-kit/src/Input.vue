<script setup lang="ts">
import { cn } from "./lib";

// 支持 v-model；其余原生 input 属性（placeholder/type/disabled 等）透传到内部 input
const model = defineModel<string>({ default: "" });
</script>

<template>
  <input
    v-model="model"
    :class="cn(
      'h-8 w-full rounded-md border border-[var(--ta-border)] bg-[#f4f5f7] px-2 text-[12px] text-[var(--ta-text)] outline-none transition placeholder:text-[var(--ta-muted)] focus:border-[#c8ced6]'
    )"
  />
</template>
