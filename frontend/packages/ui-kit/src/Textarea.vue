<script setup lang="ts">
import { useAttrs } from "vue";
import { cn } from "./lib";

defineOptions({
  inheritAttrs: false
});

const attrs = useAttrs();
// 支持 v-model；其余原生 textarea 属性透传到内部 textarea
const model = defineModel<string>({ default: "" });
</script>

<template>
  <textarea
    v-model="model"
    v-bind="attrs"
    :class="cn(
      'w-full resize-none rounded border border-[var(--ta-border)] bg-[var(--ta-surface)] pl-3 pr-3 py-2 text-[12px] font-sans text-[var(--ta-text)] outline-none transition placeholder:font-sans placeholder:text-[var(--ta-muted)] focus:border-[var(--ta-border-strong)]',
      attrs.class
    )"
  />
</template>
