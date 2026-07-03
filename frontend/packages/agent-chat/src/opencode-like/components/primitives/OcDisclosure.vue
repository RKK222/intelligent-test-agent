<script lang="ts">
export type OcDisclosureProps = {
  title: string;
  subtitle?: string;
  defaultOpen?: boolean;
};
</script>

<script setup lang="ts">
import { ref } from "vue";
import { ChevronDown, ChevronRight } from "lucide-vue-next";

const props = withDefaults(defineProps<OcDisclosureProps>(), {
  defaultOpen: false
});
const open = ref(props.defaultOpen);
</script>

<template>
  <section class="oc-disclosure">
    <button type="button" class="oc-disclosure__trigger" @click="open = !open">
      <ChevronDown v-if="open" class="oc-tool__chevron" />
      <ChevronRight v-else class="oc-tool__chevron" />
      <span class="oc-tool__title">{{ title }}</span>
      <span v-if="subtitle" class="oc-tool__subtitle">{{ subtitle }}</span>
    </button>
    <div v-if="open" class="oc-disclosure__body">
      <slot />
    </div>
  </section>
</template>
