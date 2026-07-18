<script setup lang="ts">
defineOptions({ inheritAttrs: false });

import type { StateRegionData } from "./adapter";

const props = defineProps<{ data: StateRegionData }>();
const emit = defineEmits<{ select: [regionId: string] }>();
</script>

<template>
  <section
    class="ta-state-region"
    :class="{ 'is-selected': props.data.selected }"
    :aria-label="props.data.label"
    @click.stop="emit('select', props.data.regionId)"
  >
    <span>{{ props.data.label }}</span>
  </section>
</template>

<style scoped>
.ta-state-region { box-sizing: border-box; width: 100%; height: 100%; border: 1px dashed #cbd5e1; border-radius: 10px; background: rgba(248, 250, 252, 0.62); color: #64748b; font-size: 11px; }
.ta-state-region > span { position: absolute; top: 8px; left: 12px; padding: 2px 6px; border-radius: 4px; background: #fff; }
.ta-state-region.is-selected { border-color: #6366f1; background: rgba(238, 242, 255, 0.58); }
</style>
