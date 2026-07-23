<script setup lang="ts">
defineProps<{ x: number; y: number }>();

const emit = defineEmits<{ close: [] }>();
</script>

<template>
  <Teleport to="body">
    <div
      class="ta-file-context-menu-backdrop"
      @click="emit('close')"
      @contextmenu.prevent="emit('close')"
    />
    <div
      class="ta-file-context-menu"
      role="menu"
      :style="{ left: `${x}px`, top: `${y}px` }"
    >
      <slot />
    </div>
  </Teleport>
</template>

<style>
.ta-file-context-menu-backdrop {
  position: fixed;
  z-index: 2600;
  inset: 0;
}

.ta-file-context-menu {
  position: fixed;
  z-index: 2601;
  min-width: 180px;
  border: 1px solid var(--ta-tree-border-strong, #c8c8c8);
  border-radius: 6px;
  background: var(--ta-tree-bg, #fff);
  padding: 4px;
  box-shadow: 0 12px 28px rgb(15 23 42 / 18%);
}

.ta-file-context-menu-item {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  padding: 6px 8px;
  color: var(--ta-tree-text, #1f2937);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}

.ta-file-context-menu-item:hover {
  background: var(--ta-tree-hover, #eef2f7);
}

.ta-file-context-menu-item.is-danger {
  color: var(--ta-danger, #dc2626);
}

.ta-file-context-menu-item span {
  color: var(--ta-tree-muted, #8b949e);
  font-size: 11px;
}
</style>
