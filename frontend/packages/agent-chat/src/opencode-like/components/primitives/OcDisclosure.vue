<script lang="ts">
export type OcDisclosureProps = {
  title: string;
  detail?: string;
  subtitle?: string;
  defaultOpen?: boolean;
  status?: string;
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
  <section :class="['oc-disclosure', status === 'running' ? 'is-running' : '']">
    <button type="button" class="oc-disclosure__trigger" @click="open = !open">
      <span class="oc-tool__title">{{ title }}</span>
      <span v-if="detail" class="oc-tool__subtitle">{{ detail }}</span>
      <span v-if="subtitle" :class="['oc-tool__status', status ? `is-${status}` : '']">{{ subtitle }}</span>
      <ChevronDown v-if="open" class="oc-tool__chevron" />
      <ChevronRight v-else class="oc-tool__chevron" />
    </button>
    <div v-if="open" class="oc-disclosure__body">
      <slot />
    </div>
  </section>
</template>
