<script lang="ts">
export type OcToolShellProps = {
  title: string;
  subtitle?: string;
  subtitleTitle?: string;
  status?: string;
  defaultOpen?: boolean;
  nested?: boolean;
};
</script>

<script setup lang="ts">
import { ref } from "vue";
import { ChevronDown, ChevronRight } from "lucide-vue-next";

const props = withDefaults(defineProps<OcToolShellProps>(), {
  defaultOpen: false
});
const open = ref(props.defaultOpen);

function formatStatus(status: string): string {
  const val = status.toLowerCase();
  if (val === "completed" || val === "success") return "已读取";
  if (val === "failed" || val === "error") return "失败";
  if (val === "running") return "进行中";
  return status;
}
</script>

<template>
  <section :class="['oc-tool', nested ? 'is-nested' : '']">
    <button type="button" class="oc-tool__trigger" @click="open = !open">
      <span class="oc-tool__title">{{ title }}</span>
      <span v-if="subtitle" class="oc-tool__subtitle" :title="subtitleTitle ?? subtitle">{{ subtitle }}</span>
      <span v-if="status" :class="['oc-tool__status', `is-${status.toLowerCase()}`]">{{ formatStatus(status) }}</span>
      <ChevronDown v-if="open" class="oc-tool__chevron" />
      <ChevronRight v-else class="oc-tool__chevron" />
    </button>
    <div v-if="open" class="oc-tool__body">
      <slot />
    </div>
  </section>
</template>
