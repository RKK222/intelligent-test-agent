<script setup lang="ts">
import { useAttrs } from "vue";
import { cn } from "./lib";

defineOptions({
  inheritAttrs: false
});

const attrs = useAttrs();
// 支持 v-model；其余原生 input 属性（placeholder/type/disabled 等）透传到内部 input
const model = defineModel<string>({ default: "" });
</script>

<template>
  <input
    v-model="model"
    v-bind="attrs"
    :class="cn(
      'h-8 w-full rounded border border-[var(--ta-border)] bg-[var(--ta-surface)] pl-2 pr-2 text-[12px] font-sans text-[var(--ta-text)] outline-none transition placeholder:font-sans placeholder:text-[var(--ta-muted)] focus:border-[var(--ta-border-strong)]',
      attrs.class
    )"
  />
</template>
