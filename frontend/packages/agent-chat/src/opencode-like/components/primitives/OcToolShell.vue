<script lang="ts">
export type OcToolShellProps = {
  title: string;
  subtitle?: string;
  status?: string;
  defaultOpen?: boolean;
};
</script>

<script setup lang="ts">
import { ref } from "vue";
import { ChevronDown, ChevronRight, Wrench } from "lucide-vue-next";
import OcStatusPill from "./OcStatusPill.vue";

const props = withDefaults(defineProps<OcToolShellProps>(), {
  defaultOpen: false
});
const open = ref(props.defaultOpen);
</script>

<template>
  <section class="oc-tool">
    <button type="button" class="oc-tool__trigger" @click="open = !open">
      <Wrench class="oc-tool__icon" />
      <span class="oc-tool__title">{{ title }}</span>
      <span v-if="subtitle" class="oc-tool__subtitle">{{ subtitle }}</span>
      <OcStatusPill v-if="status" :label="status" />
      <ChevronDown v-if="open" class="oc-tool__chevron" />
      <ChevronRight v-else class="oc-tool__chevron" />
    </button>
    <div v-if="open" class="oc-tool__body">
      <slot />
    </div>
  </section>
</template>
