<script setup lang="ts">
import { cn } from "./lib";

// 支持 v-model；其余原生 textarea 属性透传到内部 textarea
const model = defineModel<string>({ default: "" });
</script>

<template>
  <textarea
    v-model="model"
    :class="cn(
      'w-full resize-none rounded-md border border-[var(--ta-border)] bg-[#f4f5f7] px-2 py-2 text-[12px] text-[var(--ta-text)] outline-none transition placeholder:text-[var(--ta-muted)] focus:border-[#c8ced6]'
    )"
  />
</template>
