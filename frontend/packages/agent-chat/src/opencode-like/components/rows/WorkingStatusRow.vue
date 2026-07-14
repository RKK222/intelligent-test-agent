<script lang="ts">
export type WorkingStatusRowProps = {
  continuation?: boolean;
  showHeader?: boolean;
};
</script>

<script setup lang="ts">
withDefaults(defineProps<WorkingStatusRowProps>(), {
  continuation: true,
  showHeader: false
});
</script>

<template>
  <div :class="['oc-working-frame', 'oc-assistant-frame', continuation ? 'is-continuation' : '', showHeader ? 'has-header' : 'is-headerless']">
    <div class="oc-assistant-frame__content">
      <div class="oc-working-status" role="status" aria-live="polite">
        <span class="oc-working-status__title">正在工作</span>
        <span class="oc-working-status__subtitle">等待后续输出</span>
      </div>
    </div>
  </div>
</template>
